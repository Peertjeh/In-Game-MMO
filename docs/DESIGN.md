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
| 27 | Skill 11: Trading, in a new category Other (after 1.4) | ✅ approved ("Akkoord"), read as yes to the two duo synergies too |

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
  - 1.2.0: there it reached past the inventory's edge, so it now sits inside the inventory's own panel. It is in the
    empty space between the player's figure and the crafting grid, above the off-hand slot, clear of the recipe book's
    button and ED's.
- 💡 The Seam's structures (villages, looms, colossal buildings, the Return Gate) count as **new structures** for Exploration.
  - Milestone 10: the Seam's buildings are not world structures. Extreme Dimensions builds them from its own plans
    while you are there, and the Seam is wiped and grown anew, so a structure lookup cannot see them.
  - What counts instead: every new chunk of the Seam and its biomes, like any other dimension's (once each, also after
    a wipe). A real structure count would need an API of Extreme Dimensions; it stays open.
- ❓ Does a **Collapse** (the Tether runs dry and the player wakes up at home) count as a death for this mod? (§7)

Without Extreme Dimensions none of this code is loaded.

**How milestone 10 implements it (version 1.0.0)**
- The Seam's factor (×2.5) counts only while Extreme Dimensions is loaded, under the id of its own `SeamService.SEAM`.
  The compatibility run checks that the id matches.
- Both mods' death penalties apply in full: neither touches the other's.
- The Character Sheet button sits right of ED's Journal and Curses buttons. The client test checks that it never lies
  over another button.
- **The compatibility run** (CI, on every push) runs every server game test and the client test with Extreme
  Dimensions and the newest Sodium, Iris and Mod Menu for this game version.
  - Extreme Dimensions comes from its latest GitHub release, or is built from its source when it has none.
  - Sodium, Iris and Mod Menu come from Modrinth.
- **The release** (CI): a version not released yet goes out as a GitHub release once the build, the client test and
  the compatibility run pass on `develop` or the default branch. Its notes are that version's part of the changelog.
  - With a `MODRINTH_TOKEN` secret and a `MODRINTH_PROJECT` variable in the repository's settings, the same jar goes
    to Modrinth, with Fabric API as a required dependency.
- The balancing pass: [BALANCE.md](BALANCE.md). The pace, Mastery, respec and death examples of §4 and §7 match the
  code, and `BalanceTests` guards them. The observations there are for the owner to decide; nothing was changed.
- The license is MIT. The icon is drawn by `tools/icon/make_icon.py`, and the Modrinth page text is in
  `docs/modrinth/description.md`.

**TAB and Styled Chat (version 1.1.0)**, as Extreme Dimensions and BattleHub have them on the same server:
- `%extremeprogression:<name>%` placeholders go through Text Placeholder API, which TAB and Styled Chat read on Fabric.
  - The values are worked out on the server's thread once a second and kept as a snapshot, because TAB asks from a
    thread of its own.
  - Compile-time stand-ins for placeholder-api's classes (from Extreme Dimensions) are left out of the jar. Without
    placeholder-api nothing of it is ever loaded.
- The placeholders cover each player and the server:
  - per player: the total level, Mastery, the best skill, every skill's level and Mastery ranks, the specializations,
    the death pressure, deaths, death fatigue, the synergies found and a level badge;
  - per server: the preset, the cap, a running XP event, the server's first discoveries and the best player.
- The shared configs of that server (`server/config/`, TAB and Styled Chat for all three mods) carry Extreme
  Progression's lines. The list of placeholders is in `server/README.md`.
- 💡 The level badge: `✦<total>` in a colour that brightens every hundred levels, with a gold star once the player has
  Mastery.
- The compatibility run includes the real placeholder-api and reads the placeholders back through it, as TAB does.

**The power level above heads (version 1.2.0)**
- Beside every player's name tag in multiplayer, the same badge as in the tablist (`✦312`, a gold star with Mastery).
  It is drawn by each client from every online player's total level and Mastery ranks.
- The server sends those totals whenever one changes (it checks twice a second) and to every player who joins.
- 💡 The badge is not shown in BattleHub's lobby and matches (`nametag_hidden_dimensions`, a list of dimension ids),
  because BattleHub draws its own name tags there. `nametag_power: false` turns it off everywhere.
- It only shows where the game shows a name tag at all: never for invisible players, never through walls for sneaking
  ones.

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

### Skill 11: Trading (✅ approved in round 27, version 1.5.0)
The owner asked for an "Other" section on the Character Sheet, with trading with villagers as the example. Trading is
its first skill: nothing in the mod rewards trading yet. Category **Other** (light purple); icon: an emerald.

**Identity:** dealing with villagers, wandering traders and piglins, and defending villages.

**XP** (calibration: an active trader makes ≈ 120 trades an hour, ≈ 10,000 XP):

| Source | XP |
|---|---:|
| A trade with a villager, by the offer's tier: novice · apprentice · journeyman · expert · master | 30 · 45 · 65 · 90 · 120 |
| … plus for every emerald paid or received in it | +1.5 |
| A trade with a wandering trader | ×1.5 (counted as journeyman) |
| A villager reaching apprentice · journeyman · expert · master through your trade | 150 · 300 · 500 · 800 |
| A gold ingot a piglin barters that you gave it (thrown or handed over) | 20 |
| A raid won (Hero of the Village), × the raid's omen level (1–5) | 1,000 |

- **The same offer**: of one villager's offer, the first 4 trades after each restock count fully, the rest 25%. A
  trading hall is fine; buying and selling the same goods in a loop is not the fast way.
- **Nothing** for villagers, wandering traders and piglins from spawn eggs, spawners or commands (as for mobs, §12),
  in Creative, or while AFK. Diminishing returns (§12) as for every skill.
- Prices count what you really paid (after a cured villager's discount, Hero of the Village, this skill's own).

**Trunk:**
| Level | Bonus |
|---|---|
| 5 | Fair Price: villagers' prices 5% lower (rounded to the nearest item) |
| 10 | Good Word: villagers gain 10% more experience from your trades (they level up sooner) |
| 15 | Barterer: a piglin's barter for your gold has a 10% chance of one more roll |
| 20 | Well Stocked: a 5% chance a trade does not use up the offer |

**Specializations** (cooldowns 5 min; the ability is used looking at a villager, or for Gold Tongue holding gold or
looking at a piglin):
| | **Merchant** | **Quartermaster** | **Gold Tongue** |
|---|---|---|---|
| Playstyle | The best prices | Stock and villager careers | Piglins and wandering traders |
| Active ability | **Haggle**: for 30 seconds the trades you open with villagers cost 20% less | **Restock**: the villager you look at restocks all its offers | **Gilded Offer**: for 20 seconds piglins barter your gold at once and roll twice |
| Trade-off | Villagers gain 20% less experience from your trades | Wandering traders and piglins give you 25% less XP | Villagers' prices 10% higher |

