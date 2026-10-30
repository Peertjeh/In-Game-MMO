package com.extremeprogression.compat;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.network.PowerPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The power level beside the name above a player's head (in multiplayer): every client is told every online player's
 * total level and Mastery ranks, and draws the same badge as the tablist's behind the name ({@code ✦312★}). Checked
 * twice a second; sent only when something changed (a level, a player joining or leaving).
 */
public final class PowerTags {
	private PowerTags() {}

	private static List<PowerPayload.Entry> last = List.of();

	public static List<PowerPayload.Entry> now(MinecraftServer s) {
		List<PowerPayload.Entry> out = new ArrayList<>();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			PlayerProgress progress = PlayerStore.get(p.getUUID());
			out.add(new PowerPayload.Entry(p.getUUID(), progress.totalLevel(), progress.totalMasteryRanks()));
		}
		return out;
	}

	public static void tick(MinecraftServer s) {
		if (s.getTickCount() % 10 != 0) {
			return;
		}
		List<PowerPayload.Entry> now = now(s);
		if (now.equals(last)) {
			return;
		}
		last = now;
		PowerPayload payload = new PowerPayload(now);
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(p, PowerPayload.TYPE)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	/** A player joined: they get everyone's at once (the others with the next check, which sees them as new). */
	public static void joined(ServerPlayer p) {
		if (ServerPlayNetworking.canSend(p, PowerPayload.TYPE)) {
			ServerPlayNetworking.send(p, new PowerPayload(now(p.level().getServer())));
		}
	}

	public static void clear() {
		last = List.of();
	}
}
