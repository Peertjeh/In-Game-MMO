package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;

import com.extremeprogression.spec.Defense;
import com.extremeprogression.spec.Exploration;
import com.extremeprogression.spec.Melee;
import com.extremeprogression.spec.Specs;
import com.extremeprogression.trunk.Trunks;
import com.extremeprogression.xp.CombatXp;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
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

	/** The trunks and specializations of attacker and target change a hit's damage before armor. */
	@ModifyVariable(method = "hurtServer", at = @At(value = "LOAD", ordinal = 0), argsOnly = true, require = 1)
	private float extremeprogression$trunks(float amount, ServerLevel level, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		// (damage an effect deals itself gets no bonuses)
		return Specs.secondary() ? amount : Specs.damage(self, source, Trunks.damage(self, source, amount));
	}

	/** Sure Footing (Melee 15), and the specializations' knockback. */
	@ModifyVariable(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 1)
	private double extremeprogression$knockback(double strength) {
		LivingEntity self = (LivingEntity) (Object) this;
		return Specs.knockback(self, Trunks.knockback(self, strength));
	}

	/** Bastion (Bulwark): a raised shield blocks from every direction. */
	@ModifyArg(method = "applyItemBlocking", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/component/BlocksAttacks;resolveBlockedDamage(Lnet/minecraft/world/damagesource/DamageSource;FD)F"),
			index = 2, require = 1)
	private double extremeprogression$bastion(double angle) {
		return Defense.blockAngle((LivingEntity) (Object) this, angle);
	}

	/** Cleave (Crusher): an axe disables a shield longer. */
	@ModifyReturnValue(method = "getSecondsToDisableBlocking", at = @At("RETURN"), require = 1)
	private float extremeprogression$cleave(float seconds) {
		return Melee.disableSeconds((LivingEntity) (Object) this, seconds);
	}

	@Unique
	private boolean extremeprogression$changingEffect;

	/** Resilience: negative effects shorter, poison shorter, or shaken off. */
	@Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true,
			require = 1)
	private void extremeprogression$effect(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (extremeprogression$changingEffect || self.level().isClientSide()) {
			return;
		}
		MobEffectInstance changed = Specs.effect(self, effect);
		if (changed == null) {
			cir.setReturnValue(false);
		} else if (changed != effect) {
			extremeprogression$changingEffect = true;
			try {
				cir.setReturnValue(self.addEffect(changed, source));
			} finally {
				extremeprogression$changingEffect = false;
			}
		}
	}

	/** Steady Heart (Resilience). */
	@ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true, require = 1)
	private float extremeprogression$heal(float amount) {
		LivingEntity self = (LivingEntity) (Object) this;
		return self.level().isClientSide() ? amount : Specs.heal(self, amount);
	}

	/** Climber (Acrobat): faster up ladders and vines. */
	@ModifyReturnValue(method = "handleRelativeFrictionAndCalculateMovement", at = @At("RETURN"), require = 1)
	private Vec3 extremeprogression$climb(Vec3 movement) {
		return Exploration.climb((LivingEntity) (Object) this, movement);
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
		LivingEntity self = (LivingEntity) (Object) this;
		if (Specs.secondary()) {
			return;
		}
		CombatXp.hurt(self, source, originalDamage, amount, blocked, health.get(), hostile.get());
		Specs.after(self, source, originalDamage, amount, blocked, Math.max(0, health.get() - (self.getHealth() + self.getAbsorptionAmount())));
	}
}
