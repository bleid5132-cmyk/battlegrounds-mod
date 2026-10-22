"""Генератор звуков оружия (синтез с нуля, без чужих сэмплов).

Выстрел: ударная волна у дула (форма Фридлендера) + сверхзвуковой «треск» + низкий «бум» корпуса,
лёгкая перегрузка микрофона, лязг затворной рамы, ранние отражения от земли и предметов и хвост эха
открытого пространства (высокие частоты затухают быстрее низких).
Механика: металлические щелчки — модальный синтез (затухающие резонансы детали), скольжение — шум
с «прилипанием-проскальзыванием», ткань — полосовой шум с неровной огибающей.

python3 tools/sounds_gen.py  → src/main/resources/assets/battlegrounds/sounds/*.ogg
"""
import os, subprocess, tempfile, wave
import numpy as np
from scipy import signal

SR = 48000
OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'battlegrounds', 'sounds')
rng = np.random.default_rng(416)


# ------------------------------------------------------------------ базовые кирпичики
def T(sec):
    return np.arange(int(SR * sec)) / SR


def silence(sec):
    return np.zeros(int(SR * sec))


def place(buf, x, at, gain=1.0):
    i = int(at * SR)
    if i >= len(buf):
        return buf
    n = min(len(x), len(buf) - i)
    buf[i:i + n] += x[:n] * gain
    return buf


def bp(x, lo, hi, order=2):
    sos = signal.butter(order, [lo, hi], btype='band', fs=SR, output='sos')
    return signal.sosfilt(sos, x)


def lp(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f, btype='low', fs=SR, output='sos'), x)


def hp(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f, btype='high', fs=SR, output='sos'), x)


def noise(sec):
    return rng.standard_normal(int(SR * sec))


def env_exp(sec, tau, attack=0.0005):
    t = T(sec)
    a = np.clip(t / max(attack, 1e-6), 0, 1)
    return a * np.exp(-t / tau)


def reso(x, f, q):
    """Широкий резонанс (низкая добротность) — окраска без звона."""
    f = min(f, SR * 0.45)
    b_, a_ = signal.iirpeak(f, q, fs=SR)
    return signal.lfilter(b_, a_, x)


def click(base, sec=0.06, bright=1.0, damp=1.0):
    """Металлический щелчок: короткий шумовой удар, окрашенный широкими резонансами детали (без тона)."""
    t = T(sec)
    exc = noise(sec) * np.exp(-t / (0.0012 / damp)) * np.clip(t / 0.00008, 0, 1)
    out = np.zeros_like(t)
    for r, g in ((1.0, 1.0), (1.63, 0.8 * bright), (2.45, 0.6 * bright), (3.6, 0.45 * bright ** 1.5)):
        out += reso(exc, base * r * (1 + rng.normal(0, 0.03)), 4.5) * g
    out += hp(exc, 3000) * 0.35 * bright
    body = lp(noise(sec), 900) * np.exp(-t / (0.006 / damp)) * 0.4
    out = out + body
    return out / (np.max(np.abs(out)) + 1e-9)


def thud(f0, sec=0.12, tau=0.025):
    """Глухой удар: одиночный импульс давления + низкочастотный шумовой удар (без гудения)."""
    t = T(sec)
    n = int(SR / (2 * f0))
    pulse = np.zeros_like(t)
    pulse[:n] = np.sin(np.pi * np.arange(n) / n)
    body = lp(noise(sec), f0 * 3, 2) * np.exp(-t / tau) * 2.5
    out = pulse + body
    return out / (np.max(np.abs(out)) + 1e-9)


