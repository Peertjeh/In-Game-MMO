package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;

import com.extremeprogression.trunk.Trunks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Every furnace tick: the fuel as it was before, so a fuel the trunk saves can be put back. */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceTickMixin {
	@Inject(method = "serverTick", at = @At("HEAD"), require = 1)
	private static void extremeprogression$before(ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace, CallbackInfo ci,
			@Share("fuel") LocalRef<ItemStack> fuel, @Share("lit") LocalIntRef lit) {
		fuel.set(furnace.getItem(1).copy());
		lit.set(((FurnaceAccess) furnace).extremeprogression$getLitTimeRemaining());
	}

	@Inject(method = "serverTick", at = @At("TAIL"), require = 1)
	private static void extremeprogression$after(ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace, CallbackInfo ci,
			@Share("fuel") LocalRef<ItemStack> fuel, @Share("lit") LocalIntRef lit) {
		Trunks.furnaceTick(furnace, fuel.get(), lit.get());
	}
}
