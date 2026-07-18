#!/usr/bin/env python3
import os
import re
from pathlib import Path


def find_java_files(directories):
    java_files = []
    for directory in directories:
        for root, dirs, files in os.walk(directory):
            for file in files:
                if file.endswith('.java'):
                    java_files.append(os.path.join(root, file))
    return java_files


def parse_java_file(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    return content


def extract_class_name(content):
    match = re.search(r'(?:public\s+)?(?:abstract\s+)?(?:final\s+)?class\s+(\w+)', content)
    if match:
        return match.group(1)
    return None


def extract_super_class(content):
    match = re.search(r'class\s+\w+\s+extends\s+(\w+)', content)
    if match:
        return match.group(1)
    return None


def extract_fields(content):
    fields = []
    lines = content.split('\n')
    in_class = False
    brace_count = 0
    class_started = False

    for i, line in enumerate(lines):
        if 'class ' in line and '{' in line:
            in_class = True
            class_started = True
            brace_count = line.count('{') - line.count('}')
            continue
        if class_started and in_class:
            brace_count += line.count('{') - line.count('}')
            if brace_count <= 0:
                break

            stripped = line.strip()
            if stripped.startswith('//') or stripped.startswith('*') or stripped.startswith('/*'):
                continue
            if stripped == '':
                continue

            field_match = re.match(
                r'(?:private|protected|public)\s+(?:static\s+)?(?:final\s+)?(\w+(?:<[^>]+>)?)\s+(\w+)\s*(?:=|;)',
                stripped
            )
            if field_match and not stripped.startswith('private static final long'):
                field_type = field_match.group(1)
                field_name = field_match.group(2)
                is_final = ' final ' in stripped
                is_static = ' static ' in stripped
                fields.append({
                    'name': field_name,
                    'type': field_type,
                    'is_final': is_final,
                    'is_static': is_static
                })
    return fields


def has_lombok_annotations(content):
    lombok_annotations = [
        '@Data', '@Getter', '@Setter', '@Slf4j',
        '@RequiredArgsConstructor', '@EqualsAndHashCode',
        '@NoArgsConstructor', '@AllArgsConstructor',
        '@Builder', '@ToString', '@Value', '@Log'
    ]
    for ann in lombok_annotations:
        if ann in content:
            return True
    return False


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

    parts = []
    for f in non_static_fields:
        parts.append(f['name'] + '=" + ' + f['name'] + ' + "')
    body = '", "'.join(parts)
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
        lines.insert(2, '        if (!super.equals(o)) return false;')
    lines.append(f'        {class_name} that = ({class_name}) o;')
    for f in non_static_fields:
        if f['type'] in ('int', 'long', 'short', 'byte', 'char', 'boolean', 'double', 'float'):
            lines.append(f'        if ({f["name"]} != that.{f["name"]}) return false;')
        else:
            lines.append(f'        if ({f["name"]} != null ? !{f["name"]}.equals(that.{f["name"]}) : that.{f["name"]} != null) return false;')
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
            lines.append(f'        result = 31 * result + ({f["name"]} ? 1 : 0);')
        elif f['type'] in ('int', 'short', 'byte', 'char'):
            lines.append(f'        result = 31 * result + (int) {f["name"]};')
        elif f['type'] == 'long':
            lines.append(f'        result = 31 * result + (int) ({f["name"]} ^ ({f["name"]} >>> 32));')
        elif f['type'] == 'float':
            lines.append(f'        result = 31 * result + Float.floatToIntBits({f["name"]});')
        elif f['type'] == 'double':
            lines.append(f'        long temp = Double.doubleToLongBits({f["name"]});')
            lines.append(f'        result = 31 * result + (int) (temp ^ (temp >>> 32));')
        else:
            lines.append(f'        result = 31 * result + ({f["name"]} != null ? {f["name"]}.hashCode() : 0);')

    lines.append('        return result;')
    lines.append('    }')
    return '\n'.join(lines)


def generate_required_args_constructor(class_name, fields):
    final_fields = [f for f in fields if f['is_final'] and not f['is_static']]
    if not final_fields:
        return None

    params = ', '.join([f['type'] + ' ' + f['name'] for f in final_fields])
    body_lines = []
    for f in final_fields:
        body_lines.append('        this.' + f['name'] + ' = ' + f['name'] + ';')

    return '    public ' + class_name + '(' + params + ') {\n' + '\n'.join(body_lines) + '\n    }'


def remove_lombok_imports(content):
    lines = content.split('\n')
    new_lines = []
    for line in lines:
        if 'import lombok.' in line:
            continue
        new_lines.append(line)
    return '\n'.join(new_lines)


def remove_annotation_line(content, annotation_pattern):
    lines = content.split('\n')
    new_lines = []
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        if re.match(annotation_pattern, stripped):
            i += 1
            continue
        new_lines.append(line)
        i += 1
    return '\n'.join(new_lines)


def process_file(file_path):
    content = parse_java_file(file_path)
    if not has_lombok_annotations(content):
        return False

    original_content = content
    class_name = extract_class_name(content)
    if not class_name:
        return False

    fields = extract_fields(content)
    super_class = extract_super_class(content)

    has_data = '@Data' in content
    has_slf4j = '@Slf4j' in content
    has_required_args = '@RequiredArgsConstructor' in content
    has_equals_hash_code = '@EqualsAndHashCode' in content
    equals_call_super = 'callSuper = true' in content and has_equals_hash_code

    content = remove_lombok_imports(content)

    content = remove_annotation_line(content, r'@Data\b')
    content = remove_annotation_line(content, r'@Getter\b')
    content = remove_annotation_line(content, r'@Setter\b')
    content = remove_annotation_line(content, r'@Slf4j\b')
    content = remove_annotation_line(content, r'@RequiredArgsConstructor\b')
    content = remove_annotation_line(content, r'@EqualsAndHashCode\(.*\)')
    content = remove_annotation_line(content, r'@NoArgsConstructor\b')
    content = remove_annotation_line(content, r'@AllArgsConstructor\b')
    content = remove_annotation_line(content, r'@Builder\b')
    content = remove_annotation_line(content, r'@ToString\b')
    content = remove_annotation_line(content, r'@Value\b')

    generated_code = []

    if has_slf4j:
        generated_code.append(
            '    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(' + class_name + '.class);'
        )

    if has_required_args:
        constructor = generate_required_args_constructor(class_name, fields)
        if constructor:
            generated_code.append(constructor)

    if has_data:
        instance_fields = [f for f in fields if not f['is_static'] and not f['is_final']]
        for f in instance_fields:
            generated_code.append(generate_getter(f))
            generated_code.append(generate_setter(f))

        final_instance_fields = [f for f in fields if not f['is_static'] and f['is_final']]
        for f in final_instance_fields:
            generated_code.append(generate_getter(f))

        generated_code.append(generate_to_string(class_name, fields))
        generated_code.append(generate_equals(class_name, fields, call_super=(equals_call_super or (has_equals_hash_code and equals_call_super))))
        generated_code.append(generate_hash_code(fields, call_super=(equals_call_super or (has_equals_hash_code and equals_call_super))))

    if has_equals_hash_code and not has_data:
        generated_code.append(generate_equals(class_name, fields, call_super=equals_call_super))
        generated_code.append(generate_hash_code(fields, call_super=equals_call_super))

    if generated_code:
        class_body_pattern = re.compile(r'(class\s+\w+[^{]*\{)')
        match = class_body_pattern.search(content)
        if match:
            insert_pos = match.end()
            generated_str = '\n\n'.join(generated_code)
            content = content[:insert_pos] + '\n\n' + generated_str + '\n' + content[insert_pos:]

    if content != original_content:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
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

    for file_path in java_files:
        if process_file(file_path):
            modified_files.append(file_path)
            print(f'Modified: {file_path}')

    print(f'\nTotal modified files: {len(modified_files)}')
    return modified_files


if __name__ == '__main__':
    main()
