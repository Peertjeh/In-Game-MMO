package com.extremeprogression.mastery;

import java.util.List;

import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.spec.Specs;
import com.extremeprogression.tree.TreeRules;
import com.extremeprogression.trunk.Trunks;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Mastery perks (design §4 and §6): a perk per Mastery rank, chosen in order, rank I first. A choice is final while the
 * rank is held; a lost rank takes its perk with it (§7), and winning the rank back asks for a choice again.
 */
public final class Mastery {
	private Mastery() {}

	/** The perks of the ranks still held, rank I first. */
	public static List<String> held(SkillData d) {
		return d.perks.subList(0, Math.min(d.perks.size(), d.masteryRanks));
	}

	/** A rank lost takes its perk: the perks beyond the ranks held go, the last one first. */
	public static void trim(SkillData d) {
		while (d.perks.size() > d.masteryRanks) {
			d.perks.remove(d.perks.size() - 1);
		}
	}

	/** The next rank (1-based) still waiting for its perk, or 0 when none is. */
	public static int waiting(SkillData d) {
		trim(d);
		return d.perks.size() < d.masteryRanks ? d.perks.size() + 1 : 0;
	}

	/** Chooses the perk ("a" or "b") of the next rank waiting for one. */
	public static TreeRules.Result choose(PlayerProgress p, Skill skill, String track) {
		SkillData d = p.get(skill);
		if (MasteryPerk.of(skill, track) == null || waiting(d) == 0) {
			return TreeRules.Result.NO_PERK_WAITING;
		}
		d.perks.add(track);
		return TreeRules.Result.OK;
	}

	/** How many ranks went into this perk. */
	public static int ranks(SkillData d, MasteryPerk perk) {
		int n = 0;
		for (String t : held(d)) {
			if (t.equals(perk.track)) {
				n++;
			}
		}
		return n;
	}

	/**
	 * The perk's bonus for this player now, as a fraction (0.03 for three ranks): 1% a rank, times the skill's
	 * {@link Specs#power} (death fatigue, an opposing skill); Exploration's not in heavy armor, like its other bonuses.
	 */
	public static double bonus(Entity e, MasteryPerk perk) {
		SkillData d = Specs.skillData(e, perk.skill);
		if (d == null || d.level < Config.MAX_LEVEL) {
			return 0;
		}
		if (perk.skill == Skill.EXPLORATION && e instanceof LivingEntity l && Trunks.heavyArmor(l)) {
			return 0;
		}
		int n = ranks(d, perk);
		return n == 0 ? 0 : n * Config.MASTERY_PERK_STEP * Specs.power(e, perk.skill);
	}
}