def scrape(sec, lo, hi, grain=0.004, rough=0.6, rise=0.0):
    """Скольжение металла по металлу: полосовой шум с прерывистой (stick-slip) огибающей."""
    t = T(sec)
    n = bp(noise(sec), lo, hi, 2)
    # «прилипание-проскальзывание»: плавная неровная огибающая без отдельных щелчков
    g = lp(np.abs(noise(sec)), 1.0 / max(grain, 1e-3) * 0.6, 2)
    g = g / (np.mean(g) + 1e-9)
    g = np.clip(1 - rough + rough * g ** 1.3, 0, 2.5)
    shape = np.sin(np.pi * np.clip(t / sec, 0, 1)) ** 0.6 * (1 + rise * t / sec)
    return n * g * shape


def cloth(sec, lo=600, hi=4500, density=1.0):
    """Шорох ткани/ремня: мягкий шум средних частот с хрустящими «волнами» трения."""
    t = T(sec)
    n = bp(noise(sec), lo, hi, 2)
    n = lp(n, hi * 0.8, 1)
    m = lp(np.abs(noise(sec)), 30 * density, 2)
    m = (m / (m.max() + 1e-9)) ** 2.2                       # редкие всплески трения, между ними тише
    crackle = hp(noise(sec), 2500) * (rng.random(len(t)) < 0.002) * 3
    shape = np.sin(np.pi * np.clip(t / sec, 0, 1)) ** 0.8
    return (n * m + lp(crackle, 6000) * m * 0.5) * shape


def reverb_tail(x, sec, t60_low=1.4, t60_high=0.35, wet=0.35, predelay=0.012):
    """Хвост открытого пространства: шум, где высокие частоты гаснут быстрее низких, свёрнутый с сигналом."""
    t = T(sec)
    ir = np.zeros_like(t)
    bands = [(60, 250, t60_low), (250, 1000, t60_low * 0.75), (1000, 3500, (t60_low + t60_high) / 2), (3500, 12000, t60_high)]
    for lo, hi, t60 in bands:
        ir += bp(noise(sec), lo, hi, 2) * np.exp(-6.9 * t / t60)
    ir[: int(predelay * SR)] = 0
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    y = signal.fftconvolve(x, ir)[: len(x) + len(t)]
    out = np.zeros(len(y))
    out[: len(x)] += x
    return out + y * wet * np.max(np.abs(x)) / (np.max(np.abs(y)) + 1e-9)


def echoes(x, list_):
    """Отдельные отражения (земля, склон, деревья): задержка, громкость, срез высоких."""
    out = np.zeros(len(x) + int(SR * max(d for d, _, _ in list_)) + 10)
    out[: len(x)] += x
    for d, g, f in list_:
        e = lp(x, f, 2) * g
        place(out, e, d)
    return out


def mix(*xs):
    """Сложение сигналов разной длины."""
    n = max(len(x) for x in xs)
    out = np.zeros(n)
    for x in xs:
        out[:len(x)] += x
    return out


def soft_clip(x, drive):
    return np.tanh(x * drive) / np.tanh(drive)


def finish(x, peak=0.95, fade=0.02, hpf=30):
    x = hp(x, hpf, 2)
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    # обрезаем тишину в конце (ниже −60 дБ)
    idx = np.nonzero(np.abs(x) > 0.001)[0]
    if len(idx):
        x = x[: min(len(x), idx[-1] + int(0.03 * SR))]
    n = int(fade * SR)
    if n > 0 and len(x) > n:
        x[-n:] *= np.linspace(1, 0, n)
    return x


# ------------------------------------------------------------------ выстрелы
def friedlander(sec, T0, b=1.6):
    t = T(sec)
    p = (1 - t / T0) * np.exp(-b * t / T0)
    p[t > T0 * 6] = 0
    return p


def dense_tail(sec, t60_low, t60_high, pre=0.012):
    """Плотный хвост эха открытого места: шум, высокие гаснут быстрее; медленные «волны» отражений."""
    t = T(sec)
    out = np.zeros_like(t)
    for lo, hi, t60 in ((50, 300, t60_low), (300, 1200, t60_low * 0.7), (1200, 4000, (t60_low + t60_high) / 2.5),
                        (4000, 12000, t60_high)):
        out += bp(noise(sec), lo, hi, 2) * np.exp(-6.9 * t / t60)
    am = lp(np.abs(noise(sec)), 6, 2)
    am = 0.55 + 0.45 * am / (am.max() + 1e-9)
    out *= am
    out[: int(pre * SR)] *= np.linspace(0, 1, int(pre * SR))
    return out / (np.max(np.abs(out)) + 1e-9)


