# The Golden Cloud Realm and The Infernal Volcano (dimensions 49 and 50)

Added after the 48 horror dimensions; hand-written in `world/gen/ExtraThemes.java` so none of the 48 dimensions' data, sounds or loot tables changed.

| | id | terrain kind | purpose |
|---|---|---|---|
| 49 | `lastsignal:golden_cloud_realm` | `clouds` (`SkyRealm`) | calm, beautiful, sparse gold, return gate on the arrival island |
| 50 | `lastsignal:infernal_volcano` | `volcano` (`Volcano`) | dangerous, no loot, the exit is a hidden crater |

## Entry: diving in a natural Overworld lake (`world/LakeEntry`)
* Head under water for `lakeDiveSeconds` (default 2, config `lakeDiveSeconds`) in the Overworld. Checked every 10 ticks per player, server side.
* Leaving the water, dying, logging out or changing dimension resets the timer. A transition sets the player cooldown first (`transitCooldownSeconds`, default 5 s).
* "Natural lake" (heuristic, bounded flood fill, runs once per qualifying dive; a failed check backs off ~10 s): >= 300 connected water blocks, >= 8x8 extent, open sky above the water, biome is not ocean/river, and at most 3 % of the bank blocks are non-natural (glass, planks, bricks, ...). Buckets, small ponds, aquariums, roofed or glass/brick pools do not count. A huge player-made pool dug into dirt/stone would count: there is no way to know who made a water block.
* Destination: `RandomSource.nextBoolean()` per successful dive: exactly 50/50 in the long run (CI draws 20000 samples and checks the deviation).
* The return point is stored as dry land next to the lake (never in the water) so returning cannot re-trigger the dive.
* If the dimension is missing or anything throws, the player stays where they are ("The water is only water.").

## Golden Cloud Realm
Solid walkable cloud sea (top ~94 +-12), sky islands of different radius (9-27) and height (+8..+41), gilded pillars with lamps, floating rocks, rare shrines on big islands (one gold block each). Gold: lone surface blocks (~1 per 640 island surface columns), gold-ore flecks inside island stone, shrines, some floating rocks. No chests, no loot tables. Fog distance 420 blocks (client config `fogMultiplier`). Exit: the Signal Gate on the arrival island, or a white fracture block.

## Infernal Volcano
Bounded island (lava ocean beyond radius ~470): lava lakes, cones with perched lava calderas, 1-2 block lava fissures, ash/cinder/basalt, glowing vents. A flat "ash road" (3 blocks wide, smooth banks, causeway over lakes) leads from the spawn to a rock ring that hides the real exit crater; the ring is open on one side (facing away from the road), two signal-smoke campfires mark the ring. The real crater has a pale-orange rim circle and an ember vent at its floor. Decoy craters elsewhere are plain and are not exits.
Exit logic: server checks (every 5 ticks, volcano only) `Volcano.inExit`: within 3.2 blocks of the crater centre and at the floor. Then the player is moved to the Overworld surface at the stored shore point (no fall, so no protection needed). No gate, no beacon, no fracture blocks here; `/lastsignal recover` (if `recoveryEnabled`) and the void rescue still exist as safety nets. No chests, no loot.

## Fall protection (black/white fracture blocks)
`Transit.protect`: Slow Falling (400 + 10 per block, max 2400 ticks) plus a per-player tag; the first fall damage after it is cancelled (`TransitEvents.hurt`) and both effect and tag are removed on that landing, on water/flight/climbing, on timeout, logout, death or dimension change. Re-touching does not stack (tag/effect are replaced). Other damage and later falls are untouched.
