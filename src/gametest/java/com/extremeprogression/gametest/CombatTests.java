package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.GatheringTests.earned;
import static com.extremeprogression.gametest.GatheringTests.near;
import static com.extremeprogression.gametest.SkeletonTests.check;

import java.lang.reflect.Field;

import com.extremeprogression.antiexploit.AfkTracker;
import com.extremeprogression.antiexploit.MobOrigins;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.skill.Skill;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Milestone 4: Melee, Ranged, Defense and the Farming XP of tamed animals, with their anti-farming rules. */
public class CombatTests {
	/** A full attack bar (as after waiting), then the attack the way the server announces it. */
	static void swing(ServerPlayer p, Entity target, boolean full) {
		try {
			Field f = LivingEntity.class.getDeclaredField("attackStrengthTicker");
			f.setAccessible(true);
			f.setInt(p, full ? 1000 : 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		AttackEntityCallback.EVENT.invoker().interact(p, p.level(), InteractionHand.MAIN_HAND, target, null);
	}

	/** Hits on the same target a second apart (past its invulnerability after a hit), then the check. */
	static void spaced(GameTestHelper helper, int hits, java.util.function.IntConsumer hit, Runnable then) {
		for (int i = 0; i < hits; i++) {
			int n = i;
			helper.runAfterDelay(1 + 20L * i, () -> hit.accept(n));
		}
		helper.runAfterDelay(1 + 20L * hits, then);
	}

	static LivingEntity mob(GameTestHelper helper, net.minecraft.world.entity.EntityType<? extends LivingEntity> type, BlockPos pos,
			EntitySpawnReason reason) {
		return helper.spawn(type, pos, reason);
	}

	@GameTest
	public void fullHitsGiveMeleeXpUpToTheRemainingHealth(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "fighter");
		LivingEntity creeper = mob(helper, EntityTypes.CREEPER, new BlockPos(1, 1, 1), EntitySpawnReason.NATURAL);
		swing(p, creeper, true);
		creeper.hurtServer(level, level.damageSources().playerAttack(p), 6);
		near(helper, earned(p, Skill.MELEE), 6 * 1.5 * 4, "a full hit of 6 on a creeper");
		// (still invulnerable from the first hit, so vanilla deals 100 − 6; either way more than its 14 health left)
		swing(p, creeper, true);
		creeper.hurtServer(level, level.damageSources().playerAttack(p), 100);
		check(helper, !creeper.isAlive(), "the creeper survived 100 damage");
		near(helper, earned(p, Skill.MELEE), 20 * 1.5 * 4, "a killing blow (counted up to the remaining health)");

		LivingEntity other = mob(helper, EntityTypes.CREEPER, new BlockPos(3, 1, 3), EntitySpawnReason.NATURAL);
		swing(p, other, false);
		other.hurtServer(level, level.damageSources().playerAttack(p), 6);
		near(helper, earned(p, Skill.MELEE), 120, "after a hit with an empty attack bar");
		helper.succeed();
	}

	@GameTest
	public void spawnerMobsAndAnimalsGiveNothing(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "farmer");
		LivingEntity spawned = mob(helper, EntityTypes.CREEPER, new BlockPos(1, 1, 1), EntitySpawnReason.SPAWNER);
		check(helper, MobOrigins.flagged(spawned), "a mob from a spawner was not flagged");
		swing(p, spawned, true);
		spawned.hurtServer(level, level.damageSources().playerAttack(p), 6);
		LivingEntity egg = mob(helper, EntityTypes.ZOMBIE, new BlockPos(3, 1, 3), EntitySpawnReason.SPAWN_ITEM_USE);
		swing(p, egg, true);
		egg.hurtServer(level, level.damageSources().playerAttack(p), 6);
		LivingEntity cow = mob(helper, EntityTypes.COW, new BlockPos(5, 1, 5), EntitySpawnReason.NATURAL);
		swing(p, cow, true);
		cow.hurtServer(level, level.damageSources().playerAttack(p), 6);
		LivingEntity trial = mob(helper, EntityTypes.ZOMBIE, new BlockPos(6, 1, 1), EntitySpawnReason.TRIAL_SPAWNER);
		check(helper, !MobOrigins.flagged(trial), "a mob from a trial spawner was flagged");
		near(helper, earned(p, Skill.MELEE), 0, "a spawner creeper, a spawn-egg zombie and a cow");
		helper.succeed();
	}

