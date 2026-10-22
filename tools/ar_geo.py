"""Семейство AR (M416 и варианты: M16A4, Mk47 Mutant, K2, SCAR-L, G36C). Исходник — модель M416 — высокая детализация (900+ кубов), кости для анимаций, точки камеры и рук.

Запуск: python3 tools/m416_geo.py  -> assets/battlegrounds/geo/m416.json + textures/geo/m416.png
Координаты: пиксели, x вправо, y вверх, ствол в -z. Ось канала ствола: x=0, y=9.5.
Скруглённые детали — восьмигранники из 4 кубов (2 прямых + 2 повёрнутых на 45°),
фаски — «скруглённый прямоугольник» из 6 кубов. Точки камеры, рук и обвесов совпадают с прошлой версией,
поэтому анимации из m416_anims.py подходят без изменений.
"""
import json, math, os, random, sys
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from geolib import euler_from_matrix

VARIANTS = {
    'm416':   dict(hg0=-15.0, hg='quad', muzzle_z=-20.5, front='flip', stock='collapsible', mag='stanag', charge='rear', upper='ar', tint=None),
    'm16a4':  dict(hg0=-19.0, hg='quad', muzzle_z=-26.5, front='a2post', stock='a2', mag='stanag', charge='rear', upper='ar', tint=None),
    'mk47_mutant': dict(hg0=-13.0, hg='mlok', muzzle_z=-18.5, front='flip', stock='collapsible', mag='ak', charge='rear', upper='ar', tint=None),
    'k2':     dict(hg0=-13.5, hg='round', muzzle_z=-19.5, front='a2post', stock='folding', mag='stanag', charge='right', upper='ar', tint=None),
    'scar_l': dict(hg0=-12.5, hg='scar', muzzle_z=-17.0, front='flip', stock='scar', mag='stanag', charge='left', upper='scar', tint=(104, 86, 60)),
    'g36c':   dict(hg0=-12.0, hg='g36', muzzle_z=-17.5, front='flip', stock='g36', mag='g36', charge='top', upper='g36', tint=None),
}
GUN_ID = os.environ.get('GUN', 'm416')
V = VARIANTS[GUN_ID]

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'battlegrounds')

# ------------------------------------------------------------------ палитра (ячейки 8x8 на текстуре 64x64)
SW = {
    'rec': (40, 42, 45),       # ствольная коробка
    'rec_hi': (52, 55, 59),    # кромки
    'rec_lo': (33, 35, 37),
    'dark': (22, 23, 25),      # щели, отверстия
    'hg': (37, 39, 42),        # цевьё
    'hg_hi': (47, 50, 54),
    'rail': (31, 33, 36),      # планки
    'rail_hi': (42, 45, 48),
    'metal': (64, 67, 71),     # сталь
    'metal_d': (48, 50, 53),
    'metal_hi': (82, 86, 90),
    'poly': (34, 34, 36),      # пластик
    'poly_hi': (44, 44, 46),
    'rubber': (21, 21, 22),
    'mag': (39, 40, 41),
    'mag_hi': (50, 51, 53),
    'sight': (19, 19, 20),
    'brass': (176, 140, 72),
    'brass_d': (140, 108, 52),
    'copper': (170, 100, 60),
    'mark': (120, 122, 118),   # белые риски на планках
    'tritium': (120, 220, 120),
}
SLOT = {n: i for i, n in enumerate(SW)}


