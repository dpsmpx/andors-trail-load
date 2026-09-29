#!/usr/bin/env python3
"""Static cross-reference check of Andor's Trail game content.

Usage: python3 audit/check_content.py [path/to/AndorsTrail]

Checks only what the engine actually loads (res/values/loadresources.xml),
and reports references that would resolve to nothing at runtime.
"""
import collections
import glob
import json
import os
import re
import struct
import zlib
import base64
import sys
import xml.etree.ElementTree as ET

ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'AndorsTrail')
RES = os.path.join(ROOT, 'res')
SRC = os.path.join(ROOT, 'app/src/main/java/com/gpl/rpg/AndorsTrail')

findings = collections.defaultdict(list)


def report(cat, msg):
    findings[cat].append(msg)


# ---------------------------------------------------------------- load lists
lr = ET.parse(os.path.join(RES, 'values/loadresources.xml')).getroot()
arrays = {}
for a in lr.findall('array'):
    arrays[a.get('name')] = [i.text.strip() for i in a.findall('item')]


def files_of(array):
    out = []
    for ref in arrays[array]:
        kind, name = ref[1:].split('/')
        out.append((kind, name))
    return out


# duplicate / unreferenced resource files
for name, refs in arrays.items():
    dup = [r for r, c in collections.Counter(refs).items() if c > 1]
    if dup:
        report('loadlist', f'{name}: listed more than once: {dup}')

listed_raw = {n for a, refs in arrays.items() if not a.endswith('_debug') for r in refs for k, n in [r[1:].split('/')] if k == 'raw'}
for f in sorted(glob.glob(os.path.join(RES, 'raw/*.json'))):
    n = os.path.splitext(os.path.basename(f))[0]
    if n not in listed_raw and not n.endswith('_debug'):
        report('loadlist', f'res/raw/{n}.json is not listed in any production load list (never loaded)')
listed_maps = [n for k, n in files_of('loadresource_maps')]
tmx_files = {os.path.splitext(os.path.basename(f))[0] for f in glob.glob(os.path.join(RES, 'xml/*.tmx'))}
for n in sorted(tmx_files - set(listed_maps)):
    if n not in ('template',) and not n.startswith('debug'):
        report('loadlist', f'res/xml/{n}.tmx is not in loadresource_maps (never loaded)')


def load_json(array):
    out = []
    for kind, name in files_of(array):
        path = os.path.join(RES, kind, name + '.json')
        with open(path, encoding='utf-8') as fh:
            for obj in json.load(fh):
                out.append((name, obj))
    return out


def index(entries, what):
    ids = {}
    for f, o in entries:
        i = o.get('id')
        if i in ids:
            report('duplicate-id', f'{what} "{i}" defined in {ids[i]} and {f} (later one wins)')
        ids[i] = f
    return ids


items = load_json('loadresource_items')
monsters = load_json('loadresource_monsters')
droplists = load_json('loadresource_droplists')
phrases = load_json('loadresource_conversationlists')
quests = load_json('loadresource_quests')
conditions = load_json('loadresource_actorconditions')
categories = load_json('loadresource_itemcategories')

item_ids = index(items, 'item')
filter_ids = {'#' + o['id'] for f, o in load_json('loadresource_itemfilters')}
monster_ids = index(monsters, 'monster')
droplist_ids = index(droplists, 'droplist')
phrase_ids = index(phrases, 'phrase')
quest_ids = index(quests, 'quest')
condition_ids = index(conditions, 'actor condition')
category_ids = index(categories, 'item category')
spawngroups = {(o.get('spawnGroup') or o['id']).lower() for f, o in monsters}
quest_stages = {o['id']: {s['progress'] for s in o.get('stages', [])} for f, o in quests}

src_text = open(os.path.join(SRC, 'model/ability/SkillCollection.java'), encoding='utf-8').read()
_body = src_text[src_text.index('{', src_text.index('enum SkillID')) + 1:src_text.index('}', src_text.index('enum SkillID'))]
_body = re.sub(r'//[^\n]*', '', _body)
skill_ids = {t.strip() for t in _body.split(',') if t.strip()}
ltm = open(os.path.join(SRC, 'model/map/LayeredTileMap.java'), encoding='utf-8').read()
cf = ltm[ltm.index('enum ColorFilterId'):]
color_filters = set(re.findall(r'\b(\w+)\b', cf[cf.index('{') + 1:cf.index('}')]))

