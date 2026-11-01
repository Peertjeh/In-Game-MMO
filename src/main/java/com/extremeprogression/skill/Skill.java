package com.extremeprogression.skill;

import java.util.Locale;

/**
 * The eleven skills, in the order of the Character Sheet (Trading, 1.5.0, last). A skill's id is used in saves,
 * commands and translation keys, so it never changes once released.
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
	ARCANA(Category.CRAFTING),
	TRADING(Category.OTHER);

	public final Category category;
	public final String id;

	Skill(Category category) {
		this.category = category;
		this.id = name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "skill.extremeprogression." + id;
	}

	/** The opposing skill (design §2, mechanism c), or null. The pairs go both ways. */
	public Skill opposite() {
		return switch (this) {
			case MELEE -> RANGED;
			case RANGED -> MELEE;
			case DEFENSE -> EXPLORATION;
			case EXPLORATION -> DEFENSE;
			case SMITHING -> ARCANA;
			case ARCANA -> SMITHING;
			default -> null;
		};
	}

	/** The levels at which the trunk's four bonuses unlock (design §6). */
	public static final int[] TRUNK_LEVELS = {5, 10, 15, 20};

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
