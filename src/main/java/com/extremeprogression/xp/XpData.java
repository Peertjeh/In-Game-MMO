package com.extremeprogression.xp;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.extremeprogression.skill.Skill;

/**
 * The XP tables as data files (design §11): {@code config/extremeprogression/xp/<skill>.json}, one per skill, with
 * Appendix A's values as the defaults. A file holds the skill's single values by name and its tables of ids; ids of
 * Minecraft go without their namespace, another mod's as {@code "namespace:path"}. Missing entries are written back, so
 * every file always lists every value.
 */
public final class XpData {
	private XpData() {}

	/** One skill's file: its single values (fields of {@link XpTables}) and its tables. */
	private record Layout(List<String> values, Map<String, Map<String, Double>> tables) {}

	private static final Map<Skill, Layout> LAYOUT = new EnumMap<>(Skill.class);
	private static final Map<String, Double> VALUE_DEFAULTS = new HashMap<>();
	/** (by identity: the tables change, and two may hold the same) */
	private static final Map<Map<String, Double>, Map<String, Double>> TABLE_DEFAULTS = new java.util.IdentityHashMap<>();
	/** The structures by type ("village") with their XP, as the Exploration file lists them. */
	private static final Map<String, Double> STRUCTURE_TYPES = new TreeMap<>();
	private static final Map<String, XpTables.StructureType> STRUCTURE_DEFAULTS = new HashMap<>();

	private static void layout(Skill skill, List<String> values, Object... tables) {
		Map<String, Map<String, Double>> t = new LinkedHashMap<>();
		for (int i = 0; i < tables.length; i += 2) {
			@SuppressWarnings("unchecked")
			Map<String, Double> m = (Map<String, Double>) tables[i + 1];
			t.put((String) tables[i], m);
		}
		LAYOUT.put(skill, new Layout(values, t));
	}

	static {
		layout(Skill.MELEE, List.of("MELEE_PER_DAMAGE", "DANGER_UNLISTED_HOSTILE", "DANGER_PLAYER"), "danger", XpTables.DANGER);
		layout(Skill.RANGED, List.of("RANGED_PER_DAMAGE", "RANGED_FREE_DISTANCE", "RANGED_BONUS_PER_BLOCK", "RANGED_MAX_BONUS"));
		layout(Skill.DEFENSE, List.of("DEFENSE_PER_DAMAGE"));
		layout(Skill.MINING, List.of("TERRACOTTA", "BRUSHING"), "pickaxe", XpTables.PICKAXE, "shovel", XpTables.SHOVEL);
		layout(Skill.FORAGING, List.of("WILD_SWEET_BERRIES", "GLOW_BERRIES", "MUSHROOM", "FLOWER", "RARE_FLOWER", "WILD_COCOA", "WILD_MELON", "WILD_HONEY"),
				"logs", XpTables.LOGS);
		layout(Skill.FARMING, List.of("NETHER_WART", "STEM_FRUIT", "PLANTED_COCOA", "PLANTED_SWEET_BERRIES", "SUGAR_CANE", "CACTUS", "TORCHFLOWER", "PITCHER",
				"BREEDING", "SHEARING", "TAMING_OTHER", "PET_PER_DAMAGE"), "crops", XpTables.CROPS, "taming", XpTables.TAMING);
		layout(Skill.FISHING, List.of("TREASURE_GEAR", "JUNK"), "catches", XpTables.CATCHES);
		layout(Skill.EXPLORATION, List.of("NEW_CHUNK", "NEW_CHUNK_ELYTRA", "REVISITED_CHUNK", "NEW_BIOME", "FALL_PER_DAMAGE", "FIRST_OF_TYPE", "STRUCTURE_OTHER"),
				"structures", STRUCTURE_TYPES);
		layout(Skill.SMITHING, List.of("REPAIR", "MATERIAL_OTHER", "NETHERITE_UPGRADE", "ARMOR_TRIM"), "smelted", XpTables.SMELTED, "material", XpTables.MATERIAL);
		layout(Skill.ARCANA, List.of("POTION_BASE", "POTION_EFFECT", "POTION_STRONGER", "POTION_SPLASH", "POTION_LINGERING", "ENCHANTING_PER_LEVEL",
				"ANVIL_ENCHANTING_PER_LEVEL"));
		STRUCTURE_DEFAULTS.putAll(XpTables.STRUCTURES);
		for (XpTables.StructureType t : XpTables.STRUCTURES.values()) {
			STRUCTURE_TYPES.put(t.name(), t.xp());
		}
		for (Layout l : LAYOUT.values()) {
			for (String v : l.values()) {
				VALUE_DEFAULTS.put(v, get(v));
			}
			for (Map<String, Double> m : l.tables().values()) {
				TABLE_DEFAULTS.put(m, new HashMap<>(m));
			}
		}
	}

