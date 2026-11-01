package com.extremeprogression.client.screen;

import java.util.List;

import com.extremeprogression.client.state.ClientProgress;
import com.extremeprogression.client.ui.Ui;
import com.extremeprogression.network.ProgressPayload;
import com.extremeprogression.skill.Category;
import com.extremeprogression.skill.Skill;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/**
 * The main screen (design §9): the player, their total against the cap, and all ten skills as tiles grouped by
 * category. Opens with the hotkey, the inventory button or {@code /skills}.
 */
public class CharacterSheetScreen extends Screen {
	static final int W = 404, H = 222;
	private static final int LEFT_W = 112;

	/** How many times a Character Sheet was opened (for tests). */
	private static int opened = 0;

	/** Where closing returns to (the inventory, when opened from its button), or null. */
	private final Screen parent;
	private int x0, y0;

	public CharacterSheetScreen(Screen parent) {
		super(Component.translatable("gui.extremeprogression.sheet.title"));
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
		// The tiles: Combat and Survival on the left, Gathering and Crafting on the right
		int rx = x0 + LEFT_W + 6;
		column(rx + 6, y0 + 20, LEFT);
		column(rx + 6 + SkillTile.W + 4, y0 + 20, RIGHT);
		// The other screens, along the foot: the settings only for those who may change them
		List<Button> buttons = new java.util.ArrayList<>();
		buttons.add(Button.builder(Component.translatable("gui.extremeprogression.leaderboard.button"),
				b -> Minecraft.getInstance().gui.setScreen(new LeaderboardScreen(this))).build());
		buttons.add(Button.builder(Component.translatable("gui.extremeprogression.party.button"),
				b -> Minecraft.getInstance().gui.setScreen(new PartyScreen(this))).build());
		buttons.add(Button.builder(Component.translatable("gui.extremeprogression.codex.button"),
				b -> Minecraft.getInstance().gui.setScreen(new CodexScreen(this))).build());
		buttons.add(Button.builder(Component.translatable("gui.extremeprogression.death.button"),
				b -> Minecraft.getInstance().gui.setScreen(new DeathRegisterScreen(this))).build());
		if (com.extremeprogression.client.state.ClientAdmin.editor()) {
			buttons.add(Button.builder(Component.translatable("gui.extremeprogression.settings.button"),
					b -> Minecraft.getInstance().gui.setScreen(new SettingsScreen(this))).build());
		}
		buttons.add(Button.builder(Component.translatable("gui.done"), b -> this.onClose()).build());
		int bw = (W - 3 * (buttons.size() - 1)) / buttons.size();
		int bx = x0;
		for (Button b : buttons) {
			b.setX(bx);
			b.setY(y0 + H - 20);
			b.setWidth(bw);
			this.addRenderableWidget(b);
			bx += bw + 3;
		}
	}

	/**
	 * The two columns, as rows of categories: the n-th row of each starts on the same line (Survival beside Crafting).
	 * A row may hold more than one category under one heading (Survival · Other), so its tiles stay on the grid.
	 */
	private static final List<List<Category>> LEFT = List.of(List.of(Category.COMBAT), List.of(Category.SURVIVAL, Category.OTHER)),
			RIGHT = List.of(List.of(Category.GATHERING), List.of(Category.CRAFTING));

	private static int skills(List<Category> row) {
		int n = 0;
		for (Skill s : Skill.values()) {
			n += row.contains(s.category) ? 1 : 0;
		}
		return n;
	}

	/** Where each row of categories starts: below the taller of the two above it, so both columns stay in line. */
	static int rowTop(int y, int row) {
		for (int i = 0; i < row; i++) {
			int tallest = Math.max(i < LEFT.size() ? skills(LEFT.get(i)) : 0, i < RIGHT.size() ? skills(RIGHT.get(i)) : 0);
			y += 10 + tallest * (SkillTile.H + 2) + 4;
		}
		return y;
	}

	private void column(int x, int top, List<List<Category>> rows) {
		for (int row = 0; row < rows.size(); row++) {
			int y = rowTop(top, row) + 10;
			for (Category c : rows.get(row)) {
				for (Skill s : Skill.values()) {
					if (s.category == c) {
						this.addRenderableWidget(new SkillTile(x, y, s, this::open));
						y += SkillTile.H + 2;
					}
				}
			}
		}
	}

