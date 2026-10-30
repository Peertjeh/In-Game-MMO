# Extreme Progression: Design Document

> Name: **Extreme Progression**, mod ID `extremeprogression` (round 25).
> Status: **Design approved (round 26). Implementation in progress on the `develop` branch: see §13.**

Legend for every mechanic:
- ✅ **Confirmed**: approved by the owner
- 💡 **Suggestion**: the designer's idea, not approved yet
- ❓ **Open**: a question still to be answered

---

## 0. Interview log

| Round | Topic | Status |
|------:|-------|--------|
| 1 | Platform and architecture, outline of the skill system | ✅ answered |
| 2 | Working with Extreme Dimensions, skill list, synergies, trade-offs | ✅ answered |
| 3 | Skill template: counteraction, active abilities, specializations, discovery | ✅ answered |
| 4 | Phase 3: scale, total budget, pace, specialization levels, opposing skills | ✅ answered |
| 5 | Phase 3 numbers: pace, cap, Mastery, respec, opposing skills | ✅ all approved |
| 6 | Phase 4 part 1: what a death costs, which skills, recovery, safety net, Extreme Dimensions | ✅ answered |
| 7 | Phase 4 part 2: repeated deaths, dimensions, fatigue, Mastery, PvP | ✅ answered |
| 8 | Phase 5: tree structure, points, ranks, cooldowns, unlearning | ✅ answered |
| 9 | Skill 1/10: Melee | ✅ all approved |
| 10 | Skill 2/10: Ranged (+ three specializations per skill) | ✅ all approved |
| 11 | Skill 3/10: Defense | ✅ approved |
| 12 | Skill 4/10: Mining | ✅ approved |
| 13 | Skill 5/10: Foraging | ✅ approved |
| 14 | Skill 6/10: Farming | ✅ approved; bonemeal gives no XP |
| 15 | Skill 7/10: Fishing | ✅ approved; a small pit gives no XP |
| 16 | Skill 8/10: Exploration | ✅ approved |
| 17 | Skill 9/10: Smithing | ✅ approved; Masterwork weakened |
| 18 | Skill 10/10: Arcana | ✅ approved |
| 19 | Specialization conflicts, synergies, hard requirements | ✅ approved; synergies may be in the repo |
| 20 | Phase 6: style, screens, skill tree, "At Stake", HUD | ✅ approved |
| 21 | GUI mockup | ✅ approved ("let's go on with this"); feedback may follow later |
| 22 | Language | ✅ the mod and its repository in English; the design chat stays in Dutch |
| 23 | Phase 7: anti-exploit | ✅ approved |
| 24 | Appendix A: XP tables; Dutch translation | ✅ approved; English only, no translation |
| 25 | Phases 8 and 9: name, storage, configuration, commands, leaderboard | ✅ approved |
| 26 | Phase 10: the complete design | ✅ approved |

---

## 1. Project architecture

### Confirmed
- ✅ **Option A**: a Fabric mod that is required on the server **and** on every client. The client shows a fully custom GUI (no chest menus).
- ✅ The menu opens with a **hotkey** and can **also be reached from the inventory screen**.
- ✅ New items and blocks **only when truly necessary**. The rule is to strengthen vanilla mechanics.
- ✅ **Minecraft 26.3, Fabric** (Java 25), the same stack as Extreme Dimensions.
- ✅ **Standalone**: the mod works without Extreme Dimensions. An **optional integration** with ED is wanted.
  - 💡 Technically: ED is listed only under `suggests` in `fabric.mod.json`, never under `depends`. All integration code lives apart (`compat/extremedimensions`) and only runs when `FabricLoader.isModLoaded("extremedimensions")` is true. Without ED that code is never loaded.
  - What the integration does: §1, "Extreme Dimensions integration" below.
- ✅ **The mod is in English** (round 22): all in-game text, the documentation and this document. Only the design conversation with the owner is in Dutch.
  - ✅ **English only** (round 24): no Dutch translation is shipped. The text still lives in `en_us.json`, so translations stay possible later.

### Extreme Dimensions integration (💡 proposal, round 26)
Only when Extreme Dimensions is installed:
- ✅ The **Seam** multiplies the death penalty by **×2.5** (round 7).
- ✅ Both mods' death penalties **stack in full** (round 6).
- 💡 Our inventory button sits right of ED's Journal and Curses buttons, so they never overlap (§9).
- 💡 The Seam's structures (villages, looms, colossal buildings, the Return Gate) count as **new structures** for Exploration.
- ❓ Does a **Collapse** (the Tether runs dry and the player wakes up at home) count as a death for this mod? (§7)

Without Extreme Dimensions none of this code is loaded.

### State of the repository (inspected on 2026-10-30)
- `Peertjeh/In-Game-MMO`: 1 commit, only `README.md` ("MMO With GUI"). Greenfield: no code, build, GUI or storage.

### Reference 1: Extreme Dimensions (`Peertjeh/extremedimensions`, v3.7.0)
The style and conventions reference, by the same author.
- **Stack:** Minecraft **26.3**, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3, Loom 1.18 with `splitEnvironmentSourceSets` (`src/main` and `src/client`), **Java 25**, Gradle.
- **Tests and CI:** server game tests and a client game test with screenshots in GitHub Actions; a compatibility run with Sodium, Iris and Mod Menu; an automatic release per version with `CHANGELOG.md`.
- **Languages:** `en_us.json` + `nl_nl.json`. `checkTranslations` enforces that `nl_nl` is complete and uses the same placeholders.
- **GUI:** custom client `Screen`s (Journal, Curses, Status, Trials, ...), all drawn through `client/ui/Ui.java`. Its house style is vanilla's own: panels like the tooltip box (near-black violet with a fading violet frame), the 16 chat colours, meters as flat as a boss bar, a colour-blind option and a reduced-flashing option.
- **Inventory:** buttons right of the recipe book. Journal at `book + 2`, Curses at `book + 24` (20×18 px); they follow the book when it opens.
- **Hotkeys:** in a category of their own, **unbound** by default (`InputConstants.UNKNOWN`).
- **Networking:** the server is authoritative. The client asks with a packet, the server sends the state, the client opens the screen.
- **Storage:** one JSON file per world (`<world>/extremedimensions.json`, Gson). Changes are marked dirty and written together with the world's own saves. Older saves are filled in (`filled`), and `MigrationTests` loads saves of older versions.
- **Its own death system:** Collapse (Tether runs dry) → pick 1 of 3 curses; 92 curses + 24 Abyssal; atonement, scars, Mending Cloth; "every death undoes half of the atonement". **Relevant to phase 4 (stacking penalties).**

### Reference 2: mcMMO (design reference only, not a dependency)
Source: `mcMMO-Dev/mcmmo-wiki-repo` (documentation, no source code).
- A Spigot/Paper/Folia plugin for MC 1.20.5+. **Not usable as a code base.**
- 19 skills: combat (Swords, Axes, Archery, Crossbows, Maces, Spears, Tridents, Unarmed, Taming), gathering (Mining, Woodcutting, Excavation, Herbalism, Fishing), passive (Acrobatics), utility (Alchemy, Repair) and child skills (Salvage, Smelting).
- Levels 0–100 or 0–1000 (RetroMode). XP curve: linear `1020 + 20 × level`, or exponential `0.1 × level^1.8 + 2000`.
- Sub-skills with fixed ranks, plus one active "Super Ability" per skill (right-click with a tool).
- Child skills: level = the average of two parents.
- Hardcore: lose X% of the levels above a threshold (75% by default), no recovery.
- Vampirism: in PvP the killer steals levels.
- Anti-exploit: placed blocks give no XP, spawner/egg/portal mobs give 0 XP, diminishing returns, a fishing movement check, an XP ceiling for combat.
- Interface: chat commands and an XP boss bar.

Weaknesses we solve on purpose:
1. Weapon skills overlap. → ✅ solved with **playstyle skills**.
2. No choices. → specializations (phase 5).
3. Hardcore is blunt and opaque. → phase 4.
4. No real interface. → ✅ a custom client GUI.

### Conventions taken from Extreme Dimensions (💡)
- **Same stack:** MC 26.3, Fabric, Java 25, the same Fabric API version, so both mods run on the same server.
- **Same conventions:** split source sets, game tests in CI, Gson JSON per world with migration tests, translation files with a translation check, CHANGELOG releases.
- **Same visual language:** a `Ui` class of our own in the same vanilla style, so both mods feel like one game, with an accent colour per skill category.
- **Inventory button:** right of Extreme Dimensions' buttons (`book + 46`), or, without ED, right next to the book. Never overlapping.
- **Hotkey:** `K` by default, rebindable in Controls.

---

## 2. Skills

### Confirmed
- ✅ **A compact set.** Every skill has its own tree, its own GUI page and its own XP table.
- ✅ **Skills per playstyle**, not per weapon or tool.
- ✅ **Skills connect through synergies that players discover themselves.** Here and there a **hard requirement** (option D).
- ✅ **The 10 skills below are approved**, each with **three specializations** (round 10).
- ✅ **Undiscovered synergies** show as **runes with a vague hint** (option B), like the unknown curses in ED.
- ✅ **Discovery is both server-wide and personal** (the owner: "server-wide is fun, but I also want something personal"):
  - **Personal:** every player must discover a synergy themselves before it works for them. Their own codex shows when and where they found it.
  - **Server-wide:** the first discoverer goes into the server's **chronicle** (a GUI page), optionally with a chat announcement (configurable). After that first discovery the rune hint gets clearer for everyone else (part of it becomes readable).
  - **First discoverer:** a lasting, purely cosmetic "First Discoverer" mark on that synergy. No extra power.
