package com.extremeprogression.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import com.extremeprogression.spec.Ranged;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;

/** Running Shot (Skirmisher): drawing a bow or crossbow slows you less. Movement is worked out here, on the client. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@ModifyReturnValue(method = "modifyInput", at = @At("RETURN"), require = 1)
	private Vec2 extremeprogression$runningShot(Vec2 input) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (!self.isUsingItem() || self.isPassenger()) {
			return input;
		}
		float factor = Ranged.runningShot(self);
		return factor == 1f ? input : input.scale(factor);
	}
}
