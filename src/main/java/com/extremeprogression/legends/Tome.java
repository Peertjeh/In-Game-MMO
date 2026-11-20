package com.extremeprogression.legends;

import java.util.List;
import java.util.Locale;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * The Tomes of Insight (2.0, the owner's wish for XP boosts of 25, 50, 100% and more): a knowledge book marked as one,
 * read with a right click for a boost of this many percent more XP for this many minutes of play while earning XP.
 */
public enum Tome {
	INSIGHT(25, 30, Rarity.UNCOMMON, 0x55FF55, false),
	GREATER(50, 30, Rarity.RARE, 0x55FFFF, false),
	GRAND(100, 30, Rarity.EPIC, 0xFF55FF, true),
	LEGENDARY(200, 15, Rarity.EPIC, 0xFFAA00, true);

	/** The mark in the item's custom data. */
	public static final String KEY = "extremeprogression:tome";

	public final int percent;
	public final int minutes;
	public final Rarity rarity;
	public final int color;
	public final boolean glint;
	public final String id;

	Tome(int percent, int minutes, Rarity rarity, int color, boolean glint) {
		this.percent = percent;
		this.minutes = minutes;
		this.rarity = rarity;
		this.color = color;
		this.glint = glint;
		this.id = name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "item.extremeprogression.tome." + id;
	}

	/** This many tomes (up to 16 in a stack). */
	public ItemStack stack(int count) {
		ItemStack s = new ItemStack(Items.KNOWLEDGE_BOOK);
		s.set(DataComponents.MAX_STACK_SIZE, 16);
		s.setCount(Math.max(1, Math.min(16, count)));
		s.set(DataComponents.ITEM_NAME, Component.translatable(translationKey()).withColor(color));
		s.set(DataComponents.RARITY, rarity);
		s.set(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("item.extremeprogression.tome.lore", percent, minutes).withStyle(st -> st.withColor(0xAAAAAA).withItalic(false)),
				Component.translatable("item.extremeprogression.tome.lore2").withStyle(st -> st.withColor(0x777777).withItalic(false)))));
		if (glint) {
			s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, id);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return s;
	}

	/** The tome an item is, or null. */
	public static Tome of(ItemStack s) {
		if (s == null || s.isEmpty() || !s.is(Items.KNOWLEDGE_BOOK)) {
			return null;
		}
		CustomData data = s.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		return byId(data.copyTag().getStringOr(KEY, ""));
	}

	public static Tome byId(String id) {
		for (Tome t : values()) {
			if (t.id.equals(id)) {
				return t;
			}
		}
		return null;
	}

	/** The tome for a boost of this many percent, or null when none has it. */
	public static Tome byPercent(int percent) {
		for (Tome t : values()) {
			if (t.percent == percent) {
				return t;
			}
		}
		return null;
	}
}
