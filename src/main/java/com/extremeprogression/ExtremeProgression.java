package com.extremeprogression;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.extremeprogression.config.ConfigFile;
import com.extremeprogression.network.Sync;
import com.extremeprogression.server.ServerEvents;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

/** Common entrypoint: configuration, networking and server events. */
public class ExtremeProgression implements ModInitializer {
	public static final String MOD_ID = "extremeprogression";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ConfigFile.load();
		com.extremeprogression.config.DataFiles.load();
		Sync.register();
		ServerEvents.register();
	}
}
