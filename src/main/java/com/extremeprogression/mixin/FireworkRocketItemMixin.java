package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremeprogression.synergy.SynergyEffects;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.level.Level;

/** Tailwind (1.7.0): a firework rocket that boosts an elytra flight may come back to the hand. */
@Mixin(FireworkRocketItem.class)
public abstract class FireworkRocketItemMixin {
	@Inject(method = "use", at = @At("HEAD"), require = 1)
	private void extremeprogression$before(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (!level.isClientSide()) {
			SynergyEffects.fireworkBefore(player, hand);
		}
	}

	@Inject(method = "use", at = @At("RETURN"), require = 1)
	private void extremeprogression$after(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (!level.isClientSide()) {
			SynergyEffects.fireworkAfter(player, hand);
		}
	}
}
