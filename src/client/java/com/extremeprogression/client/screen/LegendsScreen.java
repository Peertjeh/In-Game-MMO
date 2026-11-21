package com.extremeprogression.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.client.state.ClientLegends;
import com.extremeprogression.client.state.ClientRates;
import com.extremeprogression.client.ui.Ui;
import com.extremeprogression.legends.Contracts;
import com.extremeprogression.legends.Legends;
import com.extremeprogression.legends.LegendsData;
import com.extremeprogression.legends.Titles;
import com.extremeprogression.legends.Tome;
import com.extremeprogression.network.LegendsActionPayload;
import com.extremeprogression.skill.Skill;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Legends screen (2.0): three tabs. Boosts: the XP boost now, the tomes running and waiting, Well Rested, the
 * streak, the Skill of the Day and where tomes come from. Contracts: today's three and this week's one, with their
 * progress, rewards and the daily swap. Titles: every title, earned or not and how; a click shows an earned one.
 */
public class LegendsScreen extends Screen {
	static final int W = 380, H = 232;
	/** The Boosts tab's left column (the boost now) and where its right one (where tomes come from) starts. */
	private static final int LEFT_W = 158, RIGHT_X = 176;
	private static final int TITLE_COLS = 3, TITLE_ROW = 12;

	public enum Tab {
		BOOSTS, CONTRACTS, TITLES, OATHS
	}

	private static Tab tab = Tab.BOOSTS;
	private static int opened = 0;

	private final Screen parent;
	private int x0, y0;
	private int seen = -1;

	public LegendsScreen(Screen parent) {
		super(Component.translatable("gui.extremeprogression.legends.title"));
		this.parent = parent;
		opened++;
	}

	/** How many times the Legends screen was opened (for tests). */
	public static int opened() {
		return opened;
	}

	public static void show(Tab t) {
		tab = t;
	}

	private static Legends.View view() {
		Legends.View v = ClientLegends.current();
		return v != null ? v : new Legends.View();
	}

	private int py() {
		return y0 + 32;
	}

	private int ph() {
		return H - 32 - 26;
	}

	@Override
	protected void init() {
		x0 = (this.width - W) / 2;
		y0 = Math.max(2, (this.height - H) / 2);
		seen = ClientLegends.updates();
		// The tabs
		int tw = 84, tx = this.width / 2 - (Tab.values().length * (tw + 4) - 4) / 2;
		for (Tab t : Tab.values()) {
			Button b = Button.builder(Component.translatable("gui.extremeprogression.legends.tab." + t.name().toLowerCase(java.util.Locale.ROOT)), x -> {
				tab = t;
				rebuild();
			}).pos(tx, y0 + 13).size(tw, 16).build();
			b.active = tab != t;
			this.addRenderableWidget(b);
			tx += tw + 4;
		}
		// The swaps, on the contracts that may still be swapped
		Legends.View v = view();
		if (tab == Tab.CONTRACTS && v.contractsOn && v.rerollFree) {
			for (int i = 0; i < v.contracts.daily.size(); i++) {
				if (!v.contracts.daily.get(i).done) {
					int index = i;
					Button b = Button.builder(Component.translatable("gui.extremeprogression.legends.contracts.swap"), x -> act("reroll", Integer.toString(index)))
							.pos(x0 + W - 52, cardY(i) + 4).size(42, 14).build();
					b.setTooltip(Tooltip.create(Component.translatable("gui.extremeprogression.legends.contracts.swap_tip")));
					this.addRenderableWidget(b);
				}
			}
		}
		// The Oaths that may be sworn now (2.2)
		if (tab == Tab.OATHS && v.oathsOn && Minecraft.getInstance().player != null) {
			java.util.Map<com.extremeprogression.legends.Relic, Integer> have = com.extremeprogression.legends.Oaths.carried(Minecraft.getInstance().player.getInventory());
			com.extremeprogression.legends.Oath[] all = com.extremeprogression.legends.Oath.values();
			for (int i = 0; i < all.length; i++) {
				com.extremeprogression.legends.Oath o = all[i];
				if (!v.oaths.contains(o.id) && ready(o, v, have) && v.oathReadyAt <= System.currentTimeMillis()) {
					Button b = Button.builder(Component.translatable("gui.extremeprogression.legends.oaths.swear"), x -> act("oath", o.id))
							.pos(oathX(i) + OATH_W - 44, oathY(i) + 2).size(40, 12).build();
					b.setTooltip(Tooltip.create(Component.translatable("gui.extremeprogression.legends.oaths.swear_tip")));
					this.addRenderableWidget(b);
				}
			}
		}
		this.addRenderableWidget(Button.builder(Component.translatable(parent != null ? "gui.back" : "gui.done"), b -> this.onClose())
				.pos(this.width / 2 - 50, y0 + H - 20).size(100, 20).build());
	}

