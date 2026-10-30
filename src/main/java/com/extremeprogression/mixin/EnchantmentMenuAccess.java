package com.extremeprogression.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

/** Every enchantment of an offer, as the table works it out (Arcane Insight shows them all). */
@Mixin(EnchantmentMenu.class)
public interface EnchantmentMenuAccess {
	@org.spongepowered.asm.mixin.gen.Accessor("enchantmentSeed")
	net.minecraft.world.inventory.DataSlot extremeprogression$seed();

	@Invoker("getEnchantmentList")
	List<EnchantmentInstance> extremeprogression$enchantments(RegistryAccess registries, ItemStack stack, int slot, int cost);
}