def make_texture(path):
    rnd = random.Random(416)
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    px = img.load()
    for n, i in SLOT.items():
        x0, y0 = (i % 8) * 8, (i // 8) * 8
        c = SW[n]
        for x in range(8):
            for y in range(8):
                k = rnd.randint(-4, 4)
                px[x0 + x, y0 + y] = tuple(max(0, min(255, v + k)) for v in c) + (255,)
    img.save(path)


# ------------------------------------------------------------------ сборка
bones = []
_by = {}


def bone(name, parent=None, pivot=(0, 0, 0), rot=None, scale=None):
    b = {'name': name, 'pivot': [round(v, 4) for v in pivot], 'cubes': []}
    if parent:
        b['parent'] = parent
    if rot:
        b['rot'] = [round(v, 4) for v in rot]
    if scale:
        b['scale'] = scale
    bones.append(b)
    _by[name] = b
    return name


def box(bn, f, t, sw, rot=None, origin=None):
    lo = [round(min(f[i], t[i]), 4) for i in range(3)]
    hi = [round(max(f[i], t[i]), 4) for i in range(3)]
    if hi[0] - lo[0] < 1e-4 or hi[1] - lo[1] < 1e-4 or hi[2] - lo[2] < 1e-4:
        return
    c = {'f': lo, 't': hi, 's': SLOT[sw]}
    if rot:
        c['r'] = [round(v, 4) for v in rot]
        c['o'] = [round(v, 4) for v in origin]
    _by[bn]['cubes'].append(c)


def cx(bn, w, y0, y1, z0, z1, sw, x=0.0):
    box(bn, (x - w / 2, y0, z0), (x + w / 2, y1, z1), sw)


T45 = math.tan(math.radians(22.5))


def octz(bn, x, y, r, z0, z1, sw):
    """Восьмигранный цилиндр вдоль z (r — до грани)."""
    w = r * T45
    zc = (z0 + z1) / 2
    box(bn, (x - r, y - w, z0), (x + r, y + w, z1), sw)
    box(bn, (x - w, y - r, z0), (x + w, y + r, z1), sw)
    box(bn, (x - r, y - w, z0), (x + r, y + w, z1), sw, (0, 0, 45), (x, y, zc))
    box(bn, (x - w, y - r, z0), (x + w, y + r, z1), sw, (0, 0, 45), (x, y, zc))


def octx(bn, y, z, r, x0, x1, sw):
    """Восьмигранный цилиндр вдоль x (штифты, винты сбоку)."""
    w = r * T45
    xc = (x0 + x1) / 2
    box(bn, (x0, y - r, z - w), (x1, y + r, z + w), sw)
    box(bn, (x0, y - w, z - r), (x1, y + w, z + r), sw)
    box(bn, (x0, y - r, z - w), (x1, y + r, z + w), sw, (45, 0, 0), (xc, y, z))
    box(bn, (x0, y - w, z - r), (x1, y + w, z + r), sw, (45, 0, 0), (xc, y, z))


def octy(bn, x, z, r, y0, y1, sw):
    """Восьмигранный цилиндр вдоль y."""
    w = r * T45
    yc = (y0 + y1) / 2
    box(bn, (x - r, y0, z - w), (x + r, y1, z + w), sw)
    box(bn, (x - w, y0, z - r), (x + w, y1, z + r), sw)
    box(bn, (x - r, y0, z - w), (x + r, y1, z + w), sw, (0, 45, 0), (x, yc, z))
    box(bn, (x - w, y0, z - r), (x + w, y1, z + r), sw, (0, 45, 0), (x, yc, z))


def chz(bn, x0, x1, y0, y1, z0, z1, c, sw):
    """Брусок вдоль z с фасками на продольных рёбрах (6 кубов)."""
    box(bn, (x0, y0 + c, z0), (x1, y1 - c, z1), sw)
    box(bn, (x0 + c, y0, z0), (x1 - c, y1, z1), sw)
    h = c / math.sqrt(2)
    zc = (z0 + z1) / 2
    for (px, py) in ((x0 + c, y0 + c), (x1 - c, y0 + c), (x0 + c, y1 - c), (x1 - c, y1 - c)):
        box(bn, (px - h, py - h, z0), (px + h, py + h, z1), sw, (0, 0, 45), (px, py, zc))


def chy(bn, x0, x1, y0, y1, z0, z1, c, sw):
    """Брусок вдоль y с фасками на вертикальных рёбрах (6 кубов)."""
    box(bn, (x0, y0, z0 + c), (x1, y1, z1 - c), sw)
    box(bn, (x0 + c, y0, z0), (x1 - c, y1, z1), sw)
    h = c / math.sqrt(2)
    yc = (y0 + y1) / 2
    for (px, pz) in ((x0 + c, z0 + c), (x1 - c, z0 + c), (x0 + c, z1 - c), (x1 - c, z1 - c)):
        box(bn, (px - h, y0, pz - h), (px + h, y1, pz + h), sw, (0, 45, 0), (px, yc, pz))


def pic_top(bn, z0, z1, ytop, w=1.9, marks=True):
    """Планка Пикатинни сверху: основание с фасками + зубья (2 куба) + риски."""
    base = ytop - 0.75
    chz(bn, -0.8, 0.8, base - 0.25, base + 0.05, z0, z1, 0.12, 'rail')
    z, n = z0 + 0.15, 0
    while z + 0.45 <= z1 - 0.1:
        box(bn, (-w / 2, base, z), (w / 2, ytop - 0.15, z + 0.45), 'rail')
        box(bn, (-w / 2 + 0.15, ytop - 0.15, z), (w / 2 - 0.15, ytop, z + 0.45), 'rail_hi')
        if marks and n % 2 == 0:
            box(bn, (w / 2 - 0.02, base + 0.05, z + 0.15), (w / 2 + 0.01, base + 0.25, z + 0.3), 'mark')
        z += 0.8
        n += 1


def pic_side(bn, side, z0, z1, yc):
    """Боковая планка на цевье (side = +1 справа, -1 слева)."""
    s = side
    xb, xt = 1.5 * s, 1.75 * s
    box(bn, (min(xb, xt), yc - 0.5, z0), (max(xb, xt), yc + 0.5, z1), 'rail')
    z = z0 + 0.15
    while z + 0.45 <= z1 - 0.1:
        x1, x2 = 1.75 * s, 2.0 * s
        x3 = 2.15 * s
        box(bn, (min(x1, x2), yc - 0.65, z), (max(x1, x2), yc + 0.65, z + 0.45), 'rail')
        box(bn, (min(x2, x3), yc - 0.5, z), (max(x2, x3), yc + 0.5, z + 0.45), 'rail_hi')
        z += 0.8


def pic_bottom(bn, z0, z1, ybase):
    cx(bn, 1.6, ybase - 0.3, ybase, z0, z1, 'rail')
    z = z0 + 0.15
    while z + 0.45 <= z1 - 0.1:
        box(bn, (-0.95, ybase - 0.75, z), (0.95, ybase - 0.3, z + 0.45), 'rail')
        box(bn, (-0.8, ybase - 0.9, z), (0.8, ybase - 0.75, z + 0.45), 'rail_hi')
        z += 0.8


BORE = 9.5
DZ = V['muzzle_z'] + 20.5
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'ar_parts.py'), encoding='utf-8').read())   # варианты узлов

bone('root', pivot=(0, 7.0, 6.6))
bone('gun', 'root', pivot=(0, BORE, 0))
G = 'gun'

# ================================================================== верхняя ствольная коробка
chz(G, -1.2, 1.2, 8.6, 11.2, -4.0, 7.0, 0.25, 'rec')
cx(G, 2.0, 11.2, 11.35, -4.0, 6.8, 'rec_hi')
pic_top(G, -4.0, 6.8, 12.1)
# окно выброса, крышка, затворная рама
box(G, (1.18, 9.2, 0.3), (1.22, 10.5, 3.7), 'dark')
box(G, (1.2, 9.45, 0.9), (1.24, 10.25, 3.1), 'metal')
for z in (1.3, 2.2):
    box(G, (1.24, 9.6, z), (1.28, 10.1, z + 0.45), 'metal_hi')
