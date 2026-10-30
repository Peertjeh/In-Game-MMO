package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.spec.Foraging;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;

/** Honey Harvest (Gatherer): the bees of a wild hive stay calm while you take its honey. */
@Mixin(BeehiveBlock.class)
public abstract class BeehiveBlockMixin {
	@Inject(method = "angerNearbyBees", at = @At("HEAD"), cancellable = true, require = 1)
	private void extremeprogression$calm(Level level, BlockPos pos, CallbackInfo ci) {
		if (Foraging.calm(level, pos)) {
			ci.cancel();
		}
	}
}
