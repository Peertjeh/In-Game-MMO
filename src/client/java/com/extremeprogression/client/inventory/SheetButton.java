package com.extremeprogression.client.inventory;

import com.extremeprogression.client.screen.CharacterSheetScreen;

import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Character Sheet, one click away from the inventory: a button right of the recipe book, or right of Extreme
 * Dimensions' Journal and Curses buttons when that mod is installed, so they never overlap (design §9). It follows the
 * recipe book button when the book opens or closes.
 */
public final class SheetButton extends AbstractButton {
	private static final ItemStack ICON = new ItemStack(Items.EXPERIENCE_BOTTLE);
	/** Extreme Dimensions puts its two 20-pixel buttons at the book's right edge + 2 and + 24. */
	private static final int OFFSET = FabricLoader.getInstance().isModLoaded("extremedimensions") ? 46 : 2;

	private final AbstractWidget anchor;

	private SheetButton(AbstractWidget anchor) {
		super(anchor.getX() + anchor.getWidth() + OFFSET, anchor.getY(), 20, 18, Component.translatable("gui.extremeprogression.sheet.title"));
		this.anchor = anchor;
		this.setTooltip(Tooltip.create(Component.translatable("gui.extremeprogression.sheet.button")));
	}

	public static void attach(InventoryScreen screen) {
		for (AbstractWidget w : Screens.getWidgets(screen)) {
			if (w instanceof ImageButton) {
				Screens.getWidgets(screen).add(new SheetButton(w));
				return;
			}
		}
	}

	@Override
	public void onPress(InputWithModifiers input) {
		Minecraft mc = Minecraft.getInstance();
		mc.gui.setScreen(new CharacterSheetScreen(mc.player != null ? new InventoryScreen(mc.player) : null));
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		// Stay beside the recipe book button wherever it moves
		this.setX(anchor.getX() + anchor.getWidth() + OFFSET);
		this.setY(anchor.getY());
		this.extractDefaultSprite(g);
		g.fakeItem(ICON, this.getX() + 2, this.getY() + 1);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput out) {
		out.add(NarratedElementType.TITLE, this.getMessage());
	}
}
