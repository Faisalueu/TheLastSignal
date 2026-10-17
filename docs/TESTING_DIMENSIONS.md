# Testing the 48 Phase-1 dimensions

Needs op (level 2). In a world with the mod + GeckoLib: `/lastsignal dimension <number or id>`. You arrive on a 7x7 pad in chunk 0,0. The **Signal Gate** on the pad (target 0) returns you; `/lastsignal recover` also works; falling into the void auto-rescues. No monsters/events spawn in these dimensions (Phase 1).

General checklist per dimension: (1) arrival pad is walkable and the gate/beacon exist; (2) terrain matches the terrain type; (3) the dimension's marking block (the `...` block whose texture is the glyph) appears as floor circles/wall inlays; (4) listed features appear; (5) sky/fog/particles match; (6) ambience starts within a few seconds (bed 16 s, non-looping, with random gaps) and stops when you leave; (7) walk 200+ blocks and check for gaps/lag.

| # | command | terrain | look for (features) | custom blocks |
|---|---|---|---|---|
| 01 | `/lastsignal dimension last_signal` | rooms | altar, chains, pillars, rubble | 4 blocks, prefix `d01_` |
| 02 | `/lastsignal dimension forgotten_village` | open | deadtree, fence, house, well | 4 blocks, prefix `d02_` |
| 03 | `/lastsignal dimension hollow_cathedral` | rooms | arches, pews, pillars, rubble | 4 blocks, prefix `d03_` |
| 04 | `/lastsignal dimension endless_archive` | rooms | cubicles, desks, shelves | 4 blocks, prefix `d04_` |
| 05 | `/lastsignal dimension silent_ward` | rooms | beds, cubicles, desks | 4 blocks, prefix `d05_` |
| 06 | `/lastsignal dimension last_station` | rooms | pillars, stairs_up, tracks | 4 blocks, prefix `d06_` |
| 07 | `/lastsignal dimension starless_expanse` | islands | arch, bridge, monolith, ruinpillars | 4 blocks, prefix `d07_` |
| 08 | `/lastsignal dimension watching_deep` | deep | eyepillar, platform, ruinwall, stalagmites | 4 blocks, prefix `d08_` |
| 09 | `/lastsignal dimension impossible_geometry` | islands | arch, bridge, ruinpillars, stairsnowhere | 4 blocks, prefix `d09_` |
| 10 | `/lastsignal dimension red_monolith` | open | monolith, mound, ringstones | 4 blocks, prefix `d10_` |
| 11 | `/lastsignal dimension pale_moon` | open | dome, ringstones, towerruin | 4 blocks, prefix `d11_` |
| 12 | `/lastsignal dimension fractured_beyond` | islands | arch, bridge, monolith, ruinpillars | 4 blocks, prefix `d12_` |
| 13 | `/lastsignal dimension living_walls` | rooms | cubicles, niches, pillars, rubble | 4 blocks, prefix `d13_` |
| 14 | `/lastsignal dimension rotting_garden` | open | deadtree, mound, mushroom | 4 blocks, prefix `d14_` |
| 15 | `/lastsignal dimension rootbound_abyss` | deep | rootcol, ruinwall, stalagmites | 4 blocks, prefix `d15_` |
| 16 | `/lastsignal dimension nest_below` | rooms | cubicles, niches, pillars, rubble | 4 blocks, prefix `d16_` |
| 17 | `/lastsignal dimension drowned_marsh` | open | deadtree, house, reeds, wreck | 4 blocks, prefix `d17_` |
| 18 | `/lastsignal dimension petrified_forest` | open | mound, ringstones, stonetree | 4 blocks, prefix `d18_` |
| 19 | `/lastsignal dimension rusted_engine` | rooms | machines, pillars, pipes, stairs_up | 4 blocks, prefix `d19_` |
| 20 | `/lastsignal dimension black_laboratory` | rooms | cases, cubicles, desks, machines | 4 blocks, prefix `d20_` |
| 21 | `/lastsignal dimension dead_grid` | open | fence, house, pylon | 4 blocks, prefix `d21_` |
| 22 | `/lastsignal dimension sealed_bunker` | rooms | cubicles, desks, machines, pillars | 4 blocks, prefix `d22_` |
| 23 | `/lastsignal dimension dead_server` | rooms | conduits, desks, racks | 4 blocks, prefix `d23_` |
| 24 | `/lastsignal dimension unfinished_mine` | rooms | pillars, rubble, tracks | 4 blocks, prefix `d24_` |
| 25 | `/lastsignal dimension yellow_silence` | rooms | cubicles, pillars | 4 blocks, prefix `d25_` |
| 26 | `/lastsignal dimension broken_hour` | rooms | arches, machines, pillars, stairs_up | 4 blocks, prefix `d26_` |
| 27 | `/lastsignal dimension mirrorless_hall` | rooms | arches, mirrors, pillars | 4 blocks, prefix `d27_` |
| 28 | `/lastsignal dimension forgotten_childhood` | rooms | pillars, stairs_up, toyblocks | 4 blocks, prefix `d28_` |
| 29 | `/lastsignal dimension empty_theater` | rooms | pillars, rubble, seats | 4 blocks, prefix `d29_` |
| 30 | `/lastsignal dimension staircase_below` | stairs |  | 4 blocks, prefix `d30_` |
| 31 | `/lastsignal dimension bloodless_desert` | open | mound, ringstones, saltpillar, towerruin | 4 blocks, prefix `d31_` |
| 32 | `/lastsignal dimension frozen_grave` | open | deadtree, icespire, tombs, towerruin | 4 blocks, prefix `d32_` |
| 33 | `/lastsignal dimension ashen_sun` | open | basalt, mound, ringstones, towerruin | 4 blocks, prefix `d33_` |
| 34 | `/lastsignal dimension black_tide` | sea | arch, coralspire, wreck | 4 blocks, prefix `d34_` |
| 35 | `/lastsignal dimension storm_that_waits` | islands | arch, bridge, lightningrod, monolith | 4 blocks, prefix `d35_` |
| 36 | `/lastsignal dimension grey_forest` | open | deadtree, mound, towerruin | 4 blocks, prefix `d36_` |
| 37 | `/lastsignal dimension red_chapel` | rooms | altar, arches, pews, pillars | 4 blocks, prefix `d37_` |
| 38 | `/lastsignal dimension nameless_catacombs` | rooms | niches, pillars, rubble | 4 blocks, prefix `d38_` |
| 39 | `/lastsignal dimension buried_kingdom` | rooms | arches, pillars, rubble, throne | 4 blocks, prefix `d39_` |
| 40 | `/lastsignal dimension unfinished_ritual` | rooms | altar, pillars, rubble | 4 blocks, prefix `d40_` |
| 41 | `/lastsignal dimension silent_monastery` | rooms | arches, cloister, niches, pillars | 4 blocks, prefix `d41_` |
| 42 | `/lastsignal dimension endless_cemetery` | open | mound, tombs, towerruin | 4 blocks, prefix `d42_` |
| 43 | `/lastsignal dimension corrupted_chunk` | islands | arch, bridge, monolith, stairsnowhere | 4 blocks, prefix `d43_` |
| 44 | `/lastsignal dimension white_void` | void | arch, monolith, ruinpillars | 4 blocks, prefix `d44_` |
| 45 | `/lastsignal dimension blind_observatory` | open | dome, ringstones, towerruin | 4 blocks, prefix `d45_` |
| 46 | `/lastsignal dimension forbidden_index` | rooms | cubicles, desks, shelves | 4 blocks, prefix `d46_` |
| 47 | `/lastsignal dimension signal_core` | rooms | altar, arches, conduits, pillars | 4 blocks, prefix `d47_` |
| 48 | `/lastsignal dimension unwritten_end` | void | arch, monument, ruinpillars, stairsnowhere | 4 blocks, prefix `d48_` |

## What CI verifies automatically (headless dedicated server, every push)
Chunks 7x7 around origin are generated in all 48 dimensions; arrival pad is checked (floor, free feet/head space, exit gate target 0); all material roles resolve to registered blocks. Result: see the `smoke-server` job annotations. This does NOT verify visuals, sound playback, fog, or multiplayer behaviour.
