package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.SkeletonTests.check;

import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.death.DeathRules;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.progression.XpCurve;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.tree.TreeRules;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The balancing pass (milestone 10): the numbers the code arrives at, played through its own rules, against the
 * approved tables of design §4 and §7. A change to a default that moves the game's pace fails here first.
 */
public class BalanceTests {
	private static boolean within(double actual, double expected, double share) {
		return Math.abs(actual - expected) <= expected * share;
	}

	@GameTest
	public void masteringSkillsOneAfterAnotherTakesTheApprovedHours(GameTestHelper helper) {
		Config.reset();
		PlayerProgress p = new PlayerProgress();
		Skill[] order = {Skill.MINING, Skill.MELEE, Skill.FARMING, Skill.ARCANA};
		double[] expected = {425, 534, 731, 1017};
		double cumulative = 0;
		for (int i = 0; i < order.length; i++) {
			double hours = 0;
			while (p.get(order[i]).level < Config.MAX_LEVEL) {
				double need = Progression.xpToNext(p, order[i]) - p.get(order[i]).xp;
				hours += need / Config.XP_PER_HOUR;
				Progression.add(p, order[i], need);
			}
			cumulative += hours;
			check(helper, within(hours, expected[i], 0.01), "skill %s to 100 took %s hours, the design says ~%s", i + 1, Math.round(hours), expected[i]);
		}
		check(helper, within(cumulative, 2707, 0.01), "four skills to 100 took %s hours, the design says ~2707", Math.round(cumulative));
		check(helper, p.totalLevel() == 400, "four skills at 100 are not the cap of 400");
		helper.succeed();
	}

	@GameTest
	public void singleLevelsTakeTheApprovedTime(GameTestHelper helper) {
		Config.reset();
		int[] levels = {10, 25, 50, 75, 90, 99};
		double[] hours = {0.25, 1.1, 3.4, 6.7, 9, 10.5};
		for (int i = 0; i < levels.length; i++) {
			double h = XpCurve.base(levels[i]) / Config.XP_PER_HOUR;
			check(helper, within(h, hours[i], 0.05), "level %s takes %s hours, the design says ~%s", levels[i], h, hours[i]);
		}
		check(helper, within(XpCurve.hours(25), 10, 0.02) && within(XpCurve.hours(75), 186, 0.01), "the specializations' levels are not ~10 and ~186 hours");
		check(helper, Config.MASTERY_XP_PER_RANK / Config.XP_PER_HOUR == 40, "a Mastery rank is not 40 hours");
		helper.succeed();
	}

	@GameTest
	public void aRespecCostsTheApprovedLevels(GameTestHelper helper) {
		Config.reset();
		int[][] cases = {{100, 92}, {75, 69}};
		for (int[] c : cases) {
			PlayerProgress p = new PlayerProgress();
			Progression.setLevel(p, Skill.MINING, c[0]);
			Progression.remove(p, Skill.MINING, TreeRules.respecCost(p, Skill.MINING));
			int after = p.get(Skill.MINING).level;
			check(helper, Math.abs(after - c[1]) <= 1, "a respec at level %s leaves level %s, the design says ~%s", c[0], after, c[1]);
		}
		helper.succeed();
	}

	@GameTest
	public void aDeathAtLevel90CostsAtMostHalfALevel(GameTestHelper helper) {
		Config.reset();
		PlayerProgress p = new PlayerProgress();
		Progression.setLevel(p, Skill.MINING, 90);
		double level = Progression.xpToNext(p, Skill.MINING);
		p.get(Skill.MINING).xp = level * 0.999;
		double before = p.get(Skill.MINING).xp;
		DeathRules.apply(p, DeathRules.base(0), false);
		double lostHours = (before - p.get(Skill.MINING).xp) / Config.XP_PER_HOUR;
		// (one level at 90 is about 9 hours before the budget factor; alone at 90 the factor is ×1.09)
		check(helper, within(lostHours, 4.5 * 1.09, 0.05), "a death at the end of level 90 cost %s hours, the design says at most ~4.5 (×1.09)", lostHours);
		check(helper, p.get(Skill.MINING).level == 90, "a first death took a level");
		helper.succeed();
	}
}
