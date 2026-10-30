package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;

import com.extremeprogression.xp.CombatXp;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Every hit on the server, including the killing one (Fabric's damage event leaves that out): the health before and
 * after, what a shield blocked, and whether the target was already hostile to its attacker.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "hurtServer", at = @At("HEAD"), require = 1)
	private void extremeprogression$before(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir,
			@Share("health") LocalFloatRef health, @Share("hostile") LocalBooleanRef hostile) {
		LivingEntity self = (LivingEntity) (Object) this;
		health.set(self.getHealth() + self.getAbsorptionAmount());
		hostile.set(CombatXp.wasHostile(self, source));
	}

	@Inject(method = "hurtServer", at = @At("TAIL"), require = 1)
	private void extremeprogression$after(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir,
			@Local(name = "originalDamage") float originalDamage, @Local(name = "blocked") boolean blocked,
			@Share("health") LocalFloatRef health, @Share("hostile") LocalBooleanRef hostile) {
		CombatXp.hurt((LivingEntity) (Object) this, source, originalDamage, amount, blocked, health.get(), hostile.get());
	}
}
