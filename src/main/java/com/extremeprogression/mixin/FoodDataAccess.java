package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.food.FoodData;

/** The timer of natural regeneration (Catch Breath, Defense 15). */
@Mixin(FoodData.class)
public interface FoodDataAccess {
	@Accessor("tickTimer")
	int extremeprogression$getTickTimer();

	@Accessor("tickTimer")
	void extremeprogression$setTickTimer(int ticks);
}
