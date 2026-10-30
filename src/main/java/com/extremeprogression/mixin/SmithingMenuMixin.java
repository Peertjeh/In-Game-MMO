package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.xp.CraftingXp;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;

/** The result of a smithing table taken by a player: a netherite upgrade or an armor trim. */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin {
	@Inject(method = "onTake", at = @At("HEAD"), require = 1)
	private void extremeprogression$taken(Player player, ItemStack result, CallbackInfo ci) {
		CraftingXp.smithed(player, ((SmithingMenu) (Object) this).getSlot(0).getItem().copy());
	}
}