- ✅ **Specializations always have trade-offs**: every choice has a price.
- ✅ **Skills may counteract each other**, through:
  - ✅ **a) Gear conflicts:** what you wear or hold decides which bonuses work. Example: in heavy armor the movement bonuses of Exploration do not work.
  - ✅ **b) Specialization conflicts:** some specializations of different skills exclude each other (see the table below).
  - ✅ **c) Opposing skills**, harsher than the first proposal (−20% from level 50, rejected):
    - Pairs: **Melee ↔ Ranged**, **Smithing ↔ Arcana**, **Defense ↔ Exploration**.
    - The penalty starts once the opposing skill is **above level 25** and higher than this skill.
    - It grows with the level gap, up to **−60% XP** at a gap of 50 levels.
    - On top of that, the **passive bonuses** of the weaker skill work at only **75%**.
    - Always visible in the GUI.
  - ✅ **d) Total budget: mandatory, in the hybrid form** (see §4).

### The 10 skills

| # | Skill | Category | Playstyle | XP mainly from | Specializations |
|--:|-------|----------|-----------|----------------|-----------------|
| 1 | **Melee** | Combat | Everything at close range: sword, axe, mace, spear, trident, fists | Damage to hostile mobs and players, weighted by danger | Blade · Crusher · Brawler |
| 2 | **Ranged** | Combat | Bow, crossbow, thrown trident | Hits; more for distance | Sniper · Skirmisher · Harpooner |
| 3 | **Defense** | Combat | Taking hits and surviving | Damage blocked by a shield or absorbed by armor (hostile sources only) | Bulwark · Juggernaut · Resilience |
| 4 | **Mining** | Gathering | Underground: pickaxe and shovel | Ores (rarer = more), stone, depth (no dimension bonus, see §7) | Prospector · Excavator · Tunneler |
| 5 | **Foraging** | Gathering | The wild: trees and wild plants | Natural logs, wild berries, mushrooms, flowers | Lumberjack · Gatherer · Herbalist |
| 6 | **Farming** | Gathering | Growing and tending: crops and animals | Fully grown harvests, breeding, shearing, taming | Grower · Herder · Beastmaster |
| 7 | **Fishing** | Gathering | On and by the water | Catches (rarer = more), treasure | Angler · Treasure Hunter · Hookmaster |
| 8 | **Exploration** | Survival | Travel and movement | New chunks, biomes and structures, survived falls | Wanderer · Survivalist · Acrobat |
| 9 | **Smithing** | Crafting | Metal and gear: furnace, anvil, smithing table | Smelting metal, repairing, upgrading | Forgemaster · Armorer · Tinkerer |
| 10 | **Arcana** | Crafting | Brewing and enchanting | More complex potions, higher enchantments | Alchemist · Enchanter · Hexer |

How the mcMMO skills were folded in:
- Woodcutting and Herbalism are split into **wild** (Foraging) and **cultivated** (Farming): a clearer difference in playstyle.
- Taming belongs to Farming (Beastmaster).
- Acrobatics is part of Exploration (Acrobat).
- Brewing and Enchanting are combined in Arcana.
- Excavation belongs to Mining (Excavator).
- There is no Cooking skill (in vanilla, cooking is only smelting). Food effects sit in Foraging (Herbalist) and in the trunks of Farming and Fishing.

### Skill 1/10: Melee (✅ approved in round 9)
**Identity:** fighting up close. Melee rewards timing and the right weapon, not click spam.

**XP:**
- XP = damage dealt × the target's danger factor.
  - Damage counts at most up to the target's remaining health (no XP for overkill).
  - Example danger factors: zombie 1.0 · skeleton 1.2 · creeper 1.5 · enderman 1.5 · wither skeleton 2.0 · warden and bosses 3.0.
- **Only fully charged hits** (attack bar ≥ 90%) give XP, so click spam and macros earn nothing.
- Passive animals give no XP. Mobs from spawners or spawn eggs give no XP (phase 7).
- ✅ **PvP damage gives XP too.** Measures against farming with a second account: phase 7.

**Trunk** (free, unlocks by itself):
| Level | Bonus |
|---|---|
| 5 | Combat Rhythm: the attack bar charges 3% faster |
| 10 | Full Swing: fully charged hits deal +5% damage |
| 15 | Sure Footing: 10% less knockback while attacking |
| 20 | Finishing Blow: critical hits (while falling) deal +10% damage |

**Specializations** (the first at level 25, the second at level 75):
| | **Blade** | **Crusher** | **Brawler** |
|---|---|---|---|
| Weapons | Sword, spear, trident (up close) | Axe, mace | Fists (empty hand) |
| Playstyle | Precision, bleeding, parrying | Heavy blows, breaking armor, staggering | Speed, combos, disarming |
| Active ability | **Riposte**: for 3 seconds the next hit that lands on you is fully parried, and you strike back with a guaranteed crit plus bleeding. Cooldown 4 min. | **Shatter**: your next full hit within 5 seconds releases a shockwave. It hits every enemy within 3 blocks, knocks them back and sets their armor to 0 for 8 seconds. Cooldown 5 min. | **Flurry**: for 6 seconds you ignore the attack bar, and every hit builds your combo. Cooldown 4 min. |
| Trade-off | −15% damage against targets in heavy armor | −10% attack speed with **all** weapons | Works only with an empty hand **and** without a chestplate of iron or heavier |
| Example choice node | Deep Cut (longer bleeding) **or** Quick Cut (+attack speed) | Earthquake (a wider mace smash) **or** Cleave (the axe keeps shields disabled longer) | Iron Fist (+damage) **or** Disarm (a chance that a player drops their weapon) |

**Mastery perks** (example, 1 of 2 per rank): +1% damage against bosses **or** +1% damage against players.

**Counteraction:**
- Opposing skill: **Ranged**.
- Gear conflict: Brawler (see its trade-off).

**No new items needed.**

### Skill 2/10: Ranged (✅ approved in round 10)
**Identity:** fighting at a distance. Ranged rewards patience, position and good aim.

**XP:**
- XP = damage dealt × danger factor × **distance bonus**.
  - The distance bonus is +2% per block beyond 10 blocks, up to +60% (at 40 blocks). ✅ Approved.
  - Damage counts at most up to the target's remaining health.
  - The danger factors are the same as for Melee.
- **Only a fully drawn bow** (≥ 90%) gives XP. A crossbow is always fully loaded.
- PvP damage counts, as with Melee.
- No XP from passive animals or spawner mobs.

**Trunk:**
| Level | Bonus |
|---|---|
| 5 | Steady Hand: you draw the bow 5% faster |
| 10 | Arrow Recovery: a 10% chance to recover your arrows from a slain mob |
| 15 | Keen Eye: fully drawn shots deal +5% damage |
| 20 | Wind Reader: arrows fly 5% faster (less drop) |

**Specializations:**
| | **Sniper** | **Skirmisher** | **Harpooner** |
|---|---|---|---|
| Weapons | Bow, crossbow | Bow, crossbow | Thrown trident |
| Playstyle | Distance, headshots, piercing | Moving while shooting, fast reloads | Throwing, pulling in, water and rain |
| Active ability | **Deadeye**: zoom in like a spyglass. Your next full shot within 8 seconds is a guaranteed crit and pierces 1 target; a headshot deals +100%. Cooldown 4 min. | **Volley**: for 5 seconds every shot fires 3 arrows in a fan, for the price of 1 arrow. Cooldown 4 min. | **Harpoon**: your next thrown trident pulls the target to you. Cooldown 4 min. |
| Trade-off | −20% damage on targets closer than 8 blocks | −20% damage on targets beyond 20 blocks | Bow and crossbow deal −15% damage |
| Example choice node | Piercing (+1 target) **or** Headshot (+damage to the head) | Quick Reload (crossbow −25% load time) **or** Running Shot (no slowdown while drawing) | Return (the trident comes back even without Loyalty, slowly) **or** Tidal Power (+damage in water or rain) |

**Mastery perks** (example): +1% damage against flying mobs **or** +1% damage against players.

**Counteraction:**
- Opposing skill: **Melee**.
- A trident used up close belongs to Melee (Blade); a thrown trident to Ranged.

**No new items needed.**

### Skill 3/10: Defense (✅ approved in round 11)
**Identity:** taking hits and surviving. Defense is the skill of whoever stands in front.

**XP:**
- XP = damage your **shield blocks** or your **armor absorbs**, × the attacker's danger factor. ✅ No separate bonus for surviving at low health.
  - For armor, the difference between the raw damage and the damage you actually take counts.
- Only damage from **hostile mobs and players** counts.
  - Environmental damage (falling, fire, lava, drowning, cactus) gives no Defense XP.
  - Fall damage belongs to Exploration (skill 8).
