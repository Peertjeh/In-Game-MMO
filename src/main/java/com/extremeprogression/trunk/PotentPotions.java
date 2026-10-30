package com.extremeprogression.trunk;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Potent Brews (Arcana 15): potions brewed at a stand whose user has the trunk bonus are marked, and last 5% longer
 * when they are drunk.
 */
public final class PotentPotions {
	private PotentPotions() {}

	private static final String MARK = "extremeprogression_potent";
	private static boolean drinking = false;

	public static void mark(ItemStack potion) {
		if (potion.isEmpty() || !potion.has(DataComponents.POTION_CONTENTS)) {
			return;
		}
		CompoundTag tag = potion.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.putBoolean(MARK, true);
		potion.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	public static boolean marked(ItemStack potion) {
		CustomData data = potion.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(MARK);
	}

	/** The potion now being drunk (EMPTY when the drinking is done). */
	public static void drinking(ItemStack potion) {
		drinking = !potion.isEmpty() && marked(potion);
	}

	/** The duration scale of the effects being applied now. */
	public static float scale() {
		return drinking ? 1.05f : 1.0f;
	}
}
