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
    interfaces = []

    pkg_match = re.search(r'package\s+([\w.]+)\s*;', content)
    if pkg_match:
        package = pkg_match.group(1)

    import_lines = re.findall(r'import\s+[\w.*]+\s*;', content)
    imports = [imp for imp in import_lines if 'lombok' not in imp]

    class_match = re.search(r'(?:public\s+)?(?:abstract\s+)?(?:final\s+)?(class|enum)\s+(\w+)', content)
    if class_match:
        if class_match.group(1) == 'enum':
            is_enum = True
        class_name = class_match.group(2)

    if class_name:
        if 'abstract class ' + class_name in content:
            is_abstract = True

        super_match = re.search(r'class\s+' + re.escape(class_name) + r'\s+extends\s+(\w+)', content)
        if super_match:
            super_class = super_match.group(1)

        impl_match = re.search(r'class\s+' + re.escape(class_name) + r'\s+implements\s+([^{]+)', content)
        if impl_match:
            interfaces = [i.strip() for i in impl_match.group(1).split(',')]

    return package, imports, class_name, is_enum, is_abstract, super_class, interfaces


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
            if '(' in stripped:
                continue

            field_match = re.match(
                r'(private|protected|public)\s+(static\s+)?(final\s+)?([\w<>,\s\[\]\.]+?)\s+(\w+)\s*[=;]',
                stripped
            )
            if field_match:
                is_static = field_match.group(2) is not None
                is_final = field_match.group(3) is not None
                field_type = field_match.group(4).strip()
                field_name = field_match.group(5)

                has_initializer = '=' in stripped

                if field_name == 'serialVersionUID':
                    continue
                if field_name == 'log' and 'Logger' in field_type:
                    continue

                fields.append({
                    'name': field_name,
                    'type': field_type,
                    'is_final': is_final,
                    'is_static': is_static,
                    'has_initializer': has_initializer,
                })

    return fields


def detect_lombok_usage(content, class_name, fields):
    has_getters = False
    has_setters = False
    has_to_string = False
    has_equals = False
    has_hash_code = False
    has_slf4j = False
    has_required_args_constructor = False
    has_equals_call_super = False

    if 'org.slf4j.Logger log' in content or 'Logger log =' in content:
        has_slf4j = True

    if 'public String toString()' in content:
        has_to_string = True

    if 'public boolean equals(Object o)' in content:
        has_equals = True
        if 'super.equals(o)' in content:
            has_equals_call_super = True

    if 'public int hashCode()' in content:
        has_hash_code = True

    instance_fields = [f for f in fields if not f['is_static']]
    getter_count = 0
    setter_count = 0
    for f in instance_fields:
        name = f['name']
        cap_name = name[0].upper() + name[1:]
        if 'get' + cap_name + '()' in content or (f['type'] == 'boolean' and 'is' + cap_name + '()' in content):
            getter_count += 1
        if 'set' + cap_name + '(' in content and not f['is_final']:
            setter_count += 1

    non_final_instance_fields = [f for f in instance_fields if not f['is_final']]
    if getter_count == len(instance_fields) and len(instance_fields) > 0:
        has_getters = True
    if setter_count == len(non_final_instance_fields) and len(non_final_instance_fields) > 0:
        has_setters = True

    if has_getters and has_setters and has_to_string and has_equals and has_hash_code:
        has_data = True
    else:
        has_data = False

    final_fields_no_init = [f for f in fields if f['is_final'] and not f['is_static'] and not f['has_initializer']]
    if final_fields_no_init:
        for f in final_fields_no_init:
            constructor_pattern = 'public ' + class_name + '('
            if constructor_pattern in content:
                has_required_args_constructor = True
                break

    return {
        'has_data': has_data,
        'has_getter': has_getters and not has_data,
        'has_setter': has_setters and not has_data,
        'has_slf4j': has_slf4j,
        'has_required_args': has_required_args_constructor,
        'has_equals_hash_code': has_equals and has_hash_code and not has_data,
        'equals_call_super': has_equals_call_super,
    }


