package com.extremeprogression.skill;

import java.util.Locale;

/** The five skill categories, each with its accent colour (one of the 16 chat colours, as everywhere in the GUI). */
public enum Category {
	COMBAT(0xFF5555),
	GATHERING(0x55FF55),
	SURVIVAL(0x55FFFF),
	CRAFTING(0xFFAA00),
	/** Other (1.5.0, design round 27): Trading. */
	OTHER(0xFF55FF);

	public final int accent;
	public final String id;

	Category(int accent) {
		this.accent = accent;
		this.id = name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "category.extremeprogression." + id;
	}
}
