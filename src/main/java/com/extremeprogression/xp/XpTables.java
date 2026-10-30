package com.extremeprogression.xp;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The approved XP values of the gathering skills (design Appendix A.4 to A.7), by registry id. Ids this version of
 * Minecraft does not have simply never match.
 */
public final class XpTables {
	private XpTables() {}

	// --- A.4 Mining ------------------------------------------------------------------------------

	private static final Map<String, Double> PICKAXE = table(
			"stone", 3, "andesite", 3, "diorite", 3, "granite", 3, "tuff", 3,
			"deepslate", 5, "cobbled_deepslate", 5,
			"netherrack", 1,
			"end_stone", 4, "basalt", 4, "blackstone", 4,
			"calcite", 4, "dripstone_block", 4, "magma_block", 4,
			"sandstone", 3, "red_sandstone", 3,
			"obsidian", 20, "crying_obsidian", 30,
			"coal_ore", 40, "deepslate_coal_ore", 70,
			"copper_ore", 60, "deepslate_copper_ore", 90,
			"iron_ore", 90, "deepslate_iron_ore", 130,
			"gold_ore", 130, "deepslate_gold_ore", 190,
			"redstone_ore", 60, "deepslate_redstone_ore", 90,
			"lapis_ore", 80, "deepslate_lapis_ore", 120,
			"diamond_ore", 600, "deepslate_diamond_ore", 900,
			"emerald_ore", 500, "deepslate_emerald_ore", 700,
			"nether_quartz_ore", 30, "nether_gold_ore", 40,
			"ancient_debris", 1500, "amethyst_block", 10);
	/** Terracotta in every colour (the badlands). */
	private static final double TERRACOTTA = 5;

	private static final Map<String, Double> SHOVEL = table(
			"dirt", 2, "grass_block", 2, "coarse_dirt", 2, "podzol", 2, "mycelium", 2, "rooted_dirt", 2,
			"sand", 2, "red_sand", 2, "gravel", 2,
			"clay", 6,
			"mud", 3, "soul_sand", 3, "soul_soil", 3,
			"snow", 1, "snow_block", 1);
	/** Brushing suspicious sand or gravel until it gives up its find. */
	public static final double BRUSHING = 250;

	// --- A.5 Foraging ----------------------------------------------------------------------------

	/** Logs of natural trees. (Poplar is new in 26.3 and not in the approved table: valued like birch.) */
	private static final Map<String, Double> LOGS = table(
			"oak_log", 16, "birch_log", 18, "spruce_log", 18, "poplar_log", 18,
			"dark_oak_log", 20, "acacia_log", 20,
			"jungle_log", 22, "mangrove_log", 22,
			"cherry_log", 24, "crimson_stem", 24, "warped_stem", 24,
			"pale_oak_log", 26);

	public static final double WILD_SWEET_BERRIES = 15;
	public static final double GLOW_BERRIES = 15;
	public static final double MUSHROOM = 10;
	public static final double FLOWER = 6;
	public static final double RARE_FLOWER = 10;
	public static final double WILD_COCOA = 12;
	public static final double WILD_MELON = 30;
	public static final double WILD_HONEY = 40;

	private static final Set<String> MUSHROOMS = Set.of("brown_mushroom", "red_mushroom");
	/** Flowers that are not small flowers. (Torchflowers are a Farming crop; wither roses never grow wild.) */
	private static final Set<String> OTHER_FLOWERS = Set.of("sunflower", "lilac", "rose_bush", "peony", "pink_petals", "wildflowers");
	private static final Set<String> NO_FLOWER = Set.of("torchflower", "wither_rose");
	private static final Set<String> RARE_FLOWERS = Set.of("blue_orchid", "allium", "lily_of_the_valley", "open_eyeblossom", "closed_eyeblossom",
			"golden_dandelion", "spore_blossom", "cactus_flower");

	// --- A.6 Farming -----------------------------------------------------------------------------

