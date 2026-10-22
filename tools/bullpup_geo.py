"""Буллпапы: AUG, QBZ (QBZ-95), FAMAS, Groza — свои кубы; размеры AUG и QBZ — по эталонам TACZ,
FAMAS и Groza — по чертежам и пропорциям реальных образцов.

У всех: магазин позади рукояти (кость magazine под mag_and_hand), левая рука на цевье/передней рукояти,
правая — на пистолетной рукояти, точки камеры/прицела/дула/выброса гильз.
python3 tools/bullpup_geo.py [aug|qbz|famas|groza]   (без аргумента — все)
"""
import math, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from gunkit import Gun

BASE_PAL = {
    'body': (40, 42, 40), 'body_hi': (52, 54, 52), 'body_lo': (30, 31, 30), 'dark': (15, 15, 16),
    'metal': (60, 62, 65), 'metal_d': (44, 45, 48), 'metal_hi': (84, 86, 90),
    'rail': (30, 31, 33), 'rail_hi': (42, 43, 46), 'mag': (38, 39, 41), 'mag_hi': (52, 53, 56),
    'rubber': (22, 22, 23), 'brass': (176, 140, 72), 'brass_d': (140, 108, 52), 'sight': (18, 18, 19),
    'tritium': (120, 220, 120), 'glass': (60, 70, 66),
}


def slices(g, bn, z0, z1, yb, yt, hw, sw, step=0.6, c=0.35):
    """Корпус срезами вдоль z: yb(z), yt(z), hw(z) — функции профиля."""
    z = z0
    while z < z1 - 1e-6:
        za = min(z1, z + step)
        zm = (z + za) / 2
        g.chz(bn, -hw(zm), hw(zm), yb(zm), yt(zm), z, za, min(c, (yt(zm) - yb(zm)) / 3), sw)
        z = za


def lerp(a, b, t):
    return a + (b - a) * max(0.0, min(1.0, t))


def flip_sights(g, parent, zf, zr, ytop, base_y):
    """Откидные мушка и целик на верхней планке (кости sight_front / sight_rear — прячутся с прицелом)."""
    SF = g.bone('sight_front', parent, pivot=(0, base_y + 0.5, zf))
    g.chz(SF, -0.55, 0.55, base_y, base_y + 0.35, zf - 0.55, zf + 0.55, 0.1, 'sight')
    for x0, x1 in ((-0.5, -0.2), (0.2, 0.5)):
        g.box(SF, (x0, base_y + 0.35, zf - 0.3), (x1, ytop + 0.25, zf + 0.3), 'sight')
    g.octy(SF, 0, zf, 0.08, base_y + 0.35, ytop, 'sight')
    g.box(SF, (-0.03, ytop - 0.12, zf - 0.09), (0.03, ytop - 0.06, zf - 0.07), 'tritium')
    SR = g.bone('sight_rear', parent, pivot=(0, base_y + 0.5, zr))
    g.chz(SR, -0.55, 0.55, base_y, base_y + 0.4, zr - 0.6, zr + 0.6, 0.1, 'sight')
    g.box(SR, (-0.55, base_y + 0.4, zr - 0.2), (-0.22, ytop + 0.3, zr + 0.2), 'sight')
    g.box(SR, (0.22, base_y + 0.4, zr - 0.2), (0.55, ytop + 0.3, zr + 0.2), 'sight')
    g.box(SR, (-0.55, base_y + 0.4, zr - 0.2), (0.55, ytop - 0.25, zr + 0.2), 'sight')
    for a in range(0, 360, 45):                                            # кольцо диоптра
        r = math.radians(a)
        x, y = math.sin(r) * 0.22, ytop + math.cos(r) * 0.22
        g.box(SR, (x - 0.05, y - 0.03, zr - 0.05), (x + 0.05, y + 0.03, zr + 0.05), 'sight', (0, 0, -a), (x, y, zr))


