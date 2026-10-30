package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/** An arrow's piercing, which only the crossbow's enchantment sets in vanilla. */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccess {
	@Invoker("setPierceLevel")
	void extremeprogression$setPierceLevel(byte level);
}
