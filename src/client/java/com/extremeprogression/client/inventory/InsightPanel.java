package com.extremeprogression.client.inventory;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.client.ui.Ui;
import com.extremeprogression.network.InsightPayload;

import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * Arcane Insight (Enchanter) beside the enchanting table: every enchantment of each offer, which vanilla shows only one
 * of, and a button for the one free reroll. Shown while the server sends the offers (during Insight).
 */
public final class InsightPanel extends AbstractButton {
	private static List<List<InsightPayload.Line>> offers = List.of();
	private static boolean reroll = false;

	private final EnchantmentScreen screen;

	private InsightPanel(EnchantmentScreen screen, int x, int y, int w) {
		super(x, y, w, 120, Component.translatable("gui.extremeprogression.insight.title"));
		this.screen = screen;
	}

	public static void apply(InsightPayload payload) {
		offers = payload.offers();
		reroll = payload.reroll();
	}

	public static void clear() {
		offers = List.of();
		reroll = false;
	}

	/** How many enchantments the panel shows now (for tests). */
	public static int shown() {
		return offers.stream().mapToInt(List::size).sum();
	}

	public static void attach(EnchantmentScreen screen) {
		int x = (screen.width + 176) / 2 + 4;
		int y = (screen.height - 166) / 2;
		int w = Math.min(140, screen.width - x - 4);
		if (w < 60) {
			return;
		}
		Screens.getWidgets(screen).add(new InsightPanel(screen, x, y, w));
		Screens.getWidgets(screen).add(new RerollButton(screen, x, y + 124, w));
	}

	/** An enchantment's name with its level in Roman numerals (level 1 left out, as vanilla does for most). */
	static String name(InsightPayload.Line line) {
		String id = line.enchantment();
		int colon = id.indexOf(':');
		String key = "enchantment." + id.substring(0, colon) + "." + id.substring(colon + 1);
		String name = Component.translatable(key).getString();
		return line.level() > 1 ? name + " " + Component.translatable("enchantment.level." + line.level()).getString() : name;
	}

	@Override
	public void onPress(InputWithModifiers input) {}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		this.visible = !offers.isEmpty();
		if (!this.visible) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		List<String> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		for (int i = 0; i < offers.size(); i++) {
			if (offers.get(i).isEmpty()) {
				continue;
			}
			lines.add(Ui.tr("gui.extremeprogression.insight.offer", i + 1, screen.getMenu().costs[i]));
			colors.add(Ui.GOLD);
			for (InsightPayload.Line l : offers.get(i)) {
				lines.add("  " + Ui.ellipsize(font, name(l), getWidth() - 16));
				colors.add(Ui.TEXT);
			}
		}
		int h = 16 + lines.size() * 9 + 4;
		this.setHeight(h);
		Ui.panel(g, getX(), getY(), getWidth(), h);
		Ui.text(g, font, Ui.ellipsize(font, getMessage().getString(), getWidth() - 8), getX() + 4, getY() + 4, 0xAA55FF);
		for (int i = 0; i < lines.size(); i++) {
			Ui.text(g, font, lines.get(i), getX() + 4, getY() + 16 + i * 9, colors.get(i));
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput out) {
		out.add(NarratedElementType.TITLE, this.getMessage());
	}

	/** The free reroll: the enchanting menu's button 3, which the server handles once per Insight. */
	private static final class RerollButton extends AbstractButton {
		private final EnchantmentScreen screen;
		private final int baseY;

		RerollButton(EnchantmentScreen screen, int x, int y, int w) {
			super(x, y, w, 16, Component.translatable("gui.extremeprogression.insight.reroll"));
			this.screen = screen;
			this.baseY = y;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.gameMode != null) {
				mc.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, 3);
				reroll = false;
			}
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
			this.visible = reroll && !offers.isEmpty();
			if (!this.visible) {
				return;
			}
			int lines = 0;
			for (List<InsightPayload.Line> offer : offers) {
				lines += offer.isEmpty() ? 0 : offer.size() + 1;
			}
			this.setY(baseY - 124 + 16 + lines * 9 + 8);
			this.extractDefaultSprite(g);
			Ui.centered(g, Minecraft.getInstance().font, getMessage().getString(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, Ui.TEXT);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			out.add(NarratedElementType.TITLE, this.getMessage());
		}
	}
}
