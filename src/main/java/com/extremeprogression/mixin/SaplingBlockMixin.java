package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.synergy.SynergyEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Green Thumb: after a sapling's own random tick, a chance of more growth for one its planter planted. */
@Mixin(SaplingBlock.class)
public abstract class SaplingBlockMixin {
	@Inject(method = "randomTick", at = @At("TAIL"), require = 1)
	private void extremeprogression$greenThumb(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		SaplingBlock self = (SaplingBlock) (Object) this;
		SynergyEffects.saplingTicked(level, pos, () -> {
			BlockState now = level.getBlockState(pos);
			if (now.getBlock() == self) {
				self.advanceTree(level, pos, now, random);
			}
		});
	}
}
