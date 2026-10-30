package com.extremeprogression.gametest;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.skill.Specialization;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;

/** The mod loads on a dedicated server, standalone: Extreme Dimensions is only suggested, never required. */
public class SkeletonTests {
	static void check(GameTestHelper helper, boolean ok, String message, Object... args) {
		if (!ok) {
			throw helper.assertionException(message, args);
		}
	}

	@GameTest
	public void theModLoadsOnTheServer(GameTestHelper helper) {
		check(helper, FabricLoader.getInstance().isModLoaded(ExtremeProgression.MOD_ID), "%s is not loaded", ExtremeProgression.MOD_ID);
		check(helper, ExtremeProgression.id("melee").toString().equals("extremeprogression:melee"), "ids are not in the mod's namespace");
		helper.succeed();
	}

	/** Design §2: three specializations per skill; opposing skills and specialization conflicts go both ways. */
	@GameTest
	public void everySkillHasThreeSpecializationsAndThePairsMatch(GameTestHelper helper) {
		for (Skill s : Skill.values()) {
			check(helper, Specialization.of(s).size() == 3, "%s has %s specializations", s, Specialization.of(s).size());
			check(helper, s.opposite() == null || s.opposite().opposite() == s, "%s and its opposing skill do not match", s);
		}
		int conflicts = 0;
		for (Specialization p : Specialization.values()) {
			if (p.conflict() != null) {
				conflicts++;
				check(helper, p.conflict().conflict() == p, "the conflict of %s does not go both ways", p);
				check(helper, p.conflict().skill != p.skill, "%s conflicts with a specialization of its own skill", p);
			}
		}
		check(helper, conflicts == 10, "there are %s specializations in a conflict, not the approved 5 pairs", conflicts);
		helper.succeed();
	}

	@GameTest
	public void theModRunsWithoutExtremeDimensions(GameTestHelper helper) {
		// (the compatibility run loads it on purpose)
		if (!Boolean.getBoolean("extremeprogression.compat")) {
			check(helper, !FabricLoader.getInstance().isModLoaded("extremedimensions"), "the test server should run without Extreme Dimensions");
		}
		helper.succeed();
	}
}
