package com.extremeprogression.client.state;

import java.util.ArrayList;

import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.network.ProgressPayload;
import com.extremeprogression.skill.Skill;

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

	public static void apply(ProgressPayload payload) {
		current = payload;
		updates++;
		receivedTick = ticks;
		receivedAt = System.currentTimeMillis();
	}

	/** Every client tick. */
	public static void tick() {
		ticks++;
	}

	public static void clear() {
		current = null;
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
			p.skills.put(e.id(), d);
		}
		return p;
	}
}
