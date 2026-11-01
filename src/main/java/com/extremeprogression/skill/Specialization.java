package com.extremeprogression.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.extremeprogression.ability.Trigger;

/**
 * The thirty-three specializations, three per skill (design §2; Trading's since 1.5.0), each with one active ability. Their trees follow the
 * shared {@link com.extremeprogression.tree.NodeSlot} layout.
 */
public enum Specialization {
	BLADE(Skill.MELEE, 4, Trigger.BLADE_WEAPON), CRUSHER(Skill.MELEE, 5, Trigger.AXE_OR_MACE), BRAWLER(Skill.MELEE, 4, Trigger.EMPTY_HAND),
	SNIPER(Skill.RANGED, 4, Trigger.BOW_OR_CROSSBOW), SKIRMISHER(Skill.RANGED, 4, Trigger.BOW_OR_CROSSBOW), HARPOONER(Skill.RANGED, 4, Trigger.TRIDENT),
	BULWARK(Skill.DEFENSE, 4, Trigger.SHIELD), JUGGERNAUT(Skill.DEFENSE, 5, Trigger.ANY), RESILIENCE(Skill.DEFENSE, 5, Trigger.ANY),
	PROSPECTOR(Skill.MINING, 5, Trigger.PICKAXE), EXCAVATOR(Skill.MINING, 4, Trigger.SHOVEL_OR_BRUSH), TUNNELER(Skill.MINING, 4, Trigger.PICKAXE),
	LUMBERJACK(Skill.FORAGING, 4, Trigger.AXE_AT_LOG), GATHERER(Skill.FORAGING, 5, Trigger.ANY), HERBALIST(Skill.FORAGING, 5, Trigger.FOOD),
	GROWER(Skill.FARMING, 4, Trigger.HOE), HERDER(Skill.FARMING, 4, Trigger.ANY), BEASTMASTER(Skill.FARMING, 5, Trigger.ANY),
	ANGLER(Skill.FISHING, 4, Trigger.FISHING_ROD), TREASURE_HUNTER(Skill.FISHING, 5, Trigger.FISHING_ROD), HOOKMASTER(Skill.FISHING, 4, Trigger.FISHING_ROD),
	WANDERER(Skill.EXPLORATION, 4, Trigger.ANY), SURVIVALIST(Skill.EXPLORATION, 5, Trigger.ANY), ACROBAT(Skill.EXPLORATION, 4, Trigger.ANY),
	FORGEMASTER(Skill.SMITHING, 5, Trigger.LOOK_FURNACE), ARMORER(Skill.SMITHING, 5, Trigger.LOOK_ANVIL), TINKERER(Skill.SMITHING, 5, Trigger.LOOK_SMITHING_TABLE),
	ALCHEMIST(Skill.ARCANA, 5, Trigger.LOOK_BREWING_STAND), ENCHANTER(Skill.ARCANA, 5, Trigger.LOOK_ENCHANTING_TABLE),
	HEXER(Skill.ARCANA, 4, Trigger.SPLASH_OR_LINGERING_POTION),
	MERCHANT(Skill.TRADING, 5, Trigger.LOOK_VILLAGER), QUARTERMASTER(Skill.TRADING, 5, Trigger.LOOK_VILLAGER),
	GOLD_TONGUE(Skill.TRADING, 5, Trigger.GOLD_OR_PIGLIN);

	public final Skill skill;
	/** The id in saves and translation keys: lower case, never changed once released. */
	public final String id;
	/** The cooldown of its active ability at rank I (design §2), in minutes. */
	public final int cooldownMinutes;
	/** What the ability key needs in hand or in view to reach this ability. */
	public final Trigger trigger;

	Specialization(Skill skill, int cooldownMinutes, Trigger trigger) {
		this.skill = skill;
		this.id = name().toLowerCase(Locale.ROOT);
		this.cooldownMinutes = cooldownMinutes;
		this.trigger = trigger;
	}

	public static Specialization byId(String id) {
		for (Specialization s : values()) {
			if (s.id.equals(id)) {
				return s;
			}
		}
		return null;
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
