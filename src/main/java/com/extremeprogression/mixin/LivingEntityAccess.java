package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.LivingEntity;

/** The damage of the last hit, which vanilla's invulnerability frames compare against. */
@Mixin(LivingEntity.class)
public interface LivingEntityAccess {
	@Accessor("lastHurt")
	float extremeprogression$getLastHurt();

	@Accessor("lastHurt")
	void extremeprogression$setLastHurt(float value);
}
