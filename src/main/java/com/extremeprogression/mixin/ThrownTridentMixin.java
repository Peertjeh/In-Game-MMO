package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import com.extremeprogression.spec.Ranged;

import net.minecraft.world.entity.projectile.arrow.ThrownTrident;

/** Return (Harpooner): a trident without Loyalty may come back as with Loyalty I. */
@Mixin(ThrownTrident.class)
public abstract class ThrownTridentMixin {
	@ModifyReturnValue(method = "getLoyaltyFromItem", at = @At("RETURN"), require = 1)
	private byte extremeprogression$return(byte loyalty) {
		return Ranged.loyalty((ThrownTrident) (Object) this, loyalty);
	}
}
