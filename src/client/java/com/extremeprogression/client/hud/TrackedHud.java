package com.extremeprogression.client.hud;

import java.util.List;

import com.extremeprogression.client.state.ClientOptions;
import com.extremeprogression.client.state.ClientProgress;
import com.extremeprogression.client.ui.SkillView;
import com.extremeprogression.client.ui.Ui;
import com.extremeprogression.network.ProgressPayload;
import com.extremeprogression.skill.Skill;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The skills the player chose to track (the Track button on a Skill Screen), as a second hotbar right of the vanilla
 * one, beyond the ability icon. One slot per skill in the hotbar's own frame (resource packs restyle both alike), and in
 * it what a slot shows for an item: the skill's icon, its level as the item count (its Mastery rank past the maximum,
 * "M3" in gold; a little higher than a count, clear of the bar) and a green bar of the progress to the next level where
 * a tool shows its durability. When the screen ends, the bar goes on in a row above. Nothing is shown while nothing is
 * tracked (design §9: no XP bar on screen unless the player asks for one).
 */
public final class TrackedHud {
	private TrackedHud() {}

	/** The vanilla hotbar: nine slots of 20 between an edge of 1 on each side, 22 high. */
	private static final Identifier HOTBAR = Identifier.fromNamespaceAndPath("minecraft", "hud/hotbar");
	private static final int HOTBAR_W = 182, HOTBAR_H = 22, SLOT = 20;
	/** Room left for the ability icon beside the hotbar (its icon and "⇧R"), so the bar never jumps. */
	private static final int ABILITY_ROOM = 40;
	/** The colour of a full durability bar. */
	private static final int GREEN = 0x00FF00;

	public static void extract(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		List<Skill> tracked = ClientOptions.tracked();
		if (mc.player == null || mc.player.isSpectator() || tracked.isEmpty() || ClientProgress.current() == null) {
			return;
		}
		int x = g.guiWidth() / 2 + 91 + 8 + ABILITY_ROOM;
		int perRow = perRow(g.guiWidth());
		for (int first = 0, row = 0; first < tracked.size(); first += perRow, row++) {
			List<Skill> skills = tracked.subList(first, Math.min(tracked.size(), first + perRow));
			int y = g.guiHeight() - HOTBAR_H - row * HOTBAR_H;
			frame(g, x, y, skills.size());
			for (int i = 0; i < skills.size(); i++) {
				slot(g, mc.font, skills.get(i), x + 1 + i * SLOT + 2, y + 3);
			}
		}
	}

	/** How many slots fit in a row right of the ability icon, at least one. */
	static int perRow(int guiWidth) {
		int x = guiWidth / 2 + 91 + 8 + ABILITY_ROOM;
		return Math.max(1, (guiWidth - 2 - x - 2) / SLOT);
	}

	/** The hotbar's frame for a number of slots: its left edge, as many of its slots, its right edge. */
	private static void frame(GuiGraphicsExtractor g, int x, int y, int slots) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, HOTBAR_W, HOTBAR_H, 0, 0, x, y, 1, HOTBAR_H);
		for (int i = 0; i < slots; i++) {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, HOTBAR_W, HOTBAR_H, 1 + (i % 9) * SLOT, 0, x + 1 + i * SLOT, y, SLOT, HOTBAR_H);
		}
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, HOTBAR_W, HOTBAR_H, HOTBAR_W - 1, 0, x + 1 + slots * SLOT, y, 1, HOTBAR_H);
	}

	/** One skill where an item's 16×16 would be, decorated as the game decorates a stack and a worn tool. */
	private static void slot(GuiGraphicsExtractor g, Font font, Skill s, int x, int y) {
		ProgressPayload.Entry e = SkillView.entry(s);
		boolean mastered = SkillView.mastered(e);
		g.fakeItem(SkillView.icon(s), x, y);
		int filled = (int) Math.round(13 * Ui.clamp01(SkillView.fill(e)));
		g.fill(x + 2, y + 13, x + 15, y + 15, Ui.argb(255, 0));
		if (filled > 0) {
			g.fill(x + 2, y + 13, x + 2 + filled, y + 14, Ui.argb(255, GREEN));
		}
		String count = mastered && e.masteryRanks() > 0 ? "M" + e.masteryRanks() : String.valueOf(e.level());
		// Where a stack's count stands, raised by five so that the bar below it stays in view (a stack has no bar)
		g.text(font, count, x + 19 - 2 - font.width(count), y + 6 + 3 - 5, Ui.argb(255, mastered ? Ui.GOLD : Ui.TEXT), true);
	}

	/** How many skills the HUD shows now (for tests). */
	public static int shown() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null || ClientProgress.current() == null ? 0 : ClientOptions.tracked().size();
	}
}
