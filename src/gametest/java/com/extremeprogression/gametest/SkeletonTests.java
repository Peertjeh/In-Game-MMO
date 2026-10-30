package com.extremeprogression.gametest;

import com.extremeprogression.ExtremeProgression;

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

	@GameTest
	public void theModRunsWithoutExtremeDimensions(GameTestHelper helper) {
		check(helper, !FabricLoader.getInstance().isModLoaded("extremedimensions"), "the test server should run without Extreme Dimensions");
		helper.succeed();
	}
}
