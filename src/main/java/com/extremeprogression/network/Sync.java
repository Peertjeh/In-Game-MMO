package com.extremeprogression.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.progression.Budget;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.skill.Skill;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Sends players their progression. */
public final class Sync {
	private Sync() {}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(ProgressPayload.TYPE, ProgressPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenSheetPayload.TYPE, OpenSheetPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ActivePayload.TYPE, ActivePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TreePayload.TYPE, TreePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AbilityPayload.TYPE, AbilityPayload.CODEC);
	}

	/** Opens the Character Sheet on the player's client. False when their client cannot (a client without the mod). */
	public static boolean openSheet(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, OpenSheetPayload.TYPE)) {
			return false;
		}
		send(player);
		ServerPlayNetworking.send(player, new OpenSheetPayload());
		return true;
	}

	/** The progression as the client sees it, with cooldowns and the respec wait counted from now. */
	public static ProgressPayload payload(PlayerProgress p, long gameTime, long now) {
		List<ProgressPayload.Entry> skills = new ArrayList<>();
		for (Skill s : Skill.values()) {
			SkillData d = p.get(s);
			Map<String, Integer> cooldowns = new LinkedHashMap<>();
			for (Map.Entry<String, Long> c : d.cooldowns.entrySet()) {
				long left = c.getValue() - gameTime;
				if (left > 0) {
					cooldowns.put(c.getKey(), (int) Math.min(Integer.MAX_VALUE, left));
				}
			}
			skills.add(new ProgressPayload.Entry(s.id, d.level, d.xp, Progression.xpToNext(p, s), d.masteryRanks, d.masteryXp, List.copyOf(d.specs),
					List.copyOf(d.bought), List.copyOf(d.suspended), Math.max(0, d.respecReadyAt - now), cooldowns));
		}
		int total = p.totalLevel();
		return new ProgressPayload(total, Config.TOTAL_LEVEL_CAP, Budget.factor(total), Config.MAX_LEVEL, Config.MASTERY_RANKS,
				Config.MASTERY_XP_PER_RANK, skills);
	}

	/** Sends the player their progression, if their client can receive it (a test player's cannot). */
	public static void send(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, ProgressPayload.TYPE)) {
			long gameTime = player.level().getServer().overworld().getGameTime();
			ServerPlayNetworking.send(player, payload(PlayerStore.get(player.getUUID()), gameTime, System.currentTimeMillis()));
		}
	}
}
