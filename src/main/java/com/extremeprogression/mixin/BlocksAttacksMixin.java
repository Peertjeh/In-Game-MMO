package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.extremeprogression.spec.Defense;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.BlocksAttacks;

/** Recovery (Bulwark): a shield an axe disabled is ready again sooner. */
@Mixin(BlocksAttacks.class)
public abstract class BlocksAttacksMixin {
	@ModifyVariable(method = "disable", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 1)
	private float extremeprogression$recovery(float seconds, ServerLevel level, LivingEntity blocker) {
		return Defense.disabledFor(blocker, seconds);
	}
}
