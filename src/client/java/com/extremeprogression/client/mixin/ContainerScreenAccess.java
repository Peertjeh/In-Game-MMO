package com.extremeprogression.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/** Where a container screen's panel is (it moves when the recipe book opens), for the Character Sheet's button. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccess {
	@Accessor("leftPos")
	int extremeprogression$leftPos();

	@Accessor("topPos")
	int extremeprogression$topPos();
}
