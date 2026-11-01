package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.GatheringTests.near;
import static com.extremeprogression.gametest.SkeletonTests.check;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.death.DeathRules;
import com.extremeprogression.death.DeathService;
import com.extremeprogression.mastery.Mastery;
import com.extremeprogression.mastery.MasteryEffects;
import com.extremeprogression.mastery.MasteryPerk;
import com.extremeprogression.network.TreePayload;
import com.extremeprogression.progression.Opposing;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.skill.Specialization;
import com.extremeprogression.spec.Specs;
import com.extremeprogression.tree.NodeSlot;
import com.extremeprogression.tree.TreeRules;
import com.extremeprogression.tree.TreeService;
import com.extremeprogression.trunk.Trunks;
import com.extremeprogression.xp.XpService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The connections between skills (milestone 8): opposing skills and Mastery perks. The synergies have tests of their
 * own.
 */
public class ConnectionTests {
	private static boolean close(double a, double b) {
		return Math.abs(a - b) < 1e-6;
	}

	// --- Opposing skills -------------------------------------------------------------------------

	@GameTest
	public void theOpposingPenaltyGrowsWithTheGap(GameTestHelper helper) {
		Config.reset();
		check(helper, close(Opposing.penalty(10, 25), 0), "an opposing skill at 25 should not press yet");
		check(helper, close(Opposing.penalty(10, 26), 0.6 * 16 / 50), "a gap of 16 above 25 gives %s, expected 19.2%%", Opposing.penalty(10, 26));
		check(helper, close(Opposing.penalty(0, 50), 0.6), "a gap of 50 should cost the full 60%%");
		check(helper, close(Opposing.penalty(0, 100), 0.6), "the penalty went past 60%%");
		check(helper, close(Opposing.penalty(30, 30), 0) && close(Opposing.penalty(40, 30), 0), "an equal or higher skill should have no penalty");
		check(helper, close(Opposing.passivePower(10, 30), 0.75) && close(Opposing.passivePower(30, 10), 1), "passives are not at 75%% for the weaker skill only");
		PlayerProgress p = new PlayerProgress();
		Progression.setLevel(p, Skill.SMITHING, 60);
		Progression.setLevel(p, Skill.MINING, 90);
		check(helper, close(Opposing.penalty(p, Skill.ARCANA), 0.6) && close(Opposing.penalty(p, Skill.SMITHING), 0), "Smithing does not press on Arcana");
		check(helper, close(Opposing.penalty(p, Skill.FORAGING), 0), "a skill without an opposing skill has a penalty");
		helper.succeed();
	}

	@GameTest
	public void anOpposingSkillCostsXp(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Archer");
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		Progression.setLevel(progress, Skill.MELEE, 60);
		Progression.setLevel(progress, Skill.RANGED, 10);
		near(helper, XpService.award(p, Skill.RANGED, 100), 40, "Ranged XP with Melee 50 levels higher");
		near(helper, XpService.award(p, Skill.MELEE, 100), 100, "Melee XP (the stronger skill)");
		Progression.setLevel(progress, Skill.RANGED, 45);
		near(helper, XpService.award(p, Skill.RANGED, 100), 100 * (1 - 0.6 * 15 / 50), "Ranged XP at a gap of 15");
		helper.succeed();
	}

