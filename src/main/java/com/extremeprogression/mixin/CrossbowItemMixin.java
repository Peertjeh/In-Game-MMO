package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import com.extremeprogression.spec.Ranged;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/** Quick Reload (Skirmisher): a crossbow loads faster. */
@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin {
	@ModifyReturnValue(method = "getChargeDuration", at = @At("RETURN"), require = 1)
	private static int extremeprogression$quickReload(int ticks, ItemStack crossbow, LivingEntity user) {
		return Ranged.chargeDuration(user, ticks);
	}
}
