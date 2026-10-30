package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremeprogression.antiexploit.PlacedBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** What bonemeal grows gives no XP (design §2): by hand or from a dispenser, both go through here. */
@Mixin(BoneMealItem.class)
public abstract class BoneMealItemMixin {
	@Inject(method = "growCrop", at = @At("HEAD"), require = 1)
	private static void extremeprogression$begin(ItemStack stack, Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		PlacedBlocks.beginBonemeal(level, pos);
	}

	@Inject(method = "growCrop", at = @At("RETURN"), require = 1)
	private static void extremeprogression$end(ItemStack stack, Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		PlacedBlocks.endBonemeal(level, pos, cir.getReturnValueZ());
	}
}
