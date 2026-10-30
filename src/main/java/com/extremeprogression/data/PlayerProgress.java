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
	public static final int SCHEMA = 3;

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
		return this;
	}

	private static double finite(double v) {
		return Double.isFinite(v) && v > 0 ? v : 0;
	}
}
