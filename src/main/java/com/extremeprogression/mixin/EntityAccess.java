package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.Entity;

/** An entity's shared flags: on the client the glowing outline follows flag 6, which only the server sets otherwise. */
@Mixin(Entity.class)
public interface EntityAccess {
	@Invoker("setSharedFlag")
	void extremeprogression$setSharedFlag(int flag, boolean value);
}
