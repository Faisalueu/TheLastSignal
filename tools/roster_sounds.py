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
