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

/** Potent Brews (Arcana 15): a potion its brewer marked lasts 5% longer when it is drunk. */
@Mixin(PotionContents.class)
public abstract class PotionContentsMixin {
	@Inject(method = "onConsume", at = @At("HEAD"), require = 1)
	private void extremeprogression$drinking(Level level, LivingEntity drinker, ItemStack stack, Consumable consumable, CallbackInfo ci) {
		PotentPotions.drinking(stack);
	}

	@Inject(method = "onConsume", at = @At("RETURN"), require = 1)
	private void extremeprogression$drunk(Level level, LivingEntity drinker, ItemStack stack, Consumable consumable, CallbackInfo ci) {
		PotentPotions.drinking(ItemStack.EMPTY);
	}

	@ModifyVariable(method = "forEachEffect", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 1)
	private float extremeprogression$longer(float durationScale) {
		return durationScale * PotentPotions.scale();
	}
}
