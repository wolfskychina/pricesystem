#!/usr/bin/env python3
import os
import re
import sys


def find_java_files(directories):
    java_files = []
    for directory in directories:
        for root, dirs, files in os.walk(directory):
            for file in files:
                if file.endswith('.java'):
                    java_files.append(os.path.join(root, file))
    return java_files


def read_file(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        return f.read()


def write_file(file_path, content):
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)


def has_lombok(content):
    return 'import lombok.' in content


def remove_lombok_imports(content):
    lines = content.split('\n')
    result = []
    for line in lines:
        if 'import lombok.' in line:
            continue
        result.append(line)
    return '\n'.join(result)


def remove_annotations(content):
    annotations_to_remove = [
        '@Data',
        '@Getter',
        '@Setter',
        '@Slf4j',
        '@RequiredArgsConstructor',
        '@NoArgsConstructor',
        '@AllArgsConstructor',
        '@Builder',
        '@ToString',
        '@Value',
        '@EqualsAndHashCode',
    ]
    lines = content.split('\n')
    result = []
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        removed = False
        for ann in annotations_to_remove:
            if stripped.startswith(ann):
                removed = True
                break
        if removed:
            i += 1
            continue
        result.append(line)
        i += 1
    return '\n'.join(result)


def extract_class_info(content):
    class_name = None
    is_enum = False
    is_abstract = False
    super_class = None

    class_match = re.search(r'(?:public\s+)?(?:abstract\s+)?(?:final\s+)?(class|enum)\s+(\w+)', content)
    if class_match:
        if class_match.group(1) == 'enum':
            is_enum = True
        class_name = class_match.group(2)

    if 'abstract' in content and 'class ' + class_name in content:
        is_abstract = True

    super_match = re.search(r'class\s+' + re.escape(class_name) + r'\s+extends\s+(\w+)', content)
    if super_match:
        super_class = super_match.group(1)

    return class_name, is_enum, is_abstract, super_class


def extract_fields(content, class_name):
    fields = []
    lines = content.split('\n')

    brace_count = 0
    in_class = False
    found_class = False

    for line in lines:
        if not found_class:
            if class_name in line and ('class ' in line or 'enum ' in line) and '{' in line:
                found_class = True
                in_class = True
                brace_count = line.count('{') - line.count('}')
            continue

        if in_class:
            brace_count += line.count('{') - line.count('}')
            if brace_count <= 0:
                break

            stripped = line.strip()

            if stripped.startswith('//') or stripped.startswith('*') or stripped.startswith('/*'):
                continue
            if stripped == '':
                continue

            if re.match(r'(private|protected|public)\s+', stripped):
                if '(' in stripped:
                    continue

                has_assign = '=' in stripped and ';' in stripped
                field_match = re.match(
                    r'(private|protected|public)\s+(static\s+)?(final\s+)?([\w<>,\s\[\]]+?)\s+(\w+)\s*[=;]',
                    stripped
                )
                if field_match:
                    access_modifier = field_match.group(1)
                    is_static = field_match.group(2) is not None
                    is_final = field_match.group(3) is not None
                    field_type = field_match.group(4).strip()
                    field_name = field_match.group(5)

                    if field_name == 'serialVersionUID':
                        continue

                    fields.append({
                        'name': field_name,
                        'type': field_type,
                        'is_final': is_final,
                        'is_static': is_static,
                        'has_initializer': has_assign
                    })

    return fields


def generate_getter(field):
    name = field['name']
    type_ = field['type']
    if type_ == 'boolean' or type_ == 'Boolean':
        prefix = 'is'
    else:
        prefix = 'get'
    cap_name = name[0].upper() + name[1:]
    return '    public ' + type_ + ' ' + prefix + cap_name + '() {\n        return ' + name + ';\n    }'


