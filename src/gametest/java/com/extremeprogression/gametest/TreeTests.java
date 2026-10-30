package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.SkeletonTests.check;

import java.nio.file.Files;
import java.nio.file.Path;

import com.extremeprogression.ability.AbilityService;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.network.TreePayload;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.skill.Specialization;
import com.extremeprogression.tree.NodeSlot;
import com.extremeprogression.tree.TreeRules;
import com.extremeprogression.tree.TreeService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Milestone 5: specializations, points, nodes, respec and the ability key (design §6). */
public class TreeTests {
	@GameTest
	public void aBranchCostsMoreThanASkillEverEarns(GameTestHelper helper) {
		Config.reset();
		check(helper, NodeSlot.fullBranch() > Config.MAX_LEVEL - Config.FIRST_SPECIALIZATION_LEVEL, "a full branch costs %s, not more than %s",
				NodeSlot.fullBranch(), Config.MAX_LEVEL - Config.FIRST_SPECIALIZATION_LEVEL);
		for (NodeSlot slot : NodeSlot.values()) {
			check(helper, slot.cost >= 1 && slot.cost <= 3, "%s costs %s points a rank, not 1 to 3", slot, slot.cost);
		}
		helper.succeed();
	}

	@GameTest
	public void choosingAndBuyingFollowTheRules(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "tree");
		PlayerProgress pr = PlayerStore.get(p.getUUID());
		check(helper, TreeService.handle(p, new TreePayload(TreePayload.Action.CHOOSE, "melee", "blade")) == TreeRules.Result.LEVEL,
				"a specialization was chosen below level 25");
		Progression.setLevel(pr, Skill.MELEE, 30);
		check(helper, TreeService.handle(p, new TreePayload(TreePayload.Action.CHOOSE, "melee", "blade")) == TreeRules.Result.OK, "Blade was not chosen at 30");
		check(helper, TreeService.handle(p, new TreePayload(TreePayload.Action.CHOOSE, "melee", "crusher")) == TreeRules.Result.LEVEL,
				"a second specialization was chosen before level 75");
		check(helper, TreeService.handle(p, new TreePayload(TreePayload.Action.BUY, "melee", "blade/a2")) == TreeRules.Result.REQUIRES,
				"rank II was bought before rank I");
		check(helper, TreeService.handle(p, new TreePayload(TreePayload.Action.BUY, "melee", "blade/a1")) == TreeRules.Result.OK, "rank I was not bought");
		check(helper, TreeService.handle(p, new TreePayload(TreePayload.Action.BUY, "melee", "crusher/p1")) == TreeRules.Result.NOT_CHOSEN,
				"a node of an unchosen specialization was bought");
		check(helper, TreeService.handle(p, new TreePayload(TreePayload.Action.BUY, "melee", "blade/a2")) == TreeRules.Result.POINTS,
				"rank II was bought without enough points (5 earned, 3 spent)");
		Progression.setLevel(pr, Skill.MELEE, 40);
		TreeService.handle(p, new TreePayload(TreePayload.Action.BUY, "melee", "blade/p1"));
		check(helper, TreeService.handle(p, new TreePayload(TreePayload.Action.BUY, "melee", "blade/c1")) == TreeRules.Result.OK, "choice A was not bought");
		check(helper, TreeService.handle(p, new TreePayload(TreePayload.Action.BUY, "melee", "blade/c2")) == TreeRules.Result.OTHER_CHOICE,
				"both sides of a choice were bought");
		check(helper, TreeService.handle(p, new TreePayload(TreePayload.Action.BUY, "melee", "blade/a4")) == TreeRules.Result.LEVEL
				|| TreeRules.canBuy(pr, Specialization.BLADE, NodeSlot.A4) != TreeRules.Result.OK, "rank IV was buyable below level 60");