	private int cardY(int i) {
		return py() + 16 + i * 33;
	}

	/** Sends a Legends action to the server (for tests too). */
	public void act(String action, String arg) {
		if (ClientPlayNetworking.canSend(LegendsActionPayload.TYPE)) {
			ClientPlayNetworking.send(new LegendsActionPayload(action, arg));
		}
	}

	private void rebuild() {
		this.clearWidgets();
		this.init();
	}

	@Override
	public void tick() {
		super.tick();
		if (seen != ClientLegends.updates()) {
			rebuild();
		}
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		Ui.centered(g, this.font, this.title.getString(), this.width / 2, y0 + 2, Ui.GOLD);
		Ui.panel(g, x0, py(), W, ph());
		List<Component> tip = switch (tab) {
			case BOOSTS -> boosts(g);
			case CONTRACTS -> contracts(g);
			case TITLES -> titles(g, mouseX, mouseY);
			case OATHS -> oaths(g, mouseX, mouseY);
		};
		if (tip != null) {
			g.setComponentTooltipForNextFrame(this.font, tip, mouseX, mouseY);
		}
	}

	// --- Boosts ----------------------------------------------------------------------------------

	private List<Component> boosts(GuiGraphicsExtractor g) {
		Legends.View v = view();
		int x = x0 + 8, y = py() + 8, w = LEFT_W;
		if (!v.boostsOn) {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.legends.boost.off"), x, y, Ui.MUTED);
			return null;
		}
		long total = Math.round(v.boost * 100);
		Ui.text(g, this.font, Ui.tr("gui.extremeprogression.legends.boost.total", total), x, y, total > 0 ? Ui.GOLD : Ui.MUTED);
		Ui.meter(g, x, y + 11, w, 4, v.boostMax > 0 ? v.boost / v.boostMax : 0, total > 0 ? ClientLegends.color(ClientLegends.running(v)) : Ui.DIM);
		y += 22;
		LegendsData.Boost run = ClientLegends.running(v);
		if (run == null && v.rested <= 0) {
			for (String line : Ui.wrap(this.font, Ui.tr("gui.extremeprogression.legends.boost.none"), w)) {
				Ui.text(g, this.font, line, x, y, Ui.MUTED);
				y += 10;
			}
		}
		if (run != null) {
			Ui.text(g, this.font, Ui.ellipsize(this.font, name(run), w), x, y, ClientLegends.color(run));
			y += 10;
			Ui.text(g, this.font, Ui.ellipsize(this.font, Ui.tr("gui.extremeprogression.legends.boost.running", run.percent, time(run.seconds)), w), x + 6, y,
					ClientLegends.color(run));
			y += 11;
			for (LegendsData.Boost b : v.boosts) {
				if (b != run) {
					Ui.text(g, this.font, Ui.ellipsize(this.font, Ui.tr("gui.extremeprogression.legends.boost.waiting", b.percent, time(b.seconds)), w), x + 6, y, Ui.MUTED);
					y += 10;
				}
			}
		}
		if (v.rested > 0) {
			Ui.text(g, this.font, Ui.ellipsize(this.font, Ui.tr("gui.extremeprogression.legends.boost.rested", Math.round(v.restedBonus * 100), time(v.rested)), w), x, y,
					0x55FFFF);
			y += 11;
		}
		y += 6;
		Ui.divider(g, x, y, w);
		y += 6;
		Ui.text(g, this.font, Ui.tr("gui.extremeprogression.legends.boost.streak", v.streak, v.bestStreak), x, y, Ui.TEXT);
		y += 11;
		Skill sotd = Skill.byId(v.skillOfTheDay);
		if (sotd != null) {
			Ui.text(g, this.font, Ui.ellipsize(this.font, Ui.tr("gui.extremeprogression.legends.boost.sotd", Ui.tr(sotd.translationKey()),
					Math.round(v.skillOfTheDayBonus * 100)), w), x, y, Ui.WARN);
			y += 11;
		}
		y += 4;
		for (String line : Ui.wrap(this.font, Ui.tr("gui.extremeprogression.legends.boost.max", Math.round(v.boostMax * 100)), w)) {
			Ui.text(g, this.font, line, x, y, Ui.DIM);
			y += 10;
		}
		// Right: where tomes come from
		int rx = x0 + RIGHT_X, rw = W - RIGHT_X - 8, ry = py() + 8;
		g.fill(rx - 9, py() + 6, rx - 8, py() + ph() - 6, Ui.argb(0.25, 0xFFFFFF));
		Ui.text(g, this.font, Ui.tr("gui.extremeprogression.legends.boost.how"), rx, ry, Ui.GOLD);
		ry += 13;
		for (int i = 1; i <= 6; i++) {
			List<String> lines = Ui.wrap(this.font, "• " + Ui.tr("gui.extremeprogression.legends.boost.how" + i), rw);
			for (String line : lines) {
				Ui.text(g, this.font, line, rx, ry, Ui.MUTED);
				ry += 9;
			}
			ry += 2;
		}
		return null;
	}

