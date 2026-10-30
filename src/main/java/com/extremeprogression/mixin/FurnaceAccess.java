package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/** A furnace's progress and fuel (Fire Master and Fuel Saver, Smithing 5 and 20). */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface FurnaceAccess {
	@Accessor("cookingTimer")
	int extremeprogression$getCookingTimer();

	@Accessor("cookingTimer")
	void extremeprogression$setCookingTimer(int ticks);

	@Accessor("cookingTotalTime")
	int extremeprogression$getCookingTotalTime();

	@Accessor("litTimeRemaining")
	int extremeprogression$getLitTimeRemaining();
}