	private void open(Skill skill) {
		Minecraft.getInstance().gui.setScreen(new SkillScreen(skill, this));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		Ui.centered(g, this.font, this.title.getString(), this.width / 2, y0 + 2, Ui.TEXT);
		ProgressPayload p = ClientProgress.current();

		// The player and their totals
		int lx = x0, ly = y0 + 14, lh = H - 40;
		Ui.panel(g, lx, ly, LEFT_W, lh);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			try {
				InventoryScreen.extractEntityInInventoryFollowsMouse(g, lx + 8, ly + 6, lx + LEFT_W - 8, ly + 78, 30, 0.0625f, mouseX, mouseY, mc.player);
			} catch (RuntimeException e) {
				// (a figure that cannot be drawn leaves its box empty rather than closing the screen)
			}
			Ui.centered(g, this.font, Ui.ellipsize(this.font, mc.player.getName().getString(), LEFT_W - 12), lx + LEFT_W / 2, ly + 82, Ui.TEXT);
		}
		Ui.divider(g, lx + 6, ly + 94, LEFT_W - 12);
		int ry = ly + 100;
		if (p == null) {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.sheet.waiting"), lx + 6, ry, Ui.MUTED);
		} else {
			row(g, lx, ry, Ui.tr("gui.extremeprogression.sheet.total"), p.total() + " / " + p.cap(), p.total() >= p.cap() ? Ui.WARN : Ui.TEXT);
			Ui.meter(g, lx + 6, ry + 11, LEFT_W - 12, 4, p.total() / (double) p.cap(), Ui.GOLD);
			row(g, lx, ry + 20, Ui.tr("gui.extremeprogression.sheet.budget"), String.format(java.util.Locale.ROOT, "×%.2f", p.budgetFactor()), Ui.TEXT);
			int ranks = 0;
			for (ProgressPayload.Entry e : p.skills()) {
				ranks += e.masteryRanks();
			}
			row(g, lx, ry + 32, Ui.tr("gui.extremeprogression.sheet.mastery"), String.valueOf(ranks), ranks > 0 ? Ui.GOLD : Ui.TEXT);
			// The death pressure, and death fatigue while it lasts (design §9: in the GUI, never on the HUD)
			int pressure = p.death().pressure();
			row(g, lx, ry + 44, Ui.tr("gui.extremeprogression.death.pressure"), String.valueOf(pressure),
					pressure >= com.extremeprogression.config.Config.DEATH_WHOLE_LOSS_PRESSURE ? Ui.DANGER : pressure >= 1 ? Ui.WARN : Ui.TEXT);
			long fatigue = ClientProgress.fatigueLeft();
			if (fatigue > 0) {
				row(g, lx, ry + 56, Ui.tr("gui.extremeprogression.death.fatigue"), com.extremeprogression.client.ui.DeathView.duration(fatigue), Ui.DANGER);
			}
			// A running XP event (the first one; the tooltip of the line lists all)
			var events = com.extremeprogression.client.state.ClientAdmin.events();
			if (!events.isEmpty()) {
				var e = events.get(0);
				String what = e.skill().isEmpty() ? Ui.tr("gui.extremeprogression.sheet.xp_event_all") : Ui.tr("skill.extremeprogression." + e.skill());
				long left = com.extremeprogression.client.state.ClientAdmin.left(e);
				String value = left > 0 ? (left + 59_999) / 60_000 + " min" : "";
				String label = Ui.tr("gui.extremeprogression.sheet.xp_event", com.extremeprogression.client.ui.DeathView.factor(e.multiplier()), what);
				row(g, lx, ry + 68, Ui.ellipsize(this.font, label, LEFT_W - 12 - this.font.width(value) - 4), value, Ui.GOLD);
			}
		}

		// The skills, grouped by category
		int rx = x0 + LEFT_W + 6, rw = W - LEFT_W - 6;
		Ui.panel(g, rx, ly, rw, lh);
		heading(g, rx + 6, y0 + 20, LEFT);
		heading(g, rx + 6 + SkillTile.W + 4, y0 + 20, RIGHT);
	}

	/** The category names above their tiles, each in its colour (the tiles themselves are widgets, see {@link #column}). */
	private void heading(GuiGraphicsExtractor g, int x, int top, List<List<Category>> rows) {
		for (int row = 0; row < rows.size(); row++) {
			int hx = x, y = rowTop(top, row) + 1;
			List<Category> categories = rows.get(row);
			for (int i = 0; i < categories.size(); i++) {
				if (i > 0) {
					Ui.text(g, this.font, " · ", hx, y, Ui.DIM);
					hx += this.font.width(" · ");
				}
				String name = Ui.tr(categories.get(i).translationKey()).toUpperCase(java.util.Locale.ROOT);
				Ui.text(g, this.font, name, hx, y, categories.get(i).accent);
				hx += this.font.width(name);
			}
		}
	}

	private void row(GuiGraphicsExtractor g, int x, int y, String label, String value, int color) {
		Ui.text(g, this.font, label, x + 6, y, Ui.MUTED);
		Ui.right(g, this.font, value, x + LEFT_W - 6, y, color);
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
