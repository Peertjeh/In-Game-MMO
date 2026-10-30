package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.extremeprogression.spec.Ranged;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

/** Scavenger (Skirmisher): a shot that costs no arrow, as Multishot's extra arrows cost none. */
@Mixin(ProjectileWeaponItem.class)
public abstract class ProjectileWeaponItemMixin {
	@ModifyVariable(method = "useAmmo", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 1)
	private static boolean extremeprogression$scavenger(boolean free, ItemStack weapon, ItemStack ammo, LivingEntity shooter) {
		return Ranged.freeShot(shooter, free);
	}
}
