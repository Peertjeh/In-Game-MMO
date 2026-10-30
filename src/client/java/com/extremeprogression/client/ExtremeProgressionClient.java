package com.extremeprogression.client;

import com.extremeprogression.ExtremeProgression;

import net.fabricmc.api.ClientModInitializer;

/** Client entrypoint: the screens, the HUD notices, the hotkeys and the inventory button, each added by its own milestone. */
public class ExtremeProgressionClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ExtremeProgression.LOGGER.info("Extreme Progression client loaded");
	}
}