	private static final Map<String, Double> CROPS = table(
			"wheat", 8, "carrots", 8, "potatoes", 8, "beetroots", 9);
	public static final double NETHER_WART = 10;
	public static final double STEM_FRUIT = 10;
	public static final double PLANTED_COCOA = 9;
	public static final double PLANTED_SWEET_BERRIES = 6;
	public static final double SUGAR_CANE = 4;
	public static final double CACTUS = 3;
	public static final double TORCHFLOWER = 60;
	public static final double PITCHER = 80;
	public static final double BREEDING = 40;
	public static final double SHEARING = 15;

	private static final Map<String, Double> TAMING = table(
			"wolf", 200, "cat", 250, "fox", 300,
			"horse", 300, "donkey", 300, "mule", 300, "llama", 300, "trader_llama", 300,
			"parrot", 350, "camel", 400);
	/** An animal the table does not list (one added by a later version or a mod). */
	public static final double TAMING_OTHER = 200;

	// --- A.7 Fishing -----------------------------------------------------------------------------

	private static final Map<String, Double> CATCHES = table(
			"cod", 60, "salmon", 80, "pufferfish", 100, "tropical_fish", 160,
			"name_tag", 300, "saddle", 300, "nautilus_shell", 300,
			"enchanted_book", 400,
			"heart_of_the_sea", 1000, "trident", 1000);
	/** A bow or fishing rod from the treasure table (they are always enchanted there). */
	public static final double TREASURE_GEAR = 250;
	/** Everything else: junk. */
	public static final double JUNK = 15;

	// --- Lookups ---------------------------------------------------------------------------------

	private static String id(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).getPath();
	}

	private static boolean vanilla(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft");
	}

	public static double pickaxe(BlockState state) {
		if (!vanilla(state.getBlock())) {
			return 0;
		}
		if (state.is(BlockTags.TERRACOTTA)) {
			return TERRACOTTA;
		}
		return PICKAXE.getOrDefault(id(state.getBlock()), 0.0);
	}

	public static double shovel(BlockState state) {
		return vanilla(state.getBlock()) ? SHOVEL.getOrDefault(id(state.getBlock()), 0.0) : 0;
	}

	public static double log(BlockState state) {
		return vanilla(state.getBlock()) ? LOGS.getOrDefault(id(state.getBlock()), 0.0) : 0;
	}

	public static double crop(BlockState state) {
		return vanilla(state.getBlock()) ? CROPS.getOrDefault(id(state.getBlock()), 0.0) : 0;
	}

	public static boolean mushroom(BlockState state) {
		return vanilla(state.getBlock()) && MUSHROOMS.contains(id(state.getBlock()));
	}

	/** A flower's XP, or 0 for anything that is no wild flower. */
	public static double flower(BlockState state) {
		if (!vanilla(state.getBlock())) {
			return 0;
		}
		String id = id(state.getBlock());
		if (NO_FLOWER.contains(id) || !(state.is(BlockTags.SMALL_FLOWERS) || OTHER_FLOWERS.contains(id) || RARE_FLOWERS.contains(id))) {
			return 0;
		}
		return RARE_FLOWERS.contains(id) ? RARE_FLOWER : FLOWER;
	}

	public static double taming(EntityType<?> type) {
		var key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		return key.getNamespace().equals("minecraft") ? TAMING.getOrDefault(key.getPath(), TAMING_OTHER) : TAMING_OTHER;
	}

	/** The XP of one fished item. */
	public static double caught(ItemStack stack) {
		Item item = stack.getItem();
		var key = BuiltInRegistries.ITEM.getKey(item);
		if (!key.getNamespace().equals("minecraft")) {
			return JUNK;
		}
		String id = key.getPath();
		if ((id.equals("bow") || id.equals("fishing_rod")) && stack.isEnchanted()) {
			return TREASURE_GEAR;
		}
		return CATCHES.getOrDefault(id, JUNK);
	}

	private static Map<String, Double> table(Object... pairs) {
		Map<String, Double> m = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			m.put((String) pairs[i], ((Number) pairs[i + 1]).doubleValue());
		}
		return Map.copyOf(m);
	}
}