def gunshot(kind='556', far=False):
    """Выстрел слоями: удар давлением (punch), взрыв пороховых газов, треск пули, механика, хвост эха."""
    heavy = kind == '762'
    sup = kind == 'sup'
    dur = 2.2 if not sup else 0.9
    x = silence(dur)
    t = T(0.3)
    if far:
        # дальний: только низ и середина, мягкая атака, долгие раскаты
        boom = lp(noise(0.5), 650, 3) * env_exp(0.5, 0.11 if heavy else 0.08, 0.004)
        place(x := silence(3.5), boom * 1.0, 0.0)
        rum = lp(dense_tail(3.3, 2.6 if heavy else 2.2, 0.5, pre=0.06), 900, 2)
        place(x, rum * 0.55, 0.05)
        x = soft_clip(x / (np.max(np.abs(x)) + 1e-9), 2.0)
        return finish(lp(x, 1800, 2), 0.9, fade=0.4)
    # 1) удар давлением — одиночный импульс и низкий шумовой «пинок»
    kick_ms = 0.009 if heavy else 0.007
    n = int(kick_ms * SR)
    kick = np.zeros(int(0.3 * SR))
    kick[:n] = np.sin(np.pi * np.arange(n) / n)
    kick += lp(noise(0.3), 160, 2) * np.exp(-t / (0.07 if heavy else 0.05)) * 3.0
    place(x, kick * (0.5 if sup else 1.0), 0.0)
    # 2) взрыв газов — широкополосный шум с резкой атакой; у глушителя приглушён
    blast = friedlander(0.03, 0.0018 if heavy else 0.0014) * 1.2
    gas = bp(noise(0.3), 180, 6500 if not sup else 2200, 2) * (np.exp(-t / 0.007) + 0.12 * np.exp(-t / 0.04))
    place(x, mix(blast * (0.4 if sup else 1.0), gas * (1.6 if not sup else 1.1)), 0.0)
    # 3) треск сверхзвуковой пули
    crack = hp(noise(0.02), 3000, 2) * np.exp(-T(0.02) / 0.0022)
    place(x, crack * (0.9 if not sup else 0.35), 0.0004)
    # 4) механика: рама уходит назад и возвращается (короткие металлические удары, без звона)
    place(x, hit(1300, weight=0.6, ring=0.009, crisp=0.4, body_f=230) * (0.13 if not sup else 0.4), 0.017)
    place(x, hit(950, weight=0.9, ring=0.011, crisp=0.3, body_f=180) * (0.11 if not sup else 0.33), 0.055)
    # перегрузка: плотность и громкость, как у настоящей записи выстрела
    x = soft_clip(x / (np.max(np.abs(x)) + 1e-9), 3.0 if not sup else 2.0)
    # 5) хвост открытого места
    tail = dense_tail(dur - 0.01, (1.6 if heavy else 1.3) if not sup else 0.45, 0.25, pre=0.015)
    place(x, tail * (0.16 if not sup else 0.08), 0.01)
    # приятнее на слух: тёплый плотный низ, мягче резкая середина-верх
    x = shelf_eq(x, low_gain=1.55, low_f=150, pres_gain=1.12, pres_f=2400, top_cut=10500)
    x = soft_clip(x / (np.max(np.abs(x)) + 1e-9), 1.3)
    return finish(x, 0.97, fade=0.2)


