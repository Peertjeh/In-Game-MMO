package com.extremeprogression.compat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.command.AdminCommands;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.death.DeathRules;
import com.extremeprogression.death.DeathService;
import com.extremeprogression.network.LeaderboardPayload;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.skill.Specialization;
import com.extremeprogression.synergy.Chronicle;
import com.extremeprogression.synergy.Synergy;
import com.extremeprogression.xp.XpEvents;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the mod tells a tablist, a scoreboard or a chat format about each player and about the server, as
 * {@code %extremeprogression:<name>%} placeholders (offered to Text Placeholder API by {@link PlaceholderApiHook}, which
 * is how TAB reads them on Fabric), as Extreme Dimensions does for its own. The values are worked out on the server's
 * thread now and then and kept, so a mod that asks from a thread of its own (TAB does) reads a finished snapshot and
 * never the live data. They are in English: a tablist is the same for everyone, and a dedicated server has no language
 * files of the mod's.
 */
public final class TabPlaceholders {
	private TabPlaceholders() {}

	/** The placeholders about one player. */
	public static final List<String> PLAYER = player();
	/** The placeholders about the server. */
	public static final List<String> WORLD = List.of("preset", "cap", "xp_event", "discoveries", "top");

	private static List<String> player() {
		List<String> out = new ArrayList<>(List.of("total", "mastery", "highest", "highest_skill", "highest_level", "specs", "pressure", "fatigue",
				"deaths", "synergies", "badge"));
		for (Skill s : Skill.values()) {
			out.add("level_" + s.id);
			out.add("mastery_" + s.id);
		}
		return List.copyOf(out);
	}

	private static final Map<UUID, Map<String, Component>> PLAYERS = new ConcurrentHashMap<>();
	private static volatile Map<String, Component> world = Map.of();
	/** The server's best player, worked out less often (it reads every player file). */
	private static volatile Component top = Component.empty();
	/** Whether anything asks for the placeholders (set when they are offered; the tests take snapshots directly). */
	public static volatile boolean offered = false;

	/**
	 * At start-up: offers the placeholders to placeholder-api if it is installed. (This class never touches
	 * placeholder-api itself, so a server without it never loads a class of it; a version this mod does not know is
	 * left alone with a warning.)
	 */
	public static void offer() {
		if (!FabricLoader.getInstance().isModLoaded("placeholder-api")) {
			return;
		}
		try {
			PlaceholderApiHook.register();
			offered = true;
			ExtremeProgression.LOGGER.info("Offered {} placeholders to placeholder-api as %{}:<name>%", PLAYER.size() + WORLD.size(), ExtremeProgression.MOD_ID);
		} catch (LinkageError | RuntimeException e) {
			ExtremeProgression.LOGGER.warn("This version of placeholder-api is not one Extreme Progression knows; its placeholders are not offered", e);
		}
	}

	/** A player's placeholder as last worked out ("" when the placeholders are off or not known yet). */
	public static Component player(UUID player, String name) {
		if (!Config.PLACEHOLDERS) {
			return Component.empty();
		}
		Map<String, Component> m = PLAYERS.get(player);
		Component c = m == null ? null : m.get(name);
		return c == null ? Component.empty() : c;
	}

	/** A placeholder about the server as last worked out. */
	public static Component world(String name) {
		if (!Config.PLACEHOLDERS) {
			return Component.empty();
		}
		Component c = world.get(name);
		return c == null ? Component.empty() : c;
	}

	/** Every server tick: now and then, the snapshot of every player online and of the server is made anew. */
	public static void tick(MinecraftServer s) {
		if (!offered || !Config.PLACEHOLDERS || s.getTickCount() % Config.PLACEHOLDER_REFRESH_TICKS != 0) {
			return;
		}
		// (the best player reads every player file: once a minute)
		refresh(s, s.getTickCount() % 1200 == 0 || top.getString().isEmpty());
	}

