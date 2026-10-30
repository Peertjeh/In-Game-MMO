package com.extremeprogression.synergy;

import java.util.Locale;

import com.extremeprogression.skill.Skill;

/**
 * The hidden synergies (design §2, ✅ round 19): twelve pairs of skills, never two opposing ones. A synergy needs both
 * skills at level 30, and works for a player only once they discovered it themselves, the first time its situation
 * happens in their play.
 */
public enum Synergy {
	/** After a successful shield block, your next melee hit within 2 seconds deals +20%. */
	COUNTERSTRIKE(Skill.MELEE, Skill.DEFENSE),
	/** Melee hits while sprinting deal +10%. */
	CHARGE(Skill.MELEE, Skill.EXPLORATION),
	/** +10% ranged damage on targets at least 8 blocks below you. */
	HIGH_GROUND(Skill.RANGED, Skill.EXPLORATION),
	/** Tipped arrow effects last 50% longer. */
	VENOM_TIP(Skill.RANGED, Skill.ARCANA),
	/** +5% chance of a double output when smelting raw ore. */
	FORGE_OF_THE_DEEP(Skill.MINING, Skill.SMITHING),
	/** Sculk sensors in the Deep Dark notice you 2 blocks later. */
	DEEP_WALKER(Skill.MINING, Skill.EXPLORATION),
	/** Saplings you plant grow 20% faster. */
	GREEN_THUMB(Skill.FORAGING, Skill.FARMING),
	/** Boats you steer go 10% faster. */
	SEAFARER(Skill.FISHING, Skill.EXPLORATION),
	/** Your wolves take 20% less damage while you block. */
	LOYAL_GUARD(Skill.FARMING, Skill.DEFENSE),
	/** Your armor wears 10% slower. */
	TAILORED(Skill.SMITHING, Skill.DEFENSE),
	/** Water Breathing potions last 50% longer. */
	TIDAL_MAGIC(Skill.FISHING, Skill.ARCANA),
	/** Glow berries give 10 seconds of Night Vision. */
	HERB_LORE(Skill.FORAGING, Skill.ARCANA);

	public final Skill first;
	public final Skill second;
	public final String id;

	Synergy(Skill first, Skill second) {
		this.first = first;
		this.second = second;
		this.id = name().toLowerCase(Locale.ROOT);
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
