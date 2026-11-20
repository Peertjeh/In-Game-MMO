package com.extremeprogression.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.extremeprogression.ExtremeProgression;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Every online player's power level (total level and Mastery ranks), server to client, for the level shown beside the
 * name above their heads. Sent to everyone whenever one of them changes, and whole to a player who joins.
 */
public record PowerPayload(List<Entry> players) implements CustomPacketPayload {
	public static final Type<PowerPayload> TYPE = new Type<>(ExtremeProgression.id("power"));
	public static final StreamCodec<FriendlyByteBuf, PowerPayload> CODEC = StreamCodec.ofMember(PowerPayload::write, PowerPayload::read);

	/** One player: their total level, Mastery ranks, and the title they show ("" for none, 2.0). */
	public record Entry(UUID player, int total, int mastery, String title) {}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(players.size());
		for (Entry e : players) {
			buf.writeUUID(e.player());
			buf.writeVarInt(e.total());
			buf.writeVarInt(e.mastery());
			buf.writeUtf(e.title(), 64);
		}
	}

	private static PowerPayload read(FriendlyByteBuf buf) {
		int n = buf.readVarInt();
		List<Entry> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(new Entry(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(64)));
		}
		return new PowerPayload(out);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
