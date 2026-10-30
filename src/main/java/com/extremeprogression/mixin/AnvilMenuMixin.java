package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.xp.CraftingXp;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;

/** The result of an anvil taken by a player: Smithing for a repair, Arcana for combined enchantments. */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
	@Inject(method = "onTake", at = @At("HEAD"), require = 1)
	private void extremeprogression$taken(Player player, ItemStack result, CallbackInfo ci) {
		AnvilMenu self = (AnvilMenu) (Object) this;
		CraftingXp.anvil(player, self.getSlot(0).getItem().copy(), self.getSlot(1).getItem().copy(), result, self.getCost());
	}
}
