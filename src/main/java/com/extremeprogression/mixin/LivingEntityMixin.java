package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;

import com.extremeprogression.trunk.Trunks;
import com.extremeprogression.xp.CombatXp;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;

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

	/** The trunks of attacker and target change a hit's damage before armor. */
	@ModifyVariable(method = "hurtServer", at = @At(value = "LOAD", ordinal = 0), argsOnly = true, require = 1)
	private float extremeprogression$trunks(float amount, ServerLevel level, DamageSource source) {
		return Trunks.damage((LivingEntity) (Object) this, source, amount);
	}

	/** Sure Footing (Melee 15). */
	@ModifyVariable(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 1)
	private double extremeprogression$knockback(double strength) {
		return Trunks.knockback((LivingEntity) (Object) this, strength);
	}

	/** Shield Reflex (Defense 5): a raised shield blocks one tick sooner. */
	@Inject(method = "getItemBlockingWith", at = @At("RETURN"), cancellable = true, require = 1)
	private void extremeprogression$shield(CallbackInfoReturnable<ItemStack> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (cir.getReturnValue() == null && self.isUsingItem()) {
			ItemStack use = self.getUseItem();
			BlocksAttacks blocks = use.get(DataComponents.BLOCKS_ATTACKS);
			if (blocks != null && Trunks.shieldReady(self, self.getTicksUsingItem(), blocks.blockDelayTicks())) {
				cir.setReturnValue(use);
			}
		}
	}

	@Inject(method = "hurtServer", at = @At("TAIL"), require = 1)
	private void extremeprogression$after(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir,
			@Local(name = "originalDamage") float originalDamage, @Local(name = "blocked") boolean blocked,
			@Share("health") LocalFloatRef health, @Share("hostile") LocalBooleanRef hostile) {
		CombatXp.hurt((LivingEntity) (Object) this, source, originalDamage, amount, blocked, health.get(), hostile.get());
	}
}
