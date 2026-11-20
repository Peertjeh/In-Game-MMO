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
		if (mc.player == null || mc.player.isSpectator()) {
			return;
		}
		// (the off-hand slot sits left of the hotbar for a right-handed player holding something there)
		boolean offhand = mc.player.getMainArm() == HumanoidArm.RIGHT && !mc.player.getOffhandItem().isEmpty();
		int right = g.guiWidth() / 2 - 91 - 4 - (offhand ? 31 : 0);
		int top = g.guiHeight();
		long now = System.currentTimeMillis();
		List<Skill> active = ClientOptions.pace() && ClientProgress.current() != null ? ClientRates.active(now) : List.of();
		int n = Math.min(MAX, active.size());
		int x = right - W, y = g.guiHeight() - H;
		for (int i = 0; i < n; i++) {
			if (x < 2 && i > 0) {
				x = right - W;
				y -= H + GAP;
			}
			block(g, active.get(i), x, y, now);
			top = Math.min(top, y);
			x -= W + GAP;
		}
		boost(g, mc, right, top);
	}

	/**
	 * The XP boost (2.0), above the pace: "+150% XP · 24m" in the running tome's colour, a moon while Well Rested. Shown
	 * whenever a boost runs, the pace on or off.
	 */
	private static void boost(GuiGraphicsExtractor g, Minecraft mc, int right, int top) {
		com.extremeprogression.legends.Legends.View v = com.extremeprogression.client.state.ClientLegends.current();
		if (v == null || !v.boostsOn || !(v.boost > 0)) {
			return;
		}
		com.extremeprogression.legends.LegendsData.Boost run = com.extremeprogression.client.state.ClientLegends.running(v);
		int seconds = run != null ? run.seconds : v.rested;
		String text = "+" + Math.round(v.boost * 100) + "% XP · " + ClientRates.shortTime(seconds / 3600.0) + (v.rested > 0 ? " ☾" : "");
		int w = mc.font.width(text) + 8, h = 11;
		int x = right - w, y = top - h - 2;
		int color = run != null ? com.extremeprogression.client.state.ClientLegends.color(run) : 0x55FFFF;
		g.fill(x, y, x + w, y + h, Ui.argb(0.6, Ui.SHADOW));
		g.fill(x, y, x + 1, y + h, Ui.argb(0.9, color));
		Ui.text(g, mc.font, text, x + 4, y + 2, color);
		boostShown = true;
	}

	private static boolean boostShown;

	/** Whether the boost was drawn in the last frame (for tests). */
	public static boolean boostShown() {
		return boostShown;
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