	private static Field field(String name) {
		try {
			Field f = XpTables.class.getDeclaredField(name);
			f.setAccessible(true);
			return f;
		} catch (NoSuchFieldException e) {
			throw new IllegalStateException(e);
		}
	}

	private static double get(String name) {
		try {
			return field(name).getDouble(null);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void set(String name, double value) {
		try {
			field(name).setDouble(null, value);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Back to Appendix A. */
	public static void reset() {
		VALUE_DEFAULTS.forEach(XpData::set);
		TABLE_DEFAULTS.forEach((table, defaults) -> {
			table.clear();
			table.putAll(defaults);
		});
		structuresChanged();
	}

	/** The Exploration file lists structures by type; the lookup is by id. */
	private static void structuresChanged() {
		XpTables.STRUCTURES.clear();
		STRUCTURE_DEFAULTS.forEach((id, t) -> XpTables.STRUCTURES.put(id, new XpTables.StructureType(t.name(), STRUCTURE_TYPES.getOrDefault(t.name(), t.xp()))));
	}

	/** An id as the tables hold it: Minecraft's without its namespace. */
	private static String normalize(String id) {
		String k = id.trim().toLowerCase(Locale.ROOT);
		return k.startsWith("minecraft:") ? k.substring("minecraft:".length()) : k;
	}

	/**
	 * Applies one skill's file on top of the defaults: a value of the wrong kind, a negative or a non-finite one keeps the
	 * default. Returns true when the file lacked something, so it should be written back.
	 */
	public static boolean apply(Skill skill, JsonObject json) {
		Layout l = LAYOUT.get(skill);
		boolean missing = false;
		for (String v : l.values()) {
			String key = v.toLowerCase(Locale.ROOT);
			Double read = number(json.get(key));
			if (read == null) {
				missing = true;
			} else {
				set(v, read);
			}
		}
		for (Map.Entry<String, Map<String, Double>> t : l.tables().entrySet()) {
			JsonElement e = json.get(t.getKey());
			if (e == null || !e.isJsonObject()) {
				missing = true;
				continue;
			}
			Map<String, Double> table = t.getValue();
			// A table in the file is the whole table: entries left out are gone (so a value can be removed)
			table.clear();
			for (Map.Entry<String, JsonElement> entry : e.getAsJsonObject().entrySet()) {
				Double read = number(entry.getValue());
				if (read != null) {
					table.put(normalize(entry.getKey()), read);
				}
			}
		}
		if (skill == Skill.EXPLORATION) {
			structuresChanged();
		}
		return missing;
	}

	private static Double number(JsonElement e) {
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		double d = e.getAsDouble();
		return Double.isFinite(d) && d >= 0 ? d : null;
	}

	/** One skill's file as it is now. */
	public static JsonObject toJson(Skill skill) {
		Layout l = LAYOUT.get(skill);
		JsonObject json = new JsonObject();
		for (String v : l.values()) {
			json.addProperty(v.toLowerCase(Locale.ROOT), get(v));
		}
		for (Map.Entry<String, Map<String, Double>> t : l.tables().entrySet()) {
			JsonObject table = new JsonObject();
			new TreeMap<>(t.getValue()).forEach(table::addProperty);
			json.add(t.getKey(), table);
		}
		return json;
	}
}