	@GameTest
	public void theWeakerSkillsPassivesWorkAt75Percent(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = SpecTests.specialist(helper, "Pressed", Specialization.SNIPER, 40, NodeSlot.P1, 4);
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		check(helper, close(Specs.rank(p, Specialization.SNIPER, NodeSlot.P1), 4), "a free Ranged has rank %s, not 4", Specs.rank(p, Specialization.SNIPER, NodeSlot.P1));
		Progression.setLevel(progress, Skill.MELEE, 70);
		check(helper, close(Specs.rank(p, Specialization.SNIPER, NodeSlot.P1), 3), "with Melee above, rank 4 works as %s, expected 3",
				Specs.rank(p, Specialization.SNIPER, NodeSlot.P1));
		check(helper, close(Trunks.power(p, Skill.RANGED), 0.75) && close(Trunks.power(p, Skill.MELEE), 1), "the trunk powers are not 75%% and 100%%");
		check(helper, Specs.has(p, Specialization.SNIPER), "the opposing skill took the specialization away");
		// Death fatigue on top: both together
		DeathService.died(p, p.damageSources().generic());
		check(helper, close(Specs.power(p, Skill.RANGED), 0.75 * Config.DEATH_FATIGUE_POWER), "fatigue and the opposing skill give %s, expected 37.5%%",
				Specs.power(p, Skill.RANGED));
		helper.succeed();
	}

	// --- Mastery perks ---------------------------------------------------------------------------

	@GameTest
	public void everySkillHasTwoPerksWithTexts(GameTestHelper helper) {
		for (Skill s : Skill.values()) {
			MasteryPerk a = MasteryPerk.of(s, "a"), b = MasteryPerk.of(s, "b");
			check(helper, a != null && b != null && a != b, "%s does not have two perks", s.id);
		}
		check(helper, MasteryPerk.values().length == 2 * com.extremeprogression.skill.Skill.values().length, "there are %s perks, not two a skill", MasteryPerk.values().length);
		helper.succeed();
	}

	@GameTest
	public void perksAreChosenInOrderAndGoWithTheirRank(GameTestHelper helper) {
		Config.reset();
		PlayerProgress p = new PlayerProgress();
		Progression.setLevel(p, Skill.MINING, Config.MAX_LEVEL);
		SkillData d = p.get(Skill.MINING);
		check(helper, Mastery.waiting(d) == 0 && Mastery.choose(p, Skill.MINING, "a") == TreeRules.Result.NO_PERK_WAITING, "a perk was chosen without a rank");
		d.masteryRanks = 3;
		check(helper, Mastery.waiting(d) == 1, "rank I is not the first waiting");
		check(helper, Mastery.choose(p, Skill.MINING, "c") == TreeRules.Result.NO_PERK_WAITING, "a perk that does not exist was chosen");
		Mastery.choose(p, Skill.MINING, "a");
		Mastery.choose(p, Skill.MINING, "b");
		Mastery.choose(p, Skill.MINING, "a");
		check(helper, d.perks.equals(List.of("a", "b", "a")), "the perks are %s", d.perks);
		check(helper, Mastery.choose(p, Skill.MINING, "b") == TreeRules.Result.NO_PERK_WAITING, "a fourth perk was chosen with three ranks");
		check(helper, Mastery.ranks(d, MasteryPerk.MOTHER_LODE) == 2 && Mastery.ranks(d, MasteryPerk.SWIFT_PICK) == 1, "the ranks per perk are wrong");
		// A death at pressure 2 takes the last rank and its perk
		DeathRules.apply(p, DeathRules.base(2), true);
		check(helper, d.masteryRanks == 2 && d.perks.equals(List.of("a", "b")), "after losing a rank: %s ranks, perks %s", d.masteryRanks, d.perks);
		// Winning it back asks for the choice again
		Progression.add(p, Skill.MINING, Config.MASTERY_XP_PER_RANK);
		check(helper, d.masteryRanks == 3 && Mastery.waiting(d) == 3, "rank III won back is not waiting for a perk");
		// Losing the level 100 loses every rank and perk
		Progression.setLevel(p, Skill.MINING, 99);
		check(helper, d.perks.isEmpty(), "perks stayed below level 100: %s", d.perks);
		helper.succeed();
	}

