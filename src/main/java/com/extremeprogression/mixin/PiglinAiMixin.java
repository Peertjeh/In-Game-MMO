package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremeprogression.spec.Trading;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;

/** Trading (1.5.0) with piglins: who gave the gold, the barter itself, and Golden Calm. */
@Mixin(PiglinAi.class)
public abstract class PiglinAiMixin {
	@Inject(method = "pickUpItem", at = @At("HEAD"), require = 1)
	private static void extremeprogression$pickUp(ServerLevel level, Piglin piglin, ItemEntity item, CallbackInfo ci) {
		Trading.piglinPicksUp(piglin, item);
	}

	@Inject(method = "mobInteract", at = @At("HEAD"), require = 1)
	private static void extremeprogression$given(ServerLevel level, Piglin piglin, Player player, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		Trading.piglinGiven(piglin, player, hand);
	}

	@Inject(method = "stopHoldingOffHandItem", at = @At("HEAD"), require = 1)
	private static void extremeprogression$barterStart(ServerLevel level, Piglin piglin, boolean barter, CallbackInfo ci) {
		Trading.barterStart(piglin, barter);
	}

	@Inject(method = "stopHoldingOffHandItem", at = @At("TAIL"), require = 1)
	private static void extremeprogression$barterEnd(ServerLevel level, Piglin piglin, boolean barter, CallbackInfo ci) {
		Trading.barterEnd(level, piglin);
	}

	@Inject(method = "isWearingSafeArmor", at = @At("HEAD"), cancellable = true, require = 1)
	private static void extremeprogression$calm(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		if (Trading.calm(entity)) {
			cir.setReturnValue(true);
		}
	}
}
