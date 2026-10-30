package com.extremeprogression.death;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.antiexploit.AfkTracker;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.DeathData;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.network.DeathPayload;
import com.extremeprogression.network.Sync;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.spec.Active;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Deaths on the server (design §7): the loss, the death pressure and death fatigue, the record for the Death Register,
 * and the summary after respawning. The loss is written to the player's file at once, so a server that stops right
 * after a death keeps it, and it is never applied twice. Deaths in Creative or Spectator cost nothing.
 */
public final class DeathService {
	private DeathService() {}

	/** The server tick of each player's last counted death: a second one in the same tick is the same death. */
	private static final Map<UUID, Long> LAST_DEATH = new HashMap<>();
	/** The death each player still has to see the summary of (shown after respawning). */
	private static final Map<UUID, DeathData.Record> UNSEEN = new HashMap<>();
	/** The dimension each player was in at the last check: a change sends the new factor for "At Stake". */
	private static final Map<UUID, String> DIMENSION = new HashMap<>();

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				died(p, source);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			DeathData.Record seen = UNSEEN.remove(newPlayer.getUUID());
			Sync.send(newPlayer);
			sendHistory(newPlayer, seen);
		});
	}

	/** The dimension's factor where the player is now. */
	public static double multiplier(ServerPlayer p) {
		return DeathRules.multiplier(p.level().dimension().identifier().toString(), FabricLoader.getInstance().isModLoaded("extremedimensions"));
	}

	/** A player died: what it costs them, recorded, saved and told. Returns the record, or null when nothing counted. */
	public static DeathData.Record died(ServerPlayer p, DamageSource source) {
		if (p.isCreative() || p.isSpectator()) {
			return null;
		}
		MinecraftServer server = p.level().getServer();
		long tick = server.getTickCount();
		Long last = LAST_DEATH.get(p.getUUID());
		if (last != null && last == tick) {
			return null;
		}
		LAST_DEATH.put(p.getUUID(), tick);
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		DeathData death = progress.death;
		int pressure = death.pressure;
		double multiplier = multiplier(p);
		List<DeathData.Loss> losses = DeathRules.apply(progress, DeathRules.base(pressure) * multiplier, DeathRules.whole(pressure));

		DeathData.Record r = new DeathData.Record();
		r.time = System.currentTimeMillis();
		r.dimension = p.level().dimension().identifier().toString();
		r.x = p.getBlockX();
		r.y = p.getBlockY();
		r.z = p.getBlockZ();
		r.cause = source == null ? "" : source.getLocalizedDeathMessage(p).getString();
		r.pressure = pressure;
		r.multiplier = multiplier;
		r.losses = losses;
		death.history.add(r);
		while (death.history.size() > Math.max(0, Config.DEATH_HISTORY)) {
			death.history.remove(0);
		}
		death.deaths++;
		death.pressure = pressure + 1;
		death.activeTicks = 0;
		death.fatigueUntil = server.overworld().getGameTime() + Math.round(Config.DEATH_FATIGUE_MINUTES * 60 * 20);

		PlayerStore.markDirty(p.getUUID());
		PlayerStore.saveNow(p.getUUID());
		Active.forget(p.getUUID());
		UNSEEN.put(p.getUUID(), r);
		log(p, r);
		// (a respawn sends the summary; this message is for a player who looks at the chat on the death screen)
		p.sendSystemMessage(Component.translatable("message.extremeprogression.death.lost", lostSkills(r)), false);
		Sync.send(p);
		return r;
	}

	private static int lostSkills(DeathData.Record r) {
		return (int) r.losses.stream().filter(DeathData.Loss::any).count();
	}

	private static void log(ServerPlayer p, DeathData.Record r) {
		StringBuilder sb = new StringBuilder();
		for (DeathData.Loss l : r.losses) {
			if (l.any()) {
				sb.append(' ').append(l.skill).append(String.format(java.util.Locale.ROOT, " -%.0f XP", l.xp));
				if (l.levelAfter < l.levelBefore) {
					sb.append(" -").append(l.levelBefore - l.levelAfter).append(" level");
				}
				if (l.ranksAfter < l.ranksBefore) {
					sb.append(" -").append(l.ranksBefore - l.ranksAfter).append(" Mastery rank");
				}
				sb.append(';');
			}
		}
		ExtremeProgression.LOGGER.info("{} died in {} at pressure {} (x{}):{}", p.getName().getString(), r.dimension, r.pressure, r.multiplier, sb);
	}

	/** The history for the Death Register, and the death whose summary to show now (or null). */
	public static void sendHistory(ServerPlayer p, DeathData.Record summary) {
		if (ServerPlayNetworking.canSend(p, DeathPayload.TYPE)) {
			ServerPlayNetworking.send(p, new DeathPayload(PlayerStore.get(p.getUUID()).death.history, summary));
		}
	}

	// --- Pressure and fatigue --------------------------------------------------------------------

	/** Every second: an online player who is not AFK plays actively, and an hour of it lowers the death pressure by 1. */
	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		long tick = server.getTickCount();
		long hour = Math.max(1, Math.round(Config.DEATH_PRESSURE_DECAY_MINUTES * 60 * 20));
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			String dimension = p.level().dimension().identifier().toString();
			if (!dimension.equals(DIMENSION.put(p.getUUID(), dimension))) {
				Sync.send(p);
			}
			DeathData death = PlayerStore.get(p.getUUID()).death;
			if (death.pressure <= 0 || AfkTracker.isAfk(p.getUUID(), tick)) {
				continue;
			}
			death.activeTicks += 20;
			if (death.activeTicks >= hour) {
				death.activeTicks -= hour;
				death.pressure--;
				Sync.send(p);
			} else if (death.activeTicks % 1200 == 0) {
				// (once a minute, so the Death Register's countdown never runs on while you are AFK)
				Sync.send(p);
			}
			PlayerStore.markDirty(p.getUUID());
		}
	}

	/** Whether a player's death fatigue is on now. */
	public static boolean fatigued(ServerPlayer p) {
		return PlayerStore.get(p.getUUID()).death.fatigueUntil > p.level().getServer().overworld().getGameTime();
	}

	/** Ticks of death fatigue left (0 when none). */
	public static long fatigueLeft(PlayerProgress progress, long gameTime) {
		return Math.max(0, progress.death.fatigueUntil - gameTime);
	}

	/** Ticks of active play until the death pressure drops by 1 (0 at pressure 0). */
	public static long pressureDropsIn(PlayerProgress progress) {
		if (progress.death.pressure <= 0) {
			return 0;
		}
		return Math.max(0, Math.round(Config.DEATH_PRESSURE_DECAY_MINUTES * 60 * 20) - progress.death.activeTicks);
	}

	/** Every skill's loss as the next death here would cost it (for tests; the client works it out itself). */
	public static List<DeathData.Loss> atStake(PlayerProgress copy, int pressure, double multiplier) {
		return DeathRules.apply(copy, DeathRules.base(pressure) * multiplier, DeathRules.whole(pressure));
	}

	public static Skill highest(PlayerProgress p) {
		return DeathRules.highest(p);
	}

	public static void forget(UUID id) {
		LAST_DEATH.remove(id);
		UNSEEN.remove(id);
		DIMENSION.remove(id);
	}
}