- Against AFK farming (standing in full armor in front of a spawner):
  - you only get XP if you attacked or moved yourself in the last few seconds;
  - one attacker gives XP at most 6 times, then again after 60 seconds without hits from that attacker (like mcMMO's dodge rule).

**Trunk:**
| Level | Bonus |
|---|---|
| 5 | Shield Reflex: your shield is ready 1 tick sooner (vanilla waits 5 ticks) |
| 10 | Hardened: 3% less damage from hostile mobs |
| 15 | Catch Breath: natural regeneration starts 2 seconds sooner after a fight |
| 20 | Steadfast: 5% knockback resistance |

**Specializations:**
| | **Bulwark** | **Juggernaut** | **Resilience** |
|---|---|---|---|
| Gear | Shield | Heavy armor (iron and heavier) | Anything: recovery instead of plate |
| Playstyle | Blocking, reflecting, protecting others | Absorbing blows, never yielding | Shaking off effects, recovering fast |
| Active ability | **Bastion**: for 6 seconds your shield blocks from **all** directions, and allies up to 3 blocks behind you take 30% less damage. Cooldown 4 min. | **Unbreakable**: for 8 seconds no knockback and 40% less damage, but you cannot sprint. Cooldown 5 min. | **Second Wind**: removes every negative effect at once, plus 6 hearts of healing over 5 seconds. Cooldown 5 min. |
| Trade-off | **Without a shield** in hand you take +10% damage | −10% movement speed with 3 or more pieces of iron or heavier | Your armor protects 10% less |
| Example choice node | Mirror Shield (25% of a blocked projectile bounces back) **or** Shield Bash (block at the moment of the hit and the attacker is stunned for 1 second) | Spikes (10% of melee damage returns to the attacker) **or** Anchor (full knockback immunity while sneaking) | Antidote (immune to poison) **or** Cool Blood (you burn 50% shorter) |

**Mastery perks** (example): +1% damage reduction against bosses **or** against players.

**Counteraction:**
- Opposing skill: **Exploration** (heavy against light).
- Gear conflict: in heavy armor the movement bonuses of Exploration do not work.
- Brawler (Melee) may not wear a chestplate of iron or heavier, so it clashes with Juggernaut by itself.

- ✅ **Group abilities are allowed.** Abilities that also help allies, like Bastion, are fine.

**No new items needed.**

### Skill 4/10: Mining (✅ approved in round 12)
**Identity:** work underground with pickaxe and shovel: finding ores, digging tunnels and searching the ground.

**XP:**
- XP per block, from a configurable table.
  - Rare ores give a lot, stone a little.
  - Depth is part of the table: deepslate and deepslate ores give more than their normal versions. There is no separate depth bonus and no dimension bonus.
- With the shovel: dirt, sand, gravel, clay, mud and soul sand (a little). **Brushing suspicious sand and gravel** (archaeology) gives a lot.
- **Placed blocks never give XP.** Neither do blocks from a cobblestone generator (lava + water).

**Trunk:**
| Level | Bonus |
|---|---|
| 5 | Miner's Rhythm: +3% breaking speed with pickaxe and shovel |
| 10 | Sturdy Tools: a 5% chance that a block costs no durability |
| 15 | Cave Lore: 10% less damage from dripstone, magma blocks and falling gravel or sand |
| 20 | Double Find: a 5% chance of a double ore drop (Ancient Debris excluded, configurable) |

**Specializations:**
| | **Prospector** | **Excavator** | **Tunneler** |
|---|---|---|---|
| Tool | Pickaxe | Shovel and brush | Pickaxe |
| Playstyle | Finding ores and getting more from them | Digging, treasure and archaeology | Speed and big tunnels |
| Active ability | **Ore Sense**: for 10 seconds ores within 12 blocks glow through the walls. The server only sends those positions, so it is no x-ray for the whole world. Cooldown 5 min. ✅ Approved as proposed. | **Earthmover**: for 8 seconds your shovel breaks 3×3 blocks at once. Cooldown 4 min. | **Tunnel Bore**: for 8 seconds you break 1×3 blocks (rank V: 3×3). Cooldown 4 min. |
| Trade-off | −10% breaking speed on plain stone and deepslate | −15% breaking speed with a pickaxe | **No** chance of double drops (not even the trunk's), and ores give −25% XP |
| Example choice node | Gem Eye (diamond and emerald only, but a wider range) **or** Broad Spectrum (every ore, a smaller range) | Treasure Digger (more finds in dirt, sand and gravel) **or** Archaeologist (better finds when brushing) | Deep Delver (deepslate breaks as fast as stone) **or** Enduring (50% less wear during Tunnel Bore) |

**Excavator finds** are vanilla items only: flint, clay balls, bones, gold nuggets, pottery sherds and the like.

**Mastery perks** (example): +1% chance of a double ore **or** +1% breaking speed.

- ✅ **Double drops** stay, with Ancient Debris excluded.

**No new items needed.**

### Skill 5/10: Foraging (✅ approved in round 13)
**Identity:** the wild: felling trees and gathering what grows by itself.

**XP:**
- **Logs from natural trees**, per wood type from a table. Rare trees give more, such as jungle, cherry and pale oak.
- ✅ **Trees a player planted** give **25% XP**. A tree farm stays useful, but it is not the fastest route. Configurable.
- **Wild plants:** sweet berries from wild bushes, glow berries, natural mushrooms, flowers, jungle cocoa, wild melons and pumpkins, and honey and honeycomb from wild beehives.
- Placed blocks, and plants or trees grown with bonemeal, give nothing.

**Trunk:**
| Level | Bonus |
|---|---|
| 5 | Woodcutter's Rhythm: +3% chopping speed on logs |
| 10 | Leaf Shaker: 10% more saplings, apples and sticks from leaves |
| 15 | Forest Foot: berry bushes no longer prick you |
| 20 | Double Timber: a 5% chance of a double log |

**Specializations:**
| | **Lumberjack** | **Gatherer** | **Herbalist** |
|---|---|---|---|
| Playstyle | Felling trees | Gathering wild yields | Living off wild food |
| Active ability | **Tree Feller**: the next tree you hit within 6 seconds falls at once. Every log costs durability. Rank I: up to 16 logs, rank V: up to 64. Cooldown 4 min. ✅ Whole trees allowed. | **Bountiful Harvest**: for 10 seconds all wild plants give 3× yield. Cooldown 5 min. | **Wild Remedy**: for 30 seconds all food you eat gives double saturation, plus 5 seconds of Regeneration I. Cooldown 5 min. |
| Trade-off | Wild plants give −50% XP and no bonus yield | −15% chopping speed on logs | Cultivated food (bread, crops, meat from bred animals) fills 20% less |
| Example choice node | Wide Fell (Tree Feller takes bigger trees) **or** Thrifty (Tree Feller costs half the durability) | Rare Seeds (a chance of torchflower seeds or pitcher pods from grass) **or** Honey Harvest (double honey from wild hives, without angering the bees) | Mushroom Lore (mushroom stew gives Regeneration) **or** Berry Lore (berries fill +2 hunger) |

**Mastery perks** (example): +1% chance of a double log **or** +1% chance of a double wild yield.

**No new items needed.** Torchflower seeds and pitcher pods are vanilla items that normally only come from a sniffer.

### Skill 6/10: Farming (✅ approved in round 14)
**Identity:** growing and tending: crops, livestock and tamed animals.

**XP:**
- Harvesting **fully grown crops**: wheat, carrots, potatoes, beetroot, nether wart, melons and pumpkins (from stems), cocoa, and planted berry bushes.
  - Rare crops (torchflower, pitcher plant) give more.
  - Sugar cane: only the top blocks, not the bottom one.
  - An unripe crop gives nothing.
- ✅ Crops grown with **bonemeal** give **no XP** (round 14; the proposed 50% was rejected). Configurable.
- **Automatic harvesting** (pistons, water, villagers) gives nothing. Only what a player harvests counts.
- **Animals:**
  - **breeding**: XP per baby;
  - **shearing**;
  - **taming**: once per animal. Rarer animals give more.
- Milking and collecting eggs give no XP, because they can be repeated endlessly.
- ✅ **Damage your tamed animals deal** gives Farming XP, not Melee XP, under the same anti-farming rules as Melee.

**Trunk:**
| Level | Bonus |
|---|---|
| 5 | Light Step: you no longer trample farmland |
| 10 | Rich Harvest: a 5% chance of an extra crop |
| 15 | Growing Up: babies you bred grow up 10% faster |
| 20 | Farmhouse Fare: food made from crops fills +1 hunger |

**Specializations:**
| | **Grower** | **Herder** | **Beastmaster** |
|---|---|---|---|
| Playstyle | Crops | Livestock: breeding, wool, meat and leather | Tamed animals: wolves, cats, horses, camels and the like |
| Active ability | **Harvest Festival**: for 10 seconds crops give double yield and replant themselves. Cooldown 4 min. | **Herding Call**: for 10 seconds animals within 16 blocks follow you without food, and your next breeding gives twins. Cooldown 4 min. | **Rally the Pack**: for 15 seconds your tamed wolves within 24 blocks get Strength and Resistance and attack your target. Cooldown 5 min. |
| Trade-off | Breeding gives −50% XP and no bonuses | Crops give no extra yield, not even the trunk's | You deal −10% melee damage yourself while one of your tamed wolves is within 16 blocks |
| Example choice node | Ripening Aura (crops within 4 blocks of you grow 15% faster) **or** Seed Saver (you always replant by yourself) | Twins (a bigger chance of twins) **or** Wool Trade (double wool and leather) | Pack Leader (the bonus works for up to 3 wolves) **or** Rider (horses and camels you ride are 15% faster and jump higher) |

**Mastery perks** (example): +1% crop yield **or** +1% damage from your tamed animals.

**No new items needed.**

### Skill 7/10: Fishing (✅ approved in round 15)
**Identity:** on and by the water: fish, treasure, and the rod as a tool.

**XP:**
- **Catches:**
  - fish: cod, salmon, tropical fish, pufferfish;
  - treasure: enchanted books, bows, rods, name tags, saddles, nautilus shells;
  - junk gives little.
- **Against AFK fishing:**
  - you only get XP if you moved or looked around in the last few minutes;
  - ✅ after 10 catches within 3 blocks of the same spot the XP drops sharply ("overfished"), and it only recovers when you fish elsewhere. It does not drop to zero.
- ✅ Only fishing in **open water** (as vanilla already requires for treasure) gives XP. **A small pit gives no XP** (round 15).

**Trunk:**
| Level | Bonus |
|---|---|
| 5 | Patience: fish bite 5% sooner |
| 10 | Steady Line: a 10% chance that a catch costs the rod no durability |
| 15 | Fisher's Fare: cooked fish fills +1 hunger |
| 20 | Sea Luck: +5% treasure chance |

**Specializations:**
| | **Angler** | **Treasure Hunter** | **Hookmaster** |
|---|---|---|---|
| Playstyle | Lots of fish, caught fast | Fishing up treasure | The rod as a tool against mobs and players |
| Active ability | **Feeding Frenzy**: for 20 seconds fish bite almost at once (−80% wait). Cooldown 4 min. | **Lucky Cast**: your next catch within 30 seconds is guaranteed treasure. Cooldown 5 min. | **Reel In**: the next mob you hook is yanked to you and stunned for 2 seconds. Cooldown 4 min. |
| Trade-off | −50% treasure chance | Fish bite 25% slower | Catches (fish and treasure) give −30% XP |
| Example choice node | Ice Fishing (your rod breaks through ice) **or** Big Catch (fish fill +2 hunger) | Deep Sea (a small chance of a Heart of the Sea or a trident) **or** Bookworm (fished enchanted books have higher levels) | Shake (a hooked mob drops one of its normal drops, once per mob) **or** Drag Hook (you pull mobs and players twice as hard) |

**Mastery perks** (example): +1% treasure chance **or** fish bite 1% sooner.

- ✅ Treasure Hunter may fish up a Heart of the Sea or a trident with a small chance.

**No new items needed.** The Heart of the Sea and the trident are vanilla items that normally cannot be fished up.

### Skill 8/10: Exploration (✅ approved in round 16)
**Identity:** travelling, discovering and moving. mcMMO's Acrobatics is part of it.

**XP:**
- **New chunks:** your first visit to a chunk gives a little XP. It is tracked per player, so laps in a minecart earn nothing.
- **New biomes:** your first time in a biome gives a lot of XP.
- **New structures:** your first time inside a structure gives a lot of XP, such as a village, temple, stronghold, ancient city, trial chamber, bastion or end city.
- **Survived falls:** XP for the fall damage you took. The same spot stays blocked for a while, like mcMMO's anti-farming rule.
- **With an elytra** new chunks give **25% XP**, because you see so much so fast.
- **No XP for walking or swimming distance.** That is too easy to AFK-farm in a water current.
- ✅ **Revisiting chunks:** a chunk you have not visited for a long time gives XP again, which helps on servers with a world border (round 16).
  - 💡 Numbers: after **30 real days**, for **25%** of the XP of the first visit. Biomes and structures give XP only the first time.

**Trunk.** These bonuses do not work in heavy armor (3 or more pieces of iron or heavier):
| Level | Bonus |
|---|---|
| 5 | Light-Footed: 5% less fall damage |
| 10 | Stamina: sprinting costs 5% less hunger |
| 15 | Swimmer: +5% swimming speed |
| 20 | Roll: a 10% chance that a fall deals only half damage (landing while sneaking: 20%) |

**Specializations:**
| | **Wanderer** | **Survivalist** | **Acrobat** |
|---|---|---|---|
| Playstyle | Travelling fast and far | Enduring hunger and the environment | Falling, jumping and dodging |
| Active ability | **Stride**: for 10 seconds +30% speed and higher jumps. Cooldown 4 min. | **Endure**: for 20 seconds no hunger, and 75% less damage from the environment (fire, lava, freezing, drowning). Cooldown 5 min. | **Daredevil**: for 15 seconds no fall damage, and +50% jump height. Cooldown 4 min. |
| Trade-off | You take +10% damage from mobs | −10% sprint speed | You take +25% knockback |
| Example choice node | Step Up (you step up 1 block by yourself, like a horse) **or** Tireless (sprinting costs no extra hunger) | Fireproof (−50% damage from fire and lava) **or** Gills (you can stay underwater twice as long) | Dodge (a 10% chance to dodge a melee hit while sprinting) **or** Climber (climb ladders and vines 50% faster) |

**Mastery perks** (example): +1% movement speed **or** 1% less fall damage.

**Counteraction:**
- Opposing skill: **Defense**.
- Gear conflict: in heavy armor the movement bonuses do not work.

**Technical note (phase 8):** visited chunks are stored compactly per region (1 bit per chunk), so even a player who explores a lot has a small file. For revisits, each region also stores the date of the last visit.

**No new items needed.**

### Skill 9/10: Smithing (✅ approved in round 17)
**Identity:** metal and gear: smelting, repairing and improving at the furnace, the anvil and the smithing table.

**XP:**
- ✅ **Smelting metal** in a furnace or blast furnace: raw ores, ore blocks and Ancient Debris.
  - You get the XP when you **take the output out of the furnace yourself** (shift-click counts too). A hopper emptying the furnace gives nothing.
  - Stone, sand, glass and food give **no** Smithing XP, or an automatic stone factory would become an XP source.
- **Repairing at an anvil:** XP by material, like mcMMO's repair table. Netherite and diamond give the most.
- **Smithing table:** a netherite upgrade gives a lot of XP, an armor trim a medium amount.

**Trunk:**
| Level | Bonus |
|---|---|
| 5 | Fire Master: furnaces you fill smelt 5% faster |
| 10 | Thrifty Anvil: repairs cost 5% fewer XP levels |
| 15 | Tempered Steel: a repair restores 5% extra durability |
| 20 | Fuel Saver: a 10% chance that fuel is not consumed |

**Specializations:**
| | **Forgemaster** | **Armorer** | **Tinkerer** |
|---|---|---|---|
| Workstation | Furnace and blast furnace | Anvil | Smithing table |
| Playstyle | More metal from the same ore | Keeping gear alive | Improving gear |
| Active ability | **Flash Forge**: for 30 seconds furnaces you open smelt 4× faster. Cooldown 5 min. | ✅ **Masterwork**: your next repair within 30 seconds restores the item fully. There is a **25% chance** that it costs no XP levels; otherwise you pay as usual. Cooldown 5 min. (Weakened in round 17: "always free" was too strong.) | **Reforge**: the next item you upgrade or repair gets a permanent +10% maximum durability. Cooldown 5 min. |
| Trade-off | Repairs restore 15% less | Smelting gives no bonus yield and −25% XP | Anvil repairs restore 20% less |
| Example choice node | Second Smelt (a chance of double output) **or** Slow Burn (fuel lasts 50% longer) | ✅ Preservation (weakened in round 18: the "Too Expensive" limit goes from 40 to 50 levels instead of disappearing, and repairs never lose enchantments) **or** Quick Hammer (repairs restore +25%) | Template Saver (a 20% chance to keep a smithing template; **requires Mining 30**) **or** Trim Power (every armor trim gives a small effect, depending on its material) |

**Mastery perks** (example): +1% chance of double output when smelting **or** +1% extra restored on repairs.

- ✅ **Abilities at a workstation:** Smithing has no tool in hand. You activate its ability with `R` while looking at the workstation: furnace, anvil or smithing table. The same goes for Arcana.
- ✅ Tinkerer may give armor trims a small real effect.

**Counteraction:** opposing skill **Arcana** (steel against magic).

**No new items needed.** Maximum durability and trim effects work through vanilla item components.

### Skill 10/10: Arcana (✅ approved in round 18)
**Identity:** magic: brewing potions and enchanting gear.

**XP:**
- **Brewing:** you get XP when you **take finished potions out of the brewing stand yourself**. A hopper gives nothing, as with the furnace. The more complex the potion, the more XP:

  | Potion | XP |
  |---|---|
  | Base (awkward) | little |
  | With an effect | medium |
  | Extended or amplified | more |
  | Splash or lingering | the most |

- **Enchanting at an enchanting table:** XP for the levels spent and the level of the enchantment.
- **Combining enchanted books at an anvil:** Arcana XP. Plain repairs at the anvil stay Smithing.
- The grindstone gives no XP.

**Trunk:**
| Level | Bonus |
|---|---|
| 5 | Busy Stand: brewing stands you fill brew 5% faster |
| 10 | Lapis Saver: a 10% chance that enchanting costs 1 lapis less |
| 15 | Potent Brews: potions you brew last 5% longer |
| 20 | Thrifty Magic: a 10% chance that enchanting costs 1 level less |

**Specializations:**
| | **Alchemist** | **Enchanter** | **Hexer** |
|---|---|---|---|
| Workstation / item | Brewing stand | Enchanting table | Splash and lingering potions |
| Playstyle | Better and new potions | Better enchantments | Potions as weapons |
| Active ability | **Catalyst**: for 30 seconds brewing stands you open brew 10× faster. Cooldown 5 min. | **Arcane Insight**: at your next enchantment within 30 seconds you see **all** enchantments of each offer (vanilla shows only one), and you may reroll the offers once for free. Cooldown 5 min. | **Hex**: the next splash potion you throw within 15 seconds leaves a cloud twice as large that lasts twice as long. Cooldown 4 min. |
| Trade-off | Enchanting gives −50% XP and no bonuses | Brewing gives −50% XP and no bonuses | Potions you **drink yourself** last 25% shorter |
| Example choice node | Extended (potions last 25% longer) **or** Potent (a chance that a potion comes out one level stronger) | Frugal (enchanting costs 1 level less, at least 1) **or** Rich Choice (a better chance of the best enchantment in the offer) | Lingering Venom (negative effects you cause last 30% longer) **or** Wide Mist (+50% splash radius) |

**New potions for the Alchemist** (✅): recipes for vanilla effects that vanilla cannot brew, such as **Haste**, **Resistance** and **Absorption**. They stay ordinary vanilla potions with vanilla effects, so they are not new items. **Requires Foraging 30** (see the hard requirements).

**Mastery perks** (example): potions last 1% longer **or** +1% chance of a better enchantment offer.

**Abilities:** `R` while looking at the brewing stand or enchanting table. For Hexer: `R` with a splash or lingering potion in hand.

**Counteraction:** opposing skill **Smithing** (magic against steel).

**No new items needed.**

### Specialization conflicts (✅ approved in round 19)
Mechanism b: these specializations of **different** skills exclude each other. Whoever has one cannot choose the other. The GUI shows this up front, with the reason.

| Specialization | ✕ | Specialization | Why |
|---|---|---|---|
| Juggernaut (Defense) | ✕ | Acrobat (Exploration) | Heavy plate against somersaults |
| Brawler (Melee) | ✕ | Bulwark (Defense) | Bare fists against a shield |
| Sniper (Ranged) | ✕ | Wanderer (Exploration) | Lying still against always on the move |
| Herbalist (Foraging) | ✕ | Grower (Farming) | Wild against cultivated |
| Tinkerer (Smithing) | ✕ | Enchanter (Arcana) | Improving with steel against improving with magic |

### Hard requirements (D) (✅ approved in round 19)
| Node or ability | Requires | Why |
|---|---|---|
| Template Saver (Tinkerer, Smithing) | Mining 30 | ✅ approved in round 17 |
| Deep Sea (Treasure Hunter, Fishing) | Exploration 40 | Whoever knows the sea finds its treasure |
| New potions (Alchemist, Arcana) | Foraging 30 | Knowledge of wild herbs |
| Rider (Beastmaster, Farming) | Exploration 30 | Whoever travels a lot rides better |

### Hidden synergies (✅ approved in round 19)
- ✅ The list **may be in this public repo** (the owner chose this on purpose). Players who read the repo can know them in advance.
- 💡 Phase 9: the synergies live in a data file, so a server owner can change or replace them.
- Every synergy needs **both skills at level 30**. There are deliberately no synergies between opposing skills.

| Skills | Synergy | Effect |
|---|---|---|
| Melee + Defense | Counterstrike | After a successful shield block, your next hit within 2 seconds deals +20% |
| Melee + Exploration | Charge | Hits while sprinting deal +10% |
| Ranged + Exploration | High Ground | +10% damage on targets at least 8 blocks below you |
| Ranged + Arcana | Venom Tip | Tipped arrow effects last 50% longer |
| Mining + Smithing | Forge of the Deep | +5% chance of double output when smelting raw ore |
| Mining + Exploration | Deep Walker | Sculk sensors in the Deep Dark notice you 2 blocks later |
| Foraging + Farming | Green Thumb | Saplings you plant grow 20% faster |
| Fishing + Exploration | Seafarer | Boats you steer go 10% faster |
| Farming + Defense | Loyal Guard | Your wolves take 20% less damage while you block |
| Smithing + Defense | Tailored | Your armor wears 10% slower |
| Fishing + Arcana | Tidal Magic | Water Breathing potions last 50% longer |
| Foraging + Arcana | Herb Lore | Glow berries give 10 seconds of Night Vision |

General rules:
- The rune hint appears in the GUI once one of the two skills reaches the required level.
- A synergy is **discovered** the first time its situation happens in play while both levels are met.
- Nothing records who made or found an item, so items keep stacking normally.

---

## 3. XP generation
- The XP sources per skill are in §2.
- ✅ The exact values are set now, as **Appendix A**, tuned to the 400-hour pace, so they can be approved in one go (round 23).
- 💡 The scale: **one hour of focused play in a skill ≈ 10,000 XP**, before the budget factor, the opposing-skill penalty and diminishing returns. All values live in the config (§11).
- The anti-exploit rules: §12.

## 4. Level progression and formulas

### Confirmed
- ✅ **Scale: 0–100 per skill**, with a **Mastery layer** after completion (after level 100).
- ✅ **Total budget: hybrid.** Every level gets gently more expensive as your total rises, with a hard cap at the end.
- ✅ **Pace: MUCH slower** than the first example (100 hours to level 100, rejected).
- ✅ **Respec: option B.** Switching is possible, but it costs a large share of your progress in that skill plus a waiting time.

### Numbers (✅ approved in round 5)
**Pace (the first skill, without the budget factor):** `hours(L) = 400 × (L/100)^2.66`. The first levels come fast, so a new player feels the system at once. Then the curve gets steep.

| Level | 1 | 5 | 10 | 25 | 50 | 75 | 100 |
|---|---|---|---|---|---|---|---|
| Cumulative play time | seconds | ~8 min | ~1 hour | ~10 hours | ~63 hours | ~186 hours | ~400 hours |

The hours are later converted to XP per action (phase 7), based on how fast an average player performs that action.

**Hybrid budget:** XP required = `base(level) × (1 + (total/300)²)`, with a **hard cap of 400 total levels**. Mastery does not count toward the cap.

| Masters one after another (each 0 → 100) | 1st | 2nd | 3rd | 4th (cap) |
|---|---|---|---|---|
| Hours for that skill | ~425 | ~534 | ~731 | ~1017 |
| Cumulative | ~425 | ~959 | ~1690 | ~2707 |

**Mastery:** 10 Mastery ranks per skill, each ≈ 40 hours. At every rank you pick 1 of 2 small perks. Mastery is the first thing you lose on death (§7).

**Specializations:** the first at **level 25** (≈ 10 hours), the second at **level 75** (≈ 186 hours).

**Respec (B):** switching costs **20% of the total XP in that skill** (at level 100 you drop to ~92, at level 75 to ~69). Then you must wait **7 real days** before you can switch again. The new specialization starts at rank 1.

For reference, the play time of a single level (without the budget factor):

| From level | 10 | 25 | 50 | 75 | 90 | 99 |
|---|---|---|---|---|---|---|
| To the next level | ~15 min | ~1.1 hours | ~3.4 hours | ~6.7 hours | ~9 hours | ~10.5 hours |

- ✅ **No unlearning.** Whoever reaches the cap of 400 is stuck with their choices (round 8).
  - Only a death can lower levels. That is no usable trick to free up room: the loss falls on every skill (the highest the hardest) and you cannot choose where.

## 5. Skill unlocks
- ✅ Specializations unlock at **level 25** and **level 75**. Mastery starts after **level 100**.
- ✅ Within a specialization you buy nodes with points (§6).

## 6. Abilities and skill trees

### Confirmed
- ✅ Every skill has its own tree.
- ✅ Specializations always have trade-offs.
- ✅ **One active ability per specialization** (not per skill).
- ✅ **Fully passive skills are allowed**, as long as they are not too strong. A specialization of such a skill gets a stronger passive bonus with a trade-off instead of an active ability.
- ✅ **Specializations: option B.** The first at **level 25**, the second at **level 75**, out of **three** per skill.
  - ✅ Respec costs **20% of the total XP in that skill**, plus **7 real days** of waiting. The new specialization starts at rank 1.
- ✅ **Mastery:** 10 ranks per skill after level 100, each ≈ 40 hours. At every rank you pick 1 of 2 small perks.
- ✅ Active abilities are triggered with an **ability key** (`R` by default, rebindable) while holding the right tool, or while looking at the right workstation. Not with right-click, which clashes with placing blocks and blocking with a shield.

### Structure of every tree (✅ round 8)
- ✅ **Trunk and branches:**
  - Every skill has a **trunk** of shared passive bonuses. They unlock by themselves at levels 5, 10, 15 and 20.
  - At level 25 you choose a branch (your specialization).
- ✅ **Points:**
  - From level 25 you earn **1 skill point per level**, so 75 at level 100.
  - A node costs 1–3 points. A full branch costs more than 75 points, so you can never take everything.
  - From level 75 your second specialization shares the same pool of points.
- ✅ **Occasional choice nodes:** at a few places in a branch you choose A **or** B.
- ✅ **Ability ranks I–V** for a specialization's active ability, bought with points.
- ✅ **Redistributing points** within the same specialization costs **as much as a full respec**: 20% XP and 7 days.
- ✅ **Long cooldowns:** 3–5 minutes, with a strong and decisive effect.
- ✅ **Two active abilities** in one skill (after level 75): `R` for the first specialization, `Shift + R` for the second.
- ✅ **Points on level loss:** when you drop below a level, the most recently bought point is removed first. When you win the level back, that point returns to the same node by itself.

## 7. Death penalties

### Fixed principles (from the brief, not a choice)
- Death penalties are **always visible**. The GUI shows per skill what is "at stake".
- After a death a summary shows **exactly** what you lost, and that it is **permanent**.
- Progress is **never removed silently**.

### Design consequence of the slow pace
- Level 100 takes ~400 hours, and a single level around 75–99 takes 7–10 hours. Losing whole levels is heavy.

### Confirmed (rounds 6 and 7)
- ✅ **Loss layers (option B)**, in this order:
  - (1) progress toward the next Mastery rank
  - (2) XP within your current level (you never drop below the start of your level)
  - (3) whole Mastery ranks or whole levels, only on repeated deaths
- ✅ **Every skill** is hit, **your highest skill the hardest**:
  - Every skill loses `base × (that skill's level / highest level)`.
  - With a base of **50%** of the XP within the current level, your highest skill loses 50%, a skill at half that level 25%, and a skill at a quarter 12.5%.
  - Example: at level 90 (one level ≈ 9 hours) a death costs your highest skill at most ~4.5 hours, and on average ~2.25 hours.
- ✅ **Death pressure:**
  - Every death raises the pressure by 1.
  - Every hour of active play without dying lowers it by 1.

  | Death pressure at the moment of death | Penalty |
  |---|---|
  | 0 | Layers 1 + 2: base **50%** |
  | 1 | Layers 1 + 2: base **100%** |
  | 2 or more | As at 1, plus layer 3 in your highest skill |

- ✅ **Mastery ranks can be lost**, including the perk you picked at that rank.
  - 💡 For a skill with Mastery, layer 3 first takes **1 Mastery rank**. Only when there are no ranks left does it take **1 whole level**.
- ✅ **Dimensions** multiply the penalty: Overworld ×1, Nether ×1.5, End ×2, and with Extreme Dimensions the Seam ×2.5.
- ✅ **Dangerous dimensions give no extra XP.** The risk is higher, the reward is not.
- ✅ **Death fatigue:** after a death your passive bonuses work at 50% for 10 minutes.
  - ✅ **No timer on the HUD.** The remaining time is shown **in the GUI** (and in the summary after the death).
- ✅ **No vampirism.** In a PvP death the loss simply disappears; the killer gains nothing.
- ✅ **No shade** or recovery marker at the place of death.
- ✅ **Loss is always permanent.** There are no recovery mechanics. You only earn it back by earning XP again.
- ✅ **No milestone floors.** You can drop below level 25 or 75. Your specialization then becomes **unusable** until you win the level back.
  - 💡 The choice is kept: whoever wins the level back has the specialization again at once, without respec costs.
- ✅ **Extreme Dimensions: full stacking (option A).** When both mods run, both penalties apply in full.

### Suggestions (💡)
- **Administration** (phase 9): every penalty is configurable, with **presets** like Extreme Dimensions' `Preset` class. The owner's choices are the default.

### Open questions
- ❓ Does a **Collapse** in Extreme Dimensions (the Tether runs dry and you wake up at home) also count as a death for this mod? (ED integration)

## 8. Recovery mechanics
- ✅ **No recovery mechanics.** There is no shade, no challenge and no item to win back a loss. Loss is permanent (round 7).

## 9. GUI navigation

### Confirmed
- ✅ Opens with a hotkey **and** from the inventory screen.
- ✅ A custom client screen, no chest menu.
- ✅ No death fatigue timer on the HUD; it is shown in the GUI (§7).
- ✅ All in-game text is in English (round 22).

### Design (✅ approved in round 20)
- **Opening:** the inventory button sits next to ED's buttons, or right next to the recipe book without ED. The hotkey is `K` by default.
- **Style:** the same vanilla house style as Extreme Dimensions: panels like the tooltip box, the 16 chat colours, flat meters, and the colour-blind and reduced-flashing options.
  - Skill icons are vanilla items: sword, bow, shield, pickaxe, log, wheat, fishing rod, compass, anvil and enchanted book.
  - Accent colour per category: Combat red, Gathering green, Survival aqua, Crafting gold.
- **Screens:**
  1. **Character Sheet** (main screen):
     - your player figure and name;
     - your total against the cap (e.g. 212/400) and the budget factor;
     - your number of Mastery ranks and unlocked abilities;
     - your death pressure;
     - all 10 skills as tiles, grouped by category. Each tile shows level, XP bar, your specializations, and a warning when an opposing skill costs you XP.
  2. **Skill Screen** (click a tile):
     - a header with name, description, level, XP and the XP to the next level, and the milestones 25/75/100;
     - the **tree** in the middle: the trunk at the bottom and the three branches as columns;
     - a detail panel on the right for the selected node: cost, requirements, trade-off and conflicts;
     - abilities with their rank and cooldown, and the skill's statistics.
  3. **Mastery** (per skill, after level 100): the 10 ranks with the chosen perks.
  4. **Death Register:**
     - the number of deaths, the death pressure and when it drops;
     - how long death fatigue still lasts;
     - your death history: when, where, which dimension and what you lost;
     - **"At Stake"**: exactly what you would lose if you died **now**, per skill, with the dimension factor and the death pressure included.
  5. **Codex & Chronicle:** your discovered synergies (with date and place), runes with hints for the rest, and the server's first discoverers.
  6. ✅ **Leaderboard** (round 25): the total and every skill, with every player's levels.
  7. ✅ **Settings** (operators only, round 25): the configuration in the game, like Extreme Dimensions' settings screen.
- **Skill tree:** one fixed layout that fits on one screen, without scrolling.
  - Nodes have clear states: owned, buyable, out of reach, requirement missing, conflict, dormant.
- **HUD outside the GUI:** minimal.
  - A short notice on a level-up or a discovered synergy.
  - A small icon when an ability is ready.
  - No XP bar on screen.
- **After a death:** after respawning a screen shows exactly what you lost (a fixed principle from §7).

### Mockup (round 21, ✅ approved)
- A clickable mockup of the Character Sheet, the Skill Screen (Melee) and the Death Register: https://claude.ai/artifact/UQ5xsBnWqyvD7ShrWcudv8 (private; only its owner can open it unless they share it).
- The example player has 212/400 levels, death pressure 1, and Blade, Bulwark and Prospector as specializations. All numbers follow the approved formulas.
- **Node names and effects** in the tree that are not approved above (e.g. Swordmaster, Spear Reach, Armor Breaker) are **examples** (💡). The real content of every tree is worked out per skill during implementation.

## 10. Player data (💡 proposal, round 25)

### Where it lives
| Data | Storage | Why |
|---|---|---|
| A player's progression | One Gson JSON file per player: `<world>/extremeprogression/players/<uuid>.json` | Easy to inspect and back up; one busy player never rewrites everyone's data. Same format as Extreme Dimensions. |
| Server-wide data (chronicle, first discoverers) | `<world>/extremeprogression/world.json` | One small file per world. |
| Placed blocks | A Fabric data attachment on each chunk: a bitset per chunk section, only where something was placed | Saved and loaded with the chunk itself, so it never runs ahead of or behind the blocks. |
| Mob origin flags (spawner, spawn egg, `/summon`) | A Fabric data attachment on the entity | Survives restarts and unloaded chunks. |
| Anti-exploit state (AFK timer, diminishing-returns window, PvP caps, fall spots) | Memory only | Short-lived; nothing is lost that matters when the server restarts. |

### What a player file holds
- Per skill: total XP, level, Mastery ranks and the perk chosen at each rank.
- Specializations (first, second, and dormant ones after level loss).
- The nodes bought, **in the order they were bought** (needed for "the most recently bought point goes first", §6).
- Cooldowns: ability cooldowns (game ticks) and the respec waiting time (real time).
- Death pressure and its decay timer (counted in play time), the death fatigue end time.
- Death history: the last 50 deaths, with time, place, dimension, cause and exactly what was lost.
- Discovered synergies with date and place.
- Statistics per skill (e.g. full hits, ores mined, catches).
- Exploration: visited chunks as a bitset per region and dimension, the last visit per region, visited biomes and entered structures.

### Saving safely
- Changes are marked dirty and written **with the world's autosave**, on logout and on server stop, as in Extreme Dimensions. No write on every XP gain.
- Writes are atomic (a temporary file, then a move), so a crash never leaves a half-written file.
- The last **3 versions** of every player file are kept as backups.
- Every file has a **schema version**. On an update the mod migrates old files forward; it never resets progression. Migration tests load files written by older versions (like `MigrationTests` in Extreme Dimensions).

## 11. Configuration and administration (💡 proposal, round 25)

### Confirmed (round 25)
- ✅ The configuration, presets and commands below.
- ✅ An **in-game settings screen** for operators, like Extreme Dimensions' `SettingsScreen`, next to the config file and `/skills reload`.
- ✅ **XP events**: an admin can raise the XP rate for a while, globally or per skill, like mcMMO's `/xprate`.
  - 💡 `/skills xprate <multiplier> [skill] [minutes]`, announced in chat. They end by themselves, on `/skills xprate reset`, or on a restart.
- ✅ A **leaderboard page** in the GUI, next to `/skills top`. Players may see each other's levels.
- ✅ The **Normal** preset as a milder alternative.

### Configuration
- **`config/extremeprogression.json`** for every balance value, as in Extreme Dimensions: missing keys are written back, so the file always lists every option.
- **Data files** for the larger tables: `config/extremeprogression/xp/<skill>.json` (Appendix A) and `config/extremeprogression/synergies.json`.
- **Presets**, like Extreme Dimensions' `Preset`:
  - **Hardcore** (the default): the owner's choices in this document.
  - **Normal**: half the death loss, no layer 3, 50% faster XP.
  - **Custom**: whatever the file says.
- Everything the brief lists is configurable: enabled skills, maximum levels, XP multipliers and values, ability requirements and cooldowns, death penalties, anti-farming rules and skill dependencies. (There are no recovery mechanics to configure.)

### Commands
| Command | Who | What |
|---|---|---|
| `/skills` | Everyone | Opens the Character Sheet |
| `/skills top [skill]` | Everyone | The leaderboard (total or per skill) |
| `/skills inspect <player>` | Operators | Opens that player's Character Sheet, read only |
| `/skills admin set <player> <skill> <level>` | Operators | Sets a level |
| `/skills admin add <player> <skill> <xp>` | Operators | Adds or removes XP |
| `/skills admin respec <player> <skill>` | Operators | A free respec |
| `/skills admin reset <player> [skill]` | Admins | Resets progression, only after a confirmation |
| `/skills xprate <multiplier> [skill] [minutes]` | Admins | Starts an XP event; `reset` ends it |
| `/skills reload` | Admins | Reloads the config |

- Permissions follow Extreme Dimensions: operators are op level 2 (`LEVEL_GAMEMASTERS`), admins op level 3 (`LEVEL_ADMINS`).
- **Admin changes are never silent:** the player gets a message, and the change is written to the server log.

## 12. Anti-exploit
- 💡 All XP is computed on the server; the client only displays it (follows from option A).
- Rules already approved per skill (§2):
  - **Combat:** only fully charged hits (Melee) or fully drawn bows (Ranged) count. Damage counts at most up to the target's remaining health. No XP from passive animals, spawner mobs or spawn-egg mobs.
  - **Defense:** XP only if you attacked or moved recently, and at most 6 times per attacker per 60 seconds.
  - **Blocks:** placed blocks, cobblestone generators and bonemeal-grown crops, trees and plants give no XP. Player-planted trees give 25%.
  - **Automation:** pistons, water, villagers and hoppers give no XP. Only what a player does by hand counts.
  - **Fishing:** open water only, an "overfished" decline after 10 catches in one spot, and a movement check.
  - **Exploration:** new chunks per player, 25% with an elytra, no walking or swimming XP, falls blocked per spot.
- A useful side effect of the damage rule: in a mob farm where mobs fall to 1 heart and the player finishes them, the player only earns XP for that last bit of damage. No separate farm rule is needed.

### Confirmed (round 23)
- ✅ **Trial spawners** give **full XP**. Trial chambers are designed challenges, and vanilla already puts a trial spawner on a 30-minute cooldown after it is beaten. Plain spawners and spawn eggs stay at 0 XP.
- ✅ **Diminishing returns** as a general brake, set so high that normal active play never reaches it; only farms and macros do.
  - 💡 Numbers: per skill, over a rolling 10-minute window. Above **5,000 XP** in 10 minutes (3× the normal pace), XP shrinks step by step down to a floor of **5%**. The window and threshold are configurable.
- ✅ **A general AFK rule:** **no XP** in any skill after 5 minutes without real play (no looking around, no movement of your own, no interaction).
  - 💡 Movement caused by water currents, pistons, minecarts or boats does not count as play.
- ✅ **PvP** (a and b; confirmed in round 24):
  - **a)** damaging the same player gives XP for at most 3 "lives" per hour (60 health points of damage);
  - **b)** no XP from players with less than half of your total level.
  - 💡 **c)** no XP from players on the same IP address: **off by default** (it would also hit siblings and housemates), configurable for servers that want it.

