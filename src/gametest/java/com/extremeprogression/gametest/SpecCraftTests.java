package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.SkeletonTests.check;
import static com.extremeprogression.gametest.SpecTests.specialist;

import com.extremeprogression.ability.AbilityService;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.skill.Specialization;
import com.extremeprogression.spec.Active;
import com.extremeprogression.spec.Arcana;
import com.extremeprogression.spec.Smithing;
import com.extremeprogression.tree.NodeSlot;
import com.extremeprogression.trunk.PotentPotions;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 6, part 3: the abilities of Smithing and Arcana at their workstations and with thrown potions, their
 * prices, and their passives and choice nodes with a fixed effect. Every player here is at level 30.
 */
public class SpecCraftTests {
	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1e-3;
	}

	/** Puts the player 2.5 blocks south of a workstation, looking straight at it. */
	private static void facing(ServerPlayer p, BlockPos block) {
		p.setPos(block.getX() + 0.5, block.getY() + 0.5 - p.getEyeHeight(), block.getZ() + 2.5);
		p.setYRot(180);
		p.setYHeadRot(180);
		p.setXRot(0);
	}

	/** A workstation at (2, 2, 2), the player looking at it; returns where it stands. */
	private static BlockPos station(GameTestHelper helper, ServerPlayer p, Block block) {
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 2, 2), block);
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		facing(p, at);
		return at;
	}

	/** The player opens (uses) the block, as the game tells the mods: it becomes the block's user. */
	private static void open(ServerPlayer p, BlockPos at) {
		UseBlockCallback.EVENT.invoker().interact(p, p.level(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(at), Direction.SOUTH, at, false));
	}

	// --- Smithing --------------------------------------------------------------------------------

	@GameTest(maxTicks = 200)
	public void flashForgeSmeltsFourTimesAsFast(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "forge", Specialization.FORGEMASTER, 30, NodeSlot.A1, 1, NodeSlot.P2, 5, NodeSlot.C2, 5);
		BlockPos at = station(helper, p, Blocks.FURNACE);
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Flash Forge was not used looking at a furnace");
		open(p, at);
		AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(at);
		furnace.setItem(0, new ItemStack(Items.RAW_IRON, 4));
		furnace.setItem(1, new ItemStack(Items.COAL, 1));
		check(helper, near(Smithing.smeltXp(p), 1.15) && near(Smithing.slowBurn(p), 0.5), "Smelter's Eye or Slow Burn is off");
		// vanilla takes 200 ticks a smelt; at 4× it takes about 50
		helper.runAfterDelay(120, () -> {
			int out = furnace.getItem(2).getCount();
			check(helper, out >= 2, "Flash Forge smelted %s iron in 120 ticks, not at least 2", out);
			helper.succeed();
		});
	}

	@GameTest
	public void masterworkRepairsFully(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "master", Specialization.ARMORER, 30, NodeSlot.A1, 1, NodeSlot.P2, 5, NodeSlot.C1, 5, NodeSlot.P3, 5);
		p.giveExperienceLevels(30);
		BlockPos at = station(helper, p, Blocks.ANVIL);
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Masterwork was not used looking at an anvil");
		AnvilMenu anvil = new AnvilMenu(0, p.getInventory(), ContainerLevelAccess.create(level, at));
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.setDamageValue(200);
		anvil.getSlot(0).set(sword);
		anvil.getSlot(1).set(new ItemStack(Items.IRON_INGOT, 1));
		ItemStack result = anvil.getSlot(2).getItem();
		check(helper, !result.isEmpty() && result.getDamageValue() == 0, "Masterwork did not offer a full repair: %s", result);
		anvil.getSlot(2).onTake(p, result);
		check(helper, !Active.on(p, Specialization.ARMORER), "Masterwork did not end with the repair");
		// Thrifty Hands V −20% levels; Preservation V: the limit is 50; Smith's Eye V; the price: smelting ×0.75 and no Second Smelt
		check(helper, Smithing.repairCost(p, 20) == 16 && Smithing.tooExpensive(p) == 50, "Thrifty Hands or Preservation is off");
		check(helper, near(Smithing.repairXp(p), 1.15) && near(Smithing.smeltXp(p), 0.75) && Smithing.secondSmelt(p) == 0, "Smith's Eye or the price is off");
		helper.succeed();
	}

	@GameTest
	public void reforgeAddsDurabilityOnce(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "reforge", Specialization.TINKERER, 30, NodeSlot.A1, 1, NodeSlot.P1, 5, NodeSlot.P2, 5, NodeSlot.C1, 5);
		p.giveExperienceLevels(30);
		station(helper, p, Blocks.SMITHING_TABLE);
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Reforge was not used looking at a smithing table");
		helper.setBlock(new BlockPos(4, 2, 4), Blocks.ANVIL);
		AnvilMenu anvil = new AnvilMenu(0, p.getInventory(), ContainerLevelAccess.create(level, helper.absolutePos(new BlockPos(4, 2, 4))));
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		int max = sword.getMaxDamage();
		sword.setDamageValue(200);
		anvil.getSlot(0).set(sword);
		anvil.getSlot(1).set(new ItemStack(Items.IRON_INGOT, 1));
		ItemStack result = anvil.getSlot(2).getItem();
		anvil.getSlot(2).onTake(p, result);
		check(helper, result.getMaxDamage() == Math.round(max * 1.10) && Smithing.reforged(result), "Reforge gave %s durability, not %s", result.getMaxDamage(),
				Math.round(max * 1.10));
		check(helper, !Active.on(p, Specialization.TINKERER), "Reforge did not end with the item");
		// The price: a repair restores 20% less of what it restored
		ItemStack left = new ItemStack(Items.IRON_SWORD);
		left.setDamageValue(200);
		ItemStack repaired = left.copy();
		repaired.setDamageValue(100);
		Smithing.repaired(p, left, repaired);
		check(helper, repaired.getDamageValue() == 120, "a Tinkerer's repair left %s damage, not 120", repaired.getDamageValue());
		// Upgrade Lore V; Polish V; Template Saver needs Mining 30
		check(helper, near(Smithing.smithXp(p), 1.15), "Upgrade Lore V gives ×%s", Smithing.smithXp(p));
		ItemStack upgraded = new ItemStack(Items.NETHERITE_SWORD);
		upgraded.setDamageValue(500);
		Smithing.polished(p, upgraded);
		check(helper, upgraded.getDamageValue() == 500 - Math.round(upgraded.getMaxDamage() * 0.2), "Polish V left %s damage", upgraded.getDamageValue());
		boolean saved = false;
		for (int i = 0; i < 50; i++) {
			saved |= Smithing.templateSaved(p);
		}
		check(helper, !saved, "Template Saver worked below Mining 30");
		// Trim Power: four iron-trimmed pieces at rank V give +2 armor
		check(helper, near(Smithing.trimBonus("iron", 4, 5), 2.0), "Trim Power V: %s armor for four iron trims", Smithing.trimBonus("iron", 4, 5));
		helper.succeed();
	}

	@GameTest
	public void theForgemasterRepairsLess(GameTestHelper helper) {
		Config.reset();
		ServerPlayer forge = specialist(helper, "lessrepair", Specialization.FORGEMASTER, 30, NodeSlot.C1, 5, NodeSlot.P3, 5);
		ItemStack left = new ItemStack(Items.IRON_SWORD);
		left.setDamageValue(200);
		ItemStack repaired = left.copy();
		repaired.setDamageValue(100);
		Smithing.repaired(forge, left, repaired);
		check(helper, repaired.getDamageValue() == 115, "a Forgemaster's repair left %s damage, not 115", repaired.getDamageValue());
		check(helper, near(Smithing.secondSmelt(forge), 0.2) && near(Smithing.fuelSaved(forge), 0.3), "Second Smelt or Coal Miser is off");
		// Fine Repair V: +15% of the item's durability
		ServerPlayer fine = specialist(helper, "finerepair", Specialization.ARMORER, 30, NodeSlot.P1, 5);
		ItemStack again = left.copy();
		again.setDamageValue(100);
		Smithing.repaired(fine, left, again);
		check(helper, again.getDamageValue() == 100 - Math.round(left.getMaxDamage() * 0.15), "Fine Repair V left %s damage", again.getDamageValue());
		helper.succeed();
	}

	// --- Arcana ----------------------------------------------------------------------------------

	@GameTest(maxTicks = 200)
	public void catalystBrewsTheAlchemistsOwnPotion(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "catalyst", Specialization.ALCHEMIST, 30, NodeSlot.A1, 1, NodeSlot.P2, 5, NodeSlot.C1, 5);
		Progression.setLevel(PlayerStore.get(p.getUUID()), Skill.FORAGING, 30);
		BlockPos at = station(helper, p, Blocks.BREWING_STAND);
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Catalyst was not used looking at a brewing stand");
		open(p, at);
		BrewingStandBlockEntity stand = (BrewingStandBlockEntity) level.getBlockEntity(at);
		stand.setItem(0, PotionContents.createItemStack(Items.POTION, Potions.AWKWARD));
		stand.setItem(3, new ItemStack(Items.COCOA_BEANS));
		stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
		check(helper, Arcana.mayBrew(stand), "an Alchemist with Foraging 30 may not brew Haste");
		// Brewer's Eye V; the price on enchanting
		check(helper, near(Arcana.brewXp(p), 1.15) && near(Arcana.enchantXp(p), 0.5), "Brewer's Eye or the Alchemist's price is off");
		// vanilla brews in 400 ticks; Catalyst in about 40
		helper.runAfterDelay(100, () -> {
			ItemStack brewed = stand.getItem(0);
			PotionContents contents = brewed.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
			boolean haste = false;
			for (MobEffectInstance e : contents.getAllEffects()) {
				haste |= e.getEffect().equals(MobEffects.HASTE);
			}
			check(helper, haste, "the stand did not brew Haste: %s", brewed);
			// Potent Brews (Arcana 15) × Extended V
			check(helper, near(PotentPotions.factor(brewed), 1.05 * 1.25), "the brewed potion lasts ×%s, not ×%s", PotentPotions.factor(brewed), 1.05 * 1.25);
			helper.succeed();
		});
	}

	@GameTest
	public void onlyAnAlchemistBrewsTheNewPotions(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer plain = TestPlayers.survivor(helper, "noherbs");
		ServerPlayer young = specialist(helper, "younghrb", Specialization.ALCHEMIST, 30);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.BREWING_STAND);
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		BrewingStandBlockEntity stand = (BrewingStandBlockEntity) level.getBlockEntity(at);
		stand.setItem(3, new ItemStack(Items.HONEYCOMB));
		open(plain, at);
		check(helper, !Arcana.mayBrew(stand), "a stand brews Absorption for someone who is no Alchemist");
		open(young, at);
		check(helper, !Arcana.mayBrew(stand), "an Alchemist below Foraging 30 brews Absorption");
		stand.setItem(3, new ItemStack(Items.NETHER_WART));
		check(helper, Arcana.mayBrew(stand), "a vanilla brew was stopped");
		helper.succeed();
	}

	@GameTest
	public void arcaneInsightRerollsOnceAndEnds(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "insight", Specialization.ENCHANTER, 30, NodeSlot.A1, 1, NodeSlot.P1, 5, NodeSlot.P3, 5, NodeSlot.C1, 5);
		p.giveExperienceLevels(30);
		BlockPos at = station(helper, p, Blocks.ENCHANTING_TABLE);
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Arcane Insight was not used looking at an enchanting table");
		EnchantmentMenu table = new EnchantmentMenu(0, p.getInventory(), ContainerLevelAccess.create(level, at));
		p.containerMenu = table;
		table.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
		table.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
		int seed = p.getEnchantmentSeed();
		check(helper, table.clickMenuButton(p, 3) && p.getEnchantmentSeed() != seed, "the free reroll did not reroll");
		check(helper, !table.clickMenuButton(p, 3), "the free reroll worked twice");
		int levels = p.experienceLevel;
		check(helper, table.clickMenuButton(p, 0), "the enchantment was refused");
		check(helper, !Active.on(p, Specialization.ENCHANTER), "Arcane Insight did not end with the enchantment");
		// Frugal V gives a level back; Lore V; Book Smart V; the price on brewing
		// (the offer cost 1 level; Frugal V gives it back, and the trunk's Thrifty Magic may give one more)
		check(helper, p.experienceLevel >= levels, "Frugal V did not give the level back: %s, before %s", p.experienceLevel, levels);
		check(helper, near(Arcana.enchantXp(p), 1.15) && Arcana.bookCost(p, 20) == 16 && near(Arcana.brewXp(p), 0.5), "Lore, Book Smart or the price is off");
		p.containerMenu = p.inventoryMenu;
		helper.succeed();
	}

	@GameTest
	public void hexLeavesABigCloud(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "hex", Specialization.HEXER, 30, NodeSlot.A1, 1, NodeSlot.P1, 5, NodeSlot.P2, 5, NodeSlot.P3, 5, NodeSlot.C1, 5);
		ItemStack splash = PotionContents.createItemStack(Items.SPLASH_POTION, Potions.POISON);
		p.setItemInHand(InteractionHand.MAIN_HAND, splash.copy());
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Hex was not used with a splash potion");
		ThrownSplashPotion potion = new ThrownSplashPotion(level, p, splash.copy());
		Vec3 thrown = new Vec3(0, 0.2, 0.5);
		potion.setDeltaMovement(thrown);
		level.addFreshEntity(potion);
		check(helper, !Active.on(p, Specialization.HEXER), "Hex did not end with the throw");
		check(helper, near(potion.getDeltaMovement().length(), thrown.length() * 1.25), "Strong Arm V: the potion flies at %s", potion.getDeltaMovement().length());
		// The splash, as the game does it on a hit, with the Hexer's nodes around it
		Vec3 where = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3)));
		potion.setPos(where.x, where.y, where.z);
		LivingEntity zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 2, 3), EntitySpawnReason.NATURAL);
		Arcana.hitting(potion);
		// Concentrated V and Lingering Venom V on a negative effect; Careful Aim V spares the thrower
		zombie.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), p);
		p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), p);
		potion.onHitAsPotion(level, splash, new BlockHitResult(where, Direction.UP, helper.absolutePos(new BlockPos(3, 1, 3)), false));
		Arcana.hit(potion);
		MobEffectInstance weak = zombie.getEffect(MobEffects.WEAKNESS);
		check(helper, weak != null && weak.getDuration() == Math.round(100 * 1.25 * 1.30), "a Hexer's weakness lasts %s, not %s", weak == null ? 0 : weak.getDuration(),
				Math.round(100 * 1.25 * 1.30));
		check(helper, !p.hasEffect(MobEffects.WEAKNESS), "Careful Aim V let the thrower's own potion in");
		AreaEffectCloud cloud = null;
		for (Entity e : level.getEntities((Entity) null, new AABB(BlockPos.containing(where)).inflate(3), e -> e instanceof AreaEffectCloud)) {
			cloud = (AreaEffectCloud) e;
		}
		check(helper, cloud != null && near(cloud.getRadius(), 6) && cloud.getDuration() == 1200, "Hex left no cloud of radius 6 for 1200 ticks: %s",
				cloud == null ? "none" : cloud.getRadius() + ", " + cloud.getDuration());
		if (cloud != null) {
			cloud.discard();
		}
		potion.discard();
		// The Hexer's price: their own drink lasts 25% shorter
		PotentPotions.drinking(PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS), p);
		float scale = PotentPotions.scale();
		PotentPotions.drinking(ItemStack.EMPTY, p);
		check(helper, near(scale, 0.75), "a Hexer's drink lasts ×%s, not ×0.75", scale);
		helper.succeed();
	}
}