	@GameTest
	public void fullyDrawnArrowsGiveRangedXpWithADistanceBonus(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "archer");
		LivingEntity creeper = mob(helper, EntityTypes.CREEPER, new BlockPos(1, 1, 1), EntitySpawnReason.NATURAL);
		p.setPos(creeper.getX() + 30, creeper.getY(), creeper.getZ());
		AbstractArrow arrow = EntityTypes.ARROW.create(level, EntitySpawnReason.TRIGGERED);
		arrow.setOwner(p);
		arrow.setCritArrow(false);
		creeper.hurtServer(level, level.damageSources().arrow(arrow, p), 5);
		near(helper, earned(p, Skill.RANGED), 0, "an arrow that was not fully drawn");
		LivingEntity second = mob(helper, EntityTypes.CREEPER, new BlockPos(1, 1, 3), EntitySpawnReason.NATURAL);
		AbstractArrow full = EntityTypes.ARROW.create(level, EntitySpawnReason.TRIGGERED);
		full.setOwner(p);
		full.setCritArrow(true);
		p.setPos(second.getX() + 30, second.getY(), second.getZ());
		second.hurtServer(level, level.damageSources().arrow(full, p), 11);
		near(helper, earned(p, Skill.RANGED), 11 * 1.5 * 4 * 1.4, "a full arrow from 30 blocks (+40%)");
		helper.succeed();
	}

	@GameTest(maxTicks = 300)
	public void defenseCountsWhatArmorAbsorbsAtMostSixTimesPerAttacker(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "tank");
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
		p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
		LivingEntity zombie = mob(helper, EntityTypes.ZOMBIE, new BlockPos(1, 1, 1), EntitySpawnReason.NATURAL);
		double[] after = new double[8];
		boolean[] landed = new boolean[8];
		spaced(helper, 8, i -> {
			p.setHealth(20);
			landed[i] = p.hurtServer(level, level.damageSources().mobAttack(zombie), 8);
			after[i] = earned(p, Skill.DEFENSE);
		}, () -> {
			check(helper, landed[0], "a zombie's hit did not land on the test player (%s)", TestPlayers.LOADED);
			check(helper, after[0] > 0, "armor absorbing a zombie's hit gave no Defense XP");
			check(helper, after[5] > after[4] && after[6] == after[5] && after[7] == after[5], "the cap of 6 hits per attacker did not hold: %s",
					java.util.Arrays.toString(after));
			// Damage from the environment gives nothing; neither does a hit while idle
			p.setHealth(20);
			p.hurtServer(level, level.damageSources().generic(), 8);
			LivingEntity other = mob(helper, EntityTypes.ZOMBIE, new BlockPos(3, 1, 3), EntitySpawnReason.NATURAL);
			AfkTracker.idleFor(p, level.getServer().getTickCount(), 20 * 20);
			p.hurtServer(level, level.damageSources().mobAttack(other), 8);
			near(helper, earned(p, Skill.DEFENSE), after[5], "environmental damage and a hit while idle");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 300)
	public void pvpCountsThreeLivesPerHourAndNotFromMuchWeakerPlayers(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer a = TestPlayers.survivor(helper, "duelA");
		ServerPlayer b = TestPlayers.survivor(helper, "duelB");
		boolean[] landed = new boolean[8];
		spaced(helper, 8, i -> {
			b.setHealth(20);
			swing(a, b, true);
			landed[i] = b.hurtServer(level, level.damageSources().playerAttack(a), 10);
		}, () -> {
			check(helper, landed[0], "a player's hit did not land on another player (%s)", TestPlayers.LOADED);
			near(helper, earned(a, Skill.MELEE), 60 * 1.5 * 4, "80 damage to one player within the hour");
			ServerPlayer c = TestPlayers.survivor(helper, "duelC");
			Progression.setLevel(PlayerStore.get(a.getUUID()), Skill.MINING, 100);
			double before = earned(a, Skill.MELEE);
			swing(a, c, true);
			c.hurtServer(level, level.damageSources().playerAttack(a), 10);
			near(helper, earned(a, Skill.MELEE), before, "a hit on a player with less than half of your total level");
			helper.succeed();
		});
	}

	@GameTest
	public void tamedWolvesEarnTheirOwnerFarmingXp(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "owner");
		TamableAnimal wolf = (TamableAnimal) mob(helper, EntityTypes.WOLF, new BlockPos(1, 1, 1), EntitySpawnReason.NATURAL);
		wolf.tame(p);
		double tamed = earned(p, Skill.FARMING);
		LivingEntity creeper = mob(helper, EntityTypes.CREEPER, new BlockPos(3, 1, 3), EntitySpawnReason.NATURAL);
		creeper.hurtServer(level, level.damageSources().mobAttack(wolf), 4);
		near(helper, earned(p, Skill.FARMING) - tamed, 4 * 1.5 * 3, "a wolf biting a creeper for 4");
		near(helper, earned(p, Skill.MELEE), 0, "Melee XP from a wolf's bite");
		helper.succeed();
	}
}
