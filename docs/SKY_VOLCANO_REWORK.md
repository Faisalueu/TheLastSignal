# Sky and Volcano rework (what is real, what is verified)

## The two "obsolete" blocks (identified, not guessed)
The purple block is `lastsignal:signal_gate` (hue 317°, the old return gate) and the green one is `lastsignal:checkpoint_beacon`
(hue 150°). Both were placed by `Gen.pad()` on the arrival pad of the Golden Cloud Realm. They are no longer generated there:
`Gen.run()` skips `pad()` for `clouds` (and already did for `volcano`). Both blocks stay registered and are untouched in the
other 48 dimensions and in structures. CI checks that neither block exists in the 13x13 chunks around the cloud-realm spawn.
No saved chunk is rewritten: worlds that already generated the old pad keep it (no migration, nothing destructive).

## Golden Cloud Realm
* Exit: a gold dais (11 blocks north of arrival) with a vanilla beacon on a gold 3x3. Stand on it for 2 s (server polled every 5 ticks,
  position based, so mining the gold does not break it). Returns to the stored lake shore (same safe-landing + fall-protection path as before).
* Palace (`world/gen/Palace.java`), centre (8, 208), terrace floor Y 118, reached by a linear ramp (<= 0.5 block per block):
  terrace with gilded balustrade and four obelisks, great hall with columns, gold-ribbed quartz dome, throne dais, windows, entrance arch,
  rear balcony, two spiral-stair towers with balconies, two hidden rooms (west vault, east archive) behind panels that differ from the wall.
* Gold is real vanilla `gold_block` (about 400 in the palace, minable by design). Treasure: exactly 3 chests (`chests/dim/sky_palace`),
  deterministic loot seed, so regenerating a chunk never duplicates loot.
* Peaceful `lastsignal:white_presence`: all-grey/white texture (R=G=B, 216-255), no target or attack goals, spawns only here.

## White Presence
Server state machine WALK -> TAKEOFF -> FLY <-> HOVER -> LAND -> WALK synced as one int; animation is a pure function of it
(idle, walk, turn, takeoff, fly, hover, land, death). Vanilla FlyingMoveControl/FlyingPathNavigation in the air, ground navigation on the
ground. Fall damage disabled, void rescue, flees upward when hurt, never fights back. Own sounds (`entity.white_presence.*`, synthesized).

## Infernal Volcano
* Lavafalls (`Volcano.lavafall*`): a floating basalt ledge 28-42 blocks up with a lava pool; the centre pours falling lava (level 8) to a
  landing pool. Static fluid blocks (no scheduled ticks); anchors are >= 30 blocks from the ash road and >= 90 from spawn and exit.
* Client layer (`client/VolcanoFx`): lavafall particles, nearby lava sound, a sparse schedule of spatial sounds (distant eruption, rocks, heavy
  impact, far call, cave breath, steps behind you, sudden silence), a soft bell near the real exit. Entering plays a deep rumble.
* Hostile spawns (`entity/VolcanoSpawns`): hollow and crawler, only here, never within 80 of arrival, 70 of the exit or 10 of the road, max 2 horrors per 96 blocks.
* Exit unchanged: the hidden real crater (position based), `/lastsignal recover` as the operator safety net.

## Legacy entities
Nothing in the project was superseded: the 17 GeckoLib horrors are the active roster and were kept. Nothing was removed or unregistered.

## Not done / not verified
* No game client was run: models, animations, textures, particles and sounds are verified structurally only (files exist, JSON valid, ids registered).
* Sound is synthesized, not recorded. The cloud bed is the user-supplied Lost-Girl.ogg.
* Leaving-the-volcano sound and a separate dangerous-encounter warning cue were not added.
* The other 16 horror entities were not re-modelled in this pass.
