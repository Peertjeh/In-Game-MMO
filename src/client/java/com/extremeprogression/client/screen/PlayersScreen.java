package com.extremeprogression.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.extremeprogression.client.state.ClientAdmin;
import com.extremeprogression.client.ui.Ui;
import com.extremeprogression.network.AdminActionPayload;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.skill.Specialization;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Players screen for admins (1.6.0, as Extreme Dimensions' 3.27): on the left every player the world knows (online
 * first, then offline, with a search box); on the right the one picked: their overview and every skill, with a level
 * taken off or put on (Shift: ten; past the maximum a Mastery rank), a free respec, every ability ready, death pressure
 * and fatigue cleared, and, for who may, a reset of a skill or of everything (press twice). The server checks and logs
 * every change and sends the screen fresh data.
 */
public class PlayersScreen extends Screen {
	static final int W = 404;
	private static final int LW = 132, ROW = 11, SKILL_ROW = 16;

	private static int opened = 0;

	private final Screen parent;
	private int x0, y0, h;
	private int seen = -1;
	private int scroll = 0;
	private String selected = null;
	private EditBox search;
	private String query = "";
	/** The reset waiting for its second press ("all", a skill id, or null), and until when. */
	private String confirm = null;
	private long confirmUntil = 0;

	public PlayersScreen(Screen parent) {
		super(Component.translatable("gui.extremeprogression.players.title"));
		this.parent = parent;
		opened++;
	}

	/** How many times the Players screen was opened (for tests). */
	public static int opened() {
		return opened;
	}

	private int py() {
		return y0 + 14;
	}

	private int ph() {
		return h - 40;
	}

	private int visibleRows() {
		return Math.max(1, (ph() - 26) / ROW);
	}

	@Override
	protected void init() {
		h = Math.max(200, Math.min(this.height - 4, 252));
		x0 = (this.width - W) / 2;
		y0 = Math.max(2, (this.height - h) / 2);
		if (seen == -1 && ClientPlayNetworking.canSend(AdminActionPayload.TYPE)) {
			// (opened from the Character Sheet: the list comes from the server)
			ClientPlayNetworking.send(new AdminActionPayload(selected == null ? "" : selected, "select", "", 0));
		}
		seen = ClientAdmin.adminUpdates();
		JsonObject data = ClientAdmin.admin();
		if (selected == null && data != null && data.has("selected")) {
			selected = data.getAsJsonObject("selected").get("id").getAsString();
		}
		search = new EditBox(this.font, x0 + 4, py() + 4, LW - 8, 14, Component.translatable("gui.extremeprogression.players.search"));
		search.setHint(Component.translatable("gui.extremeprogression.players.search"));
		search.setValue(query);
		search.setResponder(v -> {
			query = v;
			scroll = 0;
		});
		this.addRenderableWidget(search);
		JsonObject s = selectedData();
		if (s != null) {
			int rx = x0 + LW + 4, rw = W - LW - 4;
			int cw = (rw - 12 - 4) / 2;
			JsonArray skills = s.getAsJsonArray("skills");
			for (int i = 0; i < skills.size(); i++) {
				JsonObject o = skills.get(i).getAsJsonObject();
				String id = o.get("id").getAsString();
				int cx = rx + 6 + (i % 2) * (cw + 4), cy = py() + 38 + (i / 2) * SKILL_ROW;
				this.addRenderableWidget(small("−", "gui.extremeprogression.players.minus_tip", cx + cw - 40, cy, shift -> act("level", id, shift ? -10 : -1)));
				this.addRenderableWidget(small("+", "gui.extremeprogression.players.plus_tip", cx + cw - 27, cy, shift -> act("level", id, shift ? 10 : 1)));
				this.addRenderableWidget(small("↺", "gui.extremeprogression.players.respec_tip", cx + cw - 14, cy, shift -> {
					if (shift && data.get("reset").getAsBoolean()) {
						reset(id);
					} else {
						act("respec", id, 0);
					}
				}));
			}
			int by = py() + ph() - 22;
			int bw = (rw - 12 - 8) / 3;
			this.addRenderableWidget(Button.builder(Component.translatable("gui.extremeprogression.players.cooldowns"), b -> act("cooldowns", "", 0))
					.pos(rx + 6, by).size(bw, 16).build());
			this.addRenderableWidget(Button.builder(Component.translatable("gui.extremeprogression.players.clear_deaths"), b -> act("clear_deaths", "", 0))
					.pos(rx + 6 + bw + 4, by).size(bw, 16).build());
			if (data.get("reset").getAsBoolean()) {
				Button reset = Button.builder(Component.translatable("all".equals(confirm) ? "gui.extremeprogression.players.reset_sure" : "gui.extremeprogression.players.reset_all"),
						b -> reset("all")).pos(rx + 6 + 2 * (bw + 4), by).size(bw, 16).build();
				reset.setTooltip(Tooltip.create(Component.translatable("gui.extremeprogression.players.reset_tip")));
				this.addRenderableWidget(reset);
			}
		}
		this.addRenderableWidget(Button.builder(Component.translatable(parent != null ? "gui.back" : "gui.done"), b -> this.onClose())
				.pos(this.width / 2 - 50, y0 + h - 22).size(100, 20).build());
	}

