#!/usr/bin/env python3
"""Builds world map test pages the way the app does, for the browser tests in scenarios.js.

The page is built from the real template (AndorsTrail/res/values/worldmap_template.xml) after
applying the same processing as aapt2 applies to string resources, and from the real layout of
the "world1" segment (AndorsTrail/res/xml/worldmap.xml and the .tmx map sizes). The markup of the
map images mirrors WorldMapController.getWorldMapSegmentAsHtml. Map images are generated solid
PNGs of the real sizes (8 pixels per tile).

Usage:
  build_pages.py <out-dir> [--template FILE] [--markup lazy|eager]

Creates one directory per scenario below <out-dir>:
  full/            all 546 maps of world1 visited
  new_game/        only Crossglen visited
  missing_images/  all maps visited, every 10th image file missing
  broken_script/   the page script contains a syntax error
Each directory contains worldmap_world1.html, the PNG files and scenario.json.
"""
import argparse
import html
import json
import os
import struct
import xml.etree.ElementTree as ET
import zlib

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
RES = os.path.join(REPO, 'AndorsTrail', 'res')
TILE = 8  # WorldMapController.WORLDMAP_DISPLAY_TILESIZE
SEGMENT = 'world1'


def aapt2_string(raw):
    """Same text processing as aapt2 (ResourceUtils.cpp, StringBuilder::AppendText)."""
    out, quoted, last_space, i = [], False, False, 0
    while i < len(raw):
        c = raw[i]
        if not quoted and c.isspace():
            if not last_space:
                out.append(' ')
            last_space = True
            i += 1
            continue
        last_space = False
        if c == '\\' and i + 1 < len(raw):
            e = raw[i + 1]
            out.append({'n': '\n', 't': '\t'}.get(e, e))
            i += 2
            continue
        if c == '"':
            quoted = not quoted
        elif not quoted and c == "'":
            raise ValueError('unescaped apostrophe outside of quotes')
        else:
            out.append(c)
        i += 1
    return ''.join(out).strip()


def load_template(path):
    root = ET.parse(path).getroot()
    for s in root.findall('string'):
        if s.get('name') == 'worldmap_template':
            return aapt2_string(''.join(s.itertext()))
    raise SystemExit('worldmap_template not found in ' + path)


def png(width, height, rgb):
    raw = b''.join(b'\x00' + bytes(rgb) * width for _ in range(height))

    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw, 6)) + chunk(b'IEND', b''))


def segment_maps(visited):
    wm = ET.parse(os.path.join(RES, 'xml', 'worldmap.xml')).getroot()
    seg = next(s for s in wm.findall('segment') if s.get('id') == SEGMENT)
    names = {a.get('id'): (a.get('name'), a.get('type')) for a in seg.findall('namedarea')}
    maps = []
    for m in seg.findall('map'):
        if visited is not None and m.get('id') not in visited:
            continue
        tmx = ET.parse(os.path.join(RES, 'xml', m.get('id') + '.tmx')).getroot()
        maps.append(dict(id=m.get('id'), x=int(m.get('x')), y=int(m.get('y')), area=m.get('area'),
                         w=int(tmx.get('width')), h=int(tmx.get('height'))))
    return maps, names


def build(template, maps, names, markup):
    ox, oy = min(m['x'] for m in maps), min(m['y'] for m in maps)
    size_x = (max(m['x'] + m['w'] for m in maps) - ox) * TILE
    size_y = (max(m['y'] + m['h'] for m in maps) - oy) * TILE
    attr = 'data-src' if markup == 'lazy' else 'src'
    extra = ' decoding="async"' if markup == 'lazy' else ''
    images = ''.join(
        f'<img {attr}="{m["id"]}.png" id="{m["id"]}" style="width:{m["w"] * TILE}px; height:{m["h"] * TILE}px; '
        f'left:{(m["x"] - ox) * TILE}px; top:{(m["y"] - oy) * TILE}px;"{extra} />' for m in maps)
    areas = {}
    for m in maps:
        if m['area']:
            areas.setdefault(m['area'], []).append(m)
    area_html = ''
    for area, ms in areas.items():
        left, top = min(m['x'] for m in ms), min(m['y'] for m in ms)
        right, bottom = max(m['x'] + m['w'] for m in ms), max(m['y'] + m['h'] for m in ms)
        name, kind = names.get(area, (area, 'other'))
        area_html += (f'<div class="namedarea {kind}" style="width:{(right - left) * TILE}px; line-height:{(bottom - top) * TILE}px; '
                      f'left:{(left - ox) * TILE}px; top:{(top - oy) * TILE}px;"><span>{html.escape(name, quote=False)}</span></div>')
    page = (template.replace('{{maps}}', images).replace('{{areas}}', area_html)
            .replace('{{sizex}}', str(size_x)).replace('{{sizey}}', str(size_y))
            .replace('{{offsetx}}', str(ox * TILE)).replace('{{offsety}}', str(oy * TILE)))
    return page, (ox, oy)


def player_query(maps, map_id, tile_x, tile_y):
    """The query string DisplayWorldMapActivity.update() appends to the page URL."""
    m = next(m for m in maps if m['id'] == map_id)
    return f'{(tile_x + m["x"]) * TILE},{(tile_y + m["y"] - 1) * TILE}'


def write_scenario(out_dir, name, template, markup, visited, player, missing_every=0, broken_script=False):
    directory = os.path.join(out_dir, name)
    os.makedirs(directory, exist_ok=True)
    maps, names = segment_maps(visited)
    if broken_script:
        template = template.replace('var player = ', 'var player = syntax error here; var unused = ', 1)
    page, _ = build(template, maps, names, markup)
    with open(os.path.join(directory, 'worldmap_' + SEGMENT + '.html'), 'w', encoding='utf-8') as fh:
        fh.write(page)
    missing = []
    for i, m in enumerate(maps):
        if missing_every and i % missing_every == 0:
            missing.append(m['id'])
            continue
        color = (40 + (i * 37) % 160, 80 + (i * 53) % 120, 40 + (i * 29) % 100)
        with open(os.path.join(directory, m['id'] + '.png'), 'wb') as fh:
            fh.write(png(m['w'] * TILE, m['h'] * TILE, color))
    with open(os.path.join(directory, 'scenario.json'), 'w') as fh:
        json.dump({'maps': len(maps), 'query': player_query(maps, *player), 'missing': missing}, fh)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('out_dir')
    parser.add_argument('--template', default=os.path.join(RES, 'values', 'worldmap_template.xml'))
    parser.add_argument('--markup', choices=['lazy', 'eager'], default='lazy')
    args = parser.parse_args()
    template = load_template(args.template)
    fallhaven = ('fallhaven_ne', 15, 15)
    write_scenario(args.out_dir, 'full', template, args.markup, None, fallhaven)
    write_scenario(args.out_dir, 'new_game', template, args.markup, {'crossglen'}, ('crossglen', 10, 10))
    write_scenario(args.out_dir, 'missing_images', template, args.markup, None, fallhaven, missing_every=10)
    write_scenario(args.out_dir, 'broken_script', template, args.markup, None, fallhaven, broken_script=True)


if __name__ == '__main__':
    main()
