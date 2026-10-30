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
	/** The levels at which a skill's first and second specialization are chosen (design §6). */
	public static int FIRST_SPECIALIZATION_LEVEL = 25;
	public static int SECOND_SPECIALIZATION_LEVEL = 75;
	/** A respec costs this share of the skill's XP... */
	public static double RESPEC_XP_SHARE = 0.2;
	/** ...and the next one is only possible after this many real days. */
	public static double RESPEC_DAYS = 7.0;

	// --- Earning XP (design §3, Appendix A) ------------------------------------------------------

	/** Multiplies every XP gain, after all other rules. */
	public static double XP_MULTIPLIER = 1.0;
	/** The share of a log's XP that a tree a player planted gives (design §2, Foraging). */
	public static double PLANTED_TREE_FACTOR = 0.25;
	/** The share of the XP that crops, plants and trees grown with bonemeal give (design §2, Farming: none). */
	public static double BONEMEAL_FACTOR = 0.0;

	// --- Anti-exploit (design §12) ---------------------------------------------------------------

	/** No XP after this many minutes without looking around, moving by yourself or interacting. */
	public static double AFK_MINUTES = 5.0;
	/** Above this much XP in one skill within the window, XP shrinks step by step (3× the normal pace). */
	public static double DIMINISHING_THRESHOLD = 5000.0;
	public static double DIMINISHING_WINDOW_MINUTES = 10.0;
	/** The least share of its XP a gain keeps under diminishing returns. */
	public static double DIMINISHING_FLOOR = 0.05;
	/** Catches within this many blocks of each other count as the same spot. */
	public static double OVERFISHED_RADIUS = 3.0;
	/** After this many catches in one spot the spot is overfished, and its catches give OVERFISHED_FACTOR. */
	public static int OVERFISHED_CATCHES = 10;
	public static double OVERFISHED_FACTOR = 0.25;
	/** After this many catches in one spot, catches give OVERFISHED_LATE_FACTOR (never zero). */
	public static int OVERFISHED_LATE_CATCHES = 20;
	public static double OVERFISHED_LATE_FACTOR = 0.10;

	/** Melee: only hits with the attack bar at least this full give XP (design §2). */
	public static double MELEE_FULL_CHARGE = 0.9;
	/** Defense: XP only if you attacked, interacted or moved yourself within this many seconds. */
	public static double DEFENSE_ACTIVE_SECONDS = 10.0;
	/** Defense: one attacker gives XP at most this many times... */
	public static int DEFENSE_HITS_PER_ATTACKER = 6;
	/** ...and again only after this many seconds without hits from it. */
	public static double DEFENSE_ATTACKER_RESET_SECONDS = 60.0;
	/** PvP (a): damage to the same player gives XP for at most this much damage per hour (3 lives). */
	public static double PVP_DAMAGE_PER_HOUR = 60.0;
	/** PvP (b): no XP from players whose total level is below this share of yours. */
	public static double PVP_MIN_LEVEL_SHARE = 0.5;
	/** PvP (c): no XP from players on the same IP address. Off by default: it also hits siblings and housemates. */
	public static boolean PVP_BLOCK_SAME_IP = false;
	/** Exploration: a chunk left alone this many real days gives XP again. */
	public static double REVISIT_DAYS = 30.0;
	/** Exploration: a fall within this many blocks of an earlier one... */
	public static double FALL_SPOT_RADIUS = 3.0;
	/** ...within this many minutes gives no XP (the spot is blocked for a while). */
	public static double FALL_SPOT_MINUTES = 10.0;

	// --- Saving (design §10) ---------------------------------------------------------------------

	/** Earlier versions of every player file kept beside it. */
	public static int PLAYER_BACKUPS = 3;

	// --- Death (design §7) ---------------------------------------------------------------------
	/** The share of every skill's current step a death takes at death pressure 0 (for the highest skill). */
	public static double DEATH_BASE = 0.5;
	/** That share from death pressure 1. */
	public static double DEATH_BASE_PRESSURED = 1.0;
	/** From this death pressure a death also takes a whole level or Mastery rank of the highest skill (layer 3). */
	public static int DEATH_WHOLE_LOSS_PRESSURE = 2;
	/** The dimension factors on the loss (Overworld and others ×1). */
	public static double DEATH_NETHER = 1.5;
	public static double DEATH_END = 2.0;
	/** Extreme Dimensions' Seam, only while that mod is installed. */
	public static double DEATH_SEAM = 2.5;
	/** Death fatigue: for this many minutes after a death, passive bonuses work at this power. */
	public static double DEATH_FATIGUE_MINUTES = 10;
	public static double DEATH_FATIGUE_POWER = 0.5;
	/** Death pressure drops by 1 after this many minutes of active play without dying. */
	public static double DEATH_PRESSURE_DECAY_MINUTES = 60;
	/** How many deaths the Death Register keeps per player. */
	public static int DEATH_HISTORY = 50;

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
		if (FIRST_SPECIALIZATION_LEVEL < 1 || SECOND_SPECIALIZATION_LEVEL <= FIRST_SPECIALIZATION_LEVEL) {
			FIRST_SPECIALIZATION_LEVEL = (int) DEFAULTS.get("FIRST_SPECIALIZATION_LEVEL");
			SECOND_SPECIALIZATION_LEVEL = (int) DEFAULTS.get("SECOND_SPECIALIZATION_LEVEL");
		}
		if (!(MASTERY_XP_PER_RANK > 0)) {
			MASTERY_XP_PER_RANK = (double) DEFAULTS.get("MASTERY_XP_PER_RANK");
		}
		PLAYER_BACKUPS = Math.max(0, PLAYER_BACKUPS);
		RESPEC_XP_SHARE = share(RESPEC_XP_SHARE, "RESPEC_XP_SHARE");
		DEATH_BASE = share(DEATH_BASE, "DEATH_BASE");
		DEATH_BASE_PRESSURED = share(DEATH_BASE_PRESSURED, "DEATH_BASE_PRESSURED");
		DEATH_WHOLE_LOSS_PRESSURE = Math.max(1, DEATH_WHOLE_LOSS_PRESSURE);
		DEATH_NETHER = nonNegative(DEATH_NETHER, "DEATH_NETHER");
		DEATH_END = nonNegative(DEATH_END, "DEATH_END");
		DEATH_SEAM = nonNegative(DEATH_SEAM, "DEATH_SEAM");
		DEATH_FATIGUE_MINUTES = nonNegative(DEATH_FATIGUE_MINUTES, "DEATH_FATIGUE_MINUTES");
		DEATH_FATIGUE_POWER = share(DEATH_FATIGUE_POWER, "DEATH_FATIGUE_POWER");
		DEATH_PRESSURE_DECAY_MINUTES = nonNegative(DEATH_PRESSURE_DECAY_MINUTES, "DEATH_PRESSURE_DECAY_MINUTES");
		DEATH_HISTORY = Math.max(0, DEATH_HISTORY);
		RESPEC_DAYS = nonNegative(RESPEC_DAYS, "RESPEC_DAYS");
		XP_MULTIPLIER = nonNegative(XP_MULTIPLIER, "XP_MULTIPLIER");
		PLANTED_TREE_FACTOR = share(PLANTED_TREE_FACTOR, "PLANTED_TREE_FACTOR");
		BONEMEAL_FACTOR = share(BONEMEAL_FACTOR, "BONEMEAL_FACTOR");
		if (!(AFK_MINUTES > 0)) {
			AFK_MINUTES = (double) DEFAULTS.get("AFK_MINUTES");
		}
		if (!(DIMINISHING_THRESHOLD > 0)) {
			DIMINISHING_THRESHOLD = (double) DEFAULTS.get("DIMINISHING_THRESHOLD");
		}
		if (!(DIMINISHING_WINDOW_MINUTES > 0)) {
			DIMINISHING_WINDOW_MINUTES = (double) DEFAULTS.get("DIMINISHING_WINDOW_MINUTES");
		}
		DIMINISHING_FLOOR = share(DIMINISHING_FLOOR, "DIMINISHING_FLOOR");
		OVERFISHED_RADIUS = nonNegative(OVERFISHED_RADIUS, "OVERFISHED_RADIUS");
		OVERFISHED_CATCHES = Math.max(1, OVERFISHED_CATCHES);
		OVERFISHED_LATE_CATCHES = Math.max(OVERFISHED_CATCHES, OVERFISHED_LATE_CATCHES);
		OVERFISHED_FACTOR = share(OVERFISHED_FACTOR, "OVERFISHED_FACTOR");
		OVERFISHED_LATE_FACTOR = share(OVERFISHED_LATE_FACTOR, "OVERFISHED_LATE_FACTOR");
		MELEE_FULL_CHARGE = share(MELEE_FULL_CHARGE, "MELEE_FULL_CHARGE");
		DEFENSE_ACTIVE_SECONDS = nonNegative(DEFENSE_ACTIVE_SECONDS, "DEFENSE_ACTIVE_SECONDS");
		DEFENSE_HITS_PER_ATTACKER = Math.max(1, DEFENSE_HITS_PER_ATTACKER);
		DEFENSE_ATTACKER_RESET_SECONDS = nonNegative(DEFENSE_ATTACKER_RESET_SECONDS, "DEFENSE_ATTACKER_RESET_SECONDS");
		PVP_DAMAGE_PER_HOUR = nonNegative(PVP_DAMAGE_PER_HOUR, "PVP_DAMAGE_PER_HOUR");
		PVP_MIN_LEVEL_SHARE = share(PVP_MIN_LEVEL_SHARE, "PVP_MIN_LEVEL_SHARE");
		if (!(REVISIT_DAYS > 0)) {
			REVISIT_DAYS = (double) DEFAULTS.get("REVISIT_DAYS");
		}
		FALL_SPOT_RADIUS = nonNegative(FALL_SPOT_RADIUS, "FALL_SPOT_RADIUS");
		FALL_SPOT_MINUTES = nonNegative(FALL_SPOT_MINUTES, "FALL_SPOT_MINUTES");
	}

	/** A share from 0 to 1; anything else falls back to the default. */
	private static double share(double value, String field) {
		return value >= 0 && value <= 1 ? value : (double) DEFAULTS.get(field);
	}

	private static double nonNegative(double value, String field) {
		return value >= 0 && Double.isFinite(value) ? value : (double) DEFAULTS.get(field);
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
