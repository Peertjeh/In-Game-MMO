package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.GatheringTests.earned;
import static com.extremeprogression.gametest.GatheringTests.near;
import static com.extremeprogression.gametest.SkeletonTests.check;

import com.extremeprogression.config.Config;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.xp.CraftingXp;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/** Milestone 4: Smithing and Arcana XP, through the game's own furnace slot, anvil, table and brewing stand. */
public class CraftingTests {
	@GameTest
	public void smeltedMetalTakenByHandGivesSmithingXp(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "smelter");
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.FURNACE);
		AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)));
		Slot output = new FurnaceResultSlot(p, furnace, 2, 0, 0);
		furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 4));
		output.onTake(p, output.remove(4));
		near(helper, earned(p, Skill.SMITHING), 4 * 25, "four iron ingots");
		furnace.setItem(2, new ItemStack(Items.STONE, 8));
		output.onTake(p, output.remove(8));
		near(helper, earned(p, Skill.SMITHING), 100, "after eight smelted stone");
		helper.succeed();
	}

	@GameTest
	public void anvilRepairsGiveSmithingXpByMaterial(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "smith");
		p.giveExperienceLevels(30);
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.ANVIL);
		AnvilMenu anvil = new AnvilMenu(0, p.getInventory(), ContainerLevelAccess.create(level, helper.absolutePos(new BlockPos(1, 1, 1))));
		ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
		pick.setDamageValue(pick.getMaxDamage() - 1);
		anvil.getSlot(0).set(pick);
		anvil.getSlot(1).set(new ItemStack(Items.DIAMOND, 4));
		ItemStack result = anvil.getSlot(2).getItem();
		check(helper, !result.isEmpty() && result.getDamageValue() == 0, "the anvil offered no full repair: %s", result);
		anvil.getSlot(2).onTake(p, result);
		near(helper, earned(p, Skill.SMITHING), 200 * 3.0 * (pick.getMaxDamage() - 1) / pick.getMaxDamage(), "a broken diamond pickaxe repaired");
		near(helper, earned(p, Skill.ARCANA), 0, "Arcana XP from a plain repair");

		CraftingXp.smithed(p, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
		CraftingXp.smithed(p, new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE));
		near(helper, earned(p, Skill.SMITHING) - 200 * 3.0 * (pick.getMaxDamage() - 1) / pick.getMaxDamage(), 1500 + 300,
				"a netherite upgrade and an armor trim");
		helper.succeed();
	}

	@GameTest
	public void enchantingGivesArcanaXpForTheRequiredLevel(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "enchanter");
		p.giveExperienceLevels(30);
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.ENCHANTING_TABLE);
		EnchantmentMenu table = new EnchantmentMenu(0, p.getInventory(), ContainerLevelAccess.create(level, helper.absolutePos(new BlockPos(1, 1, 1))));
		table.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
		table.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
		int cost = table.costs[0];
		check(helper, cost > 0, "the table made no offer");
		check(helper, table.clickMenuButton(p, 0), "the enchantment was refused");
		check(helper, table.getSlot(0).getItem().isEnchanted(), "the sword was not enchanted");
		near(helper, earned(p, Skill.ARCANA), 15.0 * cost, "an offer of level " + cost);
		helper.succeed();
	}

	@GameTest(maxTicks = 600)
	public void onlyPotionsTheStandBrewedCount(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "brewer");
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.BREWING_STAND);
		BrewingStandBlockEntity stand = (BrewingStandBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)));
		BrewingStandMenu menu = new BrewingStandMenu(0, p.getInventory(), stand, new SimpleContainerData(4));
		// A potion put in by hand and taken out again was not brewed
		menu.getSlot(0).set(PotionContents.createItemStack(Items.POTION, Potions.AWKWARD));
		menu.getSlot(0).onTake(p, menu.getSlot(0).remove(1));
		near(helper, earned(p, Skill.ARCANA), 0, "a potion that was only put in");

		for (int slot = 0; slot < 3; slot++) {
			stand.setItem(slot, PotionContents.createItemStack(Items.POTION, Potions.WATER));
		}
		stand.setItem(3, new ItemStack(Items.NETHER_WART));
		stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
		helper.runAfterDelay(450, () -> {
			check(helper, CraftingXp.potionXp(stand.getItem(0)) == 5, "the stand did not brew awkward potions: %s", stand.getItem(0));
			menu.getSlot(0).onTake(p, menu.getSlot(0).remove(1));
			menu.getSlot(1).onTake(p, menu.getSlot(1).remove(1));
			near(helper, earned(p, Skill.ARCANA), 10, "two brewed awkward potions");
			// A bottle put back in place of a brewed one is no longer the stand's work
			menu.getSlot(2).set(PotionContents.createItemStack(Items.POTION, Potions.AWKWARD));
			menu.getSlot(2).onTake(p, menu.getSlot(2).remove(1));
			near(helper, earned(p, Skill.ARCANA), 10, "after a swapped-in potion");
			helper.succeed();
		});
	}
}