| Specialization | Ability ranks (after I) | P1 · P2 (2 points a rank) | P3 · P4 (3 points a rank) | Choice (3 points a rank) |
|---|---|---|---|---|
| **Merchant** | +5% off (V: 40%) | Thrifty: villagers' prices −2% · Bulk Buyer: offers costing 16 or more items −3% | Regular: a villager you traded with today −2% · Cashback: 3% chance a trade gives 1 emerald back | Bookworm: librarians −4% **or** Armorer's Friend: armorers, toolsmiths and weaponsmiths −4% |
| **Quartermaster** | +1 more villager within 8 blocks (V: 5) | Stockpile: 4% chance a trade does not use up the offer · Mentor: villagers +5% experience from your trades | Extra Crate: 3% chance a trade gives one more of its (stackable) result · Fresh Start: 20% chance a restock also undoes the prices' rise from demand (always at V) | Career Boost: a villager's level-up XP +10% **or** Second Shift: Restock's cooldown −20 s |
| **Gold Tongue** | +4 s (V: 36 s) | Gold Fever: piglins admire your gold 10% shorter · Wanderer's Deal: wandering traders −4% | Treasure Taste: 3% chance a barter rolls once more · Long Stay: wandering traders within 32 blocks stay 10% longer | Golden Calm: after a barter, piglins leave you alone 10 s without gold armor **or** Fool's Gold: 4% chance a barter gives the ingot back |

All numbers are per rank, as for every specialization (§6).

**Mastery perks:** Patron (villagers' prices 1% lower a rank) **or** Golden Touch (a 1% chance a rank that a barter or
a wandering trader's trade gives its result twice).

**Counteraction:** no opposing skill (like Mining or Fishing); the prices of the specializations are its
counterweight. No specialization conflicts.

**Two duo synergies** (§16), with a party member within 32 blocks:
- **Caravan** (Trading + Exploration): wandering traders cost 10% less.
- **Market Day** (Trading + Farming): farmers and butchers cost 10% less.

**On the Character Sheet:** Other goes in the left column under Survival, Trading beside Arcana, both under one
heading ("SURVIVAL · OTHER"), so every tile stays in line with the column beside it. Everything else that lists skills (the leaderboard, TAB, settings, data files) takes it as it
takes the others. The total cap stays 400: one more skill means more choice, not more levels.

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

### How milestone 8 implements it (version 0.9.0)
The connections between skills, as built. Everything marked 💡 was decided while building.

**Opposing skills (mechanism c)**
- A skill loses `60% × min(1, gap / 50)` of every XP gain. This applies once its opposing skill is above level 25 and
  higher than it.
  - Example: Melee 47 against Ranged 22 is a gap of 25, so Ranged earns 30% less.
  - Operators' changes are not affected.
- Meanwhile the weaker skill's passive bonuses work at 75%. That covers its trunk, its passives and choice nodes, and its
  Mastery perks.
  - 💡 Together with death fatigue the two multiply: 75% × 50% = 37.5%.
  - 💡 A specialization's price and its ability's own rank are not affected.
- Always visible:
  - the skill's tile on the Character Sheet shows the penalty in red (−30%), and its tooltip says why;
  - the Skill Screen's header shows "Melee 47: −30% XP · passives 75%" in place of the category line.

**Mastery perks** (💡 named and settled here; the examples of the skill sections above)
- Every rank after level 100 gets one of the skill's two perks, chosen on the new **Mastery** screen (a button on the
  Skill Screen).
- Each rank put in a perk adds 1% to it.
- 💡 Ranks are chosen in order, and a choice is final while you hold the rank.
- A lost rank takes its perk with it (§7). Winning the rank back asks for a new choice.
- 💡 The perks follow the skill's power (death fatigue, an opposing skill). Exploration's perks, like its other bonuses,
  do not work in heavy armor.

| Skill | Perk "a" (+1% a rank) | Perk "b" (+1% a rank) |
|---|---|---|
| Melee | Giant Slayer: melee damage against bosses | Duelist: melee damage against players |
| Ranged | Skyshot: ranged damage against flying mobs | Marksman: ranged damage against players |
| Defense | Titan Guard: less damage from bosses | Duel Guard: less damage from players |
| Mining | Mother Lode: chance of a double ore | Swift Pick: breaking speed with a pickaxe or shovel |
| Foraging | Heartwood: chance of a double log | Wild Bounty: chance of a double wild yield |
| Farming | Golden Harvest: chance of an extra crop | Kindred: your tamed animals' damage |
| Fishing | Sea's Favor: chance that a fish is treasure | Eager Fish: fish bite sooner |
| Exploration | Swift Feet: movement speed | Soft Landing: less fall damage |
| Smithing | Rich Smelt: chance of a double smelting output | Fine Mend: anvil repairs restore more |
| Arcana | Long Brew: potions you brew last longer | Bright Offer: chance an enchanting offer rolls 1 level higher |

- 💡 Bosses are the Ender Dragon, the Wither, the Warden, the Elder Guardian, and any other mod's mob in the common
  `c:bosses` tag.
- 💡 Flying mobs are the phantom, ghast, happy ghast, blaze, vex, breeze, bee, bat, parrot, allay, Ender Dragon and
  Wither.

**Synergies**
- A synergy works once both its skills are at level 30 and you discovered it yourself.
- You discover it the first time its situation happens with both levels met.
  - 💡 It already works on that occasion.
  - A discovery is kept. Below level 30 the synergy sleeps until the level is back.
- 💡 A synergy's strength is the weaker of its two skills' powers (death fatigue, an opposing skill).
- 💡 Each synergy's situation, as built:

  | Synergy | Situation and effect |
  |---|---|
  | Counterstrike | Your next melee hit within 2 seconds of a shield block, +20%. One hit per block. |
  | Charge | A melee hit while sprinting, +10%. |
  | High Ground | A projectile hit on a target at least 8 blocks below you, +10%. |
  | Venom Tip | A tipped arrow's effects on its target, 50% longer. Spectral arrows are not tipped. |
  | Forge of the Deep | A furnace you used last smelts raw ore or an ore block: a 5% chance of one more output. |
  | Deep Walker | In the Deep Dark, a sculk sensor (calibrated ones too) does not notice you in the last 2 blocks of its range. |
  | Green Thumb | A sapling you planted grows 20% more often, while you are on the server. |
  | Seafarer | A boat you steer pulls 10% harder forward. This is worked out on your own client, where your boat moves. |
  | Loyal Guard | Your tamed wolves take 20% less damage while you block. |
  | Tailored | Every point of wear on your armor has a 10% chance to be spared. |
  | Tidal Magic | A Water Breathing potion you drink lasts 50% longer. |
  | Herb Lore | Eating glow berries gives 10 seconds of Night Vision. |

- **The Chronicle:** `<world>/extremeprogression/world.json` holds every synergy's first discoverer, with the time and
  the place. It is written at once on every first discovery.
  - The first discovery is announced in the chat, unless the option `synergy_announce` is off.
  - The first discoverer gets the "★ First Discoverer" mark. It is cosmetic.
- **What the Codex shows** of a synergy you have not discovered:
  - nothing while neither of its skills is at 30;
  - its name in runes and a vague hint once one of them is;
  - after somebody on the server found it first, half the name readable, the two skills, and a clearer hint.
- **What the Codex shows** of a synergy you discovered: everything, with when and where.
- **The Codex & Chronicle screen** (a button on the Character Sheet) has two tabs. The Codex holds your own synergies,
  the Chronicle the server's first discoverers.

