package com.extremeprogression.xp;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.extremeprogression.antiexploit.AfkTracker;
import com.extremeprogression.antiexploit.DiminishingReturns;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.network.Sync;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.skill.Skill;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Every XP gain of the mod goes through here (design §3, §12), after its source applied its own rules (placed blocks,
 * bonemeal, open water and so on):
 * <ol>
 * <li>Creative and Spectator mode earn nothing;</li>
 * <li>an AFK player earns nothing;</li>
 * <li>diminishing returns shrink gains above the threshold;</li>
 * <li>the XP multiplier;</li>
 * <li>the XP goes into the skill, with a short notice on a level-up.</li>
 * </ol>
 * Changed players are marked dirty for the next save and synced once at the end of the tick.
 */
public final class XpService {
	private XpService() {}

	private static final Map<UUID, EnumMap<Skill, DiminishingReturns>> WINDOWS = new HashMap<>();
	private static final Set<UUID> TO_SYNC = new LinkedHashSet<>();

	/** Gives the player XP from a gathering or other source. Returns the XP that reached the skill. */
	public static double award(ServerPlayer player, Skill skill, double base) {
		if (!(base > 0) || !Double.isFinite(base) || player.isCreative() || player.isSpectator()) {
			return 0;
		}
		long tick = player.level().getServer().getTickCount();
		if (AfkTracker.isAfk(player.getUUID(), tick)) {
			return 0;
		}
		double share = WINDOWS.computeIfAbsent(player.getUUID(), k -> new EnumMap<>(Skill.class))
				.computeIfAbsent(skill, k -> new DiminishingReturns()).take(tick, base);
		PlayerProgress p = PlayerStore.get(player.getUUID());
		// (an opposing skill above 25 and higher than this one takes its share, design §2)
		double xp = base * share * Config.XP_MULTIPLIER * (1 - com.extremeprogression.progression.Opposing.penalty(p, skill));
		Progression.Change c = Progression.add(p, skill, xp);
		if (c.applied() > 0 || c.levelsGained() > 0 || c.ranksGained() > 0) {
			PlayerStore.markDirty(player.getUUID());
			TO_SYNC.add(player.getUUID());
		}
		if (c.levelsGained() > 0) {
			notice(player, Component.translatable("message.extremeprogression.level_up", Component.translatable(skill.translationKey()),
					p.get(skill).level), skill);
		} else if (c.ranksGained() > 0) {
			notice(player, Component.translatable("message.extremeprogression.mastery_up", Component.translatable(skill.translationKey()),
					p.get(skill).masteryRanks), skill);
		}
		return c.applied();
	}

	/** A short notice on the action bar, in the skill's category colour: the only HUD the mod shows (design §9). */
	public static void notice(ServerPlayer player, Component text, Skill skill) {
		player.sendSystemMessage(text.copy().withColor(skill.category.accent), true);
	}

	/** At the end of every tick: players whose progression changed get it once. */
	public static void tick(MinecraftServer server) {
		if (TO_SYNC.isEmpty()) {
			return;
		}
		for (UUID id : TO_SYNC) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				Sync.send(p);
			}
		}
		TO_SYNC.clear();
	}

	/** The XP of this skill counted in the player's diminishing-returns window (for tests and the GUI). */
	public static double recent(ServerPlayer player, Skill skill) {
		EnumMap<Skill, DiminishingReturns> w = WINDOWS.get(player.getUUID());
		DiminishingReturns d = w == null ? null : w.get(skill);
		return d == null ? 0 : d.recent(player.level().getServer().getTickCount());
	}

	public static void forget(UUID id) {
		WINDOWS.remove(id);
		TO_SYNC.remove(id);
	}

	public static void clear() {
		WINDOWS.clear();
		TO_SYNC.clear();
	}
}
