package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.skill.Skill;
import com.extremeprogression.trunk.Trunks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Light Step (Farming 5): you no longer trample farmland. Drying out without water still happens. */
@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockMixin {
	@Inject(method = "turnToBaseBlock", at = @At("HEAD"), cancellable = true, require = 1)
	private void extremeprogression$lightStep(Entity entity, BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
		if (entity != null && Trunks.has(entity, Skill.FARMING, 5)) {
			ci.cancel();
		}
	}
}
