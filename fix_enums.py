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


def fix_enum(file_path):
    content = read_file(file_path)
    original = content
    lines = content.split('\n')

    new_lines = []
    enum_constants = []
    enum_name = None
    in_enum = False
    found_second_constants = False
    constructor_found = False
    fields_collected = False
    fields = []
    getters = []

    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        if 'enum ' in stripped and not in_enum:
            match = re.search(r'enum\s+(\w+)', stripped)
            if match:
                enum_name = match.group(1)
            in_enum = True
            new_lines.append('public enum ' + enum_name + ' {')
            new_lines.append('')
            i += 1
            while i < len(lines) and ';' not in lines[i]:
                const_line = lines[i].strip()
                if const_line and not const_line.startswith('//') and not const_line.startswith('*'):
                    if const_line.endswith(',') or const_line.endswith(';'):
                        enum_constants.append(const_line.rstrip(';').rstrip(','))
                i += 1
            if i < len(lines) and ';' in lines[i]:
                const_part = lines[i].split(';')[0].strip()
                if const_part:
                    enum_constants.append(const_part.rstrip(','))
                i += 1
            continue

        if in_enum and not fields_collected:
            if stripped.startswith('private final') and ';' in stripped:
                fields.append(stripped)
                i += 1
                continue
            if stripped.startswith('public ') and 'get' in stripped and '()' in stripped:
                getter_lines = [line]
                brace_count = 0
                started = False
                i += 1
                while i < len(lines):
                    getter_lines.append(lines[i])
                    if '{' in lines[i]:
                        started = True
                        brace_count += lines[i].count('{')
                    if '}' in lines[i]:
                        brace_count -= lines[i].count('}')
                    if started and brace_count <= 0:
                        i += 1
                        break
                    i += 1
                getters.append('\n'.join(getter_lines))
                continue
            if stripped.startswith(enum_constants[0].split('(')[0] if enum_constants else '') and '(' in stripped:
                found_second_constants = True
                while i < len(lines) and ';' not in lines[i]:
                    i += 1
                if i < len(lines):
                    i += 1
                fields_collected = True
                continue

        if in_enum and fields_collected and not constructor_found:
            if stripped.startswith(enum_name) and '(' in stripped:
                constructor_lines = []
                brace_count = 0
                started = False
                while i < len(lines):
                    constructor_lines.append(lines[i])
                    if '{' in lines[i]:
                        started = True
                        brace_count += lines[i].count('{')
                    if '}' in lines[i]:
                        brace_count -= lines[i].count('}')
                    if started and brace_count <= 0:
                        i += 1
                        break
                    i += 1
                constructor_found = True
                continue

        new_lines.append(line)
        i += 1

    if enum_name and enum_constants and fields and getters:
        result_lines = [
            'package ' + re.search(r'package\s+([\w\.]+);', content).group(1) + ';',
            '',
            'public enum ' + enum_name + ' {',
            '',
        ]
        for idx, const in enumerate(enum_constants):
            if idx < len(enum_constants) - 1:
                result_lines.append('    ' + const + ',')
            else:
                result_lines.append('    ' + const + ';')
        result_lines.append('')
        for field in fields:
            result_lines.append('    ' + field)
        result_lines.append('')
        for getter in getters:
            getter_lines = getter.split('\n')
            for gl in getter_lines:
                result_lines.append('    ' + gl.strip())
            result_lines.append('')

        rest_start = content.find(enum_name + '(')
        if rest_start > 0:
            brace_start = content.find('{', rest_start)
            brace_count = 0
            idx = brace_start
            started = False
            while idx < len(content):
                if content[idx] == '{':
                    started = True
                    brace_count += 1
                elif content[idx] == '}':
                    brace_count -= 1
                if started and brace_count == 0:
                    idx += 1
                    break
                idx += 1
            rest = content[idx:].strip()
            if rest:
                rest_lines = rest.split('\n')
                for rl in rest_lines:
                    if rl.strip() == '}':
                        continue
                    result_lines.append(rl)

        result_lines.append('}')
        result_lines.append('')
        content = '\n'.join(result_lines)

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
        content = read_file(f)
        if '\nenum ' in content or content.startswith('enum '):
            if fix_enum(f):
                count += 1
                print('Fixed enum: ' + f)
    print('\nTotal fixed enums: ' + str(count))


if __name__ == '__main__':
    main()