### Further rules (💡)
- **Explosions** (TNT, creepers, beds, respawn anchors) give no Mining or Foraging XP.
- **Creative and Spectator** mode give no XP.
- Mobs from **`/summon`**, spawn eggs (also from a dispenser) and plain spawners are flagged and give 0 XP. The flag is stored on the entity, so it survives restarts and unloaded chunks.

## 13. Implementation roadmap (💡 proposal)

Work starts only after the owner's final approval (§15). Each milestone ends with: compiling, running the tests, checking for regressions, a report of what was built and what is still open, and the owner's approval before the next major system.

Progress: milestone 0 ✅ done (version 0.1.0) · milestone 1 ✅ done (version 0.2.0) · milestone 2 next.

| # | Milestone | Contents | Tests |
|---|---|---|---|
| 0 | Project skeleton | Gradle and Loom as in Extreme Dimensions (MC 26.3, Java 25, split source sets), `fabric.mod.json`, CI with build, server game tests and a client screenshot test. The empty mod loads on server and client. | CI green |
| 1 | Core progression | Skill definitions, the XP curve and budget, the player data model with saving (atomic, backups, schema version), syncing to the client, `/skills admin set/add` | Unit tests for every formula; a migration test |
| 2 | GUI foundation | The `Ui` house style, Character Sheet, Skill Screen (read only), inventory button, the `K` hotkey | Client screenshots |
| 3 | XP: gathering | Mining, Foraging, Farming, Fishing; placed-block tracking, bonemeal and automation rules, open water and "overfished", diminishing returns, the AFK rule | Game tests per exploit rule |
| 4 | XP: the rest | Melee, Ranged, Defense, Exploration, Smithing, Arcana; mob origin flags, PvP limits, chunk, biome and structure tracking | Game tests |
| 5 | Trees and abilities | Points, buying nodes, choice nodes, the `R` / `Shift + R` keys, cooldowns, respec; all 10 trunks | Unit and game tests |
| 6 | Specializations | All 30 specializations: abilities, nodes and trade-offs, in three parts (Combat; Gathering; Survival and Crafting) | Game tests per ability |
| 7 | Death system | Loss layers, distribution, death pressure, dimension multipliers, death fatigue, points on level loss, the Death Register, the summary after death, "At Stake" | Edge-case tests (see below) |
| 8 | Connections | Mastery, synergies (discovery, Codex & Chronicle), opposing skills, specialization conflicts, hard requirements, gear conflicts | Unit and game tests |
| 9 | Administration | Presets, data files, the settings screen, leaderboard, XP events, `inspect`, `reload` | Game tests |
| 10 | Release | Extreme Dimensions integration, a balancing pass, the Modrinth page and icon, the first release | Full CI incl. compatibility run |