def common_points(g, G, root_piv, B, muzzle_z, scope, idle, iron, shell, rgrip, lgrip, magpiv, rtoward=(0.42, -0.36, 0.83),
                  ltoward=(-0.30, -0.40, 0.86), lscale=1.5):
    g.hand('righthand', G, grip=rgrip, toward=rtoward, length_scale=1.2)
    g.hand('lefthand', 'mag_and_hand', grip=lgrip, toward=ltoward, length_scale=lscale)
    g.bone('muzzle_pos', G, pivot=(0, B, muzzle_z))
    g.bone('scope_pos', G, pivot=scope)
    g.bone('grip_pos', G, pivot=(0, lgrip[1] - 0.6, lgrip[2]))
    g.bone('shell_pos', G, pivot=shell)
    g.bone('camera', pivot=(0, iron[1], idle[2]))
    g.bone('idle_view', pivot=idle)
    g.bone('iron_view', pivot=iron)


def mag_box(g, M, z0, z1, ytop, ybot, hw, curve=0.0, sw='mag', ribs=True, brass_window=False):
    """Магазин из сегментов (сверху вниз), низ чуть вперёд при curve>0."""
    n = 6
    for i in range(n):
        y1 = ytop - i * (ytop - ybot) / n
        y0 = y1 - (ytop - ybot) / n - 0.15
        dz = -curve * (i / n) ** 1.5
        ang = -curve * 6 * (i / n)
        o = (0, (y0 + y1) / 2, (z0 + z1) / 2 + dz)
        g.box(M, (-hw, y0, z0 + dz), (hw, y1, z1 + dz), sw, (ang, 0, 0) if ang else None, o if ang else None)
        if ribs and i in (1, 3):
            g.box(M, (-hw - 0.05, y0 + 0.1, z0 + dz + 0.3), (hw + 0.05, y1 - 0.1, z1 + dz - 0.3), 'mag_hi', (ang, 0, 0) if ang else None, o if ang else None)
    g.box(M, (-hw - 0.08, ybot - 0.3 - 0.15, z0 - curve - 0.1), (hw + 0.08, ybot - 0.15, z1 - curve + 0.1), 'mag_hi')
    if brass_window:
        g.box(M, (hw - 0.02, ytop - 2.8, z0 + 0.5), (hw + 0.02, ytop - 0.6, z1 - 0.6), 'brass_d')
    g.octz(M, 0, ytop + 0.15, 0.22, z0 + 0.3, z1 - 0.3, 'brass')


