package com.extremeprogression.legends;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a player has of the Legends update (2.0, schema 6): XP boosts from tomes, Well Rested time, the daily streak,
 * the milestones and boss rewards already given, the contracts, the titles earned and the one shown, and the counts
 * titles and contracts look at.
 */
public class LegendsData {
	/** Boosts from tomes read: the strongest one runs, the others wait their turn. */
	public List<Boost> boosts = new ArrayList<>();
	/** Seconds of Well Rested left: earned while offline, spent while earning XP. */
	public int restedSeconds;
	/** When the player was last seen online (real milliseconds), for Well Rested. */
	public long lastSeen;
	/** The daily streak: the last day a reward was given (ISO date), the days in a row, the best run. */
	public String streakDay = "";
	public int streak;
	public int bestStreak;
	/** Level milestones whose tome was given ("mining:25"), so a level lost and won back gives none again. */
	public Set<String> milestones = new LinkedHashSet<>();
	/** The day each boss last gave this player its reward (boss id → ISO date). */
	public Map<String, String> bossDays = new LinkedHashMap<>();
	public ContractBook contracts = new ContractBook();
	/** Titles earned, and the one shown ("" for none). */
	public Set<String> titles = new LinkedHashSet<>();
	public String title = "";
	/** The Oaths sworn, by id (2.2), and when the last one was (real milliseconds; one a week, 2.2.1). */
	public Set<String> oaths = new LinkedHashSet<>();
	public long lastOath;
	/** Counts: champions slain, contracts done, bosses, and the like. */
	public Map<String, Long> stats = new LinkedHashMap<>();

	/** One boost: this many percent more XP for this many seconds of play while earning. */
	public static class Boost {
		public int percent;
		public int seconds;
		/** Where it came from: a tome's id, or "admin". */
		public String source = "";

		public Boost() {}

		public Boost(int percent, int seconds, String source) {
			this.percent = percent;
			this.seconds = seconds;
			this.source = source;
		}
	}

	/** One contract: earn XP in a skill, slay mobs of a kind, or slay champions. */
	public static class Contract {
		/** "xp", "kill" or "champion". */
		public String kind = "";
		/** The skill id (xp) or the entity type id (kill). */
		public String target = "";
		public double goal;
		public double progress;
		/** The reward: XP in this skill, and for a weekly contract a tome. */
		public String rewardSkill = "";
		public double rewardXp;
		public String rewardTome = "";
		public boolean done;

		public double share() {
			return goal <= 0 ? 1 : Math.min(1, progress / goal);
		}
	}

	/** Today's three contracts and this week's one. */
	public static class ContractBook {
		public String day = "";
		public List<Contract> daily = new ArrayList<>();
		public String week = "";
		public Contract weekly;
		/** The day the free reroll was last used. */
		public String rerolled = "";
		/** The day the tome for all three dailies was given. */
		public String allDone = "";
	}

	public long stat(String key) {
		Long v = stats.get(key);
		return v == null ? 0 : v;
	}

	public void add(String key, long amount) {
		stats.merge(key, amount, Long::sum);
	}

	/** Repairs what a damaged or hand-edited file may hold. */
	public LegendsData repaired() {
		if (boosts == null) {
			boosts = new ArrayList<>();
		}
		boosts.removeIf(b -> b == null || b.percent <= 0 || b.seconds <= 0);
		for (Boost b : boosts) {
			if (b.source == null) {
				b.source = "";
			}
		}
		restedSeconds = Math.max(0, restedSeconds);
		if (streakDay == null) {
			streakDay = "";
		}
		streak = Math.max(0, streak);
		bestStreak = Math.max(streak, bestStreak);
		if (milestones == null) {
			milestones = new LinkedHashSet<>();
		}
		milestones.remove(null);
		if (bossDays == null) {
			bossDays = new LinkedHashMap<>();
		}
		bossDays.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
		if (contracts == null) {
			contracts = new ContractBook();
		}
		if (contracts.day == null) {
			contracts.day = "";
		}
		if (contracts.week == null) {
			contracts.week = "";
		}
		if (contracts.rerolled == null) {
			contracts.rerolled = "";
		}
		if (contracts.allDone == null) {
			contracts.allDone = "";
		}
		if (contracts.daily == null) {
			contracts.daily = new ArrayList<>();
		}
		contracts.daily.removeIf(c -> c == null || c.kind == null || c.target == null);
		if (contracts.weekly != null && (contracts.weekly.kind == null || contracts.weekly.target == null)) {
			contracts.weekly = null;
		}
		for (Contract c : contracts.daily) {
			fix(c);
		}
		if (contracts.weekly != null) {
			fix(contracts.weekly);
		}
		if (titles == null) {
			titles = new LinkedHashSet<>();
		}
		titles.remove(null);
		if (title == null || !title.isEmpty() && !titles.contains(title)) {
			title = "";
		}
		if (oaths == null) {
			oaths = new LinkedHashSet<>();
		}
		oaths.remove(null);
		if (stats == null) {
			stats = new LinkedHashMap<>();
		}
		stats.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
		return this;
	}

	private static void fix(Contract c) {
		if (c.rewardSkill == null) {
			c.rewardSkill = "";
		}
		if (c.rewardTome == null) {
			c.rewardTome = "";
		}
		c.goal = Double.isFinite(c.goal) ? Math.max(0, c.goal) : 0;
		c.progress = Double.isFinite(c.progress) ? Math.max(0, c.progress) : 0;
		c.rewardXp = Double.isFinite(c.rewardXp) ? Math.max(0, c.rewardXp) : 0;
	}
}
