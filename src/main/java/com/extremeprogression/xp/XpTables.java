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
 * The approved XP values of every skill (design Appendix A), by registry id: the defaults of the data files
 * {@code config/extremeprogression/xp/<skill>.json} ({@link XpData}). Ids this version of Minecraft does not have simply
 * never match; another mod's ids count when a data file lists them as {@code "namespace:path"}.
 */
public final class XpTables {
	private XpTables() {}

	// --- A.2 Melee and Ranged, A.3 Defense --------------------------------------------------------

	/** XP per point of damage dealt, × the target's danger factor. */
	public static double MELEE_PER_DAMAGE = 4;
	public static double RANGED_PER_DAMAGE = 4;
	/** XP per point of damage your shield blocked or your armor absorbed, × the attacker's danger factor. */
	public static double DEFENSE_PER_DAMAGE = 15;
	/** Farming XP per point of damage your tamed animals deal, × the target's danger factor. */
	public static double PET_PER_DAMAGE = 3;
	/** Ranged: +2% per block beyond 10 blocks, at most +60% (at 40 blocks). */
	public static double RANGED_FREE_DISTANCE = 10;
	public static double RANGED_BONUS_PER_BLOCK = 0.02;
	public static double RANGED_MAX_BONUS = 0.60;

	/** Danger factors of hostile mobs. Passive animals are 0; a hostile mob not listed is 1.0. */
	static final Map<String, Double> DANGER = table(
			"silverfish", 0.5, "endermite", 0.5,
			"slime", 0.6, "magma_cube", 0.6,
			"zombified_piglin", 0.8,
			"zombie", 1.0, "husk", 1.0, "drowned", 1.0, "zombie_villager", 1.0, "spider", 1.0, "vex", 1.0, "iron_golem", 1.0,
			"skeleton", 1.2, "stray", 1.2, "bogged", 1.2, "piglin", 1.2,
			"cave_spider", 1.3, "pillager", 1.3,
			"phantom", 1.4,
			"creeper", 1.5, "enderman", 1.5, "witch", 1.5, "guardian", 1.5, "creaking", 1.5,
			"vindicator", 1.6, "blaze", 1.6, "ghast", 1.6, "hoglin", 1.6, "zoglin", 1.6, "shulker", 1.6,
			"breeze", 1.8,
			"wither_skeleton", 2.0, "evoker", 2.0, "ravager", 2.0,
			"piglin_brute", 2.2,
			"warden", 3.0, "elder_guardian", 3.0, "wither", 3.0, "ender_dragon", 3.0);
	public static double DANGER_UNLISTED_HOSTILE = 1.0;
	public static double DANGER_PLAYER = 1.5;

	/** The danger factor of a mob type, by id; null when the table does not list it. */
	public static Double danger(String namespace, String path) {
		return DANGER.get(key(namespace, path));
	}

	// --- A.4 Mining ------------------------------------------------------------------------------

