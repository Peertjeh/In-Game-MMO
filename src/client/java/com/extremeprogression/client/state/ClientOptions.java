package com.extremeprogression.client.state;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.skill.Skill;

import net.fabricmc.loader.api.FabricLoader;

/**
 * This client's own choices, kept in {@code config/extremeprogression-client.json}: for now the skills tracked beside
 * the hotbar (none at first, in the order they were tracked).
 */
public final class ClientOptions {
	private ClientOptions() {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final List<Skill> TRACKED = new ArrayList<>();

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(ExtremeProgression.MOD_ID + "-client.json");
	}

	public static void load() {
		TRACKED.clear();
		Path f = file();
		if (!Files.exists(f)) {
			return;
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
			if (parsed.isJsonObject() && parsed.getAsJsonObject().get("tracked") instanceof JsonArray list) {
				for (JsonElement e : list) {
					Skill s = e.isJsonPrimitive() ? Skill.byId(e.getAsString()) : null;
					if (s != null && !TRACKED.contains(s)) {
						TRACKED.add(s);
					}
				}
			}
		} catch (Exception e) {
			ExtremeProgression.LOGGER.warn("Could not read {}; nothing is tracked", f, e);
		}
	}

	private static void save() {
		JsonObject json = new JsonObject();
		JsonArray list = new JsonArray();
		for (Skill s : TRACKED) {
			list.add(s.id);
		}
		json.add("tracked", list);
		try {
			Files.writeString(file(), GSON.toJson(json), StandardCharsets.UTF_8);
		} catch (Exception e) {
			ExtremeProgression.LOGGER.warn("Could not write {}", file(), e);
		}
	}

	/** The skills tracked beside the hotbar, in the order they were tracked. */
	public static List<Skill> tracked() {
		return List.copyOf(TRACKED);
	}

	public static boolean tracked(Skill s) {
		return TRACKED.contains(s);
	}

	/** Tracks a skill, or stops tracking it; saved at once. */
	public static void toggle(Skill s) {
		if (!TRACKED.remove(s)) {
			TRACKED.add(s);
		}
		save();
	}
}