	private static String name(LegendsData.Boost b) {
		Tome t = Tome.byId(b.source);
		return t != null ? Ui.tr(t.translationKey()) : "+" + b.percent + "%";
	}

	private static String time(int seconds) {
		return ClientRates.shortTime(seconds / 3600.0);
	}

	// --- Contracts -------------------------------------------------------------------------------

	private List<Component> contracts(GuiGraphicsExtractor g) {
		Legends.View v = view();
		int x = x0 + 8, w = W - 16;
		if (!v.contractsOn) {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.legends.contracts.off"), x, py() + 8, Ui.MUTED);
			return null;
		}
		Ui.text(g, this.font, Ui.tr("gui.extremeprogression.legends.contracts.daily"), x, py() + 5, Ui.GOLD);
		if (v.contracts.daily.isEmpty()) {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.legends.contracts.waiting"), x, py() + 20, Ui.MUTED);
		}
		for (int i = 0; i < v.contracts.daily.size(); i++) {
			card(g, v.contracts.daily.get(i), x, cardY(i), w, false);
		}
		int wy = cardY(3) + 2;
		Ui.text(g, this.font, Ui.tr("gui.extremeprogression.legends.contracts.weekly"), x, wy, Ui.GOLD);
		if (v.contracts.weekly != null) {
			card(g, v.contracts.weekly, x, wy + 11, w, true);
		}
		int fy = py() + ph() - 11;
		Ui.text(g, this.font, Ui.tr("gui.extremeprogression.legends.contracts.all"), x, fy, Ui.MUTED);
		Ui.right(g, this.font, Ui.tr("gui.extremeprogression.legends.contracts.count", v.stats.getOrDefault("contracts", 0L)), x + w, fy, Ui.MUTED);
		return null;
	}

	private void card(GuiGraphicsExtractor g, LegendsData.Contract c, int x, int y, int w, boolean weekly) {
		Skill s = Skill.byId(c.rewardSkill);
		int edge = c.done ? Ui.OK : s != null ? s.category.accent : Ui.GOLD;
		Ui.tile(g, x, y, w, 30, Ui.mix(edge, Ui.SHADOW, 0.35));
		String text = Contracts.describe(c).getString();
		Ui.text(g, this.font, Ui.ellipsize(this.font, (c.done ? "✔ " : "") + text, w - 60), x + 5, y + 4, c.done ? Ui.OK : Ui.TEXT);
		int mw = w - 170;
		Ui.meter(g, x + 5, y + 15, mw, 4, c.share(), c.done ? Ui.OK : edge);
		Ui.text(g, this.font, Ui.number(c.progress) + " / " + Ui.number(c.goal), x + 10 + mw, y + 13, Ui.MUTED);
		Tome t = Tome.byId(c.rewardTome);
		String reward = t != null ? Ui.tr("gui.extremeprogression.legends.contracts.reward_tome", Ui.tr(t.translationKey()))
				: s != null ? Ui.tr("gui.extremeprogression.legends.contracts.reward_xp", Ui.number(c.rewardXp), Ui.tr(s.translationKey())) : "";
		Ui.text(g, this.font, Ui.ellipsize(this.font, reward, w - 10), x + 5, y + 21, t != null ? t.color : Ui.DIM);
		if (c.done) {
			Ui.right(g, this.font, Ui.tr("gui.extremeprogression.legends.contracts.done"), x + w - 6, y + 4, Ui.OK);
		}
	}

