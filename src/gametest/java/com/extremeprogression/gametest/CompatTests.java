package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.SkeletonTests.check;

import com.extremeprogression.death.DeathRules;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Extreme Dimensions, when it is loaded (the compatibility run of CI loads it, design §1): the Seam is a real dimension
 * of that mod under the id the death penalty knows, so a death there costs ×2.5. In the compatibility run the mod must
 * be there, or the run tested nothing.
 */
public class CompatTests {
	private static final String SEAM = "extremedimensions:the_seam";

	@GameTest
	public void theSeamCountsWithExtremeDimensions(GameTestHelper helper) {
		boolean ed = FabricLoader.getInstance().isModLoaded("extremedimensions");
		if (Boolean.getBoolean("extremeprogression.compat")) {
			check(helper, ed, "the compatibility run did not load Extreme Dimensions");
		}
		if (ed) {
			boolean seam = helper.getLevel().getServer().levelKeys().stream().anyMatch(k -> k.identifier().toString().equals(SEAM));
			check(helper, seam, "Extreme Dimensions has no dimension %s", SEAM);
			check(helper, DeathRules.multiplier(SEAM, true) == 2.5, "a death in the Seam does not cost ×2.5");
		}
		helper.succeed();
	}
}
