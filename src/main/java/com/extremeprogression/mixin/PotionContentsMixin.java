package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.trunk.PotentPotions;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;

/** Potent Brews (Arcana 15) and Extended (Alchemist): a marked potion lasts longer when drunk; a Hexer's own drink shorter. */
@Mixin(PotionContents.class)
public abstract class PotionContentsMixin {
	@Inject(method = "onConsume", at = @At("HEAD"), require = 1)
	private void extremeprogression$drinking(Level level, LivingEntity drinker, ItemStack stack, Consumable consumable, CallbackInfo ci) {
		if (!level.isClientSide()) {
			PotentPotions.drinking(stack, drinker);
		}
	}

	@Inject(method = "onConsume", at = @At("RETURN"), require = 1)
	private void extremeprogression$drunk(Level level, LivingEntity drinker, ItemStack stack, Consumable consumable, CallbackInfo ci) {
		if (!level.isClientSide()) {
			PotentPotions.drinking(ItemStack.EMPTY, drinker);
		}
	}

	@ModifyVariable(method = "forEachEffect", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 1)
	private float extremeprogression$longer(float durationScale) {
		return durationScale * PotentPotions.scale();
	}
}
