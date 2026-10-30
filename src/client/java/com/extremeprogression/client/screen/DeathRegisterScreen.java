package com.extremeprogression.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.client.state.ClientProgress;
import com.extremeprogression.client.ui.DeathView;
import com.extremeprogression.client.ui.Ui;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.DeathData;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.death.DeathRules;
import com.extremeprogression.network.ProgressPayload;
import com.extremeprogression.skill.Skill;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Death Register (design §9, screen 4): your deaths, the death pressure and when it drops, death fatigue, the
 * dimensions' factors, what you would lose if you died now ("At Stake") and your recent deaths. "At Stake" runs the
 * server's own death rules on a copy of the progression the server sent.
 */
public class DeathRegisterScreen extends Screen {
	static final int W = 404, MIN_H = 222, MAX_H = 300;
	private static final int LEFT_W = 136;
	private static final String[] DIMENSIONS = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end", "extremedimensions:the_seam"};

	private static int opened = 0;

	private final Screen parent;
	private int x0, y0, h;
	/** The history scrolled this many deaths back from the newest. */
	private int scroll = 0;

	/** "At Stake" and the highest skill, worked out again whenever new progression arrives. */
	private List<DeathData.Loss> stake = List.of();
	private Skill top = Skill.MELEE;
	private int topLevel = 0;
	private int stakeFor = -1;

	public DeathRegisterScreen(Screen parent) {
		super(Component.translatable("gui.extremeprogression.death.title"));
		this.parent = parent;
		opened++;
	}

	/** How many times a Death Register was opened (for tests). */
	public static int opened() {
		return opened;
	}

	@Override
	protected void init() {
		h = Math.max(MIN_H, Math.min(MAX_H, this.height - 8));
		x0 = (this.width - W) / 2;
		y0 = Math.max(2, (this.height - h) / 2);
		this.addRenderableWidget(Button.builder(Component.translatable(parent != null ? "gui.back" : "gui.done"), b -> this.onClose())
				.pos(this.width / 2 - 50, y0 + h - 20).size(100, 20).build());
	}

	/** What the next death here would cost every skill, on a copy (for this screen and for tests). */
	public static List<DeathData.Loss> atStake() {
		ProgressPayload.Death d = ClientProgress.death();
		return DeathRules.apply(ClientProgress.asProgress(), d.share(), d.whole());
	}

	private void refresh() {
		if (stakeFor == ClientProgress.updates()) {
			return;
		}
		stakeFor = ClientProgress.updates();
		PlayerProgress copy = ClientProgress.asProgress();
		top = DeathRules.highest(copy);
		topLevel = copy.get(top).level;
		stake = atStake();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		refresh();
		Ui.centered(g, this.font, this.title.getString(), this.width / 2, y0 + 2, Ui.TEXT);
		int ly = y0 + 14, lh = h - 40;
		left(g, x0, ly, lh);
		int rx = x0 + LEFT_W + 6, rw = W - LEFT_W - 6;
		int stakeH = stake(g, rx, ly, rw, mouseX, mouseY);
		history(g, rx, ly + stakeH + 4, rw, lh - stakeH - 4, mouseX, mouseY);
	}

	// --- The left panel: deaths, pressure, fatigue and the dimensions ----------------------------

