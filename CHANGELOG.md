# Changelog

## 1.3.1: The tracked skills as a second hotbar

- The skills you track now sit in a **second hotbar** right of your own, in the hotbar's own frame: one slot per
  skill with its icon, the level as the slot's number (as an item count; the Mastery rank in gold past 100) and a
  green bar of the progress to the next level, where a tool shows its durability.

## 1.3.0: Tracked skills

- **Track** on every Skill Screen: the skills you track show right of your hotbar, each with its icon, level (or Mastery
  rank) and a bar of the progress to the next. Track as many as you like; nothing shows until you track one. Your
  choice is kept on your own client.
- The Skill Screen's buttons make room for it.

## 1.2.0: Power levels and a clearer Skill Screen

- **The power level above heads**: beside every player's name in multiplayer, their total level as in the tablist
  (`✦312`, a gold star with Mastery). Not shown in BattleHub's lobby and matches (option
  `nametag_hidden_dimensions`), and off with `nametag_power: false`.
- **The Skill Screen** is easier to read: the three specializations are tabs, the tree shows one branch at a time over
  its full width with every node's own name, lines show which node opens which, and the screen grows with the window.
- **The inventory button** sits inside the inventory now, between your figure and the crafting grid, clear of the
  recipe book and of Extreme Dimensions' buttons.

## 1.1.0: TAB

Extreme Progression on the player list and in chat, beside Extreme Dimensions and BattleHub.

- **Placeholders** for TAB, Styled Chat and other mods, through Text Placeholder API: `%extremeprogression:total%`,
  `highest`, `level_<skill>`, `specs`, `pressure`, `fatigue`, `synergies`, a level `badge`, the server's `preset`,
  `xp_event`, `discoveries`, `top` and more (the full list in `server/README.md`).
- **The shared server configs** in `server/config/`: TAB's player list shows the level badge behind every name, and
  the header and footer show the preset, XP events, your level, best skill, death pressure and fatigue. Styled Chat
  shows the badge and, on hover, your level and specializations.
- Options `placeholders` and `placeholder_refresh_ticks`.

## 1.0.0: First release

The first release of Extreme Progression, for Minecraft 26.3 on Fabric. Everything of 0.1.0 to 0.10.0 below, and:

- **Extreme Dimensions**: deaths in the Seam cost ×2.5, both mods' death penalties count in full, and the inventory
  buttons sit side by side. CI runs every test with Extreme Dimensions, Sodium, Iris and Mod Menu.
- **The balance** checked against the design: the pace (~400 hours to master a first skill, ~2,700 for four), Mastery,
  respecs and deaths, guarded by tests.
- An icon, the Modrinth page, and the MIT license.

## 0.10.0: Administration (in development)

Server owners can now shape the mod without touching code.

