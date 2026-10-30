package com.extremeprogression.trunk;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * How long a brewed potion's effects last: Potent Brews (Arcana 15) and the Alchemist's Extended mark a potion with a
 * duration factor. Drunk, it lasts that much longer (a Hexer's own drink 25% shorter); thrown, its splash and cloud do
 * (see the Arcana specializations).
 */
public final class PotentPotions {
	private PotentPotions() {}

	private static final String MARK = "extremeprogression_potent";
	/** The duration scale of the potion being drunk now (1 when none). */
	private static float drinking = 1.0f;

	/** Marks a potion with a duration factor (a factor of 1 or less leaves it unmarked). */
	public static void mark(ItemStack potion, double factor) {
		if (potion.isEmpty() || !potion.has(DataComponents.POTION_CONTENTS) || factor <= 1) {
			return;
		}
		CompoundTag tag = potion.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.putDouble(MARK, factor);
		potion.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	/** Potent Brews alone: 5% longer. */
	public static void mark(ItemStack potion) {
		mark(potion, 1.05);
	}

	public static boolean marked(ItemStack potion) {
		return factor(potion) > 1;
	}

	/** A potion's duration factor: 1 unmarked; a mark from before the factor was stored counts as Potent Brews. */
	public static double factor(ItemStack potion) {
		CustomData data = potion.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return 1;
		}
		CompoundTag tag = data.copyTag();
		if (!tag.contains(MARK)) {
			return 1;
		}
		double f = tag.getDoubleOr(MARK, 1.05);
		return f > 1 ? f : 1.05;
	}

	/** The potion now being drunk, and by whom (EMPTY when the drinking is done). */
	public static void drinking(ItemStack potion, net.minecraft.world.entity.LivingEntity drinker) {
		drinking = potion.isEmpty() ? 1.0f : (float) (factor(potion) * com.extremeprogression.spec.Arcana.drinkFactor(drinker));
	}

	/** The duration scale of the effects being applied now. */
	public static float scale() {
		return drinking;
	}
}
