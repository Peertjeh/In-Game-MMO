package com.extremeprogression.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The thirty specializations, three per skill (design §2). Milestone 2 shows them; buying their nodes and using their
 * abilities come with milestones 5 and 6.
 */
public enum Specialization {
	BLADE(Skill.MELEE), CRUSHER(Skill.MELEE), BRAWLER(Skill.MELEE),
	SNIPER(Skill.RANGED), SKIRMISHER(Skill.RANGED), HARPOONER(Skill.RANGED),
	BULWARK(Skill.DEFENSE), JUGGERNAUT(Skill.DEFENSE), RESILIENCE(Skill.DEFENSE),
	PROSPECTOR(Skill.MINING), EXCAVATOR(Skill.MINING), TUNNELER(Skill.MINING),
	LUMBERJACK(Skill.FORAGING), GATHERER(Skill.FORAGING), HERBALIST(Skill.FORAGING),
	GROWER(Skill.FARMING), HERDER(Skill.FARMING), BEASTMASTER(Skill.FARMING),
	ANGLER(Skill.FISHING), TREASURE_HUNTER(Skill.FISHING), HOOKMASTER(Skill.FISHING),
	WANDERER(Skill.EXPLORATION), SURVIVALIST(Skill.EXPLORATION), ACROBAT(Skill.EXPLORATION),
	FORGEMASTER(Skill.SMITHING), ARMORER(Skill.SMITHING), TINKERER(Skill.SMITHING),
	ALCHEMIST(Skill.ARCANA), ENCHANTER(Skill.ARCANA), HEXER(Skill.ARCANA);

	public final Skill skill;
	/** The id in saves and translation keys: lower case, never changed once released. */
	public final String id;

	Specialization(Skill skill) {
		this.skill = skill;
		this.id = name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "spec.extremeprogression." + id;
	}

	/**
	 * The specialization of another skill that excludes this one (design §2, mechanism b), or null. The pairs go both
	 * ways.
	 */
	public Specialization conflict() {
		return switch (this) {
			case JUGGERNAUT -> ACROBAT;
			case ACROBAT -> JUGGERNAUT;
			case BRAWLER -> BULWARK;
			case BULWARK -> BRAWLER;
			case SNIPER -> WANDERER;
			case WANDERER -> SNIPER;
			case HERBALIST -> GROWER;
			case GROWER -> HERBALIST;
			case TINKERER -> ENCHANTER;
			case ENCHANTER -> TINKERER;
			default -> null;
		};
	}

	/** The three specializations of a skill, in their order on the Skill Screen. */
	public static List<Specialization> of(Skill skill) {
		List<Specialization> out = new ArrayList<>(3);
		for (Specialization s : values()) {
			if (s.skill == skill) {
				out.add(s);
			}
		}
		return out;
	}
}
