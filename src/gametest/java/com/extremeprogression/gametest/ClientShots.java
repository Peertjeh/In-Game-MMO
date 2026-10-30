package com.extremeprogression.gametest;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.config.Config;
import com.extremeprogression.client.hud.AbilityHud;
import com.extremeprogression.client.screen.RespecScreen;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.network.Sync;
import com.extremeprogression.tree.NodeSlot;
import com.extremeprogression.tree.TreeRules;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
		// No XP of its own while the screens are checked: exploring the spawn would otherwise raise Exploration, and the
		// totals below would depend on how long the world took to load (an operator's changes do not use the multiplier)
		Config.XP_MULTIPLIER = 0;
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

			// A tree in use: Blade chosen in Melee with a few points bought, and Bulwark in Defense, which shuts Brawler out
			sp.getServer().runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().get(0);
				PlayerProgress pr = PlayerStore.get(player.getUUID());
				TreeRules.choose(pr, Specialization.BLADE);
				for (NodeSlot slot : new NodeSlot[] {NodeSlot.A1, NodeSlot.P1, NodeSlot.P1, NodeSlot.A2, NodeSlot.P2, NodeSlot.P3}) {
					TreeRules.buy(pr, Specialization.BLADE, slot);
				}
				TreeRules.choose(pr, Specialization.BULWARK);
				Sync.send(player);
			});
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				ProgressPayload.Entry melee = ClientProgress.skill("melee");
				check(melee != null && melee.specs().contains("blade") && melee.bought().size() == 6, "the client does not show the bought Blade nodes");
			});

			// A Skill Screen, below and past the maximum level
			ctx.runOnClient(mc -> {
				SkillScreen screen = new SkillScreen(Skill.MELEE, new CharacterSheetScreen(null));
				mc.gui.setScreen(screen);
				screen.select("blade/p3");
			});
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

			// The respec costs are shown before anything happens
			ctx.runOnClient(mc -> mc.gui.setScreen(new RespecScreen(Skill.MELEE, null)));
			ctx.waitTicks(10);
			ctx.takeScreenshot("07_respec_confirmation");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));

			// With a sword in hand the ability key reaches Riposte: its icon shows beside the hotbar, and R uses it
			sp.getServer().runOnServer(s -> s.getPlayerList().getPlayers().get(0).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD)));
			// Long enough for the operator messages in the chat to fade, so the action bar shows in the screenshots
			ctx.waitTicks(220);
			check(ctx.computeOnClient(mc -> AbilityHud.ready(false)) == Specialization.BLADE, "the HUD does not show Riposte as ready with a sword");
			ctx.takeScreenshot("08_ability_ready");
			ctx.getInput().pressKey(o -> ExtremeProgressionClient.ABILITY);
			ctx.waitTicks(10);
			check(ctx.computeOnClient(mc -> AbilityHud.ready(false)) == null, "Riposte still shows as ready after R");
			check(ctx.computeOnClient(mc -> ClientProgress.cooldown("melee", "blade")) > 0, "using Riposte started no cooldown");
			ctx.takeScreenshot("09_ability_used");
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
		for (TreeRules.Result r : TreeRules.Result.values()) {
			if (r != TreeRules.Result.OK) {
				keys.add("message.extremeprogression.tree." + r.name().toLowerCase(java.util.Locale.ROOT));
			}
		}
		keys.addAll(List.of("message.extremeprogression.tree.chosen", "message.extremeprogression.tree.respec", "message.extremeprogression.ability.none",
				"message.extremeprogression.ability.none_second", "message.extremeprogression.ability.no_rank", "message.extremeprogression.ability.cooldown",
				"message.extremeprogression.ability.used", "message.extremeprogression.admin_respec", "gui.extremeprogression.node.passive",
				"gui.extremeprogression.node.desc.ability", "gui.extremeprogression.respec.loss", "key.extremeprogression.ability"));
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
