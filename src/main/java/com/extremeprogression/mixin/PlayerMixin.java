package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.extremeprogression.trunk.Trunks;

import net.minecraft.world.entity.player.Player;

/** Stamina (Exploration 10): sprinting costs less hunger. */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true, require = 1)
	private float extremeprogression$stamina(float amount) {
		return Trunks.exhaustion((Player) (Object) this, amount);
	}
}
