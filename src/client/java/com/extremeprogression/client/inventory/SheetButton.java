package com.extremeprogression.client.inventory;

import com.extremeprogression.client.mixin.ContainerScreenAccess;
import com.extremeprogression.client.screen.CharacterSheetScreen;

import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Character Sheet, one click away from the inventory: a small button inside the inventory's own panel, in the empty
 * space between the player's figure and the crafting grid, above the off-hand slot. It stays clear of the recipe book's
 * button and of the buttons other mods put beside it (Extreme Dimensions' Journal and Curses), and moves with the panel
 * when the recipe book opens or closes.
 */
public final class SheetButton extends AbstractButton {
	private static final ItemStack ICON = new ItemStack(Items.EXPERIENCE_BOTTLE);
	/** Its place in the 176 × 166 inventory panel: x 77 to 95, y 41 to 59 (the off-hand slot starts at y 62). */
	private static final int X = 77, Y = 41, SIZE = 18;

	private final InventoryScreen screen;

	private SheetButton(InventoryScreen screen) {
		super(0, 0, SIZE, SIZE, Component.translatable("gui.extremeprogression.sheet.title"));
		this.screen = screen;
		this.setTooltip(Tooltip.create(Component.translatable("gui.extremeprogression.sheet.button")));
		place();
	}

	public static void attach(InventoryScreen screen) {
		Screens.getWidgets(screen).add(new SheetButton(screen));
	}

	/** Inside the panel, wherever the panel is now. */
	private void place() {
		ContainerScreenAccess panel = (ContainerScreenAccess) screen;
		this.setX(panel.extremeprogression$leftPos() + X);
		this.setY(panel.extremeprogression$topPos() + Y);
	}

	@Override
	public void onPress(InputWithModifiers input) {
		Minecraft mc = Minecraft.getInstance();
		mc.gui.setScreen(new CharacterSheetScreen(mc.player != null ? new InventoryScreen(mc.player) : null));
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		place();
		this.extractDefaultSprite(g);
		g.fakeItem(ICON, this.getX() + 1, this.getY() + 1);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput out) {
		out.add(NarratedElementType.TITLE, this.getMessage());
	}
}
