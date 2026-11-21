# Changelog

## 2.2.1: Oaths for the truly devoted

The Oaths' buffs are for good, so they are now earned like it (the owner's wish: much, much harder).

| Oath | Relics | Feat |
|---|---|---|
| Raid Breaker | 20 Raider's Banner Shards | 10 raids won, and Defense 50 |
| Deep Dark | 12 Sculk Hearts | Exploration 75, and be there when 3 Wardens die |
| Tides | 15 Tidal Pearls | Fishing 75, and be there when 3 Elder Guardians die |
| Embers | 20 Ember Cores | Defense 75, and be there when the Wither dies |
| Void | 12 Void Shards | a skill at 100, and be there when the Ender Dragon dies |
| Wilds | 20 Verdant Idols | Foraging and Farming both at 75 |
| Champions | 15 Champion's Crests | 100 Champions slain |
| Legends | 5 of every relic | the seven other Oaths, the total level cap and 30 Mastery ranks |

- **An Oath is sworn at a beacon** (within 6 blocks), so the Wither comes first.
- **One Oath a week**: after swearing one, the next waits seven days. The Oaths tab says how long.
- **Relics are about three times as rare**: ancient cities 8%, buried treasure 10%, bastion treasure 12%, end city
  treasure 10%, temples 6–10%, ominous vaults 8%; raid captains 25%, Champions 8%, an Elder Guardian one pearl, the
  Ender Dragon two shards. Raids won still give one shard and one more for every omen level.
- Server owners tune it with `oath_relic_factor` (the relics asked for), `oath_cooldown_days`, `oath_beacon` and
  `relic_drop_factor` (every relic chance).

## 2.2.0: Relics and Oaths

