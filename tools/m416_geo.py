"""Модель M416 для Battlegrounds: средняя детализация, кости для анимаций, точки камеры и рук.

Запуск: python3 tools/m416_geo.py  -> assets/battlegrounds/geo/m416.json + textures/geo/m416.png
Координаты: пиксели, x вправо, y вверх, ствол в -z. Ось канала ствола: x=0, y=9.5.
"""
import json, os, random, sys
import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from geolib import rot_xyz, euler_from_matrix

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'battlegrounds')

# ------------------------------------------------------------------ палитра (ячейки 8x8 на текстуре 64x64)
SW = {
    'rec': (40, 42, 45),      # ствольная коробка
    'rec_hi': (52, 55, 59),   # светлые грани/кромки
    'dark': (25, 26, 28),     # самые тёмные детали, щели
    'hg': (36, 38, 41),       # цевьё
    'rail': (31, 33, 36),     # планки
    'metal': (62, 65, 69),    # сталь (ствол, штырьки)
    'metal_d': (48, 50, 53),  # тёмная сталь
    'poly': (34, 34, 36),     # пластик рукояти/приклада
    'rubber': (22, 22, 23),   # затыльник
    'mag': (38, 39, 40),      # магазин
    'mag_hi': (50, 51, 53),
    'sight': (20, 20, 21),
    'brass': (176, 140, 72),
    'glow': (230, 120, 40),
}
NAMES = list(SW.keys())
SLOT = {n: i for i, n in enumerate(NAMES)}


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


# ------------------------------------------------------------------ сборка костей
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


def box(bname, f, t, sw):
    f = [round(min(f[i], t[i]), 4) for i in range(3)]
    t2 = [round(max(f[i], t[i]), 4) for i in range(3)]
    _by[bname]['cubes'].append({'f': f, 't': t2, 's': SLOT[sw]})


def cx(bname, w, y0, y1, z0, z1, sw, x=0.0):
    """Куб по центру x (ширина w)."""
    box(bname, (x - w / 2, y0, z0), (x + w / 2, y1, z1), sw)


def teeth(bname, axis, z0, z1, step, fixed, sw='rail', tooth=0.45):
    """Зубья планки Пикатинни вдоль z. fixed = (x0,x1,y0,y1)."""
    z = z0
    while z + tooth <= z1 + 1e-6:
        x0, x1, y0, y1 = fixed
        box(bname, (x0, y0, z), (x1, y1, z + tooth), sw)
        z += step


BORE = 9.5

bone('root', pivot=(0, 7.0, 6.6))
bone('gun', 'root', pivot=(0, BORE, 0))
G = 'gun'

# --- верхняя ствольная коробка
cx(G, 2.4, 8.6, 11.2, -4.0, 7.0, 'rec')
cx(G, 2.2, 11.2, 11.35, -4.0, 6.8, 'rec_hi')
box(G, (1.2, 9.3, 0.4), (1.27, 10.4, 3.6), 'metal_d')        # крышка окна выброса
box(G, (1.2, 9.25, 0.3), (1.3, 9.35, 3.7), 'rec_hi')
box(G, (1.2, 9.6, 3.7), (1.85, 10.5, 5.0), 'rec')            # досылатель
box(G, (1.85, 9.75, 3.85), (2.0, 10.35, 4.85), 'metal_d')
box(G, (1.2, 9.0, 4.95), (1.75, 10.9, 5.4), 'rec')           # отражатель гильз
box(G, (-1.27, 9.5, -3.2), (-1.2, 10.4, -2.4), 'rec_hi')     # заглушки
# зубья планки на коробке
cx(G, 1.6, 11.35, 11.6, -4.0, 6.8, 'rail')
teeth(G, 'z', -3.85, 6.6, 0.8, (-0.95, 0.95, 11.6, 12.1))

