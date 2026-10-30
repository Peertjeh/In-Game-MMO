package com.extremeprogression.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.config.Config;

/**
 * Every player's progression, one file each in {@code <world>/extremeprogression/players/}. Files are read when a
 * player is first needed and written with the world's own saves (autosave, {@code /save-all}, stop) and when a player
 * leaves, never on every XP gain. Changes are marked dirty by the code that makes them.
 */
public final class PlayerStore {
	private PlayerStore() {}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<UUID, PlayerProgress> LOADED = new HashMap<>();
	private static final Set<UUID> DIRTY = new HashSet<>();
	/** Null while no world is open: progression asked for then is kept in memory only. */
	private static Path dir = null;

	public static void open(Path worldRoot) {
		LOADED.clear();
		DIRTY.clear();
		dir = worldRoot.resolve(ExtremeProgression.MOD_ID).resolve("players");
	}

	public static void close() {
		flush();
		LOADED.clear();
		DIRTY.clear();
		dir = null;
	}

	public static PlayerProgress get(UUID id) {
		PlayerProgress p = LOADED.get(id);
		if (p == null) {
			p = dir == null ? new PlayerProgress() : read(fileOf(id));
			LOADED.put(id, p);
		}
		return p;
	}

	/** Something of this player changed: it is written with the next save. */
	public static void markDirty(UUID id) {
		if (LOADED.containsKey(id)) {
			DIRTY.add(id);
		}
	}

	public static void flush() {
		for (UUID id : new ArrayList<>(DIRTY)) {
			save(id);
		}
	}

	/** A player left: their file is written and let go. */
	public static void unload(UUID id) {
		save(id);
		LOADED.remove(id);
	}

	/** Every player file of the world (those of players who never joined since are not there). */
	public static java.util.List<UUID> known() {
		java.util.Set<UUID> out = new java.util.LinkedHashSet<>(LOADED.keySet());
		if (dir != null && java.nio.file.Files.isDirectory(dir)) {
			try (java.util.stream.Stream<Path> files = java.nio.file.Files.list(dir)) {
				files.forEach(f -> {
					String n = f.getFileName().toString();
					if (n.endsWith(".json")) {
						try {
							out.add(UUID.fromString(n.substring(0, n.length() - 5)));
						} catch (IllegalArgumentException e) {
							// (not a player file)
						}
					}
				});
			} catch (IOException e) {
				ExtremeProgression.LOGGER.error("Could not list the player files", e);
			}
		}
		return new java.util.ArrayList<>(out);
	}

	/** A player's progression to look at: from memory when loaded, else read from the file without loading it. */
	public static PlayerProgress peek(UUID id) {
		PlayerProgress p = LOADED.get(id);
		return p != null ? p : dir == null ? new PlayerProgress() : read(fileOf(id));
	}

	/** Replaces a player's progression (an admin's reset), saved at once. */
	public static void replace(UUID id, PlayerProgress p) {
		LOADED.put(id, p);
		DIRTY.add(id);
		save(id);
	}

	public static boolean loaded(UUID id) {
		return LOADED.containsKey(id);
	}

	public static Path fileOf(UUID id) {
		return dir.resolve(id + ".json");
	}

	/** Writes this player's file now (after a death, so the loss is on disk even if the server stops at once). */
	public static void saveNow(UUID id) {
		save(id);
	}

	private static void save(UUID id) {
		PlayerProgress p = LOADED.get(id);
		if (p == null || dir == null) {
			DIRTY.remove(id);
			return;
		}
		try {
			write(fileOf(id), p);
			DIRTY.remove(id);
		} catch (IOException e) {
			// (it stays dirty, so the next save tries again)
			ExtremeProgression.LOGGER.error("Could not save the progression of {}", id, e);
		}
	}

	/** Reads a player file of any schema, falling back to its backups; a fresh progression when nothing can be read. */
	public static PlayerProgress read(Path file) {
		PlayerProgress p = SafeFiles.readWithFallback(file, Config.PLAYER_BACKUPS, text -> parse(file, text), "player file");
		return p != null ? p : new PlayerProgress();
	}

	public static void write(Path file, PlayerProgress p) throws IOException {
		p.schema = PlayerProgress.SCHEMA;
		SafeFiles.writeAtomically(file, GSON.toJson(p), Config.PLAYER_BACKUPS);
	}

	private static PlayerProgress parse(Path file, String text) {
		JsonElement parsed = JsonParser.parseString(text);
		if (!parsed.isJsonObject()) {
			return null;
		}
		JsonObject json = parsed.getAsJsonObject();
		int schema = Migrations.schemaOf(json);
		if (schema > PlayerProgress.SCHEMA) {
			keepNewer(file, text, schema);
		}
		PlayerProgress p = GSON.fromJson(Migrations.upgrade(json), PlayerProgress.class);
		return p == null ? null : p.filled();
	}

	/**
	 * A file written by a newer version of the mod (the server went back a version): this version would drop what it
	 * does not know on its next save, so the newer file is kept beside it as {@code .schema<n>} first.
	 */
	private static void keepNewer(Path file, String text, int schema) {
		Path copy = file.resolveSibling(file.getFileName() + ".schema" + schema);
		if (Files.exists(copy)) {
			return;
		}
		try {
			Files.writeString(copy, text, StandardCharsets.UTF_8);
			ExtremeProgression.LOGGER.warn("{} was written by a newer version (schema {}); kept a copy as {}", file.getFileName(), schema, copy.getFileName());
		} catch (IOException e) {
			ExtremeProgression.LOGGER.error("Could not keep a copy of {}", file, e);
		}
	}
}
