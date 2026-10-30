package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;

import com.extremeprogression.spec.Arcana;
import com.extremeprogression.trunk.Trunks;
import com.extremeprogression.xp.CraftingXp;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;

/**
 * An enchantment at the table: the level its offer required is read first, because the offers change after it. Arcane
 * Insight (Enchanter): every enchantment of the offers is sent to its player, and button 3 is its free reroll. Rich
 * Choice (Enchanter): offers are rolled a little higher.
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {
	@Shadow
	@Final
	public int[] costs;

	@Shadow
	@Final
	private DataSlot enchantmentSeed;

	@Shadow
	@Final
	private Container enchantSlots;

	@Shadow
	@Final
	private ContainerLevelAccess access;

	@Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true, require = 1)
	private void extremeprogression$offer(Player player, int id, CallbackInfoReturnable<Boolean> cir, @Share("cost") LocalIntRef cost) {
		if (id == 3) {
			// Arcane Insight's free reroll: new offers from a new seed, without levels
			if (Arcana.reroll(player)) {
				this.enchantmentSeed.set(player.getEnchantmentSeed());
				((EnchantmentMenu) (Object) this).slotsChanged(this.enchantSlots);
				cir.setReturnValue(true);
			} else {
				cir.setReturnValue(false);
			}
			return;
		}
		cost.set(id >= 0 && id < this.costs.length ? this.costs[id] : 0);
	}

	/** Arcane Insight: once the offers are worked out, their player sees every enchantment of each. */
	@Inject(method = "slotsChanged", at = @At("TAIL"), require = 1)
	private void extremeprogression$insight(Container container, CallbackInfo ci) {
		EnchantmentMenu self = (EnchantmentMenu) (Object) this;
		ItemStack item = this.enchantSlots.getItem(0);
		this.access.execute((level, pos) -> Arcana.offersChanged(self,
				slot -> ((EnchantmentMenuAccess) self).extremeprogression$enchantments(level.registryAccess(), item, slot, this.costs[slot])));
	}

	/** Rich Choice: the offers are rolled as if a little higher (the price shown stays). */
	@ModifyVariable(method = "getEnchantmentList", at = @At("HEAD"), ordinal = 1, argsOnly = true, require = 1)
	private int extremeprogression$richChoice(int cost) {
		return Arcana.richChoice((EnchantmentMenu) (Object) this, cost);
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