box(G, (1.22, 9.0, 0.2), (1.32, 9.3, 3.8), 'metal_d')                  # откинутая крышка
for z in (0.6, 1.4, 2.2, 3.0):
    box(G, (1.32, 9.05, z), (1.38, 9.25, z + 0.4), 'rec_hi')
octz(G, 1.27, 10.48, 0.07, 0.25, 3.75, 'metal')                         # ось крышки
# досылатель
octz(G, 1.55, 10.05, 0.42, 3.7, 5.0, 'rec')
octx(G, 10.05, 4.35, 0.32, 1.95, 2.15, 'metal')
for z in (3.85, 4.15, 4.45):
    box(G, (2.0, 9.75, z), (2.05, 10.35, z + 0.15), 'metal_hi')
# отражатель гильз
box(G, (1.2, 9.0, 4.95), (1.75, 10.9, 5.4), 'rec')
box(G, (1.2, 10.4, 4.95), (1.75, 10.9, 5.4), 'rec_hi', (0, 0, -25), (1.2, 10.4, 5.2))
# стык верхней и нижней коробки, штифты
box(G, (-1.23, 8.55, -3.9), (1.23, 8.62, 6.9), 'dark')
octx(G, 8.15, -3.1, 0.22, -1.32, 1.32, 'metal')                        # передний штифт
octx(G, 8.15, 5.6, 0.22, -1.32, 1.32, 'metal')                         # задний штифт
# левая сторона: заглушки и гравировка
for z in (-3.0, -1.0, 1.0, 3.0, 5.0):
    box(G, (-1.24, 9.4, z), (-1.2, 10.6, z + 1.2), 'rec_lo')
# гайка ствола у цевья
octz(G, 0, BORE, 0.95, -4.4, -4.0, 'metal_d')

# ================================================================== нижняя ствольная коробка
chz(G, -1.15, 1.15, 6.8, 8.6, -3.6, 6.4, 0.2, 'rec')
chy(G, -1.3, 1.3, 5.6, 8.0, -3.95, -0.35, 0.18, 'rec')                # горловина
chy(G, -1.45, 1.45, 5.35, 5.75, -4.1, -0.2, 0.15, 'rec_hi')           # раструб
cx(G, 0.8, 5.15, 5.35, -4.0, -3.4, 'rec')
for z in (-3.6, -2.8, -2.0, -1.2):                                     # рифление горловины
    box(G, (1.3, 6.0, z), (1.34, 7.4, z + 0.4), 'rec_lo')
    box(G, (-1.34, 6.0, z), (-1.3, 7.4, z + 0.4), 'rec_lo')
# спусковая скоба
cx(G, 0.9, 5.4, 5.8, -0.2, 2.8, 'rec')
cx(G, 0.9, 5.4, 6.8, 2.6, 3.0, 'rec')
cx(G, 0.9, 5.6, 6.8, -0.35, 0.0, 'rec')
box(G, (-0.45, 5.4, 2.55), (0.45, 5.75, 2.95), 'rec', (-45, 0, 0), (0, 5.6, 2.75))
box(G, (-0.45, 5.4, -0.25), (0.45, 5.75, 0.15), 'rec', (45, 0, 0), (0, 5.6, -0.05))
# спуск: изогнутый
for i, (y0, z0) in enumerate([(6.45, 1.0), (6.15, 1.05), (5.9, 1.15), (5.7, 1.3)]):
    cx(G, 0.42, y0, y0 + 0.32, z0, z0 + 0.32, 'metal')
octx(G, 7.45, 1.15, 0.18, -1.25, 1.25, 'metal')                        # ось спуска
octx(G, 7.45, 2.2, 0.18, -1.25, 1.25, 'metal')                         # ось курка
# переводчик огня
octx(G, 7.75, 4.0, 0.3, -1.3, -1.15, 'metal_d')
box(G, (-1.42, 7.55, 3.3), (-1.3, 7.95, 4.7), 'metal')
box(G, (-1.42, 7.6, 4.55), (-1.3, 8.3, 4.75), 'metal')
octx(G, 7.75, 4.0, 0.22, 1.15, 1.28, 'metal_d')
box(G, (1.28, 7.6, 3.75), (1.36, 7.9, 4.5), 'metal')
# кнопка магазина с защитой
octx(G, 7.55, -0.3, 0.3, 1.15, 1.42, 'metal_d')
box(G, (1.15, 6.9, -1.05), (1.35, 8.2, -0.95), 'rec')
box(G, (1.15, 6.9, 0.35), (1.35, 8.2, 0.45), 'rec')
# затворная задержка
box(G, (-1.42, 7.6, -0.3), (-1.18, 8.45, 0.65), 'metal_d')
box(G, (-1.5, 7.65, -0.25), (-1.42, 8.4, 0.25), 'metal_d')
for y in (7.8, 8.05, 8.3):
    box(G, (-1.54, y, -0.2), (-1.5, y + 0.08, 0.2), 'metal_hi')
octx(G, 8.1, 0.45, 0.13, -1.45, -1.15, 'metal')
# концевая пластина и антабка сзади
box(G, (-1.25, 6.95, 6.2), (1.25, 8.5, 6.45), 'rec_hi')
octx(G, 7.9, 6.4, 0.32, 1.25, 1.6, 'metal_d')
octx(G, 7.9, 6.4, 0.18, 1.6, 1.62, 'dark')

# ================================================================== пистолетная рукоять
bone('pgrip', G, pivot=(0, 6.8, 3.4), rot=(-20, 0, 0))
P = 'pgrip'
# профиль по срезам: (y0, y1, z_front, z_back, half_width)
SL = [(2.3, 2.6, 2.25, 4.8, 1.0), (2.6, 3.1, 2.15, 4.65, 1.08), (3.1, 3.6, 2.35, 4.6, 1.1), (3.6, 4.1, 2.1, 4.55, 1.1),
      (4.1, 4.6, 2.35, 4.5, 1.1), (4.6, 5.1, 2.1, 4.45, 1.1), (5.1, 5.6, 2.35, 4.4, 1.08), (5.6, 6.2, 2.15, 4.45, 1.05),
      (6.2, 6.8, 2.3, 4.6, 1.0)]
