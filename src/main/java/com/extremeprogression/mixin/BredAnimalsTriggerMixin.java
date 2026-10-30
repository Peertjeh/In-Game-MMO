package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.xp.AnimalXp;

import net.minecraft.advancements.triggers.BredAnimalsTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;

/** Vanilla reports every pair of animals a player bred to this advancement trigger: Farming XP per baby. */
@Mixin(BredAnimalsTrigger.class)
public abstract class BredAnimalsTriggerMixin {
	@Inject(method = "trigger", at = @At("HEAD"), require = 1)
	private void extremeprogression$bred(ServerPlayer player, Animal parent, Animal partner, AgeableMob child, CallbackInfo ci) {
		AnimalXp.bred(player);
	}
}
