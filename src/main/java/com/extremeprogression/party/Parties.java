package com.extremeprogression.party;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.antiexploit.AfkTracker;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.data.SafeFiles;
import com.extremeprogression.network.PartyPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Parties (design §16): players who play together. Anyone can invite a player who is in no party; an invitation stands
 * for two minutes, and accepting it makes a party of the two (the inviter leads) or joins the inviter's. The leader can
 * remove members and hand the lead on; when the leader leaves, the longest member leads, and a party of one is no party.
 *
 * <p>Party members within {@link Config#PARTY_RANGE} blocks who earn XP of their own give each other the
 * {@linkplain #bonus party bonus}, and their duo synergies. Parties are kept in {@code <world>/extremeprogression/
 * parties.json}, written at once on every change (they are rare), atomically and with backups like the player files.
 */
public final class Parties {
	private Parties() {}

	/** What parties.json holds. */
	public static class PartyData {
		public int schema = 1;
		public List<Party> parties = new ArrayList<>();
	}

	/** The outcome of a party action; anything but OK is told to the player who tried. */
	public enum Result {
		OK, OFF, SELF, NOT_ONLINE, NO_PARTY, NOT_LEADER, IN_A_PARTY, THEY_ARE_IN_A_PARTY, ALREADY_MEMBER, FULL, NO_INVITE, NOT_A_MEMBER;

		public String key() {
			return "message.extremeprogression.party." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** An invitation: from whom, and the server tick it lapses. */
	private record Invite(UUID from, String fromName, long expires) {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static PartyData data = new PartyData();
	private static Path file = null;
	private static final Map<UUID, Party> BY_MEMBER = new HashMap<>();
	/** Open invitations: for every invited player, from whom, the newest last. */
	private static final Map<UUID, LinkedHashMap<UUID, Invite>> INVITES = new HashMap<>();
	/** The server tick each player last earned XP of their own. */
	private static final Map<UUID, Long> EARNED = new HashMap<>();
	/** Every member's total level and Mastery ranks when last online. */
	private static final Map<UUID, int[]> POWER = new HashMap<>();
	/** What each player was sent last, so that only changes go out. */
	private static final Map<UUID, PartyPayload> SENT = new HashMap<>();

	private static final int GOOD = 0x55FF55, INFO = 0xFFFF55, BAD = 0xFF5555;

	// --- The file ----------------------------------------------------------------------------------

	public static void open(Path worldRoot) {
		file = worldRoot.resolve(ExtremeProgression.MOD_ID).resolve("parties.json");
		PartyData read = SafeFiles.readWithFallback(file, Config.PLAYER_BACKUPS, text -> GSON.fromJson(text, PartyData.class), "party file");
		data = read == null ? new PartyData() : read;
		if (data.parties == null) {
			data.parties = new ArrayList<>();
		}
		// (a damaged file: no member in two parties, the leader a member, nobody alone)
		Set<String> seen = new LinkedHashSet<>();
		for (Iterator<Party> it = data.parties.iterator(); it.hasNext();) {
			Party p = it.next();
			if (p == null || p.members == null) {
				it.remove();
				continue;
			}
			if (p.names == null) {
				p.names = new LinkedHashMap<>();
			}
			p.members.removeIf(m -> m == null || !seen.add(m));
			if (p.members.size() < 2) {
				it.remove();
				continue;
			}
			if (p.id == null || p.id.isEmpty()) {
				p.id = UUID.randomUUID().toString();
			}
			if (p.leader == null || !p.members.contains(p.leader)) {
				p.leader = p.members.get(0);
			}
		}
		index();
	}

	public static void close() {
		file = null;
		data = new PartyData();
		BY_MEMBER.clear();
		INVITES.clear();
		EARNED.clear();
		SENT.clear();
		POWER.clear();
	}

	private static void index() {
		BY_MEMBER.clear();
		for (Party p : data.parties) {
			for (String m : p.members) {
				try {
					BY_MEMBER.put(UUID.fromString(m), p);
				} catch (IllegalArgumentException e) {
					// (not a UUID: never a member)
				}
			}
		}
	}

	private static void save() {
		index();
		if (file == null) {
			return;
		}
		try {
			SafeFiles.writeAtomically(file, GSON.toJson(data), Config.PLAYER_BACKUPS);
		} catch (IOException e) {
			ExtremeProgression.LOGGER.error("Could not save the parties", e);
		}
	}

	/** For tests: no parties, invitations or recent XP (on disk too). */
	public static void clearForTests() {
		data.parties.clear();
		INVITES.clear();
		EARNED.clear();
		SENT.clear();
		save();
	}

	// --- Who is with whom --------------------------------------------------------------------------

	/** The player's party, or null. */
	public static Party of(UUID player) {
		return BY_MEMBER.get(player);
	}

	public static List<Party> all() {
		return data.parties;
	}

	/** Whether two different players are in the same party (and parties are on). */
	public static boolean together(Entity a, Entity b) {
		if (!Config.PARTIES || a == null || b == null || a == b) {
			return false;
		}
		Party p = of(a.getUUID());
		return p != null && p == of(b.getUUID());
	}

	/** The player's party members online in the same dimension within this many blocks (never the player). */
	public static List<ServerPlayer> near(ServerPlayer player, double range) {
		List<ServerPlayer> out = new ArrayList<>();
		Party party = Config.PARTIES ? of(player.getUUID()) : null;
		if (party == null) {
			return out;
		}
		MinecraftServer server = player.level().getServer();
		for (String m : party.members) {
			ServerPlayer other = online(server, m);
			if (other != null && other != player && other.level() == player.level() && !other.isSpectator() && other.distanceToSqr(player) <= range * range) {
				out.add(other);
			}
		}
		return out;
	}

	private static ServerPlayer online(MinecraftServer server, String uuid) {
		try {
			return server.getPlayerList().getPlayer(UUID.fromString(uuid));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// --- The bonus ---------------------------------------------------------------------------------

	/** The player earned XP of their own just now (counted before any bonus). */
	public static void earned(ServerPlayer p) {
		EARNED.put(p.getUUID(), (long) p.level().getServer().getTickCount());
	}

	/** Whether a party member counts for the others' bonus now: earning XP of their own, not idle, not in Creative. */
	public static boolean active(ServerPlayer p) {
		long tick = p.level().getServer().getTickCount();
		Long at = EARNED.get(p.getUUID());
		return at != null && tick - at <= Math.round(Config.PARTY_ACTIVE_SECONDS * 20) && !AfkTracker.isAfk(p.getUUID(), tick) && !p.isCreative()
				&& !p.isSpectator();
	}

	/** How many party members give this player the bonus now. */
	public static int helping(ServerPlayer p) {
		int n = 0;
		for (ServerPlayer other : near(p, Config.PARTY_RANGE)) {
			if (active(other)) {
				n++;
			}
		}
		return n;
	}

	/** The share more XP this player earns now for playing together (0.1 for +10%). */
	public static double bonus(ServerPlayer p) {
		if (!Config.PARTIES) {
			return 0;
		}
		return Math.min(Config.PARTY_BONUS_MAX, helping(p) * Config.PARTY_BONUS_PER_MEMBER);
	}

	// --- Actions -----------------------------------------------------------------------------------

	/** Invites a player into the inviter's party (or into a new one with the inviter leading). */
	public static Result invite(ServerPlayer from, ServerPlayer to) {
		if (!Config.PARTIES) {
			return Result.OFF;
		}
		if (from == to) {
			return Result.SELF;
		}
		Party mine = of(from.getUUID());
		if (mine != null && mine.has(to.getUUID())) {
			return Result.ALREADY_MEMBER;
		}
		if (of(to.getUUID()) != null) {
			return Result.THEY_ARE_IN_A_PARTY;
		}
		if (mine != null && mine.size() >= Config.PARTY_MAX_SIZE) {
			return Result.FULL;
		}
		long expires = from.level().getServer().getTickCount() + Math.round(Config.PARTY_INVITE_SECONDS * 20);
		LinkedHashMap<UUID, Invite> theirs = INVITES.computeIfAbsent(to.getUUID(), k -> new LinkedHashMap<>());
		theirs.remove(from.getUUID());
		theirs.put(from.getUUID(), new Invite(from.getUUID(), name(from), expires));
		String fromName = name(from);
		// (the command this mod's parties got: /party, or /skills party where another mod has /party)
		String command = "/" + com.extremeprogression.command.PartyCommand.root();
		MutableComponent accept = button("message.extremeprogression.party.accept_button", command + " accept " + fromName, GOOD);
		MutableComponent decline = button("message.extremeprogression.party.decline_button", command + " decline " + fromName, BAD);
		to.sendSystemMessage(Component.translatable("message.extremeprogression.party.invited_you", from.getDisplayName(),
				Math.round(Config.PARTY_INVITE_SECONDS)).withColor(INFO).append(" ").append(accept).append(" ").append(decline), false);
		from.sendSystemMessage(Component.translatable("message.extremeprogression.party.invited", to.getDisplayName()).withColor(GOOD), false);
		send(to, true);
		return Result.OK;
	}

	private static MutableComponent button(String key, String command, int color) {
		return Component.translatable(key).withStyle(s -> s.withColor(color).withUnderlined(true).withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
	}

	/** Accepts an invitation: the one from this player, or the newest when the name is null. */
	public static Result accept(ServerPlayer p, String fromName) {
		if (!Config.PARTIES) {
			return Result.OFF;
		}
		if (of(p.getUUID()) != null) {
			return Result.IN_A_PARTY;
		}
		Invite invite = take(p, fromName);
		if (invite == null) {
			return Result.NO_INVITE;
		}
		MinecraftServer server = p.level().getServer();
		Party party = of(invite.from());
		if (party != null && party.size() >= Config.PARTY_MAX_SIZE) {
			return Result.FULL;
		}
		if (party == null) {
			party = new Party();
			party.id = UUID.randomUUID().toString();
			party.leader = invite.from().toString();
			party.members.add(invite.from().toString());
			party.names.put(invite.from().toString(), invite.fromName());
			party.created = System.currentTimeMillis();
			data.parties.add(party);
		}
		party.members.add(p.getUUID().toString());
		party.names.put(p.getUUID().toString(), name(p));
		INVITES.remove(p.getUUID());
		save();
		tell(server, party, Component.translatable("message.extremeprogression.party.joined", p.getDisplayName()).withColor(GOOD));
		ExtremeProgression.LOGGER.info("{} joined the party of {}", name(p), party.name(party.leader));
		sendAll(server, party);
		return Result.OK;
	}

	/** Turns an invitation down. */
	public static Result decline(ServerPlayer p, String fromName) {
		Invite invite = take(p, fromName);
		if (invite == null) {
			return Result.NO_INVITE;
		}
		ServerPlayer from = p.level().getServer().getPlayerList().getPlayer(invite.from());
		if (from != null) {
			from.sendSystemMessage(Component.translatable("message.extremeprogression.party.declined", p.getDisplayName()).withColor(INFO), false);
		}
		p.sendSystemMessage(Component.translatable("message.extremeprogression.party.you_declined", invite.fromName()).withColor(INFO), false);
		send(p, true);
		return Result.OK;
	}

	/** Takes an open invitation out (the one from this name, or the newest), or null. */
	private static Invite take(ServerPlayer p, String fromName) {
		LinkedHashMap<UUID, Invite> theirs = INVITES.get(p.getUUID());
		if (theirs == null) {
			return null;
		}
		long tick = p.level().getServer().getTickCount();
		theirs.values().removeIf(i -> i.expires() < tick);
		Invite found = null;
		for (Invite i : theirs.values()) {
			if (fromName == null || i.fromName().equalsIgnoreCase(fromName)) {
				found = i;
			}
		}
		if (found != null) {
			theirs.remove(found.from());
		}
		if (theirs.isEmpty()) {
			INVITES.remove(p.getUUID());
		}
		return found;
	}

	/** Leaves the party. */
	public static Result leave(ServerPlayer p) {
		Party party = of(p.getUUID());
		if (party == null) {
			return Result.NO_PARTY;
		}
		remove(p.level().getServer(), party, p.getUUID().toString(), "message.extremeprogression.party.left");
		p.sendSystemMessage(Component.translatable("message.extremeprogression.party.you_left").withColor(INFO), false);
		send(p, true);
		return Result.OK;
	}

	/** The leader removes a member. */
	public static Result kick(ServerPlayer leader, String name) {
		Party party = of(leader.getUUID());
		if (party == null) {
			return Result.NO_PARTY;
		}
		if (!party.isLeader(leader.getUUID())) {
			return Result.NOT_LEADER;
		}
		String member = party.byName(name);
		if (member == null) {
			return Result.NOT_A_MEMBER;
		}
		if (member.equals(leader.getUUID().toString())) {
			return Result.SELF;
		}
		MinecraftServer server = leader.level().getServer();
		remove(server, party, member, "message.extremeprogression.party.removed");
		ServerPlayer gone = online(server, member);
		if (gone != null) {
			gone.sendSystemMessage(Component.translatable("message.extremeprogression.party.you_were_removed", leader.getDisplayName()).withColor(BAD), false);
			send(gone, true);
		}
		return Result.OK;
	}

	/** The leader hands the lead to another member. */
	public static Result promote(ServerPlayer leader, String name) {
		Party party = of(leader.getUUID());
		if (party == null) {
			return Result.NO_PARTY;
		}
		if (!party.isLeader(leader.getUUID())) {
			return Result.NOT_LEADER;
		}
		String member = party.byName(name);
		if (member == null) {
			return Result.NOT_A_MEMBER;
		}
		if (member.equals(leader.getUUID().toString())) {
			return Result.SELF;
		}
		party.leader = member;
		save();
		MinecraftServer server = leader.level().getServer();
		tell(server, party, Component.translatable("message.extremeprogression.party.new_leader", party.name(member)).withColor(INFO));
		sendAll(server, party);
		return Result.OK;
	}

	/** Takes a member out; the longest member leads when the leader went, and one left alone is no party. */
	private static void remove(MinecraftServer server, Party party, String member, String key) {
		String name = party.name(member);
		party.members.remove(member);
		party.names.remove(member);
		if (party.members.size() < 2) {
			data.parties.remove(party);
			save();
			tell(server, party, Component.translatable("message.extremeprogression.party.disbanded").withColor(INFO));
		} else {
			if (party.leader.equals(member)) {
				party.leader = party.members.get(0);
			}
			save();
			tell(server, party, Component.translatable(key, name).withColor(INFO));
		}
		sendAll(server, party);
		ExtremeProgression.LOGGER.info("{} left the party of {}", name, party.name(party.leader));
	}

	private static void tell(MinecraftServer server, Party party, Component text) {
		for (String m : party.members) {
			ServerPlayer p = online(server, m);
			if (p != null) {
				p.sendSystemMessage(text, false);
			}
		}
	}

	private static String name(ServerPlayer p) {
		return p.getName().getString();
	}

	/** A player joined: their name is kept fresh in their party, and they get their party at once. */
	public static void joined(ServerPlayer p) {
		Party party = of(p.getUUID());
		if (party != null && !name(p).equals(party.names.get(p.getUUID().toString()))) {
			party.names.put(p.getUUID().toString(), name(p));
			save();
		}
		send(p, true);
	}

	public static void forget(UUID id) {
		SENT.remove(id);
	}

	// --- The clients -------------------------------------------------------------------------------

	/** What this player sees of their party now. */
	public static PartyPayload payload(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		Party party = Config.PARTIES ? of(p.getUUID()) : null;
		List<PartyPayload.Member> members = new ArrayList<>();
		if (party != null) {
			for (String m : party.members) {
				ServerPlayer other = online(server, m);
				UUID id;
				try {
					id = UUID.fromString(m);
				} catch (IllegalArgumentException e) {
					continue;
				}
				// (an offline member's levels as last seen: their file is not read every second)
				int[] power = other != null ? power(id) : POWER.getOrDefault(id, new int[2]);
				boolean near = other != null && other != p && other.level() == p.level() && !other.isSpectator()
						&& other.distanceToSqr(p) <= Config.PARTY_RANGE * Config.PARTY_RANGE;
				members.add(new PartyPayload.Member(id, other != null ? name(other) : party.name(m), party.isLeader(id), other != null, near,
						near && active(other), power[0], power[1]));
			}
		}
		List<String> invites = new ArrayList<>();
		LinkedHashMap<UUID, Invite> theirs = INVITES.get(p.getUUID());
		if (theirs != null) {
			long tick = server.getTickCount();
			for (Invite i : theirs.values()) {
				if (i.expires() >= tick) {
					invites.add(i.fromName());
				}
			}
		}
		List<String> invitable = new ArrayList<>();
		if (Config.PARTIES && (party == null || party.size() < Config.PARTY_MAX_SIZE)) {
			for (ServerPlayer other : server.getPlayerList().getPlayers()) {
				if (other != p && of(other.getUUID()) == null && invitable.size() < 64) {
					invitable.add(name(other));
				}
			}
		}
		return new PartyPayload(Config.PARTIES, Config.PARTY_MAX_SIZE, party == null ? 0 : bonus(p), Config.PARTY_BONUS_MAX, members, invites, invitable);
	}

	private static int[] power(UUID id) {
		PlayerProgress progress = PlayerStore.get(id);
		int[] power = {progress.totalLevel(), progress.totalMasteryRanks()};
		POWER.put(id, power);
		return power;
	}

	/** Sends a player their party view when it changed (or always). */
	public static void send(ServerPlayer p, boolean always) {
		if (!ServerPlayNetworking.canSend(p, PartyPayload.TYPE)) {
			return;
		}
		PartyPayload now = payload(p);
		if (always || !now.equals(SENT.get(p.getUUID()))) {
			SENT.put(p.getUUID(), now);
			ServerPlayNetworking.send(p, now);
		}
	}

	private static void sendAll(MinecraftServer server, Party party) {
		for (String m : party.members) {
			ServerPlayer p = online(server, m);
			if (p != null) {
				send(p, true);
			}
		}
	}

	/** Once a second: invitations lapse, and every player gets what changed (who is near, who earns, the bonus). */
	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		long tick = server.getTickCount();
		INVITES.values().forEach(m -> m.values().removeIf(i -> i.expires() < tick));
		INVITES.values().removeIf(Map::isEmpty);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			send(p, false);
		}
	}
}
