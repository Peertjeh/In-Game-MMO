package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremeprogression.antiexploit.PlacedBlocks;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** Placed blocks never give gathering XP: every block placed from an item is marked (design §12). */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Inject(method = "placeBlock", at = @At("RETURN"), require = 1)
	private void extremeprogression$placed(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			PlacedBlocks.placed(context.getLevel(), context.getClickedPos(), state);
			if (context.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
				com.extremeprogression.synergy.SynergyEffects.placed(context.getPlayer(), level, context.getClickedPos(), state);
			}
		}
	}
}
