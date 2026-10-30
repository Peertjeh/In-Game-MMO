package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.SkeletonTests.check;

import java.util.Map;

import com.extremeprogression.compat.PlaceholderApiHook;
import com.extremeprogression.compat.TabPlaceholders;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.death.DeathService;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.skill.Specialization;
import com.extremeprogression.xp.XpEvents;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The tablist placeholders (TAB, through Text Placeholder API): what they say of a player and of the server, the badge,
 * the snapshot the tablist reads, and nothing at all when switched off. In the compatibility run, with the real
 * placeholder-api, they are read back through it as TAB would.
 */
public class PlaceholderTests {
	private static String text(Map<String, Component> m, String name) {
		Component c = m.get(name);
		return c == null ? null : c.getString();
	}

	@GameTest
	public void theTablistReadsAPlayersLevelsAndDeaths(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Listed");
		Map<String, Component> fresh = TabPlaceholders.values(p);
		check(helper, fresh.keySet().containsAll(TabPlaceholders.PLAYER) && TabPlaceholders.PLAYER.containsAll(fresh.keySet()),
				"the placeholders worked out are %s, not the ones offered", fresh.keySet());
		check(helper, "0".equals(text(fresh, "total")) && text(fresh, "badge").isEmpty() && text(fresh, "highest").isEmpty(),
				"a new player reads total %s, badge '%s', highest '%s'", text(fresh, "total"), text(fresh, "badge"), text(fresh, "highest"));
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		Progression.setLevel(progress, Skill.MINING, 61);
		Progression.setLevel(progress, Skill.MELEE, 47);
		progress.get(Skill.MELEE).specs.add(Specialization.BLADE.id);
		DeathService.died(p, p.damageSources().generic());
		Map<String, Component> m = TabPlaceholders.values(p);
		check(helper, "108".equals(text(m, "total")) && "Mining 61".equals(text(m, "highest")) && "61".equals(text(m, "level_mining"))
				&& "47".equals(text(m, "level_melee")), "total %s, highest %s, Mining %s, Melee %s", text(m, "total"), text(m, "highest"),
				text(m, "level_mining"), text(m, "level_melee"));
		check(helper, "Blade".equals(text(m, "specs")), "the specializations read '%s'", text(m, "specs"));
		check(helper, "1".equals(text(m, "pressure")) && "1".equals(text(m, "deaths")) && text(m, "fatigue").matches("\\d+:\\d\\d"),
				"pressure %s, deaths %s, fatigue '%s'", text(m, "pressure"), text(m, "deaths"), text(m, "fatigue"));
		check(helper, "0/12".equals(text(m, "synergies")), "synergies read %s", text(m, "synergies"));
		Component badge = m.get("badge");
		check(helper, "✦108".equals(badge.getString()) && badge.getStyle().getColor() != null
				&& badge.getStyle().getColor().getValue() == ChatFormatting.GREEN.getColor(), "the badge reads '%s' in %s", badge.getString(), badge.getStyle().getColor());
		Progression.setLevel(progress, Skill.ARCANA, Config.MAX_LEVEL);
		progress.get(Skill.ARCANA).masteryRanks = 2;
		Map<String, Component> mastered = TabPlaceholders.values(p);
		check(helper, "Arcana M2".equals(text(mastered, "highest")) && "✦208★".equals(text(mastered, "badge")) && "2".equals(text(mastered, "mastery_arcana")),
				"with Mastery: highest %s, badge %s", text(mastered, "highest"), text(mastered, "badge"));
		helper.succeed();
	}

	@GameTest
	public void theTablistReadsTheServer(GameTestHelper helper) {
		Config.reset();
		MinecraftServer server = helper.getLevel().getServer();
		try {
			Map<String, Component> m = TabPlaceholders.values(server);
			check(helper, m.keySet().containsAll(TabPlaceholders.WORLD) && TabPlaceholders.WORLD.containsAll(m.keySet()), "the server's placeholders are %s", m.keySet());
			check(helper, "Hardcore".equals(text(m, "preset")) && "400".equals(text(m, "cap")) && text(m, "xp_event").isEmpty(),
					"preset %s, cap %s, XP event '%s'", text(m, "preset"), text(m, "cap"), text(m, "xp_event"));
			check(helper, text(m, "discoveries").matches("\\d+/12"), "the discoveries read %s", text(m, "discoveries"));
			XpEvents.start(server, Skill.MINING, 2, 30, "test");
			check(helper, "×2 Mining · 30 min".equals(text(TabPlaceholders.values(server), "xp_event")), "the XP event reads '%s'",
					text(TabPlaceholders.values(server), "xp_event"));
		} finally {
			XpEvents.clear();
		}
		helper.succeed();
	}

	@GameTest
	public void theSnapshotForgetsLeaversAndCanBeSwitchedOff(GameTestHelper helper) {
		Config.reset();
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer p = TestPlayers.survivor(helper, "Snapshot");
		Progression.setLevel(PlayerStore.get(p.getUUID()), Skill.FISHING, 12);
		TabPlaceholders.refresh(server, true);
		check(helper, "12".equals(TabPlaceholders.player(p.getUUID(), "level_fishing").getString()), "the snapshot does not hold the player");
		check(helper, !TabPlaceholders.world("top").getString().isEmpty(), "the snapshot has no best player");
		try {
			Config.PLACEHOLDERS = false;
			check(helper, TabPlaceholders.player(p.getUUID(), "level_fishing").getString().isEmpty() && TabPlaceholders.world("preset").getString().isEmpty(),
					"switched off, the placeholders still say something");
		} finally {
			Config.reset();
		}
		// Read through the real placeholder-api, as TAB does (the compatibility run has it)
		if (FabricLoader.getInstance().isModLoaded("placeholder-api")) {
			check(helper, TabPlaceholders.offered, "the placeholders were not offered to placeholder-api");
			Component read = PlaceholderApiHook.parse(p, "level_fishing");
			check(helper, read != null && "12".equals(read.getString()), "placeholder-api reads '%s'", read == null ? null : read.getString());
		}
		helper.succeed();
	}
}
