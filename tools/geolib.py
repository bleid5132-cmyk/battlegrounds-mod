"""Общая математика моделей Battlegrounds (та же, что в Java-движке).

Координаты модели — пиксели (1/16 блока): x вправо, y вверх, ствол смотрит в -z.
Кость: pivot (абсолютный), rot (градусы, R = Rz*Ry*Rx), scale.
Матрица кости относительно родителя: T(pivot + animPos) * R(rest) * R(anim) * S * T(-pivot).
Кубы заданы в абсолютных координатах модели.
"""
import json, math
import numpy as np


def rot_xyz(rx, ry, rz):
    rx, ry, rz = np.radians([rx, ry, rz])
    cx, sx, cy, sy, cz, sz = np.cos(rx), np.sin(rx), np.cos(ry), np.sin(ry), np.cos(rz), np.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def euler_from_matrix(R):
    """Обратное к rot_xyz: R = Rz*Ry*Rx -> (rx, ry, rz) в градусах."""
    ry = math.asin(max(-1.0, min(1.0, -R[2, 0])))
    rx = math.atan2(R[2, 1], R[2, 2])
    rz = math.atan2(R[1, 0], R[0, 0])
    return [round(math.degrees(rx), 3), round(math.degrees(ry), 3), round(math.degrees(rz), 3)]


def M4(R=None, t=(0, 0, 0), s=(1, 1, 1)):
    M = np.eye(4)
    if R is not None:
        M[:3, :3] = R
    M[:3, :3] = M[:3, :3] @ np.diag(s)
    M[:3, 3] = t
    return M


def T(t):
    return M4(None, t)


# ------------------------------------------------------------------ анимации
def _cr(p0, p1, p2, p3, u):
    return 0.5 * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u ** 3)


def sample_channel(keys, t):
    """keys: [[time, x, y, z, mode]], mode 'c' — catmull-rom, иначе линейно (режим ключа, из которого выходим)."""
    if not keys:
        return np.zeros(3)
    if t <= keys[0][0]:
        return np.array(keys[0][1:4], float)
    if t >= keys[-1][0]:
        return np.array(keys[-1][1:4], float)
    for i in range(len(keys) - 1):
        a, b = keys[i], keys[i + 1]
        if a[0] <= t <= b[0]:
            u = (t - a[0]) / (b[0] - a[0]) if b[0] > a[0] else 0.0
            pa, pb = np.array(a[1:4], float), np.array(b[1:4], float)
            if len(a) > 4 and a[4] == 'c':
                p0 = np.array(keys[i - 1][1:4], float) if i > 0 else pa
                p3 = np.array(keys[i + 2][1:4], float) if i + 2 < len(keys) else pb
                return _cr(p0, pa, pb, p3, u)
            return pa + (pb - pa) * u
    return np.array(keys[-1][1:4], float)


def sample_anim(anim, t):
    """-> {bone: {'rot': v3, 'pos': v3, 'scale': v3?}}"""
    out = {}
    for bone, ch in anim['bones'].items():
        d = {}
        for name in ('rot', 'pos', 'scale'):
            if name in ch:
                d[name] = sample_channel(ch[name], t)
        out[bone] = d
    return out


def add_pose(*poses, weights=None):
    out = {}
    for i, p in enumerate(poses):
        w = 1.0 if weights is None else weights[i]
        for bone, d in p.items():
            o = out.setdefault(bone, {})
            for k, v in d.items():
                if k == 'scale':
                    o[k] = o.get(k, np.ones(3)) * (1 + (v - 1) * w)
                else:
                    o[k] = o.get(k, np.zeros(3)) + v * w
    return out


# ------------------------------------------------------------------ модель
class Geo:
    def __init__(self, data):
        self.data = data
        self.bones = {b['name']: b for b in data['bones']}
        self.order = [b['name'] for b in data['bones']]

    @staticmethod
    def load(path):
        return Geo(json.load(open(path)))

    def local(self, name, pose=None, rest_only=False):
        b = self.bones[name]
        p = np.array(b.get('pivot', [0, 0, 0]), float)
        rest = b.get('rot', [0, 0, 0])
        sc = np.array(b.get('scale', [1, 1, 1]), float)
        apos = np.zeros(3)
        arot = np.zeros(3)
        if pose and not rest_only and name in pose:
            apos = pose[name].get('pos', apos)
            arot = pose[name].get('rot', arot)
            if 'scale' in pose[name]:
                sc = sc * pose[name]['scale']
        R = rot_xyz(*rest) @ rot_xyz(*arot)
        return T(p + apos) @ M4(R, (0, 0, 0), sc) @ T(-p)

    def world(self, pose=None, rest_only=False):
        W = {}
        for n in self.order:
            b = self.bones[n]
            parent = b.get('parent')
            L = self.local(n, pose, rest_only)
            W[n] = (W[parent] @ L) if parent else L
        return W

    def locator(self, name):
        """Положение точки (кости-локатора) в покое: матрица с переносом в pivot."""
        W = self.world(rest_only=True)
        return W[name] @ T(self.bones[name].get('pivot', [0, 0, 0]))
