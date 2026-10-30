package com.extremeprogression.gametest;

import com.extremeprogression.ExtremeProgression;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The client starts with the mod and opens a world. Every step is saved as a screenshot (uploaded by CI) for review;
 * later milestones add the mod's own screens here.
 */
public class ClientShots implements FabricClientGameTest {
	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new AssertionError(message);
		}
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		check(FabricLoader.getInstance().isModLoaded(ExtremeProgression.MOD_ID), ExtremeProgression.MOD_ID + " is not loaded on the client");
		ctx.waitTicks(40);
		ctx.takeScreenshot("00_title");
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			ctx.takeScreenshot("01_world");
		}
	}
}