def generate_setter(field):
    name = field['name']
    type_ = field['type']
    cap_name = name[0].upper() + name[1:]
    return '    public void set' + cap_name + '(' + type_ + ' ' + name + ') {\n        this.' + name + ' = ' + name + ';\n    }'


def generate_to_string(class_name, fields):
    non_static_fields = [f for f in fields if not f['is_static']]
    if not non_static_fields:
        return '    @Override\n    public String toString() {\n        return "' + class_name + '()";\n    }'

    parts = []
    for f in non_static_fields:
        parts.append(f['name'] + '=" + ' + f['name'] + ' + "')
    body = ', '.join(parts)
    return '    @Override\n    public String toString() {\n        return "' + class_name + '(' + body + '");\n    }'


def generate_equals(class_name, fields, call_super=False):
    non_static_fields = [f for f in fields if not f['is_static']]
    lines = [
        '    @Override',
        '    public boolean equals(Object o) {',
        '        if (this == o) return true;',
        '        if (o == null || getClass() != o.getClass()) return false;',
    ]
    if call_super:
        lines.append('        if (!super.equals(o)) return false;')
    lines.append('        ' + class_name + ' that = (' + class_name + ') o;')
    for f in non_static_fields:
        if f['type'] in ('int', 'long', 'short', 'byte', 'char', 'boolean', 'double', 'float'):
            lines.append('        if (' + f['name'] + ' != that.' + f['name'] + ') return false;')
        else:
            lines.append('        if (' + f['name'] + ' != null ? !' + f['name'] + '.equals(that.' + f['name'] + ') : that.' + f['name'] + ' != null) return false;')
    lines.append('        return true;')
    lines.append('    }')
    return '\n'.join(lines)


def generate_hash_code(fields, call_super=False):
    non_static_fields = [f for f in fields if not f['is_static']]
    lines = [
        '    @Override',
        '    public int hashCode() {',
    ]
    if call_super:
        lines.append('        int result = super.hashCode();')
    else:
        lines.append('        int result = 17;')

    for f in non_static_fields:
        if f['type'] == 'boolean':
            lines.append('        result = 31 * result + (' + f['name'] + ' ? 1 : 0);')
        elif f['type'] in ('int', 'short', 'byte', 'char'):
            lines.append('        result = 31 * result + (int) ' + f['name'] + ';')
        elif f['type'] == 'long':
            lines.append('        result = 31 * result + (int) (' + f['name'] + ' ^ (' + f['name'] + ' >>> 32));')
        elif f['type'] == 'float':
            lines.append('        result = 31 * result + Float.floatToIntBits(' + f['name'] + ');')
        elif f['type'] == 'double':
            lines.append('        long temp = Double.doubleToLongBits(' + f['name'] + ');')
            lines.append('        result = 31 * result + (int) (temp ^ (temp >>> 32));')
        else:
            lines.append('        result = 31 * result + (' + f['name'] + ' != null ? ' + f['name'] + '.hashCode() : 0);')

    lines.append('        return result;')
    lines.append('    }')
    return '\n'.join(lines)


def generate_required_args_constructor(class_name, fields):
    final_fields = [f for f in fields if f['is_final'] and not f['is_static'] and not f['has_initializer']]
    if not final_fields:
        return None

    params = ', '.join([f['type'] + ' ' + f['name'] for f in final_fields])
    body_lines = []
    for f in final_fields:
        body_lines.append('        this.' + f['name'] + ' = ' + f['name'] + ';')

    return '    public ' + class_name + '(' + params + ') {\n' + '\n'.join(body_lines) + '\n    }'