# ------------------------------------------------------------------ механика
# ------------------------------------------------------------------ «приятная» механика: инструменты
def svf_sweep(x, f0, f1, q=1.2, curve=1.0):
    """Полосовой фильтр с плавно плывущей частотой (state-variable) — для «вжуха»."""
    n = len(x)
    fc = f0 * (f1 / f0) ** (np.linspace(0, 1, n) ** curve)
    g = np.tan(np.pi * np.minimum(fc, SR * 0.45) / SR)
    k = 1 / q
    y = np.zeros(n)
    ic1 = ic2 = 0.0
    for i in range(n):
        gi = g[i]
        a1 = 1 / (1 + gi * (gi + k))
        v3 = x[i] - ic2
        v1 = a1 * ic1 + gi * a1 * v3
        v2 = ic2 + gi * v1
        ic1 = 2 * v1 - ic1
        ic2 = 2 * v2 - ic2
        y[i] = v1
    return y


def whoosh(sec, f0, f1, q=1.4, curve=1.0, attack=0.35):
    """Плавный «вжух» (оружие/ремень рассекает воздух, ткань скользит) — без шершавости."""
    t = T(sec)
    x = svf_sweep(noise(sec), f0, f1, q, curve)
    env = np.where(t < sec * attack, np.sin(np.pi / 2 * t / (sec * attack)) ** 2,
                   np.cos(np.pi / 2 * (t - sec * attack) / (sec * (1 - attack))) ** 2)
    return x * env / (np.max(np.abs(x * env)) + 1e-9)


def clack(base, ring=0.010, sec=0.08, modes=9, spread=3.2):
    """Металлический «клак»: много несозвучных резонансов с очень коротким затуханием — металл без ноты."""
    t = T(sec)
    out = np.zeros_like(t)
    for i in range(modes):
        f = base * (1 + spread * (i / max(1, modes - 1)) ** 1.35) * (1 + rng.normal(0, 0.04))
        d = ring * (1.0 - 0.6 * i / modes) * rng.uniform(0.8, 1.2)
        out += np.sin(2 * np.pi * f * t + rng.random() * 6.28) * np.exp(-t / d) * (0.85 ** i)
    return out / (np.max(np.abs(out)) + 1e-9)


def tick(f=4200, sec=0.02):
    """Хрусткий верхний щелчок (кромка, пружинка)."""
    t = T(sec)
    x = hp(noise(sec), f, 2) * np.exp(-t / 0.0009)
    return x / (np.max(np.abs(x)) + 1e-9)


def knock(f0, sec=0.12, tau=0.018):
    """Плотный низкий удар — «вес» детали."""
    t = T(sec)
    n = int(SR / (2 * f0))
    pulse = np.zeros_like(t)
    pulse[:n] = np.sin(np.pi * np.arange(n) / n)
    body = lp(noise(sec), f0 * 2.2, 2) * np.exp(-t / tau) * 1.6
    out = pulse * np.exp(-t / (tau * 2)) + body
    return out / (np.max(np.abs(out)) + 1e-9)


def hit(base, weight=1.0, ring=0.010, crisp=0.5, body_f=220, sec=0.12):
    """Механический удар из трёх слоёв: вес + металл + хруст."""
    return mix(knock(body_f, sec, 0.014 + 0.01 * weight) * 0.75 * weight,
               clack(base, ring, sec) * 0.65,
               tick(3800 + rng.uniform(-400, 600), 0.02) * crisp)


def jingle(n=3, sec=0.25, lo=2600, hi=5200):
    """Звяканье антабок/карабина ремня: лёгкие короткие звонкие касания."""
    x = silence(sec)
    for t0 in np.sort(rng.uniform(0, sec * 0.55, n)):
        place(x, clack(rng.uniform(lo, hi), ring=0.012, sec=0.05, modes=6, spread=1.8) * rng.uniform(0.25, 0.5), t0)
    return x


