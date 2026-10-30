package com.extremeprogression.client.screen;

import java.util.List;

import com.extremeprogression.client.state.ClientProgress;
import com.extremeprogression.client.ui.SkillView;
import com.extremeprogression.client.ui.Ui;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.mastery.Mastery;
import com.extremeprogression.mastery.MasteryPerk;
import com.extremeprogression.network.ProgressPayload;
import com.extremeprogression.network.TreePayload;
import com.extremeprogression.skill.Skill;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A skill's Mastery (design §9, screen 3): the ten ranks after level 100 with the perk chosen at each, the two perks'
 * totals, and the progress to the next rank. The next rank waiting for a perk offers both; a choice is final while the
 * rank is held.
 */
public class MasteryScreen extends Screen {
	static final int W = 300, H = 222;
	private static final int ROW = 11;
	/** Where the ranks' two cells start, and how wide each is. */
	private static final int CELLS_X = 56, CELL_W = (W - CELLS_X - 6 - 4) / 2;
	private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

	private static int opened = 0;

	private final Skill skill;
	private final Screen parent;
	private int x0, y0;
	private Button chooseA, chooseB;

	public MasteryScreen(Skill skill, Screen parent) {
		super(Component.translatable("gui.extremeprogression.mastery.title", Component.translatable(skill.translationKey())));
		this.skill = skill;
		this.parent = parent;
		opened++;
	}

	/** How many times a Mastery screen was opened (for tests). */
	public static int opened() {
		return opened;
	}

	private static String roman(int rank) {
		return rank >= 0 && rank < ROMAN.length ? ROMAN[rank] : String.valueOf(rank);
	}

	private SkillData data() {
		return ClientProgress.skillData(skill);
	}

	@Override
	protected void init() {
		x0 = (this.width - W) / 2;
		y0 = Math.max(2, (this.height - H) / 2);
		chooseA = this.addRenderableWidget(Button.builder(perkName("a"), b -> choose("a")).pos(x0 + CELLS_X, y0).size(CELL_W, ROW).build());
		chooseB = this.addRenderableWidget(Button.builder(perkName("b"), b -> choose("b")).pos(x0 + CELLS_X + CELL_W + 4, y0).size(CELL_W, ROW).build());
		chooseA.visible = chooseB.visible = false;
		this.addRenderableWidget(Button.builder(Component.translatable(parent != null ? "gui.back" : "gui.done"), b -> this.onClose())
				.pos(this.width / 2 - 50, y0 + H - 20).size(100, 20).build());
	}

	private Component perkName(String track) {
		return Component.translatable("gui.extremeprogression.mastery.choose", Component.translatable(MasteryPerk.of(skill, track).translationKey()));
	}

	private void choose(String track) {
		SkillScreen.send(new TreePayload(TreePayload.Action.PERK, skill.id, track));
	}

