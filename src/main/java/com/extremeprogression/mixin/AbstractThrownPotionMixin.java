package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.spec.Arcana;

import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.phys.HitResult;

/** A thrown potion's splash or cloud carries its brewer's durations and its thrower's Hexer nodes (and Hex). */
@Mixin(AbstractThrownPotion.class)
public abstract class AbstractThrownPotionMixin {
	@Inject(method = "onHit", at = @At("HEAD"), require = 1)
	private void extremeprogression$hitting(HitResult hit, CallbackInfo ci) {
		AbstractThrownPotion self = (AbstractThrownPotion) (Object) this;
		if (!self.level().isClientSide()) {
			Arcana.hitting(self);
		}
	}

	@Inject(method = "onHit", at = @At("RETURN"), require = 1)
	private void extremeprogression$hit(HitResult hit, CallbackInfo ci) {
		AbstractThrownPotion self = (AbstractThrownPotion) (Object) this;
		if (!self.level().isClientSide()) {
			Arcana.hit(self);
		}
	}
}
