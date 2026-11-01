package com.extremeprogression.mastery;

import com.extremeprogression.skill.Skill;

/**
 * The Mastery perks (design §4 and §6): two per skill, "a" and "b". At every Mastery rank you choose one of the two,
 * and each rank put in a perk adds 1% to it (design §2's examples, 💡 named and settled in milestone 8).
 */
public enum MasteryPerk {
	/** +1% melee damage against bosses. */
	GIANT_SLAYER(Skill.MELEE, "a"),
	/** +1% melee damage against players. */
	DUELIST(Skill.MELEE, "b"),
	/** +1% ranged damage against flying mobs. */
	SKYSHOT(Skill.RANGED, "a"),
	/** +1% ranged damage against players. */
	MARKSMAN(Skill.RANGED, "b"),
	/** 1% less damage from bosses. */
	TITAN_GUARD(Skill.DEFENSE, "a"),
	/** 1% less damage from players. */
	DUEL_GUARD(Skill.DEFENSE, "b"),
	/** +1% chance of a double ore. */
	MOTHER_LODE(Skill.MINING, "a"),
	/** +1% breaking speed with a pickaxe or shovel. */
	SWIFT_PICK(Skill.MINING, "b"),
	/** +1% chance of a double log. */
	HEARTWOOD(Skill.FORAGING, "a"),
	/** +1% chance of a double wild yield (berries, mushrooms, flowers). */
	WILD_BOUNTY(Skill.FORAGING, "b"),
	/** +1% chance of an extra crop. */
	GOLDEN_HARVEST(Skill.FARMING, "a"),
	/** Your tamed animals deal +1% damage. */
	KINDRED(Skill.FARMING, "b"),
	/** +1% chance that a fish is treasure instead. */
	SEAS_FAVOR(Skill.FISHING, "a"),
	/** Fish bite 1% sooner. */
	EAGER_FISH(Skill.FISHING, "b"),
	/** +1% movement speed. */
	SWIFT_FEET(Skill.EXPLORATION, "a"),
	/** 1% less fall damage. */
	SOFT_LANDING(Skill.EXPLORATION, "b"),
	/** +1% chance of a double output when smelting. */
	RICH_SMELT(Skill.SMITHING, "a"),
	/** Anvil repairs restore 1% more of the durability. */
	FINE_MEND(Skill.SMITHING, "b"),
	/** Potions you brew last 1% longer. */
	LONG_BREW(Skill.ARCANA, "a"),
	/** +1% chance an enchanting offer is rolled as if 1 level higher. */
	BRIGHT_OFFER(Skill.ARCANA, "b"),
	/** Villagers' prices 1% lower. */
	PATRON(Skill.TRADING, "a"),
	/** +1% chance a barter or a wandering trader's trade gives its result twice. */
	GOLDEN_TOUCH(Skill.TRADING, "b");

	public final Skill skill;
	public final String track;
	public final String id;

	MasteryPerk(Skill skill, String track) {
		this.skill = skill;
		this.track = track;
		this.id = name().toLowerCase(java.util.Locale.ROOT);
	}

	public String translationKey() {
		return "mastery.extremeprogression." + id;
	}

	/** The skill's perk on this track ("a" or "b"), or null. */
	public static MasteryPerk of(Skill skill, String track) {
		for (MasteryPerk p : values()) {
			if (p.skill == skill && p.track.equals(track)) {
				return p;
			}
		}
		return null;
	}
}
