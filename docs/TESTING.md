# Testing Extreme Progression

This is for trying the mod in a real game (version 1.0.0, milestones 0–10 of the
[roadmap](DESIGN.md#13-implementation-roadmap--proposal)). What is not built yet is listed at the end.

## What you need

- **Minecraft Java Edition 26.3**
- **Fabric Loader 0.19.5** or newer for 26.3 (the [Fabric installer](https://fabricmc.net/use/installer/))
- **Fabric API 0.162.0+26.3** or newer ([Modrinth](https://modrinth.com/mod/fabric-api))
- **Java 25** (the Minecraft launcher brings its own)
- The mod's jar: `extremeprogression-1.0.0.jar`
  - the release: [v1.0.0 on GitHub](https://github.com/Peertjeh/In-Game-MMO/releases/tag/v1.0.0);
  - the latest green build is always on the
    [`ci-jar` branch](https://github.com/Peertjeh/In-Game-MMO/tree/ci-jar) (click the jar, then "Download raw file");
  - or under **Artifacts** of any [build run](https://github.com/Peertjeh/In-Game-MMO/actions).

## Installing

1. Install Fabric Loader for 26.3 with the installer, and start that profile once.
2. Put the Fabric API jar and `extremeprogression-1.0.0.jar` in the `mods` folder of that game directory.
3. Start the game. The title screen's mod list (Mods) should show Extreme Progression.

On a **server**, the same two jars go in the server's `mods` folder; every player needs the mod too (it has a GUI and a
key of its own). Extreme Dimensions is optional; the two work side by side.

## Getting around

| What | How |
|---|---|
| Character Sheet | `K`, the button beside the recipe book in your inventory, or `/skills` |
| A skill's tree | click the skill on the Character Sheet |
| Choose a specialization, buy a node | select it in the tree, then the button in the detail panel |
| Your first specialization's ability | `R` (rebindable in Controls), with the right tool or looking at the right workstation |
| Your second specialization's ability | `Shift + R` |
| Ability ready / in effect | the small icons right of the hotbar (in effect: with the seconds left) |
| Death Register | the button at the foot of the Character Sheet |
| Codex & Chronicle | the Codex button at the foot of the Character Sheet |
| Mastery perks | the Mastery button on a Skill Screen (from level 100) |

## Commands for testing (operators, or cheats on in singleplayer)

| Command | What it does |
|---|---|
| `/skills admin set <player> <skill> <level>` | sets a skill's level: **25** for the first specialization, **75** for the second, **100** for Mastery |
| `/skills admin add <player> <skill> <xp>` | gives (or with a minus, takes) XP |
| `/skills admin respec <player> <skill>` | a free respec: both specializations and every point back |
| `/skills admin cooldowns <player>` | every ability ready again (cooldowns are 4–5 minutes) |
| `/skills admin reset <player> [skill]` | a reset, after `/skills admin reset confirm` within 30 seconds |
| `/skills xprate <multiplier> [all\|skill] [minutes]` | an XP event (`/skills xprate reset` ends it) |
| `/skills top [skill]` | the leaderboard in the chat |
| `/skills inspect <player>` | a read-only look at a player, offline ones too |
| `/skills settings` | the settings screen (admins) |
| `/skills preset <hardcore\|normal\|custom>`, `/skills reload` | a preset; reread the config and data files |

Skill ids: `melee`, `ranged`, `defense`, `mining`, `foraging`, `farming`, `fishing`, `exploration`, `smithing`,
`arcana`. Points: from level 25 one point a level (75 at level 100), shared by both specializations of a skill. XP
raises your levels up to a total of 400 across all skills; `set` is not held to that cap.

Options are in `config/extremeprogression.json` (read at start, or with `/skills reload`). To speed up XP for a test
world, set `preset` to `custom` and raise `xp_multiplier` (with the Hardcore preset it stays 1), or start an XP event.
The XP tables and the skill and synergy settings are in `config/extremeprogression/`.
Player data is in `<world>/extremeprogression/players/`.

## A test plan

1. **XP and the Character Sheet**: mine ores and stone, fell a tree, harvest grown wheat, fish in open water, fight,
   explore new chunks and biomes, smelt metal, repair at an anvil, enchant, brew. Each shows a short XP notice in the
   skill's colour; the sheet and the trees show levels and XP.
2. **The rules against exploits**: placed blocks give no XP; crops grown with bonemeal give none; a small fishing pit
   gives none; spawner mobs give none; standing still (AFK) gives none.
3. **Trunks** (levels 5, 10, 15, 20 of every skill): each tree shows them at the bottom; they work by themselves.
4. **Specializations**: set a skill to 25, choose one, buy ability rank I (bottom of the branch after the first
   passives), and press `R` with its tool. Then set the skill to 75 for the second, and try `Shift + R`. Check that a
   specialization that conflicts with one of another skill cannot be chosen (Brawler and Bulwark, for example).
5. **The 30 abilities** and how to reach them:
   - Melee: Blade (a sword), Crusher (an axe or mace), Brawler (empty hand, no iron or heavier chestplate)
   - Ranged: Sniper and Skirmisher (a bow or crossbow), Harpooner (a trident)
   - Defense: Bulwark (a shield), Juggernaut and Resilience (anything)
   - Mining: Prospector and Tunneler (a pickaxe), Excavator (a shovel or brush)
   - Foraging: Lumberjack (an axe, looking at a log), Gatherer (anything), Herbalist (food in hand)
   - Farming: Grower (a hoe), Herder and Beastmaster (anything)
   - Fishing: all three with a fishing rod
   - Exploration: all three with anything
   - Smithing: looking at a furnace (Forgemaster), an anvil (Armorer) or a smithing table (Tinkerer)
   - Arcana: looking at a brewing stand (Alchemist) or an enchanting table (Enchanter); a splash or lingering potion in
     hand (Hexer)
6. **Nodes**: every node's detail panel says what it does; buy a few and check the effect. The full table is in
   [DESIGN.md, "How milestone 6 implements it"](DESIGN.md#how-milestone-6-implements-it-version-070).
7. **Respec**: the Respec… button shows the cost first (20% of the skill's XP, then 7 days' wait); the admin command is
   free.
8. **Deaths** (play in Survival: Creative deaths cost nothing):
   - Open the Death Register first: At Stake shows what a death would take now. Die, and compare it with the summary
     after respawning.
   - Die again soon: the pressure is 1, so twice the loss. A third time, at pressure 2, your highest skill also loses a
     whole level, or a Mastery rank when it has one.
   - Try the Nether or the End: the factor shows in the register.
   - For 10 minutes after a death your bonuses are at half strength. The Character Sheet shows the time left.
   - A skill set to 25 with a specialization, then a death at pressure 2: the specialization sleeps until you win level
     25 back.
   - To start over: `/skills admin set` does not touch the death pressure. A new world or a removed player file does.
9. **Opposing skills**: set Melee to 60 and Ranged to 10. The Ranged tile shows −60% and Ranged XP drops to 40%.
   Ranged's bonuses work at 75%.
10. **Mastery**: set a skill to 100 and add 400,000 XP per rank (`/skills admin add <player> mining 800000` for two
    ranks). The Skill Screen's Mastery button shows "(!)" while a rank waits for its perk.
11. **Synergies** (both skills at 30; see the table in
    [DESIGN.md](DESIGN.md#how-milestone-8-implements-it-version-090)):
    - Melee and Defense at 30: block a hit with a shield, then hit back within 2 seconds. This discovers
      Counterstrike, with a chat message.
    - Melee and Exploration at 30: hit something while sprinting. This discovers Charge.
    - Foraging and Arcana at 30: eat glow berries. This discovers Herb Lore.
    - The Codex shows what you found, and runes with a hint for the rest once one skill is at 30. A second player who
      finds the same synergy is not the first in the Chronicle.
12. **Administration** (as an admin, op level 3; in singleplayer with cheats on):
    - `/skills settings`: change an option and save. Every client gets it, and the file in `config/` changes.
    - Choose the Normal preset: the Death Register shows "2+·off".
    - `/skills xprate 2 all 10`: a chat message, the Character Sheet shows "XP ×2", and XP doubles.
    - Edit `config/extremeprogression/xp/mining.json` (for example `"stone": 30`), then `/skills reload`: stone gives
      ten times as much.
    - The Leaderboard button, `/skills top mining`, `/skills inspect <name>` (offline players too), and a reset with its
      confirmation.

## Not built yet

- **Extreme Dimensions integration, the balancing pass and the release**: milestone 10.

## Known rough edges

- The Respec… confirmation works its preview out with the default options; a server with other respec options charges
  what its own options say.
- A spear's charge attack gives no Melee XP.
- The Death Register describes the pressure steps with the client's own options; on a server with other death options
  the losses (and At Stake) still follow the server.
- An Extreme Dimensions Collapse does not count as a death (an open question in the design).

## Reporting a problem

Say what you did and what happened, with a screenshot if you can, and add `logs/latest.log` from the game directory
(and `crash-reports/` after a crash).
