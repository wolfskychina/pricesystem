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


def extract_class_name(content):
    match = re.search(r'(?:public\s+)?(?:abstract\s+)?(?:final\s+)?(?:class|enum)\s+(\w+)', content)
    if match:
        return match.group(1)
    return None


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
            if stripped == '' or '(' in stripped:
                continue
            field_match = re.match(
                r'(private|protected|public)\s+(static\s+)?(final\s+)?([\w<>,\s\[\]\.]+?)\s+(\w+)\s*(?:=|;)',
                stripped
            )
            if field_match:
                is_static = field_match.group(2) is not None
                is_final = field_match.group(3) is not None
                field_type = field_match.group(4).strip()
                field_name = field_match.group(5)
                has_initializer = '=' in stripped
                if field_name == 'serialVersionUID' or (field_name == 'log' and 'Logger' in field_type):
                    continue
                fields.append({
                    'name': field_name,
                    'type': field_type,
                    'is_final': is_final,
                    'is_static': is_static,
                    'has_initializer': has_initializer
                })
    return fields


def generate_to_string(class_name, fields):
    non_static = [f for f in fields if not f['is_static']]
    if not non_static:
        return '    @Override\n    public String toString() {\n        return "' + class_name + '()";\n    }'
    parts = []
    for f in non_static:
        name = f['name']
        type_ = f['type']
        if type_ in ('String', 'Character', 'char'):
            parts.append(name + '=\'" + ' + name + ' + "\'')
        else:
            parts.append(name + '=" + ' + name)
    return '    @Override\n    public String toString() {\n        return "' + class_name + '{' + ', '.join(parts) + '}";\n    }'


def fix_file(file_path):
    content = read_file(file_path)
    original = content
    class_name = extract_class_name(content)
    if not class_name:
        return False

    fields = extract_fields(content, class_name)
    if not fields:
        return False

    has_to_string = 'public String toString()' in content
    if not has_to_string:
        return False

    lines = content.split('\n')
    new_lines = []
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        if 'public String toString()' in stripped:
            new_tostring = generate_to_string(class_name, fields)
            new_lines.extend(new_tostring.split('\n'))
            i += 1
            while i < len(lines) and '}' not in lines[i].strip():
                i += 1
            if i < len(lines):
                i += 1
            continue
        new_lines.append(line)
        i += 1

    content = '\n'.join(new_lines)

    lines = content.split('\n')
    while lines and lines[-1].strip() == '':
        lines.pop()
    if lines and lines[-1].strip() == '}':
        closing = lines.pop()
        while lines and (lines[-1].strip() == '' or lines[-1].strip() == '@Override'):
            lines.pop()
        lines.append(closing)
    content = '\n'.join(lines) + '\n'

    if content != original:
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
    count = 0
    for f in sorted(java_files):
        if fix_file(f):
            count += 1
            print('Fixed: ' + f)
    print('\nTotal fixed: ' + str(count))


if __name__ == '__main__':
    main()
