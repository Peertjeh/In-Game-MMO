package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremeprogression.antiexploit.PlacedBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** Every block change in a loaded chunk: a replaced block loses its marks, a piston or generator makes new ones. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
	@Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;",
			at = @At("RETURN"), require = 1)
	private void extremeprogression$changed(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
		BlockState old = cir.getReturnValue();
		if (old != null) {
			PlacedBlocks.changed((LevelChunk) (Object) this, pos, old, state);
		}
	}
}
