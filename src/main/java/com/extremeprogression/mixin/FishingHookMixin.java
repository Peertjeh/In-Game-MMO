package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.spec.Fishing;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;

/** Drag Hook (Hookmaster): reeling in a hooked mob or player pulls harder. */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
	@Inject(method = "pullEntity", at = @At("TAIL"), require = 1)
	private void extremeprogression$drag(Entity target, CallbackInfo ci) {
		Fishing.pulled((FishingHook) (Object) this, target);
	}
}
