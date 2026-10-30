package com.extremeprogression.spec;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.extremeprogression.network.ActivePayload;
import com.extremeprogression.skill.Specialization;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The active abilities in effect: each lasts a number of ticks, or until it is used up (the next hit, the next shot).
 * Kept in memory only; none lasts longer than half a minute.
 */
public final class Active {
	private Active() {}

	/** One ability in effect: its rank, the server tick it ends, and a number for the ability's own use. */
	public static final class State {
		public final Specialization spec;
		public final int rank;
		final long until;
		/** A combo, the healing still to come: whatever the ability counts. */
		public double value;

		State(Specialization spec, int rank, long until) {
			this.spec = spec;
			this.rank = rank;
			this.until = until;
		}
	}

	private static final Map<UUID, EnumMap<Specialization, State>> ACTIVE = new HashMap<>();

	/** Sets an ability off for this many ticks, replacing one already in effect. */
	public static State start(ServerPlayer p, Specialization spec, int rank, int ticks) {
		State s = new State(spec, rank, p.level().getServer().getTickCount() + ticks);
		ACTIVE.computeIfAbsent(p.getUUID(), k -> new EnumMap<>(Specialization.class)).put(spec, s);
		sync(p);
		return s;
	}

	/** The ability in effect for this player, or null. */
	public static State get(Entity e, Specialization spec) {
		if (!(e instanceof ServerPlayer p)) {
			return null;
		}
		EnumMap<Specialization, State> map = ACTIVE.get(p.getUUID());
		State s = map == null ? null : map.get(spec);
		return s != null && s.until > p.level().getServer().getTickCount() ? s : null;
	}

	public static boolean on(Entity e, Specialization spec) {
		return get(e, spec) != null;
	}

	/** Ends an ability early: it was used up. */
	public static void end(ServerPlayer p, Specialization spec) {
		EnumMap<Specialization, State> map = ACTIVE.get(p.getUUID());
		if (map != null && map.remove(spec) != null) {
			sync(p);
		}
	}

	/** Every tick: the abilities whose time is up end. */
	public static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		for (Iterator<Map.Entry<UUID, EnumMap<Specialization, State>>> it = ACTIVE.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, EnumMap<Specialization, State>> e = it.next();
			if (e.getValue().values().removeIf(s -> s.until <= now)) {
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				if (p != null) {
					sync(p);
				}
			}
			if (e.getValue().isEmpty()) {
				it.remove();
			}
		}
	}

	private static void sync(ServerPlayer p) {
		if (!ServerPlayNetworking.canSend(p, ActivePayload.TYPE)) {
			return;
		}
		Map<String, Integer> ticks = new LinkedHashMap<>();
		EnumMap<Specialization, State> map = ACTIVE.get(p.getUUID());
		long now = p.level().getServer().getTickCount();
		if (map != null) {
			map.forEach((spec, s) -> {
				if (s.until > now) {
					ticks.put(spec.id, (int) (s.until - now));
				}
			});
		}
		ServerPlayNetworking.send(p, new ActivePayload(ticks));
	}

	public static void forget(UUID id) {
		ACTIVE.remove(id);
	}
}
