package com.extremeprogression.progression;

import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.skill.Skill;

/**
 * Opposing skills (design §2, mechanism c): Melee ↔ Ranged, Defense ↔ Exploration, Smithing ↔ Arcana. Once the
 * opposing skill is above level 25 and higher than this one, this skill earns less XP, growing with the level gap up to
 * −60% at a gap of 50, and its passive bonuses work at 75%. Pure rules: the client shows them with the same numbers.
 */
public final class Opposing {
	private Opposing() {}

	/** The share of XP this skill loses at these levels (0 when the opposing skill does not press on it). */
	public static double penalty(int level, int opposingLevel) {
		if (opposingLevel <= Config.OPPOSING_START_LEVEL || opposingLevel <= level) {
			return 0;
		}
		return Config.OPPOSING_MAX_PENALTY * Math.min(1, (opposingLevel - level) / (double) Config.OPPOSING_FULL_GAP);
	}

	public static double penalty(PlayerProgress p, Skill skill) {
		Skill other = skill.opposite();
		return other == null ? 0 : penalty(p.get(skill).level, p.get(other).level);
	}

	/** How strongly this skill's passive bonuses work: 1, or 75% while its opposing skill presses on it. */
	public static double passivePower(int level, int opposingLevel) {
		return penalty(level, opposingLevel) > 0 ? Config.OPPOSING_PASSIVE_POWER : 1;
	}

	public static double passivePower(PlayerProgress p, Skill skill) {
		Skill other = skill.opposite();
		return other == null ? 1 : passivePower(p.get(skill).level, p.get(other).level);
	}
}
