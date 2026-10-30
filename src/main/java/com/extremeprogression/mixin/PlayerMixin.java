package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import com.extremeprogression.skill.Specialization;
import com.extremeprogression.spec.Melee;
import com.extremeprogression.spec.Specs;
import com.extremeprogression.trunk.Trunks;

import net.minecraft.world.entity.player.Player;

/**
 * Stamina (Exploration 10) and the specializations' hunger; Flurry's attack bar; breaking speeds; Unbreakable's sprint.
 * The last three run on the client too, for its own player.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true, require = 1)
	private float extremeprogression$stamina(float amount) {
		Player self = (Player) (Object) this;
		return Specs.exhaustion(self, Trunks.exhaustion(self, amount));
	}

	/** Flurry (Brawler): every hit counts as fully charged. */
	@ModifyReturnValue(method = "getAttackStrengthScale", at = @At("RETURN"), require = 1)
	private float extremeprogression$flurry(float scale) {
		return scale < 1 && Melee.flurry((Player) (Object) this) ? 1f : scale;
	}

	/** The gathering specializations' breaking speeds (on the client too, which works out how fast a block breaks). */
	@ModifyReturnValue(method = "getDestroySpeed", at = @At("RETURN"), require = 1)
	private float extremeprogression$destroySpeed(float speed, net.minecraft.world.level.block.state.BlockState state) {
		return Specs.destroySpeed((Player) (Object) this, state, speed);
	}

	/** Unbreakable (Juggernaut): no sprinting. */
	@ModifyReturnValue(method = "canSprint", at = @At("RETURN"), require = 1)
	private boolean extremeprogression$unbreakable(boolean can) {
		return can && !Specs.active((Player) (Object) this, Specialization.JUGGERNAUT);
	}
}
