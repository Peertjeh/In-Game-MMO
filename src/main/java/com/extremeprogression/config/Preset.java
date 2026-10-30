package com.extremeprogression.config;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The presets (design §11, like Extreme Dimensions' {@code Preset}): Hardcore, the owner's choices and the default;
 * Normal, the milder alternative with half the death loss, no layer 3 and 50% faster XP; and Custom, where the file
 * decides everything. Hardcore and Normal set only the options they govern; every other option stays as the file says.
 */
public enum Preset {
	HARDCORE, NORMAL, CUSTOM;

	public final String id = name().toLowerCase(Locale.ROOT);

	/** The options the presets govern, with Normal's values (Hardcore's are the defaults). */
	private static final Map<String, Object> NORMAL_VALUES = new LinkedHashMap<>();

	static {
		NORMAL_VALUES.put("DEATH_BASE", 0.25);
		NORMAL_VALUES.put("DEATH_BASE_PRESSURED", 0.5);
		NORMAL_VALUES.put("DEATH_WHOLE_LOSS", false);
		NORMAL_VALUES.put("XP_MULTIPLIER", 1.5);
	}

	public static Preset of(String id) {
		for (Preset p : values()) {
			if (p.id.equals(id)) {
				return p;
			}
		}
		return null;
	}

	/** The option keys (as in the file) a preset governs. */
	public static java.util.Set<String> governed() {
		java.util.Set<String> out = new java.util.LinkedHashSet<>();
		for (String f : NORMAL_VALUES.keySet()) {
			out.add(f.toLowerCase(Locale.ROOT));
		}
		return out;
	}

	/** The value this preset gives an option (by field name), or null for Custom and for options it does not govern. */
	public Object value(String field) {
		if (this == CUSTOM || !NORMAL_VALUES.containsKey(field)) {
			return null;
		}
		return this == NORMAL ? NORMAL_VALUES.get(field) : Config.defaultOf(field);
	}

	/** Sets the options the configured preset governs. Returns true when one of them was different before. */
	static boolean enforce() {
		Preset p = of(Config.PRESET);
		if (p == null || p == CUSTOM) {
			return false;
		}
		boolean changed = false;
		for (String name : NORMAL_VALUES.keySet()) {
			try {
				Field f = Config.class.getField(name);
				Object want = p.value(name);
				if (!want.equals(f.get(null))) {
					f.set(null, want);
					changed = true;
				}
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}
		return changed;
	}
}
