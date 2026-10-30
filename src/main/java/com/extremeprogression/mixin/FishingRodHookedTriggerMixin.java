package com.extremeprogression.mixin;

import java.util.Collection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.xp.FishingXp;

import net.minecraft.advancements.triggers.FishingRodHookedTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;

/** Vanilla reports every reeled-in catch to this advancement trigger, with the items: Fishing XP. */
@Mixin(FishingRodHookedTrigger.class)
public abstract class FishingRodHookedTriggerMixin {
	@Inject(method = "trigger", at = @At("HEAD"), require = 1)
	private void extremeprogression$caught(ServerPlayer player, ItemStack rod, FishingHook hook, Collection<ItemStack> items, CallbackInfo ci) {
		// The specializations may change the catch first (the hook throws these same items to the player)
		com.extremeprogression.spec.Fishing.caught(player, hook, rod, items);
		FishingXp.caught(player, hook, items);
	}
}
