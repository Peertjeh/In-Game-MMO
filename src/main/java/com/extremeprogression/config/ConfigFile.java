package com.extremeprogression.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.data.SafeFiles;

import net.fabricmc.loader.api.FabricLoader;

/** {@code config/extremeprogression.json}: read on startup, written back whenever it lacks an option. */
public final class ConfigFile {
	private ConfigFile() {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(ExtremeProgression.MOD_ID + ".json");
	}

	public static void load() {
		Path file = path();
		JsonObject json = new JsonObject();
		boolean readable = true;
		try {
			if (Files.exists(file)) {
				JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
				if (parsed.isJsonObject()) {
					json = parsed.getAsJsonObject();
				} else {
					readable = false;
				}
			}
		} catch (Exception e) {
			readable = false;
			ExtremeProgression.LOGGER.error("Could not read {}, using the defaults", file, e);
		}
		boolean missing = Config.apply(json);
		// A file that could not be read is left for the admin to fix, never overwritten
		if (missing && readable) {
			try {
				SafeFiles.writeAtomically(file, GSON.toJson(Config.toJson()), 0);
			} catch (IOException e) {
				ExtremeProgression.LOGGER.error("Could not write {}", file, e);
			}
		}
	}
}
