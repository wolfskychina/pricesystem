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
        if in_to_string and stripped.startswith('return "'):
            match = re.search(r'return\s+"(\w+)\((.+)"\);', stripped)
            if match:
                class_name = match.group(1)
                body = match.group(2)
                fixed_body = body.replace('" + ', '=').replace(' + "', ', ')
                if fixed_body.endswith(', '):
                    fixed_body = fixed_body[:-2]
                fixed_line = '        return "' + class_name + '(' + fixed_body + ')";'
                new_lines.append(fixed_line)
                continue
        if in_to_string and stripped == '}':
            in_to_string = False
        new_lines.append(line)
    return '\n'.join(new_lines)


def fix_equals(content):
    lines = content.split('\n')
    result = []
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        if 'public boolean equals(Object o)' in stripped:
            equals_lines = [line]
            i += 1
            while i < len(lines) and '}' not in lines[i].strip():
                equals_lines.append(lines[i])
                i += 1
            if i < len(lines):
                equals_lines.append(lines[i])

            has_super = any('super.equals(o)' in l for l in equals_lines)
            if has_super:
                new_equals = []
                for l in equals_lines:
                    if 'if (!super.equals(o))' in l.strip():
                        continue
                    new_equals.append(l)
                    if 'that = (' in l.strip() or 'that = (' in l:
                        new_equals.append('        if (!super.equals(o)) return false;')
                equals_lines = new_equals

            result.extend(equals_lines)
        else:
            result.append(line)
        i += 1
    return '\n'.join(result)


def fix_constructors(content):
    lines = content.split('\n')
    result = []
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        is_constructor = False
        if 'public ' in stripped and '(' in stripped and '{' in stripped:
            method_name = re.search(r'public\s+(\w+)\s*\(', stripped)
            if method_name:
                class_match = re.search(r'(?:public\s+)?(?:abstract\s+)?(?:class|enum)\s+(\w+)', content)
                if class_match and method_name.group(1) == class_match.group(1):
                    is_constructor = True

        if is_constructor:
            constructor_lines = [line]
            brace_count = line.count('{') - line.count('}')
            i += 1
            while i < len(lines) and brace_count > 0:
                constructor_lines.append(lines[i])
                brace_count += lines[i].count('{') - lines[i].count('}')
                i += 1

            constructor_text = '\n'.join(constructor_lines)

            has_initialized_final = False
            field_pattern = re.findall(r'private\s+final\s+[\w<>\[\]]+\s+(\w+)\s*=\s*', content)
            if field_pattern:
                for field_name in field_pattern:
                    if 'this.' + field_name + ' = ' in constructor_text:
                        has_initialized_final = True
                        break

            if has_initialized_final:
                new_params = []
                new_body = []
                param_match = re.search(r'\((.*?)\)', constructor_lines[0])
                if param_match:
                    params_str = param_match.group(1)
                    params = [p.strip() for p in params_str.split(',') if p.strip()]

                    initialized_fields = set()
                    for fl in field_pattern:
                        initialized_fields.add(fl)

                    new_param_list = []
                    for p in params:
                        parts = p.split()
                        if len(parts) >= 2:
                            pname = parts[-1]
                            if pname not in initialized_fields:
                                new_param_list.append(p)

                    new_first_line = re.sub(r'\(.*?\)', '(' + ', '.join(new_param_list) + ')', constructor_lines[0])
                    result.append(new_first_line)

                    for cl in constructor_lines[1:-1]:
                        skip = False
                        for fn in initialized_fields:
                            if 'this.' + fn + ' = ' in cl:
                                skip = True
                                break
                        if not skip:
                            result.append(cl)
                    if len(constructor_lines) > 0:
                        result.append(constructor_lines[-1])
                else:
                    result.extend(constructor_lines)
            else:
                result.extend(constructor_lines)
        else:
            result.append(line)
            i += 1
    return '\n'.join(result)


def fix_file(file_path):
    content = read_file(file_path)
    original = content

    content = fix_to_string(content)
    content = fix_equals(content)
    content = fix_constructors(content)

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
