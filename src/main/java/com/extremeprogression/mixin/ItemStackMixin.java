package com.extremeprogression.mixin;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.extremeprogression.trunk.Trunks;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Sturdy Tools (Mining 10), Steady Line (Fishing 10) and Sturdy Shield (Bulwark): a use that costs no durability. */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@ModifyVariable(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
			at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 1)
	private int extremeprogression$durability(int amount, int same, ServerLevel level, ServerPlayer player, Consumer<ItemStack> broken) {
		ItemStack self = (ItemStack) (Object) this;
		return com.extremeprogression.spec.Defense.shieldDurability(self, player, Trunks.durability(self, player, amount));
	}
}