for (y0, y1, zf, zb, hw) in SL:
    chy(P, -hw, hw, y0, y1, zf, zb, 0.32, 'poly')
cx(P, 2.0, 6.3, 6.8, 4.5, 5.45, 'poly')                                # «бобровый хвост»
box(P, (-1.0, 6.55, 5.3), (1.0, 6.8, 5.6), 'poly_hi', (-30, 0, 0), (0, 6.6, 5.45))
cx(P, 1.9, 2.2, 2.3, 2.3, 4.7, 'poly_hi')                              # крышка
octy(P, 0, 3.5, 0.35, 2.12, 2.22, 'metal')                             # винт
# насечка: выпуклые точки по бокам
for row in range(6):
    for col in range(4):
        y = 3.0 + row * 0.5
        z = 2.75 + col * 0.45
        box(P, (1.1, y, z), (1.16, y + 0.25, z + 0.25), 'poly_hi')
        box(P, (-1.16, y, z), (-1.1, y + 0.25, z + 0.25), 'poly_hi')
for row in range(6):                                                    # насечка сзади
    y = 3.0 + row * 0.5
    cx(P, 1.4, y, y + 0.25, 4.5, 4.62, 'poly_hi')

# ================================================================== цевьё (квадрорельс)
HG0, HG1 = V['hg0'], -4.0
if V['hg'] == 'quad':
    chz(G, -1.5, 1.5, 7.7, 11.3, HG0, HG1, 0.35, 'hg')
    chz(G, -1.6, 1.6, 7.6, 11.4, HG0 - 0.3, HG0, 0.4, 'hg_hi')             # передний торец
    octz(G, 0, BORE, 0.62, HG0 - 0.32, HG0 - 0.28, 'dark')
    chz(G, -1.6, 1.6, 7.6, 11.4, HG1 - 0.15, HG1 + 0.05, 0.4, 'hg_hi')
    pic_top(G, HG0, HG1 - 0.2, 12.1)
    pic_side(G, 1, HG0 + 0.4, HG1 - 0.8, 9.5)
    pic_side(G, -1, HG0 + 0.4, HG1 - 0.8, 9.5)
    pic_bottom(G, HG0 + 0.4, HG1 - 0.8, 7.7)
    # вентиляционные окна на гранях между планками
    for z in (-14.2, -12.6, -11.0, -9.4, -7.8, -6.2):
        for (x, y, rz) in ((1.15, 10.95, -45), (-1.15, 10.95, 45), (1.15, 8.05, 45), (-1.15, 8.05, -45)):
            box(G, (x - 0.25, y - 0.12, z), (x + 0.25, y + 0.12, z + 1.0), 'dark', (0, 0, rz), (x, y, z + 0.5))
    # винты цевья
    for z in (-14.3, -10.5, -6.0):
        for s in (1, -1):
            octx(G, 10.55, z, 0.17, 1.48 * s if s > 0 else -1.56, 1.56 if s > 0 else -1.48, 'metal')
            octx(G, 8.45, z, 0.17, 1.48 * s if s > 0 else -1.56, 1.56 if s > 0 else -1.48, 'metal')
    # QD-антабки спереди
    for s in (1, -1):
        octx(G, 10.65, -14.4, 0.3, 1.55 if s > 0 else -1.85, 1.85 if s > 0 else -1.55, 'metal_d')
        octx(G, 10.65, -14.4, 0.16, 1.85 if s > 0 else -1.87, 1.87 if s > 0 else -1.85, 'dark')
    cx(G, 0.5, 11.15, 11.35, -15.25, -15.05, 'metal')                      # стопор планки

else:
    alt_handguard(V['hg'])

# ================================================================== ствол
octz(G, 0, BORE, 0.45, -20.5 + DZ, HG0 - 0.3, 'metal')
octz(G, 0, BORE, 0.55, -19.6 + DZ, -19.2 + DZ, 'metal_d')
octz(G, 0, BORE, 0.5, HG0 - 1.3 if V['front'] == 'flip' else HG0 - 0.9, HG0 - 0.3, 'metal_d')   # газовый блок
cx(G, 0.5, BORE - 0.75, BORE - 0.45, -20.4 + DZ, -19.6 + DZ, 'metal_d')          # проточка

# ================================================================== пламегаситель (снимается дульными обвесами)
bone('muzzle_default', G, pivot=(0, BORE, -20.5))
M = 'muzzle_default'
octz(M, 0, BORE, 0.68, -22.9, -20.5, 'metal_d')
octz(M, 0, BORE, 0.75, -21.05, -20.5, 'metal_d')                        # гайка
octz(M, 0, BORE, 0.72, -23.05, -22.85, 'metal_d')                       # передний обод
octz(M, 0, BORE, 0.36, -23.08, -23.04, 'dark')                          # канал
for ang in (0, 60, 120, 180, 240, 300):                                 # прорези «клетки»
    rad = math.radians(ang)
    x, y = math.sin(rad) * 0.7, BORE + math.cos(rad) * 0.7
    box(M, (x - 0.09, y - 0.06, -22.65), (x + 0.09, y + 0.06, -21.3), 'dark', (0, 0, -ang), (x, y, -22.0))
for z in (-20.9, -20.7):                                                # грани под ключ
    box(M, (0.72, BORE - 0.3, z), (0.78, BORE + 0.3, z + 0.12), 'metal')
    box(M, (-0.78, BORE - 0.3, z), (-0.72, BORE + 0.3, z + 0.12), 'metal')

shift_bone('muzzle_default', DZ)

