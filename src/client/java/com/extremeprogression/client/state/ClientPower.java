package com.extremeprogression.client.state;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.extremeprogression.compat.TabPlaceholders;
import com.extremeprogression.config.Config;
import com.extremeprogression.network.PowerPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Every online player's power level as the server last sent it, and the badge drawn beside their name above their head
 * ({@code ✦312★}, as in the tablist).
 */
public final class ClientPower {
	private ClientPower() {}

	private static final Map<UUID, int[]> POWER = new ConcurrentHashMap<>();

	public static void apply(PowerPayload payload) {
		POWER.clear();
		for (PowerPayload.Entry e : payload.players()) {
			POWER.put(e.player(), new int[] {e.total(), e.mastery()});
		}
	}

	public static void clear() {
		POWER.clear();
	}

	/**
	 * The badge for this player's name tag, or null: unknown players, players at level 0, the option off, or a
	 * dimension where it is hidden.
	 */
	public static Component badge(UUID player) {
		int[] p = POWER.get(player);
		if (!Config.NAMETAG_POWER || p == null || p[0] <= 0 && p[1] <= 0 || hiddenHere()) {
			return null;
		}
		return TabPlaceholders.badge(p[0], p[1]);
	}

	/** Whether name-tag additions are hidden in this dimension (BattleHub draws its own). */
	public static boolean hiddenHere() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || Config.NAMETAG_HIDDEN_DIMENSIONS == null || Config.NAMETAG_HIDDEN_DIMENSIONS.isBlank()) {
			return false;
		}
		String here = mc.level.dimension().identifier().toString();
		return Arrays.stream(Config.NAMETAG_HIDDEN_DIMENSIONS.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT)).anyMatch(here::equals);
	}
}
