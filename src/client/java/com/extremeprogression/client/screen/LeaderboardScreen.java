package com.extremeprogression.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.extremeprogression.client.state.ClientAdmin;
import com.extremeprogression.client.ui.Ui;
import com.extremeprogression.network.LeaderboardPayload;
import com.extremeprogression.skill.Skill;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The leaderboard (design §9, screen 6, ✅ round 25): every player's total, or one skill's levels, with Mastery ranks
 * after the maximum level. Players may see each other's levels. Asked from the server when it opens.
 */
public class LeaderboardScreen extends Screen {
	static final int W = 404, H = 222;
	private static final int ROW = 11;

	private static int opened = 0;

	private final Screen parent;
	private int x0, y0;
	/** -1 for the total, else the skill's index. */
	private int view = -1;
	private int scroll = 0;
	private Button which;

	public LeaderboardScreen(Screen parent) {
		super(Component.translatable("gui.extremeprogression.leaderboard.title"));
		this.parent = parent;
		opened++;
	}

	public static int opened() {
		return opened;
	}

	@Override
	protected void init() {
		x0 = (this.width - W) / 2;
		y0 = Math.max(2, (this.height - H) / 2);
		this.addRenderableWidget(Button.builder(Component.literal("<"), b -> cycle(-1)).pos(x0 + 4, y0 + 14).size(16, 14).build());
		which = this.addRenderableWidget(Button.builder(label(), b -> cycle(1)).pos(x0 + 22, y0 + 14).size(110, 14).build());
		this.addRenderableWidget(Button.builder(Component.literal(">"), b -> cycle(1)).pos(x0 + 134, y0 + 14).size(16, 14).build());
		this.addRenderableWidget(Button.builder(Component.translatable(parent != null ? "gui.back" : "gui.done"), b -> this.onClose())
				.pos(this.width / 2 - 50, y0 + H - 20).size(100, 20).build());
		if (ClientPlayNetworking.canSend(LeaderboardPayload.TYPE)) {
			ClientPlayNetworking.send(new LeaderboardPayload(List.of()));
		}
	}

	private Component label() {
		return view < 0 ? Component.translatable("gui.extremeprogression.leaderboard.total") : Component.translatable(Skill.values()[view].translationKey());
	}

	/** Shows the total (-1) or a skill (its index), for tests. */
	public void show(int view) {
		this.view = view;
		this.scroll = 0;
		which.setMessage(label());
	}

	private void cycle(int by) {
		int n = Skill.values().length + 1;
		show(Math.floorMod(view + 1 + by, n) - 1);
	}

	private List<LeaderboardPayload.Row> rows() {
		List<LeaderboardPayload.Row> all = ClientAdmin.leaderboard();
		if (all == null) {
			return null;
		}
		List<LeaderboardPayload.Row> out = new ArrayList<>(all);
		if (view >= 0) {
			int i = view;
			out.sort(Comparator.comparingInt((LeaderboardPayload.Row r) -> r.levels()[i]).reversed()
					.thenComparing(Comparator.comparingInt((LeaderboardPayload.Row r) -> r.ranks()[i]).reversed())
					.thenComparing(LeaderboardPayload.Row::name, String.CASE_INSENSITIVE_ORDER));
		}
		return out;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		Ui.centered(g, this.font, this.title.getString(), this.width / 2, y0 + 2, Ui.GOLD);
		int py = y0 + 30, ph = H - 56;
		Ui.panel(g, x0, py, W, ph);
		int ix = x0 + 6, iw = W - 12;
		List<LeaderboardPayload.Row> rows = rows();
		if (rows == null) {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.leaderboard.waiting"), ix, py + 6, Ui.MUTED);
			return;
		}
		String me = Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getName().getString();
		int visible = (ph - 10) / ROW;
		scroll = Math.max(0, Math.min(scroll, rows.size() - visible));
		Ui.right(g, this.font, Ui.tr("gui.extremeprogression.leaderboard.players", rows.size()), x0 + W - 4, y0 + 17, Ui.MUTED);
		for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
			LeaderboardPayload.Row r = rows.get(scroll + i);
			int y = py + 5 + i * ROW;
			int place = scroll + i + 1;
			boolean mine = r.name().equals(me);
			int color = place == 1 ? Ui.GOLD : mine ? Ui.WARN : Ui.TEXT;
			Ui.right(g, this.font, place + ".", ix + 22, y, Ui.MUTED);
			Ui.text(g, this.font, Ui.ellipsize(this.font, r.name(), 112), ix + 28, y, color);
			String value;
			if (view < 0) {
				value = r.total() + (r.mastery() > 0 ? "  M" + r.mastery() : "");
				// (the ten levels beside it, small)
				StringBuilder levels = new StringBuilder();
				for (int s = 0; s < r.levels().length; s++) {
					levels.append(s == 0 ? "" : " ").append(r.levels()[s]);
				}
				// (eleven skills' levels: from the name's end to the total's start)
				Ui.text(g, this.font, Ui.ellipsize(this.font, levels.toString(), iw - 146 - 60), ix + 146, y, Ui.DIM);
			} else {
				value = Ui.tr("gui.extremeprogression.leaderboard.level", r.levels()[view]) + (r.ranks()[view] > 0 ? "  M" + r.ranks()[view] : "");
			}
			Ui.right(g, this.font, value, ix + iw, y, color);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) {
			scroll = Math.max(0, scroll - (int) Math.signum(scrollY) * 3);
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