	private void left(GuiGraphicsExtractor g, int x, int y, int lh) {
		Ui.panel(g, x, y, LEFT_W, lh);
		ProgressPayload.Death d = ClientProgress.death();
		int ix = x + 6, iw = LEFT_W - 12, cy = y + 6;
		row(g, x, cy, Ui.tr("gui.extremeprogression.death.deaths"), String.valueOf(d.deaths()), Ui.TEXT);
		int pressure = d.pressure();
		row(g, x, cy + 11, Ui.tr("gui.extremeprogression.death.pressure"), String.valueOf(pressure), pressureColor(pressure));
		// The three steps of the pressure: 0 · 50%, 1 · 100%, 2+ · and a whole level
		int seg = (iw - 4) / 3, sy = cy + 23;
		String[] labels = {
				Ui.tr("gui.extremeprogression.death.step0", Ui.percent(Config.DEATH_BASE)),
				Ui.tr("gui.extremeprogression.death.step1", Ui.percent(Config.DEATH_BASE_PRESSURED)),
				Config.DEATH_WHOLE_LOSS ? Ui.tr("gui.extremeprogression.death.step2", Config.DEATH_WHOLE_LOSS_PRESSURE) : Ui.tr("gui.extremeprogression.death.step2_off")};
		int step = Math.min(2, Math.max(0, pressure >= Config.DEATH_WHOLE_LOSS_PRESSURE ? 2 : pressure));
		for (int i = 0; i < 3; i++) {
			int sx = ix + i * (seg + 2);
			boolean here = i == step;
			int color = i == 2 ? Ui.DANGER : i == 1 ? Ui.WARN : Ui.MUTED;
			g.fill(sx, sy, sx + seg, sy + 3, Ui.argb(255, here ? color : Ui.mix(color, Ui.SHADOW, 0.75)));
			Ui.centered(g, this.font, labels[i], sx + seg / 2, sy + 5, here ? color : Ui.DIM);
		}
		int ty = sy + 17;
		String drops = pressure <= 0 ? Ui.tr("gui.extremeprogression.death.no_pressure")
				: Ui.tr("gui.extremeprogression.death.drops_in", DeathView.duration(ClientProgress.pressureDropsIn()));
		for (String line : Ui.wrap(this.font, drops, iw)) {
			Ui.text(g, this.font, line, ix, ty, Ui.MUTED);
			ty += 9;
		}
		long fatigue = ClientProgress.fatigueLeft();
		ty += 3;
		row(g, x, ty, Ui.tr("gui.extremeprogression.death.fatigue"), fatigue > 0 ? DeathView.duration(fatigue) : Ui.tr("gui.extremeprogression.death.none"),
				fatigue > 0 ? Ui.DANGER : Ui.MUTED);
		ty += 10;
		if (fatigue > 0) {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.death.fatigue_effect", Ui.percent(Config.DEATH_FATIGUE_POWER)), ix, ty, Ui.MUTED);
			ty += 10;
		}
		Ui.divider(g, ix, ty + 1, iw);
		ty += 6;
		Ui.text(g, this.font, Ui.tr("gui.extremeprogression.death.per_dimension"), ix, ty, Ui.MUTED);
		ty += 11;
		boolean ed = FabricLoader.getInstance().isModLoaded("extremedimensions");
		String here = Minecraft.getInstance().level == null ? "" : Minecraft.getInstance().level.dimension().identifier().toString();
		for (String dim : DIMENSIONS) {
			if (dim.startsWith("extremedimensions:") && !ed) {
				continue;
			}
			boolean now = dim.equals(here);
			if (now) {
				Ui.tile(g, ix - 2, ty - 2, iw + 4, 11, Ui.TEXT);
			}
			String name = Ui.ellipsize(this.font, DeathView.dimension(dim), iw - 30);
			Ui.text(g, this.font, name, ix, ty, now ? Ui.TEXT : Ui.MUTED);
			Ui.right(g, this.font, DeathView.factor(DeathRules.multiplier(dim, ed)), ix + iw, ty, now ? Ui.TEXT : Ui.MUTED);
			ty += 11;
		}
		// At the foot: the loss is permanent
		List<String> foot = Ui.wrap(this.font, Ui.tr("gui.extremeprogression.death.permanent_hint"), iw);
		int fy = y + lh - 6 - 9 * (foot.size() + 1);
		if (fy > ty) {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.death.permanent"), ix, fy, Ui.DANGER);
			for (String line : foot) {
				fy += 9;
				Ui.text(g, this.font, line, ix, fy, Ui.MUTED);
			}
		}
	}

	private static int pressureColor(int pressure) {
		return pressure >= Config.DEATH_WHOLE_LOSS_PRESSURE ? Ui.DANGER : pressure >= 1 ? Ui.WARN : Ui.TEXT;
	}

	private void row(GuiGraphicsExtractor g, int x, int y, String label, String value, int color) {
		Ui.text(g, this.font, label, x + 6, y, Ui.MUTED);
		Ui.right(g, this.font, value, x + LEFT_W - 6, y, color);
	}

	// --- At Stake --------------------------------------------------------------------------------

