package com.extremeprogression.legends;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import com.extremeprogression.config.Config;
import com.extremeprogression.config.SkillSettings;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.synergy.Chronicle;
import com.extremeprogression.synergy.Synergy;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Titles (2.0): earned by feats and shown before the name above the player's head, in the tablist and in chat
 * ({@code %extremeprogression:title%}). Earned for good: a title stays when the feat is later undone (a level lost to a
 * death). One at a time is shown, chosen in the Legends screen.
 */
public final class Titles {
	private Titles() {}

	/**
	 * One title: its id (the lang key {@code title.extremeprogression.<id>} and {@code .how}), its English name (for the
	 * tablist and chat, which the server writes), its colour and its feat.
	 */
	public record Def(String id, String name, int color, Predicate<ServerPlayer> earned) {}

	/** The names of the skill grandmasters' titles. */
	static final java.util.Map<String, String> GRANDMASTERS = java.util.Map.ofEntries(java.util.Map.entry("melee", "the Warrior"),
			java.util.Map.entry("ranged", "the Marksman"), java.util.Map.entry("defense", "the Bulwark"), java.util.Map.entry("mining", "the Deep Miner"),
			java.util.Map.entry("foraging", "the Woodsman"), java.util.Map.entry("farming", "the Harvester"), java.util.Map.entry("fishing", "the Angler"),
			java.util.Map.entry("smithing", "the Smith"), java.util.Map.entry("arcana", "the Arcanist"), java.util.Map.entry("exploration", "the Wayfarer"),
			java.util.Map.entry("trading", "the Merchant Prince"));

	private static final List<Def> ALL = build();

	private static List<Def> build() {
		List<Def> out = new ArrayList<>();
		// A skill at the highest level
		for (Skill s : Skill.values()) {
			out.add(new Def(s.id + "_grandmaster", GRANDMASTERS.getOrDefault(s.id, "Grandmaster"), s.category.accent, p -> SkillSettings.enabled(s) && progress(p).get(s).level >= Config.MAX_LEVEL));
		}
		// The total level, and every skill at the highest
		out.add(new Def("adept", "Adept", 0x55FF55, p -> progress(p).totalLevel() >= 250));
		out.add(new Def("veteran", "Veteran", 0x55FFFF, p -> progress(p).totalLevel() >= 500));
		out.add(new Def("paragon", "Paragon", 0xFF55FF, p -> progress(p).totalLevel() >= 750));
		out.add(new Def("legend", "Legend", 0xFFAA00, p -> {
			for (Skill s : Skill.values()) {
				if (SkillSettings.enabled(s) && progress(p).get(s).level < Config.MAX_LEVEL) {
					return false;
				}
			}
			return true;
		}));
		out.add(new Def("masterful", "the Masterful", 0xFFD700, p -> progress(p).totalMasteryRanks() >= 25));
		// Bosses
		out.add(new Def("dragonslayer", "Dragonslayer", 0xAA00AA, p -> legends(p).stat("boss.ender_dragon") > 0));
		out.add(new Def("witherbane", "Witherbane", 0x555555, p -> legends(p).stat("boss.wither") > 0));
		out.add(new Def("wardenbane", "Wardenbane", 0x00AAAA, p -> legends(p).stat("boss.warden") > 0));
		out.add(new Def("tidebreaker", "Tidebreaker", 0x5555FF, p -> legends(p).stat("boss.elder_guardian") > 0));
		// Champions and contracts
		out.add(new Def("champion_hunter", "Champion Hunter", 0xFF5555, p -> legends(p).stat("champions") >= 10));
		out.add(new Def("champions_bane", "Champions' Bane", 0xAA0000, p -> legends(p).stat("champions") >= 100));
		out.add(new Def("contractor", "Contractor", 0xFFFF55, p -> legends(p).stat("contracts") >= 25));
		out.add(new Def("guildmaster", "Guildmaster", 0xFFAA00, p -> legends(p).stat("contracts") >= 150));
		// The daily streak
		out.add(new Def("devoted", "the Devoted", 0x55FF55, p -> legends(p).bestStreak >= 7));
		out.add(new Def("unbroken", "the Unbroken", 0x00AA00, p -> legends(p).bestStreak >= 30));
		// Synergies
		out.add(new Def("seeker", "Seeker of Secrets", 0xFFAA00, p -> progress(p).synergies.size() >= 8));
		out.add(new Def("sage", "the Sage", 0xFFFF55, p -> {
			for (Synergy s : Synergy.of(false)) {
				if (s.enabled && !progress(p).synergies.containsKey(s.id)) {
					return false;
				}
			}
			return true;
		}));
		out.add(new Def("fellowship", "of the Fellowship", 0x55FF55, p -> {
			for (Synergy s : Synergy.of(true)) {
				if (progress(p).synergies.containsKey(s.id)) {
					return true;
				}
			}
			return false;
		}));
		out.add(new Def("trailblazer", "Trailblazer", 0xFFD700, p -> {
			String id = p.getUUID().toString();
			return Chronicle.all().values().stream().anyMatch(f -> f.by(id));
		}));
		out.add(new Def("echo_forged", "Echo-Forged", 0x2AC6C6, p -> legends(p).stat("echo_forged") > 0));
		// The Oaths (2.2)
		out.add(new Def("oathsworn", "Oathsworn", 0xD8D8D8, p -> !legends(p).oaths.isEmpty()));
		out.add(new Def("oathkeeper", "Oathkeeper", 0xFFD700, p -> legends(p).oaths.contains("legends")));
		return Collections.unmodifiableList(out);
	}

	private static PlayerProgress progress(ServerPlayer p) {
		return PlayerStore.get(p.getUUID());
	}

	private static LegendsData legends(ServerPlayer p) {
		return progress(p).legends;
	}

	public static List<Def> all() {
		return ALL;
	}

	public static Def byId(String id) {
		for (Def d : ALL) {
			if (d.id().equals(id)) {
				return d;
			}
		}
		return null;
	}

	public static String translationKey(String id) {
		return "title.extremeprogression." + id.toLowerCase(Locale.ROOT);
	}

	/** Checks every title not yet earned: the new ones are the player's, with a word and a sound. Returns how many. */
	public static int check(ServerPlayer p) {
		if (!Config.TITLES) {
			return 0;
		}
		LegendsData d = legends(p);
		int earned = 0;
		for (Def t : ALL) {
			if (!d.titles.contains(t.id()) && t.earned().test(p)) {
				d.titles.add(t.id());
				earned++;
				p.sendSystemMessage(Component.translatable("message.extremeprogression.title.earned", Component.translatable(translationKey(t.id())).withColor(t.color()))
						.withColor(0xFFAA00), false);
				p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.5f, 1.4f);
			}
		}
		if (earned > 0) {
			PlayerStore.markDirty(p.getUUID());
			Legends.dirty(p);
		}
		return earned;
	}

	/** Shows an earned title ("" for none). Returns whether it is shown now. */
	public static boolean equip(ServerPlayer p, String id) {
		LegendsData d = legends(p);
		if (id == null || id.isEmpty()) {
			d.title = "";
		} else if (d.titles.contains(id) && byId(id) != null) {
			d.title = id;
		} else {
			return false;
		}
		PlayerStore.markDirty(p.getUUID());
		Legends.dirty(p);
		com.extremeprogression.compat.PowerTags.changed();
		return true;
	}

	/** The title a player shows, or null (none, or titles off). */
	public static Def shown(PlayerProgress p) {
		if (!Config.TITLES || p.legends.title.isEmpty()) {
			return null;
		}
		return byId(p.legends.title);
	}
}
