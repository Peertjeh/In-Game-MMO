package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.CombatTests.swing;
import static com.extremeprogression.gametest.SkeletonTests.check;
import static com.extremeprogression.gametest.SpecTests.specialist;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.ability.AbilityService;
import com.extremeprogression.antiexploit.PlacedBlocks;
import com.extremeprogression.config.Config;
import com.extremeprogression.mixin.FishingHookAccess;
import com.extremeprogression.network.OreSensePayload;
import com.extremeprogression.skill.Specialization;
import com.extremeprogression.spec.Active;
import com.extremeprogression.spec.Farming;
import com.extremeprogression.spec.Fishing;
import com.extremeprogression.spec.Foraging;
import com.extremeprogression.spec.Mining;
import com.extremeprogression.spec.Specs;
import com.extremeprogression.tree.NodeSlot;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 6, part 2: the abilities of Mining, Foraging, Farming and Fishing, their prices, and their passives and
 * choice nodes with a fixed effect (rank V where a chance becomes certain). Every player here is at level 30.
 */
public class SpecGatherTests {
	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1e-3;
	}

	/** Puts the player 2.5 blocks south of a block, eyes level with its middle, looking straight at its south face. */
	private static void facing(ServerPlayer p, BlockPos block) {
		p.setPos(block.getX() + 0.5, block.getY() + 0.5 - p.getEyeHeight(), block.getZ() + 2.5);
		p.setYRot(180);
		p.setYHeadRot(180);
		p.setXRot(0);
	}

	/** A wall across x 1..5, y 1..4 at z 3: the middle block is (3, 2, 3). */
	private static void wall(GameTestHelper helper, Block block) {
		for (int x = 1; x <= 5; x++) {
			for (int y = 1; y <= 4; y++) {
				helper.setBlock(new BlockPos(x, y, 3), block);
			}
		}
	}

	private static int dropped(GameTestHelper helper, BlockPos at, Item item) {
		int n = 0;
		for (Entity e : helper.getLevel().getEntities((Entity) null, new AABB(at).inflate(2), e -> e instanceof ItemEntity)) {
			ItemStack stack = ((ItemEntity) e).getItem();
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static BlockState state(Block block) {
		return block.defaultBlockState();
	}

	// --- Mining ----------------------------------------------------------------------------------

	@GameTest
	public void oreSenseFindsTheOresNearYou(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = specialist(helper, "sense", Specialization.PROSPECTOR, 30, NodeSlot.A1, 1, NodeSlot.P1, 5, NodeSlot.P2, 5, NodeSlot.P3, 5);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.DIAMOND_ORE);
		helper.setBlock(new BlockPos(4, 2, 2), Blocks.IRON_ORE);
		BlockPos diamond = helper.absolutePos(new BlockPos(2, 2, 2));
		BlockPos iron = helper.absolutePos(new BlockPos(4, 2, 2));
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 4)));
		p.setPos(at.x, at.y, at.z);
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED && Active.on(p, Specialization.PROSPECTOR), "Ore Sense was not used");
		List<BlockPos> sensed = Mining.sense(p, 1).stream().map(OreSensePayload.Ore::pos).toList();
		check(helper, sensed.contains(diamond) && sensed.contains(iron), "Ore Sense did not find both ores: %s", sensed);
		// Gem Eye: diamond only
		ServerPlayer gems = specialist(helper, "gems", Specialization.PROSPECTOR, 30, NodeSlot.C1, 5);
		gems.setPos(at.x, at.y, at.z);
		List<BlockPos> gemSensed = Mining.sense(gems, 1).stream().map(OreSensePayload.Ore::pos).toList();
		check(helper, gemSensed.contains(diamond) && !gemSensed.contains(iron), "Gem Eye showed iron, or no diamond");
		// The price on stone; Quick Pick V on ores; Prospector's Eye V; Rich Vein V
		check(helper, near(Specs.destroySpeed(p, state(Blocks.STONE), 1f), 0.9), "Prospector breaks stone at ×%s", Specs.destroySpeed(p, state(Blocks.STONE), 1f));
		check(helper, near(Specs.destroySpeed(p, state(Blocks.DIAMOND_ORE), 1f), 1.15), "Quick Pick V breaks ore at ×%s",
				Specs.destroySpeed(p, state(Blocks.DIAMOND_ORE), 1f));
		check(helper, near(Mining.xp(p, state(Blocks.DIAMOND_ORE), 100), 115), "Prospector's Eye V gives %s", Mining.xp(p, state(Blocks.DIAMOND_ORE), 100));
		check(helper, near(Mining.doubleOreChance(p), 0.05), "Rich Vein V adds %s", Mining.doubleOreChance(p));
		helper.succeed();
	}

	@GameTest
	public void earthmoverDigsThreeByThree(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = specialist(helper, "earth", Specialization.EXCAVATOR, 30, NodeSlot.A1, 1, NodeSlot.P1, 5, NodeSlot.P4, 5);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SHOVEL));
		wall(helper, Blocks.DIRT);
		BlockPos middle = helper.absolutePos(new BlockPos(3, 2, 3));
		facing(p, middle);
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Earthmover was not used with a shovel");
		p.gameMode.destroyBlock(middle);
		for (int x = 2; x <= 4; x++) {
			for (int y = 1; y <= 3; y++) {
				helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, y, 3));
			}
		}
		helper.assertBlockPresent(Blocks.DIRT, new BlockPos(1, 2, 3));
		helper.assertBlockPresent(Blocks.DIRT, new BlockPos(5, 2, 3));
		helper.assertBlockPresent(Blocks.DIRT, new BlockPos(3, 4, 3));
		// Fast Dig V +15% with a shovel; the price −15% with a pickaxe; Patient Brush V +50% brushing XP
		check(helper, near(Specs.destroySpeed(p, state(Blocks.DIRT), 1f), 1.15), "Fast Dig V digs at ×%s", Specs.destroySpeed(p, state(Blocks.DIRT), 1f));
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
		check(helper, near(Specs.destroySpeed(p, state(Blocks.COBBLESTONE), 1f), 0.85), "Excavator mines at ×%s with a pickaxe",
				Specs.destroySpeed(p, state(Blocks.COBBLESTONE), 1f));
		check(helper, near(Mining.brushXp(p), 1.5), "Patient Brush V: ×%s", Mining.brushXp(p));
		helper.succeed();
	}

	@GameTest
	public void tunnelBoreBreaksOneByThree(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = specialist(helper, "bore", Specialization.TUNNELER, 30, NodeSlot.A1, 1, NodeSlot.C1, 5);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
		wall(helper, Blocks.STONE);
		BlockPos middle = helper.absolutePos(new BlockPos(3, 2, 3));
		facing(p, middle);
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Tunnel Bore was not used with a pickaxe");
		p.gameMode.destroyBlock(middle);
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 1, 3));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 3, 3));
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 2, 3));
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(4, 2, 3));
		// The price: ores −25% XP and no double drops; Deep Delver V: deepslate twice as fast
		check(helper, near(Mining.xp(p, state(Blocks.IRON_ORE), 100), 75) && Mining.noDoubles(p), "Tunneler's price is not counted");
		check(helper, near(Specs.destroySpeed(p, state(Blocks.DEEPSLATE), 1f), 2.0), "Deep Delver V mines deepslate at ×%s",
				Specs.destroySpeed(p, state(Blocks.DEEPSLATE), 1f));
		helper.succeed();
	}

	// --- Foraging --------------------------------------------------------------------------------

	@GameTest
	public void treeFellerTakesTheTreeButNotPlacedLogs(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "feller", Specialization.LUMBERJACK, 30, NodeSlot.A1, 1, NodeSlot.P1, 5, NodeSlot.P2, 5, NodeSlot.P3, 5);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
		for (int y = 1; y <= 6; y++) {
			helper.setBlock(new BlockPos(3, y, 3), Blocks.OAK_LOG);
		}
		helper.setBlock(new BlockPos(4, 4, 3), Blocks.OAK_LOG);
		PlacedBlocks.set(level, helper.absolutePos(new BlockPos(3, 6, 3)), PlacedBlocks.PLACED);
		facing(p, helper.absolutePos(new BlockPos(3, 2, 3)));
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Tree Feller was not used looking at a log");
		p.gameMode.destroyBlock(helper.absolutePos(new BlockPos(3, 1, 3)));
		for (int y = 1; y <= 5; y++) {
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, y, 3));
		}
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(4, 4, 3));
		helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(3, 6, 3));
		check(helper, !Active.on(p, Specialization.LUMBERJACK), "Tree Feller did not end with the tree");
		// Sharp Axe V +15% on logs; Woodsman V +15% XP; Timber V +5% double logs; the price: wild plants ×0.5 XP
		check(helper, near(Specs.destroySpeed(p, state(Blocks.OAK_LOG), 1f), 1.15), "Sharp Axe V chops at ×%s", Specs.destroySpeed(p, state(Blocks.OAK_LOG), 1f));
		check(helper, near(Foraging.logXp(p), 1.15) && near(Foraging.doubleLogChance(p), 0.05) && near(Foraging.wildXp(p), 0.5),
				"Woodsman, Timber or the price is off");
		helper.succeed();
	}

	@GameTest
	public void bountifulHarvestTriplesAWildYield(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = specialist(helper, "bounty", Specialization.GATHERER, 30, NodeSlot.A1, 1, NodeSlot.P1, 5, NodeSlot.P3, 5);
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.MYCELIUM);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.RED_MUSHROOM);
		BlockPos mushroom = helper.absolutePos(new BlockPos(2, 2, 2));
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Bountiful Harvest was not used");
		p.gameMode.destroyBlock(mushroom);
		check(helper, dropped(helper, mushroom, Items.RED_MUSHROOM) == 3, "a wild mushroom gave %s during Bountiful Harvest, not 3",
				dropped(helper, mushroom, Items.RED_MUSHROOM));
		// Green Fingers V +15% XP; Quick Picking V +25% on melons; the price −15% on logs
		check(helper, near(Foraging.wildXp(p), 1.15), "Green Fingers V gives ×%s", Foraging.wildXp(p));
		check(helper, near(Specs.destroySpeed(p, state(Blocks.MELON), 1f), 1.25), "Quick Picking V: ×%s on a melon", Specs.destroySpeed(p, state(Blocks.MELON), 1f));
		check(helper, near(Specs.destroySpeed(p, state(Blocks.OAK_LOG), 1f), 0.85), "a Gatherer chops at ×%s", Specs.destroySpeed(p, state(Blocks.OAK_LOG), 1f));
		helper.succeed();
	}

	@GameTest
	public void wildRemedyAndTheHerbalistsFood(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "remedy", Specialization.HERBALIST, 30, NodeSlot.A1, 1);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Wild Remedy was not used with food in hand");
		p.getFoodData().setFoodLevel(10);
		p.getFoodData().setSaturation(0);
		new ItemStack(Items.APPLE).finishUsingItem(level, p);
		check(helper, p.getFoodData().getFoodLevel() == 14 && near(p.getFoodData().getSaturationLevel(), 4.8f), "an apple during Wild Remedy: %s food, %s saturation",
				p.getFoodData().getFoodLevel(), p.getFoodData().getSaturationLevel());
		check(helper, p.hasEffect(MobEffects.REGENERATION), "Wild Remedy gave no Regeneration");
		// The price: bread fills 1 less (20% of 5)
		p.getFoodData().setFoodLevel(10);
		new ItemStack(Items.BREAD).finishUsingItem(level, p);
		check(helper, p.getFoodData().getFoodLevel() == 14, "bread filled a Herbalist to %s, not 14", p.getFoodData().getFoodLevel());
		// Herb Knowledge V, Strong Stomach V, Nourished V, Berry Lore V
		ServerPlayer herb = specialist(helper, "herbs", Specialization.HERBALIST, 30, NodeSlot.P2, 5, NodeSlot.P3, 5, NodeSlot.P4, 5, NodeSlot.C2, 5);
		herb.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
		check(helper, herb.getEffect(MobEffects.REGENERATION) != null && herb.getEffect(MobEffects.REGENERATION).getDuration() == 150, "Herb Knowledge V is not +50%%");
		herb.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 0));
		check(helper, !herb.hasEffect(MobEffects.HUNGER), "Strong Stomach V let Hunger in");
		herb.setHealth(10);
		herb.getFoodData().setFoodLevel(10);
		new ItemStack(Items.SWEET_BERRIES).finishUsingItem(level, herb);
		check(helper, herb.getFoodData().getFoodLevel() == 14 && near(herb.getHealth(), 12f), "berries: %s food (not 14), %s health (not 12)",
				herb.getFoodData().getFoodLevel(), herb.getHealth());
		helper.succeed();
	}

	// --- Farming ---------------------------------------------------------------------------------

	@GameTest
	public void harvestFestivalDoublesAndReplants(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "festival", Specialization.GROWER, 30, NodeSlot.A1, 1, NodeSlot.P1, 5, NodeSlot.P2, 5);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.FARMLAND);
		CropBlock wheat = (CropBlock) Blocks.WHEAT;
		helper.setBlock(new BlockPos(2, 2, 2), wheat.getStateForAge(7));
		BlockPos crop = helper.absolutePos(new BlockPos(2, 2, 2));
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Harvest Festival was not used with a hoe");
		p.gameMode.destroyBlock(crop);
		BlockState after = level.getBlockState(crop);
		check(helper, after.is(Blocks.WHEAT) && wheat.getAge(after) == 0, "the wheat did not replant itself: %s", after);
		check(helper, dropped(helper, crop, Items.WHEAT) >= 2, "Harvest Festival gave %s wheat, not at least 2", dropped(helper, crop, Items.WHEAT));
		// Crop Lore V +15%; Bumper Crop V +5%; the price: breeding ×0.5 and no bonuses
		check(helper, near(Farming.cropXp(p), 1.15) && near(Farming.extraCropChance(p), 0.05), "Crop Lore or Bumper Crop is off");
		check(helper, near(Farming.breedXp(p), 0.5) && Farming.noBreedingBonus(p), "the Grower's price is not counted");
		// Seed Saver V: always replants, Harvest Festival or not
		ServerPlayer saver = specialist(helper, "saver", Specialization.GROWER, 30, NodeSlot.C2, 5);
		helper.setBlock(new BlockPos(4, 1, 2), Blocks.FARMLAND);
		BlockPos other = helper.absolutePos(new BlockPos(4, 2, 2));
		level.setBlockAndUpdate(other, Blocks.AIR.defaultBlockState());
		Farming.harvested(saver, level, other, wheat.getStateForAge(7), null);
		check(helper, level.getBlockState(other).is(Blocks.WHEAT), "Seed Saver V did not replant");
		helper.succeed();
	}

	@GameTest
	public void herdingCallGivesTwins(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "herd", Specialization.HERDER, 30, NodeSlot.A1, 1, NodeSlot.P1, 5, NodeSlot.P2, 5, NodeSlot.P4, 5);
		Animal a = (Animal) helper.spawn(EntityTypes.COW, new BlockPos(1, 2, 1), EntitySpawnReason.NATURAL);
		Animal b = (Animal) helper.spawn(EntityTypes.COW, new BlockPos(2, 2, 1), EntitySpawnReason.NATURAL);
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Herding Call was not used");
		AABB area = new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).inflate(12);
		int before = level.getEntities((Entity) null, area, e -> e.getType() == EntityTypes.COW).size();
		AgeableMob child = a.getBreedOffspring(level, b);
		child.setBaby(true);
		child.setPos(a.getX(), a.getY(), a.getZ());
		Farming.bred(p, a, b, child);
		int after = level.getEntities((Entity) null, area, e -> e.getType() == EntityTypes.COW).size();
		check(helper, after == before + 1, "Herding Call's breeding gave %s extra cows, not a twin", after - before);
		check(helper, Active.get(p, Specialization.HERDER).value == 0, "the twins of Herding Call can come again");
		// Quick Growth V: the baby grows up 20% faster; Calm Herd V: the parents wait 25% less
		check(helper, child.getAge() == -19200, "Quick Growth V left the baby at age %s, not -19200", child.getAge());
		a.setAge(6000);
		b.setAge(6000);
		Specs.tick(level.getServer());
		check(helper, a.getAge() == 4500 && b.getAge() == 4500, "Calm Herd V left the parents at %s and %s, not 4500", a.getAge(), b.getAge());
		// Breeder V; the price: no extra crops
		check(helper, near(Farming.breedXp(p), 1.15) && near(Farming.shearXp(p), 1.15) && Farming.noExtraCrops(p), "Breeder or the Herder's price is off");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void rallyThePackEmpowersYourWolves(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "rally", Specialization.BEASTMASTER, 30, NodeSlot.A1, 1, NodeSlot.P1, 5, NodeSlot.P4, 5);
		Wolf wolf = (Wolf) helper.spawn(EntityTypes.WOLF, new BlockPos(2, 2, 2), EntitySpawnReason.NATURAL);
		wolf.tame(p);
		p.setPos(wolf.getX() + 2, wolf.getY(), wolf.getZ());
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Rally the Pack was not used");
		check(helper, near(Farming.tamerXp(p), 1.25), "Tamer V gives ×%s", Farming.tamerXp(p));
		helper.runAfterDelay(25, () -> {
			check(helper, wolf.hasEffect(MobEffects.STRENGTH) && wolf.hasEffect(MobEffects.RESISTANCE), "the wolf has no Strength or Resistance from Rally");
			// Alpha Bond V: your companion deals +20%
			LivingEntity creeper = helper.spawn(EntityTypes.CREEPER, new BlockPos(5, 2, 5), EntitySpawnReason.NATURAL);
			creeper.hurtServer(level, level.damageSources().mobAttack(wolf), 10);
			check(helper, near(creeper.getHealth(), 8f), "Alpha Bond V left the creeper at %s, not 8", creeper.getHealth());
			// The price: −10% on your own hits while a wolf of yours is near
			LivingEntity other = helper.spawn(EntityTypes.CREEPER, new BlockPos(5, 2, 1), EntitySpawnReason.NATURAL);
			swing(p, other, false);
			other.hurtServer(level, level.damageSources().playerAttack(p), 10);
			check(helper, near(other.getHealth(), 11f), "a Beastmaster's hit left the creeper at %s, not 11", other.getHealth());
			helper.succeed();
		});
	}

	// --- Fishing ---------------------------------------------------------------------------------

	private static FishingHook hook(ServerLevel level, ServerPlayer p) {
		FishingHook hook = new FishingHook(p, level, 0, 0);
		p.fishing = hook;
		return hook;
	}

	@GameTest
	public void feedingFrenzyHurriesTheBite(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "frenzy", Specialization.ANGLER, 30, NodeSlot.A1, 1, NodeSlot.P2, 5, NodeSlot.C2, 5);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Feeding Frenzy was not used with a rod");
		check(helper, near(Fishing.waitFactor(p), 0.2), "Feeding Frenzy waits ×%s, not ×0.2", Fishing.waitFactor(p));
		FishingHook hook = hook(level, p);
		((FishingHookAccess) hook).extremeprogression$setTimeUntilLured(100);
		Specs.tick(level.getServer());
		int left = ((FishingHookAccess) hook).extremeprogression$getTimeUntilLured();
		check(helper, left == 96, "Feeding Frenzy took the wait to %s, not 96 (4 extra ticks a tick)", left);
		// Fishmonger V +15% on fish; Big Catch V: fish fill 2 more
		check(helper, near(Fishing.xp(p, new ItemStack(Items.COD)), 1.15), "Fishmonger V gives ×%s", Fishing.xp(p, new ItemStack(Items.COD)));
		p.getFoodData().setFoodLevel(10);
		new ItemStack(Items.COOKED_COD).finishUsingItem(level, p);
		// (10 + 5, +1 for Fisher's Fare at Fishing 15, +2 for Big Catch V)
		check(helper, p.getFoodData().getFoodLevel() == 18, "cooked cod filled to %s, not 18", p.getFoodData().getFoodLevel());
		// Quick Bite V and the Treasure Hunter's price
		ServerPlayer quick = specialist(helper, "quickbite", Specialization.ANGLER, 30, NodeSlot.P1, 5);
		ServerPlayer slow = specialist(helper, "slowbite", Specialization.TREASURE_HUNTER, 30);
		check(helper, near(Fishing.waitFactor(quick), 0.85) && near(Fishing.waitFactor(slow), 1.25), "the bite factors are %s and %s, not 0.85 and 1.25",
				Fishing.waitFactor(quick), Fishing.waitFactor(slow));
		hook.discard();
		helper.succeed();
	}

	@GameTest
	public void luckyCastCatchesTreasure(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "lucky", Specialization.TREASURE_HUNTER, 30, NodeSlot.A1, 1, NodeSlot.P2, 5);
		ItemStack rod = new ItemStack(Items.FISHING_ROD);
		p.setItemInHand(InteractionHand.MAIN_HAND, rod);
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Lucky Cast was not used with a rod");
		FishingHook hook = hook(level, p);
		List<ItemStack> catchItems = new ArrayList<>(List.of(new ItemStack(Items.COD)));
		Fishing.caught(p, hook, rod, catchItems);
		check(helper, !catchItems.isEmpty() && catchItems.stream().allMatch(s -> Fishing.kind(s) == Fishing.Kind.TREASURE), "Lucky Cast caught %s, not treasure",
				catchItems);
		check(helper, !Active.on(p, Specialization.TREASURE_HUNTER), "Lucky Cast did not end with its catch");
		// Appraiser V +20% on treasure
		check(helper, near(Fishing.xp(p, new ItemStack(Items.NAME_TAG)), 1.2), "Appraiser V gives ×%s", Fishing.xp(p, new ItemStack(Items.NAME_TAG)));
		hook.discard();
		helper.succeed();
	}

	@GameTest
	public void reelInYanksAndStuns(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "reel", Specialization.HOOKMASTER, 30, NodeSlot.A1, 1, NodeSlot.P1, 2, NodeSlot.P2, 5, NodeSlot.C2, 5);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
		LivingEntity zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 2, 2), EntitySpawnReason.NATURAL);
		p.setPos(zombie.getX() + 8, zombie.getY(), zombie.getZ());
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Reel In was not used with a rod");
		FishingHook hook = hook(level, p);
		((FishingHookAccess) hook).extremeprogression$setHookedEntity(zombie);
		Specs.tick(level.getServer());
		Vec3 toward = p.position().subtract(zombie.position()).horizontal().normalize();
		check(helper, zombie.getDeltaMovement().horizontal().dot(toward) > 0.5, "Reel In did not yank the zombie: %s", zombie.getDeltaMovement());
		MobEffectInstance slow = zombie.getEffect(MobEffects.SLOWNESS);
		check(helper, slow != null && slow.getAmplifier() >= 9, "the yanked zombie is not stunned");
		check(helper, zombie.getHealth() < 20f, "Barbed Hook II dealt no damage");
		check(helper, !Active.on(p, Specialization.HOOKMASTER), "Reel In did not end with the hook");
		hook.discard();
		// Long Cast V: the bobber flies 25% farther
		FishingHook cast = new FishingHook(p, level, 0, 0);
		Vec3 thrown = cast.getDeltaMovement();
		level.addFreshEntity(cast);
		check(helper, near(cast.getDeltaMovement().length(), thrown.length() * 1.25), "Long Cast V: the bobber flies at %s, not %s", cast.getDeltaMovement().length(),
				thrown.length() * 1.25);
		// Drag Hook V: reeling in pulls twice as hard (vanilla's 0.1 of the distance, once more)
		LivingEntity pulled = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(4, 2, 4), EntitySpawnReason.NATURAL);
		pulled.setDeltaMovement(Vec3.ZERO);
		Fishing.pulled(cast, pulled);
		Vec3 extra = p.position().subtract(pulled.position()).scale(0.1);
		check(helper, pulled.getDeltaMovement().distanceTo(extra) < 1e-3, "Drag Hook V added %s, not %s", pulled.getDeltaMovement(), extra);
		cast.discard();
		// Hookmaster's price: −30% on catches
		check(helper, near(Fishing.xp(p, new ItemStack(Items.COD)), 0.7), "a Hookmaster's cod gives ×%s", Fishing.xp(p, new ItemStack(Items.COD)));
		helper.succeed();
	}

	// --- Part 1 nodes left over: Heavy Blows (Crusher) and Headshot (Sniper) ---------------------

	@GameTest
	public void heavyBlowsAndHeadshot(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer crusher = specialist(helper, "heavy", Specialization.CRUSHER, 30, NodeSlot.P1, 5);
		crusher.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
		LivingEntity a = helper.spawn(EntityTypes.CREEPER, new BlockPos(1, 2, 1), EntitySpawnReason.NATURAL);
		swing(crusher, a, false);
		a.hurtServer(level, level.damageSources().playerAttack(crusher), 10);
		check(helper, near(a.getHealth(), 9f), "Heavy Blows V left the creeper at %s, not 9", a.getHealth());
		ServerPlayer sniper = specialist(helper, "headshot", Specialization.SNIPER, 30, NodeSlot.C2, 5);
		LivingEntity b = helper.spawn(EntityTypes.CREEPER, new BlockPos(4, 2, 4), EntitySpawnReason.NATURAL);
		sniper.setPos(b.getX(), b.getY(), b.getZ() - 12);
		var arrow = EntityTypes.ARROW.create(level, EntitySpawnReason.TRIGGERED);
		arrow.setOwner(sniper);
		arrow.setCritArrow(true);
		arrow.setPos(b.getX(), b.getEyeY(), b.getZ() - 1.0);
		arrow.setDeltaMovement(0, 0, 1.5);
		b.hurtServer(level, level.damageSources().arrow(arrow, sniper), 10);
		// 10 × 1.05 (Keen Eye) × 1.30 (Headshot V)
		check(helper, near(b.getHealth(), 20 - 10 * 1.05 * 1.30), "Headshot V left the creeper at %s", b.getHealth());
		helper.succeed();
	}
}