**Edge cases the death tests must cover:** a death at level 0; a death exactly at the start of a level; death pressure 2 with Mastery ranks (a rank goes first); a skill dropping below 25 or 75 (the specialization goes dormant and comes back without cost); points removed in the right order; a death in the Seam without ED loaded (no multiplier); two deaths within the same tick; the server stopping right after a death (the loss is saved, never applied twice).

## 14. Code structure (💡 proposal)
One component per responsibility, never one giant class. Package `com.extremeprogression`:

| Package | Responsibility |
|---|---|
| `skill` | Definitions of skills, specializations, nodes and abilities (data-driven) |
| `progression` | The XP curve, budget, levels, Mastery, opposing skills |
| `xp` | The XP sources per skill and the central XP service (multipliers, events) |
| `antiexploit` | Placed blocks, mob origins, AFK, diminishing returns, PvP limits |
| `ability` | Activation, cooldowns, the abilities themselves |
| `tree` | Points, buying nodes, choice nodes, respec |
| `death` | Loss layers, death pressure, fatigue, the death history |
| `synergy` | Synergies, discovery, the chronicle |
| `data` | Player and world storage, backups, migrations |
| `config` | The config, presets, data files |
| `command` | `/skills` and its subcommands |
| `network` | Packets between server and client |
| `compat.extremedimensions` | The optional integration (§1) |
| `client.ui`, `client.screen`, `client.hud`, `client.inventory`, `client.state` | The house style, the seven screens, the HUD notices, the inventory button, the client's copy of the progression |

