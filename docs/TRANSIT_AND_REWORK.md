# Dimension rework + Black/White fracture blocks

## Rework (48 dimensions)
* `tools/dim_profile.py` defines a per-dimension **Profile** (generated into `ProfileData.java`): room archetypes (cell, hall, pit, shaft, crawl, organic, columns),
  supercell size, hall style, landmarks, scatter density, chest density, loot table, fog distance, ambience weight.
* Landmarks (`Landmarks.java`) are evaluated per column in 128-block supercells, so towers, domes, villages, clocks, etc. span chunks without cross-chunk writes.
* Set pieces (`Gen.scatter`) use a world-anchored jittered grid; generation stays deterministic and chunk-local.
* Fog: client only, `fogMultiplier` x profile `fogFar` (120-480 blocks), eased. Root cause of the old problem: a hard-coded 22-40 block far plane.
* Audio: per dimension 2 streamed 60 s beds + 4 accents (288 ogg). Randomised intervals, 22 % deliberate silence, rare far event, fades, cleanup on dimension change / logout.
* Chests: deterministic, supported, 2 blocks of air above, loot table `lastsignal:chests/dim/<slug>` (48 tables, 5 pools: common/uncommon/rare/special/very rare). Loot is generated on first open only (vanilla behaviour).

## Black / White fracture blocks
* `lastsignal:black_fracture` (no collision): touching it teleports to a different dimension (deterministic per block, progression-locked gameplay dimensions excluded) and lands ~40 blocks above the ground (less if the ceiling is lower).
* `lastsignal:white_fracture`: returns to the Overworld (stored return point, else world spawn), ~120 blocks above safe ground (clamped to build height).
* Generated per 5x5-chunk cell, black and white in different chunks >= 2 apart, on solid floor with 3 free blocks above, never in the arrival chunk.
* `Transit.java`: per-player cooldown (5 s, stored in player data, set before teleporting), destination and landing validated (no lava, no solid column, no void), graceful failure messages.
* `TransitEvents.java`: fall protection exists only while the per-player tag `ls_land_until` is set (<= 60 s). A fall damage event clamps the damage so the player keeps >= 2 HP, then the tag is cleared. It is also cleared on landing / water / flight / logout / timeout. No global fall-damage change.
* Config (common): `transitEnabled`, `transitDensity`, `chestDensity`, `structureDensity`; (client): `fogMultiplier`, `ambienceVolume`, `ambienceFrequency`.

## What CI verifies (dedicated server, headless)
Per dimension: 11x11 chunks generate, arrival pad and exit gate, chest block entities all reference the dimension's loot table, the loot table loads and rolls items, fracture blocks counted, a 40-block landing plan is safe (feet/head free, fall column free).
It does NOT verify looks, sound quality, or the actual teleport/fall-damage behaviour with a real player: test those manually (`/lastsignal dimension <id>`, find a black/white block, walk into it).
