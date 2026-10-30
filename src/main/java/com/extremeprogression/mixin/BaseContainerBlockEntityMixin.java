package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.xp.CraftingXp;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/** A bottle put into a brewing stand (by a player or a hopper) was not brewed there. */
@Mixin(BaseContainerBlockEntity.class)
public abstract class BaseContainerBlockEntityMixin {
	@Inject(method = "setItem", at = @At("HEAD"), require = 1)
	private void extremeprogression$placed(int slot, ItemStack stack, CallbackInfo ci) {
		if ((Object) this instanceof BrewingStandBlockEntity stand) {
			CraftingXp.placed(stand, slot, stack);
		}
	}
}
