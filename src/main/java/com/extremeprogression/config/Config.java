package com.extremeprogression.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Every balance value of the mod. The values below are the defaults; {@link ConfigFile} overrides them from
 * {@code config/extremeprogression.json} on startup and writes back missing keys, so the file always lists every option.
 * A key is its field's name in lower case.
 */
public final class Config {
	private Config() {}

	// --- Progression (design §4) -----------------------------------------------------------------

	/** XP that one hour of focused play in a skill earns: the scale every XP value is measured against. */
	public static double XP_PER_HOUR = 10000.0;
	/** Hours of focused play for the first skill to go from level 0 to the maximum level, without the budget factor. */
	public static double HOURS_TO_MAX = 400.0;
	/** The steepness of the curve: hours(level) = HOURS_TO_MAX × (level / MAX_LEVEL)^CURVE_EXPONENT. */
	public static double CURVE_EXPONENT = 2.66;
	public static int MAX_LEVEL = 100;
	/** The budget factor is 1 + (total level / BUDGET_DIVISOR)²: every level costs more as the total rises. */
	public static double BUDGET_DIVISOR = 300.0;
	/** The hard cap on the sum of all skill levels. Mastery does not count toward it. */
	public static int TOTAL_LEVEL_CAP = 400;
	public static int MASTERY_RANKS = 10;
	/** XP per Mastery rank (≈ 40 hours). The budget factor does not apply. */
	public static double MASTERY_XP_PER_RANK = 400000.0;

	// --- Saving (design §10) ---------------------------------------------------------------------

	/** Earlier versions of every player file kept beside it. */
	public static int PLAYER_BACKUPS = 3;

	private static final Map<String, Object> DEFAULTS = snapshot();

	/**
	 * Takes the values a config file holds and fills in the defaults for the rest. Returns true when the file lacked
	 * keys, so it should be written back. Values out of range fall back to the default.
	 */
	public static boolean apply(JsonObject json) {
		reset();
		boolean missing = false;
		for (Field f : fields()) {
			String key = key(f);
			if (!json.has(key)) {
				missing = true;
				continue;
			}
			try {
				set(f, json.get(key));
			} catch (RuntimeException e) {
				// (a value of the wrong kind keeps the default)
			}
		}
		sanitize();
		return missing;
	}

	/** Every option with its current value, as the config file lists them. */
	public static JsonObject toJson() {
		JsonObject json = new JsonObject();
		for (Field f : fields()) {
			try {
				Object v = f.get(null);
				if (v instanceof Number n) {
					json.addProperty(key(f), n);
				} else if (v instanceof Boolean b) {
					json.addProperty(key(f), b);
				} else {
					json.addProperty(key(f), String.valueOf(v));
				}
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
		return json;
	}

	/** Back to the defaults (for tests, and before a file is applied). */
	public static void reset() {
		for (Field f : fields()) {
			try {
				f.set(null, DEFAULTS.get(f.getName()));
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
	}

	private static void sanitize() {
		if (!(XP_PER_HOUR > 0)) {
			XP_PER_HOUR = (double) DEFAULTS.get("XP_PER_HOUR");
		}
		if (!(HOURS_TO_MAX > 0)) {
			HOURS_TO_MAX = (double) DEFAULTS.get("HOURS_TO_MAX");
		}
		if (!(CURVE_EXPONENT > 0)) {
			CURVE_EXPONENT = (double) DEFAULTS.get("CURVE_EXPONENT");
		}
		if (MAX_LEVEL < 1) {
			MAX_LEVEL = (int) DEFAULTS.get("MAX_LEVEL");
		}
		if (!(BUDGET_DIVISOR > 0)) {
			BUDGET_DIVISOR = (double) DEFAULTS.get("BUDGET_DIVISOR");
		}
		if (TOTAL_LEVEL_CAP < 1) {
			TOTAL_LEVEL_CAP = (int) DEFAULTS.get("TOTAL_LEVEL_CAP");
		}
		MASTERY_RANKS = Math.max(0, MASTERY_RANKS);
		if (!(MASTERY_XP_PER_RANK > 0)) {
			MASTERY_XP_PER_RANK = (double) DEFAULTS.get("MASTERY_XP_PER_RANK");
		}
		PLAYER_BACKUPS = Math.max(0, PLAYER_BACKUPS);
	}

	private static void set(Field f, JsonElement value) throws RuntimeException {
		try {
			Class<?> t = f.getType();
			if (t == double.class) {
				f.setDouble(null, value.getAsDouble());
			} else if (t == int.class) {
				f.setInt(null, value.getAsInt());
			} else if (t == boolean.class) {
				f.setBoolean(null, value.getAsBoolean());
			} else if (t == String.class) {
				f.set(null, value.getAsString());
			}
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String key(Field f) {
		return f.getName().toLowerCase(Locale.ROOT);
	}

	private static Field[] fields() {
		return java.util.Arrays.stream(Config.class.getDeclaredFields())
				.filter(f -> Modifier.isPublic(f.getModifiers()) && Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers()))
				.toArray(Field[]::new);
	}

	private static Map<String, Object> snapshot() {
		Map<String, Object> m = new LinkedHashMap<>();
		for (Field f : fields()) {
			try {
				m.put(f.getName(), f.get(null));
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
		return m;
	}
}
