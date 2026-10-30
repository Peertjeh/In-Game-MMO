package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.xp.CraftingXp;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;

/** Smelted metal taken out of a furnace by a player, by hand or shift-click (a hopper never comes through here). */
@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {
	@Shadow
	@Final
	private Player player;
	@Shadow
	private int removeCount;

	@Inject(method = "checkTakeAchievements", at = @At("HEAD"), require = 1)
	private void extremeprogression$smelted(ItemStack stack, CallbackInfo ci) {
		CraftingXp.smelted(this.player, stack, this.removeCount);
	}
}
