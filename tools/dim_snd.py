"""Procedural ambience for the 48 dimensions: each gets a 16 s 'bed' and two ~3 s accents, built from layered
primitives with its own timbre / rhythm / spectrum. Original synthesis (numpy -> WAV -> OGG via ffmpeg)."""
import numpy as np, zlib

SR = 22050


def T(secs):
    return np.arange(int(secs * SR)) / SR


def bp(x, lo, hi):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    X[(f < lo) | (f > hi)] = 0
    return np.fft.irfft(X, len(x))


def add(buf, snd, t0):
    i = int(t0 * SR)
    if i >= len(buf) or i < 0:
        return
    n = min(len(snd), len(buf) - i)
    buf[i:i + n] += snd[:n]


def decay_sine(f, dur, decay, amp=1.0, parts=(1.0,), sweep=0.0):
    t = T(dur)
    out = np.zeros(len(t))
    for k, p in enumerate(parts):
        ff = f * p * (1 + sweep * t)
        out += np.sin(2 * np.pi * np.cumsum(ff) / SR) * np.exp(-t * decay * (1 + 0.5 * k)) / (1 + k)
    return out * amp


# ---------------------------------------------------------------- primitives: (secs, rng, **kw) -> array
def drone(s, r, f=50, harm=(1, .5, .3), lfo=.2, det=.004, amp=.5, **k):
    t = T(s)
    x = sum(np.sin(2 * np.pi * f * (i + 1) * t + i) * h + np.sin(2 * np.pi * f * (i + 1) * (1 + det) * t) * h * .7 for i, h in enumerate(harm))
    return x * amp * (.65 + .35 * np.sin(2 * np.pi * lfo * t + r.uniform(0, 6)))


def hum(s, r, f=50, nh=8, drift=.4, amp=.3, **k):
    t = T(s)
    ff = f * (1 + drift * .02 * np.sin(2 * np.pi * .13 * t + r.uniform(0, 6)))
    ph = 2 * np.pi * np.cumsum(ff) / SR
    return sum(np.sin(ph * (i + 1)) / (i + 1) ** 1.2 for i in range(nh)) * amp


def air(s, r, lo=300, hi=3000, amp=.2, lfo=.15, **k):
    t = T(s)
    x = bp(np.random.default_rng(r.randint(0, 99999)).uniform(-1, 1, len(t)), lo, hi)
    x /= (np.max(np.abs(x)) + 1e-9)
    return x * amp * (.55 + .45 * np.sin(2 * np.pi * lfo * t + r.uniform(0, 6)))


def wind(s, r, center=500, amp=.3, gust=.12, **k):
    t = T(s)
    x = bp(np.random.default_rng(r.randint(0, 99999)).uniform(-1, 1, len(t)), center * .5, center * 1.8)
    x /= (np.max(np.abs(x)) + 1e-9)
    g = .35 + .65 * (.5 + .5 * np.sin(2 * np.pi * gust * t + r.uniform(0, 6))) ** 2
    return x * g * amp


def rumble(s, r, f=45, amp=.5, **k):
    t = T(s)
    x = bp(np.random.default_rng(r.randint(0, 99999)).uniform(-1, 1, len(t)), 10, f * 2)
    x /= (np.max(np.abs(x)) + 1e-9)
    return (x * .7 + np.sin(2 * np.pi * f * t) * .3) * amp * (.6 + .4 * np.sin(np.pi * t / s))


def pulse(s, r, rate=.6, f=45, decay=8, amp=.8, jitter=0, **k):
    out = np.zeros(int(s * SR))
    tt = 0.0
    while tt < s:
        add(out, decay_sine(f, .8, decay, amp) + .6 * np.roll(decay_sine(f * 1.1, .8, decay, amp), int(.22 * SR)), tt)
        tt += 1 / rate + r.uniform(-jitter, jitter)
    return out


def ticks(s, r, rate=2, f=2000, decay=70, amp=.3, jitter=0, **k):
    out = np.zeros(int(s * SR))
    tt = r.uniform(0, 1 / rate)
    while tt < s:
        add(out, decay_sine(f * r.uniform(.9, 1.1), .05, decay, amp), tt)
        tt += 1 / rate + r.uniform(-jitter, jitter) / rate
    return out


def hits(s, r, n=3, f=100, decay=15, amp=.5, parts=(1.0,), tmin=0, **k):
    out = np.zeros(int(s * SR))
    for _ in range(n):
        add(out, decay_sine(f * r.uniform(.8, 1.25), 1.5, decay, amp * r.uniform(.5, 1), parts), r.uniform(tmin, max(tmin + .1, s - 1.0)))
    return out