	@GameTest
	public void choosingAPerkGoesThroughTheServer(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Master");
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		Progression.setLevel(progress, Skill.ARCANA, Config.MAX_LEVEL);
		progress.get(Skill.ARCANA).masteryRanks = 1;
		check(helper, TreeService.handle(p, new TreePayload(TreePayload.Action.PERK, "arcana", "b")) == TreeRules.Result.OK, "the request was refused");
		check(helper, progress.get(Skill.ARCANA).perks.equals(List.of("b")), "the server holds %s", progress.get(Skill.ARCANA).perks);
		check(helper, TreeService.handle(p, new TreePayload(TreePayload.Action.PERK, "arcana", "a")) == TreeRules.Result.NO_PERK_WAITING,
				"a second perk was taken for one rank");
		helper.succeed();
	}

	@GameTest
	public void perksWorkAndFollowThePower(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Perky");
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		Progression.setLevel(progress, Skill.MINING, Config.MAX_LEVEL);
		progress.get(Skill.MINING).masteryRanks = 5;
		progress.get(Skill.MINING).perks.addAll(List.of("a", "a", "a", "a", "a"));
		check(helper, close(com.extremeprogression.spec.Mining.doubleOreChance(p), 0.05), "five ranks of Mother Lode give %s, expected 5%%",
				com.extremeprogression.spec.Mining.doubleOreChance(p));
		// Duelist: +1% a rank on melee hits against players
		Progression.setLevel(progress, Skill.MELEE, Config.MAX_LEVEL);
		progress.get(Skill.MELEE).masteryRanks = 3;
		progress.get(Skill.MELEE).perks.addAll(List.of("b", "b", "b"));
		ServerPlayer target = TestPlayers.survivor(helper, "Target");
		float hit = MasteryEffects.damage(target, p.damageSources().playerAttack(p), 10);
		check(helper, close(hit, 10.3f), "three ranks of Duelist deal %s, expected 10.3", hit);
		check(helper, close(MasteryEffects.damage((net.minecraft.world.entity.LivingEntity) helper.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE,
				new net.minecraft.core.BlockPos(1, 2, 1), net.minecraft.world.entity.EntitySpawnReason.NATURAL), p.damageSources().playerAttack(p), 10), 10),
				"Duelist counted against a zombie");
		// Swift Feet and Soft Landing are Exploration's: not in heavy armor
		Progression.setLevel(progress, Skill.EXPLORATION, Config.MAX_LEVEL);
		progress.get(Skill.EXPLORATION).masteryRanks = 2;
		progress.get(Skill.EXPLORATION).perks.addAll(List.of("a", "b"));
		check(helper, close(Mastery.bonus(p, MasteryPerk.SWIFT_FEET), 0.01), "Swift Feet is not 1%%");
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		check(helper, close(Mastery.bonus(p, MasteryPerk.SWIFT_FEET), 0), "Swift Feet works in heavy armor");
		// Death fatigue halves the perks too
		DeathService.died(p, p.damageSources().generic());
		check(helper, close(com.extremeprogression.spec.Mining.doubleOreChance(p), 0.025), "a fatigued Mother Lode gives %s, expected 2.5%%",
				com.extremeprogression.spec.Mining.doubleOreChance(p));
		helper.succeed();
	}

	@GameTest
	public void aSchemaFourFileLoadsWithNoPerksOrSynergies(GameTestHelper helper) throws Exception {
		Config.reset();
		Path file = Files.createTempDirectory("extremeprogression-schema4").resolve("player.json");
		Files.writeString(file, "{\"schema\":4,\"skills\":{\"mining\":{\"level\":100,\"masteryRanks\":2}},\"death\":{\"pressure\":1}}");
		PlayerProgress p = PlayerStore.read(file);
		check(helper, p.schema == PlayerProgress.SCHEMA && p.schema == 5, "the file is at schema %s", p.schema);
		check(helper, p.get(Skill.MINING).masteryRanks == 2 && p.get(Skill.MINING).perks.isEmpty() && Mastery.waiting(p.get(Skill.MINING)) == 1,
				"the Mastery ranks did not come back waiting for their perks");
		check(helper, p.synergies.isEmpty() && p.death.pressure == 1, "the synergies or the death pressure did not load");
		helper.succeed();
	}
}
