package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.xp.CraftingXp;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/** A player took a bottle out of a brewing stand, by hand or shift-click (a hopper never comes through here). */
@Mixin(targets = "net.minecraft.world.inventory.BrewingStandMenu$PotionSlot")
public abstract class PotionSlotMixin {
	@Inject(method = "onTake", at = @At("HEAD"), require = 1)
	private void extremeprogression$taken(Player player, ItemStack stack, CallbackInfo ci) {
		Slot self = (Slot) (Object) this;
		if (self.container instanceof BrewingStandBlockEntity stand) {
			CraftingXp.potionTaken(player, stand, self.getContainerSlot(), stack);
		}
	}
}