# ======================================================================================= AUG
def build_aug():
    pal = dict(BASE_PAL)
    pal.update({'body': (52, 58, 46), 'body_hi': (64, 70, 58), 'body_lo': (40, 45, 36), 'mag': (50, 54, 52), 'mag_hi': (62, 66, 64)})
    g = Gun('aug', pal)
    B = 8.9
    root = g.bone('root', pivot=(0, 7.9, 5.8))
    G = g.bone('gun', root, pivot=(0, B, 0))
    # ствол и пламегаситель
    g.octz(G, 0, B, 0.42, -14.1, -6.9, 'metal_d')
    g.octz(G, 0, B, 0.5, -11.9, -11.5, 'metal')
    MD = g.bone('muzzle_default', G, pivot=(0, B, -14.5))
    g.octz(MD, 0, B, 0.46, -17.0, -14.08, 'metal_d')
    for z in (-16.6, -15.9, -15.2):
        g.box(MD, (-0.47, B - 0.08, z), (0.47, B + 0.08, z + 0.4), 'dark')
    g.octz(MD, 0, B, 0.24, -17.02, -16.98, 'dark')
    # ствольная коробка (металл) с газовой трубкой справа
    g.chz(G, -1.1, 1.1, 6.95, 9.4, -6.9, 2.1, 0.3, 'metal_d')
    g.box(G, (0.44, 7.87, -7.7), (1.06, 8.5, 2.6), 'metal')
    g.octx(G, 8.2, -5.5, 0.18, -1.15, 1.15, 'metal')
    # верхняя планка
    g.chz(G, -0.5, 0.5, 9.4, 10.0, -4.96, 6.22, 0.12, 'rail')
    g.pic_top(G, -4.96, 6.22, 10.62, w=1.0)
    # рукоять заряжания слева (кость)
    g.box(G, (-1.14, 8.6, -4.2), (-1.08, 9.3, 2.2), 'dark')
    CH = g.bone('charge_handle', G, pivot=(-1.5, 9.05, -3.5))
    g.chz(CH, -1.77, -1.1, 8.6, 9.5, -4.0, -3.0, 0.15, 'metal')
    g.octx(CH, 9.05, -3.5, 0.3, -1.85, -1.7, 'body')
    # полимерный корпус
    def yb(z):
        if z < 6.0:
            return 5.7
        return lerp(5.7, 2.4, (z - 6.0) / 12.0)
    def yt(z):
        return lerp(9.2, 10.35, (z - 2.0) / 10.0)
    def hw(z):
        return lerp(1.05, 1.45, (z + 2.0) / 6.0)
    slices(g, G, -2.0, 19.4, yb, yt, hw, 'body')
    g.chz(G, -1.3, 1.3, 2.3, 10.4, 19.4, 19.7, 0.35, 'rubber')            # затыльник
    for y in (3.2, 4.5, 5.8, 7.1, 8.4):
        g.box(G, (-1.2, y, 19.65), (1.2, y + 0.4, 19.75), 'dark')
    g.box(G, (-1.47, 6.3, 12.6), (1.47, 7.6, 17.6), 'body_hi')            # выступы-рёбра
    g.box(G, (1.42, 7.9, 8.1), (1.6, 9.5, 12.2), 'body_lo')                # крышка окна выброса (справа)
    g.box(G, (-1.6, 7.9, 8.1), (-1.42, 9.5, 12.2), 'body_lo')
    g.octx(G, 8.3, 17.7, 0.3, -1.5, 1.5, 'metal')                           # штифт
    # шахта магазина
    g.box(G, (-1.1, 5.0, 7.7), (1.1, 5.75, 12.4), 'body_lo')
    # рукоять и большая спусковая скоба
    P = g.bone('pgrip', G, pivot=(0, 5.5, 2.0))
    g.slab(P, -0.68, 0.68, 1.0, 6.4, 1.2, 3.6, 'body', -12, c=0.25)
    for k in range(6):
        g.slab(P, -0.72, 0.72, 1.6 + k * 0.75, 1.9 + k * 0.75, 1.25, 3.55, 'body_hi', -12)
    g.chz(P, -0.69, 0.69, 0.45, 1.05, -4.17, 3.6, 0.2, 'body')              # низ скобы
    g.chz(P, -0.6, 0.6, 1.0, 5.7, -4.17, -3.55, 0.2, 'body')                # передняя стойка
    g.slab(G, -0.18, 0.18, 5.9, 7.3, 1.2, 1.5, 'metal', -10)                # спуск
    # складная передняя рукоять
    FG = g.bone('foregrip', G, pivot=(0, 6.9, -5.1))
    g.chz(FG, -0.72, 0.72, 2.56, 6.91, -6.11, -4.1, 0.3, 'body')
    for y in (3.2, 4.0, 4.8, 5.6):
        g.box(FG, (-0.74, y, -6.0), (0.74, y + 0.3, -4.2), 'body_hi')
    g.octx(FG, 6.5, -5.1, 0.25, -0.75, 0.75, 'metal')
    flip_sights(g, G, -4.3, 5.4, 12.0, 10.62)
    # магазин (полупрозрачный)
    MH = g.bone('mag_and_hand', root, pivot=(0, 8.4, 10.0))
    M = g.bone('magazine', MH, pivot=(0, 8.4, 10.0))
    mag_box(g, M, 7.95, 12.15, 8.5, 1.05, 0.96, curve=0.6, brass_window=True)
    common_points(g, G, root, B, -17.0, (0, 10.62, 3.5), (-2.75, 12.06, 13.5), (0, 12.06, 10.5),
                  (1.4, 8.9, 10.2), rgrip=(0.2, 4.3, 2.6), lgrip=(-0.1, 4.8, -5.1), magpiv=(0, 8.4, 10.0),
                  ltoward=(-0.35, -0.75, 0.55))
    return g


