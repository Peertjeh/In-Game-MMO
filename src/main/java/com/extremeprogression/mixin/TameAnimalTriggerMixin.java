package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.xp.AnimalXp;

import net.minecraft.advancements.triggers.TameAnimalTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Animal;

/** Vanilla reports every animal a player tamed to this advancement trigger, once per animal: Farming XP. */
@Mixin(TameAnimalTrigger.class)
public abstract class TameAnimalTriggerMixin {
	@Inject(method = "trigger", at = @At("HEAD"), require = 1)
	private void extremeprogression$tamed(ServerPlayer player, Animal animal, CallbackInfo ci) {
		AnimalXp.tamed(player, animal);
	}
}