- **Presets**: Hardcore (the default), Normal (half the death loss, no whole levels lost, 50% faster XP) and Custom.
- **Data files** in `config/extremeprogression/`:
  - `xp/<skill>.json`, every XP value of Appendix A (another mod's blocks and mobs too, as `namespace:path`);
  - `skills.json`, skills on or off, their XP rate, ability cooldowns and hard requirements;
  - `synergies.json`, each synergy on or off and its strength.
- **`/skills reload`** rereads everything, and every client shows the server's own numbers.
- **A settings screen** for admins (`/skills settings`, or a button on the Character Sheet): every option, searchable,
  with the presets.
- **XP events**: `/skills xprate 2 mining 60`, announced in the chat and shown on the Character Sheet, ending by
  themselves.
- **Leaderboard**: a page on the Character Sheet (total or per skill) and `/skills top [skill]`.
- **`/skills inspect <player>`**: a read-only look at anyone's progression, offline players too.
- **`/skills admin reset <player> [skill]`**: only after `/skills admin reset confirm`.

## 0.9.0: Connections (in development)

The skills now push and pull on each other.

- **Opposing skills**: Melee ↔ Ranged, Defense ↔ Exploration, Smithing ↔ Arcana. Once one of a pair is above level 25
  and higher than the other, the other earns less XP, up to −60% at a gap of 50 levels, and its passive bonuses work
  at 75%. The Character Sheet and the Skill Screen show it.
- **Mastery perks**: past level 100, every Mastery rank gives one of two perks per skill, such as Giant Slayer or
  Duelist, Mother Lode or Swift Pick. Each rank adds 1%. Choose them on the new Mastery screen. A lost rank takes its
  perk with it.
- **Synergies**: twelve hidden synergies between two skills at level 30, such as Counterstrike, Charge, High Ground,
  Deep Walker, Seafarer and Herb Lore. You discover each one yourself, the first time its situation happens in play.
- **Codex & Chronicle** (a button on the Character Sheet): your synergies, with when and where you found them. The rest
  show as runes with a hint, which gets clearer once somebody on the server found it. The Chronicle lists the server's
  first discoverers, and a first discovery is announced in the chat.
- A specialization conflict now says why. Nodes with a hard requirement show the skill and level they need and yours.
  In heavy armor, Exploration's screen says that its passives are off.

## 0.8.0: Deaths (in development)

Deaths now cost you for real, and the game always shows the cost up front.

- **The loss**: every skill loses a share of its progress in the current level, or toward the next Mastery rank. Your
  highest skill loses the most and a skill at half its level half as much. A normal death never takes a level.
- **Death pressure**: +1 on every death, −1 for every hour of active play. At 0 a death takes 50%, from 1 it takes 100%,
  and from 2 your highest skill also loses a whole Mastery rank, or a whole level when it has none.
- **Dimensions**: deaths in the Nether cost ×1.5, in the End ×2, and in Extreme Dimensions' Seam ×2.5. There is no
  extra XP there.
- **Death fatigue**: for 10 minutes after a death every passive bonus works at half strength.
- **Death Register** (a button on the Character Sheet): your deaths, the pressure and when it drops, death fatigue, the
  dimensions' factors, **At Stake** (exactly what you would lose if you died now) and your last 50 deaths.
- **After respawning**: a summary of what the death cost.
- Lost levels set your newest points aside, and a specialization below 25 or 75 sleeps until the level is back.
- Deaths in Creative or Spectator cost nothing. The loss is saved the moment you die.

## 0.7.0: Specializations (in development)

Part 1 of 3: the twelve specializations of Melee, Ranged, Defense and Exploration now do what they say.

- **Abilities**: Riposte, Shatter, Flurry, Deadeye, Volley, Harpoon, Bastion, Unbreakable, Second Wind, Stride,
  Endure and Daredevil. Every rank after I makes one number stronger. An ability in effect shows above the ready icon
  with the seconds it still lasts.
- **Passives and choice nodes**: four passives and one choice of two per specialization, with real names and effects
  on the Skill Screen, from Keen Edge and Long Shot to Shield Bash and Climber.
- **Prices**: every specialization's trade-off now counts, such as Blade's −15% against heavy armor, Crusher's −10%
  attack speed and the Acrobat's extra knockback.
- Damage an effect deals itself (bleeding, a shockwave, spikes) gives no XP; area effects never hit other players.
Part 2 of 3: the twelve specializations of Mining, Foraging, Farming and Fishing.

- **Abilities**: Ore Sense (the ores near you glow through the walls, for you only), Earthmover (3 × 3 with a shovel),
  Tunnel Bore (1 × 3, 3 × 3 at rank V), Tree Feller (a whole tree, never placed logs), Bountiful Harvest, Wild Remedy,
  Harvest Festival (double yield, replants itself), Herding Call (animals follow you, twins), Rally the Pack, Feeding
  Frenzy, Lucky Cast (guaranteed treasure in open water) and Reel In.
- **Passives, choice nodes and prices** for all twelve, such as Rare Seeds, Honey Harvest (calm bees), Ice Fishing,
  Bookworm, Rider and the Tunneler's "no double drops".
Part 3 of 3: the six specializations of Smithing and Arcana.

- **Abilities** at their workstations: Flash Forge (furnaces 4× as fast), Masterwork (a full repair, sometimes free),
  Reforge (+10% maximum durability, once per item), Catalyst (brewing 10× as fast), Arcane Insight (every enchantment
  of the offers beside the table, and a free reroll) and Hex (a thrown potion's cloud twice as large and long).
- **New potions for the Alchemist**: Haste (cocoa beans), Resistance (an armadillo scute) and Absorption (honeycomb),
  brewed from an awkward potion by an Alchemist with Foraging 30.
- **Passives, choice nodes and prices** for all six, such as Second Smelt, Preservation, Trim Power, Extended, Rich
  Choice and Wide Mist. Potent Brews now also works on splash and lingering potions.
- With this, all thirty specializations do what they say.

## 0.6.0: Trees and abilities (in development)

- **Specializations**: choose the first at level 25 and the second at 75 on the Skill Screen. Specializations of
  different skills that exclude each other (such as Brawler and Bulwark) cannot be combined.
- **Points**: from level 25 every level gives 1 point (75 at level 100), shared by both specializations. Every branch
  has the same eight rows: passives, the active ability's ranks I to V and a choice node; a full branch costs 80 points,
  so nobody takes everything. The passives', choice nodes' and abilities' effects come in the next version.
- **Respec**: resets a skill's specializations and points for 20% of its XP, then not again for 7 days; the cost is
  shown before you confirm. Operators can give a free respec with `/skills admin respec`.
- **Lost levels** set your newest points aside; when you win the levels back, the points return to the same nodes.
- **The ability key**: `R` (rebindable) for a first specialization's ability, `Shift + R` for a second's, reached by
  what you hold or look at; cooldowns of 4 or 5 minutes, and a small icon beside the hotbar while it is ready.
- **The trunks**: the passive bonuses at levels 5, 10, 15 and 20 of all ten skills now work, from faster mining and a
  quicker attack bar to farmland you no longer trample and potions that last longer.
- The Skill Screen is interactive, and fits the screen at GUI scale 3.

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
