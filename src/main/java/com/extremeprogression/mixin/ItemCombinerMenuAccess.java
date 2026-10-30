package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ItemCombinerMenu;

/** The player at an anvil (Thrifty Anvil and Tempered Steel, Smithing 10 and 15). */
@Mixin(ItemCombinerMenu.class)
public interface ItemCombinerMenuAccess {
	@Accessor("player")
	Player extremeprogression$player();
}
