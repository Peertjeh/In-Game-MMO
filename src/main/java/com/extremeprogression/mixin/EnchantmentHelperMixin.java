package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import com.extremeprogression.trunk.Trunks;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Patience (Fishing 5) and Sea Luck (Fishing 20), as vanilla's Lure and Luck of the Sea are counted; Volley (Skirmisher) as Multishot. */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {
	@ModifyReturnValue(method = "getFishingTimeReduction", at = @At("RETURN"), require = 1)
	private static float extremeprogression$patience(float seconds, ServerLevel level, ItemStack rod, Entity angler) {
		return Trunks.fishingTime(angler, seconds);
	}

	@ModifyReturnValue(method = "processProjectileCount", at = @At("RETURN"), require = 1)
	private static int extremeprogression$volley(int count, ServerLevel level, ItemStack weapon, Entity shooter) {
		return com.extremeprogression.spec.Ranged.projectiles(shooter, count);
	}

	@ModifyReturnValue(method = "processProjectileSpread", at = @At("RETURN"), require = 1)
	private static float extremeprogression$volleySpread(float spread, ServerLevel level, ItemStack weapon, Entity shooter) {
		return com.extremeprogression.spec.Ranged.spread(shooter, spread);
	}

	@ModifyReturnValue(method = "getFishingLuckBonus", at = @At("RETURN"), require = 1)
	private static int extremeprogression$seaLuck(int luck, ServerLevel level, ItemStack rod, Entity angler) {
		return Trunks.fishingLuck(angler, luck);
	}
}
