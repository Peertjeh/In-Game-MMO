package com.extremeprogression.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.client.state.ClientSynergies;
import com.extremeprogression.client.ui.DeathView;
import com.extremeprogression.client.ui.SkillView;
import com.extremeprogression.client.ui.Ui;
import com.extremeprogression.config.Config;
import com.extremeprogression.network.SynergyPayload;
import com.extremeprogression.synergy.Synergy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Codex and the Chronicle (design §9, screen 5). The Codex holds your own synergies: those you discovered with when
 * and where, and the rest as runes with a hint once one of their skills is at level 30, a clearer one after somebody on
 * the server found it first. The Chronicle holds the server's first discoverers.
 */
public class CodexScreen extends Screen {
	static final int W = 404, H = 222;
	private static final int ROW = 12;
	/** Runic letters for what is not discovered yet (in the game's fallback font). */
	private static final String RUNES = "ᚠᚢᚦᚨᚱᚲᚷᚹᚺᚾᛁᛃᛇᛈᛉᛊᛏᛒᛖᛗᛚᛜᛞᛟ";

	private static int opened = 0;

	private final Screen parent;
	private int x0, y0;
	private boolean chronicle = false;
	private Button codexTab, chronicleTab;

	public CodexScreen(Screen parent) {
		super(Component.translatable("gui.extremeprogression.codex.title"));
		this.parent = parent;
		opened++;
	}

	/** How many times the Codex was opened (for tests). */
	public static int opened() {
		return opened;
	}

	@Override
	protected void init() {
		x0 = (this.width - W) / 2;
		y0 = Math.max(2, (this.height - H) / 2);
		codexTab = this.addRenderableWidget(Button.builder(Component.translatable("gui.extremeprogression.codex.tab"), b -> show(false))
				.pos(x0 + 4, y0 + 14).size(90, 14).build());
		chronicleTab = this.addRenderableWidget(Button.builder(Component.translatable("gui.extremeprogression.chronicle.tab"), b -> show(true))
				.pos(x0 + 96, y0 + 14).size(90, 14).build());
		this.addRenderableWidget(Button.builder(Component.translatable(parent != null ? "gui.back" : "gui.done"), b -> this.onClose())
				.pos(this.width / 2 - 50, y0 + H - 20).size(100, 20).build());
		show(chronicle);
	}

	/** Shows the Chronicle (true) or the Codex. */
	public void show(boolean chronicle) {
		this.chronicle = chronicle;
		codexTab.active = chronicle;
		chronicleTab.active = !chronicle;
	}

	// --- What a synergy shows --------------------------------------------------------------------

	/** How much of a synergy the player may see: 0 nothing yet, 1 runes and a vague hint, 2 a clearer hint, 3 all of it. */
	public static int stage(Synergy s) {
		SynergyPayload.Entry e = ClientSynergies.entry(s);
		if (e != null && e.known()) {
			return 3;
		}
		boolean near = SkillView.entry(s.first).level() >= Config.SYNERGY_LEVEL || SkillView.entry(s.second).level() >= Config.SYNERGY_LEVEL;
		if (!near) {
			return 0;
		}
		return e != null && e.foundOnServer() ? 2 : 1;
	}