	/** The skills in two columns of five, each with its loss and a bar of its step: kept, lost (red) and still to earn. Returns the panel's height. */
	private int stake(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY) {
		ProgressPayload.Death d = ClientProgress.death();
		ProgressPayload p = ClientProgress.current();
		List<String> warning = Ui.wrap(this.font, warning(d), w - 16);
		int height = 30 + 5 * 15 + 4 + 9 * warning.size() + 6;
		Ui.panel(g, x, y, w, height);
		int ix = x + 6, iw = w - 12;
		Ui.text(g, this.font, Ui.tr("gui.extremeprogression.death.stake"), ix, y + 5, Ui.TEXT);
		String here = Minecraft.getInstance().level == null ? "minecraft:overworld" : Minecraft.getInstance().level.dimension().identifier().toString();
		double base = d.multiplier() > 0 ? d.share() / d.multiplier() : d.share();
		String basis = Ui.tr("gui.extremeprogression.death.stake_basis", d.pressure(), Ui.percent(base), DeathView.dimension(here), DeathView.factor(d.multiplier()));
		Ui.text(g, this.font, Ui.ellipsize(this.font, basis, iw), ix, y + 16, Ui.MUTED);
		int colW = (iw - 8) / 2;
		List<Component> tip = null;
		for (int i = 0; i < stake.size() && i < 10; i++) {
			DeathData.Loss l = stake.get(i);
			ProgressPayload.Entry e = ClientProgress.skill(l.skill);
			int cx = ix + (i / 5) * (colW + 8), cy = y + 30 + (i % 5) * 15;
			int accent = DeathView.accent(l.skill);
			boolean lostSome = l.any();
			String lossText = lostSome ? lossShort(l) : "0";
			int lossW = this.font.width(lossText);
			String name = Ui.ellipsize(this.font, DeathView.skillName(l.skill) + " " + l.levelBefore, colW - lossW - 6);
			Ui.text(g, this.font, name, cx, cy, accent);
			Ui.right(g, this.font, lossText, cx + colW, cy, lostSome ? Ui.DANGER : Ui.DIM);
			// The bar: the step at the moment, what would go in red
			double need = e == null ? 0 : l.mastery ? (p == null ? 0 : p.masteryXpPerRank()) : e.need();
			double have = e == null ? 0 : l.mastery ? e.masteryXp() : e.xp();
			double lost = Math.min(have, l.xp);
			int by = cy + 10;
			g.fill(cx, by, cx + colW, by + 2, Ui.argb(255, Ui.mix(accent, Ui.SHADOW, 0.7)));
			if (need > 0) {
				int keepW = (int) Math.round(colW * Ui.clamp01((have - lost) / need));
				int lostW = (int) Math.round(colW * Ui.clamp01(have / need)) - keepW;
				g.fill(cx, by, cx + keepW, by + 2, Ui.argb(255, accent));
				g.fill(cx + keepW, by, cx + keepW + Math.max(0, lostW), by + 2, Ui.argb(255, Ui.DANGER));
			}
			if (mouseX >= cx && mouseX < cx + colW && mouseY >= cy - 1 && mouseY < cy + 13) {
				tip = new ArrayList<>();
				tip.add(Component.literal(DeathView.skillName(l.skill)).withStyle(s -> s.withColor(accent)));
				tip.add(Component.literal(Ui.tr("gui.extremeprogression.death.stake_tip_share", Ui.percent(DeathRules.fraction(l.levelBefore, topLevel, d.share())))));
				tip.add(Component.literal(DeathView.loss(l)).withStyle(s -> s.withColor(lostSome ? Ui.DANGER : Ui.MUTED)));
				if (need > 0) {
					tip.add(Component.literal(Ui.tr(l.mastery ? "gui.extremeprogression.death.stake_tip_after_mastery" : "gui.extremeprogression.death.stake_tip_after",
							Ui.number(have - lost), Ui.number(need))).withStyle(s -> s.withColor(Ui.MUTED)));
				}
			}
		}
		int wy = y + 30 + 5 * 15 + 2;
		int boxColor = d.whole() ? Ui.DANGER : Ui.WARN;
		g.fill(ix - 1, wy - 2, ix + iw + 1, wy - 1, Ui.argb(255, boxColor));
		for (String line : warning) {
			Ui.text(g, this.font, line, ix + 2, wy + 1, boxColor);
			wy += 9;
		}
		if (tip != null) {
			g.setComponentTooltipForNextFrame(this.font, tip, mouseX, mouseY);
		}
		return height;
	}

