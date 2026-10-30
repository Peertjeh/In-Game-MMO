package com.extremeprogression.client.state;

import java.util.LinkedHashMap;
import java.util.Map;

import com.extremeprogression.network.SynergyPayload;
import com.extremeprogression.synergy.Synergy;

/** The client's copy of its player's Codex and the server's Chronicle, as the server last sent them. */
public final class ClientSynergies {
	private ClientSynergies() {}

	private static final Map<String, SynergyPayload.Entry> ENTRIES = new LinkedHashMap<>();
	private static int updates = 0;

	public static void apply(SynergyPayload payload) {
		ENTRIES.clear();
		for (SynergyPayload.Entry e : payload.entries()) {
			ENTRIES.put(e.id(), e);
		}
		updates++;
	}

	public static void clear() {
		ENTRIES.clear();
	}

	/** What the server said about a synergy, or null before it said anything. */
	public static SynergyPayload.Entry entry(Synergy s) {
		return ENTRIES.get(s.id);
	}

	public static boolean known(Synergy s) {
		SynergyPayload.Entry e = entry(s);
		return e != null && e.known();
	}

	public static int discovered() {
		return (int) ENTRIES.values().stream().filter(SynergyPayload.Entry::known).count();
	}

	public static int updates() {
		return updates;
	}
}
