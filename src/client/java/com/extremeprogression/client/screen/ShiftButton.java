package com.extremeprogression.client.screen;

import java.util.function.Consumer;

import com.extremeprogression.client.ui.Ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** A small button that tells whether Shift was held when it was pressed (the Players screen's − and +). */
final class ShiftButton extends AbstractButton {
	private final Consumer<Boolean> press;

	ShiftButton(int x, int y, int w, int h, Component label, Consumer<Boolean> press) {
		super(x, y, w, h, label);
		this.press = press;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		press.accept(input.hasShiftDown());
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int x = getX(), y = getY();
		Ui.tile(g, x, y, getWidth(), getHeight(), isHovered() ? Ui.TEXT : Ui.mix(Ui.INK_RAISED, Ui.TEXT, 0.35));
		Ui.centered(g, Minecraft.getInstance().font, getMessage().getString(), x + getWidth() / 2 + 1, y + (getHeight() - 8) / 2, isHovered() ? Ui.WARN : Ui.TEXT);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput out) {
		out.add(NarratedElementType.TITLE, this.getMessage());
	}
}
