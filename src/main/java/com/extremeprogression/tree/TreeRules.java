package com.extremeprogression.tree;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.skill.Specialization;

/**
 * The rules of the skill trees (design §6), on a {@link PlayerProgress}; callers save and sync afterwards. The same
 * rules run on the client to show what can be bought, so the two never disagree.
 * <ul>
 * <li>From level 25 a skill earns 1 point per level (75 at level 100). Both specializations share them.</li>
 * <li>The first specialization is chosen at level 25, the second at 75; each is free to choose, and a specialization
 * of another skill it conflicts with blocks it.</li>
 * <li>A respec frees every point of the skill and both choices, for 20% of the skill's XP, and then waits 7 real
 * days. Redistributing points is a respec too.</li>
 * <li>When levels are lost, the most recently bought points go first; won back, they return to the same nodes by
 * themselves.</li>
 * <li>A specialization whose level is no longer reached (below 25 or 75) is dormant: kept, but not in use.</li>
 * </ul>
 */
public final class TreeRules {
	private TreeRules() {}

	/** Why something cannot be done; {@link #OK} when it can. */
	public enum Result {
		OK, LEVEL, ALREADY_CHOSEN, BOTH_CHOSEN, CONFLICT, NOT_CHOSEN, DORMANT, MAX_RANK, REQUIRES, OTHER_CHOICE, POINTS, WAITING, NOTHING_TO_RESET
	}

	public static String key(Specialization spec, NodeSlot slot) {
		return spec.id + "/" + slot.id;
	}

	/** The node of a saved key, or null for one this version does not know (it is kept, never dropped). */
	public static NodeSlot slotOf(String key) {
		int i = key.indexOf('/');
		return i < 0 ? null : NodeSlot.byId(key.substring(i + 1));
	}

	private static int costOf(String key) {
		NodeSlot slot = slotOf(key);
		return slot == null ? 0 : slot.cost;
	}

	public static int earned(SkillData d) {
		return Math.max(0, d.level - Config.FIRST_SPECIALIZATION_LEVEL);
	}

	public static int spent(SkillData d) {
		int total = 0;
		for (String k : d.bought) {
			total += costOf(k);
		}
		return total;
	}

	public static int free(SkillData d) {
		return earned(d) - spent(d);
	}

	public static int rank(SkillData d, Specialization spec, NodeSlot slot) {
		String k = key(spec, slot);
		int n = 0;
		for (String b : d.bought) {
			if (b.equals(k)) {
				n++;
			}
		}
		return n;
	}

	/** The rank (0–5) of a specialization's active ability. */
	public static int abilityRank(SkillData d, Specialization spec) {
		int rank = 0;
		for (NodeSlot slot : NodeSlot.values()) {
			if (slot.kind == NodeSlot.Kind.ABILITY && rank(d, spec, slot) > 0) {
				rank = Math.max(rank, slot.abilityRank());
			}
		}
		return rank;
	}

	/** Which of the skill's specializations this is (0 first, 1 second), or -1 when it is not chosen. */
	public static int indexOf(SkillData d, Specialization spec) {
		return d.specs.indexOf(spec.id);
	}

	public static int unlockLevel(int index) {
		return index == 0 ? Config.FIRST_SPECIALIZATION_LEVEL : Config.SECOND_SPECIALIZATION_LEVEL;
	}

	/** The chosen specialization in that place (0 or 1) when its level is reached, else null (none, or dormant). */
	public static Specialization active(SkillData d, int index) {
		if (index >= d.specs.size() || d.level < unlockLevel(index)) {
			return null;
		}
		return Specialization.byId(d.specs.get(index));
	}

	// --- Choosing --------------------------------------------------------------------------------

	public static Result canChoose(PlayerProgress p, Specialization spec) {
		SkillData d = p.get(spec.skill);
		if (d.specs.contains(spec.id)) {
			return Result.ALREADY_CHOSEN;
		}
		if (d.specs.size() >= 2) {
			return Result.BOTH_CHOSEN;
		}
		if (d.level < unlockLevel(d.specs.size())) {
			return Result.LEVEL;
		}
		Specialization conflict = spec.conflict();
		if (conflict != null && p.get(conflict.skill).specs.contains(conflict.id)) {
			return Result.CONFLICT;
		}
		return Result.OK;
	}

