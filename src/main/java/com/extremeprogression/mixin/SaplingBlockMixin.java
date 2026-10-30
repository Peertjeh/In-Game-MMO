package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.antiexploit.PlacedBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A tree that grows from a sapling a player planted is a planted tree: its logs give a share of the XP (design §2). */
@Mixin(SaplingBlock.class)
public abstract class SaplingBlockMixin {
	@Inject(method = "advanceTree", at = @At("HEAD"), require = 1)
	private void extremeprogression$begin(ServerLevel level, BlockPos pos, BlockState state, RandomSource random, CallbackInfo ci) {
		PlacedBlocks.beginGrowth(level, pos);
	}

	@Inject(method = "advanceTree", at = @At("RETURN"), require = 1)
	private void extremeprogression$end(ServerLevel level, BlockPos pos, BlockState state, RandomSource random, CallbackInfo ci) {
		PlacedBlocks.endGrowth(level, pos);
	}
}