# ======================================================================================= QBZ-95
def build_qbz():
    pal = dict(BASE_PAL)
    pal.update({'body': (38, 40, 38), 'body_hi': (50, 52, 50)})
    g = Gun('qbz', pal)
    B = 9.6
    root = g.bone('root', pivot=(0, 8.0, 2.4))
    G = g.bone('gun', root, pivot=(0, B, 0))
    g.octz(G, 0, B, 0.34, -13.95, -8.4, 'metal_d')
    g.octz(G, 0, B, 0.56, -10.2, -9.94, 'metal')
    g.octz(G, 0, B, 0.56, -8.88, -8.62, 'metal')
    MD = g.bone('muzzle_default', G, pivot=(0, B, -15.0))
    g.octz(MD, 0, B, 0.55, -17.5, -13.94, 'metal_d')
    for z in (-17.0, -16.2, -15.4):
        g.box(MD, (-0.56, B - 0.08, z), (0.56, B + 0.08, z + 0.45), 'dark')
    g.octz(MD, 0, B, 0.26, -17.52, -17.48, 'dark')
    # передняя часть корпуса (цевьё) с рифлением
    g.chz(G, -1.0, 1.0, 7.88, 10.88, -8.38, 8.0, 0.35, 'body')
    for z in [-7.2 + 0.8 * i for i in range(8)]:
        g.box(G, (-1.06, 8.4, z), (1.06, 10.4, z + 0.35), 'body_hi')
    g.chz(G, -0.46, 0.46, 7.6, 7.95, -7.75, -2.0, 0.1, 'rail')            # планка снизу
    # ручка для переноски с планкой и прицельными
    g.chz(G, -0.8, 0.8, 10.88, 11.4, -8.3, 8.6, 0.15, 'body')
    g.chz(G, -0.4, 0.4, 11.4, 13.4, -8.3, -7.6, 0.12, 'body')               # передняя стойка
    g.chz(G, -0.6, 0.6, 11.4, 13.0, 7.2, 8.6, 0.15, 'body')                 # задняя стойка
    g.chz(G, -0.42, 0.42, 13.0, 13.4, -7.9, 8.4, 0.12, 'body')              # верх ручки
    g.pic_top(G, -0.3, 6.9, 13.75, w=0.76)
    flip_sights(g, G, -8.0, 7.4, 14.2, 13.4)
    # рукоять заряжания сверху под ручкой
    CH = g.bone('charge_handle', G, pivot=(0, 11.7, 0.3))
    g.chz(CH, -0.41, 0.41, 10.75, 12.68, -0.19, 0.81, 0.12, 'metal')
    g.box(G, (-0.15, 10.85, -1.0), (0.15, 10.9, 5.0), 'dark')
    # задняя часть: нижняя коробка, щека, затыльник
    slices(g, G, 7.8, 18.75, lambda z: lerp(4.6, 4.8, (z - 8) / 10), lambda z: lerp(12.0, 12.8, (z - 8) / 10),
           lambda z: 1.06, 'body')
    g.chz(G, -1.19, 1.19, 6.26, 11.93, 18.75, 19.62, 0.3, 'rubber')
    g.box(G, (1.04, 9.4, 9.0), (1.1, 10.6, 12.5), 'dark')                   # окно выброса справа
    g.octx(G, 8.56, 18.1, 0.4, -1.25, 1.25, 'metal')
    g.box(G, (-1.08, 6.0, 13.0), (1.08, 7.5, 17.5), 'body_hi')
    # шахта и рукоять
    g.box(G, (-0.95, 5.6, 6.3), (0.95, 7.9, 12.6), 'body_lo')
    P = g.bone('pgrip', G, pivot=(0, 6.2, 2.4))
    g.slab(P, -0.56, 0.62, 3.5, 7.9, 0.2, 2.6, 'body', -14, c=0.2)
    for k in range(5):
        g.slab(P, -0.6, 0.66, 3.9 + k * 0.75, 4.2 + k * 0.75, 0.25, 2.55, 'body_hi', -14)
    g.box(G, (-0.45, 4.8, -0.8), (0.45, 5.2, 5.5), 'body')                  # скоба
    g.box(G, (-0.45, 4.8, -0.8), (0.45, 7.9, -0.4), 'body')
    g.slab(G, -0.15, 0.15, 5.6, 7.0, 0.6, 0.9, 'metal', -10)
    MH = g.bone('mag_and_hand', root, pivot=(0, 8.6, 9.5))
    M = g.bone('magazine', MH, pivot=(0, 8.6, 9.5))
    mag_box(g, M, 6.5, 9.4, 8.9, 0.3, 0.6, curve=0.9)
    common_points(g, G, root, B, -17.5, (0, 13.75, 3.88), (-2.91, 14.51, 14.4), (0, 13.94, 12.5),
                  (1.1, 10.0, 10.5), rgrip=(0.2, 5.6, 1.8), lgrip=(-0.2, 8.25, -5.0), magpiv=(0, 8.6, 9.5))
    return g


