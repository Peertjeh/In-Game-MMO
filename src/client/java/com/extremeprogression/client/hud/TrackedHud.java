package com.extremeprogression.client.hud;

import java.util.List;

import com.extremeprogression.client.state.ClientOptions;
import com.extremeprogression.client.state.ClientProgress;
import com.extremeprogression.client.ui.SkillView;
import com.extremeprogression.client.ui.Ui;
import com.extremeprogression.network.ProgressPayload;
import com.extremeprogression.skill.Skill;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The skills the player chose to track (the Track button on a Skill Screen), right of the hotbar beyond the ability
 * icon: each as a small block with its icon, its level (its Mastery rank past the maximum, in gold) and a bar of the
 * progress to the next. Blocks run to the right and, when the screen ends, go on in a row above. Nothing is shown
 * while nothing is tracked (design §9: no XP bar on screen unless the player asks for one).
 */
public final class TrackedHud {
	private TrackedHud() {}

	static final int BLOCK_W = 58, BLOCK_H = 20, GAP = 2;
	/** Room left for the ability icon beside the hotbar (its icon and "⇧R"), so the blocks never jump. */
	private static final int ABILITY_ROOM = 40;

	public static void extract(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		List<Skill> tracked = ClientOptions.tracked();
		if (mc.player == null || mc.player.isSpectator() || tracked.isEmpty() || ClientProgress.current() == null) {
			return;
		}
		int startX = g.guiWidth() / 2 + 91 + 8 + ABILITY_ROOM;
		int x = startX, y = g.guiHeight() - BLOCK_H - 1;
		for (Skill s : tracked) {
			if (x + BLOCK_W > g.guiWidth() - 2 && x > startX) {
				x = startX;
				y -= BLOCK_H + GAP;
			}
			block(g, s, x, y);
			x += BLOCK_W + GAP;
		}
	}

	private static void block(GuiGraphicsExtractor g, Skill s, int x, int y) {
		Minecraft mc = Minecraft.getInstance();
		ProgressPayload.Entry e = SkillView.entry(s);
		boolean mastered = SkillView.mastered(e);
		g.fill(x, y, x + BLOCK_W, y + BLOCK_H, Ui.argb(0.55, Ui.SHADOW));
		g.fill(x, y, x + 1, y + BLOCK_H, Ui.argb(0.9, s.category.accent));
		g.fakeItem(SkillView.icon(s), x + 2, y + 2);
		String level = mastered ? "M" + e.masteryRanks() : "Lv " + e.level();
		Ui.text(g, mc.font, level, x + 20, y + 2, mastered ? Ui.GOLD : Ui.TEXT);
		Ui.meter(g, x + 20, y + 13, BLOCK_W - 23, 3, SkillView.fill(e), mastered ? Ui.GOLD : s.category.accent);
	}

	/** How many blocks the HUD shows now (for tests). */
	public static int shown() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null || ClientProgress.current() == null ? 0 : ClientOptions.tracked().size();
	}
}
