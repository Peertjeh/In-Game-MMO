package com.extremeprogression.server;

import java.util.UUID;

import com.extremeprogression.ability.AbilityService;
import com.extremeprogression.antiexploit.AfkTracker;
import com.extremeprogression.antiexploit.MobOrigins;
import com.extremeprogression.antiexploit.PlacedBlocks;
import com.extremeprogression.command.SkillsCommand;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.network.Sync;
import com.extremeprogression.tree.TreeService;
import com.extremeprogression.trunk.Trunks;
import com.extremeprogression.xp.CombatXp;
import com.extremeprogression.xp.CraftingXp;
import com.extremeprogression.xp.ExplorationXp;
import com.extremeprogression.xp.FishingXp;
import com.extremeprogression.xp.GatheringXp;
import com.extremeprogression.xp.Harvests;
import com.extremeprogression.xp.XpService;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.storage.LevelResource;

/** The server's lifecycle, the players coming and going, and the XP sources. */
public final class ServerEvents {
	private ServerEvents() {}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			PlayerStore.open(s.getWorldPath(LevelResource.ROOT));
			com.extremeprogression.synergy.Chronicle.open(s.getWorldPath(LevelResource.ROOT));
		});
		// The progression is written with the world's own saves (autosave, /save-all, stop), never ahead of them
		ServerLifecycleEvents.AFTER_SAVE.register((s, flush, force) -> PlayerStore.flush());
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> PlayerStore.flush());
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			PlayerStore.close();
			com.extremeprogression.synergy.Chronicle.close();
			com.extremeprogression.xp.XpEvents.clear();
			com.extremeprogression.compat.TabPlaceholders.clear();
			com.extremeprogression.command.AdminCommands.clear();
			AfkTracker.clear();
			XpService.clear();
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
			ServerPlayer player = handler.getPlayer();
			PlayerProgress p = PlayerStore.get(player.getUUID());
			String name = player.getName().getString();
			if (!name.equals(p.name)) {
				p.name = name;
				PlayerStore.markDirty(player.getUUID());
			}
			Sync.send(player);
			com.extremeprogression.death.DeathService.sendHistory(player, null);
			com.extremeprogression.synergy.Synergies.send(player);
			com.extremeprogression.config.Settings.send(player, false);
			com.extremeprogression.xp.XpEvents.send(player);
		});
		// (on the server's thread: a player in a single-player world leaves from the connection's own)
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> s.executeIfPossible(() -> {
			UUID id = handler.getPlayer().getUUID();
			PlayerStore.unload(id);
			AfkTracker.forget(id);
			XpService.forget(id);
			FishingXp.forget(id);
			Harvests.forget(id);
			CombatXp.forget(id);
			ExplorationXp.forget(id);
			com.extremeprogression.spec.Specs.forget(id);
			com.extremeprogression.death.DeathService.forget(id);
			com.extremeprogression.synergy.SynergyEffects.forget(id);
		}));

		// XP sources (design §2) and the rules against exploits (design §12)
		PlacedBlocks.register();
		MobOrigins.register();
		GatheringXp.register();
		Harvests.register();
		CombatXp.register();
		CraftingXp.register();

		// Trees and abilities (design §6)
		TreeService.register();
		AbilityService.register();
		Trunks.register();
		com.extremeprogression.spec.Specs.register();
		com.extremeprogression.death.DeathService.register();
		com.extremeprogression.synergy.SynergyEffects.register();
		// An admin's settings from the settings screen
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(com.extremeprogression.network.SettingsPayload.TYPE,
				(payload, context) -> com.extremeprogression.command.AdminCommands.settingsSaved(context.player(), payload.json()));
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(com.extremeprogression.network.LeaderboardPayload.TYPE,
				(payload, context) -> com.extremeprogression.command.AdminCommands.sendLeaderboard(context.player()));
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!level.isClientSide()) {
				AfkTracker.interacted(player.getUUID(), level.getServer().getTickCount());
			}
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			long tick = s.getTickCount();
			for (ServerPlayer p : s.getPlayerList().getPlayers()) {
				AfkTracker.tick(p, tick);
			}
			Harvests.tick(s);
			ExplorationXp.tick(s);
			Trunks.tick(s);
			com.extremeprogression.spec.Specs.tick(s);
			com.extremeprogression.death.DeathService.tick(s);
			com.extremeprogression.synergy.SynergyEffects.tick(s);
			com.extremeprogression.xp.XpEvents.tick(s);
			com.extremeprogression.compat.TabPlaceholders.tick(s);
			XpService.tick(s);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> SkillsCommand.register(dispatcher));
	}
}
