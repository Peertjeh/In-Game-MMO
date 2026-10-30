package com.extremeprogression.client.ui;

import java.util.List;
import java.util.Map;

import com.extremeprogression.client.state.ClientProgress;
import com.extremeprogression.network.ProgressPayload;
import com.extremeprogression.skill.Skill;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** How a skill looks: its icon (a vanilla item, design §9) and its numbers as the server last sent them. */
public final class SkillView {
	private SkillView() {}

	public static ItemStack icon(Skill skill) {
		return new ItemStack(switch (skill) {
			case MELEE -> Items.IRON_SWORD;
			case RANGED -> Items.BOW;
			case DEFENSE -> Items.SHIELD;
			case MINING -> Items.IRON_PICKAXE;
			case FORAGING -> Items.OAK_LOG;
			case FARMING -> Items.WHEAT;
			case FISHING -> Items.FISHING_ROD;
			case EXPLORATION -> Items.COMPASS;
			case SMITHING -> Items.ANVIL;
			case ARCANA -> Items.ENCHANTED_BOOK;
		});
	}

	/** The skill's entry, or an empty one at level 0 before the server has sent anything. */
	public static ProgressPayload.Entry entry(Skill skill) {
		ProgressPayload.Entry e = ClientProgress.skill(skill.id);
		return e != null ? e : new ProgressPayload.Entry(skill.id, 0, 0, 0, 0, 0, List.of(), List.of(), List.of(), 0, Map.of(), List.of());
	}

	public static boolean mastered(ProgressPayload.Entry e) {
		ProgressPayload p = ClientProgress.current();
		return p != null && e.level() >= p.maxLevel();
	}

	/** The share of XP this skill loses to its opposing skill now (0 when none presses on it, design §2). */
	public static double opposingPenalty(Skill skill) {
		if (skill.opposite() == null) {
			return 0;
		}
		return com.extremeprogression.progression.Opposing.penalty(entry(skill).level(), entry(skill.opposite()).level());
	}

	/** "Melee 47: −30% XP, passive bonuses at 75%", or null when no opposing skill presses on this one. */
	public static String opposingWarning(Skill skill) {
		double penalty = opposingPenalty(skill);
		if (penalty <= 0) {
			return null;
		}
		return Ui.tr("gui.extremeprogression.skill.opposing_penalty", Ui.tr(skill.opposite().translationKey()), entry(skill.opposite()).level(),
				Ui.percent(penalty), Ui.percent(com.extremeprogression.config.Config.OPPOSING_PASSIVE_POWER));
	}

	/** How full the skill's bar is: the level's XP, or past the maximum level the Mastery rank's. */
	public static double fill(ProgressPayload.Entry e) {
		ProgressPayload p = ClientProgress.current();
		if (p == null) {
			return 0;
		}
		if (mastered(e)) {
			return e.masteryRanks() >= p.masteryRanksMax() ? 1 : e.masteryXp() / p.masteryXpPerRank();
		}
		return e.need() > 0 ? e.xp() / e.need() : 0;
	}
}
