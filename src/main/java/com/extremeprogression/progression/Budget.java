package com.extremeprogression.progression;

import com.extremeprogression.config.Config;

/**
 * The hybrid total budget (design §4): every level costs {@code 1 + (total / BUDGET_DIVISOR)²} times its base XP, and
 * the sum of all levels never passes TOTAL_LEVEL_CAP. Mastery counts toward neither.
 */
public final class Budget {
	private Budget() {}

	/** The factor on a level's XP for a player whose levels add up to {@code totalLevel}. */
	public static double factor(int totalLevel) {
		double r = Math.max(0, totalLevel) / Config.BUDGET_DIVISOR;
		return 1 + r * r;
	}

	public static boolean atCap(int totalLevel) {
		return totalLevel >= Config.TOTAL_LEVEL_CAP;
	}
}
