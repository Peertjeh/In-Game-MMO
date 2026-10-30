# Extreme Progression

*A hardcore RPG progression overhaul for Minecraft 26.3 on Fabric.*

Ten playstyle skills, each with its own skill tree, three specializations and a Mastery layer. Synergies between skills
that you discover yourself. Slow, meaningful progression, and deaths that cost you for real, always shown up front.

> **Status:** in development. The design is approved; the code is being built milestone by milestone. So far all ten
> skills earn XP (with the rules against exploits), and their trunks, specializations, points and respec work, and all
> thirty specializations have their abilities, nodes and prices. The death system and the synergies follow.

- The full design: [docs/DESIGN.md](docs/DESIGN.md)
- Works on its own, and optionally together with [Extreme Dimensions](https://github.com/Peertjeh/extremedimensions).

## Requirements

- Minecraft **26.3**, Fabric Loader **0.19.5+**, Fabric API **0.162.0+**, Java **25**.
- The mod goes on the server **and** on every client.

## Building

```
./gradlew build
```

This also runs the server game tests. The client test runs with `./gradlew runClientGameTest` and saves screenshots
under `build/run/clientGameTest/screenshots/`. CI runs both on every push.
