package com.extremeprogression.gametest;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.client.state.ClientProgress;
import com.extremeprogression.network.ProgressPayload;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

/**
 * The client starts with the mod, opens a world and receives its progression from the server. Every step is saved as a
 * screenshot (uploaded by CI) for review; later milestones add the mod's own screens here.
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

			// The progression arrives on joining: ten skills at level 0
			ctx.runOnClient(mc -> {
				ProgressPayload now = ClientProgress.current();
				check(now != null, "the client got no progression on joining");
				check(now.skills().size() == 10, "the client got " + now.skills().size() + " skills, not 10");
				check(now.total() == 0 && now.cap() == 400, "a new player's total is " + now.total() + " of " + now.cap());
			});
			int before = ctx.computeOnClient(mc -> ClientProgress.updates());

			// An operator's change on the server reaches the client
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().get(0);
				s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "skills admin set " + p.getName().getString() + " mining 61");
			});
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				check(ClientProgress.updates() > before, "the client was not sent the change");
				ProgressPayload.Entry mining = ClientProgress.skill("mining");
				check(mining != null && mining.level() == 61, "the client shows Mining at " + (mining == null ? "nothing" : mining.level()) + ", not 61");
				check(ClientProgress.current().total() == 61, "the client's total is " + ClientProgress.current().total() + ", not 61");
			});
			ctx.takeScreenshot("02_after_admin_set");
		}
	}
}
