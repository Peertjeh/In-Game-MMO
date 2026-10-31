package com.extremeprogression.data;

import java.util.LinkedHashMap;
import java.util.Map;

import com.extremeprogression.skill.Skill;

/**
 * Everything one player has earned, as saved in {@code <world>/extremeprogression/players/<uuid>.json}. Later milestones
 * add fields; each addition raises {@link #SCHEMA} and gets a step in {@link Migrations}.
 */
public class PlayerProgress {
	/** The schema this version writes. */
	public static final int SCHEMA = 5;

	public int schema = SCHEMA;
	/** The player's name when last seen (for leaderboards and admins while they are offline). */
	public String name = "";
	/**
	 * Per skill id. Ids this version does not know (a skill from a newer version, or one turned off) are kept as they
	 * are, never dropped.
	 */
	public Map<String, SkillData> skills = new LinkedHashMap<>();
	/** What the player discovered (schema 2). */
	public ExplorationData exploration = new ExplorationData();
	/** Death pressure, fatigue and the deaths so far (schema 4). */
	public DeathData death = new DeathData();
	/** The synergies this player discovered, by id: when and where (schema 5). */
	public Map<String, Discovery> synergies = new LinkedHashMap<>();

	/** Where and when something was discovered. */
	public static class Discovery {
		public long time;
		public String dimension = "";
		public int x;
		public int y;
		public int z;
		/** A duo synergy: the party member it was discovered with (empty for the others). */
		public String with = "";
	}

	public SkillData get(Skill skill) {
		return skills.computeIfAbsent(skill.id, k -> new SkillData());
	}

	/** The sum of all skill levels: what the budget factor and the cap count. */
	public int totalLevel() {
		int total = 0;
		for (Skill s : Skill.values()) {
			SkillData d = skills.get(s.id);
			if (d != null) {
				total += d.level;
			}
		}
		return total;
	}

	public int totalMasteryRanks() {
		int total = 0;
		for (Skill s : Skill.values()) {
			SkillData d = skills.get(s.id);
			if (d != null) {
				total += d.masteryRanks;
			}
		}
		return total;
	}

	/**
	 * Repairs what a damaged or hand-edited file may hold: no missing parts, no negative or non-numeric values. Values
	 * above the configured maximums are kept as they are: an admin who lowers a maximum and raises it again later must
	 * not have cost anyone their progress.
	 */
	public PlayerProgress filled() {
		if (name == null) {
			name = "";
		}
		if (skills == null) {
			skills = new LinkedHashMap<>();
		}
		skills.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
		for (SkillData d : skills.values()) {
			d.level = Math.max(0, d.level);
			d.xp = finite(d.xp);
			d.masteryRanks = Math.max(0, d.masteryRanks);
			d.masteryXp = finite(d.masteryXp);
			d.repair();
		}
		exploration = (exploration == null ? new ExplorationData() : exploration).repaired();
		death = (death == null ? new DeathData() : death).repaired();
		if (synergies == null) {
			synergies = new LinkedHashMap<>();
		}
		synergies.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
		for (Discovery d : synergies.values()) {
			if (d.dimension == null) {
				d.dimension = "";
			}
		}
		return this;
	}

	private static double finite(double v) {
		return Double.isFinite(v) && v > 0 ? v : 0;
	}
}
