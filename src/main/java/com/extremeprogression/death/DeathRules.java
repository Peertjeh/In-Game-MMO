package com.extremeprogression.death;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.config.Config;
import com.extremeprogression.data.DeathData;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.tree.TreeRules;

/**
 * What a death costs (design §7). Pure rules on a {@link PlayerProgress}: the server applies them to the player's own
 * progression, the client to a copy to show what is at stake.
 * <ul>
 * <li><b>Layers 1 and 2:</b> every skill loses a share of its current step: the progress toward the next Mastery rank
 * at the maximum level, else the XP within its level. Never more: a death in these layers never takes a level.</li>
 * <li><b>The share</b> is {@code base × (the skill's level / the highest level) × the dimension's factor}, at most all
 * of the step: your highest skill the hardest.</li>
 * <li><b>Layer 3</b>, from death pressure 2: the highest skill also loses one whole Mastery rank, or with none one whole
 * level (its points set aside, its specializations dormant below 25 and 75).</li>
 * </ul>
 */
public final class DeathRules {
	private DeathRules() {}

	/** The base share of the step at a death pressure: 50% at 0, 100% from 1. */
	public static double base(int pressure) {
		return pressure >= 1 ? Config.DEATH_BASE_PRESSURED : Config.DEATH_BASE;
	}

	/** Whether a death at this pressure also takes a whole level or Mastery rank (layer 3). */
	public static boolean whole(int pressure) {
		return pressure >= Config.DEATH_WHOLE_LOSS_PRESSURE;
	}

	/** The dimension's factor: Overworld ×1, Nether ×1.5, End ×2, the Seam ×2.5 (only with Extreme Dimensions). */
	public static double multiplier(String dimension, boolean extremeDimensions) {
		return switch (dimension) {
			case "minecraft:the_nether" -> Config.DEATH_NETHER;
			case "minecraft:the_end" -> Config.DEATH_END;
			case "extremedimensions:the_seam" -> extremeDimensions ? Config.DEATH_SEAM : 1;
			default -> 1;
		};
	}

	/** The skill counted as the highest: the highest level, then the most Mastery ranks, then the most XP. */
	public static Skill highest(PlayerProgress p) {
		Skill best = null;
		for (Skill s : Skill.values()) {
			SkillData d = p.get(s);
			if (best == null) {
				best = s;
				continue;
			}
			SkillData b = p.get(best);
			if (d.level > b.level || d.level == b.level && (d.masteryRanks > b.masteryRanks
					|| d.masteryRanks == b.masteryRanks && d.masteryXp + d.xp > b.masteryXp + b.xp)) {
				best = s;
			}
		}
		return best;
	}

	/** The fraction of its step a skill at this level loses: the share × (its level / the highest level), at most all. */
	public static double fraction(int level, int highest, double share) {
		// (with every skill at level 0, each counts as the highest)
		double ratio = highest <= 0 ? 1 : (double) level / highest;
		return Math.max(0, Math.min(1, share * ratio));
	}

	/**
	 * Applies a death: {@code share} is the base times the dimension's factor, {@code whole} whether layer 3 counts.
	 * Returns what every skill lost (skills that lost nothing included, with zeros).
	 */
	public static List<DeathData.Loss> apply(PlayerProgress p, double share, boolean whole) {
		Skill top = highest(p);
		int highest = p.get(top).level;
		List<DeathData.Loss> out = new ArrayList<>();
		for (Skill s : Skill.values()) {
			SkillData d = p.get(s);
			DeathData.Loss loss = new DeathData.Loss();
			loss.skill = s.id;
			loss.levelBefore = d.level;
			loss.ranksBefore = d.masteryRanks;
			loss.mastery = d.level >= Config.MAX_LEVEL;
			double step = loss.mastery ? d.masteryXp : d.xp;
			double lost = step * fraction(d.level, highest, share);
			if (lost > 0) {
				Progression.remove(p, s, Math.min(lost, step));
			}
			loss.xp = lost;
			if (whole && s == top) {
				wholeStep(p, s);
			}
			loss.levelAfter = d.level;
			loss.ranksAfter = d.masteryRanks;
			out.add(loss);
		}
		return out;
	}

	/** Layer 3: one Mastery rank (its perk with it), or with none one whole level, at the same point of the level below. */
	private static void wholeStep(PlayerProgress p, Skill s) {
		SkillData d = p.get(s);
		if (d.masteryRanks > 0) {
			d.masteryRanks--;
			return;
		}
		if (d.level <= 0) {
			return;
		}
		double need = Progression.xpToNext(p, s);
		double at = need > 0 ? d.xp / need : 0;
		d.level--;
		d.masteryXp = 0;
		d.xp = at * Progression.xpToNext(p, s);
		TreeRules.settle(p, s);
	}
}
