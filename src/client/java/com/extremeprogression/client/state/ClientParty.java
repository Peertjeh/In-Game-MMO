package com.extremeprogression.client.state;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.extremeprogression.network.PartyPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** The player's party as the server last sent it (design §16), and the mark beside party members' names. */
public final class ClientParty {
	private ClientParty() {}

	private static volatile PartyPayload current = null;
	private static volatile Set<UUID> members = Set.of();
	private static volatile int updates = 0;

	public static void apply(PartyPayload payload) {
		current = payload;
		members = payload.members().stream().map(PartyPayload.Member::id).collect(Collectors.toUnmodifiableSet());
		updates++;
	}

	public static void clear() {
		current = null;
		members = Set.of();
		updates++;
	}

	/** The party view, or null before the server sent one. */
	public static PartyPayload current() {
		return current;
	}

	/** Counts the views received, so screens know when to rebuild. */
	public static int updates() {
		return updates;
	}

	/** Whether this other player is in the player's party. */
	public static boolean member(UUID player) {
		Minecraft mc = Minecraft.getInstance();
		return members.contains(player) && (mc.player == null || !mc.player.getUUID().equals(player));
	}

	/** The green mark before a party member's name above their head, or null. */
	public static Component mark(UUID player) {
		if (!member(player) || ClientPower.hiddenHere()) {
			return null;
		}
		return Component.literal("◆").withColor(0x55FF55);
	}
}
