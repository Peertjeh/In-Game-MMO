package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;

/**
 * The ticks until a fish comes for the bobber: Feeding Frenzy and the other bite speeds count it down faster or slower.
 * (Hooking an entity directly is for tests.)
 */
@Mixin(FishingHook.class)
public interface FishingHookAccess {
	@Accessor("timeUntilLured")
	int extremeprogression$getTimeUntilLured();

	@Accessor("timeUntilLured")
	void extremeprogression$setTimeUntilLured(int ticks);

	@Invoker("setHookedEntity")
	void extremeprogression$setHookedEntity(Entity entity);
}
