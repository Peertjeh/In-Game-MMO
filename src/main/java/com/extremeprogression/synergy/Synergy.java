package com.extremeprogression.synergy;

import java.util.Locale;

import com.extremeprogression.skill.Skill;

/**
 * The hidden synergies (design §2, ✅ round 19): twelve pairs of skills, never two opposing ones. A synergy needs both
 * skills at level 30, and works for a player only once they discovered it themselves, the first time its situation
 * happens in their play.
 *
 * <p>The six <b>duo synergies</b> (design §16, 1.4.0) are between two party members near each other: the one who acts
 * needs the first skill at 30, the party member the second. They discover it together, and the Chronicle keeps both.
 */
public enum Synergy {
	/** After a successful shield block, your next melee hit within 2 seconds deals +20%. */
	COUNTERSTRIKE(Skill.MELEE, Skill.DEFENSE, 0.20),
	/** Melee hits while sprinting deal +10%. */
	CHARGE(Skill.MELEE, Skill.EXPLORATION, 0.10),
	/** +10% ranged damage on targets at least 8 blocks below you. */
	HIGH_GROUND(Skill.RANGED, Skill.EXPLORATION, 0.10),
	/** Tipped arrow effects last 50% longer. */
	VENOM_TIP(Skill.RANGED, Skill.ARCANA, 0.50),
	/** +5% chance of a double output when smelting raw ore. */
	FORGE_OF_THE_DEEP(Skill.MINING, Skill.SMITHING, 0.05),
	/** Sculk sensors in the Deep Dark notice you 2 blocks later. */
	DEEP_WALKER(Skill.MINING, Skill.EXPLORATION, 2),
	/** Saplings you plant grow 20% faster. */
	GREEN_THUMB(Skill.FORAGING, Skill.FARMING, 0.20),
	/** Boats you steer go 10% faster. */
	SEAFARER(Skill.FISHING, Skill.EXPLORATION, 0.10),
	/** Your wolves take 20% less damage while you block. */
	LOYAL_GUARD(Skill.FARMING, Skill.DEFENSE, 0.20),
	/** Your armor wears 10% slower. */
	TAILORED(Skill.SMITHING, Skill.DEFENSE, 0.10),
	/** Water Breathing potions last 50% longer. */
	TIDAL_MAGIC(Skill.FISHING, Skill.ARCANA, 0.50),
	/** Glow berries give 10 seconds of Night Vision. */
	HERB_LORE(Skill.FORAGING, Skill.ARCANA, 10),

	// --- Duo synergies: your first skill, a party member's second ---
	/** While you block with a shield, a party member within 4 blocks (Melee) takes 15% less damage. */
	SHIELD_WALL(Skill.DEFENSE, Skill.MELEE, 0.15, true),
	/** Your arrows deal +15% to a mob that is after a party member (Defense). */
	SPOTTER(Skill.RANGED, Skill.DEFENSE, 0.15, true),
	/** Your melee hits deal +10% to a mob a party member (Melee) hit within the last 3 seconds. */
	PINCER(Skill.MELEE, Skill.MELEE, 0.10, true),
	/** Good effects you splash on a party member (Defense) last 25% longer. */
	FIELD_MEDIC(Skill.ARCANA, Skill.DEFENSE, 0.25, true),
	/** Your tools and weapons wear 15% slower within 16 blocks of a party member (Smithing). */
	WHETSTONE(Skill.MINING, Skill.SMITHING, 0.15, true),
	/** Food you eat within 8 blocks of a party member (Farming) fills 25% more. */
	HEARTY_MEAL(Skill.EXPLORATION, Skill.FARMING, 0.25, true);

	public final Skill first;
	public final Skill second;
	public final String id;
	/**
	 * Its strength: the share it adds (0.20 for +20%), for Deep Walker the blocks, for Herb Lore the seconds. As above
	 * by default; {@code config/extremeprogression/synergies.json} can change it, or turn the synergy off.
	 */
	public double strength;
	public boolean enabled = true;
	public final double defaultStrength;
	/** A duo synergy: the first skill is the acting player's, the second a party member's near them. */
	public final boolean duo;

	Synergy(Skill first, Skill second, double strength) {
		this(first, second, strength, false);
	}

	Synergy(Skill first, Skill second, double strength, boolean duo) {
		this.first = first;
		this.second = second;
		this.id = name().toLowerCase(Locale.ROOT);
		this.strength = this.defaultStrength = strength;
		this.duo = duo;
	}

	/** The synergies of one kind, in order: alone (false) or together (true). */
	public static java.util.List<Synergy> of(boolean duo) {
		return java.util.Arrays.stream(values()).filter(s -> s.duo == duo).toList();
	}

	public static void reset() {
		for (Synergy s : values()) {
			s.strength = s.defaultStrength;
			s.enabled = true;
		}
	}

	/** Applies {@code synergies.json} on top of the defaults; true when it lacked something. */
	public static boolean apply(com.google.gson.JsonObject json) {
		reset();
		boolean missing = false;
		for (Synergy s : values()) {
			com.google.gson.JsonElement e = json.get(s.id);
			if (e == null || !e.isJsonObject()) {
				missing = true;
				continue;
			}
			com.google.gson.JsonObject o = e.getAsJsonObject();
			com.google.gson.JsonElement on = o.get("enabled");
			if (on != null && on.isJsonPrimitive() && on.getAsJsonPrimitive().isBoolean()) {
				s.enabled = on.getAsBoolean();
			} else {
				missing = true;
			}
			com.google.gson.JsonElement st = o.get("strength");
			if (st != null && st.isJsonPrimitive() && st.getAsJsonPrimitive().isNumber() && Double.isFinite(st.getAsDouble()) && st.getAsDouble() >= 0) {
				s.strength = st.getAsDouble();
			} else {
				missing = true;
			}
		}
		return missing;
	}

	/** The file as it is now, with what each strength means. */
	public static com.google.gson.JsonObject toJson() {
		com.google.gson.JsonObject json = new com.google.gson.JsonObject();
		for (Synergy s : values()) {
			com.google.gson.JsonObject o = new com.google.gson.JsonObject();
			o.addProperty("enabled", s.enabled);
			o.addProperty("strength", s.strength);
			o.addProperty("strength_is", s == DEEP_WALKER ? "blocks" : s == HERB_LORE ? "seconds" : "share (0.2 = 20%)");
			json.add(s.id, o);
		}
		return json;
	}

	public String translationKey() {
		return "synergy.extremeprogression." + id;
	}

	public boolean involves(Skill skill) {
		return first == skill || second == skill;
	}

	public static Synergy byId(String id) {
		for (Synergy s : values()) {
			if (s.id.equals(id)) {
				return s;
			}
		}
		return null;
	}
}
