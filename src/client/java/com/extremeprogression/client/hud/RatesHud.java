package com.extremeprogression.client.hud;

import java.util.List;

import com.extremeprogression.client.state.ClientOptions;
import com.extremeprogression.client.state.ClientProgress;
import com.extremeprogression.client.state.ClientRates;
import com.extremeprogression.client.ui.SkillView;
import com.extremeprogression.client.ui.Ui;
import com.extremeprogression.skill.Skill;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The pace (1.8.0, the owner's wish): left of the hotbar (and of the off-hand slot when it shows), the one or two skills
 * that earn the most XP right now, each with its XP an hour over the last 10 minutes, the time its next level (or
 * Mastery rank) takes at that pace, and a bar of the progress to it. Shown while a skill earns; off with the button on
 * the Character Sheet.
 */
public final class RatesHud {
	private RatesHud() {}

	static final int W = 62, H = 22, GAP = 2, MAX = 2;

	public static void extract(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.isSpectator() || !ClientOptions.pace() || ClientProgress.current() == null) {
			return;
		}
		long now = System.currentTimeMillis();
		List<Skill> active = ClientRates.active(now);
		int n = Math.min(MAX, active.size());
		if (n == 0) {
			return;
		}
		// (the off-hand slot sits left of the hotbar for a right-handed player holding something there)
		boolean offhand = mc.player.getMainArm() == HumanoidArm.RIGHT && !mc.player.getOffhandItem().isEmpty();
		int right = g.guiWidth() / 2 - 91 - 4 - (offhand ? 31 : 0);
		int x = right - W, y = g.guiHeight() - H;
		for (int i = 0; i < n; i++) {
			if (x < 2 && i > 0) {
				x = right - W;
				y -= H + GAP;
			}
			block(g, active.get(i), x, y, now);
			x -= W + GAP;
		}
	}

	private static void block(GuiGraphicsExtractor g, Skill s, int x, int y, long now) {
		Minecraft mc = Minecraft.getInstance();
		g.fill(x, y, x + W, y + H, Ui.argb(0.6, Ui.SHADOW));
		g.fill(x, y, x + W, y + 1, Ui.argb(0.9, s.category.accent));
		g.fakeItem(SkillView.icon(s), x + 2, y + 3);
		Ui.text(g, mc.font, ClientRates.shortXp(ClientRates.perHour(s, now)) + "/h", x + 20, y + 3, Ui.TEXT);
		Ui.text(g, mc.font, "→" + ClientRates.shortTime(ClientRates.hoursToNext(s, now)), x + 20, y + 12, Ui.GOLD);
		double fill = Ui.clamp01(SkillView.fill(SkillView.entry(s)));
		g.fill(x + 1, y + H - 2, x + W - 1, y + H, Ui.argb(255, 0));
		g.fill(x + 1, y + H - 2, x + 1 + (int) Math.round((W - 2) * fill), y + H - 1, Ui.argb(255, 0x00FF00));
	}

	/** How many skills the pace shows now (for tests). */
	public static int shown() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null || !ClientOptions.pace() ? 0 : Math.min(MAX, ClientRates.active(System.currentTimeMillis()).size());
	}
}
