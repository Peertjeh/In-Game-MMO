# Extreme Progression

*A hardcore RPG progression overhaul for Minecraft 26.3 on Fabric.*

Eleven playstyle skills, each with its own skill tree, three specializations and a Mastery layer. Synergies between skills
that you discover yourself. Slow, meaningful progression, and deaths that cost you for real, always shown up front.

![The Character Sheet](docs/images/character-sheet.png)

- **Eleven skills**: Melee, Ranged, Defense, Mining, Foraging, Farming, Fishing, Exploration, Smithing, Arcana and
  Trading, earned by playing, with rules against farms and AFK.
- **Trees**: a trunk of four bonuses per skill, and thirty-three specializations with an ability, passives, a choice and
  a price each.
- **Mastery** past level 100, ten ranks with a perk each.
- **Twelve hidden synergies**, a personal Codex and the server's Chronicle.
- **Playing together**: parties with an XP bonus for members near who play too, and eight duo synergies you discover
  with a party member.
- **Deaths that cost you**: death pressure, the dimensions' factors, death fatigue, the Death Register and At Stake.
- **For server owners**: presets, data files for every number, an in-game settings screen, XP events, a leaderboard and
  admin commands, and placeholders for TAB and Styled Chat ([server/README.md](server/README.md)).

More: the page text in [docs/modrinth/description.md](docs/modrinth/description.md), the full design in
[docs/DESIGN.md](docs/DESIGN.md), what changed in [CHANGELOG.md](CHANGELOG.md), and how to try it in
[docs/TESTING.md](docs/TESTING.md). Works on its own, and together with
[Extreme Dimensions](https://github.com/Peertjeh/extremedimensions).

## Requirements

- Minecraft **26.3**, Fabric Loader **0.19.5+**, Fabric API **0.162.0+**, Java **25**.
- The mod goes on the server **and** on every client.

## Building

```
./gradlew build
```

This also runs the server game tests. The client test runs with `./gradlew runClientGameTest` and saves screenshots
under `build/run/clientGameTest/screenshots/`. CI runs both on every push, and a compatibility run with Extreme
Dimensions, Sodium, Iris and Mod Menu. A new version in `gradle.properties` is released by CI once all three pass.

## License

[MIT](LICENSE).
