package com.extremeprogression.gametest;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.client.ExtremeProgressionClient;
import com.extremeprogression.client.inventory.SheetButton;
import com.extremeprogression.client.screen.CharacterSheetScreen;
import com.extremeprogression.client.screen.SkillScreen;
import com.extremeprogression.client.state.ClientProgress;
import com.extremeprogression.network.ProgressPayload;
import com.extremeprogression.skill.Category;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.skill.Specialization;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.locale.Language;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The client starts with the mod, opens a world, receives its progression and shows it: the Character Sheet (opened
 * with its hotkey), the Skill Screen, and the inventory button. Every step is saved as a screenshot (uploaded by CI).
 */
public class ClientShots implements FabricClientGameTest {
	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new AssertionError(message);
		}
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		check(FabricLoader.getInstance().isModLoaded(ExtremeProgression.MOD_ID), ExtremeProgression.MOD_ID + " is not loaded on the client");
		ctx.waitTicks(40);
		ctx.runOnClient(mc -> everyTextIsThere());
		ctx.takeScreenshot("00_title");
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			ctx.takeScreenshot("01_world");

			// The progression arrives on joining: ten skills at level 0
			ctx.runOnClient(mc -> {
				ProgressPayload now = ClientProgress.current();
				check(now != null, "the client got no progression on joining");
				check(now.skills().size() == 10, "the client got " + now.skills().size() + " skills, not 10");
				check(now.total() == 0 && now.cap() == 400, "a new player's total is " + now.total() + " of " + now.cap());
			});
			int before = ctx.computeOnClient(mc -> ClientProgress.updates());

			// An operator's changes on the server reach the client: an example character to look at
			sp.getServer().runOnServer(s -> {
				String[] levels = {"melee 47", "ranged 22", "defense 38", "mining 61", "foraging 12", "farming 9", "fishing 3", "exploration 18",
						"smithing 2", "arcana 100"};
				for (String l : levels) {
					admin(s, "set", l);
				}
				admin(s, "add", "melee 32450");
				admin(s, "add", "mining 42710");
				admin(s, "add", "ranged 10720");
				admin(s, "add", "defense 14720");
				admin(s, "add", "arcana 1000000");
			});
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				check(ClientProgress.updates() > before, "the client was not sent the changes");
				ProgressPayload.Entry mining = ClientProgress.skill("mining");
				check(mining != null && mining.level() == 61, "the client shows Mining at " + (mining == null ? "nothing" : mining.level()) + ", not 61");
				ProgressPayload.Entry arcana = ClientProgress.skill("arcana");
				check(arcana != null && arcana.masteryRanks() == 2, "a million XP at level 100 gave " + (arcana == null ? "nothing" : arcana.masteryRanks()) + " Mastery ranks, not 2");
				check(ClientProgress.current().total() == 312, "the client's total is " + ClientProgress.current().total() + ", not 312");
			});
			ctx.takeScreenshot("02_after_admin_set");

			// The hotkey opens the Character Sheet
			int sheets = ctx.computeOnClient(mc -> CharacterSheetScreen.opened());
			ctx.getInput().pressKey(o -> ExtremeProgressionClient.OPEN_SHEET);
			ctx.waitTicks(10);
			check(ctx.computeOnClient(mc -> CharacterSheetScreen.opened()) > sheets, "K did not open the Character Sheet");
			ctx.takeScreenshot("03_character_sheet");

			// A Skill Screen, below and past the maximum level
			ctx.runOnClient(mc -> mc.gui.setScreen(new SkillScreen(Skill.MELEE, new CharacterSheetScreen(null))));
			ctx.waitTicks(10);
			ctx.takeScreenshot("04_skill_melee");
			ctx.runOnClient(mc -> mc.gui.setScreen(new SkillScreen(Skill.ARCANA, null)));
			ctx.waitTicks(10);
			ctx.takeScreenshot("05_skill_arcana_mastery");

			// The inventory has the Character Sheet's button
			InventoryScreen[] inventory = new InventoryScreen[1];
			ctx.runOnClient(mc -> {
				inventory[0] = new InventoryScreen(mc.player);
				mc.gui.setScreen(inventory[0]);
			});
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> check(Screens.getWidgets(inventory[0]).stream().anyMatch(w -> w instanceof SheetButton),
					"the inventory has no Character Sheet button"));
			ctx.takeScreenshot("06_inventory_button");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
		}
	}

	private static void admin(MinecraftServer s, String what, String args) {
		ServerPlayer p = s.getPlayerList().getPlayers().get(0);
		s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), "skills admin " + what + " " + p.getName().getString() + " " + args);
	}

	/** Every text the screens ask for is in the language file (a missing one would show as its key). */
	private static void everyTextIsThere() {
		List<String> keys = new ArrayList<>();
		for (Skill s : Skill.values()) {
			keys.add(s.translationKey());
			for (int level : Skill.TRUNK_LEVELS) {
				keys.add("trunk.extremeprogression." + s.id + "." + level);
				keys.add("trunk.extremeprogression." + s.id + "." + level + ".desc");
			}
		}
		for (Category c : Category.values()) {
			keys.add(c.translationKey());
		}
		for (Specialization spec : Specialization.values()) {
			for (String part : new String[] {"", ".gear", ".style", ".ability", ".ability.desc", ".tradeoff"}) {
				keys.add(spec.translationKey() + part);
			}
		}
		// (the messages the server sends)
		keys.addAll(List.of("message.extremeprogression.admin_set", "message.extremeprogression.admin_xp", "message.extremeprogression.level_up",
				"message.extremeprogression.mastery_up", "message.extremeprogression.fishing.not_open_water", "message.extremeprogression.fishing.overfished"));
		List<String> missing = new ArrayList<>();
		for (String k : keys) {
			if (!Language.getInstance().has(k)) {
				missing.add(k);
			}
		}
		check(missing.isEmpty(), "missing texts: " + missing);
	}
}