def compress(x, thr_db=-18, ratio=3.5, att=0.001, rel=0.06, makeup=True):
    env = np.abs(x)
    a_a, a_r = np.exp(-1 / (att * SR)), np.exp(-1 / (rel * SR))
    e = np.zeros_like(env)
    s_ = 0.0
    for i, v in enumerate(env):
        s_ = (a_a if v > s_ else a_r) * s_ + (1 - (a_a if v > s_ else a_r)) * v
        e[i] = s_
    thr = 10 ** (thr_db / 20)
    gain = np.where(e > thr, (np.maximum(e, thr) / thr) ** (1 / ratio - 1), 1.0)
    y = x * gain
    return y / (np.max(np.abs(y)) + 1e-9) if makeup else y


def shelf_eq(x, low_gain=1.35, low_f=180, pres_gain=1.25, pres_f=3200, top_cut=11000):
    """Тёплый низ, немного «присутствия», мягкий срез резкого верха."""
    y = x + lp(x, low_f, 2) * (low_gain - 1) + bp(x, pres_f * 0.7, pres_f * 1.4, 1) * (pres_gain - 1)
    return lp(y, top_cut, 2)


def small_room(x, wet=0.12):
    """Маленькое пространство вокруг рук — звук «в воздухе», но близкий и сухой."""
    return reverb_tail(x, 0.18, 0.22, 0.08, wet, predelay=0.006)


def polish(x, peak=0.9, room=0.12):
    x = shelf_eq(x)
    x = compress(x, -14, 2.0)
    x = soft_clip(x, 1.4)
    x = small_room(x, room)
    return finish(x, peak, fade=0.03)


# ------------------------------------------------------------------ механика
def mag_out():
    """«тик — щёлк — шшк»: кнопка, защёлка отпускает, магазин выходит из шахты."""
    x = silence(0.4)
    place(x, tick(4800) * 0.35, 0.0)                                         # палец на кнопке
    place(x, hit(2100, weight=0.4, ring=0.006, crisp=0.6, body_f=320) * 0.7, 0.012)   # защёлка
    place(x, whoosh(0.09, 2500, 5500, q=2.0) * 0.072, 0.035)                  # магазин скользит
    place(x, hit(900, weight=0.8, ring=0.012, crisp=0.25, body_f=180) * 0.55, 0.11)   # вышел из шахты
    place(x, jingle(2, 0.2) * 0.150, 0.12)
    return polish(x, 0.85)


def mag_in():
    """«тк — ЩЁЛК»: магазин задевает край шахты, затем твёрдо встаёт на защёлку."""
    x = silence(0.4)
    place(x, hit(1700, weight=0.3, ring=0.006, crisp=0.4, body_f=300) * 0.35, 0.05)   # край шахты
    place(x, whoosh(0.06, 2000, 4500, q=2.2) * 0.056, 0.06)                   # входит
    place(x, hit(1150, weight=1.2, ring=0.013, crisp=0.8, body_f=210) * 1.0, 0.13)    # защёлкнулся
    place(x, tick(5200) * 0.25, 0.142)                                        # пружина защёлки
    place(x, jingle(2, 0.2) * 0.125, 0.15)
    return polish(x, 0.95)


def mag_tap():
    """Ладонь по дну магазина: мягкий плотный «тхап» и подтверждающий щелчок."""
    x = silence(0.25)
    place(x, knock(150, 0.15, 0.03) * 0.9, 0.0)
    place(x, hit(1300, weight=0.4, ring=0.007, crisp=0.3, body_f=260) * 0.45, 0.004)
    return polish(x, 0.85, room=0.1)


def charge_pull():
    """Пальцы на рукояти — «шк-ЩЁЛК»: рама рывком уходит назад и упирается."""
    x = silence(0.35)
    place(x, tick(4500) * 0.3, 0.0)
    place(x, hit(2400, weight=0.2, ring=0.005, crisp=0.4, body_f=380) * 0.3, 0.004)   # рукоять снялась с защёлки
    place(x, whoosh(0.1, 1800, 6000, q=2.5, curve=0.7) * 0.088, 0.08)          # рама идёт назад
    place(x, hit(1000, weight=1.0, ring=0.014, crisp=0.6, body_f=200) * 0.9, 0.18)    # упор
    return polish(x, 0.9)


