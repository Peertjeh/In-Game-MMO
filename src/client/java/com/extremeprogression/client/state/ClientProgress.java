package com.extremeprogression.client.state;

import com.extremeprogression.network.ProgressPayload;

/** The client's copy of its player's progression, as the server last sent it. Only shown, never computed here. */
public final class ClientProgress {
	private ClientProgress() {}

	private static ProgressPayload current = null;
	private static int updates = 0;

	public static void apply(ProgressPayload payload) {
		current = payload;
		updates++;
	}

	public static void clear() {
		current = null;
	}

	/** Null until the server has sent the progression. */
	public static ProgressPayload current() {
		return current;
	}

	/** How many times the progression arrived (for tests). */
	public static int updates() {
		return updates;
	}

	public static ProgressPayload.Entry skill(String id) {
		if (current == null) {
			return null;
		}
		for (ProgressPayload.Entry e : current.skills()) {
			if (e.id().equals(id)) {
				return e;
			}
		}
		return null;
	}
}
