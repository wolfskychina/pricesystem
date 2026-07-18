#!/usr/bin/env python3
import os
import re


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


def extract_class_info(content):
    class_name = None
    is_enum = False
    is_abstract = False
    super_class = None
    package = None
    imports = []

    pkg_match = re.search(r'package\s+([\w.]+)\s*;', content)
    if pkg_match:
        package = pkg_match.group(1)

    import_lines = re.findall(r'import\s+[\w.*]+\s*;', content)
    imports = import_lines

    class_match = re.search(r'(?:public\s+)?(?:abstract\s+)?(?:final\s+)?(class|enum)\s+(\w+)', content)
    if class_match:
        if class_match.group(1) == 'enum':
            is_enum = True
        class_name = class_match.group(2)

    if class_name:
        if 'abstract class ' + class_name in content or 'abstract\nclass ' + class_name in content:
            is_abstract = True

        super_match = re.search(r'class\s+' + re.escape(class_name) + r'\s+extends\s+(\w+)', content)
        if super_match:
            super_class = super_match.group(1)

    return package, imports, class_name, is_enum, is_abstract, super_class


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
                    r'(private|protected|public)\s+(static\s+)?(final\s+)?([\w<>,\s\[\]\.?]+?)\s+(\w+)\s*[=;]',
                    stripped
                )
                if field_match:
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
                        'has_initializer': has_assign,
                        'line': stripped
                    })

    return fields


def extract_class_body_start_end(content, class_name):
    lines = content.split('\n')
    start_line = -1
    end_line = -1
    brace_count = 0
    found_class = False

    for i, line in enumerate(lines):
        if not found_class:
            if class_name in line and ('class ' in line or 'enum ' in line) and '{' in line:
                found_class = True
                start_line = i
                brace_count = line.count('{') - line.count('}')
            continue
        brace_count += line.count('{') - line.count('}')
        if brace_count <= 0:
            end_line = i
            break

    return start_line, end_line


def extract_annotations_before_class(content, class_name):
    lines = content.split('\n')
    class_line_idx = -1

    for i, line in enumerate(lines):
        if class_name in line and ('class ' in line or 'enum ' in line):
            class_line_idx = i
            break

    if class_line_idx == -1:
        return []

    annotations = []
    for i in range(class_line_idx - 1, -1, -1):
        stripped = lines[i].strip()
        if stripped.startswith('@'):
            annotations.insert(0, stripped)
        elif stripped == '':
            continue
        else:
            break

    return annotations


def extract_static_final_fields(fields):
    return [f for f in fields if f['is_static'] and f['is_final']]


def generate_getter(field):
    name = field['name']
    type_ = field['type']
    if type_ == 'boolean':
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

    lines = ['    @Override', '    public String toString() {', '        return "' + class_name + '{" +']

    for i, f in enumerate(non_static_fields):
        name = f['name']
        type_ = f['type']
        if i == 0:
            prefix = ''
        else:
            prefix = '", "'
        if type_ in ('String', 'Character', 'char'):
            lines.append('            ' + prefix + name + '=\'" + ' + name + ' + "\' +')
        else:
            lines.append('            ' + prefix + name + '=" + ' + name + ' +')

    lines[-1] = lines[-1][:-2] + '";'
    lines.append('    }')
    return '\n'.join(lines)


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
        type_ = f['type']
        name = f['name']
        if type_ in ('int', 'long', 'short', 'byte', 'char', 'boolean'):
            lines.append('        if (' + name + ' != that.' + name + ') return false;')
        elif type_ == 'float':
            lines.append('        if (Float.compare(that.' + name + ', ' + name + ') != 0) return false;')
        elif type_ == 'double':
            lines.append('        if (Double.compare(that.' + name + ', ' + name + ') != 0) return false;')
        else:
            lines.append('        if (' + name + ' != null ? !' + name + '.equals(that.' + name + ') : that.' + name + ' != null) return false;')

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
        type_ = f['type']
        name = f['name']
        if type_ == 'boolean':
            lines.append('        result = 31 * result + (' + name + ' ? 1 : 0);')
        elif type_ in ('int', 'short', 'byte', 'char'):
            lines.append('        result = 31 * result + (int) ' + name + ';')
        elif type_ == 'long':
            lines.append('        result = 31 * result + (int) (' + name + ' ^ (' + name + ' >>> 32));')
        elif type_ == 'float':
            lines.append('        result = 31 * result + Float.floatToIntBits(' + name + ');')
        elif type_ == 'double':
            lines.append('        long temp = Double.doubleToLongBits(' + name + ');')
            lines.append('        result = 31 * result + (int) (temp ^ (temp >>> 32));')
        else:
            lines.append('        result = 31 * result + (' + name + ' != null ? ' + name + '.hashCode() : 0);')

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


def generate_slf4j(class_name):
    return '    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(' + class_name + '.class);'