# ================================================================== мушка (откидная)
if V['front'] == 'flip':
    bone('sight_front', G, pivot=(0, 11.6, -14.2))
    S = 'sight_front'
    chz(S, -0.75, 0.75, 12.1, 12.55, -14.9, -13.4, 0.12, 'sight')
    octx(S, 12.4, -13.6, 0.16, -0.8, 0.8, 'metal_d')                         # ось
    for s in (1, -1):                                                       # «уши»
        x0, x1 = (0.25, 0.65) if s > 0 else (-0.65, -0.25)
        box(S, (x0, 12.55, -14.75), (x1, 13.0, -14.25), 'sight')
        box(S, (x0, 13.0, -14.75), (x1, 13.75, -14.55), 'sight')
        box(S, (x0, 13.0, -14.45), (x1, 13.75, -14.25), 'sight')
        box(S, (x0, 13.55, -14.75), (x1, 13.75, -14.25), 'sight')
    cx(S, 1.3, 12.55, 12.8, -14.75, -14.25, 'sight')
    octy(S, 0, -14.5, 0.11, 12.8, 13.3, 'sight')                             # мушка, вершина 13.3
    cx(S, 0.08, 13.1, 13.18, -14.53, -14.47, 'tritium')
    octy(S, 0, -14.5, 0.2, 12.55, 12.8, 'metal_d')                          # барабан высоты
    for a in (0, 45, 90, 135):
        box(S, (-0.22, 12.6, -14.53), (0.22, 12.75, -14.47), 'metal_hi', (0, a, 0), (0, 12.67, -14.5))
    cx(S, 0.5, 12.55, 12.7, -14.2, -13.5, 'metal')                          # пружина

    shift_bone('sight_front', HG0 + 15.0)
else:
    alt_front(V['front'])

# ================================================================== целик (откидной)
bone('sight_rear', G, pivot=(0, 11.6, 5.3))
R = 'sight_rear'
chz(R, -0.75, 0.75, 12.1, 12.7, 4.6, 6.2, 0.12, 'sight')
octx(R, 12.5, 6.0, 0.16, -0.8, 0.8, 'metal_d')
box(R, (0.35, 12.7, 5.0), (0.75, 13.95, 5.6), 'sight')
box(R, (-0.75, 12.7, 5.0), (-0.35, 13.95, 5.6), 'sight')
cx(R, 1.5, 13.75, 13.95, 5.0, 5.6, 'sight')
cx(R, 0.7, 12.7, 12.95, 5.0, 5.6, 'sight')
# кольцо диоптра (центр 13.3)
for a in range(0, 360, 45):
    rad = math.radians(a)
    x, y = math.sin(rad) * 0.33, 13.3 + math.cos(rad) * 0.33
    box(R, (x - 0.07, y - 0.04, 5.25), (x + 0.07, y + 0.04, 5.35), 'sight', (0, 0, -a), (x, y, 5.3))
# барабаны поправок
octx(R, 13.0, 5.4, 0.22, 0.75, 1.0, 'sight')
octx(R, 13.0, 5.4, 0.22, -1.0, -0.75, 'sight')
for z in (5.22, 5.4, 5.58):
    box(R, (0.95, 12.8, z - 0.03), (1.02, 13.2, z + 0.03), 'metal_d')
    box(R, (-1.02, 12.8, z - 0.03), (-0.95, 13.2, z + 0.03), 'metal_d')

# ================================================================== рукоять заряжания
if V['charge'] == 'rear':
    bone('charge_handle', G, pivot=(0, 10.7, 7.0))
    C = 'charge_handle'
    cx(C, 0.6, 10.45, 10.85, 6.4, 7.05, 'metal_d')
    chz(C, -1.3, 1.3, 10.4, 11.0, 7.0, 7.55, 0.12, 'rec')
    for x in (-1.0, -0.6, 0.6, 1.0):                                         # рифление
        box(C, (x - 0.08, 10.38, 7.15), (x + 0.08, 11.02, 7.4), 'rec_hi')
    box(C, (-1.6, 10.45, 7.0), (-1.3, 10.95, 7.5), 'rec_hi')                 # защёлка
    box(C, (1.3, 10.5, 7.05), (1.45, 10.9, 7.45), 'rec_hi')

else:
    alt_charge(V['charge'])

