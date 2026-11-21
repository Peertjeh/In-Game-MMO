package com.extremeprogression.legends;

import java.util.List;
import java.util.Locale;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * The relics (2.2, the owner's wish: items to find, from raids too, for quests that unlock permanent buffs): seven
 * kinds, each from its own corner of the world, handed in to swear the Oaths. A knowledge book marked as one in its
 * custom data, drawn as the item it looks like, that does nothing when used and is in no recipe.
 */
public enum Relic {
	/** Raids won, raid captains, pillager outposts and woodland mansions. */
	WARBANNER_SHARD("ominous_bottle", 0xD8D8D8, Rarity.UNCOMMON),
	/** Ancient cities, and the Warden. */
	SCULK_HEART("echo_shard", 0x14B3C2, Rarity.RARE),
	/** Sunken treasure, and Elder Guardians. */
	TIDAL_PEARL("heart_of_the_sea", 0x3AB3DA, Rarity.RARE),
	/** Bastions and Nether fortresses, blazes and piglin brutes. */
	EMBER_CORE("magma_cream", 0xFF7F27, Rarity.UNCOMMON),
	/** End cities, shulkers and the Ender Dragon. */
	VOID_SHARD("popped_chorus_fruit", 0xC27BFF, Rarity.RARE),
	/** Jungle and desert temples, and woodland mansions. */
	VERDANT_IDOL("emerald", 0x55FF55, Rarity.UNCOMMON),
	/** Champions, and trial chamber vaults. */
	CHAMPIONS_CREST("trial_key", 0xFFAA00, Rarity.EPIC);

	/** The mark in the item's custom data. */
	public static final String KEY = "extremeprogression:relic";

	/** The vanilla item it is drawn as. */
	public final String model;
	public final int color;
	public final Rarity rarity;
	public final String id;

	Relic(String model, int color, Rarity rarity) {
		this.model = model;
		this.color = color;
		this.rarity = rarity;
		this.id = name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "item.extremeprogression.relic." + id;
	}

	/** This many of the relic (up to 64 in a stack). */
	public ItemStack stack(int count) {
		ItemStack s = new ItemStack(Items.KNOWLEDGE_BOOK);
		s.set(DataComponents.MAX_STACK_SIZE, 64);
		s.setCount(Math.max(1, Math.min(64, count)));
		s.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("minecraft", model));
		s.set(DataComponents.ITEM_NAME, Component.translatable(translationKey()).withColor(color));
		s.set(DataComponents.RARITY, rarity);
		s.set(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable(translationKey() + ".lore").withStyle(st -> st.withColor(0xAAAAAA).withItalic(false)),
				Component.translatable("item.extremeprogression.relic.use").withStyle(st -> st.withColor(0x777777).withItalic(false)))));
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, id);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return s;
	}

	/** The relic an item is, or null. */
	public static Relic of(ItemStack s) {
		if (s == null || s.isEmpty() || !s.is(Items.KNOWLEDGE_BOOK)) {
			return null;
		}
		CustomData data = s.get(DataComponents.CUSTOM_DATA);
		return data == null ? null : byId(data.copyTag().getStringOr(KEY, ""));
	}

	public static Relic byId(String id) {
		for (Relic r : values()) {
			if (r.id.equals(id)) {
				return r;
			}
		}
		return null;
	}
}
