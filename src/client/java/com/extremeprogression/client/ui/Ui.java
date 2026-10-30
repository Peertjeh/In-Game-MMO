package com.extremeprogression.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;

/**
 * The mod's visual language: Minecraft's own, as in Extreme Dimensions, so both mods feel like one game (design §9).
 * Panels are the game's tooltip box (near-black violet with its fading violet frame), text is the game's white and
 * grey with its drop shadow, colours are the sixteen of Minecraft's chat, and meters are as flat as a boss bar.
 */
public final class Ui {
	private Ui() {}

	// --- Palette (Minecraft's chat colours) ------------------------------------------------------

	/** The tooltip box's ground, and the slightly raised ground of a tile on it. */
	public static final int INK = 0x100010;
	public static final int INK_RAISED = 0x1C0C1C;
	public static final int SHADOW = 0x000000;

	public static final int TEXT = 0xFFFFFF;
	public static final int MUTED = 0xAAAAAA;
	public static final int DIM = 0x555555;
	public static final int OK = 0x55FF55;
	public static final int WARN = 0xFFFF55;
	public static final int DANGER = 0xFF5555;
	public static final int GOLD = 0xFFAA00;

	/** The tooltip frame's violet, at its top and its foot (argb, with the game's own alpha). */
	private static final int FRAME_TOP = 0x505000FF, FRAME_FOOT = 0x5028007F;

	// --- Colour math -----------------------------------------------------------------------------

	public static int argb(int alpha, int rgb) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}

	public static int argb(double alpha01, int rgb) {
		return argb((int) Math.round(255 * alpha01), rgb);
	}

	public static int mix(int a, int b, double t) {
		t = clamp01(t);
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bl;
	}

	public static double clamp01(double v) {
		return v < 0 ? 0 : v > 1 ? 1 : v;
	}

	/** Cubic ease-out over 0..1. */
	public static double ease(double t) {
		double c = 1 - clamp01(t);
		return 1 - c * c * c;
	}

	// --- Text ------------------------------------------------------------------------------------

	public static String tr(String key, Object... args) {
		return I18n.get(key, args);
	}

	/** A whole number with thousands separators: 46,360. */
	public static String number(double v) {
		return String.format(Locale.ROOT, "%,d", Math.round(v));
	}

	public static String percent(double fraction) {
		return (int) Math.floor(100 * clamp01(fraction)) + "%";
	}

	/** Word wrap on pixel width. */
	public static List<String> wrap(Font font, String text, int width) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.length() == 0 ? word : line + " " + word;
			if (font.width(candidate) > width && line.length() > 0) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(candidate);
			}
		}
		if (line.length() > 0) {
			out.add(line.toString());
		}
		return out;
	}

	/** Cuts text to a pixel width, ending in an ellipsis when it had to cut. */
	public static String ellipsize(Font font, String text, int width) {
		if (font.width(text) <= width) {
			return text;
		}
		String cut = text;
		while (!cut.isEmpty() && font.width(cut + "...") > width) {
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut.stripTrailing() + "...";
	}

	/** Text with the vanilla drop shadow. */
	public static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int rgb) {
		g.text(font, s, x, y, argb(255, rgb));
	}

	public static void right(GuiGraphicsExtractor g, Font font, String s, int rightX, int y, int rgb) {
		text(g, font, s, rightX - font.width(s), y, rgb);
	}

	public static void centered(GuiGraphicsExtractor g, Font font, String s, int cx, int y, int rgb) {
		text(g, font, s, cx - font.width(s) / 2, y, rgb);
	}

	// --- Shapes ----------------------------------------------------------------------------------

	/**
	 * The standard panel: the game's tooltip box, its ground reaching a pixel past its frame except at the corners, and
	 * its violet frame fading from top to foot.
	 */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		int ground = argb(0.94, INK);
		g.fill(x + 1, y, x + w - 1, y + h, ground);
		g.fill(x, y + 1, x + 1, y + h - 1, ground);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, ground);
		frame(g, x + 1, y + 1, w - 2, h - 2);
	}

	private static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + 1, FRAME_TOP);
		g.fill(x, y + h - 1, x + w, y + h, FRAME_FOOT);
		int steps = Math.max(1, Math.min(8, (h - 2) / 3));
		for (int i = 0; i < steps; i++) {
			int y0 = y + 1 + (h - 2) * i / steps, y1 = y + 1 + (h - 2) * (i + 1) / steps;
			int c = blend(FRAME_TOP, FRAME_FOOT, (i + 0.5) / steps);
			g.fill(x, y0, x + 1, y1, c);
			g.fill(x + w - 1, y0, x + w, y1, c);
		}
	}

	private static int blend(int a, int b, double t) {
		int alpha = (int) Math.round(((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t);
		return argb(alpha, mix(a & 0xFFFFFF, b & 0xFFFFFF, t));
	}

	/** A tile on a panel: a raised ground with a one-pixel edge. */
	public static void tile(GuiGraphicsExtractor g, int x, int y, int w, int h, int edge) {
		g.fill(x, y, x + w, y + h, argb(255, edge));
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, argb(255, INK_RAISED));
	}

	/** A rule between parts of a panel, as the game draws one under a screen's header: light over dark. */
	public static void divider(GuiGraphicsExtractor g, int x, int y, int w) {
		g.fill(x, y, x + w, y + 1, argb(0.22, 0xFFFFFF));
		g.fill(x, y + 1, x + w, y + 2, argb(0.5, SHADOW));
	}

	/**
	 * A meter as flat as a boss bar: the bar's colour darkened for what is empty, its colour for what is full, a lighter
	 * pixel along its top.
	 */
	public static void meter(GuiGraphicsExtractor g, int x, int y, int w, int h, double frac, int color) {
		frac = clamp01(frac);
		g.fill(x, y, x + w, y + h, argb(255, mix(color, SHADOW, 0.7)));
		int fw = (int) Math.round(w * frac);
		if (fw > 0) {
			g.fill(x, y, x + fw, y + h, argb(255, color));
			g.fill(x, y, x + fw, y + 1, argb(0.7, mix(color, 0xFFFFFF, 0.45)));
		}
	}
}
