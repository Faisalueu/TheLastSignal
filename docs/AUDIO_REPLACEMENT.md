# Ambience of the two lake-entry dimensions and how to replace it

The YouTube clip (https://www.youtube.com/watch?v=P89rxnT7lKw) is NOT included: audio cannot be extracted from a YouTube link here and its licence is unknown. The Golden Cloud Realm ships with a **procedurally synthesised stand-in** (soft choir pad, air, wind, bell/music-box notes) made by `tools/dim_snd.py`; it is not that clip.

To use a licensed file: put OGG Vorbis files (mono or stereo, ~60 s, loopable) at
`src/main/resources/assets/lastsignal/sounds/dim/amb_golden_cloud_realm.ogg` and `amb_golden_cloud_realm_b.ogg` (the second may be a copy). `sounds.json` already lists them as streamed beds, the client fades them in/out and never overlaps two copies. No code changes are needed. `tools/extra_dims.py` never overwrites existing files unless `EXTRA_FORCE=1`.

Files: `amb_<slug>.ogg`, `amb_<slug>_b.ogg` (beds) and `acc_<slug>_1..4.ogg` (accents) for `golden_cloud_realm` and `infernal_volcano` (12 files). Volume: client config `ambienceVolume`, `audioIntensity`, `ambienceFrequency`.
