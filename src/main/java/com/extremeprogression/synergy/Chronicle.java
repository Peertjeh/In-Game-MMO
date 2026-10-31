package com.extremeprogression.synergy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.SafeFiles;

/**
 * The server's chronicle (design §2 and §10): the first discoverer of every synergy, kept in
 * {@code <world>/extremeprogression/world.json}. Written at once on every first discovery (they are rare), atomically
 * and with backups like the player files.
 */
public final class Chronicle {
	private Chronicle() {}

	/** The first discovery of one synergy on this server. */
	public static class First {
		public String uuid = "";
		public String name = "";
		public long time;
		public String dimension = "";
		public int x;
		public int y;
		public int z;
		/** A duo synergy: the party member who discovered it with them (empty for the others). */
		public String partnerUuid = "";
		public String partnerName = "";

		/** Whether this player was among the first discoverers. */
		public boolean by(String uuid) {
			return uuid.equals(this.uuid) || uuid.equals(partnerUuid);
		}

		/** "Steve", or "Steve & Alex" for a duo synergy. */
		public String names() {
			return partnerName == null || partnerName.isEmpty() ? name : name + " & " + partnerName;
		}
	}

	/** What world.json holds. */
	public static class WorldData {
		public int schema = 1;
		public Map<String, First> firsts = new LinkedHashMap<>();
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static WorldData data = new WorldData();
	private static Path file = null;

	public static void open(Path worldRoot) {
		file = worldRoot.resolve(ExtremeProgression.MOD_ID).resolve("world.json");
		WorldData read = SafeFiles.readWithFallback(file, Config.PLAYER_BACKUPS, text -> GSON.fromJson(text, WorldData.class), "world file");
		data = read == null ? new WorldData() : read;
		if (data.firsts == null) {
			data.firsts = new LinkedHashMap<>();
		}
		data.firsts.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
	}

	public static void close() {
		file = null;
		data = new WorldData();
	}

	/** The first discoverer of a synergy, or null when nobody on this server found it yet. */
	public static First first(Synergy s) {
		return data.firsts.get(s.id);
	}

	public static Map<String, First> all() {
		return data.firsts;
	}

	/** Records a first discovery; false when the synergy already had its first discoverer. */
	public static boolean record(Synergy s, First first) {
		if (data.firsts.containsKey(s.id)) {
			return false;
		}
		data.firsts.put(s.id, first);
		save();
		return true;
	}

	private static void save() {
		if (file == null) {
			return;
		}
		try {
			SafeFiles.writeAtomically(file, GSON.toJson(data), Config.PLAYER_BACKUPS);
		} catch (IOException e) {
			ExtremeProgression.LOGGER.error("Could not save the chronicle", e);
		}
	}

	/** For tests: forgets every first discovery (on disk too). */
	public static void clearForTests() {
		data.firsts.clear();
		save();
	}
}
