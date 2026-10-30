package com.extremeprogression.ability;

import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.network.AbilityPayload;
import com.extremeprogression.network.Sync;
import com.extremeprogression.skill.Specialization;
import com.extremeprogression.tree.TreeRules;
import com.extremeprogression.xp.XpService;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The ability key on the server (design §6): finds the ability the player means, checks its rank and cooldown, starts
 * the cooldown and sets the ability off. The abilities' own effects are added with the specializations (milestone 6),
 * through {@link #register}.
 */
public final class AbilityService {
	private AbilityService() {}

	/** What pressing the key did. */
	public enum Result { NONE, NO_RANK, COOLDOWN, USED }

	/** The effect of one specialization's active ability at a rank (1–5). */
	public interface Ability {
		void activate(ServerPlayer player, int rank);
	}

	private static final java.util.Map<Specialization, Ability> ABILITIES = new java.util.EnumMap<>(Specialization.class);

	public static void register(Specialization spec, Ability ability) {
		ABILITIES.put(spec, ability);
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(AbilityPayload.TYPE, (payload, context) -> use(context.player(), payload.second()));
	}

	/** Ticks of cooldown after using a specialization's ability. */
	public static long cooldownTicks(Specialization spec) {
		return spec.cooldownMinutes * 60L * 20L;
	}

	public static Result use(ServerPlayer player, boolean second) {
		PlayerProgress p = PlayerStore.get(player.getUUID());
		int index = second ? 1 : 0;
		Specialization spec = Triggers.resolve(player, skill -> TreeRules.active(p.get(skill), index));
		if (spec == null) {
			XpService.notice(player, Component.translatable(second ? "message.extremeprogression.ability.none_second" : "message.extremeprogression.ability.none"),
					com.extremeprogression.skill.Skill.MELEE);
			return Result.NONE;
		}
		SkillData d = p.get(spec.skill);
		Component name = Component.translatable(spec.translationKey() + ".ability");
		int rank = TreeRules.abilityRank(d, spec);
		if (rank == 0) {
			XpService.notice(player, Component.translatable("message.extremeprogression.ability.no_rank", name), spec.skill);
			return Result.NO_RANK;
		}
		long now = player.level().getServer().overworld().getGameTime();
		Long ready = d.cooldowns.get(spec.id);
		if (ready != null && ready > now) {
			long seconds = (ready - now + 19) / 20;
			XpService.notice(player, Component.translatable("message.extremeprogression.ability.cooldown", name, seconds / 60, String.format("%02d", seconds % 60)),
					spec.skill);
			return Result.COOLDOWN;
		}
		d.cooldowns.put(spec.id, now + cooldownTicks(spec));
		PlayerStore.markDirty(player.getUUID());
		Sync.send(player);
		Ability ability = ABILITIES.get(spec);
		if (ability != null) {
			ability.activate(player, rank);
		}
		XpService.notice(player, Component.translatable("message.extremeprogression.ability.used", name, rank), spec.skill);
		return Result.USED;
	}
}
