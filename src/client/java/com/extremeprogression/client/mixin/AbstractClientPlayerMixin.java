package com.extremeprogression.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import com.extremeprogression.spec.Ranged;

import net.minecraft.client.player.AbstractClientPlayer;

/** Deadeye (Sniper): the view zooms in while you draw, almost like a spyglass. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@ModifyReturnValue(method = "getFieldOfViewModifier", at = @At("RETURN"), require = 1)
	private float extremeprogression$deadeye(float modifier) {
		return Ranged.zoomed((AbstractClientPlayer) (Object) this) ? modifier * 0.3f : modifier;
	}
}
