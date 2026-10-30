package com.extremeprogression.data;

/** One skill of one player, as saved. */
public class SkillData {
	public int level = 0;
	/** XP earned within the current level. */
	public double xp = 0;
	public int masteryRanks = 0;
	/** XP earned toward the next Mastery rank. */
	public double masteryXp = 0;

	public SkillData copy() {
		SkillData d = new SkillData();
		d.level = level;
		d.xp = xp;
		d.masteryRanks = masteryRanks;
		d.masteryXp = masteryXp;
		return d;
	}
}
