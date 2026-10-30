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
			// The id the death penalty knows is Extreme Dimensions' own (its SeamService.SEAM)
			String theirs;
			try {
				Object key = Class.forName("com.extremedimensions.seam.SeamService").getField("SEAM").get(null);
				theirs = ((net.minecraft.resources.ResourceKey<?>) key).identifier().toString();
			} catch (ReflectiveOperationException e) {
				throw helper.assertionException("Extreme Dimensions' Seam could not be read: %s", e);
			}
			check(helper, theirs.equals(SEAM), "Extreme Dimensions' Seam is %s, the death penalty knows %s", theirs, SEAM);
			check(helper, DeathRules.multiplier(SEAM, true) == 2.5, "a death in the Seam does not cost ×2.5");
			// (a test world has no Seam of its own; where it has, the dimension is the same)
			helper.getLevel().getServer().levelKeys().stream().filter(k -> k.identifier().toString().equals(SEAM)).findFirst()
					.ifPresent(k -> check(helper, helper.getLevel().getServer().getLevel(k) != null, "the Seam is listed but has no level"));
		}
		helper.succeed();
	}
}
