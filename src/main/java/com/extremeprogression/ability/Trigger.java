package com.extremeprogression.ability;

/**
 * What a player must hold, or look at, for the ability key to reach a specialization's ability (design §6: the right
 * tool in hand, or the right workstation in view). Workstations win over tools, and tools over {@link #ANY}.
 */
public enum Trigger {
	/** A sword, spear or trident, up close. */
	BLADE_WEAPON,
	AXE_OR_MACE,
	EMPTY_HAND,
	BOW_OR_CROSSBOW,
	TRIDENT,
	SHIELD,
	PICKAXE,
	SHOVEL_OR_BRUSH,
	/** An axe while looking at a log (so it does not clash with an axe for fighting). */
	AXE_AT_LOG,
	HOE,
	FOOD,
	FISHING_ROD,
	SPLASH_OR_LINGERING_POTION,
	LOOK_FURNACE,
	LOOK_ANVIL,
	LOOK_SMITHING_TABLE,
	LOOK_BREWING_STAND,
	LOOK_ENCHANTING_TABLE,
	/** Whatever is in hand, when no other ability claims it. */
	ANY;

	/** 0 for workstations (and the axe at a log), 1 for tools, 2 for anything. */
	public int priority() {
		return switch (this) {
			case LOOK_FURNACE, LOOK_ANVIL, LOOK_SMITHING_TABLE, LOOK_BREWING_STAND, LOOK_ENCHANTING_TABLE, AXE_AT_LOG -> 0;
			case ANY -> 2;
			default -> 1;
		};
	}
}
