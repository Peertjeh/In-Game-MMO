package com.extremeprogression.data;

import java.util.ArrayList;
import java.util.List;

/**
 * A player's deaths (design §7, schema 4): the death pressure and the active play counting toward its next drop, death
 * fatigue, and the last deaths with exactly what each cost.
 */
public class DeathData {
	/** +1 a death, −1 an hour of active play without dying. */
	public int pressure = 0;
	/** Ticks of active play since the pressure last dropped (or since the last death). */
	public long activeTicks = 0;
	/** The overworld game time death fatigue ends. */
	public long fatigueUntil = 0;
	/** Every death, ever. */
	public int deaths = 0;
	/** The last deaths, oldest first. */
	public List<Record> history = new ArrayList<>();

	/** One death: when, where, how, and what it cost. */
	public static class Record {
		/** Real time, in milliseconds. */
		public long time;
		public String dimension = "";
		public int x;
		public int y;
		public int z;
		/** The death message, as the server wrote it. */
		public String cause = "";
		/** The death pressure at the moment of death. */
		public int pressure;
		/** The dimension's factor on the loss. */
		public double multiplier = 1;
		public List<Loss> losses = new ArrayList<>();
	}

	/** What one skill lost in a death. */
	public static class Loss {
		public String skill = "";
		/** XP lost within the current step: the Mastery rank's progress at the maximum level, else the level's XP. */
		public double xp;
		/** Whether that XP was Mastery progress. */
		public boolean mastery;
		public int levelBefore;
		public int levelAfter;
		public int ranksBefore;
		public int ranksAfter;

		public boolean any() {
			return xp > 1e-6 || levelAfter < levelBefore || ranksAfter < ranksBefore;
		}
	}

	public DeathData repaired() {
		pressure = Math.max(0, pressure);
		activeTicks = Math.max(0, activeTicks);
		deaths = Math.max(0, deaths);
		if (history == null) {
			history = new ArrayList<>();
		}
		history.removeIf(r -> r == null);
		for (Record r : history) {
			if (r.losses == null) {
				r.losses = new ArrayList<>();
			}
			r.losses.removeIf(l -> l == null);
			if (r.dimension == null) {
				r.dimension = "";
			}
			if (r.cause == null) {
				r.cause = "";
			}
		}
		return this;
	}
}
