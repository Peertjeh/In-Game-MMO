# Server configs: TAB and Styled Chat

The shared configs of one Fabric server that runs **Extreme Dimensions**, **Extreme Progression** and **BattleHub**
together, as in Extreme Dimensions' `server/` folder, with Extreme Progression's lines added. Copy them into the
server's `config/` folder (`config/tab/` for TAB), then `/tab reload` (and `/styledchat reload`).

Both need **Text Placeholder API** (`placeholder-api`) on the server: every `%extremeprogression:...%`,
`%extremedimensions:...%` and `%player:...%` placeholder comes through it. Without it they show as plain text.

## What Extreme Progression adds

- **The player list (survival)**: behind each name, after Extreme Dimensions' curse badge, the level badge
  `✦<total level>`. Its colour brightens every hundred levels (gray, green, aqua, gold, light purple at the cap), with a
  gold `★` once the player has Mastery.
- **The header (survival)**: the preset, the synergies the server has found, and a running XP event.
- **The footer (survival)**: the player's level out of the cap, their best skill, their death pressure (green, yellow,
  red from 2), and their death fatigue while it lasts.
- **Chat (Styled Chat)**: the level badge behind the name. Hovering over the name shows the level, the best skill, the
  death pressure and the specializations.

## Every placeholder

| Placeholder | What it says |
|---|---|
| `%extremeprogression:total%` | the total level (`312`) |
| `%extremeprogression:mastery%` | all Mastery ranks |
| `%extremeprogression:highest%` | the best skill and its level (`Mining 61`, `Arcana M2`); empty at level 0 |
| `%extremeprogression:highest_skill%`, `%extremeprogression:highest_level%` | the same, apart |
| `%extremeprogression:level_<skill>%`, `%extremeprogression:mastery_<skill>%` | one skill's level and Mastery ranks (`level_mining`) |
| `%extremeprogression:specs%` | the specializations in use (`Blade, Prospector`) |
| `%extremeprogression:pressure%`, `%extremeprogression:deaths%` | the death pressure, the deaths |
| `%extremeprogression:fatigue%` | death fatigue left (`9:57`); empty when none |
| `%extremeprogression:synergies%` | the synergies the player discovered (`3/12`) |
| `%extremeprogression:badge%` | the level badge |
| `%extremeprogression:preset%` | Hardcore, Normal or Custom |
| `%extremeprogression:cap%` | the total level cap (`400`) |
| `%extremeprogression:xp_event%` | a running XP event (`×2 Mining · 30 min`); empty when none |
| `%extremeprogression:discoveries%` | the synergies anyone on the server found first (`5/12`) |
| `%extremeprogression:top%` | the best player and their total (`Steve 312`), checked once a minute |

The level badge also shows beside every player's name above their head (Extreme Progression draws it on each client,
no TAB needed), except in BattleHub's lobby and matches: `nametag_hidden_dimensions` and `nametag_power` in
`config/extremeprogression.json`.

The values are in English (a tablist is the same for everyone) and are worked out once a second (`placeholder_refresh_ticks`);
`placeholders: false` in `config/extremeprogression.json` empties them all.
