package com.extremeprogression.skill;

import java.util.Locale;

/**
 * The ten skills, in the order of the Character Sheet. A skill's id is used in saves, commands and translation keys,
 * so it never changes once released.
 */
public enum Skill {
	MELEE(Category.COMBAT),
	RANGED(Category.COMBAT),
	DEFENSE(Category.COMBAT),
	MINING(Category.GATHERING),
	FORAGING(Category.GATHERING),
	FARMING(Category.GATHERING),
	FISHING(Category.GATHERING),
	EXPLORATION(Category.SURVIVAL),
	SMITHING(Category.CRAFTING),
	ARCANA(Category.CRAFTING);

	public final Category category;
	public final String id;

	Skill(Category category) {
		this.category = category;
		this.id = name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "skill.extremeprogression." + id;
	}

	/** The skill with this id, or null when there is none. */
	public static Skill byId(String id) {
		for (Skill s : values()) {
			if (s.id.equals(id)) {
				return s;
			}
		}
		return null;
	}
}