def metal(s, r, n=2, f=180, amp=.4, decay=2.5, **k):
    return hits(s, r, n, f, decay, amp, parts=(1, 1.59, 2.14, 2.65, 3.5))


def bell(s, r, n=1, notes=(392,), decay=1.4, amp=.3, **k):
    out = np.zeros(int(s * SR))
    for _ in range(n):
        add(out, decay_sine(r.choice(notes), 4.0, decay, amp, parts=(1, 2.76, 5.4, 8.9)), r.uniform(0, max(.1, s - 3.0)))
    return out


def bursts(s, r, n=8, lo=1000, hi=6000, dur=.15, amp=.3, **k):
    out = np.zeros(int(s * SR))
    g = np.random.default_rng(r.randint(0, 99999))
    for _ in range(n):
        d = dur * r.uniform(.5, 1.5)
        x = bp(g.uniform(-1, 1, int(d * SR) + 64), lo, hi)
        x /= (np.max(np.abs(x)) + 1e-9)
        x *= np.hanning(len(x)) * amp * r.uniform(.4, 1)
        add(out, x, r.uniform(0, max(.1, s - d)))
    return out


def sweep(s, r, n=2, f0=300, f1=1500, dur=.8, amp=.25, **k):
    out = np.zeros(int(s * SR))
    for _ in range(n):
        t = T(dur)
        f = f0 + (f1 - f0) * (t / dur) ** 1.5
        x = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.hanning(len(t)) * amp
        add(out, x, r.uniform(0, max(.1, s - dur)))
    return out


def choir(s, r, notes=(110, 165), amp=.3, rev=False, vib=5.0, **k):
    t = T(s)
    out = np.zeros(len(t))
    for i, n in enumerate(notes):
        v = 1 + .006 * np.sin(2 * np.pi * (vib + i * .3) * t + i)
        ph = 2 * np.pi * np.cumsum(n * v) / SR
        x = sum(np.sin(ph * h + i) * w for h, w in ((1, 1), (2, .6), (3, .45), (4, .25), (5, .3), (6, .12)))
        sw = np.sin(np.pi * ((t / s + i * .17) % 1.0)) ** 2
        if rev: sw = np.maximum(0, (t / s + i * .17) % 1.0) ** 3 * (1 - ((t / s + i * .17) % 1.0 > .93))
        out += x * sw
    return out / max(1, len(notes)) * amp


def creak(s, r, n=3, f0=180, f1=120, dur=1.2, amp=.3, **k):
    out = np.zeros(int(s * SR))
    g = np.random.default_rng(r.randint(0, 99999))
    for _ in range(n):
        t = T(dur)
        f = f0 + (f1 - f0) * (t / dur) + 25 * np.sin(2 * np.pi * 9 * t)
        x = np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR)) * .3 + bp(g.uniform(-1, 1, len(t)), 100, 900) * .5
        add(out, x * np.hanning(len(t)) * amp, r.uniform(0, max(.1, s - dur)))
    return out


def drip(s, r, n=5, f=420, amp=.25, **k):
    out = np.zeros(int(s * SR))
    for _ in range(n):
        add(out, decay_sine(f * r.uniform(.8, 1.3), .25, 18, amp, sweep=-1.8), r.uniform(0, s - .3))
    return out


def bubble(s, r, n=6, amp=.25, f=250, **k):
    out = np.zeros(int(s * SR))
    for _ in range(n):
        t0 = r.uniform(0, s - .4)
        for j in range(r.randint(1, 3)):
            add(out, decay_sine(f * r.uniform(.8, 1.6), .18, 14, amp, sweep=3.0), t0 + j * .09)
    return out


def thunder(s, r, amp=.8, **k):
    t = T(s)
    x = bp(np.random.default_rng(r.randint(0, 9999)).uniform(-1, 1, len(t)), 20, 220)
    x /= (np.max(np.abs(x)) + 1e-9)
    e = np.where(t < s * .35, 0, np.exp(-(t - s * .35) * 1.4))
    return x * e * amp


def musicbox(s, r, scale=(523, 587, 659, 784, 880), tempo=.55, drop=.35, amp=.25, **k):
    out = np.zeros(int(s * SR))
    tt = .1
    i = 0
    while tt < s - .5:
        if r.random() > drop:
            add(out, decay_sine(scale[(i * 2 + r.randint(0, 2)) % len(scale)] * (1 + r.uniform(-.015, .015)), 1.2, 4, amp, parts=(1, 2.01, 3.99)), tt)
        tt += tempo * (1 + .12 * i % 3) * r.uniform(.9, 1.4)
        tempo *= 1.025
        i += 1
    return out


