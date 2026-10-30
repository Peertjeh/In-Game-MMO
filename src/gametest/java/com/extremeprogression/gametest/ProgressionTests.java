package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.SkeletonTests.check;

import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.progression.Budget;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.progression.XpCurve;
import com.extremeprogression.skill.Skill;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** The approved formulas (design §4 and Appendix A.1), and earning and losing XP by them. */
public class ProgressionTests {
	static void near(GameTestHelper helper, double actual, double expected, double tolerance, String what) {
		check(helper, Math.abs(actual - expected) <= tolerance, "%s: expected %s, got %s", what, expected, actual);
	}

	@GameTest
	public void theCurveMatchesTheApprovedTable(GameTestHelper helper) {
		Config.reset();
		near(helper, XpCurve.cumulative(25), 100134, 1, "XP to level 25");
		near(helper, XpCurve.cumulative(50), 632878, 1, "XP to level 50");
		near(helper, XpCurve.cumulative(75), 1860900, 1, "XP to level 75");
		near(helper, XpCurve.cumulative(100), 4000000, 0.01, "XP to level 100");
		near(helper, XpCurve.base(0), 19, 1, "XP for level 0 → 1");
		near(helper, XpCurve.base(99), 105519, 1, "XP for level 99 → 100");
		near(helper, XpCurve.hours(10), 0.875, 0.01, "hours to level 10");
		near(helper, XpCurve.hours(100), 400, 1e-9, "hours to level 100");
		check(helper, XpCurve.base(100) == 0 && XpCurve.base(-1) == 0, "the curve gives XP beyond its ends");
		helper.succeed();
	}

	@GameTest
	public void theBudgetFactorRisesWithTheTotal(GameTestHelper helper) {
		Config.reset();
		near(helper, Budget.factor(0), 1, 1e-12, "factor at total 0");
		near(helper, Budget.factor(212), 1.4994, 0.0005, "factor at total 212");
		near(helper, Budget.factor(300), 2, 1e-12, "factor at total 300");
		near(helper, Budget.factor(900), 10, 1e-12, "factor at total 900");
		check(helper, !Budget.atCap(399) && Budget.atCap(400), "the cap is not at 400");
		// The approved table of masters one after another (design §4): ~425, ~534, ~731 and ~1017 hours
		PlayerProgress p = new PlayerProgress();
		Skill[] four = {Skill.MELEE, Skill.MINING, Skill.FISHING, Skill.ARCANA};
		double[] expected = {425, 534, 731, 1017};
		for (int i = 0; i < four.length; i++) {
			double xp = 0;
			for (int l = 0; l < 100; l++) {
				xp += Progression.xpToNext(p, four[i]);
				Progression.add(p, four[i], Progression.xpToNext(p, four[i]) - p.get(four[i]).xp);
			}
			check(helper, p.get(four[i]).level == 100, "master %s ended at level %s", i + 1, p.get(four[i]).level);
			near(helper, xp / Config.XP_PER_HOUR, expected[i], 1.5, "hours for master " + (i + 1));
		}
		helper.succeed();
	}

	@GameTest
	public void xpLevelsUpByTheBudget(GameTestHelper helper) {
		Config.reset();
		PlayerProgress p = new PlayerProgress();
		// One skill alone: while it climbs, the total is its own level
		double sum = 0;
		for (int l = 0; l < 25; l++) {
			sum += XpCurve.base(l) * Budget.factor(l);
		}
		Progression.Change c = Progression.add(p, Skill.MELEE, sum);
		SkillData melee = p.get(Skill.MELEE);
		check(helper, melee.level == 25 && melee.xp < 1e-3, "exactly enough XP ends at level %s with %s XP", melee.level, melee.xp);
		check(helper, c.levelsGained() == 25, "the change reports %s levels", c.levelsGained());
		Progression.add(p, Skill.MELEE, 100);
		near(helper, melee.xp, 100, 1e-9, "XP within the level");
		near(helper, Progression.xpToNext(p, Skill.MINING), XpCurve.base(0) * Budget.factor(25), 1e-9, "another skill's first level at total 25");
		helper.succeed();
	}

	@GameTest
	public void removingXpGoesBackTheWayItCame(GameTestHelper helper) {
		Config.reset();
		PlayerProgress p = new PlayerProgress();
		Progression.setLevel(p, Skill.MELEE, 25);
		Progression.add(p, Skill.MELEE, 100);
		Progression.Change r = Progression.remove(p, Skill.MELEE, 101);
		SkillData melee = p.get(Skill.MELEE);
		check(helper, melee.level == 24 && r.levelsLost() == 1, "removing past the level's start left level %s", melee.level);
		near(helper, melee.xp, Progression.xpToNext(p, Skill.MELEE) - 1, 1e-6, "the level below is full but for 1 XP");
		Progression.remove(p, Skill.MELEE, 1e12);
		check(helper, melee.level == 0 && melee.xp == 0, "a skill went below level 0");
		check(helper, Progression.add(p, Skill.MELEE, -5).applied() == 0, "XP was taken from an empty skill");
		helper.succeed();
	}

	@GameTest
	public void theCapStopsLevelsButNotTheBar(GameTestHelper helper) {
		Config.reset();
		PlayerProgress p = new PlayerProgress();
		for (Skill s : new Skill[] {Skill.MELEE, Skill.RANGED, Skill.DEFENSE, Skill.MINING}) {
			Progression.setLevel(p, s, 100);
		}
		Progression.Change c = Progression.add(p, Skill.FISHING, 1e9);
		SkillData fishing = p.get(Skill.FISHING);
		check(helper, fishing.level == 0 && c.capped(), "a level came at the cap: level %s", fishing.level);
		near(helper, fishing.xp, Progression.xpToNext(p, Skill.FISHING), 1e-6, "the bar at the cap");
		// Room below the cap again: the full bar becomes a level with the next XP
		Progression.setLevel(p, Skill.MINING, 99);
		Progression.add(p, Skill.FISHING, 1);
		check(helper, fishing.level == 1, "below the cap the full bar did not level up");
		helper.succeed();
	}

	@GameTest
	public void masteryComesAfterTheMaximum(GameTestHelper helper) {
		Config.reset();
		PlayerProgress p = new PlayerProgress();
		Progression.setLevel(p, Skill.ARCANA, 100);
		Progression.add(p, Skill.ARCANA, Config.MASTERY_XP_PER_RANK);
		SkillData arcana = p.get(Skill.ARCANA);
		check(helper, arcana.masteryRanks == 1 && arcana.masteryXp < 1e-6, "400,000 XP gave %s ranks", arcana.masteryRanks);
		Progression.Change all = Progression.add(p, Skill.ARCANA, 1e9);
		check(helper, arcana.masteryRanks == 10, "Mastery passed rank 10: %s", arcana.masteryRanks);
		near(helper, all.applied(), 9 * Config.MASTERY_XP_PER_RANK, 1e-3, "XP taken up to the last rank");
		Progression.remove(p, Skill.ARCANA, 1);
		check(helper, arcana.masteryRanks == 9, "removing did not go through Mastery first");
		check(helper, p.totalLevel() == 100, "Mastery counts toward the total level");
		helper.succeed();
	}
}
