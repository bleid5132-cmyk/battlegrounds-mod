"""Анимации буллпапов (AUG, QBZ, FAMAS, Groza). Покой/ходьба/бег/прицел/доставание — общие с M416;
перезарядка своя: оружие наклоняется и уходит вперёд, левая рука идёт с цевья к магазину позади рукояти,
при пустом магазине — к рукояти заряжания (у каждого ствола своё место). Тайминги — как у M416.
"""
import copy, json, os, sys
import numpy as np

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'battlegrounds')
BASE = json.load(open(os.path.join(ROOT, 'animations', 'm416.json')))['animations']


def ch(*keys, mode='c'):
    return [[round(k[0], 4), *[round(v, 4) for v in k[1:4]], (k[4] if len(k) > 4 else mode)] for k in keys]


def piv(geo, name):
    for b in geo['bones']:
        if b['name'] == name:
            return np.array(b['pivot'], float)


def cubes_extent(geo, name):
    lo = np.array([1e9] * 3); hi = -lo
    for b in geo['bones']:
        if b['name'] == name:
            for c in b['cubes']:
                lo = np.minimum(lo, c['f']); hi = np.maximum(hi, c['t'])
    return lo, hi


CFG = {
    #           наклон оружия при смене магазина, сдвиг, поза при рывке рукояти, сдвиг, сторона кисти у рукояти
    'aug':   dict(tilt=(10, 26, -22), push=(1.4, -1.0, -6.0), cpose=(12, 8, 18), cpush=(0.5, -0.6, -2.0), side=(-0.55, 0.1, 0)),
    'qbz':   dict(tilt=(10, 26, -22), push=(1.4, -1.2, -6.5), cpose=(14, -4, 8), cpush=(0.2, -1.2, -4.5), side=(-0.3, 0.45, 0)),
    'famas': dict(tilt=(10, 26, -22), push=(1.4, -1.2, -6.5), cpose=(14, -4, 8), cpush=(0.2, -1.2, -4.5), side=(-0.3, 0.45, 0)),
    'groza': dict(tilt=(10, 24, -20), push=(1.2, -1.0, -6.0), cpose=(8, -10, 24), cpush=(-0.6, -0.8, -6.5), side=(0.55, 0.15, 0)),
}


def scale(v, k):
    return tuple(x * k for x in v)


