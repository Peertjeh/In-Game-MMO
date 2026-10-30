package com.extremeprogression.progression;

import com.extremeprogression.config.Config;

/**
 * The level curve (design §4 and Appendix A.1), before the budget factor:
 * {@code hours(L) = HOURS_TO_MAX × (L / MAX_LEVEL)^CURVE_EXPONENT}, and an hour of focused play is XP_PER_HOUR XP.
 * The first levels come in minutes; the last ones take about ten hours each.
 */
public final class XpCurve {
	private XpCurve() {}

	/** Hours of focused play from level 0 to the start of this level. */
	public static double hours(int level) {
		int l = Math.max(0, Math.min(level, Config.MAX_LEVEL));
		return Config.HOURS_TO_MAX * Math.pow(l / (double) Config.MAX_LEVEL, Config.CURVE_EXPONENT);
	}

	/** XP from level 0 to the start of this level. */
	public static double cumulative(int level) {
		return Config.XP_PER_HOUR * hours(level);
	}

	/** XP for {@code level → level + 1}; 0 at (and beyond) the maximum level. */
	public static double base(int level) {
		if (level < 0 || level >= Config.MAX_LEVEL) {
			return 0;
		}
		return cumulative(level + 1) - cumulative(level);
	}
}
