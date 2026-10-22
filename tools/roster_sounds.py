#!/usr/bin/env python3
"""Own synthesized ambient/hurt/death sounds (3 + 1 + 1 files) for each of the 12 roster-pass creatures, written to
sounds/entity/<id>/ and merged into sounds.json and the lang files. Idempotent; touches nothing else. Run from the repo root."""
import json, os, subprocess, wave
import numpy as np

RES = "src/main/resources/assets/lastsignal"
SR = 32000
# id: (base_hz, ratios, noise, vibrato_hz, vibrato_depth, ambient_len, rasp)   - each creature gets its own timbre
SPEC = {
    "black_witness": (62, [1, 1.5, 2.01], .10, 0.3, .01, 2.6, .0),
    "tall_black": (48, [1, 2.0, 3.97], .22, 0.2, .02, 3.0, .4),
    "borrowed_skin": (210, [1, 1.01, 2.0], .05, 5.0, .03, 1.8, .1),
    "drowned_choir": (330, [1, 1.25, 1.5, 2.0], .06, 0.6, .06, 3.2, .0),
    "hanging_man": (90, [1, 1.6], .04, 0.15, .01, 2.8, .0),
    "salt_mother": (120, [1, 2, 3, 4], .30, 0.4, .02, 2.2, .5),
    "radio_shepherd": (440, [1, 1.5, 3.0], .35, 7.0, .05, 2.0, .7),
    "glass_walker": (1100, [1, 2.76, 5.4], .02, 0.0, .0, 1.6, .0),
    "candle_bearer": (260, [1, 2.0], .08, 3.0, .02, 1.9, .0),
    "paper_man": (700, [1], .55, 0.0, .0, 1.4, .9),
    "stair_thing": (70, [1, 1.33, 1.99], .15, 1.2, .08, 2.4, .3),
    "moon_watcher": (180, [1, 1.5, 2.25], .03, 0.1, .005, 3.4, .0),
    # the original 17 get their own sets too (no more shared glitch sound)
    "observer": (55, [1, 1.01, 2.03], .12, 0.25, .015, 3.2, .0),
    "hollow": (150, [1, 3.0], .45, 0.8, .05, 2.0, .2),
    "mimic": (95, [1, 1.7, 2.9], .25, 2.5, .08, 1.6, .6),
    "signalman": (300, [1, 2.0, 4.0], .30, 9.0, .04, 2.2, .8),
    "witness": (40, [1, 1.5], .08, 0.1, .01, 3.6, .0),
    "crawler": (240, [1, 1.9, 3.1], .50, 6.0, .10, 1.2, .6),
    "lost_child": (520, [1, 1.5], .10, 4.0, .06, 2.0, .0),
    "doorman": (36, [1, 2.0, 3.0], .20, 0.3, .02, 2.8, .3),
    "pale_herd": (130, [1, 1.12, 1.5, 2.0], .05, 0.5, .05, 3.0, .0),
    "echo": (380, [1, 1.003, 2.0], .06, 0.2, .01, 2.6, .0),
    "burrower": (45, [1, 2.2, 3.3], .55, 1.5, .1, 1.8, .7),
    "lantern_widow": (880, [1, 1.5, 2.5], .04, 0.4, .02, 2.4, .0),
    "facet": (1500, [1, 2.4, 4.1], .03, 0.0, .0, 1.5, .0),
    "archivist": (110, [1, 1.25], .40, 0.2, .01, 2.4, .1),
    "signal_core": (30, [1, 2.0, 4.0, 8.0], .35, 0.15, .03, 3.8, .4),
    "buried_king": (52, [1, 1.5, 2.0], .18, 0.1, .01, 3.4, .1),
    "unfinished": (270, [1, 1.45, 2.1], .28, 3.5, .12, 2.1, .3),
}


def tone(spec, dur, pitch=1.0, seed=0):
    f0, ratios, noise, vf, vd, _, rasp = spec
    r = np.random.default_rng(seed)
    t = np.arange(int(SR * dur)) / SR
    ph = 2 * np.pi * f0 * pitch * t + (vd * f0 / max(vf, .01)) * np.sin(2 * np.pi * vf * t) * (vf > 0)
    x = sum(np.sin(ph * k + r.uniform(0, 6)) / (i + 1) for i, k in enumerate(ratios))
    x = x / len(ratios)
    n = r.standard_normal(len(t))
    k = np.ones(max(1, int(SR / (f0 * 4)))) / max(1, int(SR / (f0 * 4)))
    x = x * (1 - noise) + np.convolve(n, k, "same") * noise * 2.2
    if rasp:
        x = x * (1 - rasp * .5 + rasp * .5 * np.sign(np.sin(2 * np.pi * 37 * t)))
    return t, x


def env(t, a, d):
    return np.minimum(t / a, 1) * np.exp(-t / d)


def save(eid, name, x, peak=.55):
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    d = os.path.join(RES, "sounds/entity", eid)
    os.makedirs(d, exist_ok=True)
    wav = os.path.join(d, "_t_%s.wav" % name)
    with wave.open(wav, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype("<i2").tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", os.path.join(d, name + ".ogg")], check=True)
    os.remove(wav)


def main():
    sj_path = os.path.join(RES, "sounds.json")
    sj = json.load(open(sj_path))
    for eid, spec in SPEC.items():
        L = spec[5]
        for i, pitch in enumerate((1.0, .94, 1.07), 1):
            t, x = tone(spec, L, pitch, i)
            save(eid, "ambient%d" % i, x * env(t, .4, L * .6) * (1 - np.exp(-(L - t) * 3)))
        t, x = tone(spec, .5, 1.25, 11)
        save(eid, "hurt", x * env(t, .02, .15))
        t, x = tone(spec, 1.6, .6, 12)
        save(eid, "death", x * env(t, .03, .6))
        for g, files in (("ambient", ["ambient1", "ambient2", "ambient3"]), ("hurt", ["hurt"]), ("death", ["death"])):
            sj["entity.%s.%s" % (eid, g)] = {"sounds": ["lastsignal:entity/%s/%s" % (eid, f) for f in files], "subtitle": "subtitles.lastsignal.%s.%s" % (eid, g)}
    json.dump(sj, open(sj_path, "w"), indent=2)
    en = {"ambient": "%s murmurs", "hurt": "%s cries out", "death": "%s fades"}
    for code in ("en_us", "ar_sa"):
        p = os.path.join(RES, "lang/%s.json" % code)
        d = json.load(open(p))
        for eid in SPEC:
            nm = d.get("entity.lastsignal." + eid, eid)
            for g, f in en.items():
                d["subtitles.lastsignal.%s.%s" % (eid, g)] = (f % nm) if code == "en_us" else {"ambient": "%s يهمس", "hurt": "%s يصرخ", "death": "%s يتلاشى"}[g] % nm
        json.dump(d, open(p, "w"), indent=2, ensure_ascii=False)
    # Java id list (read by sound/EntitySounds)
    print("roster sounds written for", len(SPEC), "creatures")


if __name__ == "__main__":
    main()