# --- нижняя ствольная коробка
cx(G, 2.3, 6.8, 8.6, -3.6, 6.4, 'rec')
cx(G, 2.6, 5.6, 8.0, -3.95, -0.35, 'rec')                    # горловина магазина
cx(G, 2.9, 5.35, 5.75, -4.1, -0.2, 'rec_hi')                 # раструб
cx(G, 1.0, 5.4, 5.8, -0.35, 3.0, 'rec')                      # спусковая скоба
cx(G, 1.0, 5.4, 6.8, 2.6, 3.0, 'rec')
cx(G, 0.45, 5.95, 6.8, 0.9, 1.3, 'metal')                    # спуск
cx(G, 0.45, 5.7, 6.0, 1.15, 1.5, 'metal')
box(G, (-1.38, 7.45, 3.4), (-1.15, 8.0, 4.6), 'metal')       # переводчик слева
box(G, (1.15, 7.45, 3.6), (1.3, 7.9, 4.3), 'metal_d')        # переводчик справа
box(G, (1.15, 7.25, -0.65), (1.42, 7.9, 0.0), 'metal_d')     # кнопка магазина
box(G, (-1.4, 7.55, -0.25), (-1.15, 8.45, 0.6), 'metal_d')   # затворная задержка
box(G, (-1.3, 7.55, 5.35), (-1.15, 7.95, 5.75), 'metal')     # штифты
box(G, (1.15, 7.55, 5.35), (1.3, 7.95, 5.75), 'metal')
box(G, (-1.3, 7.55, -3.3), (-1.15, 7.95, -2.9), 'metal')

# --- пистолетная рукоять (наклон назад)
bone('pgrip', G, pivot=(0, 6.8, 3.4), rot=(-20, 0, 0))
cx('pgrip', 2.2, 2.6, 6.8, 2.3, 4.6, 'poly')
cx('pgrip', 2.0, 2.3, 2.6, 2.2, 4.8, 'poly')
cx('pgrip', 1.9, 5.7, 6.15, 2.05, 2.3, 'poly')               # выступы под пальцы
cx('pgrip', 1.9, 4.5, 4.95, 2.05, 2.3, 'poly')
cx('pgrip', 1.9, 3.3, 3.75, 2.05, 2.3, 'poly')
cx('pgrip', 2.3, 3.0, 5.6, 4.0, 4.35, 'dark')                # насечка сзади
box('pgrip', (1.1, 3.2, 2.6), (1.16, 6.2, 4.2), 'dark')       # насечка по бокам
box('pgrip', (-1.16, 3.2, 2.6), (-1.1, 6.2, 4.2), 'dark')
cx('pgrip', 2.0, 6.3, 6.8, 4.5, 5.4, 'poly')                 # «бобровый хвост»

# --- цевьё (квадрорельс)
HG0, HG1 = -15.0, -4.0
cx(G, 3.0, 7.7, 11.3, HG0, HG1, 'hg')
cx(G, 3.2, 7.6, 11.4, HG0 - 0.3, HG0, 'dark')               # передний торец
cx(G, 3.2, 7.6, 11.4, HG1 - 0.15, HG1 + 0.05, 'dark')       # стык с коробкой
for z in (-13.2, -11.2, -9.2, -7.2):                         # вентиляция
    box(G, (1.5, 10.25, z), (1.53, 10.85, z + 1.1), 'dark')
    box(G, (-1.53, 10.25, z), (-1.5, 10.85, z + 1.1), 'dark')
    box(G, (1.5, 8.15, z), (1.53, 8.75, z + 1.1), 'dark')
    box(G, (-1.53, 8.15, z), (-1.5, 8.75, z + 1.1), 'dark')
# верхняя планка цевья
cx(G, 1.6, 11.3, 11.6, HG0, HG1 - 0.2, 'rail')
teeth(G, 'z', HG0 + 0.15, HG1 - 0.6, 0.8, (-0.95, 0.95, 11.6, 12.1))
# боковые планки
box(G, (1.5, 9.0, HG0 + 0.4), (1.75, 10.0, HG1 - 0.8), 'rail')
box(G, (-1.75, 9.0, HG0 + 0.4), (-1.5, 10.0, HG1 - 0.8), 'rail')
teeth(G, 'z', HG0 + 0.55, HG1 - 1.2, 0.8, (1.75, 2.15, 8.85, 10.15))
teeth(G, 'z', HG0 + 0.55, HG1 - 1.2, 0.8, (-2.15, -1.75, 8.85, 10.15))
# нижняя планка
cx(G, 1.6, 7.4, 7.7, HG0 + 0.4, HG1 - 0.8, 'rail')
teeth(G, 'z', HG0 + 0.55, HG1 - 1.2, 0.8, (-0.95, 0.95, 6.95, 7.4))

