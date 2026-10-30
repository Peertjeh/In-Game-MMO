package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;

/** The player at an anvil or smithing table, and where it stands (the trunk, and the Smithing specializations). */
@Mixin(ItemCombinerMenu.class)
public interface ItemCombinerMenuAccess {
	@Accessor("player")
	Player extremeprogression$player();

	@Accessor("access")
	ContainerLevelAccess extremeprogression$access();
}
