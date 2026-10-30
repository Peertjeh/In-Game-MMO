package com.extremeprogression;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

/** Common entrypoint: configuration, registries, networking and server events, each added by its own milestone. */
public class ExtremeProgression implements ModInitializer {
	public static final String MOD_ID = "extremeprogression";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		LOGGER.info("Extreme Progression loaded");
	}
}
