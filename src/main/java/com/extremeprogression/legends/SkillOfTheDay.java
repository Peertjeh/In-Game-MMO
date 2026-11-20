package com.extremeprogression.legends;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.config.Config;
import com.extremeprogression.config.SkillSettings;
import com.extremeprogression.skill.Skill;

/**
 * Skill of the Day (2.0): every day one skill earns SKILL_OF_THE_DAY_BONUS more XP for everyone, the same one all day
 * on the whole server. Which one follows from the date alone: every skill comes once in each run of days.
 */
public final class SkillOfTheDay {
	private SkillOfTheDay() {}

	/** The skill of this day, or null when the bonus is off or no skill is on. */
	public static Skill of(LocalDate day) {
		if (!(Config.SKILL_OF_THE_DAY_BONUS > 0)) {
			return null;
		}
		List<Skill> on = new ArrayList<>();
		for (Skill s : Skill.values()) {
			if (SkillSettings.enabled(s)) {
				on.add(s);
			}
		}
		if (on.isEmpty()) {
			return null;
		}
		// (every skill once in each run of as many days as there are skills, in an order shuffled anew for every run; a run
		// never begins with the skill the last one ended on)
		long n = day.toEpochDay();
		int size = on.size();
		long run = Math.floorDiv(n, size);
		int pos = (int) Math.floorMod(n, (long) size);
		List<Skill> order = shuffled(on, run);
		if (size > 1 && order.get(0) == shuffled(on, run - 1).get(size - 1)) {
			java.util.Collections.swap(order, 0, 1);
		}
		return order.get(pos);
	}

	private static List<Skill> shuffled(List<Skill> on, long run) {
		List<Skill> out = new ArrayList<>(on);
		java.util.Collections.shuffle(out, new java.util.Random(run * 31L + 17L));
		return out;
	}

	private static long cachedDay = Long.MIN_VALUE, cachedAt;
	private static Skill cached;

	/** Today's skill (worked out once a minute at most: the bonus asks on every XP gained). */
	public static Skill today() {
		long day = LocalDate.now().toEpochDay(), now = System.currentTimeMillis();
		if (day != cachedDay || now - cachedAt > 60_000) {
			cached = of(LocalDate.ofEpochDay(day));
			cachedDay = day;
			cachedAt = now;
		}
		return cached;
	}

	/** The bonus a skill earns today (0 for the other skills). */
	public static double bonus(Skill skill) {
		return skill != null && !Legends.quiet && skill == today() ? Config.SKILL_OF_THE_DAY_BONUS : 0;
	}
}