# ======================================================================================= FAMAS
def build_famas():
    pal = dict(BASE_PAL)
    pal.update({'body': (36, 37, 36), 'body_hi': (48, 49, 48)})
    g = Gun('famas', pal)
    B = 9.0
    root = g.bone('root', pivot=(0, 7.6, 3.0))
    G = g.bone('gun', root, pivot=(0, B, 0))
    g.octz(G, 0, B, 0.36, -13.5, -7.0, 'metal_d')
    for z in (-12.9, -12.3, -11.7):                                          # кольца под ружейную гранату
        g.octz(G, 0, B, 0.55, z, z + 0.25, 'metal')
    MD = g.bone('muzzle_default', G, pivot=(0, B, -14.5))
    g.octz(MD, 0, B, 0.42, -15.6, -13.5, 'metal_d')
    g.octz(MD, 0, B, 0.22, -15.62, -15.58, 'dark')
    # корпус: перед (цевьё) и тело до затыльника
    g.chz(G, -0.95, 0.95, 7.3, 10.3, -7.0, 7.5, 0.35, 'body')
    for z in [-6.4 + 0.9 * i for i in range(6)]:
        g.box(G, (-1.0, 7.8, z), (1.0, 8.6, z + 0.45), 'dark')
    slices(g, G, 7.5, 17.6, lambda z: lerp(4.2, 4.0, (z - 7.5) / 10), lambda z: lerp(10.3, 11.0, (z - 7.5) / 10),
           lambda z: lerp(1.0, 1.15, (z - 7.5) / 3), 'body')
    g.chz(G, -1.2, 1.2, 3.8, 11.1, 17.6, 18.2, 0.35, 'rubber')
    g.box(G, (1.1, 8.6, 8.5), (1.16, 9.7, 11.5), 'dark')                    # окно выброса справа
    g.box(G, (-1.16, 8.6, 8.5), (-1.1, 9.7, 11.5), 'body_lo')              # и слева (под левшу, закрыто)
    # длинная ручка для переноски сверху — «рожок» FAMAS
    g.chz(G, -0.55, 0.55, 10.3, 11.0, -6.8, 9.5, 0.15, 'body')
    g.chz(G, -0.45, 0.45, 11.0, 12.6, -6.8, -5.9, 0.12, 'body')
    g.chz(G, -0.55, 0.55, 11.0, 12.9, 8.3, 9.5, 0.15, 'body')
    g.chz(G, -0.5, 0.5, 12.6, 13.05, -6.8, 9.5, 0.15, 'body')
    g.box(G, (-0.3, 11.0, -5.2), (0.3, 12.6, 7.6), 'dark')                  # проём ручки
    flip_sights(g, G, -6.3, 8.9, 13.3, 13.05)
    # сошки, сложенные вдоль корпуса
    for s in (-1, 1):
        g.box(G, (1.0 * s - (0.18 if s > 0 else 0.0), 9.6, -6.6), (1.0 * s + (0.0 if s > 0 else 0.18), 9.85, 1.0), 'metal_d')
    # рукоять заряжания под ручкой
    CH = g.bone('charge_handle', G, pivot=(0, 10.8, 0.5))
    g.chz(CH, -0.55, 0.55, 10.4, 11.3, 0.0, 1.0, 0.15, 'metal')
    # рукоять, полная скоба, шахта
    P = g.bone('pgrip', G, pivot=(0, 5.8, 1.5))
    g.slab(P, -0.6, 0.6, 2.4, 7.3, 0.8, 3.0, 'body', -14, c=0.2)
    g.chz(G, -0.55, 0.55, 2.0, 2.5, -2.8, 3.2, 0.15, 'body')
    g.chz(G, -0.5, 0.5, 2.0, 7.3, -2.8, -2.3, 0.15, 'body')
    g.slab(G, -0.15, 0.15, 5.4, 6.8, 0.0, 0.3, 'metal', -10)
    g.box(G, (-0.9, 5.4, 4.8), (0.9, 7.3, 8.8), 'body_lo')
    MH = g.bone('mag_and_hand', root, pivot=(0, 8.2, 6.8))
    M = g.bone('magazine', MH, pivot=(0, 8.2, 6.8))
    mag_box(g, M, 5.0, 8.6, 8.2, 2.5, 0.6, curve=0.3)
    common_points(g, G, root, B, -15.6, (0, 13.05, 3.5), (-2.8, 13.9, 14.0), (0, 13.3, 12.0),
                  (1.2, 9.2, 10.0), rgrip=(0.2, 4.9, 1.9), lgrip=(-0.2, 7.6, -4.2), magpiv=(0, 8.2, 6.8))
    return g