	// --- Titles ----------------------------------------------------------------------------------

	private int titleW() {
		return (W - 16) / TITLE_COLS;
	}

	/** The titles in the grid: "No title" first, then every title. */
	private static List<String> entries() {
		List<String> out = new ArrayList<>();
		out.add("");
		for (Titles.Def d : Titles.all()) {
			out.add(d.id());
		}
		return out;
	}

	private int titleTop() {
		return py() + 26;
	}

	private List<Component> titles(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		Legends.View v = view();
		int x = x0 + 8;
		if (!v.titlesOn) {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.legends.titles.off"), x, py() + 8, Ui.MUTED);
			return null;
		}
		Ui.text(g, this.font, Ui.tr("gui.extremeprogression.legends.titles.count", v.titles.size(), Titles.all().size()), x, py() + 5, Ui.GOLD);
		Ui.text(g, this.font, Ui.ellipsize(this.font, Ui.tr("gui.extremeprogression.legends.titles.tip"), W - 16), x, py() + 15, Ui.DIM);
		List<Component> tip = null;
		List<String> all = entries();
		int cw = titleW();
		for (int i = 0; i < all.size(); i++) {
			String id = all.get(i);
			int cx = x + (i % TITLE_COLS) * cw, cy = titleTop() + (i / TITLE_COLS) * TITLE_ROW;
			boolean shown = id.equals(v.title);
			boolean earned = id.isEmpty() || v.titles.contains(id);
			Titles.Def d = Titles.byId(id);
			if (shown) {
				g.fill(cx - 2, cy - 2, cx + cw - 4, cy + TITLE_ROW - 2, Ui.argb(0.35, d != null ? d.color() : Ui.MUTED));
			}
			String label = id.isEmpty() ? Ui.tr("gui.extremeprogression.legends.titles.none") : Ui.tr(Titles.translationKey(id));
			int color = !earned ? Ui.DIM : d != null ? d.color() : Ui.MUTED;
			Ui.text(g, this.font, Ui.ellipsize(this.font, (shown ? "✔ " : "") + label, cw - 8), cx, cy, color);
			if (d != null && mouseX >= cx - 2 && mouseX < cx + cw - 4 && mouseY >= cy - 2 && mouseY < cy + TITLE_ROW - 2) {
				tip = List.of(Component.literal(label).withColor(d.color()),
						Component.literal(Ui.tr(Titles.translationKey(id) + ".how")).withColor(earned ? Ui.OK : Ui.MUTED));
			}
		}
		return tip;
	}

	// --- Oaths (2.2) ------------------------------------------------------------------------------

	private static final int OATH_W = (W - 20) / 2, OATH_H = 34;

	private int oathX(int i) {
		return x0 + 8 + (i % 2) * (OATH_W + 4);
	}

	private int oathY(int i) {
		return py() + 27 + (i / 2) * (OATH_H + 2);
	}

	private static boolean ready(com.extremeprogression.legends.Oath o, Legends.View v, java.util.Map<com.extremeprogression.legends.Relic, Integer> have) {
		if (!v.oathFeats.contains(o.id)) {
			return false;
		}
		for (com.extremeprogression.legends.Relic r : o.relics.keySet()) {
			if (have.getOrDefault(r, 0) < o.needed(r)) {
				return false;
			}
		}
		return true;
	}