## 15. Final approval
Approving this document approves:
- every ✅ decision;
- every 💡 suggestion in it, unless the owner names one to change. Example node names, choice nodes and Mastery perks stay examples: their final content is worked out per skill during milestone 6 and shown to the owner then;
- the roadmap in §13.

Still open:
1. ❓ Does a **Collapse** in Extreme Dimensions count as a death for this mod?
2. ❓ The owner answered round 25 with a single "approved". It was read as: name Extreme Progression, yes to the settings screen, yes to XP events, yes to the leaderboard page (players see each other's levels), yes to the Normal preset. Correct?

---

## Appendix A: XP tables (✅ approved in round 24)

All values are configurable (§11). They are calibrated against an assumed rate of active play, given per skill below. Internally XP is a decimal number; the GUI rounds it.

### A.1 Scale
- **1 hour of focused play in a skill ≈ 10,000 XP.**
- XP for level L → L+1 = `10,000 × (hours(L+1) − hours(L)) × budget factor`, with `hours(L) = 400 × (L/100)^2.66` (§4).
- **A Mastery rank costs 400,000 XP** (≈ 40 hours), with no budget factor, because Mastery does not count toward the cap.

| Level | 1 | 5 | 10 | 25 | 50 | 75 | 100 |
|---|---|---|---|---|---|---|---|
| XP for that one level | 19 | 864 (5→6) | 2,525 (10→11) | 11,011 (25→26) | 34,230 (50→51) | 66,732 (75→76) | 105,519 (99→100) |
| Cumulative XP | 19 | 1,385 | 8,751 | 100,134 | 632,878 | 1,860,900 | 4,000,000 |

(Without the budget factor. With a total of 212 levels, for example, the factor is ×1.50.)

### A.2 Melee and Ranged
- **XP = damage dealt × danger factor × 4.** Damage counts at most up to the target's remaining health. Ranged multiplies by its distance bonus as well.
- Calibration: an active fighter deals ≈ 2,500 damage to hostile mobs per hour, at an average factor of ≈ 1.0–1.2.

| Danger factor | Mobs |
|---|---|
| 0 | Passive animals; mobs from spawners, spawn eggs or `/summon` |
| 0.5 | Silverfish, endermite |
| 0.6 | Slime, magma cube (they split) |
| 0.8 | Zombified piglin (when angry) |
| 1.0 | Zombie, husk, drowned, spider, vex, iron golem (when hostile to you) |
| 1.2 | Skeleton, stray, bogged, piglin |
| 1.3 | Cave spider, pillager |
| 1.4 | Phantom |
| 1.5 | Creeper, enderman, witch, guardian, creaking; **players (PvP)** |
| 1.6 | Vindicator, blaze, ghast, hoglin, zoglin, shulker |
| 1.8 | Breeze |
| 2.0 | Wither skeleton, evoker, ravager |
| 2.2 | Piglin brute |
| 3.0 | Warden, elder guardian, Wither, Ender Dragon |

### A.3 Defense
- **XP = damage your shield blocks or your armor absorbs × the attacker's danger factor × 15.**
- Calibration: a front-liner absorbs ≈ 600 damage per hour.
- The cap of 6 hits per attacker per 60 seconds applies (§2).

### A.4 Mining
Calibration: ≈ 1,200 blocks per hour, of which ≈ 60 are ores.

| Block | XP | Block | XP |
|---|---:|---|---:|
| Stone, andesite, diorite, granite, tuff | 3 | Deepslate, cobbled deepslate | 5 |
| Netherrack (breaks very fast) | 1 | End stone, basalt, blackstone | 4 |
| Calcite, dripstone, magma block | 4 | Sandstone, terracotta | 3 / 5 |
| Obsidian | 20 | Crying obsidian | 30 |
| Coal ore / deepslate | 40 / 70 | Copper ore / deepslate | 60 / 90 |
| Iron ore / deepslate | 90 / 130 | Gold ore / deepslate | 130 / 190 |
| Redstone ore / deepslate | 60 / 90 | Lapis ore / deepslate | 80 / 120 |
| Diamond ore / deepslate | 600 / 900 | Emerald ore / deepslate | 500 / 700 |
| Nether quartz ore | 30 | Nether gold ore | 40 |
| Ancient Debris | 1,500 | Amethyst block | 10 |

**Shovel** (≈ 1,800 blocks per hour):

| Block | XP | Block | XP |
|---|---:|---|---:|
| Dirt, grass block, sand, gravel | 2 | Clay | 6 |
| Mud, soul sand, soul soil | 3 | Snow | 1 |
| **Brushing** suspicious sand or gravel | 250 | | |

### A.5 Foraging
Calibration: ≈ 500 logs per hour.

| Log (natural tree) | XP | Log | XP |
|---|---:|---|---:|
| Oak | 16 | Birch, spruce | 18 |
| Dark oak, acacia | 20 | Jungle, mangrove | 22 |
| Cherry, crimson stem, warped stem | 24 | Pale oak | 26 |

Player-planted trees give 25% (§2); bonemeal-grown trees give 0.

| Wild plant | XP | Wild plant | XP |
|---|---:|---|---:|
| Sweet berries (wild bush, per harvest) | 15 | Glow berries (wild vine) | 15 |
| Natural mushroom | 10 | Flower | 6 (rare flowers 10) |
| Cocoa (natural jungle) | 12 | Wild melon or pumpkin | 30 |
| Honey bottle or honeycomb from a wild hive | 40 | | |

### A.6 Farming
Calibration: ≈ 1,000 harvested crops per hour.

| Crop (fully grown, harvested by hand) | XP | Crop | XP |
|---|---:|---|---:|
| Wheat, carrot, potato | 8 | Beetroot | 9 |
| Nether wart | 10 | Melon, pumpkin (from a stem) | 10 |
| Cocoa (planted) | 9 | Sweet berries (planted bush) | 6 |
| Sugar cane, cactus (top blocks only) | 4 / 3 | Torchflower | 60 |
| Pitcher plant | 80 | | |

| Animals | XP |
|---|---:|
| Breeding, per baby | 40 |
| Shearing a sheep | 15 |
| Taming: wolf | 200 |
| Taming: cat, fox | 250 / 300 |
| Taming: horse, donkey, mule, llama | 300 |
| Taming: parrot | 350 |
| Taming: camel | 400 |
| **Damage dealt by your tamed animals** | damage × danger factor × 3 |

### A.7 Fishing
Calibration: ≈ 120 catches per hour (with Lure).

| Catch | XP | Catch | XP |
|---|---:|---|---:|
| Cod | 60 | Salmon | 80 |
| Pufferfish | 100 | Tropical fish | 160 |
| Junk | 15 | Bow, fishing rod | 250 |
| Name tag, saddle, nautilus shell | 300 | Enchanted book | 400 |
| Heart of the Sea, trident (Treasure Hunter) | 1,000 | | |

- ✅ **Hookmaster** also earns XP for using the rod as a tool: 30 XP when Shake makes a hooked mob drop something (once per mob).

### A.8 Exploration
Calibration: ≈ 600 new chunks per hour on foot, plus structures and the first visits to biomes.

| Source | XP |
|---|---:|
| New chunk (on foot, on horseback, by boat) | 8 |
| New chunk with an elytra | 2 |
| A chunk revisited after 30 real days | 2 |
| New biome (first time) | 500 |
| Survived fall | 40 per damage point (a 10-damage fall = 400) |

| New structure (each one, the first time you enter it) | XP |
|---|---:|
| Ruined portal | 100 |
| Mineshaft, shipwreck, ocean ruin | 150–200 |
| Igloo, village, trail ruins | 250–300 |
| Desert temple, jungle temple, pillager outpost | 400 |
| Nether fortress | 800 |
| Trial chamber, end city | 1,000 |
| Ocean monument, bastion | 1,200 |
| Stronghold, woodland mansion | 1,500 |
| Ancient city | 2,000 |

- ✅ The **first structure of each type** you ever enter gives **3×** XP.

### A.9 Smithing
Smithing is limited by materials more than by time; its values are higher per action.

| Smelting (taken out by hand, per item) | XP | | XP |
|---|---:|---|---:|
| Raw copper / copper ore | 15 | Raw iron / iron ore | 25 |
| Raw gold / gold ore | 30 | Ancient Debris | 300 |
| Raw ore blocks | 9× the raw ore | Stone, sand, glass, food | 0 |

- **Anvil repair:** `200 × material factor × the share of durability restored`. Material factor: wood 0.5 · stone, leather 0.8 · chain, copper, gold 1.0 · iron 1.5 · diamond 3 · netherite 4. A diamond pickaxe repaired from broken to full gives 600.
- **Smithing table:** netherite upgrade 1,500 per item · armor trim 300 per item.

### A.10 Arcana
Calibration: ≈ 200 potions per hour for an active brewer.

| Potion (taken out by hand) | XP |
|---|---:|
| Awkward, mundane, thick | 5 |
| With an effect | 30 |
| Extended (redstone) or amplified (glowstone) | 45 |
| Splash | 60 |
| Lingering | 80 |
| New Alchemist potions (Haste, Resistance, Absorption) | 60 |

- **Enchanting table:** `15 × the level the offer requires` (a level-30 offer = 450).
- **Combining enchantments at the anvil:** `25 × the level cost`.
- The grindstone gives 0.
