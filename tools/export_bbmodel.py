"""Экспорт костной модели оружия в Blockbench (.bbmodel, формат «Generic model») и в плоский список кубов.

python3 tools/export_bbmodel.py m416 out.bbmodel out_flat.json
"""
import base64, json, os, sys, uuid
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from geolib import Geo, T, M4, rot_xyz

A = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'battlegrounds')
SKIP = {'camera', 'idle_view', 'iron_view'}


def main(gun, out_bb, out_flat):
    geo = Geo.load(os.path.join(A, 'geo', gun + '.json'))
    d = geo.data
    tex_path = os.path.join(A, 'textures', 'geo', gun + '.png')
    tex = Image.open(tex_path).convert('RGB')
    tw, th = d['texture_size']
    sw = d['swatch']
    cols = tw // sw

    def uv(s):
        u0, v0 = (s % cols) * sw + 1, (s // cols) * sw + 1
        return [u0, v0, u0 + sw - 2, v0 + sw - 2]

    # ---------------- .bbmodel
    elements, groups = [], {}
    outliner = []
    for b in d['bones']:
        name = b['name']
        g = {'name': name, 'origin': b.get('pivot', [0, 0, 0]), 'rotation': b.get('rot', [0, 0, 0]),
             'uuid': str(uuid.uuid4()), 'export': True, 'isOpen': False, 'visibility': name not in SKIP,
             'children': []}
        groups[name] = g
        for i, c in enumerate(b['cubes']):
            e = {'name': f'{name}_{i}', 'type': 'cube', 'uuid': str(uuid.uuid4()), 'box_uv': False,
                 'from': c['f'], 'to': c['t'], 'origin': c.get('o', b.get('pivot', [0, 0, 0])),
                 'faces': {f: {'uv': uv(c['s']), 'texture': 0} for f in ('north', 'east', 'south', 'west', 'up', 'down')}}
            if 'r' in c:
                e['rotation'] = c['r']
            elements.append(e)
            g['children'].append(e['uuid'])
        parent = b.get('parent')
        if parent:
            groups[parent]['children'].append(g)
        else:
            outliner.append(g)
    src = 'data:image/png;base64,' + base64.b64encode(open(tex_path, 'rb').read()).decode()
    bb = {
        'meta': {'format_version': '4.5', 'model_format': 'free', 'box_uv': False},
        'name': gun, 'model_identifier': gun,
        'resolution': {'width': tw, 'height': th},
        'elements': elements, 'outliner': outliner,
        'textures': [{'path': '', 'name': gun + '.png', 'folder': '', 'namespace': 'battlegrounds',
                      'id': '0', 'particle': True, 'render_mode': 'default', 'visible': True,
                      'mode': 'bitmap', 'saved': False, 'uuid': str(uuid.uuid4()),
                      'width': tw, 'height': th, 'uv_width': tw, 'uv_height': th, 'source': src}],
    }
    json.dump(bb, open(out_bb, 'w'), separators=(',', ':'))

    # ---------------- плоский список для веб-просмотра (поза покоя)
    W = geo.world()
    flat = []
    for b in d['bones']:
        if b['name'] in SKIP:
            continue
        for c in b['cubes']:
            M = W[b['name']]
            if 'r' in c:
                o = c['o']
                M = M @ T(o) @ M4(rot_xyz(*c['r'])) @ T([-v for v in o])
            col = tex.getpixel((uv(c['s'])[0] + 2, uv(c['s'])[1] + 2))
            flat.append({'f': c['f'], 't': c['t'], 'm': [round(v, 5) for v in M.T.flatten().tolist()],
                         'c': '#%02x%02x%02x' % col, 'b': b['name']})
    # руки игрока так, как они стоят от первого лица
    arms = []
    for side in ('right', 'left'):
        n = side + 'hand'
        Wb = W[n] @ T(geo.bones[n]['pivot'])
        if side == 'right':
            G = [-6, 11, 0]; fr, to = (-8, 0, -2), (-4, 12, 2)
        else:
            G = [6, 11, 0]; fr, to = (4, 0, -2), (8, 12, 2)
        M = Wb @ M4(rot_xyz(0, 0, 180)) @ T([-v for v in G])
        arms.append({'f': list(fr), 't': [to[0], 9, to[2]], 'm': M.T.flatten().tolist(), 'c': '#4e6878'})
        arms.append({'f': [fr[0], 9, fr[2]], 't': list(to), 'm': M.T.flatten().tolist(), 'c': '#c49876'})
    views = {k: geo.locator(k)[:3, 3].tolist() for k in ('idle_view', 'iron_view')}
    json.dump({'cubes': flat, 'arms': arms, 'views': views}, open(out_flat, 'w'), separators=(',', ':'))
    print(len(elements), 'cubes ->', out_bb)


if __name__ == '__main__':
    main(*sys.argv[1:4])
