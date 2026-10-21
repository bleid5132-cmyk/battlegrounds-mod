"""Экспорт костной модели оружия с анимациями:
  * в Blockbench (.bbmodel, формат «Generic model») — группы по костям, руки игрока, все анимации;
  * в данные для веб-просмотра (кости, кубы, анимации, точки камеры).

python3 tools/export_bbmodel.py m416 out.bbmodel out_viewer.json
"""
import base64, json, os, sys, uuid
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from geolib import Geo, rot_xyz, euler_from_matrix

A = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'battlegrounds')
SKIP = {'idle_view', 'iron_view'}
NAMES_RU = {
    'draw': 'Доставание', 'put_away': 'Убирание', 'shoot': 'Выстрел', 'idle': 'Покой', 'walk': 'Ходьба',
    'walk_aiming': 'Ходьба в прицеле', 'run_start': 'Бег: старт', 'run': 'Бег', 'run_end': 'Бег: остановка',
    'ads_up': 'Подъём к прицелу', 'reload_tactical': 'Перезарядка', 'reload_empty': 'Перезарядка пустого',
}
# рука Minecraft 4x12x4: точка хвата G и куб (рукав до y=9, дальше кисть)
ARM = {'righthand': ([-6, 11, 0], (-8, 0, -2), (-4, 12, 2)), 'lefthand': ([6, 11, 0], (4, 0, -2), (8, 12, 2))}
SLEEVE, SKIN = '#4e6878', '#c49876'


def uid():
    return str(uuid.uuid4())


def arm_cubes(bone):
    """Кубы руки в координатах модели (без учёта поворота кости), как их рисует движок:
    T(pivot) * S(1,s,1) * Rz180 * T(-G). Возвращает [(from, to, цвет)] и точку поворота."""
    G, fr, to = ARM[bone['name']]
    p = np.array(bone['pivot'], float)
    s = bone.get('scale', [1, 1, 1])[1]
    out = []
    for (y0, y1, col) in ((fr[1], 9, SLEEVE), (9, to[1], SKIN)):
        # растяжение по длине руки вокруг точки хвата (до поворота на 180°)
        ys0 = G[1] - (G[1] - y0) * s
        ys1 = G[1] - (G[1] - y1) * s
        f = np.array([fr[0], ys0, fr[2]], float) + p - G
        t = np.array([to[0], ys1, to[2]], float) + p - G
        out.append((f.tolist(), t.tolist(), col))
    return out


def bb_rot(rest, anim):
    """Наш поворот R(rest)*R(anim) -> добавка к повороту группы в Blockbench (x и y там с обратным знаком)."""
    total = euler_from_matrix(rot_xyz(*rest) @ rot_xyz(*anim))
    d = [total[i] - rest[i] for i in range(3)]
    return [-d[0], -d[1], d[2]]


