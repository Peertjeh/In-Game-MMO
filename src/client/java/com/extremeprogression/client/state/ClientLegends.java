package com.extremeprogression.client.state;

import com.extremeprogression.client.screen.LegendsScreen;
import com.extremeprogression.legends.Legends;
import com.extremeprogression.legends.LegendsData;
import com.extremeprogression.legends.Tome;
import com.extremeprogression.network.LegendsPayload;

import net.minecraft.client.Minecraft;

/** The player's legends as the server last sent them (2.0): boosts, streak, contracts, titles, the Skill of the Day. */
public final class ClientLegends {
	private ClientLegends() {}

	private static Legends.View view;
	private static int updates;

	public static void apply(LegendsPayload payload) {
		view = Legends.parse(payload.json());
		updates++;
		if (payload.open()) {
			Minecraft mc = Minecraft.getInstance();
			mc.gui.setScreen(new LegendsScreen(mc.screen));
		}
	}

	/** The last view, or null before the server sent one. */
	public static Legends.View current() {
		return view;
	}

	public static int updates() {
		return updates;
	}

	public static void clear() {
		view = null;
	}

	/** The tome boost running now (the strongest), or null. */
	public static LegendsData.Boost running(Legends.View v) {
		LegendsData.Boost best = null;
		if (v != null) {
			for (LegendsData.Boost b : v.boosts) {
				if (best == null || b.percent > best.percent) {
					best = b;
				}
			}
		}
		return best;
	}

	/** The colour of a boost: its tome's, or green for one an admin gave. */
	public static int color(LegendsData.Boost b) {
		Tome t = b == null ? null : Tome.byId(b.source);
		return t != null ? t.color : 0x55FF55;
	}
}
