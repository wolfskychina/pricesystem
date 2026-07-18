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


def clean_file(file_path):
    content = read_file(file_path)
    original = content

    pattern = re.compile(r'(\n\s+@Override\n\s+@Override\n\s+\}\n\s+\})', re.MULTILINE)
    content = pattern.sub('', content)

    pattern2 = re.compile(r'(\n\s+@Override\n\s+@Override\n\s+\})', re.MULTILINE)
    content = pattern2.sub('', content)

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
        if clean_file(f):
            count += 1
            print('Cleaned: ' + f)
    print('\nTotal cleaned: ' + str(count))


if __name__ == '__main__':
    main()