def main(gun, out_bb, out_view):
    geo = Geo.load(os.path.join(A, 'geo', gun + '.json'))
    d = geo.data
    anims = json.load(open(os.path.join(A, 'animations', gun + '.json')))['animations']
    tex_path = os.path.join(A, 'textures', 'geo', gun + '.png')
    tex = Image.open(tex_path).convert('RGB')
    tw, th = d['texture_size']
    sw = d['swatch']
    cols = tw // sw

    def uv(s):
        u0, v0 = (s % cols) * sw + 1, (s // cols) * sw + 1
        return [u0, v0, u0 + sw - 2, v0 + sw - 2]

    def color(s):
        return '#%02x%02x%02x' % tex.getpixel((uv(s)[0] + 2, uv(s)[1] + 2))

    # ================================================================ Blockbench
    elements, groups, outliner = [], {}, []
    skin_tex = 1
    for b in d['bones']:
        name = b['name']
        g = {'name': name, 'origin': b.get('pivot', [0, 0, 0]), 'rotation': b.get('rot', [0, 0, 0]),
             'uuid': uid(), 'export': True, 'isOpen': False, 'visibility': name not in SKIP, 'children': []}
        groups[name] = g
        for i, c in enumerate(b['cubes']):
            e = {'name': f'{name}_{i}', 'type': 'cube', 'uuid': uid(), 'box_uv': False,
                 'from': c['f'], 'to': c['t'], 'origin': c.get('o', b.get('pivot', [0, 0, 0])),
                 'faces': {f: {'uv': uv(c['s']), 'texture': 0} for f in ('north', 'east', 'south', 'west', 'up', 'down')}}
            if 'r' in c:
                e['rotation'] = c['r']
            elements.append(e)
            g['children'].append(e['uuid'])
        if name in ARM:
            for j, (f, t, col) in enumerate(arm_cubes(b)):
                e = {'name': f'{name}_arm_{j}', 'type': 'cube', 'uuid': uid(), 'box_uv': False,
                     'from': f, 'to': t, 'origin': b['pivot'], 'rotation': [0, 0, 180],
                     'faces': {fc: {'uv': [0, 0, 1, 1] if col == SLEEVE else [2, 0, 3, 1], 'texture': skin_tex}
                               for fc in ('north', 'east', 'south', 'west', 'up', 'down')}}
                elements.append(e)
                g['children'].append(e['uuid'])
        parent = b.get('parent')
        if parent:
            groups[parent]['children'].append(g)
        else:
            outliner.append(g)

    # маленькая текстура для рук: рукав и кожа
    arm_img = Image.new('RGBA', (4, 1))
    arm_img.putpixel((0, 0), (78, 104, 120, 255)); arm_img.putpixel((1, 0), (78, 104, 120, 255))
    arm_img.putpixel((2, 0), (196, 152, 118, 255)); arm_img.putpixel((3, 0), (196, 152, 118, 255))
    arm_path = os.path.join(os.path.dirname(out_bb), '_arm.png')
    arm_img.save(arm_path)

    def texture(path, name, i, w, h):
        return {'path': '', 'name': name, 'folder': '', 'namespace': 'battlegrounds', 'id': str(i),
                'particle': i == 0, 'render_mode': 'default', 'visible': True, 'mode': 'bitmap', 'saved': False,
                'uuid': uid(), 'width': w, 'height': h, 'uv_width': w, 'uv_height': h,
                'source': 'data:image/png;base64,' + base64.b64encode(open(path, 'rb').read()).decode()}

    bb_anims = []
    for key, an in anims.items():
        animators = {}
        for bone, ch in an['bones'].items():
            if bone not in groups:
                continue
            rest = groups[bone]['rotation']
            kfs = []
            for chan, src in (('rotation', 'rot'), ('position', 'pos'), ('scale', 'scale')):
                for k in ch.get(src, []):
                    t, v, mode = k[0], k[1:4], (k[4] if len(k) > 4 else 'l')
                    if chan == 'rotation':
                        v = bb_rot(rest, v)
                    elif chan == 'position':
                        v = [-v[0], v[1], v[2]]
                    kfs.append({'channel': chan, 'data_points': [{'x': round(v[0], 4), 'y': round(v[1], 4), 'z': round(v[2], 4)}],
                                'uuid': uid(), 'time': t, 'color': -1,
                                'interpolation': 'catmullrom' if mode == 'c' else 'linear'})
            animators[groups[bone]['uuid']] = {'name': bone, 'type': 'bone', 'keyframes': kfs}
        bb_anims.append({'uuid': uid(), 'name': f'{key} — {NAMES_RU.get(key, key)}',
                         'loop': 'loop' if an.get('loop') else 'once', 'override': False,
                         'length': an['length'], 'snapping': 20, 'selected': False,
                         'anim_time_update': '', 'blend_weight': '', 'start_delay': '', 'loop_delay': '',
                         'animators': animators})
    bb = {
        'meta': {'format_version': '4.5', 'model_format': 'free', 'box_uv': False},
        'name': gun, 'model_identifier': gun,
        'resolution': {'width': tw, 'height': th},
        'elements': elements, 'outliner': outliner,
        'textures': [texture(tex_path, gun + '.png', 0, tw, th), texture(arm_path, 'arm.png', 1, 4, 1)],
        'animations': bb_anims,
    }
    json.dump(bb, open(out_bb, 'w'), separators=(',', ':'))
    os.remove(arm_path)

    # ================================================================ веб-просмотр
    vb = []
    for b in d['bones']:
        cubes = []
        for c in b['cubes']:
            e = {'f': c['f'], 't': c['t'], 'c': color(c['s'])}
            if 'r' in c:
                e['r'] = c['r']; e['o'] = c['o']
            cubes.append(e)
        arms = [{'f': f, 't': t, 'c': col, 'r': [0, 0, 180], 'o': b['pivot']} for (f, t, col) in arm_cubes(b)] if b['name'] in ARM else []
        vb.append({'name': b['name'], 'parent': b.get('parent'), 'pivot': b.get('pivot', [0, 0, 0]),
                   'rot': b.get('rot', [0, 0, 0]),
                   # растяжение руки уже заложено в её кубы
                   'scale': [1, 1, 1] if b['name'] in ARM else b.get('scale', [1, 1, 1]), 'cubes': cubes, 'arms': arms})
    views = {k: geo.bones[k]['pivot'] for k in ('idle_view', 'iron_view')}
    order = ['draw', 'idle', 'walk', 'walk_aiming', 'run_start', 'run', 'run_end', 'ads_up', 'shoot',
             'reload_tactical', 'reload_empty', 'put_away']
    out_anims = [{'id': k, 'name': NAMES_RU.get(k, k), **anims[k]} for k in order if k in anims]
    json.dump({'bones': vb, 'views': views, 'anims': out_anims}, open(out_view, 'w'), separators=(',', ':'))
    print(len(elements), 'кубов,', len(bb_anims), 'анимаций ->', out_bb)


if __name__ == '__main__':
    main(*sys.argv[1:4])
