package com.extremeprogression.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.extremeprogression.spec.Smithing;

import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.world.entity.player.Player;

/** Preservation (Armorer): the anvil reads "Too Expensive!" from the same, higher limit the server uses. */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {
	@Shadow
	@Final
	private Player player;

	@ModifyConstant(method = "extractLabels", constant = @Constant(intValue = 40), require = 1)
	private int extremeprogression$tooExpensive(int limit) {
		return Smithing.tooExpensive(this.player);
	}
}