	/** A name in runes; with {@code partly}, every other letter readable. */
	static String runes(String name, boolean partly) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (!Character.isLetter(c) || partly && i % 2 == 0) {
				out.append(c);
			} else {
				int n = Character.toLowerCase(c) - 'a';
				out.append(RUNES.charAt(Math.floorMod(n, RUNES.length())));
			}
		}
		return out.toString();
	}

	private static String name(Synergy s, int stage) {
		String plain = Ui.tr(s.translationKey());
		return stage >= 3 ? plain : runes(plain, stage == 2);
	}

	private static String skills(Synergy s) {
		return Ui.tr(s.first.translationKey()) + " + " + Ui.tr(s.second.translationKey());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		Ui.centered(g, this.font, this.title.getString(), this.width / 2, y0 + 2, Ui.GOLD);
		int py = y0 + 30, ph = H - 56;
		Ui.panel(g, x0, py, W, ph);
		int ix = x0 + 6, iw = W - 12;
		String count = Ui.tr("gui.extremeprogression.codex.count", ClientSynergies.discovered(), Synergy.values().length);
		Ui.right(g, this.font, count, x0 + W - 4, y0 + 17, Ui.MUTED);
		List<Component> tip = null;
		int y = py + 5;
		for (Synergy s : Synergy.values()) {
			int stage = stage(s);
			SynergyPayload.Entry e = ClientSynergies.entry(s);
			boolean hover = mouseX >= ix && mouseX < ix + iw && mouseY >= y - 1 && mouseY < y + ROW - 2;
			if (hover) {
				g.fill(ix - 2, y - 2, ix + iw + 2, y + ROW - 3, Ui.argb(0.12, Ui.TEXT));
			}
			if (chronicle) {
				SynergyPayload.Entry found = chronicleRow(g, s, e, stage, ix, iw, y);
				if (hover && found != null) {
					tip = chronicleTip(s, found, stage);
				}
			} else {
				codexRow(g, s, e, stage, ix, iw, y);
				if (hover) {
					tip = codexTip(s, e, stage);
				}
			}
			y += ROW;
		}
		Ui.text(g, this.font, Ui.ellipsize(this.font, Ui.tr(chronicle ? "gui.extremeprogression.chronicle.footer" : "gui.extremeprogression.codex.footer",
				Config.SYNERGY_LEVEL), iw), ix, py + ph - 12, Ui.DIM);
		if (tip != null) {
			g.setComponentTooltipForNextFrame(this.font, tip, mouseX, mouseY);
		}
	}

	private void codexRow(GuiGraphicsExtractor g, Synergy s, SynergyPayload.Entry e, int stage, int ix, int iw, int y) {
		if (stage == 0) {
			Ui.text(g, this.font, "? ? ?", ix, y, Ui.DIM);
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.codex.out_of_reach"), ix + 118, y, Ui.DIM);
			return;
		}
		int nameColor = stage == 3 ? Ui.GOLD : Ui.MUTED;
		Ui.text(g, this.font, Ui.ellipsize(this.font, name(s, stage), 112), ix, y, nameColor);
		String mid = stage >= 2 ? skills(s) : Ui.tr("gui.extremeprogression.codex.unknown_skills");
		Ui.text(g, this.font, Ui.ellipsize(this.font, mid, 118), ix + 118, y, stage >= 2 ? Ui.TEXT : Ui.DIM);
		String right = stage == 3 ? Ui.tr(s.translationKey() + ".desc") : Ui.tr(s.translationKey() + (stage == 2 ? ".hint2" : ".hint1"));
		if (stage == 3 && e != null && e.firstMine()) {
			right = "★ " + right;
		}
		Ui.text(g, this.font, Ui.ellipsize(this.font, right, iw - 242), ix + 242, y, stage == 3 ? Ui.TEXT : Ui.MUTED);
	}

	private List<Component> codexTip(Synergy s, SynergyPayload.Entry e, int stage) {
		List<Component> out = new ArrayList<>();
		if (stage == 0) {
			out.add(Component.literal(Ui.tr("gui.extremeprogression.codex.out_of_reach_tip", Config.SYNERGY_LEVEL)).withStyle(st -> st.withColor(Ui.MUTED)));
			return out;
		}
		out.add(Component.literal(name(s, stage)).withStyle(st -> st.withColor(stage == 3 ? Ui.GOLD : Ui.MUTED)));
		if (stage == 3) {
			out.add(Component.literal(skills(s)));
			out.add(Component.literal(Ui.tr(s.translationKey() + ".desc")));
			out.add(Component.literal(Ui.tr("gui.extremeprogression.codex.found", DeathView.when(e.time()), DeathView.dimension(e.dimension()), e.x(), e.y(), e.z()))
					.withStyle(st -> st.withColor(Ui.MUTED)));
			if (e.firstMine()) {
				out.add(Component.literal("★ " + Ui.tr("gui.extremeprogression.codex.first_discoverer")).withStyle(st -> st.withColor(Ui.GOLD)));
			} else if (e.foundOnServer()) {
				out.add(Component.literal(Ui.tr("gui.extremeprogression.codex.first_by", e.firstName())).withStyle(st -> st.withColor(Ui.MUTED)));
			}
			boolean met = SkillView.entry(s.first).level() >= Config.SYNERGY_LEVEL && SkillView.entry(s.second).level() >= Config.SYNERGY_LEVEL;
			if (!met) {
				out.add(Component.literal(Ui.tr("gui.extremeprogression.codex.dormant", Config.SYNERGY_LEVEL)).withStyle(st -> st.withColor(Ui.DANGER)));
			}
		} else {
			out.add(Component.literal(Ui.tr(s.translationKey() + ".hint1")).withStyle(st -> st.withColor(Ui.TEXT)));
			if (stage == 2) {
				out.add(Component.literal(skills(s)));
				out.add(Component.literal(Ui.tr(s.translationKey() + ".hint2")).withStyle(st -> st.withColor(Ui.TEXT)));
				out.add(Component.literal(Ui.tr("gui.extremeprogression.codex.found_by_someone")).withStyle(st -> st.withColor(Ui.MUTED)));
			}
			out.add(Component.literal(Ui.tr("gui.extremeprogression.codex.needs", Config.SYNERGY_LEVEL)).withStyle(st -> st.withColor(Ui.DIM)));
		}
		return out;
	}

	/** One line of the Chronicle; returns the entry when somebody found it. */
	private SynergyPayload.Entry chronicleRow(GuiGraphicsExtractor g, Synergy s, SynergyPayload.Entry e, int stage, int ix, int iw, int y) {
		boolean found = e != null && e.foundOnServer();
		String shown = stage == 3 ? Ui.tr(s.translationKey()) : found ? runes(Ui.tr(s.translationKey()), true) : runes(Ui.tr(s.translationKey()), false);
		Ui.text(g, this.font, Ui.ellipsize(this.font, shown, 112), ix, y, found ? (stage == 3 ? Ui.GOLD : Ui.MUTED) : Ui.DIM);
		if (!found) {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.chronicle.nobody"), ix + 118, y, Ui.DIM);
			return null;
		}
		String who = (e.firstMine() ? "★ " : "") + e.firstName();
		Ui.text(g, this.font, Ui.ellipsize(this.font, who, 118), ix + 118, y, e.firstMine() ? Ui.GOLD : Ui.TEXT);
		Ui.text(g, this.font, DeathView.when(e.firstTime()), ix + 242, y, Ui.MUTED);
		return e;
	}

	private List<Component> chronicleTip(Synergy s, SynergyPayload.Entry e, int stage) {
		List<Component> out = new ArrayList<>();
		out.add(Component.literal(stage == 3 ? Ui.tr(s.translationKey()) : runes(Ui.tr(s.translationKey()), true))
				.withStyle(st -> st.withColor(stage == 3 ? Ui.GOLD : Ui.MUTED)));
		out.add(Component.literal(Ui.tr("gui.extremeprogression.chronicle.first", e.firstName(), DeathView.when(e.firstTime()))));
		if (e.firstMine()) {
			out.add(Component.literal("★ " + Ui.tr("gui.extremeprogression.codex.first_discoverer")).withStyle(st -> st.withColor(Ui.GOLD)));
		}
		return out;
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