	static final Map<String, Double> PICKAXE = table(
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
	static double TERRACOTTA = 5;

	static final Map<String, Double> SHOVEL = table(
			"dirt", 2, "grass_block", 2, "coarse_dirt", 2, "podzol", 2, "mycelium", 2, "rooted_dirt", 2,
			"sand", 2, "red_sand", 2, "gravel", 2,
			"clay", 6,
			"mud", 3, "soul_sand", 3, "soul_soil", 3,
			"snow", 1, "snow_block", 1);
	/** Brushing suspicious sand or gravel until it gives up its find. */
	public static double BRUSHING = 250;

	// --- A.5 Foraging ----------------------------------------------------------------------------

	/** Logs of natural trees. (Poplar is new in 26.3 and not in the approved table: valued like birch.) */
	static final Map<String, Double> LOGS = table(
			"oak_log", 16, "birch_log", 18, "spruce_log", 18, "poplar_log", 18,
			"dark_oak_log", 20, "acacia_log", 20,
			"jungle_log", 22, "mangrove_log", 22,
			"cherry_log", 24, "crimson_stem", 24, "warped_stem", 24,
			"pale_oak_log", 26);

	public static double WILD_SWEET_BERRIES = 15;
	public static double GLOW_BERRIES = 15;
	public static double MUSHROOM = 10;
	public static double FLOWER = 6;
	public static double RARE_FLOWER = 10;
	public static double WILD_COCOA = 12;
	public static double WILD_MELON = 30;
	public static double WILD_HONEY = 40;

	private static final Set<String> MUSHROOMS = Set.of("brown_mushroom", "red_mushroom");
	/** Flowers that are not small flowers. (Torchflowers are a Farming crop; wither roses never grow wild.) */
	private static final Set<String> OTHER_FLOWERS = Set.of("sunflower", "lilac", "rose_bush", "peony", "pink_petals", "wildflowers");
	private static final Set<String> NO_FLOWER = Set.of("torchflower", "wither_rose");
	private static final Set<String> RARE_FLOWERS = Set.of("blue_orchid", "allium", "lily_of_the_valley", "open_eyeblossom", "closed_eyeblossom",
			"golden_dandelion", "spore_blossom", "cactus_flower");

	// --- A.6 Farming -----------------------------------------------------------------------------

	static final Map<String, Double> CROPS = table(
			"wheat", 8, "carrots", 8, "potatoes", 8, "beetroots", 9);
	public static double NETHER_WART = 10;
	public static double STEM_FRUIT = 10;
	public static double PLANTED_COCOA = 9;
	public static double PLANTED_SWEET_BERRIES = 6;
	public static double SUGAR_CANE = 4;
	public static double CACTUS = 3;
	public static double TORCHFLOWER = 60;
	public static double PITCHER = 80;
	public static double BREEDING = 40;
	public static double SHEARING = 15;

	static final Map<String, Double> TAMING = table(
			"wolf", 200, "cat", 250, "fox", 300,
			"horse", 300, "donkey", 300, "mule", 300, "llama", 300, "trader_llama", 300,
			"parrot", 350, "camel", 400);
	/** An animal the table does not list (one added by a later version or a mod). */
	public static double TAMING_OTHER = 200;

	// --- A.7 Fishing -----------------------------------------------------------------------------

	static final Map<String, Double> CATCHES = table(
			"cod", 60, "salmon", 80, "pufferfish", 100, "tropical_fish", 160,
			"name_tag", 300, "saddle", 300, "nautilus_shell", 300,
			"enchanted_book", 400,
			"heart_of_the_sea", 1000, "trident", 1000);
	/** A bow or fishing rod from the treasure table (they are always enchanted there). */
	public static double TREASURE_GEAR = 250;
	/** Everything else: junk. */
	public static double JUNK = 15;

	// --- A.8 Exploration -------------------------------------------------------------------------

	public static double NEW_CHUNK = 8;
	public static double NEW_CHUNK_ELYTRA = 2;
	public static double REVISITED_CHUNK = 2;
	public static double NEW_BIOME = 500;
	public static double FALL_PER_DAMAGE = 40;
	/** The first structure of each type you ever enter gives this many times its XP. */
	public static double FIRST_OF_TYPE = 3;

	/** A structure type, as in the XP table, with its XP. */
	public record StructureType(String name, double xp) {}

	static final Map<String, StructureType> STRUCTURES = new HashMap<>();

	private static void structures(String type, double xp, String... ids) {
		for (String id : ids) {
			STRUCTURES.put(id, new StructureType(type, xp));
		}
	}

	static {
		structures("ruined_portal", 100, "ruined_portal", "ruined_portal_desert", "ruined_portal_jungle", "ruined_portal_swamp",
				"ruined_portal_mountain", "ruined_portal_ocean", "ruined_portal_nether");
		structures("mineshaft", 150, "mineshaft", "mineshaft_mesa");
		structures("shipwreck", 175, "shipwreck", "shipwreck_beached");
		structures("ocean_ruin", 200, "ocean_ruin_cold", "ocean_ruin_warm");
		structures("igloo", 250, "igloo");
		structures("swamp_hut", 250, "swamp_hut");
		structures("trail_ruins", 275, "trail_ruins");
		structures("village", 300, "village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga");
		structures("desert_pyramid", 400, "desert_pyramid");
		structures("jungle_pyramid", 400, "jungle_pyramid");
		structures("pillager_outpost", 400, "pillager_outpost");
		structures("fortress", 800, "fortress");
		structures("trial_chambers", 1000, "trial_chambers");
		structures("end_city", 1000, "end_city");
		structures("monument", 1200, "monument");
		structures("bastion_remnant", 1200, "bastion_remnant");
		structures("stronghold", 1500, "stronghold");
		structures("mansion", 1500, "mansion");
		structures("ancient_city", 2000, "ancient_city");
	}

	/** Structures nobody enters (buried in the ground, or only a few blocks). */
	private static final Set<String> NO_STRUCTURE = Set.of("buried_treasure", "nether_fossil");
	/** A structure of another mod (such as the Seam's in Extreme Dimensions): valued like a village. */
	public static double STRUCTURE_OTHER = 300;

	/** A structure's type and XP, by id; null for one that gives nothing. */
	public static StructureType structure(String namespace, String path) {
		if (!namespace.equals("minecraft")) {
			return new StructureType(namespace + ":" + path, STRUCTURE_OTHER);
		}
		if (NO_STRUCTURE.contains(path)) {
			return null;
		}
		return STRUCTURES.getOrDefault(path, new StructureType(path, STRUCTURE_OTHER));
	}

	// --- A.9 Smithing ----------------------------------------------------------------------------

	/** Smelting, per item taken out by hand, by the output (the ingot of an ore is the same from raw ore or ore). */
	static final Map<String, Double> SMELTED = table(
			"copper_ingot", 15, "iron_ingot", 25, "gold_ingot", 30, "netherite_scrap", 300,
			"copper_block", 15 * 9, "iron_block", 25 * 9, "gold_block", 30 * 9);
	public static double REPAIR = 200;
	/** Material factors of anvil repairs, by the start of the item's id. */
	static final Map<String, Double> MATERIAL = table(
			"wooden_", 0.5, "stone_", 0.8, "leather_", 0.8, "chainmail_", 1.0, "copper_", 1.0, "golden_", 1.0,
			"iron_", 1.5, "diamond_", 3.0, "netherite_", 4.0);
	/** Gear of no listed material (an elytra, a trident, a mace, a shield, a bow). */
	public static double MATERIAL_OTHER = 1.0;
	public static double NETHERITE_UPGRADE = 1500;
	public static double ARMOR_TRIM = 300;

	public static double smelted(ItemStack stack) {
		var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return SMELTED.getOrDefault(key(key.getNamespace(), key.getPath()), 0.0);
	}

	public static double material(ItemStack stack) {
		var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (key.getNamespace().equals("minecraft")) {
			for (Map.Entry<String, Double> e : MATERIAL.entrySet()) {
				if (key.getPath().startsWith(e.getKey())) {
					return e.getValue();
				}
			}
		}
		return MATERIAL_OTHER;
	}

	// --- A.11 Trading (1.5.0, design round 27) ---------------------------------------------------

	/** A trade with a villager, by the offer's tier (novice to master). */
	public static double TRADE_NOVICE = 30;
	public static double TRADE_APPRENTICE = 45;
	public static double TRADE_JOURNEYMAN = 65;
	public static double TRADE_EXPERT = 90;
	public static double TRADE_MASTER = 120;
	/** For every emerald paid or received in a trade. */
	public static double TRADE_PER_EMERALD = 1.5;
	/** A wandering trader's trade: the journeyman's XP times this. */
	public static double WANDERING_FACTOR = 1.5;
	/** Of one villager's offer, this many trades after each restock count fully, the rest at the share below. */
	public static double SAME_OFFER_FULL = 4;
	public static double SAME_OFFER_FACTOR = 0.25;
	/** A villager reaching a level through your trade: apprentice, journeyman, expert, master. */
	public static double LEVEL_APPRENTICE = 150;
	public static double LEVEL_JOURNEYMAN = 300;
	public static double LEVEL_EXPERT = 500;
	public static double LEVEL_MASTER = 800;
	/** A gold ingot of yours a piglin barters. */
	public static double BARTER_PER_INGOT = 20;
	/** A raid won (Hero of the Village), times the raid's omen level. */
	public static double RAID_PER_OMEN_LEVEL = 1000;

	/** A villager trade's XP by the tier of its offer (1 novice to 5 master). */
	public static double trade(int tier) {
		return switch (Math.max(1, Math.min(5, tier))) {
			case 1 -> TRADE_NOVICE;
			case 2 -> TRADE_APPRENTICE;
			case 3 -> TRADE_JOURNEYMAN;
			case 4 -> TRADE_EXPERT;
			default -> TRADE_MASTER;
		};
	}

	/** The XP for a villager reaching this level (2 apprentice to 5 master); 0 otherwise. */
	public static double levelUp(int level) {
		return switch (level) {
			case 2 -> LEVEL_APPRENTICE;
			case 3 -> LEVEL_JOURNEYMAN;
			case 4 -> LEVEL_EXPERT;
			case 5 -> LEVEL_MASTER;
			default -> 0;
		};
	}

	// --- A.10 Arcana -----------------------------------------------------------------------------

	public static double POTION_BASE = 5;
	public static double POTION_EFFECT = 30;
	public static double POTION_STRONGER = 45;
	public static double POTION_SPLASH = 60;
	public static double POTION_LINGERING = 80;
	public static double ENCHANTING_PER_LEVEL = 15;
	public static double ANVIL_ENCHANTING_PER_LEVEL = 25;

	/** A brewed potion's XP, from its form (potion, splash, lingering) and its kind's id ("awkward", "long_swiftness"). */
	public static double potion(String form, String potion) {
		if (potion == null || potion.equals("water")) {
			return 0;
		}
		if (form.equals("lingering_potion")) {
			return POTION_LINGERING;
		}
		if (form.equals("splash_potion")) {
			return POTION_SPLASH;
		}
		if (!form.equals("potion")) {
			return 0;
		}
		if (potion.equals("awkward") || potion.equals("mundane") || potion.equals("thick")) {
			return POTION_BASE;
		}
		return potion.startsWith("long_") || potion.startsWith("strong_") ? POTION_STRONGER : POTION_EFFECT;
	}

	// --- Lookups ---------------------------------------------------------------------------------

	private static String id(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).getPath();
	}

