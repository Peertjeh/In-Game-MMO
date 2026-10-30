# The balancing pass (milestone 10)

The numbers the code arrives at, played through its own rules, against the approved tables of the design. The game
tests in `BalanceTests` check every row below, so a change to a default that moves the pace fails CI.

## What the code arrives at

| What | Design (§4, §7) | The code |
|---|---|---|
| Hours to level 25 / 75 / 100 (the first skill) | ~10 / ~186 / ~400 | 10.0 / 186.1 / 400.0 |
| One level at 10 / 25 / 50 / 75 / 90 / 99 | ~15 min / 1.1 / 3.4 / 6.7 / 9 / 10.5 h | 15 min / 1.10 / 3.42 / 6.67 / 9.02 / 10.55 h |
| Mastering skills one after another (hours each) | ~425, ~534, ~731, ~1017 | 425, 534, 731, 1018 |
| All four, the cap of 400 | ~2707 hours | 2707 hours |
| A Mastery rank | ≈ 40 hours | 40 hours (400,000 XP) |
| A respec at level 100 / 75 | to ~92 / ~69 | to 92 / 69 |
| A first death at the end of level 90 | at most ~4.5 hours | 4.9 hours, which is 4.5 × the budget factor of ×1.09 |

Everything matches. The last row only differs by the budget factor, which the design's example leaves out.

## Observations (💡, for the owner to decide; nothing was changed)

- **Mastery perks are small.** Ten ranks of about 40 hours each give at most +10% in one perk. The design asked for
  "small perks", so the default stays 1% a rank. `mastery_perk_step` (0.02 for 2%) raises it without code.
- **An opposing skill presses at once.** Melee 26 against Ranged 0 is a gap of 26, so Ranged earns 31% less from the
  first point above 25. This follows the approved rule (from level 25, up to −60% at a gap of 50). A gentler start would
  be a new option.
- **Two deaths within an hour hurt a lot.** From death pressure 1 the base is 100%, so the highest skill loses all
  progress in its level. At level 90 that is up to ~9.8 hours. This is approved; the Normal preset halves it.
- **XP events do not speed up diminishing returns.** The window counts the XP before the multipliers, so an XP event (or
  the Normal preset's ×1.5) never makes a player hit the threshold sooner. That seems right.
