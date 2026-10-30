package com.extremeprogression.progression;

import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.skill.Skill;

/**
 * Earning and losing XP. Pure rules on a {@link PlayerProgress}: callers save and sync afterwards.
 *
 * <p>XP within a level is kept as an amount, and the XP a level needs is worked out from the current total, so the bar
 * of one skill fills more slowly as other skills rise (the budget factor, design §4).
 */
public final class Progression {
	private Progression() {}

	private static final double EPS = 1e-6;

	/** What an XP change did. */
	public record Change(double applied, int levelsGained, int levelsLost, int ranksGained, int ranksLost, boolean capped) {
		public static final Change NONE = new Change(0, 0, 0, 0, 0, false);
	}

	/** XP for the skill's next level at the player's current total; 0 at the maximum level. */
	public static double xpToNext(PlayerProgress p, Skill skill) {
		return XpCurve.base(p.get(skill).level) * Budget.factor(p.totalLevel());
	}

	/** Adds XP (or, for a negative amount, removes it as {@link #remove} does). */
	public static Change add(PlayerProgress p, Skill skill, double amount) {
		if (!Double.isFinite(amount) || amount == 0) {
			return Change.NONE;
		}
		if (amount < 0) {
			return remove(p, skill, -amount);
		}
		SkillData d = p.get(skill);
		double left = amount;
		double applied = 0;
		int levels = 0;
		int ranks = 0;
		boolean capped = false;
		while (left > EPS) {
			if (d.level >= Config.MAX_LEVEL) {
				// Past the maximum level XP goes into Mastery, until the last rank
				if (d.masteryRanks >= Config.MASTERY_RANKS) {
					break;
				}
				double take = Math.min(left, Config.MASTERY_XP_PER_RANK - d.masteryXp);
				d.masteryXp += take;
				left -= take;
				applied += take;
				if (d.masteryXp >= Config.MASTERY_XP_PER_RANK - EPS) {
					d.masteryRanks++;
					d.masteryXp = 0;
					ranks++;
				}
				continue;
			}
			double room = xpToNext(p, skill) - d.xp;
			if (Budget.atCap(p.totalLevel())) {
				// At the cap the bar still fills, but the level waits until there is room again
				double take = Math.min(left, Math.max(0, room));
				d.xp += take;
				applied += take;
				capped = true;
				break;
			}
			// (with a hair's breadth of margin: XP summed from many small gains never lands exactly on the need)
			if (left < room - EPS) {
				d.xp += left;
				applied += left;
				break;
			}
			// (room can be negative when the bar was over-full: the total dropped since, so the level costs less now)
			left -= Math.max(0, room);
			applied += Math.max(0, room);
			d.level++;
			d.xp = 0;
			levels++;
		}
		return new Change(applied, levels, 0, ranks, 0, capped);
	}

	/**
	 * Removes XP, in the reverse order it was earned: Mastery progress, then whole Mastery ranks, then the XP within
	 * the level, then whole levels. A skill never goes below level 0 with no XP.
	 */
	public static Change remove(PlayerProgress p, Skill skill, double amount) {
		if (!Double.isFinite(amount) || amount <= 0) {
			return Change.NONE;
		}
		SkillData d = p.get(skill);
		double left = amount;
		double applied = 0;
		int levels = 0;
		int ranks = 0;
		while (left > EPS) {
			if (d.level >= Config.MAX_LEVEL && (d.masteryXp > EPS || d.masteryRanks > 0)) {
				if (d.masteryXp <= EPS) {
					d.masteryRanks--;
					d.masteryXp = Config.MASTERY_XP_PER_RANK;
					ranks++;
				}
				double take = Math.min(left, d.masteryXp);
				d.masteryXp -= take;
				left -= take;
				applied += take;
			} else if (d.xp > EPS) {
				double take = Math.min(left, d.xp);
				d.xp -= take;
				left -= take;
				applied += take;
			} else if (d.level > 0) {
				d.level--;
				d.masteryXp = 0;
				levels++;
				// The level is lost, and with it all its XP: the bar of the level below is full again
				d.xp = xpToNext(p, skill);
			} else {
				d.xp = 0;
				break;
			}
		}
		return new Change(applied, 0, levels, 0, ranks, false);
	}

	/** Sets a level outright (an operator's command). The XP within the level starts at 0. Returns the old level. */
	public static int setLevel(PlayerProgress p, Skill skill, int level) {
		SkillData d = p.get(skill);
		int old = d.level;
		d.level = Math.max(0, Math.min(level, Config.MAX_LEVEL));
		d.xp = 0;
		if (d.level < Config.MAX_LEVEL) {
			d.masteryRanks = 0;
			d.masteryXp = 0;
		}
		return old;
	}
}