		// Conflicts across skills: Bulwark (Defense) shuts Brawler (Melee) out
		Progression.setLevel(pr, Skill.DEFENSE, 25);
		TreeService.handle(p, new TreePayload(TreePayload.Action.CHOOSE, "defense", "bulwark"));
		Progression.setLevel(pr, Skill.MELEE, 75);
		check(helper, TreeService.handle(p, new TreePayload(TreePayload.Action.CHOOSE, "melee", "brawler")) == TreeRules.Result.CONFLICT,
				"Brawler was chosen next to Bulwark");
		check(helper, TreeService.handle(p, new TreePayload(TreePayload.Action.CHOOSE, "melee", "crusher")) == TreeRules.Result.OK,
				"Crusher was not chosen as the second specialization at 75");
		helper.succeed();
	}

	@GameTest
	public void lostLevelsSetTheLastPointsAsideUntilTheyAreWonBack(GameTestHelper helper) {
		Config.reset();
		PlayerProgress pr = new PlayerProgress();
		Progression.setLevel(pr, Skill.MINING, 40);
		TreeRules.choose(pr, Specialization.PROSPECTOR);
		for (NodeSlot slot : new NodeSlot[] {NodeSlot.A1, NodeSlot.P1, NodeSlot.A2, NodeSlot.P2, NodeSlot.P3}) {
			check(helper, TreeRules.buy(pr, Specialization.PROSPECTOR, slot) == TreeRules.Result.OK, "%s could not be bought", slot);
		}
		SkillData d = pr.get(Skill.MINING);
		check(helper, TreeRules.spent(d) == 13 && TreeRules.free(d) == 2, "spent %s, free %s", TreeRules.spent(d), TreeRules.free(d));
		// Down to level 33 (8 points): the last bought, P3 (3) and P2 (2), go aside, newest first
		Progression.setLevel(pr, Skill.MINING, 33);
		check(helper, d.level == 33 && d.bought.size() == 3 && d.suspended.equals(java.util.List.of("prospector/p3", "prospector/p2")),
				"at level %s: bought %s, aside %s", d.level, d.bought, d.suspended);
		// Back up: they return by themselves, to the same nodes
		Progression.setLevel(pr, Skill.MINING, 40);
		check(helper, d.suspended.isEmpty() && d.bought.equals(java.util.List.of("prospector/a1", "prospector/p1", "prospector/a2", "prospector/p2", "prospector/p3")),
				"the points did not come back in order: %s", d.bought);
		// Below 25 the specialization is dormant; it is not lost
		Progression.setLevel(pr, Skill.MINING, 20);
		check(helper, TreeRules.active(d, 0) == null && d.specs.contains("prospector"), "Prospector is not dormant but %s at level 20", d.specs);
		helper.succeed();
	}

	@GameTest
	public void aRespecCostsAFifthOfTheXpAndWaitsAWeek(GameTestHelper helper) throws Exception {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "respec");
		PlayerProgress pr = PlayerStore.get(p.getUUID());
		Progression.setLevel(pr, Skill.FISHING, 100);
		TreeRules.choose(pr, Specialization.ANGLER);
		TreeRules.buy(pr, Specialization.ANGLER, NodeSlot.A1);
		check(helper, TreeService.handle(p, new TreePayload(TreePayload.Action.RESPEC, "fishing", "")) == TreeRules.Result.OK, "the respec was refused");
		SkillData d = pr.get(Skill.FISHING);
		check(helper, d.level >= 88 && d.level <= 94 && d.specs.isEmpty() && d.bought.isEmpty(), "after a respec at 100: level %s, specs %s, bought %s",
				d.level, d.specs, d.bought);
		check(helper, d.respecReadyAt - System.currentTimeMillis() > 6L * 24 * 3600 * 1000, "the waiting time did not start");
		TreeRules.choose(pr, Specialization.HOOKMASTER);
		check(helper, TreeService.handle(p, new TreePayload(TreePayload.Action.RESPEC, "fishing", "")) == TreeRules.Result.WAITING,
				"a second respec was allowed within a week");

		// An operator's respec is free, and the player is told
		int level = d.level;
		int ok = helper.getLevel().getServer().getCommands().getDispatcher().execute("skills admin respec " + p.getName().getString() + " fishing",
				helper.getLevel().getServer().createCommandSourceStack());
		check(helper, ok == 1 && d.specs.isEmpty() && d.level == level, "the free respec failed or cost levels: %s, level %s", ok, d.level);

		// Saved and loaded (schema 3), and an older file loads with nothing chosen
		Path file = Files.createTempDirectory("ep-tree").resolve("player.json");
		TreeRules.choose(pr, Specialization.TREASURE_HUNTER);
		PlayerStore.write(file, pr);
		check(helper, PlayerStore.read(file).get(Skill.FISHING).specs.equals(java.util.List.of("treasure_hunter")), "the choice was not saved");
		Path old = file.resolveSibling("old.json");
		Files.writeString(old, "{\"schema\":2,\"skills\":{\"fishing\":{\"level\":30}}}");
		PlayerProgress loaded = PlayerStore.read(old);
		check(helper, loaded.schema == PlayerProgress.SCHEMA && loaded.get(Skill.FISHING).level == 30 && loaded.get(Skill.FISHING).specs.isEmpty(),
				"a schema 2 file did not load");
		helper.succeed();
	}

	@GameTest
	public void theAbilityKeyFindsTheAbilityForWhatYouHoldAndCoolsDown(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "ability");
		PlayerProgress pr = PlayerStore.get(p.getUUID());
		Progression.setLevel(pr, Skill.MINING, 30);
		TreeRules.choose(pr, Specialization.PROSPECTOR);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
		check(helper, AbilityService.use(p, false) == AbilityService.Result.NO_RANK, "Ore Sense was used without rank I");
		TreeRules.buy(pr, Specialization.PROSPECTOR, NodeSlot.A1);
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Ore Sense was not used with a pickaxe");
		check(helper, AbilityService.use(p, false) == AbilityService.Result.COOLDOWN, "Ore Sense was used again during its cooldown");
		long ready = pr.get(Skill.MINING).cooldowns.get("prospector") - helper.getLevel().getServer().overworld().getGameTime();
		check(helper, ready > 5 * 60 * 20 - 40 && ready <= 5 * 60 * 20, "the cooldown is %s ticks, not 5 minutes", ready);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
		check(helper, AbilityService.use(p, false) == AbilityService.Result.NONE, "a sword reached Ore Sense");
		check(helper, AbilityService.use(p, true) == AbilityService.Result.NONE, "Shift + R found an ability without a second specialization");
		helper.succeed();
	}
}