# --- ствол
cx(G, 0.9, BORE - 0.45, BORE + 0.45, -20.5, HG0 - 0.3, 'metal')
cx(G, 1.1, BORE - 0.55, BORE + 0.55, -19.6, -19.2, 'metal_d')  # ступенька

# --- пламегаситель (снимается дульными обвесами)
bone('muzzle_default', G, pivot=(0, BORE, -20.5))
M = 'muzzle_default'
cx(M, 1.3, BORE - 0.65, BORE + 0.65, -23.0, -20.5, 'metal_d')
cx(M, 1.5, BORE - 0.75, BORE + 0.75, -21.1, -20.5, 'metal_d')
for (x0, x1, y0, y1) in [(0.62, 0.68, BORE - 0.15, BORE + 0.15), (-0.68, -0.62, BORE - 0.15, BORE + 0.15),
                         (-0.15, 0.15, BORE + 0.62, BORE + 0.68)]:
    box(M, (x0, y0, -22.6), (x1, y1, -21.4), 'dark')
cx(M, 0.6, BORE - 0.3, BORE + 0.3, -23.05, -23.0, 'dark')

# --- откидные прицельные (прячутся при установке прицела)
bone('sight_front', G, pivot=(0, 11.6, -14.2))
S = 'sight_front'
cx(S, 1.5, 12.1, 12.55, -14.9, -13.4, 'sight')
box(S, (0.25, 12.55, -14.75), (0.65, 13.75, -14.25), 'sight')
box(S, (-0.65, 12.55, -14.75), (-0.25, 13.75, -14.25), 'sight')
cx(S, 0.22, 12.55, 13.3, -14.62, -14.38, 'sight')           # мушка, вершина на 13.3
cx(S, 1.3, 12.55, 12.8, -14.75, -14.25, 'sight')

bone('sight_rear', G, pivot=(0, 11.6, 5.3))
R = 'sight_rear'
cx(R, 1.5, 12.1, 12.7, 4.6, 6.2, 'sight')
box(R, (0.35, 12.7, 5.0), (0.75, 13.95, 5.6), 'sight')
box(R, (-0.75, 12.7, 5.0), (-0.35, 13.95, 5.6), 'sight')
cx(R, 1.5, 13.75, 13.95, 5.0, 5.6, 'sight')
cx(R, 0.7, 12.7, 12.95, 5.0, 5.6, 'sight')                   # диоптр: центр ~13.3
box(R, (0.75, 12.8, 5.2), (0.95, 13.2, 5.6), 'sight')        # барабанчик

# --- рукоять заряжания
bone('charge_handle', G, pivot=(0, 10.7, 7.0))
C = 'charge_handle'
cx(C, 0.6, 10.45, 10.85, 6.4, 7.05, 'metal_d')
cx(C, 2.6, 10.4, 11.0, 7.0, 7.55, 'rec')
box(C, (-1.6, 10.45, 7.0), (-1.3, 10.95, 7.5), 'rec_hi')

# --- приклад (снимается тактическим прикладом)
bone('stock_default', G, pivot=(0, 9.6, 6.4))
K = 'stock_default'
cx(K, 1.5, 8.85, 10.35, 6.4, 12.0, 'metal_d')                 # буферная трубка
cx(K, 1.8, 8.7, 10.5, 6.4, 6.9, 'metal')                      # гайка
cx(K, 2.3, 7.0, 10.5, 8.6, 13.0, 'poly')                      # корпус приклада
cx(K, 2.0, 10.5, 10.85, 9.6, 13.0, 'poly')                    # щека
cx(K, 2.2, 6.4, 7.0, 10.6, 13.0, 'poly')                      # нижний «хвост»
cx(K, 2.4, 6.2, 10.95, 13.0, 13.6, 'rubber')                   # затыльник
cx(K, 0.8, 8.3, 8.75, 8.9, 10.2, 'metal_d')                   # рычаг регулировки
box(K, (1.15, 9.3, 11.9), (1.4, 9.95, 12.6), 'metal')         # антабка
box(K, (1.15, 7.6, 9.4), (1.2, 10.3, 10.0), 'dark')           # прорези
box(K, (-1.2, 7.6, 9.4), (-1.15, 10.3, 10.0), 'dark')
box(K, (1.15, 7.6, 11.0), (1.2, 10.3, 11.6), 'dark')
box(K, (-1.2, 7.6, 11.0), (-1.15, 10.3, 11.6), 'dark')

