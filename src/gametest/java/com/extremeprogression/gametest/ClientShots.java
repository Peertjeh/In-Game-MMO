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
import net.minecraft.network.chat.Component;
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
			// Both columns in line: Survival's Exploration beside Crafting's Smithing
			ctx.runOnClient(mc -> {
				java.util.Map<Skill, Integer> top = new java.util.EnumMap<>(Skill.class);
				for (var child : mc.gui.screen().children()) {
					if (child instanceof com.extremeprogression.client.screen.SkillTile t) {
						top.put(t.skill(), t.getY());
					}
				}
				check(top.size() == Skill.values().length && top.get(Skill.EXPLORATION).equals(top.get(Skill.SMITHING))
						&& top.get(Skill.MELEE).equals(top.get(Skill.MINING)), "the sheet's columns are not in line: " + top);
			});

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

			// Mastery (milestone 8): Arcana's two ranks wait for their perks; the first is chosen from the screen
			int masteries = ctx.computeOnClient(mc -> com.extremeprogression.client.screen.MasteryScreen.opened());
			ctx.runOnClient(mc -> mc.gui.setScreen(new com.extremeprogression.client.screen.MasteryScreen(Skill.ARCANA, null)));
			ctx.waitTicks(5);
			check(ctx.computeOnClient(mc -> com.extremeprogression.client.screen.MasteryScreen.opened()) > masteries, "the Mastery screen did not open");
			ctx.runOnClient(mc -> com.extremeprogression.client.screen.SkillScreen.send(
					new com.extremeprogression.network.TreePayload(com.extremeprogression.network.TreePayload.Action.PERK, "arcana", "a")));
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				ProgressPayload.Entry arcana = ClientProgress.skill("arcana");
				check(arcana.perks().equals(List.of("a")), "the client shows the perks " + arcana.perks() + ", not [a]");
			});
			ctx.getInput().setCursorPos(1, 1);
			ctx.waitTicks(2);
			ctx.takeScreenshot("05_mastery_arcana");
			// The opposing skills press: Melee 47 on Ranged 22, Defense 38 on Exploration 18, Arcana 100 on Smithing 2
			ctx.runOnClient(mc -> {
				check(Math.abs(com.extremeprogression.client.ui.SkillView.opposingPenalty(Skill.RANGED) - 0.6 * 25 / 50) < 1e-9, "Ranged does not show its penalty");
				check(Math.abs(com.extremeprogression.client.ui.SkillView.opposingPenalty(Skill.SMITHING) - 0.6) < 1e-9, "Smithing does not show its full penalty");
				check(com.extremeprogression.client.ui.SkillView.opposingPenalty(Skill.MELEE) == 0, "Melee shows a penalty");
			});
			ctx.runOnClient(mc -> mc.gui.setScreen(new SkillScreen(Skill.SMITHING, null)));
			ctx.waitTicks(10);
			ctx.takeScreenshot("05_skill_smithing_opposed");

			// Synergies: Mining 61 with Exploration 18 shows Deep Walker's runes; Melee 47 and Defense 38 discover Counterstrike
			sp.getServer().runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().get(0);
				com.extremeprogression.synergy.SynergyEffects.blocked(player);
				net.minecraft.world.entity.LivingEntity zombie = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(player.level(),
						net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				com.extremeprogression.synergy.SynergyEffects.damage(zombie, player.damageSources().playerAttack(player), 10);
			});
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				check(com.extremeprogression.client.state.ClientSynergies.known(com.extremeprogression.synergy.Synergy.COUNTERSTRIKE),
						"the client does not know the discovered Counterstrike");
				check(com.extremeprogression.client.screen.CodexScreen.stage(com.extremeprogression.synergy.Synergy.DEEP_WALKER) == 1,
						"Deep Walker does not show as runes with a vague hint");
				check(com.extremeprogression.client.screen.CodexScreen.stage(com.extremeprogression.synergy.Synergy.GREEN_THUMB) == 0,
						"Green Thumb shows although neither skill is at 30");
				mc.gui.setScreen(new com.extremeprogression.client.screen.CodexScreen(null));
			});
			ctx.waitTicks(10);
			ctx.getInput().setCursorPos(1, 1);
			ctx.waitTicks(2);
			ctx.takeScreenshot("05_codex");
			ctx.runOnClient(mc -> ((com.extremeprogression.client.screen.CodexScreen) mc.gui.screen()).show(true));
			ctx.waitTicks(5);
			ctx.takeScreenshot("05_chronicle");
			ctx.runOnClient(mc -> ((com.extremeprogression.client.screen.CodexScreen) mc.gui.screen()).tab(1));
			ctx.waitTicks(5);
			ctx.takeScreenshot("05_codex_together");

			// The Party screen (design §16), with a party as the server sends one: you leading, Alex near and earning,
			// Steve offline, and two players to invite
			java.util.UUID alex = java.util.UUID.randomUUID();
			ctx.runOnClient(mc -> {
				com.extremeprogression.client.state.ClientParty.apply(new com.extremeprogression.network.PartyPayload(true, 5, 0.05, 0.15, List.of(
						new com.extremeprogression.network.PartyPayload.Member(mc.player.getUUID(), mc.player.getName().getString(), true, true, false, false, 312, 2),
						new com.extremeprogression.network.PartyPayload.Member(alex, "Alex", false, true, true, true, 187, 0),
						new com.extremeprogression.network.PartyPayload.Member(java.util.UUID.randomUUID(), "Steve", false, false, false, false, 95, 0)),
						List.of(), List.of("Notch", "Jeb")));
				mc.gui.setScreen(new com.extremeprogression.client.screen.PartyScreen(null));
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("05_party");
			ctx.runOnClient(mc -> {
				check(com.extremeprogression.client.screen.PartyScreen.opened() > 0, "the Party screen did not open");
				check(com.extremeprogression.client.state.ClientParty.member(alex) && !com.extremeprogression.client.state.ClientParty.member(mc.player.getUUID()),
						"the client does not know who is in its party");
				Component mark = com.extremeprogression.client.state.ClientParty.mark(alex);
				check(mark != null && mark.getString().equals("◆"), "a party member's name tag has no mark");
				com.extremeprogression.client.state.ClientParty.clear();
				mc.gui.setScreen(null);
			});

			// The power level beside the name above a player's head: the client knows everyone's, and hides it where told
			ctx.runOnClient(mc -> {
				Component badge = com.extremeprogression.client.state.ClientPower.badge(mc.player.getUUID());
				check(badge != null && badge.getString().equals("✦312★"), "the name tag badge is " + (badge == null ? "missing" : badge.getString()) + ", not ✦312★");
				String hidden = Config.NAMETAG_HIDDEN_DIMENSIONS;
				Config.NAMETAG_HIDDEN_DIMENSIONS = "battlehub:hub, minecraft:overworld";
				try {
					check(com.extremeprogression.client.state.ClientPower.badge(mc.player.getUUID()) == null, "the badge shows in a dimension that hides it");
				} finally {
					Config.NAMETAG_HIDDEN_DIMENSIONS = hidden;
				}
			});

			// Administration (milestone 9): the settings reached the client, the leaderboard, an XP event, an inspection
			check(ctx.computeOnClient(mc -> com.extremeprogression.client.state.ClientAdmin.settings() != null), "the client got no settings");
			ctx.runOnClient(mc -> mc.gui.setScreen(new com.extremeprogression.client.screen.LeaderboardScreen(new CharacterSheetScreen(null))));
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				var rows = com.extremeprogression.client.state.ClientAdmin.leaderboard();
				check(rows != null && rows.stream().anyMatch(r -> r.name().equals(mc.player.getName().getString()) && r.total() == 312),
						"the leaderboard does not show the player at 312");
			});
			ctx.getInput().setCursorPos(1, 1);
			ctx.waitTicks(2);
			ctx.takeScreenshot("05_leaderboard");
			sp.getServer().runOnServer(s -> {
				com.extremeprogression.xp.XpEvents.start(s, Skill.MINING, 2, 30, "test");
				ServerPlayer player = s.getPlayerList().getPlayers().get(0);
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
						com.extremeprogression.command.AdminCommands.inspectPayload(s, player.getUUID(), player.getName().getString()));
			});
			ctx.waitTicks(10);
			check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof com.extremeprogression.client.screen.InspectScreen), "the inspection did not open");
			ctx.getInput().setCursorPos(1, 1);
			ctx.waitTicks(2);
			ctx.takeScreenshot("05_inspect");
			ctx.runOnClient(mc -> {
				check(com.extremeprogression.client.state.ClientAdmin.events().size() == 1, "the client does not know the XP event");
				mc.gui.setScreen(new CharacterSheetScreen(null));
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("05_character_sheet_xp_event");
			sp.getServer().runOnServer(s -> com.extremeprogression.config.Settings.send(s.getPlayerList().getPlayers().get(0), true));
			ctx.waitTicks(10);
			check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof com.extremeprogression.client.screen.SettingsScreen), "the settings screen did not open");
			ctx.getInput().setCursorPos(1, 1);
			ctx.waitTicks(2);
			ctx.takeScreenshot("05_settings");
			sp.getServer().runOnServer(s -> com.extremeprogression.xp.XpEvents.clear());
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(5);

			// The inventory has the Character Sheet's button
			InventoryScreen[] inventory = new InventoryScreen[1];
			ctx.runOnClient(mc -> {
				inventory[0] = new InventoryScreen(mc.player);
				mc.gui.setScreen(inventory[0]);
			});
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> {
				var widgets = Screens.getWidgets(inventory[0]);
				var sheet = widgets.stream().filter(w -> w instanceof SheetButton).findFirst().orElse(null);
				check(sheet != null, "the inventory has no Character Sheet button");
				// Inside the inventory's own panel (176 × 166)
				var panel = (com.extremeprogression.client.mixin.ContainerScreenAccess) inventory[0];
				int left = panel.extremeprogression$leftPos(), top = panel.extremeprogression$topPos();
				check(sheet.getX() >= left && sheet.getX() + sheet.getWidth() <= left + 176 && sheet.getY() >= top && sheet.getY() + sheet.getHeight() <= top + 166,
						"the Character Sheet button lies outside the inventory, at " + (sheet.getX() - left) + ", " + (sheet.getY() - top));
				// Beside the recipe book, and beside Extreme Dimensions' buttons when that mod is there: never over another
				for (var w : widgets) {
					if (w != sheet && w.visible && w.getX() < sheet.getX() + sheet.getWidth() && sheet.getX() < w.getX() + w.getWidth()
							&& w.getY() < sheet.getY() + sheet.getHeight() && sheet.getY() < w.getY() + w.getHeight()) {
						throw new AssertionError("the Character Sheet button lies over " + w.getClass().getSimpleName() + " at " + w.getX() + ", " + w.getY());
					}
				}
				if (Boolean.getBoolean("extremeprogression.compat")) {
					check(FabricLoader.getInstance().isModLoaded("extremedimensions"), "the compatibility run did not load Extreme Dimensions");
				}
			});
			ctx.takeScreenshot("06_inventory_button");

			// The respec costs are shown before anything happens
			ctx.runOnClient(mc -> mc.gui.setScreen(new RespecScreen(Skill.MELEE, null)));
			ctx.waitTicks(10);
			ctx.takeScreenshot("07_respec_confirmation");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));

			// Tracking: Melee from its Skill Screen's Track button, Mining and Arcana too; they show right of the hotbar
			ctx.runOnClient(mc -> {
				SkillScreen screen = new SkillScreen(Skill.MELEE, null);
				mc.gui.setScreen(screen);
				screen.toggleTrack();
				com.extremeprogression.client.state.ClientOptions.toggle(Skill.MINING);
				com.extremeprogression.client.state.ClientOptions.toggle(Skill.ARCANA);
				check(com.extremeprogression.client.state.ClientOptions.tracked().equals(List.of(Skill.MELEE, Skill.MINING, Skill.ARCANA)),
						"tracked are " + com.extremeprogression.client.state.ClientOptions.tracked());
				mc.gui.setScreen(null);
			});
			check(java.nio.file.Files.exists(FabricLoader.getInstance().getConfigDir().resolve("extremeprogression-client.json")), "the tracked skills were not saved");
			check(ctx.computeOnClient(mc -> com.extremeprogression.client.hud.TrackedHud.shown()) == 3, "the HUD does not show the three tracked skills");

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

			// Ore Sense (Prospector): the ores in the ground ahead glow through it, on this client only
			sp.getServer().runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().get(0);
				PlayerProgress pr = PlayerStore.get(player.getUUID());
				TreeRules.choose(pr, Specialization.PROSPECTOR);
				TreeRules.buy(pr, Specialization.PROSPECTOR, NodeSlot.A1);
				Sync.send(player);
				net.minecraft.core.BlockPos feet = player.blockPosition();
				for (int i = -2; i <= 2; i++) {
					player.level().setBlockAndUpdate(feet.offset(i * 2, -3, 5), net.minecraft.world.level.block.Blocks.DIAMOND_ORE.defaultBlockState());
					player.level().setBlockAndUpdate(feet.offset(i * 2 + 1, -4, 7), net.minecraft.world.level.block.Blocks.GOLD_ORE.defaultBlockState());
				}
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
			});
			ctx.runOnClient(mc -> {
				mc.player.setYRot(0);
				mc.player.setXRot(40);
			});
			ctx.waitTicks(10);
			ctx.getInput().pressKey(o -> ExtremeProgressionClient.ABILITY);
			ctx.waitTicks(10);
			int glowing = ctx.computeOnClient(mc -> com.extremeprogression.client.OreSense.shown());
			check(glowing >= 10, "Ore Sense shows " + glowing + " ores, not the 10 placed");
			ctx.takeScreenshot("10_ore_sense");

			// Arcane Insight (Enchanter): every enchantment of the offers beside the enchanting table, with a free reroll
			sp.getServer().runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().get(0);
				PlayerProgress pr = PlayerStore.get(player.getUUID());
				TreeRules.choose(pr, Specialization.ENCHANTER);
				TreeRules.buy(pr, Specialization.ENCHANTER, NodeSlot.A1);
				Sync.send(player);
				player.giveExperienceLevels(30);
				player.level().setBlockAndUpdate(player.blockPosition().south(2), net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE.defaultBlockState());
			});
			ctx.runOnClient(mc -> {
				mc.player.setYRot(0);
				mc.player.setXRot(35);
			});
			ctx.waitTicks(10);
			ctx.getInput().pressKey(o -> ExtremeProgressionClient.ABILITY);
			ctx.waitTicks(10);
			check(ctx.computeOnClient(mc -> ClientProgress.activeTicks("enchanter")) > 0, "Arcane Insight was not used looking at the enchanting table");
			sp.getServer().runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().get(0);
				net.minecraft.core.BlockPos table = player.blockPosition().south(2);
				player.openMenu(player.level().getBlockState(table).getMenuProvider(player.level(), table));
				player.containerMenu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
				player.containerMenu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
			});
			ctx.waitTicks(10);
			int insight = ctx.computeOnClient(mc -> com.extremeprogression.client.inventory.InsightPanel.shown());
			check(insight > 0, "the Insight panel shows no enchantments");
			ctx.takeScreenshot("11_arcane_insight");
			sp.getServer().runOnServer(s -> s.getPlayerList().getPlayers().get(0).closeContainer());
			ctx.waitTicks(5);

			// The anvil screen (Preservation's "Too Expensive!" limit is read there): it opens and draws
			ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.AnvilScreen(
					new net.minecraft.world.inventory.AnvilMenu(0, mc.player.getInventory()), mc.player.getInventory(),
					net.minecraft.network.chat.Component.translatable("container.repair"))));
			ctx.waitTicks(5);
			check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AnvilScreen), "the anvil screen did not open");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(5);

			// A death (milestone 7): two earlier ones in the history, death pressure 1, then a real death in the Overworld
			sp.getServer().runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().get(0);
				PlayerProgress pr = PlayerStore.get(player.getUUID());
				pr.death.history.add(record(System.currentTimeMillis() - 3L * 86_400_000, "minecraft:the_end", 2, 2.0, "mining", 62, 61, 0));
				pr.death.history.add(record(System.currentTimeMillis() - 3_600_000, "minecraft:the_nether", 0, 1.5, "mining", 61, 61, 31200));
				pr.death.deaths = 2;
				pr.death.pressure = 1;
				pr.death.activeTicks = 20 * 60 * 22;
				Sync.send(player);
				com.extremeprogression.death.DeathService.sendHistory(player, null);
			});
			ctx.waitTicks(5);
			java.util.List<com.extremeprogression.data.DeathData.Loss> stake = ctx.computeOnClient(mc -> com.extremeprogression.client.screen.DeathRegisterScreen.atStake());
			ctx.runOnClient(mc -> mc.gui.setScreen(new com.extremeprogression.client.screen.DeathRegisterScreen(new CharacterSheetScreen(null))));
			ctx.waitTicks(10);
			// (the cursor in a corner, where it shows no tooltip over the screen: opening a screen centres it)
			ctx.getInput().setCursorPos(1, 1);
			ctx.waitTicks(2);
			ctx.takeScreenshot("12_death_register_before");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			double miningBefore = ctx.computeOnClient(mc -> ClientProgress.skill("mining").xp());
			int summaries = ctx.computeOnClient(mc -> com.extremeprogression.client.screen.DeathSummaryScreen.opened());
			sp.getServer().runOnServer(s -> {
				ServerPlayer player = s.getPlayerList().getPlayers().get(0);
				player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
				player.kill((net.minecraft.server.level.ServerLevel) player.level());
			});
			ctx.waitTicks(20);
			check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.DeathScreen), "the death screen did not show");
			// (as the death screen's Respawn button does)
			ctx.runOnClient(mc -> {
				mc.player.respawn();
				mc.gui.setScreen(null);
			});
			ctx.waitTicks(40);
			check(ctx.computeOnClient(mc -> com.extremeprogression.client.screen.DeathSummaryScreen.opened()) > summaries, "no summary was shown after respawning");
			ctx.runOnClient(mc -> {
				ProgressPayload.Death d = ClientProgress.death();
				check(d.deaths() == 3 && d.pressure() == 2, "after the death the client shows " + d.deaths() + " deaths at pressure " + d.pressure());
				check(ClientProgress.fatigueLeft() > 0, "the client shows no death fatigue after the death");
				check(com.extremeprogression.spec.Specs.localFatigued.getAsBoolean(), "the client's own effects do not know about the fatigue");
				check(ClientProgress.deathHistory().size() == 3, "the client's history holds " + ClientProgress.deathHistory().size() + " deaths, not 3");
				// "At Stake" before the death was what the death took
				com.extremeprogression.data.DeathData.Record last = ClientProgress.deathHistory().get(2);
				for (com.extremeprogression.data.DeathData.Loss l : last.losses) {
					double shown = stake.stream().filter(x -> x.skill.equals(l.skill)).findFirst().orElseThrow().xp;
					check(Math.abs(shown - l.xp) < 1e-6, "At Stake showed " + shown + " XP for " + l.skill + ", the death took " + l.xp);
				}
				double miningAfter = ClientProgress.skill("mining").xp();
				check(miningAfter < miningBefore, "Mining did not lose XP: " + miningBefore + " then " + miningAfter);
			});
			ctx.takeScreenshot("13_death_summary");
			ctx.runOnClient(mc -> mc.gui.setScreen(new com.extremeprogression.client.screen.DeathRegisterScreen(null)));
			ctx.waitTicks(10);
			ctx.getInput().setCursorPos(1, 1);
			ctx.waitTicks(2);
			ctx.takeScreenshot("14_death_register_after");
			ctx.runOnClient(mc -> mc.gui.setScreen(new CharacterSheetScreen(null)));
			ctx.waitTicks(10);
			ctx.takeScreenshot("15_character_sheet_fatigued");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(5);
		}
	}

	/** An earlier death for the history, with one skill's loss. */
	private static com.extremeprogression.data.DeathData.Record record(long time, String dimension, int pressure, double multiplier, String skill,
			int levelBefore, int levelAfter, double xp) {
		com.extremeprogression.data.DeathData.Record r = new com.extremeprogression.data.DeathData.Record();
		r.time = time;
		r.dimension = dimension;
		r.x = 120;
		r.y = 64;
		r.z = -340;
		r.cause = "Steve fell from a high place";
		r.pressure = pressure;
		r.multiplier = multiplier;
		com.extremeprogression.data.DeathData.Loss l = new com.extremeprogression.data.DeathData.Loss();
		l.skill = skill;
		l.levelBefore = levelBefore;
		l.levelAfter = levelAfter;
		l.xp = xp;
		r.losses.add(l);
		return r;
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
		// The nodes of all thirty specializations (milestone 6)
		for (Specialization spec : Specialization.values()) {
			{
				keys.add(spec.translationKey() + ".ability.ranks");
				for (String slot : new String[] {"p1", "p2", "p3", "p4", "c1", "c2"}) {
					keys.add(spec.translationKey() + "." + slot);
					keys.add(spec.translationKey() + "." + slot + ".desc");
				}
			}
		}
		keys.addAll(List.of("gui.extremeprogression.node.desc.ability_key", "gui.extremeprogression.node.desc.choice_rule", "gui.extremeprogression.node.light_only",
				"gui.extremeprogression.insight.title", "gui.extremeprogression.insight.offer", "gui.extremeprogression.insight.reroll",
				"item.minecraft.potion.effect.haste", "item.minecraft.splash_potion.effect.resistance", "item.minecraft.lingering_potion.effect.absorption"));
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
				"message.extremeprogression.mastery_up", "message.extremeprogression.fishing.not_open_water", "message.extremeprogression.fishing.overfished",
				"message.extremeprogression.death.lost", "message.extremeprogression.mastery.chosen", "gui.extremeprogression.skill.opposing_penalty"));
		for (com.extremeprogression.mastery.MasteryPerk perk : com.extremeprogression.mastery.MasteryPerk.values()) {
			keys.add(perk.translationKey());
			keys.add(perk.translationKey() + ".desc");
		}
		for (String k : new String[] {"title", "button", "button_waiting", "button.tip", "locked_short", "locked", "rank", "next", "maxed", "row", "choose", "later",
				"weaker", "final"}) {
			keys.add("gui.extremeprogression.mastery." + k);
		}
		for (com.extremeprogression.synergy.Synergy syn : com.extremeprogression.synergy.Synergy.values()) {
			for (String part : new String[] {"", ".desc", ".hint1", ".hint2"}) {
				keys.add(syn.translationKey() + part);
			}
		}
		for (String k : new String[] {"codex.button", "codex.title", "codex.tab", "chronicle.tab", "codex.count", "codex.out_of_reach", "codex.out_of_reach_tip",
				"codex.unknown_skills", "codex.found", "codex.first_discoverer", "codex.first_by", "codex.dormant", "codex.found_by_someone", "codex.needs",
				"codex.footer", "chronicle.nobody", "chronicle.first", "chronicle.footer", "codex.together_tab", "codex.together_footer", "codex.roles",
				"codex.found_with", "chronicle.count", "chronicle.alone", "chronicle.together", "death.button"}) {
			keys.add("gui.extremeprogression." + k);
		}
		// Parties (1.4.0)
		for (String k : new String[] {"title", "button", "summary", "alone", "off", "members", "legend", "invitations", "no_invitations", "invites_you",
				"invite_header", "invite", "invite_tip", "full", "nobody", "leave", "lead", "leader", "lead_tip", "remove_tip", "accept_tip", "decline_tip", "level",
				"state.you", "state.active", "state.near", "state.far", "state.offline"}) {
			keys.add("gui.extremeprogression.party." + k);
		}
		for (com.extremeprogression.party.Parties.Result r : com.extremeprogression.party.Parties.Result.values()) {
			if (r != com.extremeprogression.party.Parties.Result.OK) {
				keys.add(r.key());
			}
		}
		for (String k : new String[] {"invited_you", "accept_button", "decline_button", "invited", "joined", "declined", "you_declined", "left", "you_left",
				"removed", "you_were_removed", "new_leader", "disbanded", "none", "waiting", "header", "state.you", "state.active", "state.near", "state.far",
				"state.offline", "state.leader"}) {
			keys.add("message.extremeprogression.party." + k);
		}
		keys.add("message.extremeprogression.synergy.discovered_together");
		keys.add("message.extremeprogression.synergy.first_together");
		for (String k : new String[] {"leaderboard.title", "leaderboard.button", "leaderboard.total", "leaderboard.waiting", "leaderboard.players",
				"leaderboard.level", "inspect.title", "inspect.online", "inspect.offline", "inspect.synergies", "inspect.read_only", "inspect.no_spec",
				"inspect.points", "settings.title", "settings.button", "settings.preset.hardcore", "settings.preset.normal", "settings.preset.custom",
				"settings.search", "settings.save", "settings.revert", "settings.invalid", "settings.sent", "settings.waiting", "settings.preset", "settings.page",
				"settings.read_only", "sheet.xp_event", "sheet.xp_event_all", "death.step2_off"}) {
			keys.add("gui.extremeprogression." + k);
		}
		keys.addAll(List.of("message.extremeprogression.synergy.discovered", "message.extremeprogression.synergy.first",
				"message.extremeprogression.synergy.first_you", "message.extremeprogression.tree.no_perk_waiting"));
		// The Death Register and the summary (milestone 7)
		for (String k : new String[] {"title", "deaths", "pressure", "step0", "step1", "step2", "no_pressure", "drops_in", "fatigue", "none", "fatigue_effect",
				"per_dimension", "dimension.minecraft.overworld", "dimension.minecraft.the_nether", "dimension.minecraft.the_end",
				"dimension.extremedimensions.the_seam", "permanent", "permanent_hint", "stake", "stake_basis", "stake_tip_share", "stake_tip_after",
				"stake_tip_after_mastery", "short_rank", "short_level", "whole_level", "whole_rank", "warning_now", "warning_next", "warning_base", "history",
				"history_scroll", "history_empty", "details_place", "minutes", "hours", "hours_whole", "today", "yesterday", "lost_rank", "lost_level", "lost_xp",
				"lost_mastery_xp", "lost_nothing", "and_more", "summary.title", "summary.continue", "summary.place", "summary.lost", "summary.fatigue",
				"summary.pressure", "summary.pressure_whole"}) {
			keys.add("gui.extremeprogression.death." + k);
		}
		List<String> missing = new ArrayList<>();
		for (String k : keys) {
			if (!Language.getInstance().has(k)) {
				missing.add(k);
			}
		}
		check(missing.isEmpty(), "missing texts: " + missing);
	}
}
