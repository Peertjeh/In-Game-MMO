package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.synergy.SynergyEffects;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;

/** Venom Tip: while a tipped arrow gives its effects, its owner is known, so the effects can last longer. */
@Mixin(Arrow.class)
public abstract class ArrowMixin {
	@Inject(method = "doPostHurtEffects", at = @At("HEAD"), require = 1)
	private void extremeprogression$venomTipStart(LivingEntity target, CallbackInfo ci) {
		SynergyEffects.arrowOwner = ((Arrow) (Object) this).getOwner();
	}

	@Inject(method = "doPostHurtEffects", at = @At("RETURN"), require = 1)
	private void extremeprogression$venomTipEnd(LivingEntity target, CallbackInfo ci) {
		SynergyEffects.arrowOwner = null;
	}
}
