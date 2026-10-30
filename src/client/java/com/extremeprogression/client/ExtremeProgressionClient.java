package com.extremeprogression.client;

import com.extremeprogression.client.state.ClientProgress;
import com.extremeprogression.network.ProgressPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client entrypoint: receives the progression; the screens, HUD notices, hotkeys and inventory button follow. */
public class ExtremeProgressionClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(ProgressPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientProgress.apply(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientProgress::clear));
	}
}
