package com.extremeprogression.client.state;

import java.util.List;

import com.extremeprogression.config.ConfigFile;
import com.extremeprogression.config.DataFiles;
import com.extremeprogression.config.Settings;
import com.extremeprogression.network.LeaderboardPayload;
import com.extremeprogression.network.SettingsPayload;
import com.extremeprogression.network.XpEventPayload;

import net.minecraft.client.Minecraft;

/**
 * What the client knows of the server's administration: its settings (so the GUI shows the server's numbers), whether
 * this player may change them, the running XP events and the last leaderboard.
 */
public final class ClientAdmin {
	private ClientAdmin() {}

	private static String settings = null;
	private static boolean editor = false;
	/** Whether this client took over a remote server's settings, to be undone on leaving. */
	private static boolean remote = false;
	private static List<XpEventPayload.Entry> events = List.of();
	private static long eventsAt = 0;
	private static List<LeaderboardPayload.Row> leaderboard = null;
	private static int leaderboardUpdates = 0;

	public static void apply(SettingsPayload payload) {
		settings = payload.json();
		editor = payload.editor();
		// (in a world of this client's own, the server's settings are these already: the same game)
		if (!Minecraft.getInstance().hasSingleplayerServer()) {
			Settings.applyShared(payload.json());
			remote = true;
		}
	}

	public static void apply(XpEventPayload payload) {
		events = List.copyOf(payload.events());
		eventsAt = System.currentTimeMillis();
	}

	public static void apply(LeaderboardPayload payload) {
		leaderboard = List.copyOf(payload.rows());
		leaderboardUpdates++;
	}

	/** On leaving a server: back to this client's own settings. */
	public static void clear() {
		if (remote) {
			ConfigFile.load();
			DataFiles.load();
			remote = false;
		}
		settings = null;
		editor = false;
		events = List.of();
		leaderboard = null;
	}

	/** The server's settings as one JSON text, or null before they arrived. */
	public static String settings() {
		return settings;
	}

	public static boolean editor() {
		return editor;
	}

	public static List<XpEventPayload.Entry> events() {
		return events;
	}

	/** Milliseconds an event still runs (0 for one that runs until reset). */
	public static long left(XpEventPayload.Entry e) {
		return e.millisLeft() == 0 ? 0 : Math.max(0, e.millisLeft() - (System.currentTimeMillis() - eventsAt));
	}

	public static List<LeaderboardPayload.Row> leaderboard() {
		return leaderboard;
	}

	public static int leaderboardUpdates() {
		return leaderboardUpdates;
	}
}