# ================================================================== приклад (снимается тактическим)
if V['stock'] == 'collapsible':
    bone('stock_default', G, pivot=(0, 9.6, 6.4))
    K = 'stock_default'
    octz(K, 0, 9.6, 0.75, 6.4, 12.0, 'metal_d')                             # буферная трубка
    for z in (7.6, 8.4, 9.2, 10.0, 10.8, 11.6):                               # отверстия фиксатора
        cx(K, 0.3, 8.83, 8.87, z, z + 0.3, 'dark')
    octz(K, 0, 9.6, 0.92, 6.4, 6.9, 'metal')                                  # гайка
    for ang in (0, 90, 180, 270):
        rad = math.radians(ang)
        x, y = math.sin(rad) * 0.9, 9.6 + math.cos(rad) * 0.9
        box(K, (x - 0.1, y - 0.1, 6.45), (x + 0.1, y + 0.1, 6.85), 'dark', (0, 0, -ang), (x, y, 6.65))
    # корпус приклада: срезы вдоль z (y0, y1, half_width)
    ST = [(8.6, 9.1, 7.2, 10.45, 1.12), (9.1, 9.6, 7.05, 10.5, 1.15), (9.6, 10.1, 7.0, 10.5, 1.15),
          (10.1, 10.6, 6.75, 10.5, 1.15), (10.6, 11.1, 6.5, 10.5, 1.15), (11.1, 11.6, 6.4, 10.5, 1.15),
          (11.6, 12.1, 6.35, 10.5, 1.15), (12.1, 12.6, 6.3, 10.5, 1.15), (12.6, 13.0, 6.25, 10.45, 1.15)]
    for (z0, z1, y0, y1, hw) in ST:
        chz(K, -hw, hw, y0, y1, z0, z1, 0.3, 'poly')
    chz(K, -1.0, 1.0, 10.45, 10.85, 9.6, 13.0, 0.15, 'poly_hi')              # щека
    for (z0, z1) in ((9.4, 10.0), (11.0, 11.6), (12.2, 12.7)):               # прорези
        for s in (1, -1):
            x0, x1 = (1.12, 1.17) if s > 0 else (-1.17, -1.12)
            box(K, (x0, 7.6, z0), (x1, 10.3, z1), 'dark')
            box(K, (x0, 10.3, z0), (x1, 10.4, z1), 'poly_hi')
    # затыльник
    chz(K, -1.2, 1.2, 6.2, 10.95, 13.0, 13.6, 0.35, 'rubber')
    for y in (6.6, 7.4, 8.2, 9.0, 9.8):
        cx(K, 2.5, y, y + 0.35, 13.55, 13.68, 'dark')
    # рычаг регулировки и защёлка
    chz(K, -0.4, 0.4, 8.3, 8.75, 8.9, 10.2, 0.1, 'metal_d')
    cx(K, 1.0, 6.9, 7.05, 8.7, 10.4, 'metal_d')
    for z in (9.1, 9.4, 9.7):
        cx(K, 0.85, 8.25, 8.3, z, z + 0.15, 'metal_hi')
    # QD-антабки по бокам
    for s in (1, -1):
        octx(K, 9.6, 12.25, 0.32, 1.15 if s > 0 else -1.42, 1.42 if s > 0 else -1.15, 'metal')
        octx(K, 9.6, 12.25, 0.16, 1.42 if s > 0 else -1.44, 1.44 if s > 0 else -1.42, 'dark')

else:
    alt_stock(V['stock'])

# ================================================================== магазин вместе с левой рукой
if V['mag'] == 'stanag':
    bone('mag_and_hand', 'root', pivot=(0, 7.6, -2.15))
    bone('magazine', 'mag_and_hand', pivot=(0, 7.6, -2.15))
    Mg = 'magazine'
    chy(Mg, -0.9, 0.9, 2.2, 7.6, -3.55, -0.75, 0.25, 'mag')
    for y in (2.5, 3.1, 3.7, 4.3, 4.9):                                      # «вафельные» рёбра
        for s in (1, -1):
            x0, x1 = (0.9, 0.97) if s > 0 else (-0.97, -0.9)
            box(Mg, (x0, y, -3.3), (x1, y + 0.32, -1.0), 'mag_hi')
    for s in (1, -1):                                                        # вертикальные рёбра
        x0, x1 = (0.9, 0.98) if s > 0 else (-0.98, -0.9)
        box(Mg, (x0, 2.3, -3.42), (x1, 5.3, -3.3), 'mag_hi')
        box(Mg, (x0, 2.3, -1.0), (x1, 5.3, -0.88), 'mag_hi')
        box(Mg, (x0, 5.6, -3.3), (x1, 6.9, -2.4), 'dark')                    # окошко
    cx(Mg, 0.6, 2.2, 7.6, -3.6, -3.55, 'mag_hi')                             # передний гребень
    cx(Mg, 0.6, 2.2, 7.6, -0.75, -0.7, 'mag_hi')
    cx(Mg, 1.5, 7.55, 7.75, -3.4, -2.9, 'mag_hi')                            # губы
    cx(Mg, 1.5, 7.55, 7.75, -1.4, -0.9, 'mag_hi')
    octz(Mg, 0.18, 7.82, 0.25, -3.1, -1.2, 'brass')                           # патроны
    octz(Mg, 0.18, 7.82, 0.16, -3.45, -3.1, 'copper')
    octz(Mg, -0.22, 7.62, 0.25, -3.0, -1.1, 'brass_d')
    bone('mag_lower', Mg, pivot=(0, 2.4, -2.15), rot=(10, 0, 0))
    Ml = 'mag_lower'
    chy(Ml, -0.9, 0.9, -0.8, 2.45, -3.55, -0.75, 0.25, 'mag')
    for y in (-0.5, 0.1, 0.7, 1.3, 1.9):
        for s in (1, -1):
            x0, x1 = (0.9, 0.97) if s > 0 else (-0.97, -0.9)
            box(Ml, (x0, y, -3.3), (x1, y + 0.32, -1.0), 'mag_hi')
    for s in (1, -1):
        x0, x1 = (0.9, 0.98) if s > 0 else (-0.98, -0.9)
        box(Ml, (x0, -0.7, -3.42), (x1, 2.4, -3.3), 'mag_hi')
        box(Ml, (x0, -0.7, -1.0), (x1, 2.4, -0.88), 'mag_hi')
    chy(Ml, -1.05, 1.05, -1.3, -0.8, -3.8, -0.5, 0.25, 'mag_hi')           # пятка
    for z in (-3.4, -2.8, -2.2, -1.6, -1.0):
        cx(Ml, 1.7, -1.38, -1.3, z, z + 0.3, 'dark')
    cx(Ml, 0.8, -1.42, -1.3, -3.9, -3.6, 'mag_hi')
    bone('mag_pos', Ml, pivot=(0, -1.3, -2.15))

else:
    alt_mag(V['mag'])

# ================================================================== дополнительные детали
# риски на всех зубьях планок цевья слева
z = HG0 + 0.3
while z + 0.45 <= HG1 - 0.3:
    box(G, (-0.97, 11.4, z), (-0.95, 11.55, z + 0.15), 'mark')
    z += 0.8