# --- магазин вместе с левой рукой (двигаются при перезарядке)
bone('mag_and_hand', 'root', pivot=(0, 7.6, -2.15))
bone('magazine', 'mag_and_hand', pivot=(0, 7.6, -2.15))
cx('magazine', 1.8, 2.2, 7.6, -3.55, -0.75, 'mag')
for y in (2.8, 3.8, 4.8):                                     # рёбра
    box('magazine', (0.9, y, -3.3), (0.97, y + 0.35, -1.0), 'mag_hi')
    box('magazine', (-0.97, y, -3.3), (-0.9, y + 0.35, -1.0), 'mag_hi')
bone('mag_lower', 'magazine', pivot=(0, 2.4, -2.15), rot=(10, 0, 0))
cx('mag_lower', 1.8, -0.8, 2.45, -3.55, -0.75, 'mag')
for y in (-0.2, 0.8, 1.7):
    box('mag_lower', (0.9, y, -3.3), (0.97, y + 0.35, -1.0), 'mag_hi')
    box('mag_lower', (-0.97, y, -3.3), (-0.9, y + 0.35, -1.0), 'mag_hi')
cx('mag_lower', 2.1, -1.3, -0.8, -3.8, -0.5, 'mag_hi')        # пятка
cx('mag_lower', 1.4, -1.38, -1.3, -3.4, -0.9, 'dark')
cx('magazine', 0.7, 7.6, 7.85, -3.0, -1.3, 'brass')           # верхний патрон
bone('mag_pos', 'mag_lower', pivot=(0, -1.3, -2.15))

# --- руки: точка хвата = pivot, ось +Y кости идёт от кисти к плечу


def hand(name, parent, grip, toward, length_scale, side_hint=(1, 0, 0)):
    y = np.array(toward, float)
    y /= np.linalg.norm(y)
    h = np.array(side_hint, float)
    xv = h - y * np.dot(h, y)
    xv /= np.linalg.norm(xv)
    zv = np.cross(xv, y)
    Rm = np.stack([xv, y, zv], axis=1)
    bone(name, parent, pivot=grip, rot=euler_from_matrix(Rm), scale=[1, length_scale, 1])


hand('righthand', G, grip=(0.25, 4.7, 3.7), toward=(0.42, -0.36, 0.83), length_scale=1.2)
hand('lefthand', 'mag_and_hand', grip=(-0.2, 6.9, -10.6), toward=(-0.30, -0.40, 0.86), length_scale=1.5)

# --- мелкие детали (винты, антабки, штифты)
for z in (-13.8, -10.6, -7.4, -5.0):
    box(G, (1.5, 8.85, z), (1.58, 9.0, z + 0.35), 'metal')
    box(G, (-1.58, 8.85, z), (-1.5, 9.0, z + 0.35), 'metal')
box(G, (1.5, 10.05, -14.6), (1.85, 10.6, -14.0), 'metal_d')     # QD-антабки цевья
box(G, (-1.85, 10.05, -14.6), (-1.5, 10.6, -14.0), 'metal_d')
cx(G, 1.7, BORE - 0.85, BORE + 0.85, HG1 + 0.05, HG1 + 0.45, 'metal_d')  # гайка ствола
box(G, (1.18, 9.45, 0.9), (1.21, 10.25, 3.1), 'metal')           # затворная рама в окне
box(G, (1.2, 10.4, 0.3), (1.32, 10.55, 3.7), 'metal_d')          # ось крышки окна
box(G, (1.85, 9.85, 3.95), (2.05, 10.25, 4.75), 'metal')         # кнопка досылателя
box(G, (-1.26, 9.0, -3.9), (-1.2, 11.0, 6.9), 'rec_hi')          # грань слева
for z in (0.5, 1.7, 5.55):
    box(G, (-1.3, 7.3, z), (-1.15, 7.65, z + 0.35), 'metal')     # штифты УСМ
    box(G, (1.15, 7.3, z), (1.3, 7.65, z + 0.35), 'metal')
