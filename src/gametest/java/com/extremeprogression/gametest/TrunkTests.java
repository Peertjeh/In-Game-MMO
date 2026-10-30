package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.CombatTests.swing;
import static com.extremeprogression.gametest.SkeletonTests.check;

import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.trunk.PotentPotions;
import com.extremeprogression.trunk.Trunks;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;

/** Milestone 5: the trunk bonuses of levels 5, 10, 15 and 20 (design §2). */
public class TrunkTests {
	static ServerPlayer at(GameTestHelper helper, String name, Skill skill, int level) {
		ServerPlayer p = TestPlayers.survivor(helper, name);
		Progression.setLevel(PlayerStore.get(p.getUUID()), skill, level);
		return p;
	}

	@GameTest
	public void attributeBonusesComeAndGoWithLevelAndTool(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = at(helper, "rhythm", Skill.MINING, 5);
		Progression.setLevel(PlayerStore.get(p.getUUID()), Skill.MELEE, 5);
		Progression.setLevel(PlayerStore.get(p.getUUID()), Skill.DEFENSE, 20);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
		double breakBefore = p.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
		double speedBefore = p.getAttributeValue(Attributes.ATTACK_SPEED);
		Trunks.tick(helper.getLevel().getServer());
		check(helper, Math.abs(p.getAttributeValue(Attributes.BLOCK_BREAK_SPEED) - breakBefore * 1.03) < 1e-6, "no +3% breaking speed with a pickaxe");
		check(helper, p.getAttributeValue(Attributes.ATTACK_SPEED) > speedBefore, "no faster attack bar at Melee 5");
		check(helper, Math.abs(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - 0.05) < 1e-6, "no 5% knockback resistance at Defense 20");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		Trunks.tick(helper.getLevel().getServer());
		check(helper, Math.abs(p.getAttributeValue(Attributes.BLOCK_BREAK_SPEED) - breakBefore) < 1e-6, "the breaking speed bonus stayed with a sword");
		Progression.setLevel(PlayerStore.get(p.getUUID()), Skill.MELEE, 4);
		Trunks.tick(helper.getLevel().getServer());
		check(helper, Math.abs(p.getAttributeValue(Attributes.ATTACK_SPEED) - speedBefore) < 1e-6, "the attack bar bonus stayed below level 5");
		helper.succeed();
	}

	@GameTest
	public void hitsFollowTheTrunksOfAttackerAndTarget(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		// Full Swing (Melee 10): a full hit deals +5%
		ServerPlayer fighter = at(helper, "swing", Skill.MELEE, 10);
		LivingEntity creeper = helper.spawn(EntityTypes.CREEPER, new BlockPos(1, 1, 1), EntitySpawnReason.NATURAL);
		swing(fighter, creeper, true);
		creeper.hurtServer(level, level.damageSources().playerAttack(fighter), 10);
		check(helper, Math.abs(creeper.getHealth() - 9.5f) < 1e-3, "a full hit at Melee 10 left the creeper at %s, not 9.5", creeper.getHealth());

		// Cave Lore (Mining 15): 10% less from a magma block; Light-Footed (Exploration 5): 5% less from a fall
		ServerPlayer miner = at(helper, "cave", Skill.MINING, 15);
		miner.hurtServer(level, level.damageSources().hotFloor(), 4);
		check(helper, Math.abs(miner.getHealth() - 16.4f) < 1e-3, "a magma block at Mining 15 left %s health, not 16.4", miner.getHealth());
		ServerPlayer walker = at(helper, "light", Skill.EXPLORATION, 5);
		walker.hurtServer(level, level.damageSources().fall(), 10);
		check(helper, Math.abs(walker.getHealth() - 10.5f) < 1e-3, "a fall at Exploration 5 left %s health, not 10.5", walker.getHealth());
		// ...but not in heavy armor
		ServerPlayer heavy = at(helper, "heavy", Skill.EXPLORATION, 5);
		heavy.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		heavy.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		heavy.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		heavy.hurtServer(level, level.damageSources().fall(), 10);
		check(helper, Math.abs(heavy.getHealth() - 10f) < 1e-3, "Light-Footed worked in heavy armor: %s health", heavy.getHealth());

		// Forest Foot (Foraging 15): berry bushes no longer prick
		ServerPlayer forager = at(helper, "berry", Skill.FORAGING, 15);
		forager.hurtServer(level, level.damageSources().sweetBerryBush(), 1);
		check(helper, forager.getHealth() == 20f, "a berry bush pricked at Foraging 15");
		helper.succeed();
	}

