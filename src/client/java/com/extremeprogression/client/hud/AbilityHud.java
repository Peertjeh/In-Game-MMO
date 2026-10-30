package com.extremeprogression.client.hud;

import com.extremeprogression.ability.Triggers;
import com.extremeprogression.client.state.ClientProgress;
import com.extremeprogression.client.ui.SkillView;
import com.extremeprogression.client.ui.Ui;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.skill.Specialization;
import com.extremeprogression.tree.TreeRules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The one HUD element of the mod besides its notices (design §9): beside the hotbar, the icon of the ability the
 * ability key would use now, while it is ready (holding Shift shows the second specialization's), and above it the
 * abilities in effect with the seconds they still last.
 */
public final class AbilityHud {
	private AbilityHud() {}

	private static PlayerProgress view;
	private static int viewOf = -1;

	/** The ability the key would use now and that is ready, or null. */
	public static Specialization ready(boolean second) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || ClientProgress.current() == null) {
			return null;
		}
		if (view == null || viewOf != ClientProgress.updates()) {
			view = ClientProgress.asProgress();
			viewOf = ClientProgress.updates();
		}
		int index = second ? 1 : 0;
		Specialization spec = Triggers.resolve(mc.player, skill -> TreeRules.active(view.get(skill), index));
		if (spec == null || TreeRules.abilityRank(view.get(spec.skill), spec) == 0 || ClientProgress.cooldown(spec.skill.id, spec.id) > 0) {
			return null;
		}
		return spec;
	}

	public static void extract(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.isSpectator()) {
			return;
		}
		int x = g.guiWidth() / 2 + 91 + 8;
		int y = g.guiHeight() - 20;
		// The abilities in effect, above: their icon and the seconds left
		int above = y - 18;
		for (Specialization active : Specialization.values()) {
			int left = ClientProgress.activeTicks(active.id);
			if (left > 0) {
				g.fakeItem(SkillView.icon(active.skill), x, above);
				Ui.text(g, mc.font, (left + 19) / 20 + "s", x + 18, above + 4, Ui.WARN);
				above -= 18;
			}
		}
		boolean second = mc.options.keyShift.isDown();
		Specialization spec = ready(second);
		if (spec == null) {
			return;
		}
		g.fakeItem(SkillView.icon(spec.skill), x, y);
		Ui.text(g, mc.font, second ? "⇧R" : "R", x + 18, y + 4, spec.skill.category.accent);
	}

	/** The ability in effect that the HUD shows first, or null (for tests). */
	public static Specialization activeShown() {
		for (Specialization active : Specialization.values()) {
			if (ClientProgress.activeTicks(active.id) > 0) {
				return active;
			}
		}
		return null;
	}
}
