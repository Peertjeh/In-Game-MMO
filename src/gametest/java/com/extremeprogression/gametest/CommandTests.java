package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.SkeletonTests.check;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.skill.Skill;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** {@code /skills}: the overview for everyone, the admin tools for operators only. */
public class CommandTests {
	private static int run(GameTestHelper helper, String command, CommandSourceStack source) throws CommandSyntaxException {
		return helper.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
	}

	@GameTest
	public void operatorsSetLevelsAndGiveXp(GameTestHelper helper) throws Exception {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "set");
		MinecraftServer server = helper.getLevel().getServer();
		String name = p.getName().getString();
		check(helper, run(helper, "skills admin set " + name + " melee 20", server.createCommandSourceStack()) == 1, "set did not succeed");
		check(helper, PlayerStore.get(p.getUUID()).get(Skill.MELEE).level == 20, "set did not give level 20");
		check(helper, run(helper, "skills admin add " + name + " melee 1000000", server.createCommandSourceStack()) == 1, "add did not succeed");
		int level = PlayerStore.get(p.getUUID()).get(Skill.MELEE).level;
		check(helper, level > 20, "a million XP from level 20 gave no level: %s", level);
		run(helper, "skills admin add " + name + " melee -100000000", server.createCommandSourceStack());
		check(helper, PlayerStore.get(p.getUUID()).get(Skill.MELEE).level == 0, "removing XP did not go down to level 0");
		check(helper, run(helper, "skills admin set " + name + " necromancy 5", server.createCommandSourceStack()) == 0, "an unknown skill was accepted");
		check(helper, run(helper, "skills admin set nobody_here melee 5", server.createCommandSourceStack()) == 0, "an absent player was accepted");
		helper.succeed();
	}

	@GameTest
	public void operatorsMakeEveryAbilityReadyAgain(GameTestHelper helper) throws Exception {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "cool");
		MinecraftServer server = helper.getLevel().getServer();
		PlayerStore.get(p.getUUID()).get(Skill.MELEE).cooldowns.put("blade", Long.MAX_VALUE);
		PlayerStore.get(p.getUUID()).get(Skill.MINING).cooldowns.put("prospector", Long.MAX_VALUE);
		check(helper, run(helper, "skills admin cooldowns " + p.getName().getString(), server.createCommandSourceStack()) == 1, "cooldowns did not succeed");
		check(helper, PlayerStore.get(p.getUUID()).get(Skill.MELEE).cooldowns.isEmpty() && PlayerStore.get(p.getUUID()).get(Skill.MINING).cooldowns.isEmpty(),
				"a cooldown was left");
		helper.succeed();
	}

	@GameTest
	public void playersSeeTheirOverviewButNotTheAdminTools(GameTestHelper helper) throws Exception {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "plain");
		check(helper, run(helper, "skills", p.createCommandSourceStack()) == 1, "/skills did not answer a player");
		boolean blocked;
		try {
			run(helper, "skills admin set " + p.getName().getString() + " melee 100", p.createCommandSourceStack());
			blocked = false;
		} catch (CommandSyntaxException e) {
			blocked = true;
		}
		check(helper, blocked, "a player who is not an operator used /skills admin");
		check(helper, PlayerStore.get(p.getUUID()).get(Skill.MELEE).level == 0, "a player's own admin attempt changed their level");
		helper.succeed();
	}
}
