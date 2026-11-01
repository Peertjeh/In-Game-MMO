![The Character Sheet](https://raw.githubusercontent.com/Peertjeh/In-Game-MMO/develop/docs/images/character-sheet.png)

*Every level is earned. Every death is felt.*

**Extreme Progression** is a hardcore RPG progression overhaul for Minecraft 26.3 on Fabric. Eleven playstyle skills,
each with its own skill tree, three specializations and a Mastery layer. Synergies between skills that you discover
yourself. Slow, meaningful progression, and deaths that cost you for real, always shown up front.

It works on its own, and goes hand in hand with [Extreme Dimensions](https://github.com/Peertjeh/extremedimensions).

---

## ⚔ Eleven skills, earned by playing

| Combat | Gathering | Survival | Crafting | Other |
|---|---|---|---|---|
| Melee · Ranged · Defense | Mining · Foraging · Farming · Fishing | Exploration | Smithing · Arcana | Trading |

- You earn XP by doing what the skill is about: full hits on dangerous mobs, rare ores, natural trees, grown crops,
  catches in open water, new chunks, biomes and structures, smelting metal, brewing and enchanting, trading with
  villagers and bartering with piglins.
- **Slow on purpose**: about 400 hours take one skill from 0 to 100. The first levels come in minutes, the last ones in
  hours.
- **A total budget**: every level costs a little more as your total rises, up to a cap of 400. Nobody masters
  everything; you choose what you are.
- **No farms**: placed blocks, bonemeal, spawners, AFK pools, mob grinders and fishing pits give nothing.

## 🌳 Trees, specializations and abilities

![A skill tree](https://raw.githubusercontent.com/Peertjeh/In-Game-MMO/develop/docs/images/skill-screen.png)

- Every skill has a **trunk** of four bonuses that unlock by themselves at levels 5, 10, 15 and 20.
- At **level 25** you choose one of three **specializations**, at **level 75** a second. Thirty-three in all, from Blade
  and Sniper to Prospector, Beastmaster, Forgemaster, Hexer and Merchant.
- Every specialization has an **active ability** (`R`, or `Shift + R` for the second), four passives, a choice of two,
  and always **a price**.
- From level 25 every level gives a point. A full branch costs more than you will ever have.
- Some specializations of different skills **exclude each other**; some nodes need **another skill's level**.
- **Track** the skills you care about: a second hotbar beside yours shows their level and a green bar of their progress.

## 👑 Mastery

![Mastery](https://raw.githubusercontent.com/Peertjeh/In-Game-MMO/develop/docs/images/mastery.png)

Past level 100, ten Mastery ranks of about 40 hours each. At every rank you choose one of the skill's two perks, such as
Giant Slayer or Duelist, Mother Lode or Swift Pick.

## ✦ Synergies you discover yourself

![The Codex](https://raw.githubusercontent.com/Peertjeh/In-Game-MMO/develop/docs/images/codex.png)

Twelve hidden synergies between two skills at level 30: Counterstrike, Charge, High Ground, Deep Walker, Seafarer,
Herb Lore and more.

- Each one works only once **you** discovered it, the first time its situation happens in your play.
- Until then it is runes and a vague hint in your **Codex**.
- The server's **Chronicle** remembers who found each one first, and that makes the hint clearer for everyone else.

## 🤝 Playing together

- **Parties** (`/party`, or the Party button): up to five players, invited with a click.
- **The party bonus**: a little more XP for every party member near you who plays too, never for one standing idle.
- **Eight duo synergies** between two party members, one skill each: Shield Wall, Spotter, Pincer, Field Medic,
  Whetstone, Hearty Meal, Caravan and Market Day. Discovered together, with both names in the Chronicle.

## ☠ Deaths that cost you

![The Death Register](https://raw.githubusercontent.com/Peertjeh/In-Game-MMO/develop/docs/images/death-register.png)

- Every skill loses part of its progress. Your highest skill loses the most.
- **Death pressure** rises with every death and falls with every hour of play. Die again too soon, and you lose more,
  then whole levels or Mastery ranks.
- The Nether costs ×1.5, the End ×2, and Extreme Dimensions' Seam ×2.5.
- **Death fatigue**: for ten minutes after a death your bonuses work at half strength.
- The **Death Register** shows exactly what you would lose if you died now. After a death a summary shows what you did
  lose. Nothing comes back but new XP.

## ⚙ For server owners

- **Presets**: Hardcore (the default), Normal (half the death loss, no whole levels lost, 50% faster XP) or Custom.
- Every number is configurable:
  - `config/extremeprogression.json`;
  - an XP table per skill (other mods' blocks and mobs too);
  - skills on or off and their XP rates, ability cooldowns, hard requirements;
  - every synergy's strength.
- An **in-game settings screen** for admins, `/skills reload`, **XP events** (`/skills xprate 2 mining 60`), a
  **leaderboard**, `/skills inspect` and resets that wait for a confirmation.
- **TAB and Styled Chat**: `%extremeprogression:...%` placeholders through Text Placeholder API: a level badge in the
  player list, your best skill, death pressure and fatigue in the footer, an XP event in the header.
- Player data is saved atomically with backups, and old files are migrated forward, never reset.

## 📦 Requirements

- Minecraft **26.3**, Fabric Loader **0.19.5** or newer, **Fabric API**, Java **25**.
- On the server **and** on every client.
- Extreme Dimensions is optional; with it, the Seam counts for deaths and the inventory buttons sit side by side.
- Tested with Sodium, Iris, Mod Menu and Text Placeholder API.

## ❓ FAQ

- **Can I lose a specialization?** A skill below 25 or 75 makes it sleep. It wakes, with the same points, once the
  level is back.
- **Can I change my mind?** A respec costs 20% of the skill's XP and then waits seven days.
- **Does it work in singleplayer?** Yes. Everything happens on the integrated server.
- **Where is everything I can do?** Press `K` for the Character Sheet, or `/skills`.

*Source and issues: [GitHub](https://github.com/Peertjeh/In-Game-MMO) · License: MIT*
