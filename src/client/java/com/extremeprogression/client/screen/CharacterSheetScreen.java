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
		column(rx + 6, y0 + 20, List.of(Category.COMBAT, Category.SURVIVAL));
		column(rx + 6 + SkillTile.W + 4, y0 + 20, List.of(Category.GATHERING, Category.CRAFTING));
		this.addRenderableWidget(Button.builder(Component.translatable("gui.extremeprogression.codex.button"),
				b -> Minecraft.getInstance().gui.setScreen(new CodexScreen(this))).pos(this.width / 2 - 153, y0 + H - 20).size(100, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.extremeprogression.death.title"),
				b -> Minecraft.getInstance().gui.setScreen(new DeathRegisterScreen(this))).pos(this.width / 2 - 50, y0 + H - 20).size(100, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
				.pos(this.width / 2 + 53, y0 + H - 20).size(100, 20).build());
	}

	private void column(int x, int y, List<Category> categories) {
		for (Category c : categories) {
			y += 10;
			for (Skill s : Skill.values()) {
				if (s.category == c) {
					this.addRenderableWidget(new SkillTile(x, y, s, this::open));
					y += SkillTile.H + 2;
				}
			}
			y += 4;
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
		}

		// The skills, grouped by category
		int rx = x0 + LEFT_W + 6, rw = W - LEFT_W - 6;
		Ui.panel(g, rx, ly, rw, lh);
		heading(g, rx + 6, y0 + 20, List.of(Category.COMBAT, Category.SURVIVAL));
		heading(g, rx + 6 + SkillTile.W + 4, y0 + 20, List.of(Category.GATHERING, Category.CRAFTING));
	}

	/** The category names above their tiles (the tiles themselves are widgets, see {@link #column}). */
	private void heading(GuiGraphicsExtractor g, int x, int y, List<Category> categories) {
		for (Category c : categories) {
			Ui.text(g, this.font, Ui.tr(c.translationKey()).toUpperCase(java.util.Locale.ROOT), x, y + 1, c.accent);
			y += 10;
			for (Skill s : Skill.values()) {
				if (s.category == c) {
					y += SkillTile.H + 2;
				}
			}
			y += 4;
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
