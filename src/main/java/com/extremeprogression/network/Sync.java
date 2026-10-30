package com.extremeprogression.network;

import java.util.ArrayList;
import java.util.List;

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
	}

	public static ProgressPayload payload(PlayerProgress p) {
		List<ProgressPayload.Entry> skills = new ArrayList<>();
		for (Skill s : Skill.values()) {
			SkillData d = p.get(s);
			skills.add(new ProgressPayload.Entry(s.id, d.level, d.xp, Progression.xpToNext(p, s), d.masteryRanks, d.masteryXp));
		}
		int total = p.totalLevel();
		return new ProgressPayload(total, Config.TOTAL_LEVEL_CAP, Budget.factor(total), Config.MAX_LEVEL, Config.MASTERY_RANKS,
				Config.MASTERY_XP_PER_RANK, skills);
	}

	/** Sends the player their progression, if their client can receive it (a test player's cannot). */
	public static void send(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, ProgressPayload.TYPE)) {
			ServerPlayNetworking.send(player, payload(PlayerStore.get(player.getUUID())));
		}
	}
}