	/** Makes the snapshot anew, and forgets players who have left. */
	public static void refresh(MinecraftServer s, boolean withTop) {
		Map<UUID, Boolean> online = new LinkedHashMap<>();
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			PLAYERS.put(p.getUUID(), values(p));
			online.put(p.getUUID(), true);
		}
		PLAYERS.keySet().removeIf(id -> !online.containsKey(id));
		if (withTop) {
			List<LeaderboardPayload.Row> rows = AdminCommands.leaderboard(1);
			top = rows.isEmpty() ? Component.empty() : Component.literal(rows.get(0).name() + " " + rows.get(0).total());
		}
		world = values(s);
	}

	/** The server stops: nothing is kept for the next world. */
	public static void clear() {
		PLAYERS.clear();
		world = Map.of();
		top = Component.empty();
	}

	// --- The values ------------------------------------------------------------------------------

	/** Every placeholder about one player, worked out now (on the server's thread). */
	public static Map<String, Component> values(ServerPlayer p) {
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		Map<String, Component> m = new LinkedHashMap<>();
		int total = progress.totalLevel(), mastery = progress.totalMasteryRanks();
		m.put("total", text(total));
		m.put("mastery", text(mastery));
		Skill best = DeathRules.highest(progress);
		SkillData b = progress.get(best);
		boolean any = b.level > 0 || b.masteryRanks > 0;
		m.put("highest", text(any ? name(best) + " " + level(b) : ""));
		m.put("highest_skill", text(any ? name(best) : ""));
		m.put("highest_level", text(any ? level(b) : ""));
		m.put("specs", text(specs(progress)));
		m.put("pressure", text(progress.death.pressure));
		long fatigue = DeathService.fatigueLeft(progress, p.level().getServer().overworld().getGameTime());
		m.put("fatigue", text(fatigue > 0 ? clock(fatigue / 20.0) : ""));
		m.put("deaths", text(progress.death.deaths));
		m.put("synergies", text(progress.synergies.size() + "/" + Synergy.values().length));
		m.put("badge", badge(total, mastery));
		for (Skill s : Skill.values()) {
			SkillData d = progress.get(s);
			m.put("level_" + s.id, text(d.level));
			m.put("mastery_" + s.id, text(d.masteryRanks));
		}
		return m;
	}

	/** Every placeholder about the server, worked out now. */
	public static Map<String, Component> values(MinecraftServer s) {
		Map<String, Component> m = new LinkedHashMap<>();
		m.put("preset", text(preset()));
		m.put("cap", text(Config.TOTAL_LEVEL_CAP));
		m.put("xp_event", text(xpEvent()));
		m.put("discoveries", text(Chronicle.all().size() + "/" + Synergy.values().length));
		m.put("top", top);
		return m;
	}

	/** Hardcore, Normal or Custom. */
	static String preset() {
		return switch (Config.PRESET) {
			case "hardcore" -> "Hardcore";
			case "normal" -> "Normal";
			default -> "Custom";
		};
	}

	/** The running XP event ("×2 Mining", "×2 all skills", with the minutes left), "" without one. */
	static String xpEvent() {
		List<XpEvents.Event> events = XpEvents.active();
		if (events.isEmpty()) {
			return "";
		}
		XpEvents.Event e = events.get(0);
		String what = e.skill() == null ? "all skills" : name(e.skill());
		String left = e.endsAt() == 0 ? "" : " · " + Math.max(1, (e.endsAt() - System.currentTimeMillis() + 59_999) / 60_000) + " min";
		String factor = String.format(Locale.ROOT, "%.2f", e.multiplier()).replaceAll("0+$", "").replaceAll("\\.$", "");
		return "×" + factor + " " + what + left + (events.size() > 1 ? " (+" + (events.size() - 1) + ")" : "");
	}

	/** The skill's name in English (the tablist is the same for everyone). */
	static String name(Skill s) {
		return s.id.substring(0, 1).toUpperCase(Locale.ROOT) + s.id.substring(1);
	}

	/** "61", or past the maximum "M3". */
	static String level(SkillData d) {
		return d.level >= Config.MAX_LEVEL ? "M" + d.masteryRanks : Integer.toString(d.level);
	}

	/** The specializations in use, in English ("Blade, Prospector"). */
	static String specs(PlayerProgress p) {
		List<String> out = new ArrayList<>();
		for (Skill s : Skill.values()) {
			SkillData d = p.get(s);
			for (int i = 0; i < d.specs.size(); i++) {
				Specialization spec = com.extremeprogression.tree.TreeRules.active(d, i);
				if (spec != null) {
					String id = spec.id;
					out.add(id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1).replace('_', ' '));
				}
			}
		}
		return String.join(", ", out);
	}

	/**
	 * The total level at a glance, behind a name: "✦312" in a colour that brightens every hundred levels (gray, green,
	 * aqua, gold, light purple at the cap), with a gold star once the player has Mastery; nothing at level 0.
	 */
	public static Component badge(int total, int mastery) {
		if (total <= 0 && mastery <= 0) {
			return Component.empty();
		}
		ChatFormatting colour = total >= Config.TOTAL_LEVEL_CAP ? ChatFormatting.LIGHT_PURPLE : total >= 300 ? ChatFormatting.GOLD
				: total >= 200 ? ChatFormatting.AQUA : total >= 100 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
		MutableComponent c = Component.literal("✦" + total).withStyle(colour);
		if (mastery > 0) {
			c.append(Component.literal("★").withStyle(ChatFormatting.GOLD));
		}
		return c;
	}

	private static Component text(int n) {
		return Component.literal(Integer.toString(n));
	}

	private static Component text(String s) {
		return s.isEmpty() ? Component.empty() : Component.literal(s);
	}

	/** Seconds as m:ss. */
	static String clock(double seconds) {
		long s = (long) Math.ceil(Math.max(0, seconds));
		return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
	}
}
