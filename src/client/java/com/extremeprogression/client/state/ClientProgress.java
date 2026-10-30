package com.extremeprogression.client.state;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.data.DeathData;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.network.ProgressPayload;
import com.extremeprogression.skill.Skill;

import net.minecraft.client.Minecraft;

/**
 * The client's copy of its player's progression, as the server last sent it. Shown, never decided here: the Skill
 * Screen runs the same tree rules on it only to show what the server would allow.
 */
public final class ClientProgress {
	private ClientProgress() {}

	private static ProgressPayload current = null;
	private static int updates = 0;
	/** The client tick and the real time the progression arrived, to count cooldowns and the respec wait down. */
	private static long receivedTick = 0;
	private static long receivedAt = 0;
	private static long ticks = 0;
	/** Client ticks while the game runs (not paused), to count death fatigue and the death pressure down as the server does. */
	private static long played = 0;
	private static long receivedPlayed = 0;

	/** The progression as the tree rules read it, rebuilt whenever it arrives. */
	private static PlayerProgress cached = new PlayerProgress();
	/** The abilities in effect: each specialization's id and the client tick it ends. */
	private static final java.util.Map<String, Long> ACTIVE = new java.util.HashMap<>();
	/** The death history for the Death Register, newest last, and the death whose summary is still to be shown. */
	private static List<DeathData.Record> deaths = List.of();
	private static DeathData.Record summary = null;

	public static void apply(ProgressPayload payload) {
		current = payload;
		updates++;
		receivedTick = ticks;
		receivedAt = System.currentTimeMillis();
		receivedPlayed = played;
		cached = asProgress();
	}

	public static void applyDeaths(com.extremeprogression.network.DeathPayload payload) {
		deaths = List.copyOf(payload.history());
		if (payload.summary() != null) {
			summary = payload.summary();
		}
	}

	/** The deaths the server keeps, newest last. */
	public static List<DeathData.Record> deathHistory() {
		return deaths;
	}

	/** The death whose summary is still to be shown, once: null after the first call. */
	public static DeathData.Record takeSummary() {
		DeathData.Record out = summary;
		summary = null;
		return out;
	}

	public static boolean summaryPending() {
		return summary != null;
	}

	/** The deaths part of the progression ({@link ProgressPayload.Death#NONE} until it arrives). */
	public static ProgressPayload.Death death() {
		return current == null ? ProgressPayload.Death.NONE : current.death();
	}

	/** Ticks of death fatigue left (0 when none). */
	public static long fatigueLeft() {
		return Math.max(0, death().fatigueLeft() - (played - receivedPlayed));
	}

	/** Ticks of play until the death pressure drops by 1 (0 at pressure 0; the server corrects it every minute). */
	public static long pressureDropsIn() {
		return death().pressure() <= 0 ? 0 : Math.max(0, death().pressureDropsIn() - (played - receivedPlayed));
	}

	public static void applyActive(com.extremeprogression.network.ActivePayload payload) {
		ACTIVE.clear();
		payload.ticks().forEach((spec, left) -> ACTIVE.put(spec, ticks + left));
	}

	/** Ticks an ability still lasts (0 when it is not in effect). */
	public static int activeTicks(String spec) {
		Long end = ACTIVE.get(spec);
		return end == null ? 0 : (int) Math.max(0, end - ticks);
	}

	/** The skill as the tree rules read it, for the effects felt on the client (never null for a known skill). */
	public static SkillData skillData(Skill skill) {
		return cached.get(skill);
	}

	/** Every client tick. */
	public static void tick() {
		ticks++;
		if (!Minecraft.getInstance().isPaused()) {
			played++;
		}
	}

	public static void clear() {
		current = null;
		cached = new PlayerProgress();
		ACTIVE.clear();
		deaths = List.of();
		summary = null;
	}

	/** Null until the server has sent the progression. */
	public static ProgressPayload current() {
		return current;
	}

	/** How many times the progression arrived (for tests). */
	public static int updates() {
		return updates;
	}

	public static ProgressPayload.Entry skill(String id) {
		if (current == null) {
			return null;
		}
		for (ProgressPayload.Entry e : current.skills()) {
			if (e.id().equals(id)) {
				return e;
			}
		}
		return null;
	}

	/** Ticks until a specialization's ability is ready again (0 when ready). */
	public static int cooldown(String skill, String spec) {
		ProgressPayload.Entry e = skill(skill);
		Integer left = e == null ? null : e.cooldowns().get(spec);
		return left == null ? 0 : (int) Math.max(0, left - (ticks - receivedTick));
	}

	/** Milliseconds until the skill can be respecced again (0 when it can). */
	public static long respecWait(String skill) {
		ProgressPayload.Entry e = skill(skill);
		return e == null ? 0 : Math.max(0, e.respecWait() - (System.currentTimeMillis() - receivedAt));
	}

	/** The progression as the tree rules read it (a copy: changing it changes nothing). */
	public static PlayerProgress asProgress() {
		PlayerProgress p = new PlayerProgress();
		if (current == null) {
			return p;
		}
		for (ProgressPayload.Entry e : current.skills()) {
			if (Skill.byId(e.id()) == null) {
				continue;
			}
			SkillData d = new SkillData();
			d.level = e.level();
			d.xp = e.xp();
			d.masteryRanks = e.masteryRanks();
			d.masteryXp = e.masteryXp();
			d.specs = new ArrayList<>(e.specs());
			d.bought = new ArrayList<>(e.bought());
			d.suspended = new ArrayList<>(e.suspended());
			d.perks = new ArrayList<>(e.perks());
			p.skills.put(e.id(), d);
		}
		return p;
	}
}
