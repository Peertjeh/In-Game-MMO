# Changelog

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
