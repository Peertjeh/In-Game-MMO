package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.antiexploit.PlacedBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Mushrooms that spread from a placed one, and vines grown from planted glow berries, are not wild (design §2). */
@Mixin({MushroomBlock.class, GrowingPlantHeadBlock.class})
public abstract class SpreadingPlantMixin {
	@Inject(method = "randomTick", at = @At("HEAD"), require = 1)
	private void extremeprogression$begin(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		PlacedBlocks.beginGrowth(level, pos);
	}

	@Inject(method = "randomTick", at = @At("RETURN"), require = 1)
	private void extremeprogression$end(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		PlacedBlocks.endGrowth(level, pos);
	}
}
