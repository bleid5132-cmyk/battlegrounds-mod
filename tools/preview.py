"""Превью «от первого лица» для моделей и анимаций Battlegrounds (та же математика, что в игре).

python3 tools/preview.py m416 [anim] [out.png]   — без anim: кадр от бедра и в прицеле
С anim: лист кадров с шагом 0.05 с.
"""
import json, os, sys
import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
from geolib import Geo, T, M4, rot_xyz, sample_anim

A = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'battlegrounds')
FACES = [((0, 1, 3, 2), (-1, 0, 0)), ((4, 6, 7, 5), (1, 0, 0)), ((0, 4, 5, 1), (0, -1, 0)),
         ((2, 3, 7, 6), (0, 1, 0)), ((0, 2, 6, 4), (0, 0, -1)), ((1, 5, 7, 3), (0, 0, 1))]
LIGHT = np.array([0.25, 0.85, 0.45]) / np.linalg.norm([0.25, 0.85, 0.45])
SLEEVE = (78, 104, 120)
SKIN = (196, 152, 118)


def corners(f, t):
    return np.array([[x, y, z, 1] for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])], float)


def arm_boxes(Wbone, side):
    """Рука Minecraft 4x12x4, как её рисует движок: S(1,s,1)*Rz180*T(-G)."""
    if side == 'right':
        G = np.array([-6, 11, 0]); fr, to = (-8, 0, -2), (-4, 12, 2)
    else:
        G = np.array([6, 11, 0]); fr, to = (4, 0, -2), (8, 12, 2)
    M = Wbone @ M4(rot_xyz(0, 0, 180)) @ T(-G)
    hand_split = 9.0
    return [(M, fr, (to[0], hand_split, to[2]), SLEEVE), (M, (fr[0], hand_split, fr[2]), to, SKIN)]


def render(boxes, view, fov=70, size=(480, 270), bg=(146, 170, 194)):
    W, H = size
    f = (H / 2) / np.tan(np.radians(fov) / 2)
    img = Image.new('RGB', size, bg)
    d = ImageDraw.Draw(img)
    polys = []
    for M, fr, to, col in boxes:
        VM = view @ M
        C = (VM @ corners(fr, to).T).T[:, :3]
        R = VM[:3, :3]
        for idx, n in FACES:
            nn = R @ np.array(n, float)
            pts = C[list(idx)]
            c0 = pts.mean(0)
            if np.dot(nn, c0) >= 0 or (pts[:, 2] > -0.3).any():
                continue
            proj = [(W / 2 + f * p[0] / -p[2], H / 2 - f * p[1] / -p[2]) for p in pts]
            nl = nn / (np.linalg.norm(nn) + 1e-9)
            shade = 0.6 + 0.4 * max(0.0, float(np.dot(nl, LIGHT)))
            c = tuple(int(min(255, v * shade)) for v in col)
            polys.append((c0[2], proj, c))
    polys.sort(key=lambda p: p[0])
    for _, proj, c in polys:
        d.polygon(proj, fill=c, outline=tuple(max(0, v - 18) for v in c))
    d.line([(W / 2 - 4, H / 2), (W / 2 + 4, H / 2)], fill=(255, 255, 255))
    d.line([(W / 2, H / 2 - 4), (W / 2, H / 2 + 4)], fill=(255, 255, 255))
    return img


def frame(geo, rgb, pose=None, aim=0.0, hidden=(), arms=True, cam_rot=None, fov=None):
    Wm = geo.world(pose)
    boxes = []
    hide = set(hidden)
    for name in geo.order:
        b = geo.bones[name]
        p = b
        skip = False
        while p:
            if p['name'] in hide:
                skip = True
            p = geo.bones.get(p.get('parent'))
        if skip:
            continue
        for c in b['cubes']:
            boxes.append((Wm[name], c['f'], c['t'], rgb[c['s']]))
    if arms:
        for side in ('right', 'left'):
            n = side + 'hand'
            Wb = Wm[n] @ T(geo.bones[n]['pivot'])
            boxes += arm_boxes(Wb, side)
    idle = geo.locator('idle_view')
    iron = geo.locator('iron_view')
    L = idle + (iron - idle) * aim
    view = np.linalg.inv(L)
    if cam_rot is not None:
        view = M4(rot_xyz(*cam_rot)) @ view
    if fov is None:
        fov = 70 + (48 - 70) * aim
    return render(boxes, view, fov)


def sheet(frames, cols=6, label=None):
    w, h = frames[0][1].size
    rows = (len(frames) + cols - 1) // cols
    out = Image.new('RGB', (w * cols, h * rows), (30, 30, 30))
    for i, (t, im) in enumerate(frames):
        dd = ImageDraw.Draw(im)
        dd.rectangle([0, 0, 64, 14], fill=(0, 0, 0))
        dd.text((4, 2), f'{t:.2f} s', fill=(255, 220, 120))
        out.paste(im, ((i % cols) * w, (i // cols) * h))
    return out


def load(gun):
    geo = Geo.load(os.path.join(A, 'geo', gun + '.json'))
    tex = Image.open(os.path.join(A, 'textures', 'geo', gun + '.png')).convert('RGB')
    sw = geo.data['swatch']
    cols = tex.width // sw
    rgb = {}
    for b in geo.data['bones']:
        for c in b['cubes']:
            s = c['s']
            if s not in rgb:
                rgb[s] = tex.getpixel(((s % cols) * sw + 3, (s // cols) * sw + 3))
    # чуть светлее, чтобы на превью было видно форму
    rgb = {k: tuple(min(255, int(v * 1.9 + 20)) for v in c) for k, c in rgb.items()}
    return geo, rgb


if __name__ == '__main__':
    gun = sys.argv[1]
    geo, rgb = load(gun)
    if len(sys.argv) < 3 or sys.argv[2] == 'static':
        out = sys.argv[3] if len(sys.argv) > 3 else f'/tmp/{gun}_static.png'
        a = frame(geo, rgb, aim=0.0)
        b = frame(geo, rgb, aim=1.0)
        img = Image.new('RGB', (a.width * 2, a.height))
        img.paste(a, (0, 0)); img.paste(b, (a.width, 0))
        img.save(out)
        print(out)
    else:
        anim_name = sys.argv[2]
        out = sys.argv[3] if len(sys.argv) > 3 else f'/tmp/{gun}_{anim_name}.png'
        anims = json.load(open(os.path.join(A, 'animations', gun + '.json')))['animations']
        an = anims[anim_name]
        n = int(round(an['length'] / 0.05)) + 1
        step = int(sys.argv[4]) if len(sys.argv) > 4 else 1
        frames = []
        for i in range(0, n, step):
            t = i * 0.05
            pose = sample_anim(an, t)
            cam = pose.get('camera', {}).get('rot')
            frames.append((t, frame(geo, rgb, pose, cam_rot=cam)))
        sheet(frames).save(out)
        print(out, len(frames), 'frames')
