package com.extremeprogression.gametest;

import net.fabricmc.api.ModInitializer;

/**
 * Keeps the Legends update's automatic parts (the daily reward and Well Rested at login, Champion spawns, the Skill of
 * the Day) out of the tests that count XP and items; LegendsTests call them directly.
 */
public class TestSetup implements ModInitializer {
	@Override
	public void onInitialize() {
		com.extremeprogression.legends.Legends.quiet = true;
	}
}
