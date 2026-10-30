package com.extremeprogression.xp;

import static com.extremeprogression.antiexploit.PlacedBlocks.BONEMEALED;
import static com.extremeprogression.antiexploit.PlacedBlocks.PLACED;
import static com.extremeprogression.antiexploit.PlacedBlocks.PLANTED;

import com.extremeprogression.antiexploit.AfkTracker;
import com.extremeprogression.antiexploit.PlacedBlocks;
import com.extremeprogression.config.Config;
import com.extremeprogression.skill.Skill;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Mining, Foraging and Farming XP for blocks a player breaks by hand (design §2, Appendix A). Explosions, pistons,
 * water and villagers break blocks without a player, so they never give XP.
 */
public final class GatheringXp {
	private GatheringXp() {}

	/** What the block was like just before it broke: its marks go with it, and a fruit's stem lets go of it. */
	private record Before(Level level, BlockPos pos, int flags, int lowerFlags, boolean fromStem) {}

	private static Before before = null;

	public static void register() {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (!level.isClientSide()) {
				AfkTracker.interacted(player.getUUID(), level.getServer().getTickCount());
				BlockPos lower = isUpperHalf(state) ? pos.below() : pos;
				before = new Before(level, pos.immutable(), PlacedBlocks.flags(level, pos), PlacedBlocks.flags(level, lower), fromStem(level, pos, state));
			}
			return true;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			Before b = before;
			before = null;
			// (a block broken without the check before it: its marks are gone, so it gives nothing)
			if (player instanceof ServerPlayer p && b != null && b.level == level && b.pos.equals(pos)) {
				broken(p, level, pos, state, b);
			}
		});
	}

	private static boolean isUpperHalf(BlockState state) {
		return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
	}

	/** A melon or pumpkin that a stem grew: the stem next to it points at it. */
	private static boolean fromStem(Level level, BlockPos pos, BlockState state) {
		if (!state.is(Blocks.MELON) && !state.is(Blocks.PUMPKIN)) {
			return false;
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockState n = level.getBlockState(pos.relative(d));
			if (n.getBlock() instanceof AttachedStemBlock && n.getValue(AttachedStemBlock.FACING) == d.getOpposite()) {
				return true;
			}
		}
		return false;
	}

	private static void broken(ServerPlayer player, Level level, BlockPos pos, BlockState state, Before b) {
		Block block = state.getBlock();
		int flags = b.flags;

		// Mining: with a pickaxe that can mine it, or a shovel
		double pick = XpTables.pickaxe(state);
		if (pick > 0) {
			if ((flags & PLACED) == 0 && player.getMainHandItem().is(ItemTags.PICKAXES) && player.hasCorrectToolForDrops(state)) {
				XpService.award(player, Skill.MINING, pick);
			}
			return;
		}
		double shovel = XpTables.shovel(state);
		if (shovel > 0) {
			if ((flags & PLACED) == 0 && player.getMainHandItem().is(ItemTags.SHOVELS)) {
				XpService.award(player, Skill.MINING, shovel);
			}
			return;
		}

		// Foraging: logs of natural trees; planted trees give a share, bonemeal-grown ones nothing
		double log = XpTables.log(state);
		if (log > 0) {
			if ((flags & PLACED) == 0) {
				double share = (flags & BONEMEALED) != 0 ? Config.BONEMEAL_FACTOR : (flags & PLANTED) != 0 ? Config.PLANTED_TREE_FACTOR : 1.0;
				XpService.award(player, Skill.FORAGING, log * share);
			}
			return;
		}

		// Farming: fully grown crops
		if (block instanceof CropBlock crop) {
			if (crop.isMaxAge(state)) {
				farming(player, XpTables.crop(state), flags);
			}
			return;
		}
		if (block instanceof NetherWartBlock) {
			if (state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE) {
				farming(player, XpTables.NETHER_WART, flags);
			}
			return;
		}
		if (block instanceof PitcherCropBlock) {
			if (state.getValue(PitcherCropBlock.AGE) >= PitcherCropBlock.MAX_AGE) {
				farming(player, XpTables.PITCHER, b.lowerFlags);
			}
			return;
		}
		if (state.is(Blocks.TORCHFLOWER)) {
			// (a torchflower only grows from a seed; one that was placed as a flower gives nothing)
			if ((flags & PLACED) == 0) {
				farming(player, XpTables.TORCHFLOWER, flags);
			}
			return;
		}
		if (state.is(Blocks.SUGAR_CANE) || state.is(Blocks.CACTUS)) {
			column(player, level, pos, state, flags);
			return;
		}

		// Wild or planted: Foraging in the wild, Farming when a player planted it
		if (block instanceof CocoaBlock) {
			if (state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE) {
				wildOrPlanted(player, flags, XpTables.WILD_COCOA, XpTables.PLANTED_COCOA);
			}
			return;
		}
		if (block instanceof SweetBerryBushBlock) {
			if (state.getValue(SweetBerryBushBlock.AGE) > 1) {
				wildOrPlanted(player, flags, XpTables.WILD_SWEET_BERRIES, XpTables.PLANTED_SWEET_BERRIES);
			}
			return;
		}
		if (block instanceof CaveVines) {
			if (CaveVines.hasGlowBerries(state)) {
				wildOrPlanted(player, flags, XpTables.GLOW_BERRIES, 0);
			}
			return;
		}
		if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) {
			if ((flags & PLACED) != 0) {
				return;
			}
			if (b.fromStem) {
				farming(player, XpTables.STEM_FRUIT, flags);
			} else {
				wild(player, flags, XpTables.WILD_MELON);
			}
			return;
		}
		if (XpTables.mushroom(state)) {
			wild(player, flags, XpTables.MUSHROOM);
			return;
		}
		double flower = XpTables.flower(state);
		if (flower > 0) {
			wild(player, isUpperHalf(state) ? b.lowerFlags : flags, flower);
		}
	}

	/** A crop: always planted, so only bonemeal counts against it. */
	private static void farming(ServerPlayer player, double xp, int flags) {
		XpService.award(player, Skill.FARMING, (flags & BONEMEALED) != 0 ? xp * Config.BONEMEAL_FACTOR : xp);
	}

	/** Something that only gives XP where it grew by itself: never placed, never from something planted. */
	static void wild(ServerPlayer player, int flags, double xp) {
		if ((flags & (PLACED | PLANTED)) != 0) {
			return;
		}
		XpService.award(player, Skill.FORAGING, (flags & BONEMEALED) != 0 ? xp * Config.BONEMEAL_FACTOR : xp);
	}

	/** Foraging XP where it grew wild, Farming XP where a player planted it (cocoa, berry bushes). */
	static void wildOrPlanted(ServerPlayer player, int flags, double wildXp, double plantedXp) {
		if ((flags & (PLACED | PLANTED)) != 0) {
			farming(player, plantedXp, flags);
		} else {
			wild(player, flags, wildXp);
		}
	}

	/** Sugar cane and cactus: every block above the bottom one that grew by itself, up to the top. */
	private static void column(ServerPlayer player, Level level, BlockPos pos, BlockState state, int flags) {
		Block block = state.getBlock();
		double grown = 0;
		if (level.getBlockState(pos.below()).is(block)) {
			grown += share(flags);
		}
		for (int up = 1; up < 32; up++) {
			BlockPos at = pos.above(up);
			if (!level.getBlockState(at).is(block)) {
				break;
			}
			grown += share(PlacedBlocks.flags(level, at));
		}
		XpService.award(player, Skill.FARMING, grown * (state.is(Blocks.SUGAR_CANE) ? XpTables.SUGAR_CANE : XpTables.CACTUS));
	}

	private static double share(int flags) {
		return (flags & PLACED) != 0 ? 0 : (flags & BONEMEALED) != 0 ? Config.BONEMEAL_FACTOR : 1.0;
	}

	/** A player brushed a suspicious block until it gave up its find (archaeology). */
	public static void brushed(Player player) {
		if (player instanceof ServerPlayer p) {
			XpService.award(p, Skill.MINING, XpTables.BRUSHING);
		}
	}
}
