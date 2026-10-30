# Testing Extreme Progression

This is for trying the mod in a real game while it is still in development (version 0.7.0, milestones 0–6 of the
[roadmap](DESIGN.md#13-implementation-roadmap--proposal)). What is not built yet is listed at the end.

## What you need

- **Minecraft Java Edition 26.3**
- **Fabric Loader 0.19.5** or newer for 26.3 (the [Fabric installer](https://fabricmc.net/use/installer/))
- **Fabric API 0.162.0+26.3** or newer ([Modrinth](https://modrinth.com/mod/fabric-api))
- **Java 25** (the Minecraft launcher brings its own)
- The mod's jar: `extremeprogression-0.7.0.jar`
  - the latest green build is always on the
    [`ci-jar` branch](https://github.com/Peertjeh/In-Game-MMO/tree/ci-jar) (click the jar, then "Download raw file");
  - or under **Artifacts** of any [build run](https://github.com/Peertjeh/In-Game-MMO/actions).

## Installing

1. Install Fabric Loader for 26.3 with the installer, and start that profile once.
2. Put the Fabric API jar and `extremeprogression-0.7.0.jar` in the `mods` folder of that game directory.
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

## Commands for testing (operators, or cheats on in singleplayer)

| Command | What it does |
|---|---|
| `/skills admin set <player> <skill> <level>` | sets a skill's level: **25** for the first specialization, **75** for the second, **100** for Mastery |
| `/skills admin add <player> <skill> <xp>` | gives (or with a minus, takes) XP |
| `/skills admin respec <player> <skill>` | a free respec: both specializations and every point back |
| `/skills admin cooldowns <player>` | every ability ready again (cooldowns are 4–5 minutes) |

Skill ids: `melee`, `ranged`, `defense`, `mining`, `foraging`, `farming`, `fishing`, `exploration`, `smithing`,
`arcana`. Points: from level 25 one point a level (75 at level 100), shared by both specializations of a skill. XP
raises your levels up to a total of 400 across all skills; `set` is not held to that cap.

Options are in `config/extremeprogression.json` (read at start). `xp_multiplier` speeds up XP for a test world.
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

## Not built yet

- **Dying costs nothing yet**: the death system (losing levels, death pressure, the Death Register) is milestone 7.
- **Mastery perks, synergies, opposing skills' XP penalty** and showing specialization conflicts and hard requirements
  in more detail: milestone 8.
- **Presets, the settings screen, the leaderboard, XP events**: milestone 9.

## Known rough edges

- The Respec… confirmation works its preview out with the default options; a server with other respec options charges
  what its own options say.
- A spear's charge attack gives no Melee XP.

## Reporting a problem

Say what you did and what happened, with a screenshot if you can, and add `logs/latest.log` from the game directory
(and `crash-reports/` after a crash).
