package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;

import com.extremeprogression.spec.Smithing;
import com.extremeprogression.xp.CraftingXp;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The result of a smithing table taken by a player: a netherite upgrade or an armor trim. The Tinkerer's Polish and
 * Reforge work on the item taken; Template Saver puts the template back afterwards.
 */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin {
	@Inject(method = "onTake", at = @At("HEAD"), require = 1)
	private void extremeprogression$taken(Player player, ItemStack result, CallbackInfo ci, @Share("template") LocalRef<ItemStack> template) {
		SmithingMenu self = (SmithingMenu) (Object) this;
		template.set(self.getSlot(0).getItem().copy());
		CraftingXp.smithed(player, template.get());
		if (player instanceof ServerPlayer p) {
			Smithing.polished(p, result);
			Smithing.reforge(p, result);
		}
	}

	@Inject(method = "onTake", at = @At("TAIL"), require = 1)
	private void extremeprogression$template(Player player, ItemStack result, CallbackInfo ci, @Share("template") LocalRef<ItemStack> template) {
		SmithingMenu self = (SmithingMenu) (Object) this;
		ItemStack before = template.get();
		ItemStack now = self.getSlot(0).getItem();
		if (player instanceof ServerPlayer p && !before.isEmpty() && now.getCount() < before.getCount() && Smithing.templateSaved(p)) {
			if (now.isEmpty()) {
				self.getSlot(0).set(before.copyWithCount(1));
			} else {
				now.grow(1);
				self.getSlot(0).setChanged();
			}
		}
	}
}
