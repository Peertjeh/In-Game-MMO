package com.extremeprogression.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.client.state.ClientProgress;
import com.extremeprogression.client.ui.DeathView;
import com.extremeprogression.client.ui.Ui;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.DeathData;
import com.extremeprogression.death.DeathRules;
import com.extremeprogression.network.ProgressPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * After respawning (design §7 and §9): exactly what the death cost, every skill that lost something, the death fatigue
 * now on, and what the death pressure means for the next death. Shown once per death, as soon as no other screen is open.
 */
public class DeathSummaryScreen extends Screen {
	private static final int W = 300;
	private static final int MAX_ROWS = 10;

	private static int opened = 0;

	private final DeathData.Record death;
	private final List<DeathData.Loss> lost;
	private int x0, y0, h;

	public DeathSummaryScreen(DeathData.Record death) {
		super(Component.translatable("gui.extremeprogression.death.summary.title"));
		this.death = death;
		this.lost = DeathView.lost(death);
		opened++;
	}

	/** How many times a summary was shown (for tests). */
	public static int opened() {
		return opened;
	}

	@Override
	protected void init() {
		int rows = Math.max(1, Math.min(MAX_ROWS, lost.size()));
		h = 94 + 10 * rows + 9 * notes().size();
		x0 = (this.width - W) / 2;
		y0 = Math.max(2, (this.height - h) / 2);
		this.addRenderableWidget(Button.builder(Component.translatable("gui.extremeprogression.death.title"),
				b -> Minecraft.getInstance().gui.setScreen(new DeathRegisterScreen(null))).pos(this.width / 2 - 102, y0 + h - 22).size(100, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.extremeprogression.death.summary.continue"), b -> this.onClose())
				.pos(this.width / 2 + 2, y0 + h - 22).size(100, 20).build());
	}

	/** The notes under the losses: death fatigue, and what the death pressure now means. */
	private List<String> notes() {
		List<String> out = new ArrayList<>();
		int iw = W - 12;
		long fatigue = ClientProgress.fatigueLeft();
		if (fatigue > 0) {
			out.addAll(Ui.wrap(this.font, Ui.tr("gui.extremeprogression.death.summary.fatigue", Ui.percent(Config.DEATH_FATIGUE_POWER), DeathView.duration(fatigue)), iw));
		}
		ProgressPayload.Death d = ClientProgress.death();
		String pressure = DeathRules.whole(d.pressure())
				? Ui.tr("gui.extremeprogression.death.summary.pressure_whole", d.pressure(), DeathView.duration(ClientProgress.pressureDropsIn()))
				: Ui.tr("gui.extremeprogression.death.summary.pressure", d.pressure(), Ui.percent(DeathRules.base(d.pressure())));
		out.addAll(Ui.wrap(this.font, pressure, iw));
		return out;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		Ui.panel(g, x0, y0, W, h);
		int ix = x0 + 6, iw = W - 12, y = y0 + 6;
		Ui.centered(g, this.font, this.title.getString(), this.width / 2, y, Ui.DANGER);
		y += 12;
		if (!death.cause.isEmpty()) {
			Ui.centered(g, this.font, Ui.ellipsize(this.font, death.cause, iw), this.width / 2, y, Ui.TEXT);
		}
		y += 10;
		String place = Ui.tr("gui.extremeprogression.death.summary.place", DeathView.dimension(death.dimension), DeathView.factor(death.multiplier),
				death.x, death.y, death.z, death.pressure);
		Ui.centered(g, this.font, Ui.ellipsize(this.font, place, iw), this.width / 2, y, Ui.MUTED);
		y += 12;
		Ui.divider(g, ix, y, iw);
		y += 6;
		Ui.text(g, this.font, Ui.tr("gui.extremeprogression.death.summary.lost"), ix, y, Ui.TEXT);
		y += 12;
		if (lost.isEmpty()) {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.death.lost_nothing"), ix, y, Ui.MUTED);
			y += 10;
		}
		for (int i = 0; i < lost.size() && i < MAX_ROWS; i++) {
			DeathData.Loss l = lost.get(i);
			Ui.text(g, this.font, DeathView.skillName(l.skill), ix, y, DeathView.accent(l.skill));
			boolean hard = l.levelAfter < l.levelBefore || l.ranksAfter < l.ranksBefore;
			Ui.right(g, this.font, DeathView.loss(l), ix + iw, y, hard ? Ui.DANGER : Ui.MUTED);
			y += 10;
		}
		y += 4;
		Ui.divider(g, ix, y - 2, iw);
		y += 2;
		for (String line : notes()) {
			Ui.text(g, this.font, line, ix, y, Ui.WARN);
			y += 9;
		}
	}

	public boolean isPauseScreen() {
		return false;
	}
}