def charge_release():
    """«ЧА-ЧАНК»: рама срывается вперёд, затвор с силой встаёт на место, рукоять защёлкивается."""
    x = silence(0.4)
    place(x, whoosh(0.03, 3000, 7000, q=2.0) * 0.100, 0.0)
    place(x, hit(800, weight=1.6, ring=0.016, crisp=0.9, body_f=160) * 1.0, 0.022)    # затвор закрылся
    place(x, hit(1500, weight=0.5, ring=0.010, crisp=0.5, body_f=280) * 0.5, 0.034)   # рама добивает
    place(x, hit(2600, weight=0.2, ring=0.006, crisp=0.6, body_f=400) * 0.35, 0.075)  # рукоять на защёлке
    place(x, jingle(3, 0.25) * 0.125, 0.04)
    return polish(x, 0.97)


def rattle(n=4, sec=0.3):
    """Оружие покачнулось: антабки и ремень."""
    x = silence(sec)
    place(x, jingle(n, sec) * 0.400, 0.0)
    place(x, whoosh(sec * 0.6, 900, 2200, q=1.0) * 0.032, 0.0)
    return polish(x, 0.55, room=0.08)


def gun_draw():
    """Оружие снимается с ремня: «вжух» вверх, звяканье, ладонь уверенно ложится на рукоять."""
    x = silence(0.7)
    place(x, whoosh(0.32, 500, 2600, q=1.3, curve=0.8, attack=0.45) * 0.168, 0.02)
    place(x, jingle(4, 0.32) * 0.200, 0.08)
    place(x, hit(1250, weight=1.0, ring=0.010, crisp=0.45, body_f=190) * 0.8, 0.36)   # хват
    place(x, knock(120, 0.12, 0.02) * 0.35, 0.36)
    place(x, jingle(2, 0.18) * 0.125, 0.40)
    return polish(x, 0.85)


def gun_holster():
    """Оружие уходит за спину: «вжух» вниз, звяканье, мягкий удар о спину."""
    x = silence(0.6)
    place(x, whoosh(0.3, 2400, 500, q=1.3, curve=0.9, attack=0.4) * 0.168, 0.0)
    place(x, jingle(4, 0.3) * 0.175, 0.05)
    place(x, knock(110, 0.15, 0.03) * 0.55, 0.32)
    place(x, hit(900, weight=0.6, ring=0.009, crisp=0.2, body_f=170) * 0.35, 0.325)
    return polish(x, 0.8)


def aim_in():
    """Вскидка к плечу: короткий «вжух», приклад мягко упирается, тихий щелчок."""
    x = silence(0.3)
    place(x, whoosh(0.13, 700, 2400, q=1.4, curve=0.8, attack=0.5) * 0.160, 0.0)
    place(x, knock(120, 0.12, 0.022) * 0.6, 0.11)
    place(x, hit(2200, weight=0.2, ring=0.006, crisp=0.3, body_f=350) * 0.25, 0.112)
    return polish(x, 0.6, room=0.08)


def aim_out():
    x = silence(0.22)
    place(x, whoosh(0.12, 1800, 700, q=1.4, attack=0.3) * 0.160, 0.0)
    place(x, jingle(1, 0.15) * 0.100, 0.06)
    return polish(x, 0.45, room=0.08)


def dry_fire():
    """Курок бьёт по пустому патроннику: сухой «тк»."""
    x = silence(0.2)
    place(x, hit(1900, weight=0.5, ring=0.008, crisp=0.8, body_f=300) * 0.9, 0.0)
    return polish(x, 0.8, room=0.1)


def fire_mode():
    """Переводчик огня: два чётких фиксатора."""
    x = silence(0.2)
    place(x, hit(2600, weight=0.2, ring=0.005, crisp=0.7, body_f=420) * 0.55, 0.0)
    place(x, hit(2300, weight=0.35, ring=0.006, crisp=0.8, body_f=380) * 0.8, 0.05)
    return polish(x, 0.75, room=0.08)


