package com.extremeprogression.client.ui;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.extremeprogression.data.DeathData;
import com.extremeprogression.skill.Skill;

import net.minecraft.locale.Language;

/** How deaths read on screen: dimensions, times, durations and losses, shared by the Death Register and the summary. */
public final class DeathView {
	private DeathView() {}

	/** A dimension's name: the four the penalty knows by name, any other by its id. */
	public static String dimension(String id) {
		String key = "gui.extremeprogression.death.dimension." + id.replace(':', '.');
		return Language.getInstance().has(key) ? Ui.tr(key) : id;
	}

	/** A dimension's factor: ×1, ×1.5, ×2.5. */
	public static String factor(double multiplier) {
		String s = String.format(Locale.ROOT, "%.2f", multiplier).replaceAll("0+$", "").replaceAll("\\.$", "");
		return "×" + s;
	}

	/** Game ticks as a duration: 6:12, 38 min, 1 h 5 min. */
	public static String duration(long ticks) {
		long seconds = (ticks + 19) / 20;
		if (seconds < 600) {
			return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
		}
		long minutes = (seconds + 59) / 60;
		if (minutes < 60) {
			return Ui.tr("gui.extremeprogression.death.minutes", minutes);
		}
		return minutes % 60 == 0 ? Ui.tr("gui.extremeprogression.death.hours_whole", minutes / 60)
				: Ui.tr("gui.extremeprogression.death.hours", minutes / 60, minutes % 60);
	}

	/** When a death was: "Today 14:02", "Yesterday 21:47", or "Oct 27 09:15" (the player's own time zone). */
	public static String when(long millis) {
		LocalDateTime t = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
		String time = t.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT));
		LocalDate today = LocalDate.now();
		if (t.toLocalDate().equals(today)) {
			return Ui.tr("gui.extremeprogression.death.today", time);
		}
		if (t.toLocalDate().equals(today.minusDays(1))) {
			return Ui.tr("gui.extremeprogression.death.yesterday", time);
		}
		return t.format(DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.ENGLISH));
	}

	public static String skillName(String id) {
		Skill s = Skill.byId(id);
		return s == null ? id : Ui.tr(s.translationKey());
	}

	public static int accent(String id) {
		Skill s = Skill.byId(id);
		return s == null ? Ui.TEXT : s.category.accent;
	}

	/** What one skill lost: "−31,200 XP", "−1 level (62 → 61)", "−1 Mastery rank (3 → 2)", or with both. */
	public static String loss(DeathData.Loss l) {
		List<String> parts = new ArrayList<>();
		if (l.ranksAfter < l.ranksBefore) {
			parts.add(Ui.tr("gui.extremeprogression.death.lost_rank", l.ranksBefore, l.ranksAfter));
		}
		if (l.levelAfter < l.levelBefore) {
			parts.add(Ui.tr("gui.extremeprogression.death.lost_level", l.levelBefore, l.levelAfter));
		}
		if (l.xp >= 0.5) {
			parts.add(Ui.tr(l.mastery ? "gui.extremeprogression.death.lost_mastery_xp" : "gui.extremeprogression.death.lost_xp", Ui.number(l.xp)));
		}
		return parts.isEmpty() ? Ui.tr("gui.extremeprogression.death.lost_nothing") : String.join(", ", parts);
	}

	/** The skills that lost something, the biggest loss first (a whole level or rank before any XP). */
	public static List<DeathData.Loss> lost(DeathData.Record r) {
		List<DeathData.Loss> out = new ArrayList<>();
		for (DeathData.Loss l : r.losses) {
			if (l.any()) {
				out.add(l);
			}
		}
		out.sort(Comparator.comparingInt((DeathData.Loss l) -> (l.levelBefore - l.levelAfter) + (l.ranksBefore - l.ranksAfter)).reversed()
				.thenComparing(Comparator.comparingDouble((DeathData.Loss l) -> l.xp).reversed()));
		return out;
	}

	/** One line for a death in the history: "Today 14:02 · Nether ×1.5 · Mining −31,200 XP and 6 more". */
	public static String line(DeathData.Record r) {
		List<DeathData.Loss> lost = lost(r);
		String what = lost.isEmpty() ? Ui.tr("gui.extremeprogression.death.lost_nothing")
				: skillName(lost.get(0).skill) + " " + loss(lost.get(0))
						+ (lost.size() > 1 ? " " + Ui.tr("gui.extremeprogression.death.and_more", lost.size() - 1) : "");
		return when(r.time) + " · " + dimension(r.dimension) + " " + factor(r.multiplier) + " · " + what;
	}
}
