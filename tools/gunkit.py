"""Общие инструменты моделей оружия (как в m416_geo.py, но для любого ствола).

Координаты: пиксели, x вправо, y вверх, ствол в -z.
Скруглённые детали — восьмигранники из 4 кубов, фаски — брусок из 6 кубов.
"""
import json, math, os, random, sys, zlib
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from geolib import euler_from_matrix

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'battlegrounds')
T45 = math.tan(math.radians(22.5))


class Gun:
    def __init__(self, gid, palette):
        self.id = gid
        self.palette = dict(palette)
        self.slot = {n: i for i, n in enumerate(self.palette)}
        assert len(self.slot) <= 64, 'не больше 64 цветов (текстура 64x64, ячейка 8x8)'
        self.bones = []
        self.by = {}

    # ------------------------------------------------------------ кости и кубы
    def bone(self, name, parent=None, pivot=(0, 0, 0), rot=None, scale=None):
        b = {'name': name, 'pivot': [round(v, 4) for v in pivot], 'cubes': []}
        if parent:
            b['parent'] = parent
        if rot:
            b['rot'] = [round(v, 4) for v in rot]
        if scale:
            b['scale'] = scale
        self.bones.append(b)
        self.by[name] = b
        return name

    def box(self, bn, f, t, sw, rot=None, origin=None):
        lo = [round(min(f[i], t[i]), 4) for i in range(3)]
        hi = [round(max(f[i], t[i]), 4) for i in range(3)]
        if hi[0] - lo[0] < 1e-4 or hi[1] - lo[1] < 1e-4 or hi[2] - lo[2] < 1e-4:
            return
        c = {'f': lo, 't': hi, 's': self.slot[sw]}
        if rot:
            c['r'] = [round(v, 4) for v in rot]
            c['o'] = [round(v, 4) for v in (origin if origin is not None else [(lo[i] + hi[i]) / 2 for i in range(3)])]
        self.by[bn]['cubes'].append(c)

    def cx(self, bn, w, y0, y1, z0, z1, sw, x=0.0):
        self.box(bn, (x - w / 2, y0, z0), (x + w / 2, y1, z1), sw)

    def octz(self, bn, x, y, r, z0, z1, sw):
        w = r * T45
        zc = (z0 + z1) / 2
        self.box(bn, (x - r, y - w, z0), (x + r, y + w, z1), sw)
        self.box(bn, (x - w, y - r, z0), (x + w, y + r, z1), sw)
        self.box(bn, (x - r, y - w, z0), (x + r, y + w, z1), sw, (0, 0, 45), (x, y, zc))
        self.box(bn, (x - w, y - r, z0), (x + w, y + r, z1), sw, (0, 0, 45), (x, y, zc))

    def octx(self, bn, y, z, r, x0, x1, sw):
        w = r * T45
        xc = (x0 + x1) / 2
        self.box(bn, (x0, y - r, z - w), (x1, y + r, z + w), sw)
        self.box(bn, (x0, y - w, z - r), (x1, y + w, z + r), sw)
        self.box(bn, (x0, y - r, z - w), (x1, y + r, z + w), sw, (45, 0, 0), (xc, y, z))
        self.box(bn, (x0, y - w, z - r), (x1, y + w, z + r), sw, (45, 0, 0), (xc, y, z))

    def octy(self, bn, x, z, r, y0, y1, sw):
        w = r * T45
        yc = (y0 + y1) / 2
        self.box(bn, (x - r, y0, z - w), (x + r, y1, z + w), sw)
        self.box(bn, (x - w, y0, z - r), (x + w, y1, z + r), sw)
        self.box(bn, (x - r, y0, z - w), (x + r, y1, z + w), sw, (0, 45, 0), (x, yc, z))
        self.box(bn, (x - w, y0, z - r), (x + w, y1, z + r), sw, (0, 45, 0), (x, yc, z))

    def chz(self, bn, x0, x1, y0, y1, z0, z1, c, sw):
        """Брусок вдоль z с фасками на продольных рёбрах."""
        self.box(bn, (x0, y0 + c, z0), (x1, y1 - c, z1), sw)
        self.box(bn, (x0 + c, y0, z0), (x1 - c, y1, z1), sw)
        h = c / math.sqrt(2)
        zc = (z0 + z1) / 2
        for (px, py) in ((x0 + c, y0 + c), (x1 - c, y0 + c), (x0 + c, y1 - c), (x1 - c, y1 - c)):
            self.box(bn, (px - h, py - h, z0), (px + h, py + h, z1), sw, (0, 0, 45), (px, py, zc))

    def chy(self, bn, x0, x1, y0, y1, z0, z1, c, sw):
        """Брусок вдоль y с фасками на вертикальных рёбрах."""
        self.box(bn, (x0, y0, z0 + c), (x1, y1, z1 - c), sw)
        self.box(bn, (x0 + c, y0, z0), (x1 - c, y1, z1), sw)
        h = c / math.sqrt(2)
        yc = (y0 + y1) / 2
        for (px, pz) in ((x0 + c, z0 + c), (x1 - c, z0 + c), (x0 + c, z1 - c), (x1 - c, z1 - c)):
            self.box(bn, (px - h, y0, pz - h), (px + h, y1, pz + h), sw, (0, 45, 0), (px, yc, pz))

    def slab(self, bn, x0, x1, y0, y1, z0, z1, sw, ang, c=0.0):
        """Брусок, наклонённый вокруг x на ang градусов (рукоять, наклонная деталь); c — фаска."""
        o = ((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)
        if c > 0:
            self.box(bn, (x0, y0, z0 + c), (x1, y1, z1 - c), sw, (ang, 0, 0), o)
            self.box(bn, (x0 + c, y0, z0), (x1 - c, y1, z1), sw, (ang, 0, 0), o)
        else:
            self.box(bn, (x0, y0, z0), (x1, y1, z1), sw, (ang, 0, 0), o)

    def ring_x(self, bn, x0, x1, y, z, r, sw, th=0.14, n=8):
        """Кольцо в плоскости yz (антабка, петля) из n брусочков."""
        for k in range(n):
            a = 360.0 * k / n
            rad = math.radians(a)
            yy, zz = y + math.cos(rad) * r, z + math.sin(rad) * r
            self.box(bn, (x0, yy - th / 2, zz - th / 2), (x1, yy + th / 2, zz + th / 2), sw, (a, 0, 0), ((x0 + x1) / 2, yy, zz))

    # ------------------------------------------------------------ планки
    def pic_top(self, bn, z0, z1, ytop, w=1.9, base_sw='rail', tooth_sw='rail_hi', mark_sw=None):
        base = ytop - 0.75
        self.chz(bn, -0.8, 0.8, base - 0.25, base + 0.05, z0, z1, 0.12, base_sw)
        z, n = z0 + 0.15, 0
        while z + 0.45 <= z1 - 0.1:
            self.box(bn, (-w / 2, base, z), (w / 2, ytop - 0.15, z + 0.45), base_sw)
            self.box(bn, (-w / 2 + 0.15, ytop - 0.15, z), (w / 2 - 0.15, ytop, z + 0.45), tooth_sw)
            if mark_sw and n % 2 == 0:
                self.box(bn, (w / 2 - 0.02, base + 0.05, z + 0.15), (w / 2 + 0.01, base + 0.25, z + 0.3), mark_sw)
            z += 0.8
            n += 1

    # ------------------------------------------------------------ руки
    def hand(self, name, parent, grip, toward, length_scale, side_hint=(1, 0, 0)):
        """Кость руки: pivot — точка хвата, ось +Y кости — от кисти к плечу."""
        y = np.array(toward, float)
        y /= np.linalg.norm(y)
        h = np.array(side_hint, float)
        xv = h - y * np.dot(h, y)
        xv /= np.linalg.norm(xv)
        zv = np.cross(xv, y)
        Rm = np.stack([xv, y, zv], axis=1)
        self.bone(name, parent, pivot=grip, rot=euler_from_matrix(Rm), scale=[1, length_scale, 1])

    # ------------------------------------------------------------ запись
    def texture(self, path, seed=0, grain=None):
        """Текстура: ячейки 8x8 с лёгким шумом; grain — имена цветов с древесным волокном."""
        rnd = random.Random(seed)
        img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        px = img.load()
        grain = set(grain or ())
        for n, i in self.slot.items():
            x0, y0 = (i % 8) * 8, (i // 8) * 8
            c = self.palette[n]
            for x in range(8):
                for y in range(8):
                    k = rnd.randint(-4, 4)
                    if n in grain:
                        k += int(6 * math.sin((y + rnd.random() * 0.6) * 1.9))
                    px[x0 + x, y0 + y] = tuple(max(0, min(255, v + k)) for v in c) + (255,)
        img.save(path)

    def save(self, extra, grain=None):
        os.makedirs(os.path.join(ROOT, 'geo'), exist_ok=True)
        os.makedirs(os.path.join(ROOT, 'textures', 'geo'), exist_ok=True)
        data = {'texture': f'battlegrounds:textures/geo/{self.id}.png', 'texture_size': [64, 64], 'swatch': 8}
        data.update(extra)
        data['bones'] = self.bones
        json.dump(data, open(os.path.join(ROOT, 'geo', self.id + '.json'), 'w'), separators=(',', ':'))
        self.texture(os.path.join(ROOT, 'textures', 'geo', self.id + '.png'), seed=zlib.crc32(self.id.encode()), grain=grain)
        return sum(len(b['cubes']) for b in self.bones)
