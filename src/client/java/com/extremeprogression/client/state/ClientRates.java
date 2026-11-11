package com.extremeprogression.client.state;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.extremeprogression.network.ProgressPayload;
import com.extremeprogression.skill.Skill;

/**
 * How fast each skill grows right now (1.8.0, the owner's wish): the XP the client saw it earn over the last 10 minutes,
 * as XP an hour, and the time the next level (or Mastery rank) takes at that pace. Worked out on the client from the
 * progression the server sends, so it starts anew when you join.
 */
public final class ClientRates {
	private ClientRates() {}

	/** The window the pace is taken over, and the least time it counts (a first gain is not an hour's pace). */
	static final long WINDOW_MS = 10 * 60_000L, LEAST_MS = 2 * 60_000L;

	/** The most XP one update can bring in play; more is an operator's command (or a reset) and is not counted. */
	static final double MOST_AT_ONCE = 20_000;

	private record Gain(long at, double xp) {}

	private record Seen(int level, double xp, double need, int ranks, double masteryXp) {}

	private static final Map<Skill, Deque<Gain>> GAINS = new EnumMap<>(Skill.class);
	private static final Map<Skill, Seen> SEEN = new EnumMap<>(Skill.class);

	/** A new progression from the server: every skill's XP since the last one counts as earned now. */
	public static void observe(ProgressPayload payload, long now) {
		for (ProgressPayload.Entry e : payload.skills()) {
			Skill s = Skill.byId(e.id());
			if (s == null) {
				continue;
			}
			Seen before = SEEN.get(s);
			Seen after = new Seen(e.level(), e.xp(), e.need(), e.masteryRanks(), e.masteryXp());
			SEEN.put(s, after);
			double gained = before == null ? 0 : gained(before, after, payload.masteryXpPerRank());
			// (more at once than any play gives between two updates is an operator's command: not counted)
			if (gained > 0 && gained <= MOST_AT_ONCE) {
				GAINS.computeIfAbsent(s, k -> new ArrayDeque<>()).addLast(new Gain(now, gained));
			}
		}
	}

	/** The XP between two sightings of a skill (0 for a loss): within a level, or across level-ups and Mastery ranks. */
	static double gained(Seen a, Seen b, double perRank) {
		if (b.level() == a.level() && b.ranks() == a.ranks()) {
			return Math.max(0, b.xp() - a.xp()) + Math.max(0, b.masteryXp() - a.masteryXp());
		}
		// (a jump of many levels at once is an operator's hand, not play: not counted)
		if (b.level() > a.level() + 3) {
			return 0;
		}
		if (b.level() > a.level()) {
			// (the rest of the old level, a level's need for every level between, and the new level's XP so far)
			return Math.max(0, a.need() - a.xp()) + Math.max(0, b.level() - a.level() - 1) * a.need() + b.xp() + b.masteryXp();
		}
		if (b.level() == a.level() && b.ranks() == a.ranks() + 1) {
			return Math.max(0, perRank - a.masteryXp()) + b.masteryXp();
		}
		return 0;
	}

	private static void prune(long now) {
		for (Deque<Gain> d : GAINS.values()) {
			while (!d.isEmpty() && now - d.peekFirst().at() > WINDOW_MS) {
				d.removeFirst();
			}
		}
	}

	/** The skill's XP an hour over the last 10 minutes (0 when it earned nothing in them). */
	public static double perHour(Skill s, long now) {
		prune(now);
		Deque<Gain> d = GAINS.get(s);
		if (d == null || d.isEmpty()) {
			return 0;
		}
		double sum = 0;
		for (Gain g : d) {
			sum += g.xp();
		}
		long span = Math.max(LEAST_MS, now - d.peekFirst().at());
		return sum * 3_600_000.0 / span;
	}

	/** Hours to the next level (or Mastery rank) at the pace now; infinite at no pace, 0 when there is nothing left. */
	public static double hoursToNext(Skill s, long now) {
		Seen seen = SEEN.get(s);
		ProgressPayload p = ClientProgress.current();
		double rate = perHour(s, now);
		if (seen == null || p == null) {
			return Double.POSITIVE_INFINITY;
		}
		double left = seen.level() >= p.maxLevel() ? (seen.ranks() >= p.masteryRanksMax() ? 0 : p.masteryXpPerRank() - seen.masteryXp())
				: Math.max(0, seen.need() - seen.xp());
		if (left <= 0) {
			return 0;
		}
		return rate <= 0 ? Double.POSITIVE_INFINITY : left / rate;
	}

	/** The skills that earned XP in the last 10 minutes, fastest first. */
	public static List<Skill> active(long now) {
		prune(now);
		List<Skill> out = new ArrayList<>();
		for (Skill s : Skill.values()) {
			if (perHour(s, now) > 0) {
				out.add(s);
			}
		}
		out.sort(Comparator.comparingDouble((Skill s) -> perHour(s, now)).reversed());
		return out;
	}

	public static void clear() {
		GAINS.clear();
		SEEN.clear();
	}

	/** "850", "3.2k", "12k": a number of XP short enough for a small HUD. */
	public static String shortXp(double xp) {
		if (xp < 1000) {
			return Long.toString(Math.round(xp));
		}
		if (xp < 10_000) {
			return String.format(java.util.Locale.ROOT, "%.1fk", xp / 1000);
		}
		return Math.round(xp / 1000) + "k";
	}

	/** "45m", "1h20m", "12h", "4d", "—": a time short enough for a small HUD. */
	public static String shortTime(double hours) {
		if (Double.isInfinite(hours) || Double.isNaN(hours)) {
			return "—";
		}
		long minutes = Math.max(1, Math.round(hours * 60));
		if (minutes < 60) {
			return minutes + "m";
		}
		if (minutes < 600) {
			return minutes / 60 + "h" + (minutes % 60 == 0 ? "" : String.format(java.util.Locale.ROOT, "%02dm", minutes % 60));
		}
		if (minutes < 100 * 60) {
			return Math.round(minutes / 60.0) + "h";
		}
		return Math.round(minutes / 1440.0) + "d";
	}
}
