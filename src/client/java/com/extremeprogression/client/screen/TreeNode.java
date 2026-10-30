package com.extremeprogression.client.screen;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.extremeprogression.client.ui.Ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * A node of a skill tree: a label in a box whose edge shows its state (design §9: owned, reachable, out of reach,
 * conflict). Clicking it selects it for the detail panel.
 */
final class TreeNode extends AbstractButton {
	/** The node's state as its edge and text colour show it. */
	enum State {
		OWNED(Ui.OK, Ui.TEXT), REACHABLE(Ui.WARN, Ui.TEXT), LOCKED(Ui.DIM, Ui.MUTED), CONFLICT(0xAA0000, 0xD09090), DORMANT(0x5555FF, Ui.MUTED);

		final int edge, text;

		State(int edge, int text) {
			this.edge = edge;
			this.text = text;
		}
	}

	private final Supplier<State> state;
	private final BooleanSupplier selected;
	private final Runnable select;
	/** A short note on the right (a rank like 2/5, a ✓), or null. */
	private final Supplier<String> badge;

	TreeNode(int x, int y, int w, int h, Component label, Supplier<State> state, BooleanSupplier selected, Runnable select) {
		this(x, y, w, h, label, state, selected, select, () -> null);
	}

	TreeNode(int x, int y, int w, int h, Component label, Supplier<State> state, BooleanSupplier selected, Runnable select, Supplier<String> badge) {
		super(x, y, w, h, label);
		this.state = state;
		this.selected = selected;
		this.select = select;
		this.badge = badge;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		select.run();
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Font font = Minecraft.getInstance().font;
		State s = state.get();
		int edge = selected.getAsBoolean() ? Ui.TEXT : isHovered() ? Ui.mix(s.edge, Ui.TEXT, 0.5) : s.edge;
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		Ui.tile(g, x, y, w, h, edge);
		String note = badge.get();
		int noteW = note == null ? 0 : font.width(note) + 3;
		String label = Ui.ellipsize(font, getMessage().getString(), w - 8 - noteW);
		Ui.text(g, font, label, x + 4, y + (h - 8) / 2, s.text);
		if (note != null) {
			Ui.right(g, font, note, x + w - 3, y + (h - 8) / 2, s == State.OWNED ? Ui.OK : s == State.REACHABLE ? Ui.WARN : Ui.MUTED);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput out) {
		out.add(NarratedElementType.TITLE, this.getMessage());
	}
}
