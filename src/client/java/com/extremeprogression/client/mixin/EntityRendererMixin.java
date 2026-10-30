package com.extremeprogression.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.client.state.ClientPower;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * The power level beside a player's name above their head: once the game has worked out the name tag (only when one is
 * shown at all, so never through walls for a sneaking player, never for an invisible one), the badge goes behind it.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
			at = @At("TAIL"), require = 1)
	private void extremeprogression$power(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
		if (entity instanceof Player p && state.nameTag != null) {
			Component badge = ClientPower.badge(p.getUUID());
			if (badge != null) {
				state.nameTag = state.nameTag.copy().append(" ").append(badge);
			}
		}
	}
}