# каннелюры ствола
for a in (0, 90, 180, 270):
    rad = math.radians(a)
    x, y = math.sin(rad) * 0.44, BORE + math.cos(rad) * 0.44
    box(G, (x - 0.06, y - 0.04, -19.1 + DZ), (x + 0.06, y + 0.04, min(-16.4 + DZ, HG0 - 0.5)), 'dark', (0, 0, -a), (x, y, -17.7 + DZ))
for z in (-20.45, -20.35, -20.25, -20.15):                               # резьба под дульное
    octz(G, 0, BORE, 0.47, z + DZ, z + DZ + 0.05, 'metal_hi')
# насечка торца цевья
for a in range(0, 360, 45):
    rad = math.radians(a)
    x, y = math.sin(rad) * 1.25, 9.5 + math.cos(rad) * 1.25
    box(G, (x - 0.12, y - 0.12, HG0 - 0.34), (x + 0.12, y + 0.12, HG0 - 0.3), 'hg', (0, 0, -a), (x, y, HG0 - 0.32))
# рифление под большой палец на нижней коробке
for z in (4.9, 5.2, 5.5, 5.8):
    box(G, (1.15, 7.0, z), (1.19, 7.35, z + 0.15), 'rec_hi')
    box(G, (-1.19, 7.0, z), (-1.15, 7.35, z + 0.15), 'rec_hi')
# рёбра спусковой скобы
for z in (0.4, 1.0, 1.6, 2.2):
    cx(G, 0.7, 5.32, 5.4, z, z + 0.2, 'rec_hi')
# антабка с кольцом сзади нижней коробки
for a in range(0, 360, 45):
    rad = math.radians(a)
    y, z2 = 7.9 + math.cos(rad) * 0.45, 6.4 + math.sin(rad) * 0.45
    box(G, (1.6, y - 0.07, z2 - 0.07), (1.7, y + 0.07, z2 + 0.07), 'metal', (a, 0, 0), (1.65, y, z2))
# мушка/целик: насечки на «ушах»
for y in (13.1, 13.3, 13.5) if V['front'] == 'flip' else ():
    dzf = HG0 + 15.0
    box(S, (0.65, y, -14.7 + dzf), (0.68, y + 0.08, -14.3 + dzf), 'metal_d')
    box(S, (-0.68, y, -14.7 + dzf), (-0.65, y + 0.08, -14.3 + dzf), 'metal_d')
# магазин: рифление пятки и верх «вафли»
for z in (-3.6, -3.0, -2.4, -1.8, -1.2) if V['mag'] == 'stanag' else ():
    box(Ml, (1.05, -1.25, z), (1.1, -0.85, z + 0.3), 'mag')
    box(Ml, (-1.1, -1.25, z), (-1.05, -0.85, z + 0.3), 'mag')
for y in (5.5, 6.1, 6.7, 7.2) if V['mag'] == 'stanag' else ():
    box(Mg, (0.9, y, -1.0), (0.97, y + 0.2, -0.85), 'mag_hi')
    box(Mg, (-0.97, y, -1.0), (-0.9, y + 0.2, -0.85), 'mag_hi')
# приклад: рифление щеки и антабка-петля снизу
if V['stock'] == 'collapsible':
    for z in (10.0, 10.5, 11.0, 11.5, 12.0, 12.5):
        cx(K, 1.6, 10.85, 10.9, z, z + 0.25, 'poly_hi')
    for a in range(0, 360, 45):
        rad = math.radians(a)
        x2, y = math.sin(rad) * 0.4, 6.0 + math.cos(rad) * 0.4
        box(K, (x2 - 0.07, y - 0.07, 12.3), (x2 + 0.07, y + 0.07, 12.45), 'metal', (0, 0, -a), (x2, y, 12.37))
    chz(K, -0.35, 0.35, 6.2, 6.45, 12.1, 12.65, 0.08, 'metal_d')
# рукоять: насечка спереди
for row in range(6):
    y = 3.0 + row * 0.5
    cx(P, 1.2, y, y + 0.22, 2.08, 2.16, 'poly_hi')

# ================================================================== руки: точка хвата = pivot, ось +Y кости — от кисти к плечу


def hand(name, parent, grip, toward, length_scale, side_hint=(1, 0, 0)):
    y = np.array(toward, float)
    y /= np.linalg.norm(y)
    h = np.array(side_hint, float)
    xv = h - y * np.dot(h, y)
    xv /= np.linalg.norm(xv)
    zv = np.cross(xv, y)
    Rm = np.stack([xv, y, zv], axis=1)
    bone(name, parent, pivot=grip, rot=euler_from_matrix(Rm), scale=[1, length_scale, 1])


hand('righthand', G, grip=(0.25, 4.1, 3.9), toward=(0.42, -0.36, 0.83), length_scale=1.2)
hand('lefthand', 'mag_and_hand', grip=(-0.2, 6.9, -10.6), toward=(-0.30, -0.40, 0.86), length_scale=1.5)

# ================================================================== точки крепления, камера
bone('muzzle_pos', G, pivot=(0, BORE, -20.5 + DZ))
bone('scope_pos', G, pivot=(0, 12.1, 0.6))
bone('grip_pos', G, pivot=(0, 6.95, -10.3))
bone('stock_pos', G, pivot=(0, 8.9, 7.2))
bone('shell_pos', G, pivot=(1.15, 9.72, 1.9))             # окно выброса гильз
bone('camera', pivot=(0, 13.4, 16.0))
bone('idle_view', pivot=(-3.25, 13.4, 16.1))
bone('iron_view', pivot=(0, 13.3, 14.6))

# ------------------------------------------------------------------ ширина как в эталоне TACZ
# коробка 2.1 px, цевьё ~2.2 px, рукоять 1.25 px, магазин 1.1 px. Сужаем только по x;
# круглые детали вдоль ствола (восьмигранники вокруг оси z) не трогаем.