	/** A small button that knows whether Shift was held. */
	private ShiftButton small(String label, String tip, int x, int y, java.util.function.Consumer<Boolean> press) {
		ShiftButton b = new ShiftButton(x, y, 12, 12, Component.literal(label), press);
		b.setTooltip(Tooltip.create(Component.translatable(tip)));
		return b;
	}

	/** A reset asks for a second press within 5 seconds. */
	private void reset(String what) {
		if (what.equals(confirm) && System.currentTimeMillis() < confirmUntil) {
			confirm = null;
			act("all".equals(what) ? "reset_all" : "reset_skill", "all".equals(what) ? "" : what, 0);
		} else {
			confirm = what;
			confirmUntil = System.currentTimeMillis() + 5000;
			if (!"all".equals(what)) {
				Minecraft.getInstance().gui.hud.setOverlayMessage(Component.translatable("gui.extremeprogression.players.reset_skill_sure",
						Component.translatable("skill.extremeprogression." + what)), false);
			}
			rebuild();
		}
	}

	/** Sends a change (or, with "select", picks a player) to the server; it answers with fresh data. */
	public void act(String action, String skill, int amount) {
		if (selected != null && ClientPlayNetworking.canSend(AdminActionPayload.TYPE)) {
			ClientPlayNetworking.send(new AdminActionPayload(selected, action, skill, amount));
		}
	}

	/** Picks a player (for tests too). */
	public void select(String id) {
		selected = id;
		confirm = null;
		act("select", "", 0);
	}

