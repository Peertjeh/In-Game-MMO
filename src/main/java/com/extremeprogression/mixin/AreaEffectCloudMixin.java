package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.spec.Arcana;

import net.minecraft.world.entity.AreaEffectCloud;

/** A Hexer's potion cloud: while it ticks (and gives its effects), its thrower's Hexer nodes count. */
@Mixin(AreaEffectCloud.class)
public abstract class AreaEffectCloudMixin {
	@Inject(method = "tick", at = @At("HEAD"), require = 1)
	private void extremeprogression$ticking(CallbackInfo ci) {
		AreaEffectCloud self = (AreaEffectCloud) (Object) this;
		if (!self.level().isClientSide()) {
			Arcana.cloudTicking(self, true);
		}
	}

	@Inject(method = "tick", at = @At("RETURN"), require = 1)
	private void extremeprogression$ticked(CallbackInfo ci) {
		AreaEffectCloud self = (AreaEffectCloud) (Object) this;
		if (!self.level().isClientSide()) {
			Arcana.cloudTicking(self, false);
		}
	}
}
