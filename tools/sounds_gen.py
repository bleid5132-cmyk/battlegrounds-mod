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
    place(x, click(1300, 0.04, bright=1.0, damp=1.0) * (0.12 if not sup else 0.35), 0.017)
    place(x, click(950, 0.05, bright=0.8, damp=0.9) * (0.10 if not sup else 0.3), 0.055)
    # перегрузка: плотность и громкость, как у настоящей записи выстрела
    x = soft_clip(x / (np.max(np.abs(x)) + 1e-9), 3.0 if not sup else 2.0)
    # 5) хвост открытого места
    tail = dense_tail(dur - 0.01, (1.6 if heavy else 1.3) if not sup else 0.45, 0.25, pre=0.015)
    place(x, tail * (0.16 if not sup else 0.08), 0.01)
    return finish(x, 0.97, fade=0.2)


# ------------------------------------------------------------------ механика
def mag_out():
    x = silence(0.32)
    place(x, click(3100, 0.04, bright=1.2, damp=1.4) * 0.5, 0.0)                    # кнопка защёлки
    place(x, click(1500, 0.05, bright=0.7, damp=1.2) * 0.35, 0.012)                 # защёлка отпускает
    place(x, scrape(0.07, 2000, 9000, rough=0.5) * 0.07, 0.03)                       # магазин скользит вниз
    place(x, mix(click(900, 0.08, bright=0.6) * 0.3, thud(260, 0.08, 0.015) * 0.25), 0.17)  # выходит из шахты
    place(x, cloth(0.08) * 0.04, 0.17)
    return finish(reverb_tail(x, 0.25, 0.25, 0.08, 0.12), 0.85)


def mag_in():
    x = silence(0.38)
    place(x, scrape(0.06, 2000, 9000, rough=0.5, rise=0.8) * 0.06, 0.07)             # входит в шахту
    place(x, mix(click(1250, 0.10, bright=1.0) * 0.9, thud(320, 0.10, 0.02) * 0.6), 0.13)  # защёлкнулся
    place(x, click(2600, 0.05, bright=1.3, damp=1.3) * 0.35, 0.137)
    place(x, cloth(0.08) * 0.03, 0.16)
    return finish(reverb_tail(x, 0.3, 0.3, 0.08, 0.15), 0.95)


def mag_tap():
    x = silence(0.26)
    place(x, thud(190, 0.16, 0.03) * 0.9, 0.0)                                       # ладонь по дну магазина
    place(x, lp(noise(0.03), 2500) * env_exp(0.03, 0.006) * 0.5, 0.0)
    place(x, click(1100, 0.08, bright=0.7) * 0.25, 0.002)
    return finish(reverb_tail(x, 0.25, 0.25, 0.08, 0.12), 0.85)


def charge_pull():
    x = silence(0.32)
    place(x, click(2300, 0.03, bright=1.0, damp=1.6) * 0.3, 0.0)                    # пальцы на рукояти
    place(x, scrape(0.10, 1800, 9000, grain=0.003, rough=0.5, rise=1.2) * 0.035, 0.08)  # рама идёт назад против пружины
    place(x, click(1050, 0.09, bright=1.1) * 0.8, 0.18)                             # упор в крайнем заднем
    return finish(reverb_tail(x, 0.3, 0.3, 0.08, 0.15), 0.9)


def charge_release():
    x = silence(0.45)
    place(x, scrape(0.02, 2000, 9000, rough=0.4) * 0.1, 0.0)                         # рама срывается вперёд
    slam = mix(click(780, 0.16, bright=1.0) * 1.0, click(1650, 0.1, bright=1.2) * 0.5, thud(170, 0.15, 0.03) * 0.7)
    place(x, slam, 0.02)                                                             # удар затвора в крайнее переднее
    place(x, click(2900, 0.05, bright=1.3, damp=1.3) * 0.35, 0.045)                  # рукоять встала на защёлку
    place(x, click(2200, 0.04, bright=1.2, damp=1.5) * 0.18, 0.07)
    x = soft_clip(x / (np.max(np.abs(x)) + 1e-9), 1.5)
    return finish(reverb_tail(x, 0.35, 0.4, 0.1, 0.18), 0.97)


def rattle(n=4, sec=0.3):
    """Дребезг антабок и ремня при движении оружия."""
    x = silence(sec)
    ts = np.sort(rng.uniform(0, sec * 0.6, n))
    for i, t0 in enumerate(ts):
        place(x, click(rng.uniform(1800, 4200), 0.05, bright=1.2, damp=1.8) * rng.uniform(0.15, 0.35), t0)
    place(x, cloth(sec * 0.4) * 0.05, 0.0)
    return finish(x, 0.6)


def gun_draw():
    x = silence(0.7)
    place(x, cloth(0.25, 600, 4500, density=1.3) * 0.12, 0.08)                         # оружие снимается с ремня
    place(x, rattle(5, 0.35) * 0.5, 0.05)
    place(x, mix(thud(240, 0.12, 0.02) * 0.6, click(1300, 0.06, bright=0.6) * 0.25), 0.36)  # ладонь обхватывает рукоять
    place(x, rattle(2, 0.2) * 0.35, 0.41)
    return finish(reverb_tail(x, 0.3, 0.3, 0.1, 0.1), 0.8)


def gun_holster():
    x = silence(0.55)
    place(x, cloth(0.22, 600, 4500, density=1.2) * 0.12, 0.0)
    place(x, rattle(4, 0.3) * 0.45, 0.05)
    place(x, thud(150, 0.15, 0.03) * 0.5, 0.33)                                     # оружие легло на спину
    return finish(reverb_tail(x, 0.3, 0.3, 0.1, 0.1), 0.75)


def aim_in():
    x = silence(0.32)
    place(x, cloth(0.10, 700, 5000, density=2.2) * 0.12, 0.0)                         # быстрый взмах
    place(x, thud(130, 0.12, 0.025) * 0.55, 0.11)                                    # приклад в плечо
    place(x, click(3300, 0.03, bright=1.3, damp=1.8) * 0.12, 0.115)
    return finish(x, 0.6)


def aim_out():
    x = silence(0.22)
    place(x, cloth(0.09, 700, 5000, density=2.0) * 0.12, 0.0)
    place(x, click(3000, 0.03, bright=1.2, damp=1.8) * 0.08, 0.08)
    return finish(x, 0.45)


def dry_fire():
    x = silence(0.18)
    place(x, click(2100, 0.07, bright=1.2) * 0.9, 0.0)                               # курок бьёт по пустому
    place(x, click(3600, 0.03, bright=1.4, damp=1.5) * 0.3, 0.003)
    return finish(reverb_tail(x, 0.2, 0.2, 0.08, 0.1), 0.8)


def fire_mode():
    x = silence(0.2)
    place(x, click(2600, 0.04, bright=1.1, damp=1.4) * 0.6, 0.0)                    # переводчик: первый фиксатор
    place(x, click(2350, 0.05, bright=1.2, damp=1.2) * 0.8, 0.045)                  # второй
    return finish(x, 0.75)


def reload_full():
    """Перезарядка целиком — её слышат другие игроки (и ты от третьего лица)."""
    x = silence(2.3)
    place(x, mag_out(), 0.04)
    place(x, mag_in(), 0.86)
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
