package com.extremeprogression.xp;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.extremeprogression.antiexploit.AfkTracker;
import com.extremeprogression.antiexploit.Overfishing;
import com.extremeprogression.skill.Skill;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;

/**
 * Fishing XP for catches (design §2, Appendix A.7), with its own rules against AFK fishing:
 * <ul>
 * <li>only fishing in open water counts, as vanilla already requires for treasure: a small pit gives nothing;</li>
 * <li>you must have looked around or pressed a key in the last few minutes;</li>
 * <li>after ten catches in one spot the spot is overfished until you fish elsewhere.</li>
 * </ul>
 */
public final class FishingXp {
	private FishingXp() {}

	private static final Map<UUID, Overfishing> SPOTS = new HashMap<>();
	/** The tick of the last hint per player, so a hint shows at most once a minute. */
	private static final Map<UUID, Long> HINTED = new HashMap<>();

	/** A catch was reeled in (from the vanilla trigger for advancements). An empty catch is a hooked mob or block. */
	public static void caught(ServerPlayer player, FishingHook hook, Collection<ItemStack> items) {
		if (items.isEmpty()) {
			return;
		}
		long tick = player.level().getServer().getTickCount();
		if (!hook.isOpenWaterFishing()) {
			hint(player, tick, "message.extremeprogression.fishing.not_open_water");
			return;
		}
		if (AfkTracker.isStill(player.getUUID(), tick)) {
			return;
		}
		double base = 0;
		for (ItemStack stack : items) {
			base += XpTables.caught(stack) * Math.max(1, stack.getCount()) * com.extremeprogression.spec.Fishing.xp(player, stack);
		}
		Overfishing spot = SPOTS.computeIfAbsent(player.getUUID(), k -> new Overfishing());
		double share = spot.take(hook.getX(), hook.getY(), hook.getZ());
		if (share < 1) {
			hint(player, tick, "message.extremeprogression.fishing.overfished");
		}
		XpService.award(player, Skill.FISHING, base * share);
	}

	private static void hint(ServerPlayer player, long tick, String key) {
		Long last = HINTED.get(player.getUUID());
		if (last == null || tick - last >= 1200) {
			HINTED.put(player.getUUID(), tick);
			XpService.notice(player, Component.translatable(key), Skill.FISHING);
		}
	}

	public static void forget(UUID id) {
		SPOTS.remove(id);
		HINTED.remove(id);
	}
}