	@GameTest
	public void farmlandIsNotTrampledAtFarmingFive(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer farmer = at(helper, "step", Skill.FARMING, 5);
		ServerPlayer other = TestPlayers.survivor(helper, "stomp");
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.FARMLAND);
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.FARMLAND);
		BlockPos a = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos b = helper.absolutePos(new BlockPos(3, 1, 3));
		Blocks.FARMLAND.fallOn(level, level.getBlockState(a), a, farmer, 3.0);
		Blocks.FARMLAND.fallOn(level, level.getBlockState(b), b, other, 3.0);
		check(helper, level.getBlockState(a).is(Blocks.FARMLAND), "farmland was trampled at Farming 5");
		check(helper, !level.getBlockState(b).is(Blocks.FARMLAND), "farmland was not trampled by a player without Farming 5");
		helper.succeed();
	}

	@GameTest
	public void foodAndBabiesFollowTheFarmingAndFishingTrunks(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = at(helper, "fare", Skill.FARMING, 20);
		p.getFoodData().setFoodLevel(4);
		new ItemStack(Items.BREAD).finishUsingItem(helper.getLevel(), p);
		check(helper, p.getFoodData().getFoodLevel() == 4 + 5 + 1, "bread at Farming 20 filled to %s, not 10", p.getFoodData().getFoodLevel());
		AgeableMob baby = helper.spawn(EntityTypes.COW, new BlockPos(1, 1, 1), EntitySpawnReason.NATURAL);
		baby.setAge(-24000);
		Progression.setLevel(PlayerStore.get(p.getUUID()), Skill.FARMING, 15);
		Trunks.bred(p, baby);
		check(helper, baby.getAge() == -21600, "a bred baby at Farming 15 must grow up from %s, not -21600", baby.getAge());

		ServerPlayer angler = at(helper, "angler", Skill.FISHING, 20);
		ItemStack rod = new ItemStack(Items.FISHING_ROD);
		check(helper, Math.abs(EnchantmentHelper.getFishingTimeReduction(helper.getLevel(), rod, angler) - 0.875f) < 1e-4, "no Patience at Fishing 5");
		check(helper, EnchantmentHelper.getFishingLuckBonus(helper.getLevel(), rod, angler) == 2, "no Sea Luck at Fishing 20");
		helper.succeed();
	}

	@GameTest
	public void theAnvilAndPotionsFollowTheCraftingTrunks(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.ANVIL);
		int[] cost = new int[2];
		int[] damage = new int[2];
		for (int i = 0; i < 2; i++) {
			ServerPlayer smith = at(helper, "smith" + i, Skill.SMITHING, i == 0 ? 0 : 15);
			AnvilMenu anvil = new AnvilMenu(0, smith.getInventory(), ContainerLevelAccess.create(level, helper.absolutePos(new BlockPos(1, 1, 1))));
			ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
			pick.set(DataComponents.REPAIR_COST, 20);
			pick.setDamageValue(pick.getMaxDamage() - 1);
			anvil.getSlot(0).set(pick);
			anvil.getSlot(1).set(new ItemStack(Items.DIAMOND, 1));
			cost[i] = anvil.getCost();
			damage[i] = anvil.getSlot(2).getItem().getDamageValue();
		}
		check(helper, cost[1] < cost[0], "Thrifty Anvil did not lower the cost: %s and %s", cost[0], cost[1]);
		check(helper, damage[0] - damage[1] == (int) Math.ceil(Items.DIAMOND_PICKAXE.getDefaultInstance().getMaxDamage() * 0.05),
				"Tempered Steel did not restore 5%% more: %s and %s", damage[0], damage[1]);

		// Potent Brews (Arcana 15): a marked potion lasts 5% longer
		ServerPlayer drinker = TestPlayers.survivor(helper, "drinker");
		ItemStack potion = PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS);
		PotentPotions.mark(potion);
		potion.finishUsingItem(level, drinker);
		int longest = drinker.getActiveEffects().stream().mapToInt(MobEffectInstance::getDuration).max().orElse(0);
		check(helper, longest >= 3600 * 1.05 - 2, "a potent Swiftness lasts %s ticks, not 3780", longest);
		helper.succeed();
	}

	@GameTest
	public void bowsAndToolsFollowTheirTrunks(GameTestHelper helper) {
		Config.reset();
		ServerPlayer archer = at(helper, "archer", Skill.RANGED, 5);
		ItemStack bow = new ItemStack(Items.BOW);
		int duration = bow.getUseDuration(archer);
		check(helper, Trunks.bowTimeLeft(bow, archer, duration - 19) == duration - 20, "19 ticks of drawing did not count as 20 at Ranged 5");
		ServerPlayer miner = at(helper, "sturdy", Skill.MINING, 10);
		ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
		int free = 0;
		for (int i = 0; i < 2000; i++) {
			if (Trunks.durability(pick, miner, 1) == 0) {
				free++;
			}
		}
		check(helper, free > 40 && free < 200, "Sturdy Tools spared %s of 2000 uses, not about 100", free);
		check(helper, Trunks.durability(pick, TestPlayers.survivor(helper, "plain"), 1) == 1, "a player without Mining 10 was spared");
		helper.succeed();
	}
}
