package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremeprogression.synergy.SynergyEffects;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;

/** Deep Walker: a sculk sensor in the Deep Dark notices a player who has it 2 blocks later. */
@Mixin(VibrationSystem.Listener.class)
public abstract class VibrationListenerMixin {
	@Shadow
	@Final
	private VibrationSystem system;

	@Inject(method = "handleGameEvent", at = @At("HEAD"), cancellable = true, require = 1)
	private void extremeprogression$deepWalker(ServerLevel level, Holder<GameEvent> event, GameEvent.Context context, Vec3 source,
			CallbackInfoReturnable<Boolean> cir) {
		VibrationSystem.User user = this.system.getVibrationUser();
		if (SynergyEffects.unheard(level, user, context, source)) {
			cir.setReturnValue(false);
		}
	}
}
