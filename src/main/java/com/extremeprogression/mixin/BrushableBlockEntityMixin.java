package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.xp.GatheringXp;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;

/** Brushing suspicious sand or gravel until it gives up its find: Mining XP (design §2, Appendix A.4). */
@Mixin(BrushableBlockEntity.class)
public abstract class BrushableBlockEntityMixin {
	@Inject(method = "brushingCompleted", at = @At("HEAD"), require = 1)
	private void extremeprogression$brushed(ServerLevel level, LivingEntity user, ItemStack brush, CallbackInfo ci) {
		if (user instanceof Player player) {
			GatheringXp.brushed(player);
		}
	}
}