	private void rebuild() {
		this.clearWidgets();
		this.init();
		if (this.getFocused() == null) {
			this.setFocused(search);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (seen != ClientAdmin.adminUpdates() || confirm != null && System.currentTimeMillis() >= confirmUntil) {
			if (confirm != null && System.currentTimeMillis() >= confirmUntil) {
				confirm = null;
			}
			boolean typing = this.getFocused() == search;
			rebuild();
			if (typing) {
				this.setFocused(search);
			}
		}
	}

	/** The players shown: those whose name holds the search. */
	private List<JsonObject> shown() {
		List<JsonObject> out = new ArrayList<>();
		JsonObject data = ClientAdmin.admin();
		if (data == null) {
			return out;
		}
		String q = query.trim().toLowerCase(Locale.ROOT);
		for (JsonElement e : data.getAsJsonArray("players")) {
			JsonObject o = e.getAsJsonObject();
			if (q.isEmpty() || o.get("name").getAsString().toLowerCase(Locale.ROOT).contains(q)) {
				out.add(o);
			}
		}
		return out;
	}

	private JsonObject selectedData() {
		JsonObject data = ClientAdmin.admin();
		if (data == null || !data.has("selected") || selected == null) {
			return null;
		}
		JsonObject s = data.getAsJsonObject("selected");
		return selected.equals(s.get("id").getAsString()) ? s : null;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX < x0 + LW && scrollY != 0) {
			scroll = Math.max(0, Math.min(Math.max(0, shown().size() - visibleRows()), scroll - (int) Math.signum(scrollY) * 3));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		int top = py() + 22;
		if (mx >= x0 + 2 && mx < x0 + LW - 2 && my >= top && my < top + visibleRows() * ROW) {
			int i = scroll + (int) ((my - top) / ROW);
			List<JsonObject> rows = shown();
			if (i >= 0 && i < rows.size()) {
				select(rows.get(i).get("id").getAsString());
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		Ui.centered(g, this.font, this.title.getString(), this.width / 2, y0 + 2, Ui.GOLD);
		int py = py(), ph = ph();
		Ui.panel(g, x0, py, LW, ph);
		Ui.panel(g, x0 + LW + 4, py, W - LW - 4, ph);
		JsonObject data = ClientAdmin.admin();
		if (data == null) {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.players.waiting"), x0 + 6, py + 24, Ui.MUTED);
			return;
		}
		// Left: every player, online first
		List<JsonObject> rows = shown();
		scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows())));
		int top = py + 22;
		for (int i = 0; i < visibleRows() && scroll + i < rows.size(); i++) {
			JsonObject o = rows.get(scroll + i);
			int y = top + i * ROW;
			boolean online = o.get("online").getAsBoolean();
			boolean picked = o.get("id").getAsString().equals(selected);
			if (picked) {
				g.fill(x0 + 3, y - 1, x0 + LW - 3, y + ROW - 2, Ui.argb(0.25, Ui.GOLD));
			} else if (mouseX >= x0 + 2 && mouseX < x0 + LW - 2 && mouseY >= y - 1 && mouseY < y + ROW - 2) {
				g.fill(x0 + 3, y - 1, x0 + LW - 3, y + ROW - 2, Ui.argb(0.12, Ui.TEXT));
			}
			Ui.text(g, this.font, "●", x0 + 6, y, online ? Ui.OK : Ui.DIM);
			String total = "✦" + o.get("total").getAsInt();
			Ui.right(g, this.font, total, x0 + LW - 6, y, o.get("mastery").getAsInt() > 0 ? Ui.GOLD : Ui.MUTED);
			Ui.text(g, this.font, Ui.ellipsize(this.font, o.get("name").getAsString(), LW - 24 - this.font.width(total)), x0 + 14, y, online ? Ui.TEXT : Ui.MUTED);
		}
		Ui.right(g, this.font, Ui.tr("gui.extremeprogression.players.count", rows.size()), x0 + W, y0 + 2, Ui.DIM);
		// Right: the player picked
		int rx = x0 + LW + 4, rw = W - LW - 4, ix = rx + 6;
		JsonObject s = selectedData();
		if (s == null) {
			g.textWithWordWrap(this.font, Component.translatable("gui.extremeprogression.players.pick"), ix, py + 8, rw - 12, Ui.argb(255, Ui.MUTED));
			return;
		}
		boolean online = s.get("online").getAsBoolean();
		Ui.text(g, this.font, Ui.ellipsize(this.font, s.get("name").getAsString(), rw - 80), ix, py + 5, Ui.GOLD);
		Ui.right(g, this.font, Ui.tr(online ? "gui.extremeprogression.inspect.online" : "gui.extremeprogression.inspect.offline"), rx + rw - 6, py + 5,
				online ? Ui.OK : Ui.MUTED);
		long fatigue = s.get("fatigue").getAsLong();
		String line = Ui.tr("gui.extremeprogression.players.summary", s.get("total").getAsInt(), data.get("cap").getAsInt(), s.get("mastery").getAsInt(),
				s.get("deaths").getAsInt(), s.get("pressure").getAsInt()) + (fatigue > 0 ? " · " + Ui.tr("gui.extremeprogression.players.fatigue", fatigue / 60, String.format(Locale.ROOT, "%02d", fatigue % 60)) : "");
		Ui.text(g, this.font, Ui.ellipsize(this.font, line, rw - 12), ix, py + 16, Ui.TEXT);
		String party = s.get("party").getAsString();
		String third = Ui.tr("gui.extremeprogression.players.more", s.get("synergies").getAsInt()) + (party.isEmpty() ? "" : " · " + Ui.tr("gui.extremeprogression.players.party", party));
		Ui.text(g, this.font, Ui.ellipsize(this.font, third, rw - 12), ix, py + 26, Ui.MUTED);
		// Every skill, two columns
		int cw = (rw - 12 - 4) / 2;
		int max = data.get("max").getAsInt();
		JsonArray skills = s.getAsJsonArray("skills");
		List<Component> tip = null;
		for (int i = 0; i < skills.size(); i++) {
			JsonObject o = skills.get(i).getAsJsonObject();
			Skill skill = Skill.byId(o.get("id").getAsString());
			int cx = ix + (i % 2) * (cw + 4), cy = py + 38 + (i / 2) * SKILL_ROW;
			int level = o.get("level").getAsInt(), ranks = o.get("ranks").getAsInt();
			String value = level >= max && ranks > 0 ? "M" + ranks : "Lv " + level;
			int accent = skill == null ? Ui.TEXT : skill.category.accent;
			boolean sure = skill != null && skill.id.equals(confirm);
			Ui.right(g, this.font, value, cx + cw - 44, cy + 2, level >= max && ranks > 0 ? Ui.GOLD : Ui.TEXT);
			String name = skill == null ? o.get("id").getAsString() : Ui.tr(skill.translationKey());
			Ui.text(g, this.font, Ui.ellipsize(this.font, name, cw - 48 - this.font.width(value)), cx, cy + 2, sure ? Ui.DANGER : accent);
			if (mouseX >= cx && mouseX < cx + cw - 42 && mouseY >= cy && mouseY < cy + SKILL_ROW - 2) {
				tip = skillTip(skill, o);
			}
		}
		if (tip != null) {
			g.setComponentTooltipForNextFrame(this.font, tip, mouseX, mouseY);
		}
	}

	private static List<Component> skillTip(Skill skill, JsonObject o) {
		List<Component> out = new ArrayList<>();
		if (skill != null) {
			out.add(Component.translatable(skill.translationKey()).withStyle(st -> st.withColor(skill.category.accent)));
		}
		List<String> specs = new ArrayList<>();
		for (JsonElement e : o.getAsJsonArray("specs")) {
			Specialization spec = Specialization.byId(e.getAsString());
			specs.add(spec == null ? e.getAsString() : Ui.tr(spec.translationKey()));
		}
		out.add(Component.literal(specs.isEmpty() ? Ui.tr("gui.extremeprogression.inspect.no_spec") : String.join(" · ", specs)).withStyle(st -> st.withColor(Ui.MUTED)));
		out.add(Component.literal(Ui.tr("gui.extremeprogression.players.points", o.get("free").getAsInt(), o.get("earned").getAsInt())).withStyle(st -> st.withColor(Ui.MUTED)));
		out.add(Component.literal(Ui.tr("gui.extremeprogression.players.skill_help")).withStyle(st -> st.withColor(Ui.DIM)));
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

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