# ---------------------------------------------------------------- tilesets
rl = open(os.path.join(SRC, 'resource/ResourceLoader.java'), encoding='utf-8').read()
sizes = {'sz1x1': (1, 1), 'sz2x1': (2, 1), 'sz2x2': (2, 2), 'sz7x1': (7, 1), 'sz20x12': (20, 12), 'sz32x8': (32, 8), 'sz8x3': (8, 3), 'sz16x8': (16, 8), 'sz16x10': (16, 10), 'sz16x20': (16, 20), 'sz1x2': (1, 2)}
for m in re.finditer(r'final Size (sz\w+) = new Size\((\d+),\s*(\d+)\)', rl):
    sizes[m.group(1)] = (int(m.group(2)), int(m.group(3)))
tilesets = {}
for m in re.finditer(r'prepareTileset\(R\.drawable\.(\w+),\s*"(\w+)",\s*(new Size\((\d+),\s*(\d+)\)|\w+),\s*(new Size\((\d+),\s*(\d+)\)|\w+)', rl):
    grid = (int(m.group(4)), int(m.group(5))) if m.group(4) else sizes.get(m.group(3))
    sprite = (int(m.group(7)), int(m.group(8))) if m.group(7) else sizes.get(m.group(6))
    tilesets[m.group(2)] = (m.group(1), grid, sprite)


def png_size(name):
    for d in ('drawable', 'drawable-nodpi'):
        p = os.path.join(RES, d, name + '.png')
        if os.path.exists(p):
            with open(p, 'rb') as fh:
                head = fh.read(24)
            return struct.unpack('>II', head[16:24])
    return None


for ts, (drawable, grid, sprite) in sorted(tilesets.items()):
    if grid is None or sprite is None:
        continue
    size = png_size(drawable)
    if size is None:
        report('tileset', f'tileset {ts}: drawable {drawable}.png not found')
        continue
    ew, eh = grid[0] * sprite[0] * 32, grid[1] * sprite[1] * 32
    if size[0] < ew or size[1] < eh:
        report('tileset', f'tileset {ts}: {drawable}.png is {size[0]}x{size[1]}, grid needs {ew}x{eh}')


def check_icon(icon, where):
    if not icon:
        return
    if ':' not in icon:
        report('icon', f'{where}: malformed iconID "{icon}"')
        return
    ts, idx = icon.split(':', 1)
    if ts not in tilesets:
        report('icon', f'{where}: iconID "{icon}" uses unknown tileset "{ts}"')
        return
    grid = tilesets[ts][1]
    if grid and int(idx) >= grid[0] * grid[1]:
        report('icon', f'{where}: iconID "{icon}" is outside the {grid[0]}x{grid[1]} grid of "{ts}"')


# ---------------------------------------------------------------- items/monsters/droplists
def check_effect_conditions(effect, where):
    if not isinstance(effect, dict):
        return
    for key in ('addedConditions', 'conditionsSource', 'conditionsTarget'):
        for c in effect.get(key, []) or []:
            if c.get('condition') not in condition_ids:
                report('missing-ref', f'{where}: unknown actor condition "{c.get("condition")}"')


for f, o in items:
    w = f'item {o["id"]} ({f})'
    if o.get('category') and o['category'] not in category_ids:
        report('missing-ref', f'{w}: unknown category "{o["category"]}"')
    check_icon(o.get('iconID'), w)
    for k, v in o.items():
        if k.endswith('Effect'):
            check_effect_conditions(v, w)

for f, o in monsters:
    w = f'monster {o["id"]} ({f})'
    if o.get('droplistID') and o['droplistID'] not in droplist_ids:
        report('missing-ref', f'{w}: unknown droplist "{o["droplistID"]}"')
    if o.get('phraseID') and o['phraseID'] not in phrase_ids:
        report('missing-ref', f'{w}: unknown phrase "{o["phraseID"]}"')
    check_icon(o.get('iconID'), w)
    for k, v in o.items():
        if k.endswith('Effect'):
            check_effect_conditions(v, w)

