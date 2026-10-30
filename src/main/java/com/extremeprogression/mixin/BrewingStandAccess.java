package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/** A brewing stand's remaining brew time (Busy Stand, Arcana 5). */
@Mixin(BrewingStandBlockEntity.class)
public interface BrewingStandAccess {
	@Accessor("brewTime")
	int extremeprogression$getBrewTime();

	@Accessor("brewTime")
	void extremeprogression$setBrewTime(int ticks);
}