def process_file(file_path):
    content = read_file(file_path)
    original = content

    package, imports, class_name, is_enum, is_abstract, super_class = extract_class_info(content)
    if not class_name:
        return False

    has_data = '@Data' in content
    has_slf4j = '@Slf4j' in content
    has_required_args = '@RequiredArgsConstructor' in content
    has_getter = '@Getter' in content
    has_setter = '@Setter' in content
    has_equals_hash_code = '@EqualsAndHashCode' in content
    equals_call_super = 'callSuper = true' in content

    if not any([has_data, has_slf4j, has_required_args, has_getter, has_setter, has_equals_hash_code]):
        return False

    fields = extract_fields(content, class_name)

    new_imports = []
    for imp in imports:
        if 'lombok' not in imp:
            new_imports.append(imp)

    annotations = extract_annotations_before_class(content, class_name)
    new_annotations = []
    for ann in annotations:
        is_lombok = False
        lombok_anns = ['@Data', '@Getter', '@Setter', '@Slf4j', '@RequiredArgsConstructor',
                      '@NoArgsConstructor', '@AllArgsConstructor', '@Builder', '@ToString',
                      '@Value', '@EqualsAndHashCode']
        for la in lombok_anns:
            if ann.startswith(la):
                is_lombok = True
                break
        if not is_lombok:
            new_annotations.append(ann)

    start_line, end_line = extract_class_body_start_end(content, class_name)
    if start_line == -1 or end_line == -1:
        return False

    lines = content.split('\n')
    class_declaration = lines[start_line].strip()

    body_lines = lines[start_line + 1:end_line]

    cleaned_body_lines = []
    skip_until_brace = 0
    for line in body_lines:
        stripped = line.strip()

        if skip_until_brace > 0:
            skip_until_brace -= 1
            if '{' in line:
                skip_until_brace += line.count('{')
            if '}' in line:
                skip_until_brace -= line.count('}')
            if skip_until_brace <= 0:
                continue
            continue

        if stripped.startswith('public ') and '(' in stripped and '{' in stripped:
            method_match = re.match(r'public\s+[\w<>\[\],\s]+\s+(\w+)\s*\(', stripped)
            if method_match:
                method_name = method_match.group(1)
                if method_name == class_name:
                    if has_required_args:
                        brace_count = line.count('{') - line.count('}')
                        skip_until_brace = brace_count
                        if skip_until_brace > 0:
                            continue
                    continue
                if method_name.startswith('get') or method_name.startswith('set') or method_name.startswith('is'):
                    field_name = None
                    if method_name.startswith('get'):
                        field_name = method_name[3:4].lower() + method_name[4:]
                    elif method_name.startswith('set'):
                        field_name = method_name[3:4].lower() + method_name[4:]
                    elif method_name.startswith('is'):
                        field_name = method_name[2:3].lower() + method_name[3:]
                    if field_name and any(f['name'] == field_name for f in fields):
                        brace_count = line.count('{') - line.count('}')
                        skip_until_brace = brace_count
                        if skip_until_brace > 0:
                            continue
                        continue
                if method_name in ('toString', 'equals', 'hashCode'):
                    brace_count = line.count('{') - line.count('}')
                    skip_until_brace = brace_count
                    if skip_until_brace > 0:
                        continue
                    continue
                if 'log' in stripped and 'private static final' in stripped and 'Logger' in stripped:
                    continue

        if 'private static final org.slf4j.Logger log' in stripped:
            continue

        cleaned_body_lines.append(line)

    generated_parts = []

    if has_slf4j:
        generated_parts.append(generate_slf4j(class_name))

    if has_required_args:
        constructor = generate_required_args_constructor(class_name, fields)
        if constructor:
            generated_parts.append(constructor)

    instance_fields = [f for f in fields if not f['is_static']]
    if has_data or has_getter:
        for f in instance_fields:
            generated_parts.append(generate_getter(f))

    if has_data or has_setter:
        non_final_instance_fields = [f for f in fields if not f['is_static'] and not f['is_final']]
        for f in non_final_instance_fields:
            generated_parts.append(generate_setter(f))

    if has_data:
        call_super = equals_call_super
        generated_parts.append(generate_equals(class_name, fields, call_super=call_super))
        generated_parts.append(generate_hash_code(fields, call_super=call_super))
        generated_parts.append(generate_to_string(class_name, fields))

    if has_equals_hash_code and not has_data:
        generated_parts.append(generate_equals(class_name, fields, call_super=equals_call_super))
        generated_parts.append(generate_hash_code(fields, call_super=equals_call_super))

    field_lines = []
    other_lines = []
    in_fields_section = True
    for line in cleaned_body_lines:
        stripped = line.strip()
        if stripped == '':
            if in_fields_section:
                field_lines.append(line)
            else:
                other_lines.append(line)
            continue
        if re.match(r'(private|protected|public)\s+', stripped) and '(' not in stripped and ';' in stripped:
            if 'class ' not in stripped and 'enum ' not in stripped:
                field_lines.append(line)
                continue
        in_fields_section = False
        other_lines.append(line)

    new_body = []
    new_body.extend([l.rstrip() for l in field_lines if l.strip() != ''] )
    if generated_parts:
        new_body.append('')
        new_body.append(generated_parts.join('\n\n'))
    if other_lines:
        new_body.append('')
        new_body.extend([l.rstrip() for l in other_lines if l.strip() != ''])

    new_content_lines = []

    if package:
        new_content_lines.append('package ' + package + ';')
        new_content_lines.append('')

    if new_imports:
        new_content_lines.extend(new_imports)
        new_content_lines.append('')

    if new_annotations:
        new_content_lines.extend(new_annotations)

    new_content_lines.append(class_declaration)
    new_content_lines.extend(new_body)
    new_content_lines.append('}')

    new_content = '\n'.join(new_content_lines) + '\n'

    if new_content != original:
        write_file(file_path, new_content)
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

    for f in sorted(java_files):
        try:
            if process_file(f):
                modified_files.append(f)
                print('Processed: ' + f)
        except Exception as e:
            print('Error processing ' + f + ': ' + str(e))

    print('\nTotal modified: ' + str(len(modified_files)))
    return modified_files


if __name__ == '__main__':
    main()
