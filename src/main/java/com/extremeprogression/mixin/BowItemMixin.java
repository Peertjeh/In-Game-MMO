package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.extremeprogression.trunk.Trunks;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Steady Hand (Ranged 5): the draw counts as 5% longer, so a bow is fully drawn sooner. */
@Mixin(BowItem.class)
public abstract class BowItemMixin {
	@ModifyVariable(method = "releaseUsing", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 1)
	private int extremeprogression$steadyHand(int timeLeft, ItemStack bow, Level level, LivingEntity user) {
		return Trunks.bowTimeLeft(bow, user, timeLeft);
	}
}
