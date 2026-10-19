# Ambience of the two lake-entry dimensions and how to replace it

The Golden Cloud Realm bed is now the user-supplied file `Lost-Girl.ogg` (80 s, Vorbis, 48 kHz stereo), copied to `amb_golden_cloud_realm.ogg` and `amb_golden_cloud_realm_b.ogg`. The YouTube link itself was never used. The accents (`acc_golden_cloud_realm_*`) and the whole volcano set are still synthesised by `tools/dim_snd.py`. The user is responsible for the licence of the supplied file.

To use a licensed file: put OGG Vorbis files (mono or stereo, ~60 s, loopable) at
`src/main/resources/assets/lastsignal/sounds/dim/amb_golden_cloud_realm.ogg` and `amb_golden_cloud_realm_b.ogg` (the second may be a copy). `sounds.json` already lists them as streamed beds, the client fades them in/out and never overlaps two copies. No code changes are needed. `tools/extra_dims.py` never overwrites existing files unless `EXTRA_FORCE=1`.

Files: `amb_<slug>.ogg`, `amb_<slug>_b.ogg` (beds) and `acc_<slug>_1..4.ogg` (accents) for `golden_cloud_realm` and `infernal_volcano` (12 files). Volume: client config `ambienceVolume`, `audioIntensity`, `ambienceFrequency`.
