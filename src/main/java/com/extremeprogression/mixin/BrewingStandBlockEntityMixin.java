package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;

import com.extremeprogression.spec.Arcana;
import com.extremeprogression.trunk.Trunks;
import com.extremeprogression.xp.CraftingXp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A brewing stand finished brewing: the bottles in it are its own work, marked with their user's duration bonuses (and
 * the Alchemist's Distiller may keep the ingredient). Its ticks run at the speed of its user's trunk and Alchemist
 * nodes, and the Alchemist's own potions only brew for an Alchemist.
 */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {
	@Inject(method = "doBrew", at = @At("HEAD"), require = 1)
	private static void extremeprogression$brewing(ServerLevel level, BlockPos pos, BrewingStandBlockEntity stand, CallbackInfo ci,
			@Share("ingredient") LocalRef<ItemStack> ingredient) {
		ingredient.set(stand.getItem(3).copy());
	}

	@Inject(method = "doBrew", at = @At("TAIL"), require = 1)
	private static void extremeprogression$brewed(ServerLevel level, BlockPos pos, BrewingStandBlockEntity stand, CallbackInfo ci,
			@Share("ingredient") LocalRef<ItemStack> ingredient) {
		CraftingXp.brewed(stand, true);
		Arcana.brewed(stand);
		ItemStack before = ingredient.get();
		if (!before.isEmpty() && stand.getItem(3).getCount() < before.getCount() && Arcana.distilled(stand)) {
			stand.setItem(3, before);
		}
	}

	@Inject(method = "serverTick", at = @At("TAIL"), require = 1)
	private static void extremeprogression$tick(ServerLevel level, BlockPos pos, BlockState state, BrewingStandBlockEntity stand, CallbackInfo ci) {
		Trunks.brewingTick(stand);
	}

	@ModifyReturnValue(method = "isBrewable", at = @At("RETURN"), require = 1)
	private static boolean extremeprogression$herbal(boolean brewable, ServerLevel level, BrewingStandBlockEntity stand) {
		return brewable && Arcana.mayBrew(stand);
	}
}
