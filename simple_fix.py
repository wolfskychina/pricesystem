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


def fix_to_string(content):
    lines = content.split('\n')
    new_lines = []
    in_to_string = False
    for i, line in enumerate(lines):
        stripped = line.strip()
        if 'public String toString()' in stripped:
            in_to_string = True
            new_lines.append(line)
            continue
        if in_to_string and 'return "' in stripped and stripped.endswith('";'):
            match = re.search(r'return\s+"(\w+)\{(.+)"\};', stripped)
            if match:
                class_name = match.group(1)
                body = match.group(2)
                body = body.replace(', ', '=')
                body = body.replace('" + ', '=')
                body = body.replace(' + "', ', ')
                if body.endswith(', '):
                    body = body[:-2]
                fixed_line = '        return "' + class_name + '{' + body + '}";'
                new_lines.append(fixed_line)
                continue
        if in_to_string and stripped == '}':
            in_to_string = False
        new_lines.append(line)
    return '\n'.join(new_lines)


def remove_trailing_overrides(content):
    lines = content.split('\n')
    while lines and lines[-1].strip() == '':
        lines.pop()
    if lines and lines[-1].strip() == '}':
        closing_brace = lines.pop()
        while lines and (lines[-1].strip() == '' or lines[-1].strip() == '@Override'):
            lines.pop()
        lines.append(closing_brace)
    return '\n'.join(lines) + '\n'


def remove_unused_imports(content):
    lines = content.split('\n')
    new_lines = []
    for line in lines:
        if 'import java.util.Objects;' in line:
            continue
        new_lines.append(line)
    return '\n'.join(new_lines)


def fix_file(file_path):
    content = read_file(file_path)
    original = content

    content = fix_to_string(content)
    content = remove_trailing_overrides(content)
    content = remove_unused_imports(content)

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
    fixed_files = []

    for f in sorted(java_files):
        if fix_file(f):
            fixed_files.append(f)
            print('Fixed: ' + f)

    print('\nTotal fixed: ' + str(len(fixed_files)))


if __name__ == '__main__':
    main()