def extract_annotations(content, class_name):
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
            if not any(stripped.startswith(la) for la in ['@Data', '@Getter', '@Setter', '@Slf4j',
                                                           '@RequiredArgsConstructor', '@NoArgsConstructor',
                                                           '@AllArgsConstructor', '@Builder', '@ToString',
                                                           '@Value', '@EqualsAndHashCode']):
                annotations.insert(0, stripped)
        elif stripped == '':
            continue
        else:
            break

    return annotations


def extract_other_methods_and_code(content, class_name, fields, lombok_info):
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

    if start_line == -1 or end_line == -1:
        return []

    body_lines = lines[start_line + 1:end_line]

    result = []
    skip_until_end = 0

    field_names = set(f['name'] for f in fields)

    for line in body_lines:
        stripped = line.strip()

        if skip_until_end > 0:
            brace_change = line.count('{') - line.count('}')
            skip_until_end += brace_change
            if skip_until_end <= 0:
                skip_until_end = 0
            continue

        if re.match(r'(private|protected|public)\s+(static\s+)?(final\s+)?[\w<>,\s\[\]]+\s+\w+\s*[=;]', stripped):
            if '(' not in stripped:
                continue

        if 'private static final org.slf4j.Logger log' in stripped:
            continue

        if stripped.startswith('public ') and '(' in stripped and ('{' in stripped or ')' in stripped):
            method_match = re.match(r'public\s+[\w<>\[\],\s]+\s+(\w+)\s*\(', stripped)
            if method_match:
                method_name = method_match.group(1)

                if method_name == class_name and lombok_info['has_required_args']:
                    if '{' in stripped:
                        skip_until_end = 1
                    else:
                        skip_until_end = 0
                    continue

                is_getter = False
                is_setter = False
                if method_name.startswith('get') and len(method_name) > 3:
                    field_name = method_name[3:4].lower() + method_name[4:]
                    if field_name in field_names:
                        is_getter = True
                elif method_name.startswith('is') and len(method_name) > 2:
                    field_name = method_name[2:3].lower() + method_name[3:]
                    if field_name in field_names:
                        is_getter = True
                elif method_name.startswith('set') and len(method_name) > 3:
                    field_name = method_name[3:4].lower() + method_name[4:]
                    if field_name in field_names:
                        is_setter = True

                if (is_getter and (lombok_info['has_data'] or lombok_info['has_getter'])) or \
                   (is_setter and (lombok_info['has_data'] or lombok_info['has_setter'])):
                    if '{' in stripped:
                        skip_until_end = 1
                    else:
                        skip_until_end = 0
                    continue

                if method_name in ('toString', 'equals', 'hashCode'):
                    if lombok_info['has_data'] or lombok_info['has_equals_hash_code'] or \
                       (method_name == 'toString' and lombok_info['has_data']):
                        if '{' in stripped:
                            skip_until_end = 1
                        else:
                            skip_until_end = 0
                        continue

        result.append(line)

    return result


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

    lines = ['    @Override', '    public String toString() {']
    parts = []
    for f in non_static_fields:
        name = f['name']
        type_ = f['type']
        if type_ in ('String', 'Character', 'char'):
            parts.append(name + '=\'" + ' + name + ' + "\'')
        else:
            parts.append(name + '=" + ' + name)
    lines.append('        return "' + class_name + '{' + ', '.join(parts) + '}";')
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