	private List<Component> oaths(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		Legends.View v = view();
		int x = x0 + 8;
		if (!v.oathsOn) {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.legends.oaths.off"), x, py() + 8, Ui.MUTED);
			return null;
		}
		com.extremeprogression.legends.Oath[] all = com.extremeprogression.legends.Oath.values();
		Ui.text(g, this.font, Ui.tr("gui.extremeprogression.legends.oaths.count", v.oaths.size(), all.length), x, py() + 5, Ui.GOLD);
		long wait = v.oathReadyAt - System.currentTimeMillis();
		String line = wait > 0 ? Ui.tr("gui.extremeprogression.legends.oaths.wait", ClientRates.shortTime(wait / 3_600_000.0))
				: Ui.tr(v.oathBeacon ? "gui.extremeprogression.legends.oaths.tip_beacon" : "gui.extremeprogression.legends.oaths.tip");
		Ui.text(g, this.font, Ui.ellipsize(this.font, line, W - 16), x, py() + 15, wait > 0 ? Ui.WARN : Ui.DIM);
		java.util.Map<com.extremeprogression.legends.Relic, Integer> have = Minecraft.getInstance().player == null ? java.util.Map.of()
				: com.extremeprogression.legends.Oaths.carried(Minecraft.getInstance().player.getInventory());
		List<Component> tip = null;
		for (int i = 0; i < all.length; i++) {
			com.extremeprogression.legends.Oath o = all[i];
			int cx = oathX(i), cy = oathY(i);
			boolean sworn = v.oaths.contains(o.id), feat = v.oathFeats.contains(o.id), ready = !sworn && ready(o, v, have);
			Ui.tile(g, cx, cy, OATH_W, OATH_H, Ui.mix(sworn ? Ui.GOLD : ready ? Ui.OK : 0x777777, Ui.SHADOW, 0.35));
			String name = Ui.tr(o.translationKey());
			Ui.text(g, this.font, Ui.ellipsize(this.font, (sworn ? "✔ " : "") + name, OATH_W - (ready ? 52 : 10)), cx + 5, cy + 3, sworn ? Ui.GOLD : Ui.TEXT);
			// What it needs: the relics (carried / asked) and the feat
			StringBuilder needs = new StringBuilder();
			boolean relicsMet = true;
			for (com.extremeprogression.legends.Relic r : o.relics.keySet()) {
				int n = Math.min(have.getOrDefault(r, 0), o.needed(r));
				relicsMet &= n >= o.needed(r);
				if (o.relics.size() == 1) {
					needs.append(n).append('/').append(o.needed(r)).append(' ').append(Ui.tr(r.translationKey()));
				}
			}
			if (o.relics.size() > 1) {
				needs.append(Ui.tr("gui.extremeprogression.legends.oaths.every_relic", o.relics.keySet().stream()
						.filter(r -> have.getOrDefault(r, 0) >= o.needed(r)).count(), o.relics.size(), o.needed(o.relics.keySet().iterator().next())));
			}
			needs.append(" · ").append(feat ? "✔ " : "✘ ").append(Ui.tr(o.translationKey() + ".need"));
			Ui.text(g, this.font, Ui.ellipsize(this.font, needs.toString(), OATH_W - 10), cx + 5, cy + 13,
					sworn ? Ui.DIM : relicsMet && feat ? Ui.OK : Ui.MUTED);
			Ui.text(g, this.font, Ui.ellipsize(this.font, Ui.tr(o.translationKey() + ".reward"), OATH_W - 10), cx + 5, cy + 23, sworn ? Ui.GOLD : 0x88CC88);
			if (mouseX >= cx && mouseX < cx + OATH_W && mouseY >= cy && mouseY < cy + OATH_H) {
				List<Component> lines = new ArrayList<>();
				lines.add(Component.literal(name).withColor(Ui.GOLD));
				for (com.extremeprogression.legends.Relic r : o.relics.keySet()) {
					int n = have.getOrDefault(r, 0);
					lines.add(Component.literal("• " + Ui.tr(r.translationKey()) + "  " + Math.min(n, o.needed(r)) + "/" + o.needed(r))
							.withColor(n >= o.needed(r) ? Ui.OK : r.color));
				}
				lines.add(Component.literal("• " + Ui.tr(o.translationKey() + ".need") + (feat ? "  ✔" : "")).withColor(feat ? Ui.OK : Ui.MUTED));
				lines.add(Component.literal(Ui.tr("gui.extremeprogression.legends.oaths.gives", Ui.tr(o.translationKey() + ".reward"))).withColor(0x88CC88));
				tip = lines;
			}
		}
		return tip;
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		if (tab == Tab.TITLES && view().titlesOn) {
			double mx = event.x(), my = event.y();
			int x = x0 + 8, cw = titleW();
			List<String> all = entries();
			for (int i = 0; i < all.size(); i++) {
				int cx = x + (i % TITLE_COLS) * cw, cy = titleTop() + (i / TITLE_COLS) * TITLE_ROW;
				if (mx >= cx - 2 && mx < cx + cw - 4 && my >= cy - 2 && my < cy + TITLE_ROW - 2) {
					String id = all.get(i);
					if (id.isEmpty() || view().titles.contains(id)) {
						act("title", id);
						return true;
					}
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}
}
