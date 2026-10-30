# Changelog

## 0.5.0: XP from the other six skills (in development)

- **Melee**: fully charged hits (attack bar at least 90%), by the damage dealt up to the target's remaining health × its
  danger factor. **Ranged**: fully drawn arrows, thrown tridents and crossbow fireworks, the same way, +2% per block
  beyond 10 blocks (at most +60%).
- **Defense**: what your shield blocked and your armor absorbed, from hostile mobs and players; only while you are
  playing, and at most 6 hits per attacker until it leaves you alone for a minute.
- **Exploration**: your first visit to each chunk (a quarter with an elytra), chunks left alone for 30 days, your first
  time in each biome, each structure the first time you enter it (the first of each type ×3), and falls you survive
  (once per spot for 10 minutes). Discoveries are saved in the player file (schema 2; older files load unchanged).
- **Smithing**: metal you take out of a furnace yourself, anvil repairs by material, netherite upgrades and armor trims.
- **Arcana**: potions the stand brewed that you take out yourself, enchanting at the table, combining enchantments at
  the anvil.
- **Farming**: the damage your tamed animals deal.
- Against exploits:
  - mobs from spawners, spawn eggs (also from a dispenser) and `/summon` give no combat XP; the mark is saved on the
    mob and passes to what it turns into. Trial spawners give full XP;
  - passive animals, player-built iron golems, and iron golems or zombified piglins that were not yet after you give
    nothing;
  - PvP: at most 60 damage (3 lives) per player per hour counts, and nothing from players with less than half of your
    total level. A same-IP rule can be turned on.

## 0.4.0: XP from gathering (in development)

- **Mining**: XP per block with a pickaxe that can mine it (rare ores much more than stone; deepslate more than its
  normal version), with a shovel for dirt, sand, gravel, clay, mud and snow, and for brushing suspicious sand or gravel.
- **Foraging**: logs of natural trees per wood type, and wild plants: berries, mushrooms, flowers, cocoa, melons and
  pumpkins, and honey from wild bee nests.
- **Farming**: fully grown crops (sugar cane and cactus above the bottom block), melons and pumpkins from a stem, planted
  cocoa and berry bushes; breeding, shearing and taming.
- **Fishing**: every catch, by kind: fish, treasure and junk.
- Against exploits:
  - **placed blocks never give XP**, and neither do blocks a piston moved or that lava and water formed. The marks are
    saved with the chunk, follow a block that falls, and stay when dirt turns into grass or a log is stripped;
  - **bonemeal**: crops, plants and trees it grew give nothing; a tree grown from a planted sapling gives 25%;
  - only what a **player** harvests by hand counts: no XP from explosions, pistons, water, dispensers or villagers;
  - **fishing** only gives XP in open water, and after ten catches in one spot that spot is "overfished";
  - **AFK**: no XP after 5 minutes without looking around, moving or interacting (for fishing: without looking around
    or moving);
  - **diminishing returns** above 5,000 XP per skill in 10 minutes, down to 5%;
  - no XP in Creative or Spectator.
- A short notice on the action bar on a level-up. Every value is in `config/extremeprogression.json`.

## 0.3.0: The GUI (in development)

- The **Character Sheet**: your figure, your total against the cap of 400, the budget factor and your Mastery ranks, and
  all ten skills as tiles grouped by category, each with its level, bar and a tooltip with the exact numbers.
- The **Skill Screen**: a skill's level, XP and milestones (25, 75, Mastery), its opposing skill, its three
  specializations with their ability and price, and its trunk; a detail panel shows whatever you select.
- Opens with **K** (rebindable), with a **button in the inventory** (beside Extreme Dimensions' buttons when that mod is
  there), or with **/skills**.
- In the house style of Extreme Dimensions: Minecraft's own tooltip panels, chat colours and flat meters.

## 0.2.0: Core progression (in development)

- The ten skills, in four categories.
- The level curve (≈ 400 hours to level 100, 10,000 XP per hour of focused play), the budget factor and the cap of 400
  total levels, and Mastery after level 100: 10 ranks of 400,000 XP.
- Progression is saved per player in `<world>/extremeprogression/players/`, with the world's saves and when a player
  leaves. Writes are crash-safe, three backups are kept, a damaged file falls back to its newest backup, and files from
  older or newer versions load without losing anything.
- `config/extremeprogression.json` lists every progression value.
- `/skills` shows your levels in chat; operators have `/skills admin set` and `/skills admin add`. An operator's change
  is always told to the player and written to the server log.
- The server sends every player their progression on joining and after every change.

## 0.1.0: Skeleton (in development)

- The project: Gradle and Loom for Minecraft 26.3 on Fabric (Java 25), with split client and server sources.
- The mod loads on a dedicated server and on the client, standalone or next to Extreme Dimensions.
- CI builds the mod, runs the server game tests, and runs a client test that saves screenshots.
