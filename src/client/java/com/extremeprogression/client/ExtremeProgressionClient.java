package com.extremeprogression.client;

import com.mojang.blaze3d.platform.InputConstants;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.client.inventory.SheetButton;
import com.extremeprogression.client.screen.CharacterSheetScreen;
import com.extremeprogression.client.state.ClientProgress;
import com.extremeprogression.network.OpenSheetPayload;
import com.extremeprogression.network.ProgressPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/** Client entrypoint: the progression from the server, the Character Sheet's hotkey, inventory button and packet. */
public class ExtremeProgressionClient implements ClientModInitializer {
	private static final KeyMapping.Category KEYS = KeyMapping.Category.register(ExtremeProgression.id("main"));
	/** `K` by default (design §1), rebindable in Controls. */
	public static final KeyMapping OPEN_SHEET = new KeyMapping("key.extremeprogression.sheet", InputConstants.KEY_K, KEYS);

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(ProgressPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientProgress.apply(payload)));
		ClientPlayNetworking.registerGlobalReceiver(OpenSheetPayload.TYPE, (payload, context) ->
				context.client().execute(() -> context.client().gui.setScreen(new CharacterSheetScreen(null))));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientProgress::clear));

		KeyMappingHelper.registerKeyMapping(OPEN_SHEET);
		// Key mappings only register presses while no screen is open, so this never opens over another screen
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_SHEET.consumeClick()) {
				// (on a server without the mod nothing was sent, and there is nothing to show)
				if (client.getConnection() != null && ClientProgress.current() != null) {
					client.gui.setScreen(new CharacterSheetScreen(null));
				}
			}
		});
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof InventoryScreen inv && ClientProgress.current() != null) {
				SheetButton.attach(inv);
			}
		});
	}
}
