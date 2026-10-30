package com.extremeprogression.xp;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.network.XpEventPayload;
import com.extremeprogression.skill.Skill;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * XP events (design §11, like mcMMO's {@code /xprate}): an admin raises the XP rate for a while, for every skill or one.
 * An event ends by itself after its minutes, on {@code /skills xprate reset}, or when the server stops. A new event for
 * the same skills replaces the running one; an event for one skill and one for all multiply.
 */
public final class XpEvents {
	private XpEvents() {}

	/** One event: {@code skill} null for every skill; {@code endsAt} real milliseconds, 0 when it runs until reset. */
	public record Event(Skill skill, double multiplier, long endsAt) {}

	private static final List<Event> EVENTS = new ArrayList<>();

	/** The XP factor of all running events for this skill (1 when none). */
	public static double multiplier(Skill skill) {
		double out = 1;
		long now = System.currentTimeMillis();
		for (Event e : EVENTS) {
			if ((e.skill() == null || e.skill() == skill) && (e.endsAt() == 0 || e.endsAt() > now)) {
				out *= e.multiplier();
			}
		}
		return out;
	}

	public static List<Event> active() {
		return List.copyOf(EVENTS);
	}

	/** Starts an event (minutes 0: until reset), announced to everyone. */
	public static void start(MinecraftServer server, Skill skill, double multiplier, int minutes, String by) {
		EVENTS.removeIf(e -> e.skill() == skill);
		long ends = minutes > 0 ? System.currentTimeMillis() + minutes * 60_000L : 0;
		EVENTS.add(new Event(skill, multiplier, ends));
		Component what = skill == null ? Component.translatable("message.extremeprogression.xprate.all") : Component.translatable(skill.translationKey());
		String factor = format(multiplier);
		server.getPlayerList().broadcastSystemMessage(minutes > 0
				? Component.translatable("message.extremeprogression.xprate.started", factor, what, minutes).withColor(0xFFAA00)
				: Component.translatable("message.extremeprogression.xprate.started_open", factor, what).withColor(0xFFAA00), false);
		ExtremeProgression.LOGGER.info("{} started an XP event: x{} for {} ({} minutes)", by, factor, skill == null ? "every skill" : skill.id,
				minutes > 0 ? minutes : "until reset");
		sync(server);
	}

	/** Ends every event; false when none ran. */
	public static boolean reset(MinecraftServer server, String by) {
		if (EVENTS.isEmpty()) {
			return false;
		}
		EVENTS.clear();
		server.getPlayerList().broadcastSystemMessage(Component.translatable("message.extremeprogression.xprate.ended").withColor(0xFFAA00), false);
		ExtremeProgression.LOGGER.info("{} ended the XP events", by);
		sync(server);
		return true;
	}

	/** Every second: events whose time ran out end, announced. */
	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0 || EVENTS.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		List<Event> over = new ArrayList<>();
		for (Event e : EVENTS) {
			if (e.endsAt() != 0 && e.endsAt() <= now) {
				over.add(e);
			}
		}
		if (over.isEmpty()) {
			return;
		}
		EVENTS.removeAll(over);
		for (Event e : over) {
			Component what = e.skill() == null ? Component.translatable("message.extremeprogression.xprate.all") : Component.translatable(e.skill().translationKey());
			server.getPlayerList().broadcastSystemMessage(Component.translatable("message.extremeprogression.xprate.over", format(e.multiplier()), what)
					.withColor(0xFFAA00), false);
		}
		sync(server);
	}

	/** A server that stops ends its events. */
	public static void clear() {
		EVENTS.clear();
	}

	static String format(double multiplier) {
		String s = String.format(java.util.Locale.ROOT, "%.2f", multiplier).replaceAll("0+$", "").replaceAll("\\.$", "");
		return "×" + s;
	}

	public static void sync(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			send(p);
		}
	}

	public static void send(ServerPlayer p) {
		if (ServerPlayNetworking.canSend(p, XpEventPayload.TYPE)) {
			ServerPlayNetworking.send(p, XpEventPayload.now());
		}
	}
}
