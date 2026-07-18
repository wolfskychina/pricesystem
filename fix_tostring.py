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
    for i, f in enumerate(non_static):
        name = f['name']
        type_ = f['type']
        is_string = type_ in ('String', 'Character', 'char')
        if i == 0:
            if is_string:
                parts.append('"' + class_name + '{' + name + '=\'" + ' + name)
            else:
                parts.append('"' + class_name + '{' + name + '=" + ' + name)
        else:
            prev_is_string = non_static[i-1]['type'] in ('String', 'Character', 'char')
            if prev_is_string:
                if is_string:
                    parts.append(' + "\', ' + name + '=\'" + ' + name)
                else:
                    parts.append(' + "\', ' + name + '=" + ' + name)
            else:
                if is_string:
                    parts.append(' + ", ' + name + '=\'" + ' + name)
                else:
                    parts.append(' + ", ' + name + '=" + ' + name)
    last_is_string = non_static[-1]['type'] in ('String', 'Character', 'char')
    if last_is_string:
        parts.append(' + "\'}"')
    else:
        parts.append(' + "}"')
    return '    @Override\n    public String toString() {\n        return ' + ''.join(parts) + ';\n    }'


def remove_tostring_and_get_body(content):
    lines = content.split('\n')
    result_lines = []
    i = 0
    found = False
    while i < len(lines):
        line = lines[i]
        if 'public String toString()' in line:
            found = True
            i += 1
            brace_count = 0
            started = False
            while i < len(lines):
                if '{' in lines[i]:
                    started = True
                    brace_count += lines[i].count('{')
                if '}' in lines[i]:
                    brace_count -= lines[i].count('}')
                if started and brace_count <= 0:
                    i += 1
                    break
                i += 1
            continue
        result_lines.append(line)
        i += 1
    return '\n'.join(result_lines), found


def find_insert_position(content):
    lines = content.split('\n')
    last_brace_idx = -1
    for i, line in enumerate(lines):
        if line.strip() == '}':
            last_brace_idx = i
    return last_brace_idx


def fix_file(file_path):
    content = read_file(file_path)
    original = content
    class_name = extract_class_name(content)
    if not class_name:
        return False

    fields = extract_fields(content, class_name)
    if not fields:
        return False

    content, found = remove_tostring_and_get_body(content)
    if not found:
        return False

    new_tostring = generate_to_string(class_name, fields)
    insert_pos = find_insert_position(content)
    if insert_pos < 0:
        return False

    lines = content.split('\n')
    while insert_pos > 0 and lines[insert_pos - 1].strip() == '':
        insert_pos -= 1

    lines.insert(insert_pos, '')
    lines.insert(insert_pos, new_tostring)
    content = '\n'.join(lines)

    lines = content.split('\n')
    while lines and lines[-1].strip() == '':
        lines.pop()
    if lines and lines[-1].strip() == '}':
        closing = lines.pop()
        while lines and lines[-1].strip() == '':
            lines.pop()
        lines.append('')
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
            print('Fixed toString: ' + f)
    print('\nTotal fixed: ' + str(count))


if __name__ == '__main__':
    main()
