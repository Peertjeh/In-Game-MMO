package com.extremeprogression.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One skill of one player, as saved. */
public class SkillData {
	public int level = 0;
	/** XP earned within the current level. */
	public double xp = 0;
	public int masteryRanks = 0;
	/** XP earned toward the next Mastery rank. */
	public double masteryXp = 0;
	/** The chosen specializations by id: the first (level 25), then the second (level 75). Schema 3. */
	public List<String> specs = new ArrayList<>();
	/** Every point bought, as {@code "<specialization>/<node>"}, one entry per rank, in the order bought. */
	public List<String> bought = new ArrayList<>();
	/** Points set aside after levels were lost, the last one set aside at the end; they return by themselves. */
	public List<String> suspended = new ArrayList<>();
	/** When the next respec is allowed (milliseconds since the epoch, real time). */
	public long respecReadyAt = 0;
	/** When each specialization's ability is ready again (the overworld's game time). */
	public Map<String, Long> cooldowns = new LinkedHashMap<>();

	public SkillData copy() {
		SkillData d = new SkillData();
		d.level = level;
		d.xp = xp;
		d.masteryRanks = masteryRanks;
		d.masteryXp = masteryXp;
		d.specs = new ArrayList<>(specs);
		d.bought = new ArrayList<>(bought);
		d.suspended = new ArrayList<>(suspended);
		d.respecReadyAt = respecReadyAt;
		d.cooldowns = new LinkedHashMap<>(cooldowns);
		return d;
	}

	void repair() {
		if (specs == null) {
			specs = new ArrayList<>();
		}
		if (bought == null) {
			bought = new ArrayList<>();
		}
		if (suspended == null) {
			suspended = new ArrayList<>();
		}
		if (cooldowns == null) {
			cooldowns = new LinkedHashMap<>();
		}
		specs.removeIf(s -> s == null);
		bought.removeIf(s -> s == null);
		suspended.removeIf(s -> s == null);
		cooldowns.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
		respecReadyAt = Math.max(0, respecReadyAt);
	}
}