def rebuild_file(file_path):
    content = read_file(file_path)
    original = content

    package, imports, class_name, is_enum, is_abstract, super_class, interfaces = extract_class_info(content)
    if not class_name:
        return False

    fields = extract_fields(content, class_name)
    lombok_info = detect_lombok_usage(content, class_name, fields)

    if not any(lombok_info.values()):
        return False

    annotations = extract_annotations(content, class_name)
    other_code = extract_other_methods_and_code(content, class_name, fields, lombok_info)

    generated_parts = []

    if lombok_info['has_slf4j']:
        generated_parts.append(generate_slf4j(class_name))

    if lombok_info['has_required_args']:
        constructor = generate_required_args_constructor(class_name, fields)
        if constructor:
            generated_parts.append(constructor)

    instance_fields = [f for f in fields if not f['is_static']]
    if lombok_info['has_data'] or lombok_info['has_getter']:
        for f in instance_fields:
            generated_parts.append(generate_getter(f))

    if lombok_info['has_data'] or lombok_info['has_setter']:
        non_final_instance_fields = [f for f in fields if not f['is_static'] and not f['is_final']]
        for f in non_final_instance_fields:
            generated_parts.append(generate_setter(f))

    if lombok_info['has_data']:
        generated_parts.append(generate_equals(class_name, fields, call_super=lombok_info['equals_call_super']))
        generated_parts.append(generate_hash_code(fields, call_super=lombok_info['equals_call_super']))
        generated_parts.append(generate_to_string(class_name, fields))

    if lombok_info['has_equals_hash_code']:
        generated_parts.append(generate_equals(class_name, fields, call_super=lombok_info['equals_call_super']))
        generated_parts.append(generate_hash_code(fields, call_super=lombok_info['equals_call_super']))

    field_lines = []
    for f in fields:
        modifiers = []
        if f['is_static']:
            modifiers.append('static')
        if f['is_final']:
            modifiers.append('final')

        mod_str = ' '.join(modifiers)
        if mod_str:
            mod_str = ' ' + mod_str

        field_str = '    private' + mod_str + ' ' + f['type'] + ' ' + f['name'] + ';'
        field_lines.append(field_str)

    has_serial_version_uid = 'private static final long serialVersionUID' in content
    if has_serial_version_uid:
        field_lines.insert(0, '    private static final long serialVersionUID = 1L;')

    class_decl_parts = []
    if 'public' in content.split('class ' + class_name)[0].split('\n')[-1] if 'class ' + class_name in content else False:
        class_decl_parts.append('public')
    if is_abstract:
        class_decl_parts.append('abstract')
    if is_enum:
        class_decl_parts.append('enum')
    else:
        class_decl_parts.append('class')
    class_decl_parts.append(class_name)

    if super_class:
        class_decl_parts.append('extends ' + super_class)
    if interfaces:
        class_decl_parts.append('implements ' + ', '.join(interfaces))

    class_declaration = ' '.join(class_decl_parts) + ' {'

    new_content = []
    if package:
        new_content.append('package ' + package + ';')
        new_content.append('')

    if imports:
        new_content.extend(imports)
        new_content.append('')

    if annotations:
        new_content.extend(annotations)

    new_content.append(class_declaration)
    new_content.append('')

    if field_lines:
        new_content.extend(field_lines)

    if generated_parts:
        new_content.append('')
        new_content.append('\n\n'.join(generated_parts))

    if other_code:
        cleaned_other = []
        for line in other_code:
            if line.strip() != '' or (cleaned_other and cleaned_other[-1].strip() != ''):
                cleaned_other.append(line.rstrip())
        if cleaned_other:
            new_content.append('')
            new_content.extend(cleaned_other)

    new_content.append('}')
    new_content.append('')

    final_content = '\n'.join(new_content)

    if final_content != original:
        write_file(file_path, final_content)
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
            if rebuild_file(f):
                modified_files.append(f)
                print('Rebuilt: ' + f)
        except Exception as e:
            print('Error rebuilding ' + f + ': ' + str(e))
            import traceback
            traceback.print_exc()

    print('\nTotal rebuilt: ' + str(len(modified_files)))
    return modified_files


if __name__ == '__main__':
    main()