Things to find out in the world, and quests that turn them into permanent buffs (the owner's wish).

### Seven relics
| Relic | Where from |
|---|---|
| **Raider's Banner Shard** | every raid won (one more for each omen level), raid captains, pillager outposts, woodland mansions |
| **Sculk Heart** | ancient cities (25%), the Warden |
| **Tidal Pearl** | buried treasure (35%), shipwreck treasure, ocean ruins, Elder Guardians (2), drowned (1%) |
| **Ember Core** | bastion treasure (40%) and other bastion chests, Nether fortresses, piglin brutes (15%), blazes (2%) |
| **Void Shard** | end city treasure (30%), shulkers (4%), the Ender Dragon (3) |
| **Verdant Idol** | jungle temples (30%), desert temples, woodland mansions |
| **Champion's Crest** | Champions (20%), trial chamber vaults (ominous 25%) |

Mobs only drop them when a player slays them, never from a spawner, egg or command.

### Eight Oaths
Swear them in the new **Oaths** tab of the Legends screen: the relics are taken, and the buff is yours for good, through
deaths and logins.

| Oath | Needs | Gives for good |
|---|---|---|
| Raid Breaker | 5 Raider's Banner Shards, a raid won | +2 hearts |
| Deep Dark | 3 Sculk Hearts, Exploration 30 | ability cooldowns 10% shorter |
| Tides | 4 Tidal Pearls, Fishing 30 | your breath lasts three times as long underwater |
| Embers | 5 Ember Cores, Defense 30 | fire burns you half as long |
| Void | 4 Void Shards, any skill at 50 | abilities last 15% longer |
| Wilds | 4 Verdant Idols, Foraging or Farming 30 | 5% quicker on foot |
| Champions | 5 Champion's Crests, 10 Champions slain | +2 armor |
| **Legends** | one of every relic, the seven other Oaths | **+10% XP in every skill**, and the title Oathkeeper |

- Two new titles: Oathsworn (your first Oath) and Oathkeeper.
- Admins: `/skills relic <player> <relic> [count]` (permission `extremeprogression.admin.boosts`); the option `relics`
  turns relics and Oaths off.

## 2.1.0: Skill potions

Two new potions to brew (the owner's wish): one that brings your abilities back sooner, one that makes them last
longer. Both are real status effects with their own icons, and work as splash and lingering potions and tipped arrows
too.

| Potion | Brew | Effect | Long (redstone) | Strong (glowstone) |
|---|---|---|---|---|
| **Potion of Quickening** | Awkward Potion + **clock** | your abilities' cooldowns run half again as fast | 8:00 | level II, twice as fast, 1:30 |
| **Potion of Persistence** | Awkward Potion + **amethyst shard** | an ability you set off lasts 30% longer | 8:00 | level II, +60%, 1:30 |

- The base potions last 3:00. Gunpowder makes them splash potions, dragon's breath lingering ones.
- Quickening works on every cooldown that is running while it lasts, so drinking it after using an ability still helps.
- Persistence works on abilities that last a while, set off while it lasts.
- Arcana's Potent Brews and the Alchemist's Extended make them last longer, like any brewed potion.
- New options: `quickening_per_level` (0.5) and `persistence_per_level` (0.3).

## 2.0.0: Legends

The biggest update yet (the owner's wish: XP boosts to earn, and a lot of new content). Everything is in the new
**Legends screen**: the first button along the Character Sheet's foot, or `/skills legends`.

### XP boosts: +25%, +50%, +100%, +200%
- **Tomes of Insight**, read with a right click: Tome of Insight (+25% XP for 30 minutes), Greater (+50%), Grand
  (+100%) and Legendary (+200% for 15 minutes). Read several: the strongest runs, the others wait their turn.
- **Their time runs only while you earn XP**, so nothing is lost while you build, trade or step away.
- **Well Rested**: every hour offline gives 6 minutes of +100% XP, up to an hour.
- **A daily login streak**: your first login each day gives a Tome of Insight, every 7th day a Greater, every 30th a
  Grand.
- **Tomes to earn**: every 25 levels in a skill (a Grand at level 100), every 5 Mastery ranks, bosses (the Ender Dragon
  a Legendary, the Wither and the Warden a Grand, an Elder Guardian a Greater, once a day each for everyone near),
  treasure chests (dungeons, temples, buried treasure, ancient cities, bastions, end cities, trial chambers),
  Champions and contracts.
- Boosts add to the party bonus, together at most +300%. Your boost shows above the pace, left of the hotbar.
- Admins: `/skills boost give <player> <percent> <minutes>` and `/skills boost tome <player> <tome> [count]` (for vote
  rewards), `/skills boost clear <player>`; `/skills boost` shows your own. Permission `extremeprogression.admin.boosts`.

### Champions
- Now and then a hostile mob is a **Champion**, its name above its head: three times the health, half again the damage
  and one of seven affixes: **Frenzied**, **Juggernaut**, **Vampiric**, **Venomous**, **Frostbound**, **Infernal** and
  **Warded**. More of them in the Nether and the End.
- Slay one for 400 XP on top of the fight's own, a good chance of a tome, and the weekly contract and titles.

### Contracts
- **Three contracts a day**: XP in two of your skills and a bounty on a mob. Each pays XP; all three a Tome of Insight.
- **One a week**: slay five Champions or earn 20,000 XP, for a Grand Tome.
- Swap one daily contract a day for another.

### Titles
- **Thirty-one titles** to earn: a grandmaster's for every skill at level 100, Adept to Legend for your total level,
  Dragonslayer, Witherbane, Wardenbane, Tidebreaker, Champion Hunter, Guildmaster, the Devoted, Trailblazer,
  Echo-Forged and more. Show one before your name above your head, in the tablist and in chat.

### Skill of the Day
- Every day one skill earns **+25% XP for everyone**. Every skill gets its day in turn.

### For server owners
- New options: `boosts`, `boost_max`, `rested_bonus`, `rested_minutes_per_hour`, `rested_max_minutes`,
  `daily_rewards`, `milestone_tomes`, `boss_tomes`, `loot_tomes`, `champion_chance`, `champion_kill_xp`, `contracts`,
  `skill_of_the_day_bonus`, `titles`.
- New placeholders: `title`, `title_prefix`, `boost`, `streak`. The shared TAB config shows the title before the name
  and the boost in the footer; the Styled Chat config shows the title in chat.
- `/skills champion <affix>` makes the nearest hostile mob a Champion (permission `extremeprogression.admin.events`).
- Player files move to schema 6 by themselves.

## 1.9.0: The Echo Forging ritual

Echo Forging was too easy for what it gives; it is now a ritual you do together (the owner's wish).

- **The Echo Forge**: the anvil must stand within 4 blocks of a **sculk catalyst** (`echo_catalyst_range`, 0 for
  anywhere). Catalysts only drop with Silk Touch, deep in the Deep Dark.
- **Voices**: every level beyond the maximum needs one more voice besides you (`echo_voices_per_level`): a **party
  member** within 8 blocks of the anvil (`echo_voice_range`) with an **echo shard in their off hand**. Sharpness VI
  takes two players, VII three. With parties turned off, any player counts.
- **In singleplayer an allay** holding an echo shard is a voice too; on a server only with `echo_allays_on_servers`.
- **Every shard is used up**: yours and each voice's (an allay's too). A Sharpness VII from scratch takes seven echo
  shards: two for each VI and three for the VII.
- **It costs more**: VI at least 20 levels, VII at least 30 (still under "Too Expensive").
- **The item remembers who forged it**: a line of lore, "Echo-forged by Steve, Alex & Sam", for every echo it went
  through.
- **The echo answers**: a sculk shriek, sculk souls rising from the anvil, and a moment of darkness for everyone near.
- What is missing is said on the action bar: no catalyst, or how many voices of how many. When a voice walks away (or
  lets go of the shard) before the result is taken, the result stays in the anvil.
- The anvil's result beyond the maximum is now worked out by the server alone; the client is sent what it shows.

## 1.8.0: The Pace and Echo Forging

- **The pace, left of the hotbar**: the one or two skills you earn the most XP in right now, each with its **XP an
  hour** over the last 10 minutes, **the time to its next level** at that pace (or to its next Mastery rank), and a
  bar of the progress. It shows while you earn and goes when you stop; the **XP/h** button at the top left of the
  Character Sheet turns it off (kept on your own client).
- **A new synergy, Echo Forging** (Arcana + Exploration, both at level 30): a special process at the anvil. With an
  **echo shard** in your off hand, two equal enchantments at their highest level combine into **one level beyond the
  maximum**, up to two: Sharpness V + V = VI, VI + VI = VII. It costs at least 10 levels (5 more a level beyond), and
  the shard when you take the result. Enchantments with a maximum of 1 (Mending, Silk Touch) never do.
- A level beyond the maximum is never lost at the anvil again: combining such an item keeps it.
- The Codex grows with the window so that its fourteen synergies fit.

## 1.7.0: Tailwind

- **A new synergy, Tailwind** (Exploration + Smithing, both at level 30): a firework rocket that boosts your elytra
  flight has a 50% chance not to be used up; it comes back to your hand. Discovered the first time you boost a flight
  with both levels met, like every synergy; its strength is in `synergies.json`.
- The Codex and the Chronicle fit thirteen synergies.

## 1.6.0: The Players Screen and permissions

- **A Players screen for admins** (as in Extreme Dimensions): `/skills players`, or the **Players** button at the top of
  the Character Sheet (shown only to who may). On the left every player the world knows, online first and then offline,
  with a search box; on the right the one picked: total, Mastery, deaths, death pressure, fatigue, party and synergies,
  and every skill with **−** and **+** (Shift: ten levels; at the maximum level a Mastery rank, and a minus takes ranks
  first), **↺** a free respec, **Abilities ready**, **Clear deaths**, and **Reset all** (press twice). Shift-click on
  ↺ (twice) resets one skill. It works for players offline too; the server checks every request, logs every change
  with who made it, and tells a player online.
- **Permissions for every admin command and screen**, through Fabric API's permission API, for LuckPerms and the like:
  `extremeprogression.admin.players` (the Players screen, `inspect`, `admin set|add|respec|cooldowns`),
  `extremeprogression.admin.reset` (resets), `extremeprogression.admin.settings` (the settings screen, `settings`,
  `preset`, `reload`) and `extremeprogression.admin.events` (`xprate`). Without a permission mod each falls back to
  the operator level it always needed (2 for players, 3 for the rest), and in a world of your own you hold them all.

## 1.5.1

- **Inspect** fits eleven skills: each tile is one line shorter (the specializations and the points share a line), so
  Trading no longer hides behind the Done button. The synergy count counts all of them.
- **Ranking** shows all eleven levels beside the name.

## 1.5.0: Trading

- **A new skill, Trading**, the first of a new category **Other** (on the Character Sheet under Survival, beside
  Arcana). XP for trading with villagers (more for higher offers and for every emerald), wandering traders, a villager
  reaching a new level through your trade, piglins bartering your gold, and a won raid. The same offer of one villager
  counts fully for 4 trades after each restock; villagers and piglins from spawn eggs, spawners or commands give
  nothing.
- **Its trunk**: Fair Price (5% lower prices), Good Word (villagers learn 10% faster from you), Barterer (a 10% chance
  of a second barter roll), Well Stocked (a 5% chance a trade does not use up the offer).
- **Three specializations**: **Merchant** (Haggle: 30 s of 20% lower prices), **Quartermaster** (Restock: the villager
  you look at restocks at once) and **Gold Tongue** (Gilded Offer: piglins barter at once and roll twice), each with a
  price, four passives and a choice.
- **Mastery perks** Patron and Golden Touch, and two **duo synergies**: Caravan (Trading + Exploration) and Market Day
  (Trading + Farming).
- The total cap stays 400: one more skill means more choice, not more levels.

## 1.4.3

- The Character Sheet's columns are in line: Survival starts beside Crafting, Exploration beside Smithing.
- The Leaderboard's button on the Character Sheet reads **Ranking**, so it fits beside Settings.

## 1.4.2

- **Fixed**: on a server with another mod's `/party` (BattleHub's), the two merged into one command, and accepting an
  invitation answered "No (valid) party invite from that player.". This mod's parties now stay out of the way: they
  are always under `/skills party`, and `/party` is theirs only when no other mod has one. The chat's Accept and
  Decline buttons use whichever it got; the Party screen never needed a command.

## 1.4.1

- The Party screen names the leader in words, with a **Lead** button to hand the lead on (the crown was too small to
  read), and its list of members is wider, so names fit.
- The Codex's and the Chronicle's foot lines are short enough to read whole.

## 1.4.0: Playing together

- **Parties**: `/party invite <player>` (or the new **Party** button on the Character Sheet), accept with a click in
  the chat. Up to 5 players; the leader can remove members and hand the lead on.
- **The party bonus**: +5% XP for every party member within 48 blocks who earns XP of their own (so never for one
  standing idle), up to +15% under Hardcore; +10% up to +30% under Normal. All configurable (`party_*`).
- **A fight is shared**: every hit already earns its own Melee or Ranged XP and every block its Defense XP, so a tank
  and the ones who strike each earn their part of every mob.
- **Six duo synergies** between two party members near each other, one skill each: Shield Wall, Spotter, Pincer,
  Field Medic, Whetstone and Hearty Meal. You discover them together; the Codex has a **Together** tab, and the
  Chronicle names both first discoverers.
- Party members carry a green **◆** before their name above their head; TAB placeholders `party_size`,
  `party_bonus`, `party_leader`, `party_members`, `party` and `parties`, and the party in the shared TAB footer.

## 1.3.2

- The level in a tracked skill's slot stands a little higher, clear of its progress bar, so both read at a glance.

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
