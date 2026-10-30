package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.SkeletonTests.check;

import java.util.List;

import com.extremeprogression.antiexploit.PlacedBlocks;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.progression.XpCurve;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.xp.FishingXp;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 3: the gathering skills earn XP the way the design says, through the game's own code paths (breaking,
 * placing, bonemeal, pistons, falling blocks, lava and water, tree growth, harvests, fishing, animals).
 */
public class GatheringTests {
	/** XP earned in a skill so far (exact enough at low totals, where the budget factor is about 1). */
	static double earned(ServerPlayer p, Skill skill) {
		SkillData d = PlayerStore.get(p.getUUID()).get(skill);
		return XpCurve.cumulative(d.level) + d.xp;
	}

	static void near(GameTestHelper helper, double actual, double expected, String what) {
		check(helper, Math.abs(actual - expected) <= Math.max(0.5, expected * 0.01), "%s: %s XP, expected %s", what, actual, expected);
	}

	static ServerPlayer holding(GameTestHelper helper, String name, ItemStack stack) {
		ServerPlayer p = TestPlayers.survivor(helper, name);
		p.setItemInHand(InteractionHand.MAIN_HAND, stack);
		return p;
	}

	/** Places a block the way a player does, from its item. */
	static void place(GameTestHelper helper, ServerPlayer p, BlockPos rel, ItemStack stack) {
		BlockPos at = helper.absolutePos(rel);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false);
		((BlockItem) stack.getItem()).place(new BlockPlaceContext(p, InteractionHand.MAIN_HAND, stack, hit));
	}

	static void breakBlock(GameTestHelper helper, ServerPlayer p, BlockPos rel) {
		p.gameMode.destroyBlock(helper.absolutePos(rel));
	}

	static int marks(GameTestHelper helper, BlockPos rel) {
		return PlacedBlocks.flags(helper.getLevel(), helper.absolutePos(rel));
	}

	@GameTest
	public void naturalOresGiveMiningXpPlacedOnesNothing(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = holding(helper, "miner", new ItemStack(Items.DIAMOND_PICKAXE));
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.DIAMOND_ORE);
		breakBlock(helper, p, new BlockPos(1, 1, 1));
		near(helper, earned(p, Skill.MINING), 600, "a natural diamond ore");

		place(helper, p, new BlockPos(2, 1, 1), new ItemStack(Items.DIAMOND_ORE));
		check(helper, (marks(helper, new BlockPos(2, 1, 1)) & PlacedBlocks.PLACED) != 0, "a placed ore was not marked");
		breakBlock(helper, p, new BlockPos(2, 1, 1));
		near(helper, earned(p, Skill.MINING), 600, "after a placed diamond ore");
		check(helper, marks(helper, new BlockPos(2, 1, 1)) == 0, "the mark stayed after the block was broken");

		// Without a pickaxe that can mine it, an ore gives nothing; a shovel block needs a shovel
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));
		helper.setBlock(new BlockPos(3, 1, 1), Blocks.DIAMOND_ORE);
		breakBlock(helper, p, new BlockPos(3, 1, 1));
		helper.setBlock(new BlockPos(4, 1, 1), Blocks.CLAY);
		breakBlock(helper, p, new BlockPos(4, 1, 1));
		near(helper, earned(p, Skill.MINING), 600, "a diamond ore with a wooden pickaxe and clay without a shovel");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SHOVEL));
		helper.setBlock(new BlockPos(5, 1, 1), Blocks.CLAY);
		breakBlock(helper, p, new BlockPos(5, 1, 1));
		near(helper, earned(p, Skill.MINING), 606, "clay with a shovel");
		helper.succeed();
	}

	@GameTest
	public void lavaAndWaterMakeNoXp(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = holding(helper, "generator", new ItemStack(Items.DIAMOND_PICKAXE));
		helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 0, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 1, 1), Blocks.WATER);
		// A lava source next to water turns into obsidian at once
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.LAVA);
		check(helper, helper.getBlockState(new BlockPos(1, 1, 1)).is(Blocks.OBSIDIAN), "lava next to water did not turn into obsidian");
		check(helper, (marks(helper, new BlockPos(1, 1, 1)) & PlacedBlocks.PLACED) != 0, "generated obsidian was not marked");
		breakBlock(helper, p, new BlockPos(1, 1, 1));
		near(helper, earned(p, Skill.MINING), 0, "generated obsidian");
		helper.setBlock(new BlockPos(4, 1, 4), Blocks.OBSIDIAN);
		breakBlock(helper, p, new BlockPos(4, 1, 4));
		near(helper, earned(p, Skill.MINING), 20, "natural obsidian");
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void blocksAPistonMovedCountAsPlaced(GameTestHelper helper) {
		Config.reset();
		helper.setBlock(new BlockPos(2, 1, 4), Blocks.PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(3, 1, 4), Blocks.IRON_ORE);
		helper.setBlock(new BlockPos(1, 1, 4), Blocks.REDSTONE_BLOCK);
		helper.succeedWhen(() -> {
			check(helper, helper.getBlockState(new BlockPos(4, 1, 4)).is(Blocks.IRON_ORE), "the piston has not pushed the ore yet");
			check(helper, (marks(helper, new BlockPos(4, 1, 4)) & PlacedBlocks.PLACED) != 0, "the pushed ore was not marked");
		});
	}

	@GameTest(maxTicks = 100)
	public void fallingBlocksTakeTheirMarksAlong(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = holding(helper, "sand", ItemStack.EMPTY);
		helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(5, 0, 5), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 5, 2), Blocks.SAND);
		place(helper, p, new BlockPos(5, 5, 5), new ItemStack(Items.SAND));
		helper.succeedWhen(() -> {
			check(helper, helper.getBlockState(new BlockPos(2, 1, 2)).is(Blocks.SAND) && helper.getBlockState(new BlockPos(5, 1, 5)).is(Blocks.SAND),
					"the sand has not landed yet");
			check(helper, (marks(helper, new BlockPos(5, 1, 5)) & PlacedBlocks.PLACED) != 0, "placed sand lost its mark by falling");
			check(helper, marks(helper, new BlockPos(2, 1, 2)) == 0, "natural sand got a mark by falling");
		});
	}

	@GameTest
	public void bonemealedCropsGiveNoXp(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = holding(helper, "farmer", ItemStack.EMPTY);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(1, 0, 1), Blocks.FARMLAND);
		helper.setBlock(new BlockPos(2, 0, 1), Blocks.FARMLAND);
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.WHEAT);
		BlockPos wheat = helper.absolutePos(new BlockPos(1, 1, 1));
		for (int i = 0; i < 20 && !((CropBlock) Blocks.WHEAT).isMaxAge(level.getBlockState(wheat)); i++) {
			BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, wheat);
		}
		check(helper, ((CropBlock) Blocks.WHEAT).isMaxAge(level.getBlockState(wheat)), "bonemeal did not grow the wheat");
		check(helper, (marks(helper, new BlockPos(1, 1, 1)) & PlacedBlocks.BONEMEALED) != 0, "bonemealed wheat was not marked");
		breakBlock(helper, p, new BlockPos(1, 1, 1));
		near(helper, earned(p, Skill.FARMING), 0, "bonemealed wheat");

		helper.setBlock(new BlockPos(2, 1, 1), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
		breakBlock(helper, p, new BlockPos(2, 1, 1));
		near(helper, earned(p, Skill.FARMING), 8, "wheat grown by itself");
		helper.setBlock(new BlockPos(2, 1, 1), Blocks.WHEAT);
		breakBlock(helper, p, new BlockPos(2, 1, 1));
		near(helper, earned(p, Skill.FARMING), 8, "after unripe wheat");
		helper.succeed();
	}

	private static ServerPlayer growTree(GameTestHelper helper, boolean bonemeal) {
		ServerPlayer p = holding(helper, "forester", new ItemStack(Items.IRON_AXE));
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(4, 0, 4), Blocks.DIRT);
		place(helper, p, new BlockPos(4, 1, 4), new ItemStack(Items.BIRCH_SAPLING));
		BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
		for (int i = 0; i < 60 && level.getBlockState(at).is(Blocks.BIRCH_SAPLING); i++) {
			if (bonemeal) {
				BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, at);
			} else {
				((SaplingBlock) Blocks.BIRCH_SAPLING).advanceTree(level, at, level.getBlockState(at), level.getRandom());
			}
		}
		check(helper, level.getBlockState(at).is(Blocks.BIRCH_LOG), "the birch did not grow: %s", level.getBlockState(at));
		return p;
	}

	@GameTest(skyAccess = true)
	public void plantedTreesGiveAQuarter(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = growTree(helper, false);
		int flags = marks(helper, new BlockPos(4, 2, 4));
		check(helper, flags == PlacedBlocks.PLANTED, "a log of a planted tree has marks %s, not planted", flags);
		breakBlock(helper, p, new BlockPos(4, 2, 4));
		near(helper, earned(p, Skill.FORAGING), 18 * 0.25, "a log of a planted birch");
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.BIRCH_LOG);
		breakBlock(helper, p, new BlockPos(1, 1, 1));
		near(helper, earned(p, Skill.FORAGING), 18 * 1.25, "after a natural birch log");
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void bonemealedTreesGiveNothing(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = growTree(helper, true);
		check(helper, (marks(helper, new BlockPos(4, 2, 4)) & PlacedBlocks.BONEMEALED) != 0, "a log of a bonemealed tree was not marked");
		breakBlock(helper, p, new BlockPos(4, 2, 4));
		near(helper, earned(p, Skill.FORAGING), 0, "a log of a bonemealed birch");
		helper.succeed();
	}

	@GameTest
	public void wildPlantsAreForagingPlantedOnesFarming(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = holding(helper, "berries", ItemStack.EMPTY);
		for (int x = 1; x <= 6; x++) {
			helper.setBlock(new BlockPos(x, 0, 1), Blocks.GRASS_BLOCK);
		}
		// A wild melon, a melon its stem grew, and a placed one
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.MELON);
		breakBlock(helper, p, new BlockPos(1, 1, 1));
		near(helper, earned(p, Skill.FORAGING), 30, "a wild melon");
		helper.setBlock(new BlockPos(2, 0, 1), Blocks.FARMLAND);
		helper.setBlock(new BlockPos(2, 1, 1), Blocks.ATTACHED_MELON_STEM.defaultBlockState().setValue(AttachedStemBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(3, 1, 1), Blocks.MELON);
		breakBlock(helper, p, new BlockPos(3, 1, 1));
		near(helper, earned(p, Skill.FARMING), 10, "a melon from a stem");
		place(helper, p, new BlockPos(4, 1, 1), new ItemStack(Items.MELON));
		breakBlock(helper, p, new BlockPos(4, 1, 1));
		near(helper, earned(p, Skill.FORAGING) + earned(p, Skill.FARMING), 40, "after a placed melon");

		// Sugar cane: every block above the bottom one
		helper.setBlock(new BlockPos(6, 1, 1), Blocks.SUGAR_CANE);
		helper.setBlock(new BlockPos(6, 2, 1), Blocks.SUGAR_CANE);
		helper.setBlock(new BlockPos(6, 3, 1), Blocks.SUGAR_CANE);
		breakBlock(helper, p, new BlockPos(6, 2, 1));
		near(helper, earned(p, Skill.FARMING), 10 + 2 * 4, "two blocks of sugar cane above the bottom one");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void pickingBerries(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = holding(helper, "picker", ItemStack.EMPTY);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(1, 0, 1), Blocks.GRASS_BLOCK);
		helper.setBlock(new BlockPos(3, 0, 1), Blocks.GRASS_BLOCK);
		helper.setBlock(new BlockPos(5, 0, 1), Blocks.GRASS_BLOCK);
		BlockState ripe = Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3);
		// A wild bush, a bush a player planted, and a planted bush ripened with bonemeal
		helper.setBlock(new BlockPos(1, 1, 1), ripe);
		place(helper, p, new BlockPos(3, 1, 1), new ItemStack(Items.SWEET_BERRIES));
		helper.setBlock(new BlockPos(3, 1, 1), ripe);
		place(helper, p, new BlockPos(5, 1, 1), new ItemStack(Items.SWEET_BERRIES));
		BlockPos meal = helper.absolutePos(new BlockPos(5, 1, 1));
		for (int i = 0; i < 10 && level.getBlockState(meal).getValue(SweetBerryBushBlock.AGE) < 3; i++) {
			BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, meal);
		}
		for (BlockPos rel : new BlockPos[] {new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1)}) {
			BlockPos at = helper.absolutePos(rel);
			p.gameMode.useItemOn(p, level, p.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
		}
		helper.runAfterDelay(2, () -> {
			check(helper, level.getBlockState(meal).getValue(SweetBerryBushBlock.AGE) == 1, "the bonemealed bush was not picked");
			near(helper, earned(p, Skill.FORAGING), 15, "a wild bush");
			near(helper, earned(p, Skill.FARMING), 6, "a planted bush and a bonemealed one");
			check(helper, (marks(helper, new BlockPos(5, 1, 1)) & PlacedBlocks.BONEMEALED) == 0, "picking did not clear the bonemeal mark");
			helper.succeed();
		});
	}

	@GameTest
	public void brushingGivesMiningXp(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = holding(helper, "brusher", new ItemStack(Items.BRUSH));
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(1, 0, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.SUSPICIOUS_SAND);
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		BrushableBlockEntity be = (BrushableBlockEntity) level.getBlockEntity(at);
		check(helper, be != null, "suspicious sand has no block entity");
		long tick = level.getGameTime();
		for (int i = 0; i < 30 && level.getBlockState(at).is(Blocks.SUSPICIOUS_SAND); i++) {
			be.brush(tick + i * 20L, level, p, Direction.UP, p.getMainHandItem());
		}
		check(helper, !level.getBlockState(at).is(Blocks.SUSPICIOUS_SAND), "brushing did not finish");
		near(helper, earned(p, Skill.MINING), 250, "brushing suspicious sand");
		helper.succeed();
	}

	@GameTest
	public void catchesGiveFishingXpUntilTheSpotIsOverfished(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = holding(helper, "angler", new ItemStack(Items.FISHING_ROD));
		FishingHook hook = new FishingHook(p, helper.getLevel(), 0, 0);
		check(helper, hook.isOpenWaterFishing(), "a new hook does not count as open water");
		FishingXp.caught(p, hook, List.of(new ItemStack(Items.COD)));
		near(helper, earned(p, Skill.FISHING), 60, "a cod");
		FishingXp.caught(p, hook, List.of());
		near(helper, earned(p, Skill.FISHING), 60, "a hooked mob (no catch)");
		for (int i = 0; i < 9; i++) {
			FishingXp.caught(p, hook, List.of(new ItemStack(Items.COD)));
		}
		near(helper, earned(p, Skill.FISHING), 600, "ten cod in one spot");
		FishingXp.caught(p, hook, List.of(new ItemStack(Items.COD)));
		near(helper, earned(p, Skill.FISHING), 600 + 60 * 0.25, "the eleventh cod in one spot");
		hook.setPos(hook.getX() + 10, hook.getY(), hook.getZ());
		FishingXp.caught(p, hook, List.of(new ItemStack(Items.ENCHANTED_BOOK)));
		near(helper, earned(p, Skill.FISHING), 615 + 400, "an enchanted book in fresh water");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void animalsGiveFarmingXp(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = holding(helper, "herder", new ItemStack(Items.SHEARS));
		ServerLevel level = helper.getLevel();
		Animal a = helper.spawn(EntityTypes.COW, new BlockPos(1, 1, 1));
		Animal b = helper.spawn(EntityTypes.COW, new BlockPos(2, 1, 1));
		a.setInLove(p);
		b.setInLove(p);
		a.spawnChildFromBreeding(level, b);
		near(helper, earned(p, Skill.FARMING), 40, "breeding two cows");
		TamableAnimal wolf = helper.spawn(EntityTypes.WOLF, new BlockPos(4, 1, 4));
		wolf.tame(p);
		near(helper, earned(p, Skill.FARMING), 240, "taming a wolf");
		Sheep sheep = helper.spawn(EntityTypes.SHEEP, new BlockPos(6, 1, 6));
		UseEntityCallback.EVENT.invoker().interact(p, level, InteractionHand.MAIN_HAND, sheep, null);
		sheep.shear(level, SoundSource.PLAYERS, p.getMainHandItem());
		helper.runAfterDelay(2, () -> {
			near(helper, earned(p, Skill.FARMING), 255, "shearing a sheep");
			helper.succeed();
		});
	}
}
