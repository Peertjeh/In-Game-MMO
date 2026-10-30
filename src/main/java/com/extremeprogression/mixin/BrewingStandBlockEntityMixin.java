package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.xp.CraftingXp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/** A brewing stand finished brewing: the bottles in it are its own work. */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {
	@Inject(method = "doBrew", at = @At("TAIL"), require = 1)
	private static void extremeprogression$brewed(ServerLevel level, BlockPos pos, BrewingStandBlockEntity stand, CallbackInfo ci) {
		CraftingXp.brewed(stand, true);
	}
}
