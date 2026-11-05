package com.extremeprogression.admin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.network.AdminPayload;
import com.extremeprogression.network.Sync;
import com.extremeprogression.party.Parties;
import com.extremeprogression.party.Party;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.server.Perms;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.tree.TreeRules;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Players screen for admins (1.6.0, as Extreme Dimensions' 3.27): every player the world knows, online or not; for
 * the one picked, their overview and every skill, with a level (or past the maximum a Mastery rank) taken off or put on
 * at a click, a free respec, every ability ready, death pressure and fatigue cleared, and a reset of a skill or of
 * everything.
 *
 * <p>Only who holds {@code extremeprogression.admin.players} gets the data or may change anything, and only who holds
 * {@code extremeprogression.admin.reset} may reset. The server checks every request; every change is written to the
 * server log with who made it, and a player online is told and sees it at once. A player offline finds it so when they
 * come back (their file is written at once).
 */
public final class AdminService {
	private AdminService() {}

	/** At most this many players are listed (online first, then by name): enough for any server, small enough for one payload. */
	public static final int LIST_MAX = 500;
	/** The offline players' part of the list, worked out from their files at most every 30 seconds. */
	private static List<JsonObject> offline = null;
	private static long offlineAt = 0;
	private static int offlineFiles = -1;

	/** Whether this player may open the Players screen and change another's progression. */
	public static boolean allowed(ServerPlayer p) {
		return Perms.has(p, Perms.Node.PLAYERS);
	}

	/** Whether this player may reset a skill or everything. */
	public static boolean mayReset(ServerPlayer p) {
		return Perms.has(p, Perms.Node.RESET);
	}

	/** Sends the Players screen's data to an admin; anyone else is told no (and gets nothing). */
	public static void send(ServerPlayer op, String selected, boolean open) {
		if (!allowed(op)) {
			if (open) {
				op.sendSystemMessage(Component.translatable("message.extremeprogression.admin.refused").withColor(0xFF5555), false);
			}
			return;
		}
		if (ServerPlayNetworking.canSend(op, AdminPayload.TYPE)) {
			ServerPlayNetworking.send(op, new AdminPayload(data(op, selected).toString(), open));
		}
	}

	/** A change asked for from the screen: made if the asker may, then the screen gets fresh data. Returns why not, or null. */
	public static String act(ServerPlayer op, String target, String action, String skill, int amount) {
		if (!allowed(op)) {
			op.sendSystemMessage(Component.translatable("message.extremeprogression.admin.refused").withColor(0xFF5555), false);
			return "message.extremeprogression.admin.refused";
		}
		String why = change(op, target, action, skill, amount);
		if (why != null) {
			op.sendSystemMessage(Component.translatable(why).withColor(0xFF5555), false);
		}
		send(op, target, false);
		return why;
	}

	// --- The data --------------------------------------------------------------------------------

	private static String nameOf(UUID id, PlayerProgress p) {
		return p.name == null || p.name.isEmpty() ? id.toString().substring(0, 8) : p.name;
	}

	private static JsonObject row(UUID id, String name, boolean online, PlayerProgress p) {
		JsonObject o = new JsonObject();
		o.addProperty("id", id.toString());
		o.addProperty("name", name);
		o.addProperty("online", online);
		o.addProperty("total", p.totalLevel());
		o.addProperty("mastery", p.totalMasteryRanks());
		return o;
	}

	/** Every player: online ones as they are now, then the others from their files (kept for 30 seconds). */
	static List<JsonObject> players(MinecraftServer server) {
		List<JsonObject> out = new ArrayList<>();
		List<UUID> online = new ArrayList<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			online.add(p.getUUID());
			out.add(row(p.getUUID(), p.getName().getString(), true, PlayerStore.get(p.getUUID())));
		}
		out.sort(Comparator.comparing(o -> o.get("name").getAsString(), String.CASE_INSENSITIVE_ORDER));
		long now = System.currentTimeMillis();
		List<UUID> known = PlayerStore.known();
		if (offline == null || now - offlineAt > 30_000 || known.size() != offlineFiles) {
			List<JsonObject> rows = new ArrayList<>();
			offlineFiles = known.size();
			for (UUID id : known) {
				PlayerProgress p = PlayerStore.peek(id);
				rows.add(row(id, nameOf(id, p), false, p));
			}
			rows.sort(Comparator.comparing(o -> o.get("name").getAsString(), String.CASE_INSENSITIVE_ORDER));
			offline = rows;
			offlineAt = now;
		}
		for (JsonObject o : offline) {
			if (out.size() >= LIST_MAX) {
				break;
			}
			if (!online.contains(UUID.fromString(o.get("id").getAsString()))) {
				out.add(o);
			}
		}
		return out.size() > LIST_MAX ? new ArrayList<>(out.subList(0, LIST_MAX)) : out;
	}

	/** The screen's data as this admin would be sent it (for tests). */
	public static JsonObject dataFor(ServerPlayer op, String selected) {
		return data(op, selected);
	}

	/** The screen's data: the list, and the player picked (or none). */
	static JsonObject data(ServerPlayer op, String selected) {
		MinecraftServer server = op.level().getServer();
		JsonObject root = new JsonObject();
		root.addProperty("reset", mayReset(op));
		root.addProperty("cap", Config.TOTAL_LEVEL_CAP);
		root.addProperty("max", Config.MAX_LEVEL);
		root.addProperty("ranks", Config.MASTERY_RANKS);
		JsonArray list = new JsonArray();
		players(server).forEach(list::add);
		root.add("players", list);
		UUID id = parse(selected);
		PlayerProgress p = id == null ? null : progress(server, id);
		if (p != null) {
			ServerPlayer online = server.getPlayerList().getPlayer(id);
			JsonObject s = row(id, online != null ? online.getName().getString() : nameOf(id, p), online != null, p);
			s.addProperty("pressure", p.death.pressure);
			s.addProperty("deaths", p.death.deaths);
			s.addProperty("fatigue", com.extremeprogression.death.DeathService.fatigueLeft(p, server.overworld().getGameTime()) / 20);
			s.addProperty("synergies", p.synergies.size());
			Party party = Parties.of(id);
			s.addProperty("party", party == null ? "" : party.name(party.leader));
			JsonArray skills = new JsonArray();
			for (Skill sk : Skill.values()) {
				SkillData d = p.get(sk);
				JsonObject o = new JsonObject();
				o.addProperty("id", sk.id);
				o.addProperty("level", d.level);
				o.addProperty("ranks", d.masteryRanks);
				o.addProperty("free", TreeRules.free(d));
				o.addProperty("earned", TreeRules.earned(d));
				JsonArray specs = new JsonArray();
				d.specs.forEach(specs::add);
				o.add("specs", specs);
				skills.add(o);
			}
			s.add("skills", skills);
			root.add("selected", s);
		}
		return root;
	}

	private static UUID parse(String id) {
		try {
			return id == null || id.isEmpty() ? null : UUID.fromString(id);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** A player's progression to look at, online or not; null for a player the world does not know. */
	private static PlayerProgress progress(MinecraftServer server, UUID id) {
		if (server.getPlayerList().getPlayer(id) != null || PlayerStore.loaded(id)) {
			return PlayerStore.get(id);
		}
		return PlayerStore.known().contains(id) ? PlayerStore.peek(id) : null;
	}

	// --- The changes -----------------------------------------------------------------------------

	private static String change(ServerPlayer op, String target, String action, String skillId, int amount) {
		MinecraftServer server = op.level().getServer();
		if ("select".equals(action)) {
			return null;
		}
		UUID id = parse(target);
		if (id == null || progress(server, id) == null) {
			return "message.extremeprogression.admin.no_player";
		}
		Skill skill = skillId == null || skillId.isEmpty() ? null : Skill.byId(skillId);
		String by = op.getName().getString();
		switch (action) {
			case "level" -> {
				if (skill == null || amount == 0) {
					return "message.extremeprogression.admin.unknown";
				}
				int by10 = Math.max(-Config.MAX_LEVEL, Math.min(Config.MAX_LEVEL, amount));
				modify(server, id, p -> level(p, skill, by10), Component.translatable("message.extremeprogression.admin_level", Component.translatable(skill.translationKey())),
						skill.id + (by10 > 0 ? " +" : " ") + by10, by);
			}
			case "respec" -> {
				if (skill == null) {
					return "message.extremeprogression.admin.unknown";
				}
				PlayerProgress look = progress(server, id);
				if (look.get(skill).specs.isEmpty() && look.get(skill).bought.isEmpty()) {
					return "message.extremeprogression.admin.nothing";
				}
				modify(server, id, p -> TreeRules.respec(p, skill, System.currentTimeMillis(), true),
						Component.translatable("message.extremeprogression.admin_respec", Component.translatable(skill.translationKey())), skill.id + ": free respec", by);
			}
			case "cooldowns" -> modify(server, id, p -> {
				for (Skill s : Skill.values()) {
					p.get(s).cooldowns.clear();
				}
			}, Component.translatable("message.extremeprogression.admin_cooldowns"), "all ability cooldowns reset", by);
			case "clear_deaths" -> modify(server, id, p -> {
				p.death.pressure = 0;
				p.death.fatigueUntil = 0;
			}, Component.translatable("message.extremeprogression.admin_deaths"), "death pressure and fatigue cleared", by);
			case "reset_skill", "reset_all" -> {
				if (!mayReset(op)) {
					return "message.extremeprogression.admin.refused";
				}
				if ("reset_skill".equals(action) && skill == null) {
					return "message.extremeprogression.admin.unknown";
				}
				com.extremeprogression.command.AdminCommands.reset(server, id, "reset_all".equals(action) ? null : skill, by);
				offline = null;
			}
			default -> {
				return "message.extremeprogression.admin.unknown";
			}
		}
		return null;
	}

	/**
	 * Up or down by this many levels: past the maximum level a plus adds Mastery ranks, and a minus takes Mastery ranks
	 * first. Changing a level outright starts its XP at 0, as {@code /skills admin set} does.
	 */
	public static void level(PlayerProgress p, Skill s, int by) {
		SkillData d = p.get(s);
		if (by > 0 && d.level >= Config.MAX_LEVEL) {
			d.masteryRanks = Math.min(Config.MASTERY_RANKS, d.masteryRanks + by);
			d.masteryXp = 0;
		} else if (by < 0 && d.masteryRanks > 0) {
			d.masteryRanks = Math.max(0, d.masteryRanks + by);
			d.masteryXp = 0;
			com.extremeprogression.mastery.Mastery.trim(d);
		} else {
			Progression.setLevel(p, s, Math.max(0, Math.min(Config.MAX_LEVEL, d.level + by)));
		}
	}

	/** Changes a player's progression, online or not: synced and told for one online, their file written for one offline. */
	private static void modify(MinecraftServer server, UUID id, Consumer<PlayerProgress> change, Component told, String logged, String by) {
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		PlayerProgress p = progress(server, id);
		change.accept(p);
		if (online != null) {
			PlayerStore.markDirty(id);
			Sync.send(online);
			com.extremeprogression.death.DeathService.sendHistory(online, null);
			online.sendSystemMessage(told.copy().withColor(0xFFAA00), false);
		} else {
			PlayerStore.replace(id, p);
			PlayerStore.unload(id);
			offline = null;
		}
		ExtremeProgression.LOGGER.info("{} changed the progression of {} in the Players screen: {}", by, nameOf(id, p), logged);
	}
}
