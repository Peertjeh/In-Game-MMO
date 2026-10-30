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
import com.extremeprogression.skill.Skill;
import com.extremeprogression.synergy.Synergy;
import com.extremeprogression.xp.XpData;

import net.fabricmc.loader.api.FabricLoader;

/**
 * The data files beside the config (design §11), in {@code config/extremeprogression/}: {@code xp/<skill>.json} (the XP
 * tables), {@code skills.json} (skills on or off, their XP multipliers, ability cooldowns, hard requirements) and
 * {@code synergies.json}. Read on startup and on {@code /skills reload}; a file that lacks something is written back
 * complete, one that cannot be read is left for the admin to fix and its defaults are used.
 */
public final class DataFiles {
	private DataFiles() {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve(ExtremeProgression.MOD_ID);
	}

	/** Every data file back to its defaults (also what {@link Config#reset} does). */
	public static void reset() {
		XpData.reset();
		SkillSettings.reset();
		Synergy.reset();
	}

	public static void load() {
		Path dir = dir();
		for (Skill s : Skill.values()) {
			Path file = dir.resolve("xp").resolve(s.id + ".json");
			JsonObject json = read(file);
			if (json != null && XpData.apply(s, json)) {
				write(file, XpData.toJson(s));
			}
		}
		Path skills = dir.resolve("skills.json");
		JsonObject json = read(skills);
		if (json != null && SkillSettings.apply(json)) {
			write(skills, SkillSettings.toJson());
		}
		Path synergies = dir.resolve("synergies.json");
		json = read(synergies);
		if (json != null && Synergy.apply(json)) {
			write(synergies, Synergy.toJson());
		}
	}

	/** A file's contents: an empty object when it does not exist yet, null when it cannot be read. */
	static JsonObject read(Path file) {
		if (!Files.exists(file)) {
			return new JsonObject();
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (parsed.isJsonObject()) {
				return parsed.getAsJsonObject();
			}
		} catch (Exception e) {
			ExtremeProgression.LOGGER.error("Could not read {}, using its defaults", file, e);
			return null;
		}
		ExtremeProgression.LOGGER.error("{} is not a JSON object, using its defaults", file);
		return null;
	}

	static void write(Path file, JsonObject json) {
		try {
			Files.createDirectories(file.getParent());
			SafeFiles.writeAtomically(file, GSON.toJson(json), 0);
		} catch (IOException e) {
			ExtremeProgression.LOGGER.error("Could not write {}", file, e);
		}
	}
}