	/** The short loss in a cell: "−12,345", "−1 lvl", "−1 rank". */
	private static String lossShort(DeathData.Loss l) {
		if (l.ranksAfter < l.ranksBefore) {
			return Ui.tr("gui.extremeprogression.death.short_rank", l.ranksBefore - l.ranksAfter);
		}
		if (l.levelAfter < l.levelBefore) {
			return Ui.tr("gui.extremeprogression.death.short_level", l.levelBefore - l.levelAfter);
		}
		return "−" + Ui.number(l.xp);
	}

	/** What the death pressure means for this death and the next. */
	private String warning(ProgressPayload.Death d) {
		String skill = DeathView.skillName(top.id);
		ProgressPayload.Entry e = ClientProgress.skill(top.id);
		boolean rank = e != null && e.masteryRanks() > 0;
		String whole = rank ? Ui.tr("gui.extremeprogression.death.whole_rank", skill) : Ui.tr("gui.extremeprogression.death.whole_level", skill);
		if (d.whole()) {
			return Ui.tr("gui.extremeprogression.death.warning_now", d.pressure(), whole);
		}
		int next = d.pressure() + 1;
		if (DeathRules.whole(next)) {
			return Ui.tr("gui.extremeprogression.death.warning_next", next, whole);
		}
		return Ui.tr("gui.extremeprogression.death.warning_base", next, Ui.percent(DeathRules.base(next)));
	}

	// --- The history -----------------------------------------------------------------------------

	private int historyRows(int height) {
		return Math.max(1, (height - 18) / 10);
	}

	private void history(GuiGraphicsExtractor g, int x, int y, int w, int height, int mouseX, int mouseY) {
		Ui.panel(g, x, y, w, height);
		int ix = x + 6, iw = w - 12;
		List<DeathData.Record> all = ClientProgress.deathHistory();
		int rows = historyRows(height);
		scroll = Math.max(0, Math.min(scroll, all.size() - rows));
		String title = Ui.tr("gui.extremeprogression.death.history", all.size());
		Ui.text(g, this.font, title, ix, y + 5, Ui.TEXT);
		if (all.size() > rows) {
			Ui.right(g, this.font, Ui.tr("gui.extremeprogression.death.history_scroll", scroll + 1, Math.min(all.size(), scroll + rows), all.size()), ix + iw, y + 5, Ui.DIM);
		}
		if (all.isEmpty()) {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.death.history_empty"), ix, y + 17, Ui.MUTED);
			return;
		}
		List<Component> tip = null;
		for (int i = 0; i < rows && scroll + i < all.size(); i++) {
			DeathData.Record r = all.get(all.size() - 1 - scroll - i);
			int ry = y + 17 + i * 10;
			boolean hard = r.losses.stream().anyMatch(l -> l.levelAfter < l.levelBefore || l.ranksAfter < l.ranksBefore);
			Ui.text(g, this.font, Ui.ellipsize(this.font, DeathView.line(r), iw), ix, ry, hard ? Ui.DANGER : Ui.MUTED);
			if (mouseX >= ix && mouseX < ix + iw && mouseY >= ry - 1 && mouseY < ry + 9) {
				tip = details(r);
			}
		}
		if (tip != null) {
			g.setComponentTooltipForNextFrame(this.font, tip, mouseX, mouseY);
		}
	}

	/** Everything about one death: when, where, why, the pressure and the factor, and every skill's loss. */
	static List<Component> details(DeathData.Record r) {
		List<Component> out = new ArrayList<>();
		out.add(Component.literal(DeathView.when(r.time) + " · " + DeathView.dimension(r.dimension) + " " + DeathView.factor(r.multiplier)));
		if (!r.cause.isEmpty()) {
			out.add(Component.literal(r.cause).withStyle(s -> s.withColor(Ui.TEXT)));
		}
		out.add(Component.literal(Ui.tr("gui.extremeprogression.death.details_place", r.x, r.y, r.z, r.pressure)).withStyle(s -> s.withColor(Ui.MUTED)));
		List<DeathData.Loss> lost = DeathView.lost(r);
		if (lost.isEmpty()) {
			out.add(Component.literal(Ui.tr("gui.extremeprogression.death.lost_nothing")).withStyle(s -> s.withColor(Ui.MUTED)));
		}
		for (DeathData.Loss l : lost) {
			int accent = DeathView.accent(l.skill);
			out.add(Component.literal(DeathView.skillName(l.skill) + ": ").withStyle(s -> s.withColor(accent))
					.append(Component.literal(DeathView.loss(l)).withStyle(s -> s.withColor(Ui.DANGER))));
		}
		return out;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) {
			scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
