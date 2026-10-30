package com.extremeprogression.server;

import com.extremeprogression.command.SkillsCommand;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.network.Sync;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/** The server's lifecycle and the players coming and going. */
public final class ServerEvents {
	private ServerEvents() {}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> PlayerStore.open(s.getWorldPath(LevelResource.ROOT)));
		// The progression is written with the world's own saves (autosave, /save-all, stop), never ahead of them
		ServerLifecycleEvents.AFTER_SAVE.register((s, flush, force) -> PlayerStore.flush());
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> PlayerStore.flush());
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> PlayerStore.close());

		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
			ServerPlayer player = handler.getPlayer();
			PlayerProgress p = PlayerStore.get(player.getUUID());
			String name = player.getName().getString();
			if (!name.equals(p.name)) {
				p.name = name;
				PlayerStore.markDirty(player.getUUID());
			}
			Sync.send(player);
		});
		// (on the server's thread: a player in a single-player world leaves from the connection's own)
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> s.executeIfPossible(() -> PlayerStore.unload(handler.getPlayer().getUUID())));

		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> SkillsCommand.register(dispatcher));
	}
}
