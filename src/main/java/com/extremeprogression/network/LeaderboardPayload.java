package com.extremeprogression.network;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.ExtremeProgression;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The leaderboard (design §11): a client asks with an empty one, the server answers with every player's levels (the
 * first 100 by total), from their files when they are offline.
 */
public record LeaderboardPayload(List<Row> rows) implements CustomPacketPayload {
	public static final Type<LeaderboardPayload> TYPE = new Type<>(ExtremeProgression.id("leaderboard"));
	public static final StreamCodec<FriendlyByteBuf, LeaderboardPayload> CODEC = StreamCodec.ofMember(LeaderboardPayload::write, LeaderboardPayload::read);

	/** One player: name, total level, Mastery ranks in all, and per skill (in the order of the skills) level and ranks. */
	public record Row(String name, int total, int mastery, int[] levels, int[] ranks) {}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(rows.size());
		for (Row r : rows) {
			buf.writeUtf(r.name());
			buf.writeVarInt(r.total());
			buf.writeVarInt(r.mastery());
			buf.writeVarIntArray(r.levels());
			buf.writeVarIntArray(r.ranks());
		}
	}

	private static LeaderboardPayload read(FriendlyByteBuf buf) {
		int n = buf.readVarInt();
		List<Row> rows = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			rows.add(new Row(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarIntArray(), buf.readVarIntArray()));
		}
		return new LeaderboardPayload(rows);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
