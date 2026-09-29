#!/usr/bin/env python3
"""Compares format specifiers of every translated string with the default strings.xml.

Usage: python3 audit/check_translation_formats.py [path/to/AndorsTrail]

A translation that uses a conversion or an argument index the code does not pass
throws at runtime (IllegalFormatConversionException, MissingFormatArgumentException,
UnknownFormatConversionException) when the string is formatted.
"""
import collections
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'AndorsTrail')
RES = os.path.join(ROOT, 'res')
SRC = os.path.join(ROOT, 'app/src/main/java')

SPEC = re.compile(r'%(?:(\d+)\$)?([-#+ 0,(<]*)?(\d+)?(?:\.(\d+))?([a-zA-Z%])')


def text_of(el):
    return ''.join(el.itertext())


def unescape(s):
    # aapt2 rules that matter here: \" \' \\ \n \t, and quote toggling
    out, i, quote = [], 0, False
    while i < len(s):
        c = s[i]
        if c == '\\' and i + 1 < len(s):
            n = s[i + 1]
            out.append({'n': '\n', 't': '\t'}.get(n, n))
            i += 2
            continue
        if c == '"':
            quote = not quote
            i += 1
            continue
        out.append(c)
        i += 1
    return ''.join(out)


def specs(value):
    """Returns ({argIndex: conversion}, [problems])."""
    res, problems, auto = {}, [], 0
    for m in SPEC.finditer(value):
        idx, flags, width, prec, conv = m.groups()
        if conv == '%':
            continue
        if conv == 'n':
            continue
        if conv not in 'sSdfeExXgGoOcCbBhH':
            problems.append(f'invalid conversion "%{conv}"')
            continue
        if idx is None:
            auto += 1
            i = auto
        else:
            i = int(idx)
        c = conv.lower()
        c = {'x': 'd', 'o': 'd', 'e': 'f', 'g': 'f'}.get(c, c)
        res.setdefault(i, set()).add(c)
    return res, problems


def load(path):
    out = {}
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as e:
        return None
    for s in root.findall('string'):
        if s.get('formatted') == 'false':
            continue
        out[s.get('name')] = unescape(text_of(s))
    for p in root.findall('plurals'):
        for it in p.findall('item'):
            out[f'{p.get("name")}#{it.get("quantity")}'] = unescape(text_of(it))
    return out


# strings that the code formats with arguments
java = ''
for f in glob.glob(os.path.join(SRC, '**/*.java'), recursive=True):
    java += open(f, encoding='utf-8').read()
formatted_in_code = set(re.findall(r'R\.string\.(\w+)\s*,', java)) | set(re.findall(r'getQuantityString\(\s*R\.plurals\.(\w+)', java))

default = load(os.path.join(RES, 'values/strings.xml'))
default_specs = {k: specs(v)[0] for k, v in default.items()}

crash, missing = collections.defaultdict(list), collections.defaultdict(list)
for d in sorted(glob.glob(os.path.join(RES, 'values-*'))):
    path = os.path.join(d, 'strings.xml')
    if not os.path.exists(path):
        continue
    lang = os.path.basename(d)[7:]
    tr = load(path)
    if tr is None:
        crash['<parse>'].append(f'{lang}: strings.xml is not well-formed XML')
        continue
    for key, value in tr.items():
        base = key.split('#')[0]
        if key not in default and base not in default and not any(k.startswith(base + '#') for k in default):
            continue
        dspec = default_specs.get(key) or default_specs.get(next((k for k in default if k.startswith(base + '#')), ''), {})
        tspec, problems = specs(value)
        is_formatted = base in formatted_in_code or bool(dspec)
        if not is_formatted:
            continue
        for p in problems:
            crash[key].append(f'{lang}: {p}: {value!r}')
        for i, convs in tspec.items():
            if i not in dspec:
                crash[key].append(f'{lang}: argument {i} is not passed (default has {sorted(dspec)}): {value!r}')
            elif not convs <= dspec[i] and not ('s' in convs and len(convs) == 1):
                crash[key].append(f'{lang}: argument {i} uses %{"/%".join(sorted(convs))} but default uses %{"/%".join(sorted(dspec[i]))}: {value!r}')
        for i in dspec:
            if i not in tspec and '#' not in key:
                missing[key].append(lang)

print(f'Strings formatted with arguments in code: {len(formatted_in_code)}')
print(f'\n## Translations that throw when formatted: {sum(len(v) for v in crash.values())} in {len(crash)} strings')
for k in sorted(crash):
    for m in crash[k]:
        print(f'  - {k}: {m}')
print(f'\n## Translations that drop an argument (no crash, value not shown): {sum(len(v) for v in missing.values())} in {len(missing)} strings')
for k in sorted(missing):
    print(f'  - {k}: {", ".join(missing[k])}')
