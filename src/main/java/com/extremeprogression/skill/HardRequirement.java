package com.extremeprogression.skill;

import com.extremeprogression.data.SkillData;
import com.extremeprogression.spec.Specs;
import com.extremeprogression.tree.NodeSlot;

import net.minecraft.world.entity.Entity;

/**
 * Hard requirements (design §2, option D, ✅ round 19): a node, or the Alchemist's new potions, that only works with
 * another skill at a level. The node can be bought without it; it does nothing until the level is there.
 */
public enum HardRequirement {
	/** Template Saver (Tinkerer): Mining 30. */
	TEMPLATE_SAVER(Specialization.TINKERER, NodeSlot.C1, Skill.MINING, 30),
	/** Deep Sea (Treasure Hunter): Exploration 40. */
	DEEP_SEA(Specialization.TREASURE_HUNTER, NodeSlot.C1, Skill.EXPLORATION, 40),
	/** Rider (Beastmaster): Exploration 30. */
	RIDER(Specialization.BEASTMASTER, NodeSlot.C2, Skill.EXPLORATION, 30),
	/** The Alchemist's new potions: Foraging 30 (no node: the specialization itself). */
	NEW_POTIONS(Specialization.ALCHEMIST, null, Skill.FORAGING, 30);

	public final Specialization spec;
	/** The node, or null when it is the specialization's own (the new potions). */
	public final NodeSlot slot;
	public final Skill skill;
	public final int level;

	HardRequirement(Specialization spec, NodeSlot slot, Skill skill, int level) {
		this.spec = spec;
		this.slot = slot;
		this.skill = skill;
		this.level = level;
	}

	/** Whether the player has the level (on the server; on the client for its own player). */
	public boolean met(Entity e) {
		SkillData d = Specs.skillData(e, skill);
		return d != null && d.level >= level;
	}

	/** The requirement of a node, or null when it has none. */
	public static HardRequirement of(Specialization spec, NodeSlot slot) {
		for (HardRequirement r : values()) {
			if (r.spec == spec && r.slot == slot) {
				return r;
			}
		}
		return null;
	}
}