def reload_full():
    """Перезарядка целиком — её слышат другие игроки (и ты от третьего лица)."""
    x = silence(2.3)
    place(x, mag_out(), 0.04)
    place(x, mag_in(), 0.91)
    place(x, mag_tap() * 0.8, 1.14)
    place(x, rattle(3, 0.3) * 0.5, 1.62)
    return finish(x, 0.9)


def bullet_water():
    """Пуля входит в воду: резкий шлепок, шипящий всплеск, булькающие пузыри и падающие капли."""
    x = silence(0.9)
    place(x, mix(thud(420, 0.05, 0.004) * 0.6, hp(noise(0.01), 1500) * env_exp(0.01, 0.0015) * 0.9), 0.0)   # удар о поверхность
    place(x, bp(noise(0.35), 1500, 9000) * env_exp(0.35, 0.07, 0.004) * 0.55, 0.002)                        # шипящий всплеск
    for _ in range(9):                                                                                      # пузыри: короткие восходящие «чирпы»
        d = rng.uniform(0.012, 0.03)
        t = T(d)
        f0 = rng.uniform(500, 1400)
        b = np.sin(2 * np.pi * f0 * t * (1 + 1.8 * t / d)) * np.exp(-t / (d * 0.35)) * np.sin(np.pi * t / d) ** 0.3
        place(x, b * rng.uniform(0.15, 0.4), rng.uniform(0.01, 0.12))
    for _ in range(14):                                                                                     # капли падают обратно
        place(x, mix(click(rng.uniform(1800, 3600), 0.02, bright=0.6, damp=2.5) * 0.06,
                     bp(noise(0.02), 2000, 8000) * env_exp(0.02, 0.003) * 0.12), rng.uniform(0.18, 0.6))
    return finish(reverb_tail(x, 0.4, 0.5, 0.15, 0.2), 0.85)


# ------------------------------------------------------------------ запись
def write_ogg(name, x):
    os.makedirs(OUT, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f:
        tmp = f.name
    with wave.open(tmp, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '5',
                    os.path.join(OUT, name + '.ogg')], check=True)
    os.remove(tmp)


SOUNDS = {}
for i in range(3):
    SOUNDS[f'shot_556_{i + 1}'] = gunshot('556')
    SOUNDS[f'shot_762_{i + 1}'] = gunshot('762')
    SOUNDS[f'shot_suppressed_{i + 1}'] = gunshot('sup')
for i in range(2):
    SOUNDS[f'shot_far_{i + 1}'] = gunshot('556', far=True)
    SOUNDS[f'shot_far_762_{i + 1}'] = gunshot('762', far=True)
for i in range(2):
    SOUNDS[f'mag_out_{i + 1}'] = mag_out()
    SOUNDS[f'mag_in_{i + 1}'] = mag_in()
    SOUNDS[f'mag_tap_{i + 1}'] = mag_tap()
    SOUNDS[f'charge_pull_{i + 1}'] = charge_pull()
    SOUNDS[f'charge_release_{i + 1}'] = charge_release()
    SOUNDS[f'gun_rattle_{i + 1}'] = rattle()
    SOUNDS[f'gun_draw_{i + 1}'] = gun_draw()
    SOUNDS[f'aim_in_{i + 1}'] = aim_in()
SOUNDS['gun_holster'] = gun_holster()
SOUNDS['aim_out'] = aim_out()
SOUNDS['dry_fire'] = dry_fire()
SOUNDS['fire_mode'] = fire_mode()
SOUNDS['reload'] = reload_full()
for i in range(3):
    SOUNDS[f'bullet_water_{i + 1}'] = bullet_water()

if __name__ == '__main__':
    import sys
    only = sys.argv[1:]
    for k, v in SOUNDS.items():
        if only and not any(k.startswith(o) for o in only):
            continue
        write_ogg(k, v)
        print(f'{k:24s} {len(v) / SR:.2f} c')
