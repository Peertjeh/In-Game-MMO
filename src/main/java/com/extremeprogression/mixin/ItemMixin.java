package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;

import com.extremeprogression.trunk.Trunks;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Farmhouse Fare (Farming 20) and Fisher's Fare (Fishing 15): what was eaten, once it is eaten. */
@Mixin(Item.class)
public abstract class ItemMixin {
	@Inject(method = "finishUsingItem", at = @At("HEAD"), require = 1)
	private void extremeprogression$eating(ItemStack stack, Level level, LivingEntity eater, CallbackInfoReturnable<ItemStack> cir,
			@Share("food") LocalRef<ItemStack> food) {
		food.set(stack.copyWithCount(1));
	}

	@Inject(method = "finishUsingItem", at = @At("RETURN"), require = 1)
	private void extremeprogression$ate(ItemStack stack, Level level, LivingEntity eater, CallbackInfoReturnable<ItemStack> cir,
			@Share("food") LocalRef<ItemStack> food) {
		if (!level.isClientSide()) {
			Trunks.ate(eater, food.get());
		}
	}
}
