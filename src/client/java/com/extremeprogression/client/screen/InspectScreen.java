package com.extremeprogression.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.client.ui.SkillView;
import com.extremeprogression.client.ui.Ui;
import com.extremeprogression.mastery.MasteryPerk;
import com.extremeprogression.network.InspectPayload;
import com.extremeprogression.network.ProgressPayload;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.skill.Specialization;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * An operator's read-only look at another player's progression ({@code /skills inspect}, design §11): their totals,
 * deaths and synergies, and every skill's level, specializations, points and Mastery perks. Nothing can be changed here.
 */
public class InspectScreen extends Screen {
	static final int W = 404, H = 222;
	private static final int LEFT_W = 112, TILE_H = 32;

	private static int opened = 0;

	private final InspectPayload data;
	private int x0, y0;

	public InspectScreen(InspectPayload data) {
		super(Component.translatable("gui.extremeprogression.inspect.title", data.name()));
		this.data = data;
		opened++;
	}

	public static int opened() {
		return opened;
	}

	@Override
	protected void init() {
		x0 = (this.width - W) / 2;
		y0 = Math.max(2, (this.height - H) / 2);
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose()).pos(this.width / 2 - 50, y0 + H - 20).size(100, 20).build());
	}

	private ProgressPayload.Entry entry(Skill s) {
		for (ProgressPayload.Entry e : data.progress().skills()) {
			if (e.id().equals(s.id)) {
				return e;
			}
		}
		return null;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		Ui.centered(g, this.font, this.title.getString(), this.width / 2, y0 + 2, Ui.TEXT);
		ProgressPayload p = data.progress();
		int ly = y0 + 14, lh = H - 40;
		Ui.panel(g, x0, ly, LEFT_W, lh);
		int ix = x0 + 6, y = ly + 6;
		Ui.text(g, this.font, Ui.ellipsize(this.font, data.name(), LEFT_W - 12), ix, y, Ui.TEXT);
		Ui.text(g, this.font, Ui.tr(data.online() ? "gui.extremeprogression.inspect.online" : "gui.extremeprogression.inspect.offline"), ix, y + 10,
				data.online() ? Ui.OK : Ui.MUTED);
		Ui.divider(g, ix, y + 22, LEFT_W - 12);
		y += 28;
		int ranks = 0;
		for (ProgressPayload.Entry e : p.skills()) {
			ranks += e.masteryRanks();
		}
		y = row(g, y, Ui.tr("gui.extremeprogression.sheet.total"), p.total() + " / " + p.cap(), Ui.TEXT);
		y = row(g, y, Ui.tr("gui.extremeprogression.sheet.mastery"), String.valueOf(ranks), ranks > 0 ? Ui.GOLD : Ui.TEXT);
		y = row(g, y, Ui.tr("gui.extremeprogression.death.deaths"), String.valueOf(p.death().deaths()), Ui.TEXT);
		y = row(g, y, Ui.tr("gui.extremeprogression.death.pressure"), String.valueOf(p.death().pressure()), p.death().pressure() > 0 ? Ui.WARN : Ui.TEXT);
		y = row(g, y, Ui.tr("gui.extremeprogression.inspect.synergies"), data.synergies() + " / 12", Ui.TEXT);
		Ui.text(g, this.font, Ui.ellipsize(this.font, Ui.tr("gui.extremeprogression.inspect.read_only"), LEFT_W - 12), ix, ly + lh - 12, Ui.DIM);

		// Every skill: level, specializations, points, perks
		int rx = x0 + LEFT_W + 6, rw = W - LEFT_W - 6;
		Ui.panel(g, rx, ly, rw, lh);
		int tw = (rw - 12 - 4) / 2;
		List<Component> tip = null;
		for (Skill s : Skill.values()) {
			int i = s.ordinal();
			int tx = rx + 6 + (i % 2) * (tw + 4), ty = ly + 5 + (i / 2) * (TILE_H + 2);
			ProgressPayload.Entry e = entry(s);
			Ui.tile(g, tx, ty, tw, TILE_H, Ui.mix(Ui.INK_RAISED, s.category.accent, 0.4));
			g.fakeItem(SkillView.icon(s), tx + 3, ty + 3);
			boolean mastered = e != null && e.level() >= p.maxLevel();
			String level = e == null ? "0" : mastered ? "M" + e.masteryRanks() : "Lv " + e.level();
			Ui.right(g, this.font, level, tx + tw - 4, ty + 3, mastered ? Ui.GOLD : Ui.TEXT);
			Ui.text(g, this.font, Ui.ellipsize(this.font, Ui.tr(s.translationKey()), tw - 26 - this.font.width(level) - 6), tx + 22, ty + 3, s.category.accent);
			List<String> specs = new ArrayList<>();
			if (e != null) {
				for (String id : e.specs()) {
					Specialization spec = Specialization.byId(id);
					specs.add(spec == null ? id : Ui.tr(spec.translationKey()));
				}
			}
			String second = specs.isEmpty() ? Ui.tr("gui.extremeprogression.inspect.no_spec") : String.join(" · ", specs);
			Ui.text(g, this.font, Ui.ellipsize(this.font, second, tw - 26), tx + 22, ty + 13, specs.isEmpty() ? Ui.DIM : Ui.MUTED);
			int bought = e == null ? 0 : e.bought().size();
			Ui.text(g, this.font, Ui.ellipsize(this.font, Ui.tr("gui.extremeprogression.inspect.points", bought), tw - 26), tx + 22, ty + 22, Ui.DIM);
			if (mouseX >= tx && mouseX < tx + tw && mouseY >= ty && mouseY < ty + TILE_H && e != null) {
				tip = tip(s, e);
			}
		}
		if (tip != null) {
			g.setComponentTooltipForNextFrame(this.font, tip, mouseX, mouseY);
		}
	}

	private List<Component> tip(Skill s, ProgressPayload.Entry e) {
		List<Component> out = new ArrayList<>();
		out.add(Component.translatable(s.translationKey()).withStyle(st -> st.withColor(s.category.accent)));
		out.add(Component.literal(Ui.tr("gui.extremeprogression.tile.level", e.level(), data.progress().maxLevel())));
		out.add(Component.literal(Ui.tr("gui.extremeprogression.tile.xp", Ui.number(e.xp()), Ui.number(e.need()), Ui.percent(e.need() > 0 ? e.xp() / e.need() : 0)))
				.withStyle(st -> st.withColor(Ui.MUTED)));
		if (!e.perks().isEmpty()) {
			int a = 0, b = 0;
			for (String t : e.perks()) {
				if (t.equals("a")) {
					a++;
				} else {
					b++;
				}
			}
			out.add(Component.literal(Ui.tr(MasteryPerk.of(s, "a").translationKey()) + " " + a + " · " + Ui.tr(MasteryPerk.of(s, "b").translationKey()) + " " + b)
					.withStyle(st -> st.withColor(Ui.GOLD)));
		}
		return out;
	}

	private int row(GuiGraphicsExtractor g, int y, String label, String value, int color) {
		Ui.text(g, this.font, label, x0 + 6, y, Ui.MUTED);
		Ui.right(g, this.font, value, x0 + LEFT_W - 6, y, color);
		return y + 11;
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(null);
	}

	public boolean isPauseScreen() {
		return false;
	}
}