for f, o in droplists:
    for it in o.get('items', []):
        if it.get('itemID') not in item_ids:
            report('missing-ref', f'droplist {o["id"]} ({f}): unknown item "{it.get("itemID")}"')
        q = it.get('quantity', {})
        if q and int(q.get('min', 0)) > int(q.get('max', 0)):
            report('data', f'droplist {o["id"]} ({f}): quantity min > max for {it.get("itemID")}')

for f, o in conditions:
    check_icon(o.get('iconID'), f'actor condition {o["id"]} ({f})')

# ---------------------------------------------------------------- maps
maps = {}
for name in listed_maps:
    path = os.path.join(RES, 'xml', name + '.tmx')
    if not os.path.exists(path):
        report('missing-ref', f'loadresource_maps: {name}.tmx does not exist')
        continue
    root = ET.parse(path).getroot()
    layers = {l.get('name') for l in root.findall('layer')}
    blocked = None
    for l in root.findall('layer'):
        if l.get('name', '').lower() == 'walkable':
            d = l.find('data')
            raw = zlib.decompress(base64.b64decode(d.text.strip()))
            blocked = [struct.unpack_from('<I', raw, 4 * k)[0] != 0 for k in range(len(raw) // 4)]
    objs = []
    groups = set()
    for g in root.findall('objectgroup'):
        groups.add(g.get('name'))
        for o in g.findall('object'):
            props = {p.get('name'): p.get('value') for p in o.findall('properties/property')}
            objs.append((g.get('name'), o.get('type'), o.get('name'), props, o))
    maps[name] = dict(layers=layers, objs=objs, groups=groups,
                      places={n for g, t, n, p, o in objs if t == 'mapchange'},
                      spawns=[n for g, t, n, p, o in objs if t == 'spawn'],
                      w=int(root.get('width')), h=int(root.get('height')), blocked=blocked,
                      inactive={g.get('name') for g in root.findall('objectgroup') if any(p.get('name') == 'active' and p.get('value') == 'false' for p in g.findall('properties/property'))})

timers_created = set()
quest_set = set()
for f, o in phrases:
    for r in o.get('rewards', []) or []:
        if r.get('rewardType') == 'questProgress':
            quest_set.add((r.get('rewardID'), int(r.get('value') or 0)))
factions_set = {o.get('faction') for f, o in monsters if o.get('faction')}
for f, o in phrases:
    for r in o.get('rewards', []) or []:
        if r.get('rewardType') == 'createTimer':
            timers_created.add(r.get('rewardID'))
        if r.get('rewardType', '').startswith('alignment'):
            factions_set.add(r.get('rewardID'))


def check_requirement(rt, rid, value, where, negate=False):
    if rt in ('questProgress', 'questLatestProgress'):
        if not negate and value is not None and str(value).lstrip('-').isdigit() and (rid, int(value)) not in quest_set:
            report('never-set', f'{where}: requirement {rt} {rid}:{value} is never set by any reward')
    elif rt in ('inventoryRemove', 'inventoryKeep', 'wear', 'wearRemove', 'usedItem'):
        if rid not in item_ids and rid not in filter_ids:
            report('missing-ref', f'{where}: requirement {rt} on unknown item "{rid}"')
    elif rt == 'killedMonster':
        if rid not in monster_ids:
            report('missing-ref', f'{where}: requirement killedMonster on unknown monster "{rid}"')
    elif rt == 'hasActorCondition':
        if rid not in condition_ids:
            report('missing-ref', f'{where}: requirement hasActorCondition on unknown condition "{rid}"')
    elif rt in ('skillLevel', 'skillIncrease'):
        if rid not in skill_ids:
            report('missing-ref', f'{where}: requirement {rt} on unknown skill "{rid}" (SkillID.valueOf would throw)')
    elif rt == 'timerElapsed':
        if rid not in timers_created:
            report('missing-ref', f'{where}: timerElapsed on timer "{rid}" that no reward ever creates')
    elif rt in ('factionScore', 'factionScoreEquals'):
        if rid not in factions_set:
            report('suspicious', f'{where}: {rt} on faction "{rid}" that no monster or reward uses')


for name, m in maps.items():
    names_seen = collections.Counter(n for g, t, n, p, o in m['objs'] if t == 'spawn')
    for n, c in names_seen.items():
        if c > 1:
            report('map', f'map {name}: spawn area name "{n}" used {c} times (must be unique, used by scripts and savegames)')
    for g, t, n, p, o in m['objs']:
        w = f'map {name} object "{n}" ({t})'
        x, y = int(float(o.get('x', 0))), int(float(o.get('y', 0)))
        ow, oh = int(float(o.get('width', 0))), int(float(o.get('height', 0)))
        if x < 0 or y < 0 or x + ow > m['w'] * 32 or y + oh > m['h'] * 32:
            report('map', f'{w}: area {x},{y} {ow}x{oh} extends outside the {m["w"]}x{m["h"]} map')
        if t == 'mapchange' and p.get('map'):
            target = p['map']
            if target not in maps:
                walkable = True
                if m['blocked'] is not None:
                    tiles = [(tx, ty) for tx in range(x // 32, (x + ow) // 32) for ty in range(y // 32, (y + oh) // 32)]
                    walkable = any(0 <= tx < m['w'] and 0 <= ty < m['h'] and not m['blocked'][ty * m['w'] + tx] for tx, ty in tiles)
                if g in m['inactive']:
                    report('dead-exit', f'{w}: target map "{target}" does not exist (object group "{g}" is inactive until a script enables it)')
                elif walkable:
                    report('missing-ref', f'{w}: target map "{target}" does not exist, and the exit is on walkable tiles')
                else:
                    report('dead-exit', f'{w}: target map "{target}" does not exist (exit is walled off)')
            elif p.get('place') and p['place'] not in maps[target]['places']:
                report('missing-ref', f'{w}: target place "{p["place"]}" does not exist in map {target}')
        elif t == 'container':
            if n not in droplist_ids:
                report('missing-ref', f'{w}: container droplist "{n}" does not exist')
        elif t == 'key':
            if p.get('phrase') and p['phrase'] not in phrase_ids:
                report('missing-ref', f'{w}: key phrase "{p["phrase"]}" does not exist')
        elif t in ('script', 'sign'):
            if n not in phrase_ids:
                report('missing-ref', f'{w}: {t} phrase "{n}" does not exist')
        elif t == 'spawn':
            sg = p.get('spawngroup')
            if sg and sg.lower() not in spawngroups and sg not in monster_ids:
                report('missing-ref', f'{w}: spawngroup "{sg}" matches no monster spawnGroup or id (area is dropped at load)')
        elif t == 'replace':
            for layer in ('Ground', 'Objects', 'Above', 'Walkable', 'Top', 'Base'):
                if p.get(layer) and p[layer] not in m['layers']:
                    report('missing-ref', f'{w}: replacement layer "{p[layer]}" does not exist')
        if 'requireType' in p:
            check_requirement(p['requireType'], p.get('requireId'), p.get('requireValue'), w, p.get('requireNegation') == 'true')

# ---------------------------------------------------------------- conversations
referenced_phrases = set()
for f, o in monsters:
    if o.get('phraseID'):
        referenced_phrases.add(o['phraseID'])
for name, m in maps.items():
    for g, t, n, p, o in m['objs']:
        if t in ('script', 'sign'):
            referenced_phrases.add(n)
        if t == 'key' and p.get('phrase'):
            referenced_phrases.add(p['phrase'])

special = {'X', 'S', 'F', 'R'}
for f, o in phrases:
    w = f'phrase {o["id"]} ({f})'
    if o.get('switchToNPC') and o['switchToNPC'] not in monster_ids:
        report('missing-ref', f'{w}: switchToNPC "{o["switchToNPC"]}" is not a monster')
    for rep in o.get('replies', []) or []:
        nxt = rep.get('nextPhraseID')
        if nxt:
            referenced_phrases.add(nxt)
            if nxt not in special and nxt not in phrase_ids:
                report('missing-ref', f'{w}: reply leads to unknown phrase "{nxt}"')
        for q in rep.get('requires', []) or []:
            check_requirement(q.get('requireType'), q.get('requireID'), q.get('value'), w, bool(q.get('negate')))
    for r in o.get('rewards', []) or []:
        rt, rid, val, mp = r.get('rewardType'), r.get('rewardID'), r.get('value'), r.get('mapName')
        if rt == 'questProgress' and rid in quest_ids and val is not None and int(val) not in quest_stages[rid] and quest_ids.get(rid):
            report('quest-log', f'{w}: questProgress {rid}:{val} has no log stage in quest "{rid}" (no log text, no XP)')
        elif rt == 'dropList' and rid not in droplist_ids:
            report('missing-ref', f'{w}: dropList reward "{rid}" does not exist')
        elif rt == 'giveItem' and rid not in item_ids and rid not in filter_ids:
            report('missing-ref', f'{w}: giveItem reward "{rid}" does not exist')
        elif rt in ('actorCondition', 'actorConditionImmunity') and rid not in condition_ids:
            report('missing-ref', f'{w}: {rt} reward "{rid}" does not exist')
        elif rt == 'skillIncrease' and rid not in skill_ids:
            report('missing-ref', f'{w}: skillIncrease "{rid}" is not a SkillID (SkillID.valueOf would throw)')
        elif rt == 'setNextPhraseID':
            referenced_phrases.add(rid)
            if rid not in phrase_ids and rid not in special:
                report('missing-ref', f'{w}: setNextPhraseID "{rid}" does not exist')
        elif rt in ('spawnAll', 'removeSpawnArea', 'deactivateSpawnArea', 'activateMapObjectGroup', 'deactivateMapObjectGroup', 'changeMapFilter', 'mapchange'):
            if mp is not None and mp not in maps:
                report('missing-ref', f'{w}: {rt} on unknown map "{mp}" (findPredefinedMap returns null -> NullPointerException)')
                continue
            cands = [maps[mp]] if mp else list(maps.values())
            if rt in ('spawnAll', 'removeSpawnArea', 'deactivateSpawnArea'):
                if not any(rid in c['spawns'] for c in cands):
                    report('missing-ref', f'{w}: {rt} "{rid}" matches no spawn area' + (f' in map {mp}' if mp else ' in any map'))
            elif rt in ('activateMapObjectGroup', 'deactivateMapObjectGroup'):
                if not any(rid in c['groups'] for c in cands):
                    report('missing-ref', f'{w}: {rt} "{rid}" matches no object group' + (f' in map {mp}' if mp else ' in any map'))
            elif rt == 'changeMapFilter':
                if rid not in color_filters:
                    report('missing-ref', f'{w}: changeMapFilter "{rid}" is not a color filter')
            elif rt == 'mapchange':
                if not any(rid in c['places'] for c in cands):
                    report('missing-ref', f'{w}: mapchange to place "{rid}"' + (f' which does not exist in {mp}' if mp else ' without map'))
        elif rt == 'changeIcon' and int(val or 0) not in (0, 1, 2, 10, 11, 999):
            report('missing-ref', f'{w}: changeIcon value {val} is not handled')

orphans = [i for i in phrase_ids if i not in referenced_phrases]
print('Spawn areas in loaded maps:', sum(len(m['spawns']) for m in maps.values()))

# ---------------------------------------------------------------- worldmap
wm = ET.parse(os.path.join(RES, 'xml/worldmap.xml')).getroot()
seen = {}
for seg in wm.findall('segment'):
    for mm in seg.findall('map'):
        n = mm.get('id')
        if n not in maps:
            report('worldmap', f'worldmap segment {seg.get("id")}: map "{n}" is not loaded')
        if n in seen and seen[n] != seg.get('id'):
            report('worldmap', f'map "{n}" is in segments {seen[n]} and {seg.get("id")}')
        seen[n] = seg.get('id')
    areas = {a.get('id') for a in seg.findall('namedarea')}
    for mm in seg.findall('map'):
        if mm.get('area') and mm.get('area') not in areas:
            report('worldmap', f'worldmap segment {seg.get("id")}: map {mm.get("id")} uses undefined named area "{mm.get("area")}"')

# ---------------------------------------------------------------- output
print(f'Loaded: {len(item_ids)} items, {len(monster_ids)} monsters, {len(droplist_ids)} droplists, '
      f'{len(phrase_ids)} phrases, {len(quest_ids)} quests, {len(condition_ids)} actor conditions, {len(maps)} maps, {len(tilesets)} tilesets')
print(f'Phrases never referenced from content (may be unused): {len(orphans)}')
for cat in sorted(findings):
    print(f'\n## {cat}: {len(findings[cat])}')
    for msg in findings[cat]:
        print('  - ' + msg)