	public static Result choose(PlayerProgress p, Specialization spec) {
		Result r = canChoose(p, spec);
		if (r == Result.OK) {
			p.get(spec.skill).specs.add(spec.id);
		}
		return r;
	}

	// --- Buying ----------------------------------------------------------------------------------

	public static Result canBuy(PlayerProgress p, Specialization spec, NodeSlot slot) {
		SkillData d = p.get(spec.skill);
		int index = indexOf(d, spec);
		if (index < 0) {
			return Result.NOT_CHOSEN;
		}
		if (active(d, index) == null) {
			return Result.DORMANT;
		}
		if (rank(d, spec, slot) >= slot.maxRank) {
			return Result.MAX_RANK;
		}
		if (d.level < slot.level) {
			return Result.LEVEL;
		}
		NodeSlot partner = slot.partner();
		if (partner != null && rank(d, spec, partner) > 0) {
			return Result.OTHER_CHOICE;
		}
		List<NodeSlot> requires = slot.requires();
		if (!requires.isEmpty() && requires.stream().noneMatch(r -> rank(d, spec, r) > 0)) {
			return Result.REQUIRES;
		}
		if (free(d) < slot.cost) {
			return Result.POINTS;
		}
		return Result.OK;
	}

	public static Result buy(PlayerProgress p, Specialization spec, NodeSlot slot) {
		Result r = canBuy(p, spec, slot);
		if (r == Result.OK) {
			p.get(spec.skill).bought.add(key(spec, slot));
		}
		return r;
	}

	// --- Respec ----------------------------------------------------------------------------------

	public static long respecWait() {
		return Math.round(Config.RESPEC_DAYS * 24 * 60 * 60 * 1000);
	}

	public static Result canRespec(PlayerProgress p, Skill skill, long now) {
		SkillData d = p.get(skill);
		if (d.specs.isEmpty() && d.bought.isEmpty() && d.suspended.isEmpty()) {
			return Result.NOTHING_TO_RESET;
		}
		if (now < d.respecReadyAt) {
			return Result.WAITING;
		}
		return Result.OK;
	}

	/** The XP a respec of this skill would cost now. */
	public static double respecCost(PlayerProgress p, Skill skill) {
		return Progression.totalXp(p, skill) * Config.RESPEC_XP_SHARE;
	}

	/**
	 * Resets the skill's tree: both specializations and every point, which are free to choose again. Unless it is an
	 * operator's free respec, it costs {@link #respecCost} and starts the waiting time.
	 */
	public static Result respec(PlayerProgress p, Skill skill, long now, boolean free) {
		Result r = canRespec(p, skill, now);
		if (r == Result.NOTHING_TO_RESET || r == Result.WAITING && !free) {
			return r;
		}
		SkillData d = p.get(skill);
		double cost = free ? 0 : respecCost(p, skill);
		d.specs.clear();
		d.bought.clear();
		d.suspended.clear();
		d.cooldowns.clear();
		if (!free) {
			d.respecReadyAt = now + respecWait();
			Progression.remove(p, skill, cost);
		}
		return Result.OK;
	}

	// --- Points and levels -----------------------------------------------------------------------

	/**
	 * Brings the points in line with the level: while more are spent than earned, the most recently bought point is set
	 * aside; while there are points again, the points set aside return, the last one set aside first, to the same node.
	 */
	public static void settle(PlayerProgress p, Skill skill) {
		SkillData d = p.get(skill);
		while (spent(d) > earned(d) && !d.bought.isEmpty()) {
			d.suspended.add(d.bought.remove(d.bought.size() - 1));
		}
		while (!d.suspended.isEmpty()) {
			String next = d.suspended.get(d.suspended.size() - 1);
			if (costOf(next) > free(d)) {
				break;
			}
			d.bought.add(d.suspended.remove(d.suspended.size() - 1));
		}
	}

	/** The nodes bought, for the GUI and tests: each key once with its rank. */
	public static List<String> owned(SkillData d) {
		List<String> out = new ArrayList<>();
		for (String k : d.bought) {
			if (!out.contains(k)) {
				out.add(k);
			}
		}
		return out;
	}
}