def find_last_field_end(content, class_name):
    lines = content.split('\n')
    brace_count = 0
    in_class = False
    found_class = False
    last_field_line = -1
    first_method_line = -1

    for i, line in enumerate(lines):
        if not found_class:
            if class_name in line and ('class ' in line or 'enum ' in line) and '{' in line:
                found_class = True
                in_class = True
                brace_count = line.count('{') - line.count('}')
                last_field_line = i
            continue

        if in_class:
            prev_brace = brace_count
            brace_count += line.count('{') - line.count('}')
            if brace_count <= 0:
                break

            stripped = line.strip()

            if stripped == '' or stripped.startswith('//') or stripped.startswith('*') or stripped.startswith('/*'):
                continue

            if re.match(r'(private|protected|public)\s+', stripped) and '(' not in stripped and ';' in stripped:
                if 'class ' not in stripped and 'enum ' not in stripped:
                    last_field_line = i
                    continue

            if '(' in stripped and ('{' in line or brace_count > prev_brace):
                if first_method_line == -1:
                    first_method_line = i
                    break

    return last_field_line


def insert_generated_code(content, class_name, generated_code):
    if not generated_code:
        return content

    insert_line = find_last_field_end(content, class_name)
    if insert_line == -1:
        class_match = re.search(r'(class|enum)\s+' + re.escape(class_name) + r'[^{]*\{', content)
        if class_match:
            insert_pos = class_match.end()
            return content[:insert_pos] + '\n\n' + generated_code + '\n' + content[insert_pos:]
        return content

    lines = content.split('\n')
    lines.insert(insert_line + 1, '')
    lines.insert(insert_line + 2, generated_code)
    return '\n'.join(lines)


def process_file(file_path):
    content = read_file(file_path)
    original_content = content

    if not has_lombok(content):
        return False

    class_name, is_enum, is_abstract, super_class = extract_class_info(content)
    if not class_name:
        return False

    fields = extract_fields(content, class_name)

    has_data = '@Data' in content
    has_slf4j = '@Slf4j' in content
    has_required_args = '@RequiredArgsConstructor' in content
    has_getter = '@Getter' in content
    has_setter = '@Setter' in content
    has_equals_hash_code = '@EqualsAndHashCode' in content
    equals_call_super = 'callSuper = true' in content

    content = remove_lombok_imports(content)
    content = remove_annotations(content)

    generated_parts = []

    if has_slf4j:
        generated_parts.append(
            '    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(' + class_name + '.class);'
        )

    if has_required_args:
        constructor = generate_required_args_constructor(class_name, fields)
        if constructor:
            generated_parts.append(constructor)

    if has_data or has_getter:
        instance_fields = [f for f in fields if not f['is_static']]
        for f in instance_fields:
            generated_parts.append(generate_getter(f))

    if has_data or has_setter:
        non_final_instance_fields = [f for f in fields if not f['is_static'] and not f['is_final']]
        for f in non_final_instance_fields:
            generated_parts.append(generate_setter(f))

    if has_data:
        generated_parts.append(generate_to_string(class_name, fields))
        call_super = equals_call_super or (super_class is not None and '@EqualsAndHashCode(callSuper = true)' in original_content)
        generated_parts.append(generate_equals(class_name, fields, call_super=call_super))
        generated_parts.append(generate_hash_code(fields, call_super=call_super))

    if has_equals_hash_code and not has_data:
        generated_parts.append(generate_equals(class_name, fields, call_super=equals_call_super))
        generated_parts.append(generate_hash_code(fields, call_super=equals_call_super))

    if generated_parts:
        generated_code = '\n\n'.join(generated_parts)
        content = insert_generated_code(content, class_name, generated_code)

    if content != original_content:
        write_file(file_path, content)
        return True
    return False


def main():
    directories = [
        '/workspace/common/common-core/src/main/java/',
        '/workspace/common/common-persistence/src/main/java/',
        '/workspace/sim/sim-exchange/src/main/java/',
        '/workspace/services/refdata-service/src/main/java/',
    ]

    java_files = find_java_files(directories)
    modified_files = []

    for file_path in sorted(java_files):
        if process_file(file_path):
            modified_files.append(file_path)
            print('Modified: ' + file_path)

    print('\nTotal modified files: ' + str(len(modified_files)))
    return modified_files


if __name__ == '__main__':
    main()
