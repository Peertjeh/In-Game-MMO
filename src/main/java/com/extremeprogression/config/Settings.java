package com.extremeprogression.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.network.SettingsPayload;
import com.extremeprogression.synergy.Synergy;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Everything an admin can set, as one: the config and the data files that clients need too (skills.json and
 * synergies.json; the XP tables only matter on the server). Sent to every client, so the GUI shows the server's own
 * numbers, and changed from the settings screen.
 */
public final class Settings {
	private Settings() {}

	private static final Gson GSON = new Gson();

	/** Rereads the config and the data files ({@code /skills reload}) and tells every client. */
	public static void reload(MinecraftServer server) {
		ConfigFile.load();
		DataFiles.load();
		ExtremeProgression.LOGGER.info("Reloaded the config and the data files");
		syncAll(server);
	}

	/** The settings a client gets, as one JSON text. */
	public static String shared() {
		JsonObject json = new JsonObject();
		json.add("config", Config.toJson());
		json.add("skills", SkillSettings.toJson());
		json.add("synergies", Synergy.toJson());
		return GSON.toJson(json);
	}

	/**
	 * Applies settings from a JSON text: on a client the server's (for its GUI), on the server an admin's change from the
	 * settings screen. Returns false when the text is no settings at all.
	 */
	public static boolean applyShared(String text) {
		JsonObject json;
		try {
			json = JsonParser.parseString(text).getAsJsonObject();
		} catch (RuntimeException e) {
			return false;
		}
		if (!json.has("config") || !json.get("config").isJsonObject()) {
			return false;
		}
		Config.apply(json.getAsJsonObject("config"));
		if (json.has("skills") && json.get("skills").isJsonObject()) {
			SkillSettings.apply(json.getAsJsonObject("skills"));
		}
		if (json.has("synergies") && json.get("synergies").isJsonObject()) {
			Synergy.apply(json.getAsJsonObject("synergies"));
		}
		return true;
	}

	/** Writes the config and the two shared data files as they are now (after a change from the settings screen). */
	public static void save() {
		DataFiles.write(ConfigFile.path(), Config.toJson());
		DataFiles.write(DataFiles.dir().resolve("skills.json"), SkillSettings.toJson());
		DataFiles.write(DataFiles.dir().resolve("synergies.json"), Synergy.toJson());
	}

	/** Whether a player may change the settings: an admin (op level 3), as for {@code /skills reload}. */
	public static boolean mayEdit(ServerPlayer p) {
		java.util.function.Predicate<net.minecraft.commands.CommandSourceStack> admins = Commands.hasPermission(Commands.LEVEL_ADMINS);
		return admins.test(p.createCommandSourceStack());
	}

	public static void send(ServerPlayer p, boolean open) {
		if (ServerPlayNetworking.canSend(p, SettingsPayload.TYPE)) {
			ServerPlayNetworking.send(p, new SettingsPayload(shared(), mayEdit(p), open));
		}
	}

	public static void syncAll(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			send(p, false);
		}
	}
}