**Specialization conflicts, hard requirements and gear conflicts in the GUI**
- A specialization's detail panel names the one it excludes, with the reason from the table above.
- A node with a hard requirement shows the skill and level it needs and your own level. The Alchemist's new potions
  show the same on the specialization.
  - The four requirements live in one table that the effects read too.
  - 💡 Such a node can be bought before the level is there. It does nothing until then.
- In heavy armor, Exploration's Skill Screen says that its passives are off.

**Data and options**
- Player file schema 5: the perk chosen at each Mastery rank, and the discovered synergies with when and where. An
  older file loads with none.
- Seven new options:
  - the opposing skills' start level, maximum penalty, full gap and passive power;
  - the Mastery perk step;
  - the synergy level;
  - the announcement.

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

### How milestone 5 implements it (version 0.6.0)
- 💡 **The branch of every specialization** follows the approved Skill Screen mockup (round 21), from the bottom up:
  two passives; ability rank I; rank II; two passives; a choice node (one of two); ranks III, IV (level 60) and V
  (level 80). The mockup's branch cost about 30 points, but a branch must cost **more than 75** (round 8), so passives
  and choice nodes have **5 ranks**: row 1 at 2 points a rank, row 4 and the choice at 3, each ability rank 3. A full
  branch costs **80 points**; two branches share the 75 points a skill earns from level 25 to 100.
  - Points: 1 per level from level 25 (a skill at level 47 has 22).
  - The passives, choice nodes and abilities can be bought now, but **their effects come in milestone 6**, with each
    specialization's content; until then they show placeholder names ("Passive 1", "Choice A").
- **Respec:** one button per skill resets both specializations and every point, for 20% of the skill's XP (its levels at
  today's budget factor, plus Mastery), then waits 7 real days. Redistributing points is the same respec. The
  confirmation shows the exact cost first. Operators: `/skills admin respec <player> <skill>` is free and immediate,
  and the player is told.
- **Lost levels** set the newest points aside; regained levels give them back to the same nodes, in the same order. A
  specialization below its level (25 or 75) is dormant: kept, not in use. (This is the rule of §6; the death system
  in milestone 7 uses it.)
- **The ability key:** `R` uses a first specialization's ability, `Shift + R` a second's. It reaches the ability whose
  trigger fits what you hold or look at; workstations before tools before "anything"; on a tie, the first skill.
  - 💡 Triggers: Blade sword or spear · Crusher axe or mace · Brawler empty hand · Sniper and Skirmisher bow or crossbow
    · Harpooner trident · Bulwark shield · Prospector and Tunneler pickaxe · Excavator shovel or brush · Lumberjack an
    axe while looking at a log · Herbalist food · Grower hoe · the three Fishing specializations a fishing rod · Hexer a
    splash or lingering potion · Forgemaster, Armorer, Tinkerer, Alchemist and Enchanter their workstation in view ·
    Juggernaut, Resilience, Gatherer, Herder, Beastmaster, Wanderer, Survivalist and Acrobat anything.
  - Cooldowns are those of §2, saved in game time; a small icon beside the hotbar shows the ability the key would use
    while it is ready.