	/** The top of the ranks' list. */
	private int ranksY() {
		return y0 + 70;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		ProgressPayload p = ClientProgress.current();
		ProgressPayload.Entry e = SkillView.entry(skill);
		SkillData d = data();
		int ix = x0 + 6, iw = W - 12;
		Ui.panel(g, x0, y0 + 12, W, H - 38);
		Ui.centered(g, this.font, this.title.getString(), this.width / 2, y0 + 2, skill.category.accent);
		int y = y0 + 18;
		int max = p == null ? Config.MASTERY_RANKS : p.masteryRanksMax();
		boolean mastered = SkillView.mastered(e);
		if (!mastered) {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.mastery.locked", p == null ? Config.MAX_LEVEL : p.maxLevel(), e.level()), ix, y, Ui.MUTED);
		} else {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.mastery.rank", e.masteryRanks(), max), ix, y, Ui.GOLD);
			String next = e.masteryRanks() >= max ? Ui.tr("gui.extremeprogression.mastery.maxed")
					: Ui.tr("gui.extremeprogression.mastery.next", Ui.number(e.masteryXp()), Ui.number(p.masteryXpPerRank()), roman(e.masteryRanks() + 1));
			Ui.right(g, this.font, next, ix + iw, y, Ui.MUTED);
			Ui.meter(g, ix, y + 11, iw, 3, SkillView.fill(e), Ui.GOLD);
		}

		// The two perks, with their totals
		int colW = (iw - 4) / 2;
		y += 20;
		for (int i = 0; i < 2; i++) {
			MasteryPerk perk = MasteryPerk.of(skill, i == 0 ? "a" : "b");
			int cx = ix + i * (colW + 4);
			Ui.tile(g, cx, y, colW, 29, Ui.mix(Ui.INK_RAISED, Ui.GOLD, 0.35));
			int ranks = d == null ? 0 : Mastery.ranks(d, perk);
			double now = Minecraft.getInstance().player == null ? 0 : Mastery.bonus(Minecraft.getInstance().player, perk);
			String total = "+" + Ui.percent(ranks * Config.MASTERY_PERK_STEP);
			Ui.text(g, this.font, Ui.ellipsize(this.font, Ui.tr(perk.translationKey()), colW - 8 - this.font.width(total)), cx + 4, y + 3, Ui.TEXT);
			Ui.right(g, this.font, total, cx + colW - 4, y + 3, ranks > 0 ? Ui.GOLD : Ui.DIM);
			List<String> desc = Ui.wrap(this.font, Ui.tr(perk.translationKey() + ".desc"), colW - 8);
			for (int l = 0; l < desc.size() && l < 2; l++) {
				Ui.text(g, this.font, desc.get(l), cx + 4, y + 12 + l * 8, Ui.MUTED);
			}
			if (ranks > 0 && now + 1e-9 < ranks * Config.MASTERY_PERK_STEP && mouseX >= cx && mouseX < cx + colW && mouseY >= y && mouseY < y + 29) {
				g.setComponentTooltipForNextFrame(this.font, List.of(Component.literal(Ui.tr("gui.extremeprogression.mastery.weaker", "+" + percentExact(now)))), mouseX, mouseY);
			}
		}

		// The ten ranks
		int ry = ranksY();
		int waiting = d == null ? 0 : Mastery.waiting(d);
		chooseA.visible = chooseB.visible = waiting > 0;
		for (int rank = 1; rank <= max; rank++) {
			int rowY = ry + (rank - 1) * ROW;
			boolean held = e.masteryRanks() >= rank;
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.mastery.row", roman(rank)), ix, rowY + 2, held ? Ui.GOLD : Ui.DIM);
			if (rank == waiting) {
				chooseA.setY(rowY);
				chooseB.setY(rowY);
				continue;
			}
			String track = d != null && rank <= d.perks.size() && held ? d.perks.get(rank - 1) : null;
			if (held && track == null) {
				// (a later rank waiting too: it is chosen after the one before it)
				Ui.text(g, this.font, Ui.tr("gui.extremeprogression.mastery.later"), x0 + CELLS_X, rowY + 2, Ui.WARN);
				continue;
			}
			for (int i = 0; i < 2; i++) {
				MasteryPerk perk = MasteryPerk.of(skill, i == 0 ? "a" : "b");
				int cx = x0 + CELLS_X + i * (CELL_W + 4);
				boolean chosen = perk.track.equals(track);
				String label = (chosen ? "✓ " : "") + Ui.tr(perk.translationKey());
				if (chosen) {
					g.fill(cx - 2, rowY, cx + CELL_W, rowY + ROW - 1, Ui.argb(0.25, Ui.GOLD));
				}
				Ui.text(g, this.font, Ui.ellipsize(this.font, label, CELL_W - 4), cx, rowY + 2, chosen ? Ui.GOLD : Ui.DIM);
			}
		}
		int fy = ry + max * ROW + 2;
		Ui.text(g, this.font, Ui.ellipsize(this.font, Ui.tr("gui.extremeprogression.mastery.final"), iw), ix, fy, Ui.DIM);
	}

	private static String percentExact(double fraction) {
		return String.format(java.util.Locale.ROOT, "%.1f%%", fraction * 100);
	}

	@Override
	public void onClose() {
		if (parent != null) {
			Minecraft.getInstance().gui.setScreen(parent);
		} else {
			super.onClose();
		}
	}

	public boolean isPauseScreen() {
		return false;
	}
}