cx(G, 0.8, 5.15, 5.35, -4.0, -3.4, 'rec')                        # передний выступ горловины
box(G, (-1.25, 6.95, 6.2), (1.25, 8.5, 6.45), 'rec_hi')          # концевая пластина
box(G, (1.25, 7.6, 6.15), (1.55, 8.2, 6.6), 'metal_d')           # QD сзади
cx(G, 0.5, 11.15, 11.35, -15.25, -15.05, 'metal')                # стопор планки
cx(M, 1.0, BORE - 0.5, BORE + 0.5, -23.2, -23.05, 'metal')       # дульный срез
box('pgrip', (-0.6, 2.15, 2.9), (0.6, 2.3, 4.1), 'metal')        # винт рукояти
box('pgrip', (1.1, 4.4, 2.5), (1.25, 5.6, 4.0), 'poly')          # приливы под ладонь
box('pgrip', (-1.25, 4.4, 2.5), (-1.1, 5.6, 4.0), 'poly')
box(C, (1.3, 10.5, 7.05), (1.45, 10.9, 7.45), 'rec_hi')          # защёлка справа
for z in (12.4, 12.75, 13.1):                                     # рифление затыльника
    pass
for y in (7.0, 8.2, 9.4):
    cx(K, 2.5, y, y + 0.35, 13.55, 13.7, 'dark')
box(K, (-1.4, 9.3, 11.9), (-1.15, 9.95, 12.6), 'metal')           # антабка слева
box(K, (1.15, 7.6, 12.2), (1.2, 10.3, 12.7), 'dark')
box(K, (-1.2, 7.6, 12.2), (-1.15, 10.3, 12.7), 'dark')
cx(K, 1.0, 6.9, 7.05, 8.7, 10.4, 'metal_d')                       # защёлка регулировки
cx('magazine', 1.5, 7.55, 7.75, -3.4, -2.9, 'mag_hi')             # губы магазина
cx('magazine', 1.5, 7.55, 7.75, -1.4, -0.9, 'mag_hi')
cx('mag_lower', 0.8, -1.42, -1.3, -3.9, -3.6, 'mag_hi')           # язычок пятки
box('magazine', (-0.97, 5.6, -3.3), (-0.9, 6.9, -2.4), 'dark')    # окошко
cx(S, 0.5, 12.55, 12.7, -14.2, -13.5, 'metal')                    # пружина мушки
box(R, (-0.95, 12.8, 5.2), (-0.75, 13.2, 5.6), 'sight')          # барабан слева

# --- точки крепления обвесов
bone('muzzle_pos', G, pivot=(0, BORE, -20.5))
bone('scope_pos', G, pivot=(0, 12.1, 0.6))
bone('grip_pos', G, pivot=(0, 6.95, -10.3))
bone('stock_pos', G, pivot=(0, 8.9, 7.2))

# --- камера и точки обзора (не рисуются)
bone('camera', pivot=(0, 13.4, 16.0))
bone('idle_view', pivot=(-3.25, 13.4, 16.1))
bone('iron_view', pivot=(0, 13.3, 14.6))

# ------------------------------------------------------------------ запись
cubes = sum(len(b['cubes']) for b in bones)
data = {
    'texture': 'battlegrounds:textures/geo/m416.png',
    'texture_size': [64, 64],
    'swatch': 8,
    'third_person_scale': 0.6,
    'gui_scale': 0.42,
    'bones': bones,
}
os.makedirs(os.path.join(ROOT, 'geo'), exist_ok=True)
os.makedirs(os.path.join(ROOT, 'textures', 'geo'), exist_ok=True)
json.dump(data, open(os.path.join(ROOT, 'geo', 'm416.json'), 'w'), separators=(',', ':'))
make_texture(os.path.join(ROOT, 'textures', 'geo', 'm416.png'))
SWATCH_RGB = {SLOT[n]: SW[n] for n in SW}
if __name__ == '__main__':
    print('bones', len(bones), 'cubes', cubes)
