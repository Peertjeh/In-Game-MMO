package com.extremeprogression.config;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.extremeprogression.skill.HardRequirement;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.skill.Specialization;

/**
 * {@code config/extremeprogression/skills.json} (design §11): which skills earn XP and how fast, every ability's
 * cooldown, and the skill and level of every hard requirement. A disabled skill earns no XP; what it already reached
 * keeps working.
 */
public final class SkillSettings {
	private SkillSettings() {}

	private static final Map<Skill, Boolean> ENABLED = new EnumMap<>(Skill.class);
	private static final Map<Skill, Double> MULTIPLIER = new EnumMap<>(Skill.class);
	private static final Map<Specialization, Integer> COOLDOWN = new EnumMap<>(Specialization.class);

	public static boolean enabled(Skill s) {
		return ENABLED.getOrDefault(s, true);
	}

	public static double multiplier(Skill s) {
		return MULTIPLIER.getOrDefault(s, 1.0);
	}

	/** The cooldown of a specialization's ability in minutes (§2's value unless the file says otherwise). */
	public static int cooldownMinutes(Specialization spec) {
		return COOLDOWN.getOrDefault(spec, spec.cooldownMinutes);
	}

	public static void reset() {
		ENABLED.clear();
		MULTIPLIER.clear();
		COOLDOWN.clear();
		HardRequirement.reset();
	}

	/** Applies the file on top of the defaults; true when it lacked something. */
	public static boolean apply(JsonObject json) {
		reset();
		boolean missing = false;
		JsonObject skills = object(json, "skills");
		missing |= skills == null;
		for (Skill s : Skill.values()) {
			JsonObject o = skills == null ? null : object(skills, s.id);
			if (o == null) {
				missing = true;
				continue;
			}
			JsonElement e = o.get("enabled");
			if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
				ENABLED.put(s, e.getAsBoolean());
			} else {
				missing = true;
			}
			Double m = number(o.get("xp_multiplier"));
			if (m != null) {
				MULTIPLIER.put(s, m);
			} else {
				missing = true;
			}
		}
		JsonObject abilities = object(json, "abilities");
		missing |= abilities == null;
		for (Specialization spec : Specialization.values()) {
			JsonObject o = abilities == null ? null : object(abilities, spec.id);
			Double m = o == null ? null : number(o.get("cooldown_minutes"));
			if (m != null) {
				COOLDOWN.put(spec, (int) Math.round(m));
			} else {
				missing = true;
			}
		}
		JsonObject requirements = object(json, "requirements");
		missing |= requirements == null;
		for (HardRequirement r : HardRequirement.values()) {
			JsonObject o = requirements == null ? null : object(requirements, r.name().toLowerCase(Locale.ROOT));
			Skill skill = o == null || o.get("skill") == null ? null : Skill.byId(o.get("skill").getAsString());
			Double level = o == null ? null : number(o.get("level"));
			if (skill != null && level != null) {
				r.set(skill, (int) Math.round(level));
			} else {
				missing = true;
			}
		}
		return missing;
	}

	public static JsonObject toJson() {
		JsonObject json = new JsonObject();
		JsonObject skills = new JsonObject();
		for (Skill s : Skill.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("enabled", enabled(s));
			o.addProperty("xp_multiplier", multiplier(s));
			skills.add(s.id, o);
		}
		json.add("skills", skills);
		JsonObject abilities = new JsonObject();
		for (Specialization spec : Specialization.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("cooldown_minutes", cooldownMinutes(spec));
			abilities.add(spec.id, o);
		}
		json.add("abilities", abilities);
		JsonObject requirements = new JsonObject();
		for (HardRequirement r : HardRequirement.values()) {
			JsonObject o = new JsonObject();
			o.addProperty("skill", r.skill.id);
			o.addProperty("level", r.level);
			requirements.add(r.name().toLowerCase(Locale.ROOT), o);
		}
		json.add("requirements", requirements);
		return json;
	}

	static JsonObject object(JsonObject json, String key) {
		JsonElement e = json.get(key);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	static Double number(JsonElement e) {
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		double d = e.getAsDouble();
		return Double.isFinite(d) && d >= 0 ? d : null;
	}
}