def glitch(s, r, n=8, amp=.25, **k):
    out = np.zeros(int(s * SR))
    g = np.random.default_rng(r.randint(0, 99999))
    for _ in range(n):
        d = r.uniform(.03, .18)
        base = np.sign(np.sin(2 * np.pi * r.choice([110, 440, 1300, 3000]) * T(d))) * .5 + g.uniform(-1, 1, int(d * SR)) * .4
        x = np.round(base * 4) / 4
        x = np.tile(x, r.randint(1, 4))
        add(out, x * amp, r.uniform(0, s - .2))
    return out


def clocks(s, r, rates=(1.0, 1.37), amp=.25, **k):
    out = np.zeros(int(s * SR))
    for i, rt in enumerate(rates):
        out += ticks(s, r, rate=rt, f=1200 + 400 * i, decay=80, amp=amp, jitter=.05)
    return out


def applause(s, r, dens=.2, amp=.25, **k):
    t = T(s)
    g = np.random.default_rng(r.randint(0, 9999))
    x = bp(g.uniform(-1, 1, len(t)), 1500, 6000)
    gate = (g.uniform(0, 1, len(t) // 220 + 1).repeat(220)[:len(t)] < dens).astype(float)
    env = np.sin(np.pi * t / s) ** 2
    return x * gate * env * amp


def beeps(s, r, rate=.5, f=1000, dur=.12, amp=.25, **k):
    out = np.zeros(int(s * SR))
    tt = .3
    while tt < s - dur:
        t = T(dur)
        add(out, np.sin(2 * np.pi * f * t) * np.hanning(len(t)) * amp, tt)
        tt += 1 / rate
    return out


def siren(s, r, f0=500, f1=620, rate=.1, amp=.1, **k):
    t = T(s)
    f = f0 + (f1 - f0) * (.5 + .5 * np.sin(2 * np.pi * rate * t))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * amp


def fan(s, r, f=120, amp=.25, **k):
    t = T(s)
    n = bp(np.random.default_rng(r.randint(0, 9999)).uniform(-1, 1, len(t)), 60, 1800)
    n /= (np.max(np.abs(n)) + 1e-9)
    return (n * .6 + np.sin(2 * np.pi * f * t) * .25) * (.8 + .2 * np.sin(2 * np.pi * f / 4 * t)) * amp


def footsteps(s, r, n=5, amp=.4, f=90, rate=0, **k):
    out = np.zeros(int(s * SR))
    g = np.random.default_rng(r.randint(0, 9999))
    for i in range(n):
        t0 = (i / rate if rate else r.uniform(0, s - .4)) + .1
        if t0 > s - .3: break
        x = bp(g.uniform(-1, 1, len(T(.25))), 40, 400) * np.exp(-T(.25) * 25) * 3
        add(out, x * amp + decay_sine(f * r.uniform(.8, 1.2), .25, 16, amp), t0)
    return out


def motif(s, r, notes=(220, 330, 262, 196), step=.5, amp=.25, **k):
    out = np.zeros(int(s * SR))
    tt = .2
    i = 0
    while tt < s - .6:
        add(out, decay_sine(notes[i % len(notes)], .7, 5, amp, parts=(1, 2, 3.01)), tt)
        tt += step; i += 1
    return out


def whisper(s, r, amp=.15, **k):
    t = T(s)
    x = air(s, r, 400, 2600, 1.0, .0)
    fm = .5 + .5 * np.sin(2 * np.pi * (6 + r.random() * 3) * t) * np.sin(2 * np.pi * .7 * t + r.uniform(0, 6))
    return x * fm * amp * np.sin(np.pi * t / s) ** 2


P = dict(drone=drone, hum=hum, air=air, wind=wind, rumble=rumble, pulse=pulse, ticks=ticks, hits=hits, metal=metal, bell=bell,
         bursts=bursts, sweep=sweep, choir=choir, creak=creak, drip=drip, bubble=bubble, thunder=thunder, musicbox=musicbox,
         glitch=glitch, clocks=clocks, applause=applause, beeps=beeps, siren=siren, fan=fan, footsteps=footsteps, motif=motif, whisper=whisper)


def L(name, g=1.0, **kw):
    return (name, g, kw)


def echo(x, delays=(.37, .71), gain=.45):
    out = x.copy()
    for i, d in enumerate(delays):
        k = int(d * SR)
        out[k:] += x[:len(x) - k] * gain ** (i + 1)
    return out


def reverb(x, amt=.3, tail=1.4):
    if amt <= 0: return x
    n = int(tail * SR)
    ir = np.random.default_rng(7).uniform(-1, 1, n) * np.exp(-np.arange(n) / SR * 3.2)
    ir[0] = 1.0
    ir[1:] *= amt * .35
    y = np.fft.irfft(np.fft.rfft(x, len(x) + n) * np.fft.rfft(ir, len(x) + n))
    return y[:len(x)]


def gate(x, r, n=2, dur=.8):
    for _ in range(n):
        i = r.randint(0, max(1, len(x) - int(dur * SR))); k = int(dur * r.uniform(.5, 1.3) * SR)
        w = np.ones(len(x)); w[i:i + k] = 0
        x = x * np.convolve(w, np.ones(200) / 200, mode="same")
    return x


def render(layers, secs, seed, rv=0.25, fx=(), fade=.4, lp=None, vol=1.0):
    import random
    r = random.Random(seed)
    x = np.zeros(int(secs * SR))
    for name, g, kw in layers:
        x += P[name](secs, r, **kw) * g
    if "echo" in fx: x = echo(x)
    if "gate" in fx: x = gate(x, r)
    if "crush" in fx: x = np.round(x * 6) / 6
    if "fadeout" in fx: x = x * np.clip(1 - (T(secs) / secs - .25) / .7, 0, 1)
    if "rev" in fx: x = x[::-1].copy()
    if lp: x = bp(x, 0, lp)
    x = reverb(x, rv)
    e = np.minimum(1, np.minimum(T(secs) / fade, (secs - T(secs)) / fade))
    x = x * np.clip(e, 0, 1)
    m = np.max(np.abs(x)) or 1
    return (x / m * .8 * vol * 32767).astype(np.int16)


# ---------------------------------------------------------------- the 48 recipes: slug -> (bed layers, accent1, accent2, opts)
R = {}
VOL = dict(white_void=.3, unwritten_end=.55, yellow_silence=.7, pale_moon=.6, starless_expanse=.7, silent_monastery=.8, forbidden_index=.7)


def rec(slug, bed, a1, a2, rv=.25, fx=(), lp=None, afx=((), ())):
    R[slug] = dict(bed=bed, a1=a1, a2=a2, rv=rv, fx=fx, lp=lp, afx=afx, vol=VOL.get(slug, 1.0))

rec("last_signal", [L("drone", .8, f=48), L("air", .5, lo=300, hi=3000, amp=.4, lfo=.2), L("bursts", .7, n=10, lo=800, hi=6000, dur=.15), L("pulse", .8, rate=.55, f=42), L("hits", .5, n=3, f=110, decay=20)],
    [L("sweep", 1, n=1, f0=300, f1=1700, dur=1.6), L("bursts", .6, n=4, lo=1000, hi=5000, dur=.2)], [L("hits", 1, n=3, f=120, decay=25, tmin=0)], .3)
rec("forgotten_village", [L("wind", .8, center=400, amp=.4), L("creak", .8, n=3, f0=180, f1=120, dur=1.2), L("bell", .6, n=1, notes=(392,), decay=1.2), L("footsteps", .5, n=4, f=300)],
    [L("bell", 1, n=1, notes=(262,), decay=1.0)], [L("creak", 1, n=1, f0=200, f1=90, dur=2.2)], .5)
rec("hollow_cathedral", [L("choir", .9, notes=(110, 164.8, 220), rev=True), L("drone", .6, f=36), L("bell", .5, n=1, notes=(196,), decay=.8), L("rumble", .5, f=60)],
    [L("bell", 1, n=1, notes=(147,), decay=.7)], [L("choir", 1, notes=(98, 147), rev=True)], .9, lp=3000)
rec("endless_archive", [L("air", .5, lo=2000, hi=7000, amp=.3), L("bursts", .8, n=12, lo=1500, hi=6000, dur=.25), L("air", .4, lo=300, hi=1200, amp=.3, lfo=.3), L("creak", .5, n=2, f0=150, f1=100, dur=1.0)],
    [L("bursts", 1, n=3, lo=1500, hi=6500, dur=.35)], [L("whisper", 1)], .4)
rec("silent_ward", [L("hum", .4, f=60), L("beeps", .8, rate=.5, f=1000), L("fan", .3, f=100), L("ticks", .4, rate=.3, f=800)],
    [L("beeps", 1, rate=.3, f=1000, dur=1.4)], [L("hum", 1, f=120, amp=.5), L("bursts", .5, n=5, lo=2000, hi=8000, dur=.1)], .2)
rec("last_station", [L("wind", .8, center=200, amp=.5), L("rumble", .8, f=80), L("metal", .4, n=2, f=420), L("sweep", .3, n=2, f0=700, f1=650, dur=.8), L("bursts", .4, n=6, lo=800, hi=5000, dur=.2)],
    [L("rumble", 1, f=70), L("sweep", .5, n=1, f0=1800, f1=900, dur=1.5)], [L("sweep", .8, n=2, f0=700, f1=640, dur=.7), L("bursts", .8, n=8, lo=800, hi=6000, dur=.15)], .45)
rec("starless_expanse", [L("drone", .9, f=32, harm=(1, .7, .4)), L("drone", .5, f=47.5, lfo=.07)], [L("drone", .2, f=1800, harm=(1,), amp=.2)], [L("hits", 1, n=1, f=45, decay=3)], .6, fx=("gate",))
rec("watching_deep", [L("pulse", 1.0, rate=.4, f=38, decay=6), L("drone", .5, f=29, lfo=.08), L("bursts", .4, n=5, lo=60, hi=500, dur=.8)],
    [L("bursts", 1, n=3, lo=80, hi=600, dur=.7)], [L("pulse", 1, rate=.9, f=36, decay=5)], .5)
rec("impossible_geometry", [L("footsteps", .7, n=6), L("drone", .5, f=61.7), L("drone", .4, f=92.4), L("hits", .5, n=3, f=200, decay=12)],
    [L("hits", 1, n=2, f=300, decay=10)], [L("footsteps", 1, n=4)], .6, fx=("echo",), afx=(("rev",), ("echo",)))
rec("red_monolith", [L("metal", .8, n=2, f=60, decay=1.2), L("drone", .8, f=40, harm=(1, .8, .6), lfo=.12), L("rumble", .6, f=45), L("pulse", .5, rate=.3, f=34)],
    [L("metal", 1, n=1, f=55, decay=1.0)], [L("pulse", 1, rate=.35, f=33)], .5)
rec("pale_moon", [L("wind", .5, center=1200, amp=.2), L("hits", .5, n=8, f=900, decay=80, amp=.3), L("drone", .3, f=330, harm=(1,), amp=.2)],
    [L("hits", 1, n=1, f=523, decay=1.2, parts=(1, 2.0))], [L("hits", 1, n=6, f=1100, decay=90)], .5)
rec("fractured_beyond", [L("sweep", .6, n=5, f0=2000, f1=200, dur=.5), L("hits", .6, n=4, f=140, decay=14), L("drone", .5, f=55, det=.02)],
    [L("bursts", 1, n=4, lo=2500, hi=9000, dur=.1), L("hits", .6, n=1, f=180, decay=8)], [L("sweep", 1, n=2, f0=3000, f1=300, dur=.8)], .5, fx=("echo",))
rec("living_walls", [L("pulse", .9, rate=.75, f=55, decay=9), L("air", .5, lo=100, hi=500, amp=.5, lfo=.5), L("creak", .6, n=4, f0=90, f1=60, dur=1.5), L("drip", .4, n=3, f=500)],
    [L("air", 1, lo=100, hi=700, amp=.8, lfo=1.2), L("drip", .5, n=2, f=300)], [L("pulse", 1, rate=.8, f=52, decay=8)], .3, lp=2500)
rec("rotting_garden", [L("bursts", .8, n=14, lo=2500, hi=8000, dur=.05, amp=.4), L("air", .4, lo=600, hi=2500, amp=.3, lfo=.2), L("wind", .4, center=300, amp=.3)],
    [L("bursts", 1, n=6, lo=2500, hi=9000, dur=.04)], [L("air", 1, lo=1500, hi=5000, amp=.7, lfo=2)], .35)
rec("rootbound_abyss", [L("creak", .9, n=6, f0=80, f1=50, dur=2.0, amp=.5), L("rumble", .6, f=40), L("hits", .5, n=3, f=55, decay=8)],
    [L("creak", 1, n=1, f0=90, f1=45, dur=2.6)], [L("hits", 1, n=3, f=60, decay=7), L("bursts", .5, n=3, lo=100, hi=900, dur=.3)], .5)
rec("nest_below", [L("ticks", .9, rate=3, f=3500, decay=90, jitter=.5), L("drone", .4, f=120, harm=(1, .3)), L("bursts", .5, n=3, lo=2000, hi=5000, dur=.6)],
    [L("bursts", 1, n=2, lo=2000, hi=5500, dur=.7)], [L("ticks", 1, rate=6, f=3800, decay=100, jitter=.7)], .55, fx=("echo",))
rec("drowned_marsh", [L("drip", .7, n=6, f=300), L("bubble", .8, n=8), L("wind", .3, center=1500, amp=.2), L("drone", .5, f=44)],
    [L("bubble", 1, n=5, f=140)], [L("drip", 1, n=4, f=380)], .5, lp=5000)
rec("petrified_forest", [L("hits", .6, n=4, f=140, decay=9), L("creak", .5, n=2, f0=140, f1=90, dur=1.2), L("bursts", .7, n=6, lo=3000, hi=9000, dur=.04), L("wind", .3, center=250, amp=.3)],
    [L("bursts", .8, n=5, lo=300, hi=3000, dur=.5), L("rumble", .6, f=60)], [L("bursts", 1, n=2, lo=3000, hi=9000, dur=.04)], .5)
rec("rusted_engine", [L("pulse", .8, rate=.45, f=70, decay=5), L("metal", .6, n=4, f=180), L("drone", .5, f=55), L("hits", .6, n=6, f=250, decay=9)],
    [L("pulse", 1, rate=.6, f=65, decay=4), L("bursts", .6, n=2, lo=800, hi=4000, dur=.5)], [L("hits", 1, n=3, f=240, decay=8, parts=(1, 1.6))], .4)
rec("black_laboratory", [L("hum", .5, f=120), L("beeps", .6, rate=.3, f=1500, dur=.08), L("ticks", .5, rate=.6, f=1200), L("air", .2, lo=3000, hi=9000, amp=.2)],
    [L("sweep", 1, n=1, f0=200, f1=2400, dur=1.2), L("hum", .4, f=180)], [L("hits", 1, n=1, f=880, decay=1.5)], .3)
rec("dead_grid", [L("hum", .9, f=60, nh=12, drift=.2), L("bursts", .6, n=5, lo=1500, hi=9000, dur=.12), L("ticks", .3, rate=.7, f=2600)],
    [L("bursts", 1, n=3, lo=2000, hi=9000, dur=.08), L("hum", .5, f=120)], [L("hum", 1, f=120, nh=6, amp=.6)], .25, fx=("gate",))
rec("sealed_bunker", [L("siren", .6, f0=500, f1=620, rate=.1, amp=.2), L("fan", .6, f=60), L("metal", .5, n=2, f=90), L("air", .3, lo=600, hi=4000, amp=.2, lfo=.07)],
    [L("air", 1, lo=800, hi=5000, amp=.8, lfo=.5), L("hits", .8, n=1, f=70, decay=6)], [L("siren", 1, f0=480, f1=600, rate=.4, amp=.3)], .45, lp=4000)
rec("dead_server", [L("fan", .8, f=180), L("glitch", .5, n=8), L("ticks", .5, rate=5, f=2500, jitter=.8), L("hum", .3, f=60)],
    [L("glitch", 1, n=10)], [L("sweep", .8, n=1, f0=2000, f1=150, dur=1.8), L("fan", .5, f=120)], .2, fx=("crush",))
rec("unfinished_mine", [L("hits", .8, n=4, f=700, decay=30), L("bursts", .6, n=6, lo=500, hi=3000, dur=.35), L("creak", .5, n=2, f0=140, f1=100, dur=1.2), L("wind", .4, center=150, amp=.3)],
    [L("hits", 1, n=2, f=700, decay=28, parts=(1, 2.7))], [L("bursts", 1, n=7, lo=500, hi=3500, dur=.3)], .6, fx=("echo",))
rec("yellow_silence", [L("hum", 1.0, f=120, nh=5, drift=2.5), L("air", .3, lo=100, hi=800, amp=.3, lfo=.05), L("drone", .3, f=60, harm=(1, .3))],
    [L("ticks", 1, rate=14, f=3000, jitter=.9), L("hum", .6, f=120)], [L("sweep", 1, n=1, f0=118, f1=150, dur=2.4)], .35)
rec("broken_hour", [L("clocks", 1.0, rates=(1.0, 1.37, .8, 2.1)), L("bell", .5, n=2, notes=(262, 330)), L("ticks", .3, rate=12, f=900, jitter=.9), L("creak", .3, n=2, f0=300, f1=200, dur=.8)],
    [L("bell", 1, n=2, notes=(233, 311), decay=1.0)], [L("ticks", 1, rate=16, f=700, jitter=.5), L("sweep", .4, n=1, f0=200, f1=500, dur=1.5)], .45)
rec("mirrorless_hall", [L("footsteps", .8, n=5), L("drone", .3, f=1250, harm=(1,), det=.0056, amp=.3), L("air", .2, lo=3000, hi=9000, amp=.2)],
    [L("drone", .8, f=1250, harm=(1,), det=.006, amp=.4, lfo=.5)], [L("footsteps", 1, n=4)], .7, fx=("echo",), afx=((), ("echo",)))
rec("forgotten_childhood", [L("musicbox", 1.0), L("ticks", .5, rate=.5, f=900, decay=40), L("creak", .3, n=2, f0=250, f1=180, dur=.7)],
    [L("musicbox", 1, tempo=.6, drop=.5)], [L("ticks", 1, rate=3, f=900, decay=40, jitter=.7)], .5)
rec("empty_theater", [L("applause", .8, dens=.15), L("air", .4, lo=100, hi=600, amp=.4), L("creak", .5, n=3, f0=140, f1=100, dur=1.0), L("wind", .2, center=200, amp=.3)],
    [L("applause", 1, dens=.35)], [L("creak", 1, n=2, f0=130, f1=90, dur=1.4)], .85)
rec("staircase_below", [L("footsteps", 1.0, n=10, rate=1.5, f=110), L("rumble", .5, f=50), L("hits", .5, n=2, f=60, decay=5)],
    [L("hits", 1, n=1, f=55, decay=3), L("rumble", .5, f=40)], [L("footsteps", 1, n=5, rate=1.6)], .7, fx=("echo",))
rec("bloodless_desert", [L("wind", 1.0, center=800, amp=.5, gust=.07), L("air", .3, lo=4000, hi=9000, amp=.2), L("hits", .3, n=6, f=1200, decay=120), L("drone", .3, f=70, harm=(1, .5))],
    [L("wind", 1, center=900, amp=.7, gust=.4)], [L("bursts", 1, n=10, lo=4000, hi=9000, dur=.06, amp=.4)], .3)
rec("frozen_grave", [L("drone", .8, f=33, harm=(1, .6, .3)), L("creak", .6, n=4, f0=300, f1=120, dur=1.4), L("wind", .5, center=1000, amp=.4), L("hits", .5, n=3, f=2200, decay=40)],
    [L("bursts", 1, n=2, lo=2000, hi=9000, dur=.06), L("sweep", .5, n=1, f0=2500, f1=300, dur=.6)], [L("drone", 1, f=30, harm=(1, .7))], .5)
rec("ashen_sun", [L("rumble", 1.0, f=35), L("bursts", .6, n=20, lo=2000, hi=8000, dur=.03, amp=.4), L("hits", .6, n=2, f=45, decay=4)],
    [L("hits", 1, n=1, f=40, decay=2.5), L("rumble", .6, f=30)], [L("bursts", 1, n=14, lo=2000, hi=8000, dur=.03)], .4)
rec("black_tide", [L("rumble", 1.0, f=28), L("air", .5, lo=80, hi=400, amp=.6, lfo=.1), L("bubble", .6, n=10), L("drone", .5, f=40)],
    [L("bubble", 1, n=6)], [L("sweep", .7, n=1, f0=160, f1=40, dur=2.2), L("rumble", .5, f=35)], .6, lp=1500)
rec("storm_that_waits", [L("wind", .9, center=600, amp=.5), L("hum", .2, f=95), L("thunder", .6)],
    [L("thunder", 1)], [L("bursts", 1, n=8, lo=1500, hi=9000, dur=.05), L("hum", .4, f=190)], .45)
rec("grey_forest", [L("creak", .8, n=4, f0=250, f1=170, dur=.8), L("wind", .2, center=500, amp=.2), L("bursts", .4, n=6, lo=2000, hi=6000, dur=.5, amp=.2), L("hits", .4, n=2, f=400, decay=25)],
    [L("hits", 1, n=1, f=380, decay=22, parts=(1, 1.8))], [L("bursts", 1, n=4, lo=2000, hi=6500, dur=.55, amp=.5)], .5)
rec("red_chapel", [L("drone", .9, f=55, harm=(1, .7, .5)), L("pulse", .6, rate=.6, f=50), L("choir", .5, notes=(55, 82.5), amp=.4), L("rumble", .4, f=70)],
    [L("choir", 1, notes=(55, 82.5, 110))], [L("pulse", 1, rate=.7, f=48, decay=6)], .65)
rec("nameless_catacombs", [L("drip", .7, n=5, f=420), L("bursts", .6, n=4, lo=300, hi=2500, dur=.25), L("rumble", .5, f=40), L("wind", .2, center=120, amp=.3)],
    [L("bursts", 1, n=5, lo=300, hi=2500, dur=.3)], [L("drip", 1, n=3, f=400)], .75, fx=("echo",))
rec("buried_kingdom", [L("air", .5, lo=2000, hi=6000, amp=.3, lfo=.08), L("metal", .5, n=2, f=130, decay=1.0), L("rumble", .6, f=38), L("hits", .4, n=3, f=60, decay=6)],
    [L("metal", 1, n=1, f=120, decay=.8)], [L("bursts", 1, n=4, lo=200, hi=2000, dur=.4), L("hits", .6, n=1, f=55, decay=5)], .7)
rec("unfinished_ritual", [L("pulse", .9, rate=.5, f=48, jitter=.2), L("drone", .6, f=55, lfo=.3), L("choir", .4, notes=(82.5, 110), amp=.4)],
    [L("sweep", 1, n=1, f0=200, f1=600, dur=1.6)], [L("pulse", 1, rate=.8, f=48)], .6, fx=("gate", "fadeout"))
rec("silent_monastery", [L("bell", .8, n=2, notes=(220, 165), decay=.9), L("wind", .5, center=180, amp=.4), L("air", .2, lo=500, hi=1500, amp=.2)],
    [L("bell", 1, n=1, notes=(196,), decay=.8)], [L("footsteps", 1, n=1, f=100)], .9, lp=2800)
rec("endless_cemetery", [L("wind", .8, center=900, amp=.4, gust=.1), L("bell", .5, n=1, notes=(330,), decay=1.3), L("bursts", .3, n=4, lo=500, hi=2500, dur=.2), L("drone", .5, f=45)],
    [L("bell", 1, n=1, notes=(247,), decay=1.1)], [L("bursts", 1, n=6, lo=500, hi=3000, dur=.18)], .55)
rec("corrupted_chunk", [L("glitch", .9, n=14, amp=.4), L("bursts", .5, n=8, lo=1000, hi=9000, dur=.1), L("sweep", .4, n=4, f0=200, f1=3000, dur=.4), L("drone", .4, f=50)],
    [L("bursts", 1, n=6, lo=1000, hi=9000, dur=.2)], [L("sweep", 1, n=3, f0=300, f1=2500, dur=.5), L("glitch", .8, n=5)], .2, fx=("crush", "gate"))
rec("white_void", [L("drone", .03, f=1000, harm=(1,), amp=.5), L("air", .03, lo=4000, hi=6000, amp=.3), L("hits", .7, n=1, f=800, decay=2.0)],
    [L("hits", 1, n=1, f=660, decay=1.5)], [L("hits", 1, n=1, f=990, decay=1.8)], .5)
rec("blind_observatory", [L("ticks", .5, rate=6, f=600, decay=30), L("drone", .5, f=50, harm=(1, .5)), L("drone", .2, f=1760, harm=(1,), det=.005, amp=.3), L("metal", .5, n=2, f=300)],
    [L("sweep", 1, n=1, f0=300, f1=120, dur=1.8), L("metal", .5, n=1, f=250)], [L("drone", 1, f=1760, harm=(1,), det=.005, amp=.4, lfo=.5)], .55)
rec("forbidden_index", [L("bursts", .6, n=3, lo=1500, hi=6000, dur=.4), L("air", .3, lo=400, hi=1500, amp=.4, lfo=.2), L("creak", .4, n=2, f0=150, f1=100, dur=1.0), L("drone", .4, f=65)],
    [L("bursts", 1, n=1, lo=1500, hi=6500, dur=.5)], [L("whisper", 1)], .7)
rec("signal_core", [L("motif", .8), L("air", .5, lo=300, hi=4000, amp=.4, lfo=.3), L("pulse", .7, rate=.8, f=40), L("metal", .4, n=2, f=110, decay=1.5), L("drone", .5, f=45)],
    [L("motif", 1, step=.35, amp=.4)], [L("sweep", .8, n=2, f0=400, f1=2800, dur=.9), L("bursts", .8, n=6, lo=800, hi=7000, dur=.15)], .5)
rec("unwritten_end", [L("bell", .4, n=1, notes=(392,), decay=1.0), L("choir", .3, notes=(110, 165), amp=.4, rev=True), L("pulse", .4, rate=.5, f=38), L("clocks", .3, rates=(.8,)), L("air", .3, lo=300, hi=3000, amp=.3)],
    [L("bell", .5, n=1, notes=(392,), decay=1.0)], [L("motif", .5, step=.6, amp=.2)], .8, fx=("fadeout",))

BED_SECS, ACC_SECS = 16.0, 3.0


def render_dim(slug):
    rc = R[slug]
    seed = zlib.crc32(slug.encode())
    bed = render(rc["bed"], BED_SECS, seed, rc["rv"], rc["fx"], fade=.6, lp=rc["lp"], vol=rc["vol"])
    a1 = render(rc["a1"], ACC_SECS, seed + 1, rc["rv"], rc["afx"][0], fade=.03, lp=rc["lp"], vol=rc["vol"])
    a2 = render(rc["a2"], ACC_SECS, seed + 2, rc["rv"], rc["afx"][1], fade=.03, lp=rc["lp"], vol=rc["vol"])
    return bed, a1, a2
