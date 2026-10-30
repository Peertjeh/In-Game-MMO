package com.extremeprogression.data;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What a player discovered (design §2, Exploration; §10): visited chunks per dimension as one bitset per region of 32 ×
 * 32 chunks, the biomes they have been in, and the structures they entered. Kept small: a region costs 16 longs, plus
 * 16 more once it is revisited.
 */
public class ExplorationData {
	/** Dimension id → region key ({@code "x,z"}) → its chunks. */
	public Map<String, Map<String, RegionVisits>> chunks = new LinkedHashMap<>();
	/** Biome ids ever visited. */
	public Set<String> biomes = new LinkedHashSet<>();
	/** Structure types (as in the XP table: "village", "stronghold") ever entered, for the first-of-a-type bonus. */
	public Set<String> structureTypes = new LinkedHashSet<>();
	/** Structures entered, each once: {@code "<dimension>|<structure id>|<start chunk x>,<z>"}. */
	public Set<String> structures = new LinkedHashSet<>();

	/** The chunks of one region a player has visited. */
	public static class RegionVisits {
		public long[] seen = new long[16];
		/** The chunks visited again since the region was last left alone for the revisit time; null until then. */
		public long[] revisited = null;
		/** When the player was last in this region (milliseconds since the epoch, real time). */
		public long lastVisit = 0;

		public static int index(int chunkX, int chunkZ) {
			return ((chunkZ & 31) << 5) | (chunkX & 31);
		}

		public static boolean get(long[] bits, int i) {
			return bits != null && (bits[i >>> 6] & (1L << (i & 63))) != 0;
		}

		public static void set(long[] bits, int i) {
			bits[i >>> 6] |= 1L << (i & 63);
		}

		void repair() {
			if (seen == null || seen.length != 16) {
				long[] fixed = new long[16];
				if (seen != null) {
					System.arraycopy(seen, 0, fixed, 0, Math.min(16, seen.length));
				}
				seen = fixed;
			}
			if (revisited != null && revisited.length != 16) {
				revisited = null;
			}
			lastVisit = Math.max(0, lastVisit);
		}
	}

	/** What entering a chunk was. */
	public enum Visit { OLD, NEW, REVISIT }

	/**
	 * Notes that the player entered a chunk. A region the player left alone for the revisit time starts a new round:
	 * each of its chunks counts once more as a revisit.
	 */
	public Visit visit(String dimension, int chunkX, int chunkZ, long now, long revisitAfter) {
		RegionVisits r = chunks.computeIfAbsent(dimension, k -> new LinkedHashMap<>())
				.computeIfAbsent(regionKey(chunkX, chunkZ), k -> new RegionVisits());
		if (r.lastVisit > 0 && now - r.lastVisit >= revisitAfter) {
			r.revisited = new long[16];
		}
		r.lastVisit = now;
		int i = RegionVisits.index(chunkX, chunkZ);
		boolean seen = RegionVisits.get(r.seen, i);
		boolean again = r.revisited != null && !RegionVisits.get(r.revisited, i);
		RegionVisits.set(r.seen, i);
		if (r.revisited != null) {
			RegionVisits.set(r.revisited, i);
		}
		return !seen ? Visit.NEW : again ? Visit.REVISIT : Visit.OLD;
	}

	public static String regionKey(int chunkX, int chunkZ) {
		return (chunkX >> 5) + "," + (chunkZ >> 5);
	}

	ExplorationData repaired() {
		if (chunks == null) {
			chunks = new LinkedHashMap<>();
		}
		chunks.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
		for (Map<String, RegionVisits> regions : chunks.values()) {
			regions.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
			regions.values().forEach(RegionVisits::repair);
		}
		if (biomes == null) {
			biomes = new LinkedHashSet<>();
		}
		if (structureTypes == null) {
			structureTypes = new LinkedHashSet<>();
		}
		if (structures == null) {
			structures = new LinkedHashSet<>();
		}
		biomes.remove(null);
		structureTypes.remove(null);
		structures.remove(null);
		return this;
	}
}