# ======================================================================================= Groza (ОЦ-14)
def build_groza():
    pal = dict(BASE_PAL)
    g = Gun('groza', pal)
    B = 8.6
    root = g.bone('root', pivot=(0, 7.4, 2.0))
    G = g.bone('gun', root, pivot=(0, B, 0))
    g.octz(G, 0, B, 0.4, -12.0, -4.0, 'metal_d')
    MD = g.bone('muzzle_default', G, pivot=(0, B, -13.0))
    g.octz(MD, 0, B, 0.5, -14.6, -12.0, 'metal_d')                           # дульный компенсатор
    g.box(MD, (-0.51, B + 0.15, -14.4), (0.51, B + 0.52, -13.8), 'dark')
    g.octz(MD, 0, B, 0.24, -14.62, -14.58, 'dark')
    # газовая трубка и цевьё с рукоятью-скобой спереди (особенность «Грозы»)
    g.octz(G, 0, 9.55, 0.3, -9.5, -4.0, 'metal')
    g.chz(G, -0.85, 0.85, 7.2, 10.2, -8.0, -1.0, 0.3, 'body')
    for z in (-7.3, -6.3, -5.3, -4.3):
        g.box(G, (-0.9, 8.0, z), (0.9, 9.4, z + 0.4), 'body_hi')
    P = g.bone('pgrip', G, pivot=(0, 5.6, -2.4))
    g.slab(P, -0.62, 0.62, 2.0, 7.2, -3.6, -1.5, 'body', -12, c=0.2)          # рукоять впереди магазина
    g.chz(G, -0.55, 0.55, 1.5, 2.1, -6.2, -1.4, 0.15, 'body')               # большая скоба до цевья
    g.chz(G, -0.5, 0.5, 1.5, 7.2, -6.2, -5.7, 0.15, 'body')
    g.slab(G, -0.15, 0.15, 5.4, 6.8, -4.3, -4.0, 'metal', -10)
    # ствольная коробка АК (сталь) и приклад-затыльник
    g.chz(G, -0.82, 0.82, 6.9, 10.4, -1.0, 13.5, 0.25, 'metal_d')
    for z in (1.0, 3.0, 5.0, 7.0, 9.0, 11.0):
        g.box(G, (-0.85, 9.2, z), (0.85, 9.7, z + 0.3), 'metal')
    g.box(G, (0.8, 8.9, 5.0), (0.84, 9.6, 8.6), 'dark')
    slices(g, G, 10.5, 17.0, lambda z: lerp(5.4, 4.6, (z - 10.5) / 6), lambda z: 10.2, lambda z: 0.95, 'body')
    g.chz(G, -1.0, 1.0, 4.4, 10.3, 17.0, 17.6, 0.3, 'rubber')
    # верх: ручка с прицельными
    g.chz(G, -0.45, 0.45, 10.4, 12.6, -2.0, -1.2, 0.12, 'body')
    g.chz(G, -0.45, 0.45, 10.4, 12.6, 9.4, 10.2, 0.12, 'body')
    g.chz(G, -0.5, 0.5, 12.2, 12.8, -2.0, 10.2, 0.15, 'body')
    g.pic_top(G, 0.0, 8.0, 13.2, w=0.9)
    flip_sights(g, G, -1.6, 9.8, 13.6, 12.8)
    # рукоять затворной рамы справа (как у АК)
    CH = g.bone('charge_handle', G, pivot=(1.4, 9.2, 6.0))
    g.box(CH, (0.84, 8.95, 5.7), (1.6, 9.45, 6.3), 'metal')
    g.octx(CH, 9.2, 6.0, 0.32, 1.6, 2.0, 'metal_hi')
    # шахта под изогнутый магазин АК
    g.box(G, (-0.8, 5.8, 0.2), (0.8, 6.95, 3.6), 'metal_d')
    MH = g.bone('mag_and_hand', root, pivot=(0, 7.8, 2.0))
    M = g.bone('magazine', MH, pivot=(0, 7.8, 0.3))
    SEG = [(0, 6.2, 7.8, 0.3, 3.3), (-12.5, 4.5, 6.6, 0.0, 3.0), (-22.5, 2.9, 4.9, -0.6, 2.4), (-32.5, 1.4, 3.3, -1.5, 1.6),
           (-45, 0.1, 1.9, -2.6, 0.5)]
    for ang, y0, y1, z0, z1 in SEG:
        o = (0, (y0 + y1) / 2, (z0 + z1) / 2)
        g.box(M, (-0.5, y0 - 0.25, z0), (0.5, y1 + 0.25, z1), 'mag', (ang, 0, 0) if ang else None, o if ang else None)
        g.box(M, (-0.56, y0 + 0.2, z0 + 0.3), (0.56, y1 - 0.2, z0 + 0.55), 'mag_hi', (ang, 0, 0) if ang else None, o if ang else None)
    g.octz(M, 0, 7.95, 0.2, 0.6, 3.0, 'brass')
    common_points(g, G, root, B, -14.6, (0, 13.2, 4.0), (-2.6, 13.6, 13.6), (0, 13.45, 11.5),
                  (0.9, 9.2, 7.0), rgrip=(0.2, 4.5, -2.3), lgrip=(-0.2, 7.9, -6.3), magpiv=(0, 7.8, 2.0))
    return g


BUILDERS = {'aug': build_aug, 'qbz': build_qbz, 'famas': build_famas, 'groza': build_groza}
SHELL = {
    'aug': [0.42, 0.42, 1.75], 'qbz': [0.42, 0.42, 1.75], 'famas': [0.42, 0.42, 1.75], 'groza': [0.5, 0.5, 1.5],
}

if __name__ == '__main__':
    for gid in (sys.argv[1:] or BUILDERS):
        g = BUILDERS[gid]()
        n = g.save({
            'third_person_scale': 0.6,
            'gui_scale': 0.42,
            'shell': {'size': SHELL[gid], 'swatch': g.slot['brass'], 'swatch_end': g.slot['brass_d'],
                      'velocity': [70, 28, 10], 'random': [15, 15, 4], 'gravity': -160,
                      'spin': [360, -1200, 90], 'life': 1.0},
        })
        print(gid, 'bones', len(g.bones), 'cubes', n)