- **The trunks** all work. 💡 Choices made while building:
  - Patience (Fishing 5) takes 0.875 s off the bite wait (5% of vanilla's average); Sea Luck (Fishing 20) is 2 points
    of luck (treasure from 5% to about 9%).
  - Swimmer (Exploration 15) is +0.05 water movement efficiency; Catch Breath (Defense 15) brings the next natural
    regeneration 2 s forward after each hit in a fight.
  - "Furnaces / brewing stands you fill" are the ones you last opened.
  - Potent Brews (Arcana 15) works on potions you drink, and (from milestone 6) on the splash and cloud of thrown ones.
  - The Skill Screen fits GUI scale 3 at 1280 × 720 (426 × 240), the scale Minecraft picks there by itself.

### How milestone 6 implements it (version 0.7.0)
Milestone 6 gives the 30 specializations their content, in three parts. **Part 1: Combat and Survival** (Melee,
Ranged, Defense, Exploration). The abilities, prices and example choice nodes are those of §2; everything else is 💡,
worked out while building, as §9 foresaw ("the real content of every tree is worked out per skill during
implementation").

- 💡 **Rules for every specialization:**
  - **Ability ranks:** rank I is the ability of §2; every rank after it makes one number stronger (below); the
    cooldown stays.
  - **Passives and choice nodes** have five ranks; the effect grows by the same step each rank.
  - **The price** counts from the moment the specialization is chosen, as long as it is in use (not dormant).
  - **Damage an effect deals itself** (bleeding, a shockwave, Spikes, Mirror Shield, Riposte's counter) gives no XP and
    no bonuses, and leaves the target's invulnerability frames as they were.
  - **Area effects** (Shatter, Earthquake) hit hostile mobs and mobs after you, never other players.
  - **Exploration** keeps its gear conflict: its passives and choice nodes do not work in heavy armor (like its
    trunk); its abilities do.
  - **Allies** are other players and tamed animals.
  - An ability in effect shows above the ready icon beside the hotbar, with the seconds it still lasts.

| Specialization | Ability ranks (after I) | P1 · P2 (2 points a rank) | P3 · P4 (3 points a rank) | Choice (3 points a rank) |
|---|---|---|---|---|
| **Blade** | parry window +0.5 s, bleeding +1 s (rank I: 3 s, 4 s; bleeding = 1 damage a second) | Keen Edge: +2% damage with blade weapons · Lacerate: full blade hits 4% chance of 3 s bleeding | Parry: holding a blade, 3% chance to halve a hit up close · Precision: critical blade hits +4% | Deep Cut: bleeding +1 s **or** Quick Cut: +2% attack speed with blades |
| **Crusher** | shockwave +0.5 block, armor gone +1 s (I: 3 blocks, 8 s; others take half the hit) | Heavy Blows: +2% with axe or mace · Stagger: full hits 4% chance of Slowness II for 2 s | Sunder: full hits on armored targets +3% · Crater: mace smashes +4% | Earthquake: a mace smash hits enemies within 3 blocks for 6% of its damage **or** Cleave: your axe disables shields +1 s |
| **Brawler** | +1 s (I: 6 s); every hit in a Flurry +4% to the next (up to +40%) | Hardened Knuckles: +0.3 fist damage · Quick Hands: +4% fist attack speed | Combo: +2% per step of hits within 2 s (up to 5 steps) · Knockout: full punches 3% chance to stun 1 s | Iron Fist: +0.3 fist damage **or** Disarm: 3% chance a punched player drops their weapon |
| **Sniper** | headshot +20% (I: ×2) | Marksman: full shots +2% · Long Shot: +3% from 25 blocks | Hunter's Mark: a full hit makes the target glow 1 s · Patient Aim: a bow held 1 s past full draw +3% | Piercing: 20% chance to pierce 1 more target (always at V) **or** Headshot: headshots +6% |
| **Skirmisher** | +1 s (I: 5 s; the extra arrows cannot be picked up) | Quick Draw: bows draw 3% faster · Hit and Run: after a hit +3% speed for 3 s | Close Range: +2% within 15 blocks · Scavenger: 4% chance a shot costs no arrow | Quick Reload: crossbows load 5% faster **or** Running Shot: drawing slows you 20% less (not at all at V) |
| **Harpooner** | the pulled target is slowed (Slowness II) 1 s a rank; the harpoon waits 10 s for the throw | Trident Arm: thrown tridents +2% · Strong Throw: tridents fly 4% faster | Barbed: a trident slows its target 0.5 s · Hunter of the Deep: +4% on targets in water | Return: 20% chance a trident without Loyalty comes back (always at V) **or** Tidal Power: +4% in water or rain |
| **Bulwark** | +1 s (I: 6 s) | Sturdy Shield: 10% chance a block costs no durability · Brace: +10% knockback resistance while blocking | Guardian: shield raised, allies within 4 blocks take 2% less from mobs · Recovery: a disabled shield is ready 10% sooner | Mirror Shield: 5% of a blocked shot back to the shooter **or** Shield Bash: a block within 0.5 s of raising stuns the attacker 0.2 s |
| **Juggernaut** | +1 s (I: 8 s) | *In heavy armor:* Plating: +0.5 armor toughness · Iron Skin: 2% less from hits up close | Unyielding: +4% knockback resistance · Last Stand: below 30% health, 3% less damage | Spikes: 2% of a hit up close back to the attacker **or** Anchor: +20% knockback resistance while sneaking (immune at V) |
| **Resilience** | +1 heart (I: 6 hearts over 5 s) | Thick Blood: negative effects 5% shorter · Quick Recovery: natural regeneration 5% faster | Steady Heart: healing 3% stronger · Shake It Off: 4% chance to shake off a negative effect | Antidote: poison 20% shorter (immune at V) **or** Cool Blood: you burn 10% shorter |
| **Wanderer** | +2 s (I: 10 s) | Fleet: +1% speed · Explorer's Eye: +5% XP from new and revisited chunks | Rough Terrain: soul sand, mud, honey slow you 10% less · Landmark Hunter: +5% XP from new biomes and structures | Step Up: +0.08 step height (a full block at V) **or** Tireless: sprinting costs 20% less hunger (none extra at V) |
| **Survivalist** | +4 s (I: 20 s) | Slow Metabolism: 3% less hunger · Weatherproof: 4% less from fire, lava, freezing, drowning, cactus, magma | Hearty Eater: food gives 5% more saturation · Hunger Pangs: starving hurts 10% less | Fireproof: 10% less from fire and lava **or** Gills: 20% longer underwater (twice as long at V) |
| **Acrobat** | +3 s (I: 15 s) | Featherfall: falls hurt from 0.5 block higher · Spring: +2% jump strength | Tumble: Roll (Exploration 20) +3% chance · Evasive: 2% chance a projectile misses you | Dodge: sprinting, 2% chance to dodge a hit up close **or** Climber: ladders and vines 10% faster |

All numbers are **per rank**. A few readings of §2 that needed a choice (💡):
- **Riposte** parries a hit from any living attacker; it strikes back (your attack damage × 1.5, plus bleeding) only
  when the attacker is within your reach + 1 block.
- **Shatter** sets armor to 0 for this hit too: the shockwave comes before the armor is counted.
- **Flurry** makes every hit count as fully charged, so every hit also gives Melee XP while it lasts.
- **Deadeye** zooms the view to 30% while drawing; a headshot is a hit at the eyes or higher.
- **Bastion**: "behind you" is the half-plane at your back, up to 3 blocks away; with your shield raised it blocks
  from every direction.
- **Unbreakable** stops sprinting on the server and on your client.
- **Heavy armor** for Blade's price is the same as for Exploration: 3 or more pieces of iron or heavier.

**Part 2: Gathering** (Mining, Foraging, Farming, Fishing). The same rules hold (💡); in addition:
- **Area abilities** (Earthmover, Tunnel Bore, Tree Feller) break each block as if you broke it yourself, so every
  block gives its own XP and drops, follows the placed-block rules and costs durability. Earthmover and Tunnel Bore take
  only blocks of their tool, without a block entity, and no harder than twice the first block. **Tree Feller never takes
  a placed log**, so houses of logs are safe.
- **Ore Sense** sends only the ores near you to your client, which shows each as a glowing copy (in the ore's colour)
  seen through the walls; nothing else of the world is sent.
- **Catches** that change (Lucky Cast, Treasure Sense, Clean Water, Angler's price) are rolled again from vanilla's own
  fishing tables, so data packs keep their say. Lucky Cast only works in open water, where treasure gives XP.
- **Your companion** (Beastmaster) is your tamed animal nearest to you within 16 blocks; Pack Leader adds more.

| Specialization | Ability ranks (after I) | P1 · P2 (2 points a rank) | P3 · P4 (3 points a rank) | Choice (3 points a rank) |
|---|---|---|---|---|
| **Prospector** | range +2 blocks (I: 12 blocks, 10 s) | Rich Vein: +1% double ores · Prospector's Eye: ores +3% XP | Quick Pick: +3% breaking speed on ores · Lingering Sense: Ore Sense +1 s | Gem Eye: only diamond, emerald and ancient debris, +4 blocks **or** Broad Spectrum: every ore, +2 s |
| **Excavator** | +1 s (I: 8 s) | Fast Dig: +3% with a shovel · Finds: 1% chance of a find (flint, clay ball, bone, gold nugget, stick, string) | Shovel Saver: 5% chance a shovel or brush use costs no durability · Patient Brush: brushing +10% XP | Treasure Digger: +1% finds, one in ten rare (a pottery sherd or an emerald) **or** Archaeologist: 10% chance a brushed find comes twice |
| **Tunneler** | +1 s (I: 8 s, 1 × 3; V: 3 × 3) | Momentum: +2% with a pickaxe · Cave Runner: +2% speed out of sight of the sky | Strong Back: 5% chance a pickaxe use costs no durability · Bore Master: Tunnel Bore +1 s | Deep Delver: +20% on deepslate (as fast as stone at V) **or** Enduring: during Tunnel Bore, 10% chance a block costs no durability |
| **Lumberjack** | +12 logs (I: 16, V: 64) | Sharp Axe: +3% chopping on logs · Timber: +1% double logs | Woodsman: logs +3% XP · Steady Swing: 5% chance an axe use costs no durability | Wide Fell: Tree Feller +8 logs **or** Thrifty: 10% chance a log Tree Feller takes costs no durability |
| **Gatherer** | +2 s (I: 10 s) | Green Fingers: wild plants +3% XP · Plenty: 4% chance of a double wild yield | Quick Picking: +5% on melons, pumpkins, cocoa, mushroom blocks · Season's Bounty: Bountiful Harvest +1 s | Rare Seeds: grass gives torchflower seeds or a pitcher pod, 1% **or** Honey Harvest: 20% double honey from wild hives, whose bees stay calm |
| **Herbalist** | +5 s (I: 30 s) | Forager's Diet: wild food +10% saturation · Herb Knowledge: Regeneration +10% longer | Strong Stomach: Hunger 20% shorter (none at V) · Nourished: eating heals 0.4 | Mushroom Lore: stews give Regeneration I for 2 s **or** Berry Lore: berries fill +0.4 hunger (+2 at V) |
| **Grower** | +2 s (I: 10 s) | Bumper Crop: +1% extra crops · Crop Lore: crops +3% XP | Tiller: 5% chance a hoe use costs no durability · Festival Spirit: Harvest Festival +1 s | Ripening Aura: crops within 4 blocks grow 3% faster **or** Seed Saver: 20% chance a harvested crop replants itself (always at V) |
| **Herder** | +2 s (I: 10 s); the next breeding gives twins | Breeder: breeding and shearing +3% XP · Quick Growth: babies grow up 4% faster | Butcher: 10% chance of one more meat · Calm Herd: bred animals may breed again 5% sooner | Twins: 4% chance of twins **or** Wool Trade: 20% chance of one more wool when shearing, and one more leather from a cow |
| **Beastmaster** | +3 s (I: 15 s) | Alpha Bond: your companion deals +4% · Thick Fur: your companion takes 4% less | Mending Pack: your companion heals 0.1 a second · Tamer: taming and your animals' fighting +5% XP | Pack Leader: the companion bonuses reach 2 animals (3 from rank III), +2% damage **or** Rider: horses and camels you ride +3% speed and +2% jump |
| **Angler** | +4 s (I: 20 s, bites 80% sooner) | Quick Bite: fish bite 3% sooner · Fishmonger: fish +3% XP | Double Catch: 3% chance a fish comes twice · Strong Line: 5% chance a catch costs no durability | Ice Fishing: the bobber melts the ice it lands on (3 × 3 at I, 5 × 5 from II, 7 × 7 from IV; packed ice from III, blue ice at V) **or** Big Catch: fish fill +0.4 hunger (+2 at V) |
| **Treasure Hunter** | it waits +10 s; 2 treasure catches from rank III, 3 at V | Treasure Sense: 1% chance a fish becomes treasure · Appraiser: treasure +4% XP | Clean Water: 10% chance junk becomes a fish · Old Salt: fish bite 4% sooner | Deep Sea: 0.2% chance of a Heart of the Sea or a trident **or** Bookworm: 20% chance an enchantment on a fished book is one level higher |
| **Hookmaster** | stun +0.5 s (I: 2 s; it waits 10 s for the hook) | Barbed Hook: hooking deals 0.5 damage · Long Cast: the bobber flies 5% farther | Snag: what you hook is slowed 1 s · Hook Fighter: +3% melee on what is on your hook | Shake: 20% chance a hooked mob drops one of its normal drops, once per mob **or** Drag Hook: pull 20% harder (twice at V) |

All numbers are **per rank**. Readings of §2 (💡): Herding Call's animals are untamed animals within 16 blocks, which
walk to you; Rally the Pack's target is the mob you last hit (or the one that last hit you); the Herbalist's
"cultivated food" is food made from crops and the meat of farm animals (beef, pork, mutton, chicken); "bred animals"
for Calm Herd are the two parents.

**Part 3: Crafting** (Smithing, Arcana). In addition (💡):
- **"Furnaces / brewing stands you open"** are the ones you opened last (as for the trunk); a workstation remembers its
  user across restarts.
- **Repairs** follow this order: vanilla, then the trunk (Tempered Steel), then the prices (Forgemaster −15%, Tinkerer
  −20% of what was restored), then Fine Repair and Quick Hammer (of the item's whole durability), then Masterwork
  (fully).
- **The Alchemist's new potions** are plain vanilla potions with vanilla effects, brewed from an awkward potion
  (normal, splash or lingering) by data recipes: **Haste** (cocoa beans, 3:00), **Resistance** (an armadillo scute,
  3:00) and **Absorption** (honeycomb, 2:00). A stand brews them only for an Alchemist with **Foraging 30** (the hard
  requirement of §2). All four hard requirements of §2 are checked from milestone 6 on (Template Saver Mining 30, the new
  potions Foraging 30, Deep Sea Exploration 40, Rider Exploration 30); the GUI shows them in milestone 8.
- **Arcane Insight** lists every enchantment of the three offers beside the enchanting table, with a **Reroll the
  offers** button for the free reroll; both go once the enchantment is made or the time is up.
- **Thrown potions** carry their brewer's duration bonus (Potent Brews, Extended) into their splash and cloud, and
  their thrower's Hexer nodes; a Hexed splash potion leaves a cloud as a lingering potion would.
- **Preservation** raises the "Too Expensive" limit on the server and on the anvil screen alike. Repairs keep their
  enchantments in vanilla already, so that half of Preservation needs nothing.

| Specialization | Ability ranks (after I) | P1 · P2 (2 points a rank) | P3 · P4 (3 points a rank) | Choice (3 points a rank) |
|---|---|---|---|---|
| **Forgemaster** | +5 s (I: 30 s, 4× as fast) | Hot Coals: furnaces you use +4% speed · Smelter's Eye: smelting metal +3% XP | Coal Miser: 4% chance fuel is not used up · Long Forge: Flash Forge +5 s | Second Smelt: 4% chance of a double output **or** Slow Burn: fuel burns 10% longer |
| **Armorer** | +5% chance that it costs no levels (I: 25%) | Fine Repair: repairs restore +3% of the durability · Thrifty Hands: repairs cost 4% fewer levels | Smith's Eye: repairing +3% XP · Gentle Hammer: 5% chance the anvil does not wear | Preservation: the "Too Expensive" limit +2 levels (50 at V) **or** Quick Hammer: repairs restore +5% |
| **Tinkerer** | +2% durability (I: +10%); it waits 60 s; once per item | Upgrade Lore: upgrades and trims +3% XP · Polish: an upgraded item is repaired by 4% | Spare Material: 4% chance an anvil repair keeps its material · Patient Craft: Reforge waits +15 s | Template Saver: 4% chance to keep the template (needs Mining 30) **or** Trim Power: each trimmed armor piece gives a bonus by its material |
| **Alchemist** | +5 s (I: 30 s, 10× as fast) | Quick Brew: stands you use +4% speed · Brewer's Eye: brewing +3% XP | Distiller: 4% chance a brew keeps its ingredient · Long Catalyst: Catalyst +6 s | Extended: potions you brew last 5% longer **or** Potent: 6% chance a potion comes out one level stronger |
| **Enchanter** | +5 s (I: 30 s) | Lore: enchanting +3% XP · Lapis Thrift: 4% chance of a lapis back | Book Smart: combining books at an anvil costs 4% fewer levels · Insightful: Arcane Insight +6 s | Frugal: 20% chance enchanting costs 1 level less (always at V) **or** Rich Choice: offers rolled as if 1 level higher |
| **Hexer** | the cloud +10% larger (I: twice the size and time of a lingering cloud) | Strong Arm: thrown potions fly 5% farther · Concentrated: your thrown potions' effects last 5% longer | Careful Aim: 20% chance your own negative thrown potions miss you · Long Hex: Hex waits +3 s | Lingering Venom: negative effects from your thrown potions last 6% longer **or** Wide Mist: your splash potions and clouds reach 10% farther |

**Trim Power** at rank V, per trimmed piece (a fifth of it a rank): iron +0.5 armor · diamond +0.5 armor toughness ·
netherite +5% knockback resistance · copper +2% speed · redstone +2% attack speed · quartz +0.25 attack damage ·
amethyst +0.5 max health · lapis +2% breaking speed · emerald +0.5 luck · gold +0.1 movement efficiency · resin +0.2
oxygen bonus.

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
  - Until it is answered, a Collapse does **not** count: the mod only reacts to a real death.

### How milestone 7 implements it (version 0.8.0)
The confirmed rules above, as built. Everything marked 💡 was decided while building.

- **The loss, layers 1 and 2:**
  - Every skill's "step" is its progress toward the next Mastery rank at the maximum level, or else the XP within its
    current level.
  - Every skill loses `step × min(1, base × (its level / the highest level) × the dimension's factor)`. So a skill never
    drops a level in these layers, even in the End at pressure 1 (200%).
  - 💡 **The highest skill** is the one with the highest level. On a tie it is the one with more Mastery ranks, then
    the one with more XP.
  - 💡 With **every skill at level 0**, each counts as the highest: each loses the base share of its XP.
  - 💡 **A death right at the start of a level** costs that skill nothing in layers 1 and 2. Nothing goes below level 0.
- **Layer 3**, from death pressure 2, after layers 1 and 2:
  - The highest skill loses 1 Mastery rank and the perk chosen at it. Only with no ranks does it lose 1 whole level.
  - 💡 A lost level lands at the **same point of the level below**. At pressure 2 the base is 100%, so layer 2 has
    already taken the whole step of the highest skill, and the skill lands at the start of the level below.
  - 💡 Layer 3 is **always exactly one** rank or level. The dimension's factor does not multiply it.
- **Points and specializations on a lost level** (the rules of milestone 5):
  - The most recently bought point is set aside first.
  - A specialization below 25 or 75 is dormant. It works again, with the same choice and the same points, as soon as
    the level is back.
- **Death pressure:**
  - +1 on every death.
  - −1 for every hour of active play, which means online and not AFK (the 5-minute AFK rule of §12), counted per second.
  - 💡 A death starts that hour over.
  - 💡 The server sends the countdown once a minute, so the Death Register never runs on while you are AFK.
- **Dimensions:**
  - Overworld ×1, Nether ×1.5, End ×2, the Seam ×2.5.
  - 💡 The Seam only counts as the Seam with Extreme Dimensions loaded. Without it the id counts ×1, as does any other
    mod's dimension.
- **Death fatigue:**
  - For 10 minutes after a death, every passive bonus works at 50%.
  - 💡 That covers the 40 trunk bonuses and every passive and choice node, including the nodes that lengthen an ability.
  - 💡 It does not cover an ability's own rank, a specialization's price, or XP.
  - 💡 A bonus that is all or nothing (Forest Foot, Light Step, Farmhouse Fare, Fisher's Fare) works half of the time.
  - 💡 **Shield Reflex**, one tick sooner, is off while fatigued (a tick cannot be halved).
  - 💡 Thresholds such as Pack Leader's companions and Ice Fishing's radius use the halved rank.
  - 💡 The 10 minutes are **game time**: they pause in a paused singleplayer world, and on a server they also run while
    you are offline.
- **Which deaths count:**
  - 💡 Deaths in **Creative or Spectator** cost nothing and do not raise the pressure.
  - 💡 **Two deaths in one server tick** count once.
  - A PvP death costs the same as any other, and the killer gains nothing (no vampirism).
- **Saving:**
  - 💡 The loss is written to the player file **at once**, not at the next autosave. A server that stops right after
    a death keeps the loss, and nothing can apply it twice: the file holds the result, not a pending loss.
  - The player file (schema 4) adds the deaths, the pressure, its play-time counter, the end of death fatigue, and the
    last 50 deaths. An older file loads with none.
- **The Death Register** (the approved mockup of round 21, at GUI size):
  - **Left:** the deaths, the death pressure with its three steps (0 · 50%, 1 · 100%, 2+ · a whole level), when it
    drops, death fatigue left, the dimensions' factors (the current one framed), and "Loss is permanent".
  - **At Stake:** every skill in two columns. Each shows what it would lose now and a bar of its step: kept, lost in
    red, still to earn. Hovering shows its share and what would be left. A line under it says what the pressure means
    for this death and the next.
  - **Recent deaths:** one line each, the newest first, with a scroll wheel. Hovering shows the cause, the place, the
    pressure and every skill's loss.
  - 💡 At Stake runs the **server's own death rules** on a copy of the progression the server sent, with the share and
    pressure the server sent. A death takes exactly what it showed (a client test checks this).
- **After a death:**
  - 💡 A chat line at the moment of death says how many skills lost progress.
  - After respawning, a **summary** opens once, as soon as no other screen is open. It shows the cause, the place and
    the factor, every skill that lost something, death fatigue, and what the death pressure now means. Its buttons are
    Death Register and Continue.
  - 💡 The **Character Sheet** shows the death pressure, the death fatigue while it lasts (never on the HUD), and a
    Death Register button.
- **Configuration** (`config/extremeprogression.json`, 10 options): the two base shares, the pressure for layer 3, the
  three dimension factors, the fatigue's minutes and power, the pressure decay in minutes, and the history length.
  - 💡 Until milestone 9 syncs the settings, the client's own copy describes the pressure steps in the GUI. The losses
    themselves always come from the server.

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
  - 1.2.0: the owner found the three branches side by side too cramped. The specializations are now tabs, and the tree
    shows one branch at a time over its full width. Every node has its own name, lines show which node opens which,
    and the screen grows with the window up to 560 × 330.
  - Nodes have clear states: owned, buyable, out of reach, requirement missing, conflict, dormant.
- **HUD outside the GUI:** minimal.
  - A short notice on a level-up or a discovered synergy.
  - A small icon when an ability is ready.
  - No XP bar on screen.
    - 1.3.0, the owner's choice: a player may **track** skills (a Track button on every Skill Screen). Each tracked
      skill shows right of the hotbar, with its icon, level (Mastery rank past 100) and progress bar.
      Nothing shows until a player tracks something, and the choice is kept per client in
      `config/extremeprogression-client.json`.
    - 1.3.1, the owner's choice: the tracked skills are a **second hotbar** in the hotbar's own frame, one slot per
      skill: the icon, the level as an item count and a green durability bar for the progress.
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

### How milestone 9 implements it (version 0.10.0)
Everything marked 💡 was decided while building.

**Presets**
- The option `preset` is "hardcore" (the default), "normal" or "custom".
- 💡 Hardcore and Normal set only the options they govern (four, and the party bonus's two since 1.4.0):

  | Option | Hardcore | Normal |
  |---|---|---|
  | `death_base` | 0.5 | 0.25 |
  | `death_base_pressured` | 1.0 | 0.5 |
  | `death_whole_loss` (layer 3 at all, new) | true | false |
  | `xp_multiplier` | 1.0 | 1.5 |
  | `party_bonus_per_member`, `party_bonus_max` (1.4.0, §16) | 0.05, 0.15 | 0.10, 0.30 |

- Every other option stays as the file says.
- 💡 To change one of them by hand, choose "custom". With Hardcore or Normal, the file is written back with the
  preset's values.
  - The settings screen switches to "custom" by itself when you edit one of them.
- 💡 An unknown preset counts as "custom", with a warning in the log.

**Data files** in `config/extremeprogression/`, read on startup and on `/skills reload`:
- `xp/<skill>.json`: Appendix A, one file per skill. It holds the single values by name (`brushing`, `new_biome`) and
  the tables of ids (`pickaxe`, `logs`, `danger`, `catches`, `structures` by type).
  - Minecraft's ids go without their namespace.
  - 💡 Another mod's blocks, mobs and items give XP when a table lists them as `"namespace:path"`.
  - 💡 A table in a file is the whole table: an id left out gives nothing.
- `skills.json`, per skill:
  - `enabled` (💡 a disabled skill earns no XP; what it reached keeps working) and `xp_multiplier`;
  - every specialization's ability `cooldown_minutes`;
  - every hard requirement's `skill` and `level`.
- `synergies.json`: per synergy, `enabled` and its `strength`. The strength is the share it adds (0.2 = 20%), for Deep
  Walker the blocks, for Herb Lore the seconds.
- Files are written back complete when they lack something.
  - 💡 A file that cannot be read is left for the admin to fix, and its defaults are used.

**The settings between server and clients**
- 💡 The server sends its config, `skills.json` and `synergies.json` to every client on joining and after every
  change. The GUI then shows the server's own numbers.
  - The XP tables only matter on the server and stay there.
  - A client in its own singleplayer world takes nothing over (it is the same game).
  - On leaving a server the client goes back to its own files.
- **The settings screen** (admins; `/skills settings`, or a Settings button on the Character Sheet for whoever may
  change them):
  - every option of the three, searchable and in pages of eight;
  - the presets as buttons;
  - Save and Revert.
- The server checks that the player is an admin, applies the settings, writes the files, tells every client, and logs
  the change.
  - 💡 Anyone else's settings are ignored and logged.

**XP events**
- `/skills xprate <multiplier> [all|skill] [minutes]` starts an event, announced in the chat. Without minutes it runs
  until `/skills xprate reset`.
- 💡 A new event for the same skills replaces the running one. An event for one skill and one for all multiply.
- An event ends by itself, on reset, or when the server stops, announced each time.
- The Character Sheet shows a running event with its minutes.

**Commands**
- `/skills top [skill]` (everyone): the first ten in the chat.
- `/skills inspect <player>` (operators) opens a read-only screen of that player. It works for offline players too, from
  their file.
  - The screen shows totals, deaths, synergies, and every skill's level, specializations, points and perks.
- `/skills admin reset <player> [skill]` (admins) asks first. `/skills admin reset confirm` within 30 seconds does it.
  - A full reset keeps only the name.
  - The file is written at once, the player is told, and the log records it.
- `/skills preset <hardcore|normal|custom>` and `/skills reload` (admins).
- **The leaderboard page** (a button on the Character Sheet): every player with a file, by total level or by one skill,
  with Mastery ranks. The first 100 come from the server.

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

### How milestone 3 implements it (version 0.4.0)
- **Marks per block**, saved with the chunk as three bitsets per section (§10): *placed*, *bonemealed*, and *planted*
  (grown by itself from something a player planted).
  - *Placed:* every block placed from an item (by a player or a dispenser), every block a piston moved, and stone,
    cobblestone, obsidian and basalt that lava and water formed.
  - A block's marks go when it is replaced by another block. They stay between blocks that turn into each other: the
    soils (dirt, grass, farmland, mud, clay and so on), a log and its stripped log, the parts of a cave vine. A falling
    block takes its marks along.
  - *Bonemealed:* everything bonemeal changes or grows, by hand or from a dispenser: the crop or bush itself, a whole
    tree, the flowers on a grass block. Picking berries that bonemeal ripened clears the mark, so what grows back by
    itself counts again.
  - *Planted:* the logs of a tree grown from a placed sapling (25% XP), mushrooms spread from a placed one and vines
    grown down from planted glow berries (no Foraging XP: they are not wild).
- **Only a player's own hand counts:** XP comes from breaking a block, picking or shearing by hand, fishing, breeding and
  taming. Explosions, pistons, water, dispensers and villagers never give any.
- **Mining** needs a pickaxe that can mine the block (a wooden pickaxe on diamond ore gives nothing), and a shovel for
  the shovel blocks. Logs need no axe.
- **AFK:** real play is looking around, a change in the movement keys, or an interaction. Fishing asks for looking
  around or the keys, because casting can be done with an auto-clicker.
- **Diminishing returns:** a gain keeps `threshold ÷ XP in the window` once the window is past the threshold, never
  less than the floor (so 50% at 10,000 XP in 10 minutes, 5% from 100,000).
- 💡 Choices made while building, open to change (all in the config or the XP table):
  - "Overfished": catches 11–20 in one spot give **25%**, from the 21st **10%**.
  - Wood new in 26.3: **poplar logs 18 XP**, like birch.
  - Rare flowers (10 XP): blue orchid, allium, lily of the valley, the eyeblossoms, the golden dandelion (new in 26.3),
    the spore blossom and the cactus flower. Every other wild flower 6 XP.
  - Taming an animal the table does not list: 200 XP.
- Still to come: **damage your tamed animals deal** (Farming XP) needs the mob danger factors and origin flags, so it
  comes with the combat XP in milestone 4.
- ✅ **Foxes** cannot be tamed in vanilla, only bred (the baby trusts you): a fox baby gives the normal **40** breeding
  XP, not the 300 of taming, so a fox farm is no faster than other Farming (the owner, after milestone 3).

### How milestone 4 implements it (version 0.5.0)
- **Every hit on the server** is measured, the killing blow included: the target's health and absorption before and
  after, what a shield blocked, and what armor absorbed.
  - Melee and Ranged count the damage that really came off the target, so never more than its remaining health.
  - Melee: the attack bar is read just before the attack (the attack empties it). Sweeping hits count with it.
  - Ranged: an arrow counts when vanilla marks it critical, which is a fully drawn bow (100%, a little stricter than the
    90% in §2) and every crossbow arrow; thrown tridents and crossbow fireworks always count. The distance is measured
    between the shooter and the target when the shot lands.
  - Defense: shield-blocked damage plus `damage − damage taken` (armor, protection enchantments, Resistance).
- **Mob origins:** spawners, spawn eggs (also from a dispenser) and commands mark the mob when it is made or set up; the
  mark is saved on the mob and passes on when it turns into another mob (a zombie that drowns). Trial spawners give
  full XP.
- **Exploration** is stored in the player file (schema 2): per dimension a bitset of 32 × 32 chunks per region, with the
  time of the last visit. A region left alone for 30 days starts a revisit round, in which each of its chunks counts
  once more. Chunks only count while the player could earn XP (not AFK, not in Creative or Spectator), so nothing is
  used up while it cannot pay. Structures count when you stand inside one of their pieces.
- **Crafting:** only what a player takes out by hand counts. A brewing stand remembers which bottles it brewed itself
  (saved on the stand); a bottle put in by hand or by a hopper is not its work. Enchanting only pays when the item was
  really enchanted.
- 💡 Choices made while building, open to change (all in the config or the XP table):
  - Defense: "attacked or moved recently" is **10 seconds**.
  - Falls: the spot is blocked within **3 blocks for 10 minutes**.
  - A hostile mob the table does not list: danger factor **1.0**. Iron golems and zombified piglins only count when they
    were already after you before the hit, so a gold farm or iron farm earns nothing.
  - Structures the table does not list: the witch hut **250**; structures of other mods (such as the Seam's) **300**;
    buried treasure and nether fossils nothing.
  - The spear's charge (hold to use) does not go through a normal attack, so it gives no Melee XP yet; a normal hit with
    a spear does, and so does the mace's smash.
- Not yet: the **opposing-skill** penalty (Melee ↔ Ranged, Defense ↔ Exploration, Smithing ↔ Arcana) comes with the
  connections in milestone 8, and the new Alchemist potions with the specializations in milestone 6.

## 13. Implementation roadmap (💡 proposal)

Work starts only after the owner's final approval (§15). Each milestone ends with: compiling, running the tests, checking for regressions, a report of what was built and what is still open, and the owner's approval before the next major system.

Progress: milestone 0 ✅ done (version 0.1.0) · milestone 1 ✅ done (0.2.0) · milestone 2 ✅ done (0.3.0) · milestone 3 ✅ done (0.4.0) · milestone 4 ✅ done (0.5.0) · milestone 5 ✅ done (0.6.0) · milestone 6 ✅ done (0.7.0) · milestone 7 ✅ done (0.8.0) · milestone 8 ✅ done (0.9.0) · milestone 9 ✅ done (0.10.0) · milestone 10 ✅ done (1.0.0).

| # | Milestone | Contents | Tests |
|---|---|---|---|
| 0 | Project skeleton | Gradle and Loom as in Extreme Dimensions (MC 26.3, Java 25, split source sets), `fabric.mod.json`, CI with build, server game tests and a client screenshot test. The empty mod loads on server and client. | CI green |
| 1 | Core progression | Skill definitions, the XP curve and budget, the player data model with saving (atomic, backups, schema version), syncing to the client, `/skills admin set/add` | Unit tests for every formula; a migration test |
| 2 | GUI foundation | The `Ui` house style, Character Sheet, Skill Screen (read only), inventory button, the `K` hotkey | Client screenshots |
| 3 | XP: gathering | Mining, Foraging, Farming, Fishing; placed-block tracking, bonemeal and automation rules, open water and "overfished", diminishing returns, the AFK rule | Game tests per exploit rule |
| 4 | XP: the rest | Melee, Ranged, Defense, Exploration, Smithing, Arcana; mob origin flags, PvP limits, chunk, biome and structure tracking | Game tests |
| 5 | Trees and abilities | Points, buying nodes, choice nodes, the `R` / `Shift + R` keys, cooldowns, respec; all 10 trunks | Unit and game tests |
| 6 | Specializations | All 30 specializations: abilities, nodes and trade-offs, in three parts (Combat and Survival; Gathering; Crafting) | Game tests per ability |
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

## 16. Playing together (1.4.0, the owner's choice)

The owner asked for a reason to play together. Approved: a party with an XP bonus, and duo synergies. Not done, on
purpose: giving XP lost in a death back through a teammate, which would break §7's fixed principle "nothing comes back
but new XP".

### Parties
- The commands are always under `/skills party`, and also `/party` when no other mod has a `/party` (BattleHub has
  its own parties; two mods' `/party` would merge into one command). They register after every other mod's to know.
- `/party invite <player>` invites a player who is in no party; the invitation stands for 2 minutes
  (`party_invite_seconds`) and is accepted with a click in the chat, `/party accept` or the Party screen.
- Accepting makes a party of the two (the inviter leads) or joins the inviter's party. At most 5 players
  (`party_max_size`).
- Any member invites. The leader removes members (`/party kick`) and hands the lead on (`/party leader`). When the leader
  leaves, the longest member leads; a party of one is no party.
- Parties are kept in `<world>/extremeprogression/parties.json`, written at once on every change, with backups.
- The **Party screen** (a button on the Character Sheet): the members with a crown for the leader and a dot for who is
  near and earning, buttons for the leader, the invitations waiting, and the players online who are in no party.
- Party members carry a green ◆ before their name above their head (not where name-tag additions are hidden).

### The party bonus
- +X% XP for every other party member who is within 48 blocks in the same dimension (`party_range`) and earned XP of
  their own in the last 60 seconds (`party_active_seconds`), up to a maximum. AFK, Creative and Spectator never count.
- The presets govern it: Hardcore +5% a member up to +15%, Normal +10% up to +30% (`party_bonus_per_member`,
  `party_bonus_max`).
- It multiplies every gain after the other rules (diminishing returns, the XP multiplier, an XP event, an opposing skill).
- A fight needed no new rule: every hit earns its own Melee or Ranged XP (counted up to the target's remaining health),
  and every block or armor hit its Defense XP, so everyone in a fight earns their own share.

### Duo synergies
- Six synergies (eight since 1.5.0) between two party members near each other: the one who acts needs the first skill at level 30, the
  party member the second. They count as synergies everywhere (the strength and on/off in `synergies.json`, the
  power of the weaker part).
- The first time the situation happens, both discover it, whoever of them had not yet. The Codex shows them under
  **Together**, with whom each was found; the Chronicle shows the first pair ("Steve & Alex") beside the others.

| Duo synergy | Yours + the party member's | Effect |
|---|---|---|
| Shield Wall | Defense + Melee | While you block with a shield, a party member within 4 blocks takes 15% less damage. |
| Spotter | Ranged + Defense | Your arrows deal 15% more to a mob that is after a party member. |
| Pincer | Melee + Melee | Your melee hits deal 10% more to a mob a party member hit within the last 3 seconds. |
| Field Medic | Arcana + Defense | Good effects you splash on a party member last 25% longer. |
| Whetstone | Mining + Smithing | Your tools and weapons wear 15% slower within 16 blocks of a party member. |
| Hearty Meal | Exploration + Farming | Food you eat within 8 blocks of a party member fills 25% more. |
| Caravan (1.5.0) | Trading + Exploration | Wandering traders cost 10% less with a party member within 32 blocks. |
| Market Day (1.5.0) | Trading + Farming | Farmers and butchers cost 10% less with a party member within 32 blocks. |

### TAB
- Per player: `party_size`, `party_bonus`, `party_leader`, `party_members`, and `party` (a green ◆ with the size).
  Per server: `parties`. The shared TAB footer shows the party and its bonus.

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

### A.11 Trading (1.5.0, round 27)
Calibration: ≈ 120 trades an hour for an active trader. Every value is in `config/extremeprogression/xp/trading.json`.

| Source | XP |
|---|---:|
| A villager's trade, by the offer's tier: novice · apprentice · journeyman · expert · master | 30 · 45 · 65 · 90 · 120 |
| Every emerald paid or received in a trade | +1.5 |
| A wandering trader's trade | the journeyman's × 1.5 |
| One villager's offer after its 4th trade since the last restock | × 0.25 |
| A villager reaching apprentice · journeyman · expert · master through your trade | 150 · 300 · 500 · 800 |
| A gold ingot of yours a piglin barters | 20 |
| A raid won, × its omen level | 1,000 |

- An offer's tier is the villager's level when the offer came; offers from before 1.5.0 count two a level from novice.
- Villagers, wandering traders and piglins from spawn eggs, spawners, dispensers or commands give nothing.
