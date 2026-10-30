package com.extremeprogression.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.config.Config;
import com.extremeprogression.config.Preset;
import com.extremeprogression.config.Settings;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.network.InspectPayload;
import com.extremeprogression.network.LeaderboardPayload;
import com.extremeprogression.network.Sync;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.xp.XpEvents;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The administration commands of design §11: {@code top} for everyone; {@code inspect} for operators; {@code reload},
 * {@code settings}, {@code preset}, {@code xprate} and {@code admin reset} for admins. A reset waits for a confirmation,
 * and like every admin change it is told to the player and written to the server log.
 */
public final class AdminCommands {
	private AdminCommands() {}

	/** A reset waiting for its confirmation, by who asked for it. */
	private record PendingReset(UUID target, String name, Skill skill, long until) {}

	private static final Map<String, PendingReset> PENDING = new HashMap<>();
	private static final long CONFIRM_MILLIS = 30_000;

	public static void clear() {
		PENDING.clear();
	}

	private static Iterable<String> skillIds() {
		return Arrays.stream(Skill.values()).map(s -> s.id).toList();
	}

	/** The subcommands, added to {@code /skills}. */
	static void register(LiteralArgumentBuilder<CommandSourceStack> skills) {
		var ops = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
		var admins = Commands.hasPermission(Commands.LEVEL_ADMINS);
		skills.then(Commands.literal("top")
				.executes(c -> top(c.getSource(), null))
				.then(Commands.argument("skill", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(skillIds(), b))
						.executes(c -> {
							Skill s = Skill.byId(StringArgumentType.getString(c, "skill").toLowerCase(Locale.ROOT));
							if (s == null) {
								c.getSource().sendFailure(Component.translatable("commands.extremeprogression.unknown_skill", StringArgumentType.getString(c, "skill")));
								return 0;
							}
							return top(c.getSource(), s);
						})));
		skills.then(Commands.literal("inspect").requires(ops)
				.then(Commands.argument("player", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(knownNames(c.getSource().getServer()), b))
						.executes(AdminCommands::inspect)));
		skills.then(Commands.literal("reload").requires(admins).executes(c -> {
			Settings.reload(c.getSource().getServer());
			c.getSource().sendSuccess(() -> Component.translatable("commands.extremeprogression.reload", Config.PRESET), true);
			return 1;
		}));
		skills.then(Commands.literal("settings").requires(admins).executes(c -> {
			Settings.send(c.getSource().getPlayerOrException(), true);
			return 1;
		}));
		skills.then(Commands.literal("preset").requires(admins)
				.then(Commands.argument("preset", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Preset.values()).map(p -> p.id), b))
						.executes(AdminCommands::preset)));
		skills.then(Commands.literal("xprate").requires(admins)
				.then(Commands.literal("reset").executes(c -> {
					if (!XpEvents.reset(c.getSource().getServer(), c.getSource().getTextName())) {
						c.getSource().sendFailure(Component.translatable("commands.extremeprogression.xprate.none"));
						return 0;
					}
					return 1;
				}))
				.then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0, 100))
						.executes(c -> xprate(c, "all", 0))
						.then(Commands.argument("skills", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(java.util.stream.Stream.of("all"),
										Arrays.stream(Skill.values()).map(s -> s.id)).toList(), b))
								.executes(c -> xprate(c, StringArgumentType.getString(c, "skills"), 0))
								.then(Commands.argument("minutes", IntegerArgumentType.integer(1, 10080))
										.executes(c -> xprate(c, StringArgumentType.getString(c, "skills"), IntegerArgumentType.getInteger(c, "minutes")))))));
	}

	/** {@code /skills admin reset}, added to the admin branch (admins only). */
	static LiteralArgumentBuilder<CommandSourceStack> reset() {
		return Commands.literal("reset").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
				.then(Commands.literal("confirm").executes(AdminCommands::confirm))
				.then(Commands.argument("player", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(knownNames(c.getSource().getServer()), b))
						.executes(c -> askReset(c, null))
						.then(Commands.argument("skill", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(skillIds(), b))
								.executes(c -> {
									Skill s = Skill.byId(StringArgumentType.getString(c, "skill").toLowerCase(Locale.ROOT));
									if (s == null) {
										c.getSource().sendFailure(Component.translatable("commands.extremeprogression.unknown_skill", StringArgumentType.getString(c, "skill")));
										return 0;
									}
									return askReset(c, s);
								})));
	}

	// --- Players by name, online or not ----------------------------------------------------------

	/** Every name the player files know, online players first. */
	static List<String> knownNames(MinecraftServer server) {
		List<String> out = new ArrayList<>(Arrays.asList(server.getPlayerNames()));
		for (UUID id : PlayerStore.known()) {
			String n = PlayerStore.peek(id).name;
			if (n != null && !n.isEmpty() && !out.contains(n)) {
				out.add(n);
			}
		}
		return out;
	}

	/** A player's id by name: online first, else from their file. */
	static UUID byName(MinecraftServer server, String name) {
		ServerPlayer online = server.getPlayerList().getPlayerByName(name);
		if (online != null) {
			return online.getUUID();
		}
		for (UUID id : PlayerStore.known()) {
			if (name.equalsIgnoreCase(PlayerStore.peek(id).name)) {
				return id;
			}
		}
		return null;
	}

	// --- The leaderboard -------------------------------------------------------------------------

	/** Every player with a file, sorted by total level, then Mastery ranks, then name; the first {@code limit}. */
	public static List<LeaderboardPayload.Row> leaderboard(int limit) {
		List<LeaderboardPayload.Row> rows = new ArrayList<>();
		for (UUID id : PlayerStore.known()) {
			PlayerProgress p = PlayerStore.peek(id);
			int[] levels = new int[Skill.values().length];
			int[] ranks = new int[Skill.values().length];
			for (Skill s : Skill.values()) {
				SkillData d = p.skills.get(s.id);
				levels[s.ordinal()] = d == null ? 0 : d.level;
				ranks[s.ordinal()] = d == null ? 0 : d.masteryRanks;
			}
			String name = p.name == null || p.name.isEmpty() ? id.toString().substring(0, 8) : p.name;
			rows.add(new LeaderboardPayload.Row(name, p.totalLevel(), p.totalMasteryRanks(), levels, ranks));
		}
		rows.sort(Comparator.comparingInt(LeaderboardPayload.Row::total).reversed().thenComparing(Comparator.comparingInt(LeaderboardPayload.Row::mastery).reversed())
				.thenComparing(LeaderboardPayload.Row::name, String.CASE_INSENSITIVE_ORDER));
		return rows.size() > limit ? new ArrayList<>(rows.subList(0, limit)) : rows;
	}

	/** One skill's ranking of the same rows: level, then Mastery ranks. */
	public static List<LeaderboardPayload.Row> bySkill(List<LeaderboardPayload.Row> rows, Skill s) {
		List<LeaderboardPayload.Row> out = new ArrayList<>(rows);
		out.sort(Comparator.comparingInt((LeaderboardPayload.Row r) -> r.levels()[s.ordinal()]).reversed()
				.thenComparing(Comparator.comparingInt((LeaderboardPayload.Row r) -> r.ranks()[s.ordinal()]).reversed())
				.thenComparing(LeaderboardPayload.Row::name, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	public static void sendLeaderboard(ServerPlayer p) {
		if (ServerPlayNetworking.canSend(p, LeaderboardPayload.TYPE)) {
			ServerPlayNetworking.send(p, new LeaderboardPayload(leaderboard(100)));
		}
	}

	private static int top(CommandSourceStack src, Skill skill) {
		List<LeaderboardPayload.Row> rows = leaderboard(Integer.MAX_VALUE);
		if (skill != null) {
			rows = bySkill(rows, skill);
		}
		Component title = skill == null ? Component.translatable("commands.extremeprogression.top.total")
				: Component.translatable("commands.extremeprogression.top.skill", Component.translatable(skill.translationKey()));
		src.sendSuccess(() -> title, false);
		for (int i = 0; i < rows.size() && i < 10; i++) {
			LeaderboardPayload.Row r = rows.get(i);
			int place = i + 1;
			String value = skill == null ? r.total() + (r.mastery() > 0 ? " (M" + r.mastery() + ")" : "")
					: r.levels()[skill.ordinal()] + (r.ranks()[skill.ordinal()] > 0 ? " (M" + r.ranks()[skill.ordinal()] + ")" : "");
			src.sendSuccess(() -> Component.translatable("commands.extremeprogression.top.row", place, r.name(), value), false);
		}
		if (rows.isEmpty()) {
			src.sendSuccess(() -> Component.translatable("commands.extremeprogression.top.empty"), false);
		}
		return rows.size();
	}

	// --- Inspect ---------------------------------------------------------------------------------

	private static int inspect(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		CommandSourceStack src = c.getSource();
		ServerPlayer viewer = src.getPlayerOrException();
		String name = StringArgumentType.getString(c, "player");
		UUID id = byName(src.getServer(), name);
		if (id == null) {
			src.sendFailure(Component.translatable("commands.extremeprogression.no_player", name));
			return 0;
		}
		var payload = inspectPayload(src.getServer(), id, name);
		if (!ServerPlayNetworking.canSend(viewer, InspectPayload.TYPE)) {
			src.sendFailure(Component.translatable("commands.extremeprogression.inspect.no_client"));
			return 0;
		}
		ServerPlayNetworking.send(viewer, payload);
		ExtremeProgression.LOGGER.info("{} inspected the progression of {}", src.getTextName(), payload.name());
		return 1;
	}

	/** What an operator sees of a player (online or not). */
	public static InspectPayload inspectPayload(MinecraftServer server, UUID id, String name) {
		PlayerProgress p = PlayerStore.peek(id);
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		double multiplier = online == null ? 1 : com.extremeprogression.death.DeathService.multiplier(online);
		return new InspectPayload(p.name.isEmpty() ? name : p.name, online != null,
				Sync.payload(p, server.overworld().getGameTime(), System.currentTimeMillis(), multiplier), p.synergies.size());
	}

	// --- Reset -----------------------------------------------------------------------------------

	private static int askReset(CommandContext<CommandSourceStack> c, Skill skill) {
		CommandSourceStack src = c.getSource();
		String name = StringArgumentType.getString(c, "player");
		UUID id = byName(src.getServer(), name);
		if (id == null) {
			src.sendFailure(Component.translatable("commands.extremeprogression.no_player", name));
			return 0;
		}
		String shown = PlayerStore.peek(id).name.isEmpty() ? name : PlayerStore.peek(id).name;
		PENDING.put(src.getTextName(), new PendingReset(id, shown, skill, System.currentTimeMillis() + CONFIRM_MILLIS));
		src.sendSuccess(() -> skill == null ? Component.translatable("commands.extremeprogression.reset.ask_all", shown).withColor(0xFF5555)
				: Component.translatable("commands.extremeprogression.reset.ask_skill", shown, Component.translatable(skill.translationKey())).withColor(0xFF5555), false);
		return 1;
	}

	private static int confirm(CommandContext<CommandSourceStack> c) {
		CommandSourceStack src = c.getSource();
		PendingReset r = PENDING.remove(src.getTextName());
		if (r == null || r.until() < System.currentTimeMillis()) {
			src.sendFailure(Component.translatable("commands.extremeprogression.reset.nothing"));
			return 0;
		}
		reset(src.getServer(), r.target(), r.skill(), src.getTextName());
		src.sendSuccess(() -> r.skill() == null ? Component.translatable("commands.extremeprogression.reset.done_all", r.name())
				: Component.translatable("commands.extremeprogression.reset.done_skill", r.name(), Component.translatable(r.skill().translationKey())), true);
		return 1;
	}

	/** Resets a player's progression (one skill, or everything), saved at once, told and logged. */
	public static void reset(MinecraftServer server, UUID id, Skill skill, String by) {
		PlayerProgress p = PlayerStore.loaded(id) ? PlayerStore.get(id) : PlayerStore.peek(id);
		String name = p.name;
		if (skill == null) {
			PlayerProgress fresh = new PlayerProgress();
			fresh.name = name;
			PlayerStore.replace(id, fresh);
		} else {
			p.skills.put(skill.id, new SkillData());
			PlayerStore.replace(id, p);
		}
		ServerPlayer online = server.getPlayerList().getPlayer(id);
		if (online != null) {
			Sync.send(online);
			com.extremeprogression.death.DeathService.sendHistory(online, null);
			com.extremeprogression.synergy.Synergies.send(online);
			online.sendSystemMessage(skill == null ? Component.translatable("message.extremeprogression.admin_reset_all")
					: Component.translatable("message.extremeprogression.admin_reset_skill", Component.translatable(skill.translationKey())), false);
		} else {
			// (an offline player's file is written, and not kept in memory)
			PlayerStore.unload(id);
		}
		ExtremeProgression.LOGGER.info("{} reset the progression of {}: {}", by, name, skill == null ? "everything" : skill.id);
	}

	// --- Presets, XP events and the settings screen ----------------------------------------------

	private static int preset(CommandContext<CommandSourceStack> c) {
		CommandSourceStack src = c.getSource();
		Preset p = Preset.of(StringArgumentType.getString(c, "preset").toLowerCase(Locale.ROOT));
		if (p == null) {
			src.sendFailure(Component.translatable("commands.extremeprogression.preset.unknown", StringArgumentType.getString(c, "preset")));
			return 0;
		}
		com.google.gson.JsonObject json = Config.toJson();
		json.addProperty("preset", p.id);
		Config.apply(json);
		Settings.save();
		Settings.syncAll(src.getServer());
		ExtremeProgression.LOGGER.info("{} chose the preset {}", src.getTextName(), p.id);
		src.sendSuccess(() -> Component.translatable("commands.extremeprogression.preset.set", p.id), true);
		return 1;
	}

	private static int xprate(CommandContext<CommandSourceStack> c, String skills, int minutes) {
		CommandSourceStack src = c.getSource();
		Skill skill = null;
		if (!skills.equalsIgnoreCase("all")) {
			skill = Skill.byId(skills.toLowerCase(Locale.ROOT));
			if (skill == null) {
				src.sendFailure(Component.translatable("commands.extremeprogression.unknown_skill", skills));
				return 0;
			}
		}
		XpEvents.start(src.getServer(), skill, DoubleArgumentType.getDouble(c, "multiplier"), minutes, src.getTextName());
		return 1;
	}

	/** An admin saved the settings screen: applied, written to the files, sent to everyone. Anyone else is refused. */
	public static void settingsSaved(ServerPlayer p, String json) {
		if (!Settings.mayEdit(p)) {
			ExtremeProgression.LOGGER.warn("{} sent settings without being an admin; ignored", p.getName().getString());
			return;
		}
		if (!Settings.applyShared(json)) {
			p.sendSystemMessage(Component.translatable("message.extremeprogression.settings.invalid"), false);
			return;
		}
		Settings.save();
		Settings.syncAll(p.level().getServer());
		ExtremeProgression.LOGGER.info("{} changed the settings in the game (preset {})", p.getName().getString(), Config.PRESET);
		p.sendSystemMessage(Component.translatable("message.extremeprogression.settings.saved", Config.PRESET), false);
	}
}
