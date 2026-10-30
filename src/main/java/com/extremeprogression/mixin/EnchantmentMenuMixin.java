package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;

import com.extremeprogression.trunk.Trunks;
import com.extremeprogression.xp.CraftingXp;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;

/** An enchantment at the table: the level its offer required is read first, because the offers change after it. */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {
	@Shadow
	@Final
	public int[] costs;

	@Inject(method = "clickMenuButton", at = @At("HEAD"), require = 1)
	private void extremeprogression$offer(Player player, int id, CallbackInfoReturnable<Boolean> cir, @Share("cost") LocalIntRef cost) {
		cost.set(id >= 0 && id < this.costs.length ? this.costs[id] : 0);
	}

	@Inject(method = "clickMenuButton", at = @At("RETURN"), require = 1)
	private void extremeprogression$enchanted(Player player, int id, CallbackInfoReturnable<Boolean> cir, @Share("cost") LocalIntRef cost) {
		// (an offer can come up empty: then nothing was enchanted, and nothing is earned)
		ItemStack item = ((EnchantmentMenu) (Object) this).getSlot(0).getItem();
		if (cir.getReturnValueZ() && (item.isEnchanted() || item.has(DataComponents.STORED_ENCHANTMENTS))) {
			CraftingXp.enchanted(player, cost.get());
			Trunks.enchanted(player, (EnchantmentMenu) (Object) this);
		}
	}
}
