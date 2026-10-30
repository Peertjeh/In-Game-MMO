package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.SkeletonTests.check;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.extremeprogression.config.Config;
import com.extremeprogression.data.DeathData;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.death.DeathRules;
import com.extremeprogression.death.DeathService;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.skill.Specialization;
import com.extremeprogression.spec.Specs;
import com.extremeprogression.tree.NodeSlot;
import com.extremeprogression.tree.TreeRules;
import com.extremeprogression.trunk.Trunks;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Deaths (design §7): the loss layers and how they spread over the skills, the death pressure and its decay, the
 * dimensions' factors, death fatigue, and the edge cases of milestone 7: a death at level 0 or right at the start of a
 * level, Mastery going before levels, specializations falling dormant and coming back, points set aside in the right
 * order, the Seam without Extreme Dimensions, two deaths in one tick, and a server stopping right after a death.
 */
public class DeathTests {
	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1e-6;
	}

	private static DeathData.Loss loss(List<DeathData.Loss> losses, Skill s) {
		return losses.stream().filter(l -> l.skill.equals(s.id)).findFirst().orElseThrow();
	}

	private static PlayerProgress at(Object... levelsAndXp) {
		PlayerProgress p = new PlayerProgress();
		for (int i = 0; i < levelsAndXp.length; i += 3) {
			Skill s = (Skill) levelsAndXp[i];
			Progression.setLevel(p, s, (Integer) levelsAndXp[i + 1]);
			p.get(s).xp = ((Number) levelsAndXp[i + 2]).doubleValue();
		}
		return p;
	}

	// --- Layers 1 and 2, and how they spread -----------------------------------------------------

	@GameTest
	public void everySkillLosesByItsLevelTheHighestTheMost(GameTestHelper helper) {
		Config.reset();
		PlayerProgress p = at(Skill.MINING, 60, 1000, Skill.MELEE, 30, 1000, Skill.FARMING, 15, 1000, Skill.ARCANA, 0, 10);
		List<DeathData.Loss> lost = DeathRules.apply(p, DeathRules.base(0), DeathRules.whole(0));
		check(helper, near(p.get(Skill.MINING).xp, 500), "the highest skill kept %s of 1,000 XP at base 50%%, expected 500", p.get(Skill.MINING).xp);
		check(helper, near(p.get(Skill.MELEE).xp, 750), "a skill at half the highest level kept %s, expected 750", p.get(Skill.MELEE).xp);
		check(helper, near(p.get(Skill.FARMING).xp, 875), "a skill at a quarter kept %s, expected 875", p.get(Skill.FARMING).xp);
		check(helper, near(p.get(Skill.ARCANA).xp, 10), "a skill at level 0 lost XP while another skill is higher");
		check(helper, near(loss(lost, Skill.MINING).xp, 500) && loss(lost, Skill.MINING).levelAfter == 60, "the loss was not recorded as 500 XP and no level");
		check(helper, p.get(Skill.MINING).level == 60 && p.get(Skill.MELEE).level == 30, "a death at pressure 0 took a level");
		helper.succeed();
	}

	@GameTest
	public void pressureOneDoublesTheShareButNeverTakesALevel(GameTestHelper helper) {
		Config.reset();
		PlayerProgress p = at(Skill.MINING, 60, 1000, Skill.MELEE, 30, 1000);
		check(helper, near(DeathRules.base(1), 1.0) && !DeathRules.whole(1), "pressure 1 is not base 100%% without a whole level");
		DeathRules.apply(p, DeathRules.base(1), false);
		check(helper, near(p.get(Skill.MINING).xp, 0) && p.get(Skill.MINING).level == 60, "the highest skill did not lose exactly its level's XP");
		check(helper, near(p.get(Skill.MELEE).xp, 500), "half the level lost %s, expected 500", 1000 - p.get(Skill.MELEE).xp);
		// The End at pressure 1 is 200% of the step: still never more than the step
		PlayerProgress q = at(Skill.MINING, 60, 1000, Skill.MELEE, 30, 1000);
		DeathRules.apply(q, DeathRules.base(1) * DeathRules.multiplier("minecraft:the_end", false), false);
		check(helper, near(q.get(Skill.MINING).xp, 0) && q.get(Skill.MINING).level == 60, "a share over 100%% took more than the step");
		check(helper, near(q.get(Skill.MELEE).xp, 0) && q.get(Skill.MELEE).level == 30, "half the level ×2 did not take all of its step, and only that");
		helper.succeed();
	}

	@GameTest
	public void aDeathAtLevelZero(GameTestHelper helper) {
		Config.reset();
		// Every skill at level 0: each counts as the highest
		PlayerProgress p = at(Skill.MINING, 0, 40, Skill.MELEE, 0, 10);
		List<DeathData.Loss> lost = DeathRules.apply(p, DeathRules.base(0), false);
		check(helper, near(p.get(Skill.MINING).xp, 20) && near(p.get(Skill.MELEE).xp, 5), "at level 0 every skill should lose half: %s and %s",
				p.get(Skill.MINING).xp, p.get(Skill.MELEE).xp);
		// Pressure 2 at level 0: no level to take, and nothing below 0
		DeathRules.apply(p, DeathRules.base(2), DeathRules.whole(2));
		DeathRules.apply(p, DeathRules.base(2), DeathRules.whole(2));
		for (Skill s : Skill.values()) {
			SkillData d = p.get(s);
			check(helper, d.level == 0 && d.xp >= 0 && d.masteryRanks == 0, "%s went below level 0: level %s, %s XP", s.id, d.level, d.xp);
		}
		check(helper, lost.size() == Skill.values().length, "the loss list does not hold every skill");
		helper.succeed();
	}

	@GameTest
	public void aDeathRightAtTheStartOfALevel(GameTestHelper helper) {
		Config.reset();
		PlayerProgress p = at(Skill.MINING, 40, 0, Skill.MELEE, 20, 0);
		List<DeathData.Loss> lost = DeathRules.apply(p, DeathRules.base(1), false);
		check(helper, p.get(Skill.MINING).level == 40 && near(p.get(Skill.MINING).xp, 0), "a death at the start of a level took the level below");
		check(helper, !loss(lost, Skill.MINING).any() && !loss(lost, Skill.MELEE).any(), "a death with nothing in the level counted as a loss");
		// At pressure 2 the highest loses exactly one level, and lands at the start of the level below
		DeathRules.apply(p, DeathRules.base(2), true);
		check(helper, p.get(Skill.MINING).level == 39 && near(p.get(Skill.MINING).xp, 0), "layer 3 left level %s with %s XP, expected 39 with 0",
				p.get(Skill.MINING).level, p.get(Skill.MINING).xp);
		check(helper, p.get(Skill.MELEE).level == 20, "layer 3 took a level from a skill that is not the highest");
		helper.succeed();
	}

	// --- Layer 3 ---------------------------------------------------------------------------------

	@GameTest
	public void layerThreeKeepsThePointInTheLevelBelow(GameTestHelper helper) {
		Config.reset();
		PlayerProgress p = at(Skill.MINING, 40, 0);
		double need40 = Progression.xpToNext(p, Skill.MINING);
		p.get(Skill.MINING).xp = need40 * 0.6;
		// A share of 0.5 takes half of the level's XP (to 30%), then the whole level, at 30% of level 39
		DeathRules.apply(p, 0.5, true);
		double need39 = Progression.xpToNext(p, Skill.MINING);
		check(helper, p.get(Skill.MINING).level == 39, "layer 3 did not take one level");
		check(helper, Math.abs(p.get(Skill.MINING).xp / need39 - 0.3) < 1e-6, "level 39 holds %s of its XP, expected 30%%", p.get(Skill.MINING).xp / need39);
		helper.succeed();
	}

	@GameTest
	public void pressureTwoWithMasteryTakesARankFirst(GameTestHelper helper) {
		Config.reset();
		PlayerProgress p = new PlayerProgress();
		Progression.setLevel(p, Skill.MINING, Config.MAX_LEVEL);
		p.get(Skill.MINING).masteryRanks = 2;
		p.get(Skill.MINING).masteryXp = 300;
		Progression.setLevel(p, Skill.MELEE, 50);
		p.get(Skill.MELEE).xp = 100;
		List<DeathData.Loss> lost = DeathRules.apply(p, DeathRules.base(2), true);
		SkillData m = p.get(Skill.MINING);
		check(helper, m.level == Config.MAX_LEVEL && m.masteryRanks == 1 && near(m.masteryXp, 0),
				"the Mastery skill is at level %s, %s ranks, %s Mastery XP: expected the progress and one rank gone", m.level, m.masteryRanks, m.masteryXp);
		DeathData.Loss ml = loss(lost, Skill.MINING);
		check(helper, ml.mastery && near(ml.xp, 300) && ml.ranksBefore == 2 && ml.ranksAfter == 1 && ml.levelAfter == Config.MAX_LEVEL,
				"the loss was not recorded as 300 Mastery XP and one rank");
		DeathRules.apply(p, DeathRules.base(2), true);
		check(helper, m.level == Config.MAX_LEVEL && m.masteryRanks == 0, "the second rank did not go next");
		// No ranks left: now a whole level goes
		DeathRules.apply(p, DeathRules.base(2), true);
		check(helper, m.level == Config.MAX_LEVEL - 1 && m.masteryRanks == 0 && near(m.masteryXp, 0), "with no ranks left the level did not go: level %s", m.level);
		check(helper, p.get(Skill.MELEE).level == 50, "layer 3 hit a skill that is not the highest");
		helper.succeed();
	}

	@GameTest
	public void aSpecializationSleepsBelowItsLevelAndWakesWithoutARespec(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = SpecTests.specialist(helper, "Sleeper", Specialization.WANDERER, 75);
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		SkillData d = progress.get(Skill.EXPLORATION);
		d.specs.add(Specialization.SURVIVALIST.id);
		check(helper, Specs.has(p, Specialization.WANDERER) && Specs.has(p, Specialization.SURVIVALIST), "both specializations should work at 75");
		DeathRules.apply(progress, DeathRules.base(2), true);
		check(helper, d.level == 74, "Exploration is at %s, expected 74", d.level);
		check(helper, Specs.has(p, Specialization.WANDERER) && !Specs.has(p, Specialization.SURVIVALIST), "below 75 the second specialization did not fall dormant");
		Progression.setLevel(progress, Skill.EXPLORATION, 24);
		check(helper, !Specs.has(p, Specialization.WANDERER), "below 25 the first specialization did not fall dormant");
		check(helper, d.specs.equals(List.of(Specialization.WANDERER.id, Specialization.SURVIVALIST.id)), "the choice was not kept: %s", d.specs);
		Progression.add(progress, Skill.EXPLORATION, Progression.xpToNext(progress, Skill.EXPLORATION) + 1);
		check(helper, d.level == 25 && Specs.has(p, Specialization.WANDERER), "winning level 25 back did not wake the first specialization");
		Progression.setLevel(progress, Skill.EXPLORATION, 75);
		check(helper, Specs.has(p, Specialization.SURVIVALIST), "winning level 75 back did not wake the second specialization");
		helper.succeed();
	}

	@GameTest
	public void pointsGoTheLastBoughtFirstAndComeBackInOrder(GameTestHelper helper) {
		Config.reset();
		PlayerProgress p = new PlayerProgress();
		Progression.setLevel(p, Skill.MELEE, Config.FIRST_SPECIALIZATION_LEVEL + 6);
		SkillData d = p.get(Skill.MELEE);
		d.specs.add(Specialization.BLADE.id);
		String p1 = TreeRules.key(Specialization.BLADE, NodeSlot.P1), p2 = TreeRules.key(Specialization.BLADE, NodeSlot.P2);
		// 6 points: P1, P1, P2 (2 each), in that order
		d.bought.addAll(List.of(p1, p1, p2));
		DeathRules.apply(p, DeathRules.base(2), true);
		check(helper, d.bought.equals(List.of(p1, p1)) && d.suspended.equals(List.of(p2)), "one level less: bought %s, set aside %s", d.bought, d.suspended);
		DeathRules.apply(p, DeathRules.base(2), true);
		DeathRules.apply(p, DeathRules.base(2), true);
		check(helper, d.level == Config.FIRST_SPECIALIZATION_LEVEL + 3, "three deaths at pressure 2 should take three levels, at %s", d.level);
		check(helper, d.bought.equals(List.of(p1)) && d.suspended.equals(List.of(p2, p1)), "three levels less: bought %s, set aside %s", d.bought, d.suspended);
		Progression.setLevel(p, Skill.MELEE, Config.FIRST_SPECIALIZATION_LEVEL + 5);
		check(helper, d.bought.equals(List.of(p1, p1)) && d.suspended.equals(List.of(p2)), "the last point set aside did not come back first: %s, %s", d.bought, d.suspended);
		Progression.setLevel(p, Skill.MELEE, Config.FIRST_SPECIALIZATION_LEVEL + 6);
		check(helper, d.bought.equals(List.of(p1, p1, p2)) && d.suspended.isEmpty(), "the points did not all come back: %s, %s", d.bought, d.suspended);
		helper.succeed();
	}

	// --- Dimensions ------------------------------------------------------------------------------

	@GameTest
	public void dimensionsMultiplyAndTheSeamNeedsExtremeDimensions(GameTestHelper helper) {
		Config.reset();
		check(helper, near(DeathRules.multiplier("minecraft:overworld", true), 1), "the Overworld is not ×1");
		check(helper, near(DeathRules.multiplier("minecraft:the_nether", false), 1.5), "the Nether is not ×1.5");
		check(helper, near(DeathRules.multiplier("minecraft:the_end", false), 2), "the End is not ×2");
		check(helper, near(DeathRules.multiplier("extremedimensions:the_seam", true), 2.5), "the Seam with Extreme Dimensions is not ×2.5");
		check(helper, near(DeathRules.multiplier("extremedimensions:the_seam", false), 1), "the Seam without Extreme Dimensions is not ×1");
		check(helper, near(DeathRules.multiplier("somemod:elsewhere", true), 1), "another mod's dimension is not ×1");
		ServerPlayer p = TestPlayers.survivor(helper, "Here");
		boolean ed = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("extremedimensions");
		check(helper, near(DeathService.multiplier(p), DeathRules.multiplier(p.level().dimension().identifier().toString(), ed)), "the player's factor is not their dimension's");
		// The Nether at pressure 0: 75% of the highest skill's step
		PlayerProgress q = at(Skill.MINING, 60, 1000);
		DeathRules.apply(q, DeathRules.base(0) * DeathRules.multiplier("minecraft:the_nether", false), false);
		check(helper, near(q.get(Skill.MINING).xp, 250), "the Nether at pressure 0 left %s of 1,000, expected 250", q.get(Skill.MINING).xp);
		helper.succeed();
	}

	// --- On the server ---------------------------------------------------------------------------

	@GameTest
	public void aDeathRaisesThePressureRecordsItAndSavesAtOnce(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Doomed");
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		Progression.setLevel(progress, Skill.MINING, 40);
		progress.get(Skill.MINING).xp = 800;
		Progression.setLevel(progress, Skill.MELEE, 20);
		progress.get(Skill.MELEE).xp = 800;
		// What "At Stake" shows is exactly what the death takes
		Path copyFile;
		try {
			copyFile = Files.createTempDirectory("extremeprogression-stake").resolve("copy.json");
			PlayerStore.write(copyFile, progress);
		} catch (Exception e) {
			throw helper.assertionException("could not copy the progression: %s", e);
		}
		PlayerProgress copy = PlayerStore.read(copyFile);
		List<DeathData.Loss> stake = DeathService.atStake(copy, progress.death.pressure, DeathService.multiplier(p));

		DeathData.Record r = DeathService.died(p, p.damageSources().generic());
		check(helper, r != null, "the death did not count");
		check(helper, progress.death.deaths == 1 && progress.death.pressure == 1, "deaths %s and pressure %s, expected 1 and 1", progress.death.deaths, progress.death.pressure);
		check(helper, near(progress.get(Skill.MINING).xp, 400) && near(progress.get(Skill.MELEE).xp, 600), "the losses were %s and %s",
				800 - progress.get(Skill.MINING).xp, 800 - progress.get(Skill.MELEE).xp);
		for (Skill s : Skill.values()) {
			check(helper, near(loss(stake, s).xp, loss(r.losses, s).xp), "At Stake showed %s for %s, the death took %s", loss(stake, s).xp, s.id, loss(r.losses, s).xp);
		}
		check(helper, progress.death.history.size() == 1 && progress.death.history.get(0) == r && r.pressure == 0, "the death was not recorded at pressure 0");
		check(helper, DeathService.fatigued(p), "death fatigue did not start");
		// The file holds the loss already: a server that stops now keeps it, and applies nothing twice
		PlayerProgress onDisk = PlayerStore.read(PlayerStore.fileOf(p.getUUID()));
		check(helper, near(onDisk.get(Skill.MINING).xp, 400) && onDisk.death.deaths == 1 && onDisk.death.pressure == 1 && onDisk.death.history.size() == 1,
				"the file after the death holds %s XP, %s deaths, pressure %s", onDisk.get(Skill.MINING).xp, onDisk.death.deaths, onDisk.death.pressure);
		check(helper, onDisk.death.fatigueUntil == progress.death.fatigueUntil, "the death fatigue's end was not saved");
		helper.succeed();
	}

	@GameTest
	public void twoDeathsInOneTickCountOnce(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Twice");
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		Progression.setLevel(progress, Skill.MINING, 40);
		progress.get(Skill.MINING).xp = 800;
		p.kill(helper.getLevel());
		check(helper, progress.death.deaths == 1, "a real death counted %s times", progress.death.deaths);
		check(helper, DeathService.died(p, p.damageSources().generic()) == null, "a second death in the same tick counted");
		check(helper, progress.death.deaths == 1 && progress.death.pressure == 1 && near(progress.get(Skill.MINING).xp, 400),
				"two deaths in one tick: %s deaths, pressure %s, %s XP left", progress.death.deaths, progress.death.pressure, progress.get(Skill.MINING).xp);
		helper.succeed();
	}

	@GameTest
	public void creativeAndSpectatorDeathsCostNothing(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Builder");
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		Progression.setLevel(progress, Skill.MINING, 40);
		progress.get(Skill.MINING).xp = 800;
		p.setGameMode(GameType.CREATIVE);
		check(helper, DeathService.died(p, p.damageSources().generic()) == null, "a death in Creative counted");
		p.setGameMode(GameType.SPECTATOR);
		check(helper, DeathService.died(p, p.damageSources().generic()) == null, "a death in Spectator counted");
		check(helper, progress.death.deaths == 0 && near(progress.get(Skill.MINING).xp, 800), "a Creative or Spectator death cost something");
		helper.succeed();
	}

	@GameTest
	public void pressureTwoTakesALevelFromTheHighest(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Again");
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		Progression.setLevel(progress, Skill.MINING, 40);
		progress.get(Skill.MINING).xp = 800;
		progress.death.pressure = 2;
		DeathData.Record r = DeathService.died(p, p.damageSources().generic());
		check(helper, r != null && r.pressure == 2, "the death was not recorded at pressure 2");
		check(helper, progress.get(Skill.MINING).level == 39 && near(progress.get(Skill.MINING).xp, 0), "Mining is at level %s with %s XP, expected 39 with 0",
				progress.get(Skill.MINING).level, progress.get(Skill.MINING).xp);
		check(helper, progress.death.pressure == 3, "the pressure did not rise to 3");
		DeathData.Loss l = loss(r.losses, Skill.MINING);
		check(helper, l.levelBefore == 40 && l.levelAfter == 39 && near(l.xp, 800), "the record does not show 800 XP and level 40 → 39");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void anHourOfPlayLowersThePressureButNotWhileAfk(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Patient");
		ServerPlayer idle = TestPlayers.survivor(helper, "Idle");
		long hour = Math.round(Config.DEATH_PRESSURE_DECAY_MINUTES * 60 * 20);
		DeathData death = PlayerStore.get(p.getUUID()).death;
		death.pressure = 2;
		death.activeTicks = hour - 20;
		DeathData away = PlayerStore.get(idle.getUUID()).death;
		away.pressure = 2;
		away.activeTicks = hour - 20;
		long now = helper.getLevel().getServer().getTickCount();
		com.extremeprogression.antiexploit.AfkTracker.idleFor(idle, now, Math.round(Config.AFK_MINUTES * 60 * 20) + 100);
		helper.succeedWhen(() -> {
			check(helper, death.pressure == 1 && death.activeTicks < 40, "the pressure is %s after an hour of play, expected 1", death.pressure);
			check(helper, away.pressure == 2 && away.activeTicks == hour - 20, "an AFK player's pressure dropped or their play time grew");
		});
	}

	// --- Death fatigue ---------------------------------------------------------------------------

	@GameTest
	public void deathFatigueHalvesPassiveBonusesForTenMinutes(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = SpecTests.specialist(helper, "Tired", Specialization.WANDERER, 40, NodeSlot.P1, 5);
		PlayerStore.get(p.getUUID()).get(Skill.MELEE).level = 20;
		check(helper, near(Specs.rank(p, Specialization.WANDERER, NodeSlot.P1), 5) && near(Trunks.power(p, Skill.MELEE), 1), "a rested player is not at full power");
		DeathService.died(p, p.damageSources().generic());
		check(helper, DeathService.fatigued(p), "death fatigue did not start");
		check(helper, near(Specs.rank(p, Specialization.WANDERER, NodeSlot.P1), 5 * Config.DEATH_FATIGUE_POWER), "a rank V passive is %s while fatigued, expected 2.5",
				Specs.rank(p, Specialization.WANDERER, NodeSlot.P1));
		check(helper, near(Trunks.power(p, Skill.MELEE), Config.DEATH_FATIGUE_POWER), "the trunk bonuses are not at half power");
		check(helper, Specs.has(p, Specialization.WANDERER), "death fatigue took the specialization itself away (its price included)");
		long left = DeathService.fatigueLeft(PlayerStore.get(p.getUUID()), helper.getLevel().getServer().overworld().getGameTime());
		check(helper, left == Math.round(Config.DEATH_FATIGUE_MINUTES * 60 * 20), "death fatigue lasts %s ticks, expected ten minutes", left);
		PlayerStore.get(p.getUUID()).death.fatigueUntil = 0;
		check(helper, !DeathService.fatigued(p) && near(Specs.rank(p, Specialization.WANDERER, NodeSlot.P1), 5), "after the fatigue the bonuses did not come back");
		helper.succeed();
	}
}
