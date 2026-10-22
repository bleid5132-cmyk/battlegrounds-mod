"""Анимации семейства AR (M16A4, Mk47 Mutant, K2, SCAR-L, G36C) — от M416: рукоятки, магазин и руки стоят
в тех же точках (модели собраны в системе M416). Отличается только рывок рукояти заряжания при пустом
магазине — рука тянется к своей рукояти (сзади, слева спереди, справа или сверху).

python3 tools/ar_anims.py  (после m416_anims.py и ar_geo.py для всех вариантов)
"""
import copy, json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
import numpy as np

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'battlegrounds')
BASE = json.load(open(os.path.join(ROOT, 'animations', 'm416.json')))['animations']
M416_GEO = json.load(open(os.path.join(ROOT, 'geo', 'm416.json')))


def bone_pivot(geo, name):
    for b in geo['bones']:
        if b['name'] == name:
            return np.array(b['pivot'], float)
    return None


LH = bone_pivot(M416_GEO, 'lefthand')                 # точка хвата левой (у всех AR-вариантов одна)
GRAB_M416 = np.array([-2.0, 2.25, 17.9])
HAND_SIDE = {'rear': (-0.75, 0, 0.25), 'left': (-0.55, 0.1, 0), 'right': (0.55, 0.15, 0), 'top': (-0.3, 0.45, 0)}
WEIGHT = {1.32: 0.4, 1.42: 0.85, 1.76: 1.0, 1.9: 0.5, 2.05: 0.15}

VARIANTS = {
    'm16a4': dict(charge='rear', recoil=1.0),
    'mk47_mutant': dict(charge='rear', recoil=1.25),
    'k2': dict(charge='right', recoil=1.0),
    'scar_l': dict(charge='left', recoil=1.0),
    'g36c': dict(charge='top', recoil=1.0),
}


def build(gid, cfg):
    A = copy.deepcopy(BASE)
    geo = json.load(open(os.path.join(ROOT, 'geo', gid + '.json')))
    if cfg['recoil'] != 1.0:
        for bone in A['shoot']['bones'].values():
            for chan in bone.values():
                for k in chan:
                    k[1:4] = [round(v * cfg['recoil'], 4) for v in k[1:4]]
    if cfg['charge'] != 'rear':
        hp = bone_pivot(geo, 'charge_handle') * np.array([0.875, 1, 1])     # x у кубов сужен, у точки — нет
        grab = hp - LH + np.array(HAND_SIDE[cfg['charge']])
        delta = grab - GRAB_M416
        e = A['reload_empty']['bones']
        for k in e['lefthand']['pos']:
            t = round(k[0], 2)
            if t in (1.5, 1.54, 1.68, 1.71) or t in WEIGHT:
                w = WEIGHT.get(t, 1.0)
                for i in range(3):
                    k[1 + i] = round(k[1 + i] + delta[i] * w, 4)
        # крен оружия: показать бок, где рукоять
        for k in e['root']['rot']:
            if 1.4 <= k[0] <= 2.06:
                if cfg['charge'] == 'left':
                    k[2], k[3] = -k[2], -k[3]
                elif cfg['charge'] == 'top':
                    k[3] *= 0.4
                elif cfg['charge'] == 'right':
                    k[3] = round(k[3] * 1.2, 4)
        # отодвинуть оружие, если рукоять ближе к камере, чем у M416
        if hp[2] < 0:
            for k in e['root']['pos']:
                if 1.4 <= k[0] <= 2.0:
                    k[3] = round(k[3] * 0.4, 4)
    return A


if __name__ == '__main__':
    for gid, cfg in VARIANTS.items():
        A = build(gid, cfg)
        json.dump({'animations': A}, open(os.path.join(ROOT, 'animations', gid + '.json'), 'w'), separators=(',', ':'))
        print(gid, 'ok', cfg)
