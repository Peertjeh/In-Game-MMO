package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.synergy.SynergyEffects;

import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

/**
 * Seafarer: a boat steered by a player who has it pulls 10% harder forward. A steered boat moves where its driver's
 * game works it out, so this counts on their client.
 */
@Mixin(AbstractBoat.class)
public abstract class AbstractBoatMixin {
	@Shadow
	private boolean inputUp;

	@Inject(method = "controlBoat", at = @At("TAIL"), require = 1)
	private void extremeprogression$seafarer(CallbackInfo ci) {
		if (this.inputUp) {
			SynergyEffects.steered((AbstractBoat) (Object) this);
		}
	}
}