def narrow(bn, k, zrange=None):
    for c in _by[bn]['cubes']:
        z0, z1 = c['f'][2], c['t'][2]
        if zrange and not (z1 > zrange[0] and z0 < zrange[1]):
            continue
        r = c.get('r')
        if r is None or (r[0] != 0 and r[1] == 0 and r[2] == 0):
            # без поворота или поворот вокруг x: масштаб x коммутирует с поворотом
            c['f'][0] = round(c['f'][0] * k, 4)
            c['t'][0] = round(c['t'][0] * k, 4)
            if r is not None:
                c['o'][0] = round(c['o'][0] * k, 4)
        else:
            # повёрнутый куб (фаска, восьмигранник): сдвигаем целиком, форму сохраняем
            ox = c['o'][0]
            if abs(ox) < 1e-6:
                continue
            dx = ox * k - ox
            c['f'][0] = round(c['f'][0] + dx, 4)
            c['t'][0] = round(c['t'][0] + dx, 4)
            c['o'][0] = round(ox + dx, 4)


narrow(G, 0.73, (HG0 - 0.5, HG1 + 0.1))         # цевьё 3.0 -> 2.2
narrow(G, 0.875, (HG1 + 0.1, 99))               # коробки 2.4 -> 2.1
narrow(G, 0.875, (-99, HG0 - 0.5))
narrow('pgrip', 0.55)                           # рукоять 2.3 -> 1.25
narrow('magazine', 0.6)                         # магазин 1.8 -> 1.1
narrow('mag_lower', 0.6)
narrow('stock_default', 0.875)
narrow('charge_handle', 0.875)

# ------------------------------------------------------------------ высота как в эталоне TACZ
# над осью ствола: планка 2.6 -> 1.75 px; цевьё под осью: 2.7 -> 1.5 px; рукоять 4.5 -> 6 px.
KU = 1.75 / 2.6
KL = 1.5 / 2.7


def fy(y, z, lower_hg=True):
    if y >= BORE:
        return BORE + (y - BORE) * KU
    if lower_hg and HG0 - 0.5 <= z <= HG1 + 0.1:
        return BORE - (BORE - y) * KL
    return y


def squash(bn, lower_hg=True, shift_only=None):
    for c in _by[bn]['cubes']:
        zc = (c['f'][2] + c['t'][2]) / 2
        if shift_only is not None:
            c['f'][1] = round(c['f'][1] + shift_only, 4); c['t'][1] = round(c['t'][1] + shift_only, 4)
            if 'o' in c:
                c['o'][1] = round(c['o'][1] + shift_only, 4)
            continue
        if 'r' in c and not (c['r'][0] != 0 and c['r'][1] == 0 and c['r'][2] == 0):
            oy = c['o'][1]
            dy = fy(oy, zc, lower_hg) - oy
            c['f'][1] = round(c['f'][1] + dy, 4); c['t'][1] = round(c['t'][1] + dy, 4); c['o'][1] = round(oy + dy, 4)
        else:
            c['f'][1] = round(fy(c['f'][1], zc, lower_hg), 4)
            c['t'][1] = round(fy(c['t'][1], zc, lower_hg), 4)
            if 'o' in c:
                c['o'][1] = round(fy(c['o'][1], zc, lower_hg), 4)


squash(G)
squash('stock_default', lower_hg=False)
squash('charge_handle', lower_hg=False)
_by['charge_handle']['pivot'][1] = round(fy(_by['charge_handle']['pivot'][1], 7.0, False), 4)
RAIL_DY = fy(12.1, 0) - 12.1                      # прицельные стоят на планке — опускаем целиком
for sb in ('sight_front', 'sight_rear'):
    if sb not in _by:
        continue
    squash(sb, shift_only=RAIL_DY)
    _by[sb]['pivot'][1] = round(_by[sb]['pivot'][1] + RAIL_DY, 4)
# рукоять длиннее вниз (в своей системе, от верха 6.8)
for c in _by['pgrip']['cubes']:
    for k in ('f', 't', 'o'):
        if k in c:
            c[k][1] = round(6.8 - (6.8 - c[k][1]) * (6.0 / 4.5), 4)
# точки: прицел, рукоятка, левая/правая рука, глаз в прицеле
_by['scope_pos']['pivot'][1] = round(fy(12.1, 0.6), 4)
_by['grip_pos']['pivot'][1] = round(fy(6.95, -10.3), 4)
_by['iron_view']['pivot'][1] = round(13.3 + RAIL_DY, 4)
LEFT_Y = round(fy(6.9, -10.6), 4)
_by['lefthand']['pivot'][1] = LEFT_Y


# ------------------------------------------------------------------ запись
cubes = sum(len(b['cubes']) for b in bones)
data = {
    'texture': f'battlegrounds:textures/geo/{GUN_ID}.png',
    'texture_size': [64, 64],
    'swatch': 8,
    'third_person_scale': 0.6,
    'gui_scale': 0.42,
    # гильза 5.56: размер (px), цвет корпуса и донца; вылет в системе оружия (px/с), как у TACZ
    'shell': {'size': [0.42, 0.42, 1.75], 'swatch': SLOT['brass'], 'swatch_end': SLOT['brass_d'],
              'velocity': [70, 28, 10], 'random': [15, 15, 4], 'gravity': -160,
              'spin': [360, -1200, 90], 'life': 1.0},
    'bones': bones,
}
os.makedirs(os.path.join(ROOT, 'geo'), exist_ok=True)
os.makedirs(os.path.join(ROOT, 'textures', 'geo'), exist_ok=True)
json.dump(data, open(os.path.join(ROOT, 'geo', GUN_ID + '.json'), 'w'), separators=(',', ':'))
make_texture(os.path.join(ROOT, 'textures', 'geo', GUN_ID + '.png'))
if __name__ == '__main__':
    print('bones', len(bones), 'cubes', cubes)
    for b in bones:
        if b['cubes']:
            print(f"  {b['name']:16s} {len(b['cubes'])}")