	private static boolean vanilla(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft");
	}

	// (another mod's blocks give XP only when a data file lists them, as "namespace:path")
	public static double pickaxe(BlockState state) {
		if (vanilla(state.getBlock()) && state.is(BlockTags.TERRACOTTA)) {
			return TERRACOTTA;
		}
		return PICKAXE.getOrDefault(key(state.getBlock()), 0.0);
	}

	public static double shovel(BlockState state) {
		return SHOVEL.getOrDefault(key(state.getBlock()), 0.0);
	}

	public static double log(BlockState state) {
		return LOGS.getOrDefault(key(state.getBlock()), 0.0);
	}

	public static double crop(BlockState state) {
		return CROPS.getOrDefault(key(state.getBlock()), 0.0);
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
		return TAMING.getOrDefault(key(key.getNamespace(), key.getPath()), TAMING_OTHER);
	}

	/** The XP of one fished item. */
	public static double caught(ItemStack stack) {
		Item item = stack.getItem();
		var key = BuiltInRegistries.ITEM.getKey(item);
		String id = key(key.getNamespace(), key.getPath());
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
		return m;
	}

	/** The key an id has in the tables: its path for Minecraft's own, "namespace:path" for another mod's. */
	static String key(String namespace, String path) {
		return namespace.equals("minecraft") ? path : namespace + ":" + path;
	}

	private static String key(Block block) {
		var k = BuiltInRegistries.BLOCK.getKey(block);
		return key(k.getNamespace(), k.getPath());
	}
}