def build(gid, cfg):
    geo = json.load(open(os.path.join(ROOT, 'geo', gid + '.json')))
    LH = piv(geo, 'lefthand')
    lo, hi = cubes_extent(geo, 'magazine')
    mz = (lo[2] + hi[2]) / 2
    mag_side = np.array([lo[0] - 0.5, hi[1] - 2.2, mz])
    mag_bottom = np.array([0.0, lo[1] - 0.6, mz])
    TO_MAG = tuple(mag_side - LH)
    TO_BOT = tuple(mag_bottom - LH)
    hp = piv(geo, 'charge_handle')
    GRAB = tuple(hp - LH + np.array(cfg['side']))
    PULL = (GRAB[0], GRAB[1], GRAB[2] + 2.2)
    T, P = cfg['tilt'], cfg['push']

    def reload(empty):
        L = 2.6 if empty else 2.3
        rr = [(0, 0, 0, 0), (0.1, *scale(T, 0.3)), (0.25, *T), (0.32, T[0] + 1.5, T[1], T[2] - 1.5), (0.45, *T),
              (1.05, *T), (1.12, T[0] - 2.0, T[1], T[2] + 1.5), (1.18, T[0] + 1.0, T[1], T[2] - 0.5), (1.3, *T)]
        rp = [(0, 0, 0, 0), (0.1, *scale(P, 0.3)), (0.25, *P), (1.05, *P), (1.12, P[0], P[1] + 0.3, P[2] - 0.3), (1.3, *P)]
        lp = [(0, 0, 0, 0), (0.08, *scale(TO_MAG, 0.3)), (0.18, *TO_MAG), (1.02, *TO_MAG),
              (1.1, *[(a + b) / 2 for a, b in zip(TO_MAG, TO_BOT)]), (1.16, *TO_BOT), (1.22, TO_BOT[0], TO_BOT[1] - 0.4, TO_BOT[2])]
        lr = [(0, 0, 0, 0), (0.18, 35, -1, 3), (1.02, 35, -1, 3), (1.16, 47, 1.5, -3.4), (1.22, 45, 1.5, -3)]
        cam = [(0, 0, 0, 0), (0.15, 0.3, 0, -0.2), (0.35, 1.6, 0.1, -0.9), (0.8, 1.5, 0.1, -0.7), (1.05, 1.3, 0.1, -0.9),
               (1.12, 2.3, 0, -0.1), (1.3, 1.6, 0.1, -0.7)]
        bones = {}
        if not empty:
            rr += [(1.45, *T), (1.7, *scale(T, 0.6)), (1.95, *scale(T, 0.1)), (2.05, *scale(T, -0.05)), (2.3, 0, 0, 0)]
            rp += [(1.45, *P), (1.7, *scale(P, 0.55)), (1.95, *scale(P, 0.1)), (2.05, 0, 0.1, 0.2), (2.3, 0, 0, 0)]
            lp += [(1.45, *TO_BOT), (1.55, *scale(TO_BOT, 0.8)), (1.65, *scale(TO_MAG, 0.45)), (1.78, *scale(TO_MAG, 0.12)),
                   (1.88, 0, 0, 0), (2.3, 0, 0, 0)]
            lr += [(1.45, 45, 1.5, -3), (1.65, 20, 0, -2), (1.85, 0, 0, 0), (2.3, 0, 0, 0)]
            cam += [(1.7, 1.0, 0.1, -0.3), (1.95, 0.2, 0, 0), (2.05, -0.3, 0, 0.2), (2.3, 0, 0, 0)]
            sounds = [[0.05, 'mag_out'], [0.92, 'mag_in'], [1.14, 'mag_tap'], [1.62, 'gun_rattle']]
        else:
            C, CP = cfg['cpose'], cfg['cpush']
            rr += [(1.42, *scale(C, 0.8)), (1.5, *C), (1.62, *C), (1.7, C[0] + 2.5, C[1], C[2] - 1.0), (1.76, *C),
                   (1.95, *scale(C, 0.5)), (2.1, *scale(C, 0.1)), (2.25, -1.2, 0, 1.0), (2.4, 0.3, 0, -0.3), (2.6, 0, 0, 0)]
            rp += [(1.42, *scale(CP, 0.8)), (1.5, *CP), (1.68, *CP), (1.72, CP[0], CP[1] + 0.3, CP[2] + 0.4), (1.8, *CP),
                   (1.95, *scale(CP, 0.5)), (2.2, 0, -0.1, 0), (2.45, 0, 0, 0.1), (2.6, 0, 0, 0)]
            lp += [(1.32, *[0.5 * a + 0.5 * b for a, b in zip(TO_BOT, GRAB)]), (1.42, *scale(GRAB, 0.95)), (1.5, *GRAB),
                   (1.54, *GRAB), (1.68, *PULL), (1.71, PULL[0], PULL[1] + 0.1, PULL[2] + 0.1), (1.76, *scale(GRAB, 1.0)),
                   (1.9, *scale(GRAB, 0.5)), (2.05, *scale(GRAB, 0.2)), (2.15, *scale(GRAB, 0.05)), (2.25, 0, 0, 0), (2.6, 0, 0, 0)]
            lr += [(1.42, 30, 0, 8), (1.5, 30, 0, 12), (1.71, 30, 0, 12), (1.9, 15, 0, 5), (2.15, 3, 0, 1), (2.25, 0, 0, 0), (2.6, 0, 0, 0)]
            cam += [(1.5, 1.3, 0.1, -0.4), (1.66, 1.8, 0.1, -0.2), (1.73, 2.7, -0.2, 0.4), (1.82, 1.2, 0.1, -0.3),
                    (2.1, 0.7, 0.1, -0.2), (2.25, 0.1, 0, 0), (2.35, -0.3, 0, 0.2), (2.45, 0.1, 0, 0), (2.6, 0, 0, 0)]
            bones['charge_handle'] = {'pos': ch((0.0, 0, 0, 0, 'l'), (1.54, 0, 0, 0, 'c'), (1.68, 0, 0, 2.2, 'l'), (1.71, 0, 0, 2.2, 'l'),
                                                (1.74, 0, 0, -0.15, 'l'), (1.77, 0, 0, 0.08, 'l'), (1.80, 0, 0, 0, 'l'), (2.6, 0, 0, 0, 'l'))}
            sounds = [[0.05, 'mag_out'], [0.92, 'mag_in'], [1.14, 'mag_tap'], [1.5, 'charge_pull'], [1.7, 'charge_release'],
                      [2.2, 'gun_rattle']]
        mag_pos = BASE['reload_tactical']['bones']['mag_and_hand']['pos']
        mag_rot = BASE['reload_tactical']['bones']['mag_and_hand']['rot']
        bones.update({
            'root': {'rot': ch(*rr), 'pos': ch(*rp)},
            'mag_and_hand': {'pos': copy.deepcopy(mag_pos), 'rot': copy.deepcopy(mag_rot)},
            'lefthand': {'pos': ch(*lp), 'rot': ch(*lr)},
            'camera': {'rot': ch(*cam)},
        })
        return {'length': L, 'bones': bones, 'sounds': sounds}

    A = {k: copy.deepcopy(BASE[k]) for k in ('idle', 'walk', 'walk_aiming', 'run_start', 'run', 'run_end', 'ads_up',
                                             'draw', 'put_away', 'shoot')}
    if gid == 'groza':
        for bone in A['shoot']['bones'].values():
            for c in bone.values():
                for k in c:
                    k[1:4] = [round(v * 1.25, 4) for v in k[1:4]]
    A['reload_tactical'] = reload(False)
    A['reload_empty'] = reload(True)
    return A


if __name__ == '__main__':
    for gid, cfg in CFG.items():
        A = build(gid, cfg)
        json.dump({'animations': A}, open(os.path.join(ROOT, 'animations', gid + '.json'), 'w'), separators=(',', ':'))
        print(gid, 'ok')
