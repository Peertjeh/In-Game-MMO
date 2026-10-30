package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.trunk.PotentPotions;
import com.extremeprogression.trunk.Trunks;
import com.extremeprogression.xp.CraftingXp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A brewing stand finished brewing: the bottles in it are its own work. Its ticks run at the speed of its user's trunk. */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {
	@Inject(method = "doBrew", at = @At("TAIL"), require = 1)
	private static void extremeprogression$brewed(ServerLevel level, BlockPos pos, BrewingStandBlockEntity stand, CallbackInfo ci) {
		CraftingXp.brewed(stand, true);
		if (Trunks.potentBrews(stand)) {
			for (int slot = 0; slot < 3; slot++) {
				PotentPotions.mark(stand.getItem(slot));
			}
		}
	}

	@Inject(method = "serverTick", at = @At("TAIL"), require = 1)
	private static void extremeprogression$tick(ServerLevel level, BlockPos pos, BlockState state, BrewingStandBlockEntity stand, CallbackInfo ci) {
		Trunks.brewingTick(stand);
	}
}
