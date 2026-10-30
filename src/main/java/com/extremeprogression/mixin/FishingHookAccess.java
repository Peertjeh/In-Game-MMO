package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.projectile.FishingHook;

/** The ticks until a fish comes for the bobber: Feeding Frenzy and the other bite speeds count it down faster or slower. */
@Mixin(FishingHook.class)
public interface FishingHookAccess {
	@Accessor("timeUntilLured")
	int extremeprogression$getTimeUntilLured();

	@Accessor("timeUntilLured")
	void extremeprogression$setTimeUntilLured(int ticks);
}
