package com.extremeprogression.network;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.ExtremeProgression;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The player's whole progression, server to client: sent on joining and after every change. The client only shows it;
 * every number is worked out on the server.
 */
public record ProgressPayload(int total, int cap, double budgetFactor, int maxLevel, int masteryRanksMax, double masteryXpPerRank,
							  List<Entry> skills) implements CustomPacketPayload {
	public static final Type<ProgressPayload> TYPE = new Type<>(ExtremeProgression.id("progress"));
	public static final StreamCodec<FriendlyByteBuf, ProgressPayload> CODEC = StreamCodec.ofMember(ProgressPayload::write, ProgressPayload::read);

	/** One skill: its level, the XP within the level and what the next level needs, and its Mastery. */
	public record Entry(String id, int level, double xp, double need, int masteryRanks, double masteryXp) {}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(total);
		buf.writeVarInt(cap);
		buf.writeDouble(budgetFactor);
		buf.writeVarInt(maxLevel);
		buf.writeVarInt(masteryRanksMax);
		buf.writeDouble(masteryXpPerRank);
		buf.writeVarInt(skills.size());
		for (Entry e : skills) {
			buf.writeUtf(e.id());
			buf.writeVarInt(e.level());
			buf.writeDouble(e.xp());
			buf.writeDouble(e.need());
			buf.writeVarInt(e.masteryRanks());
			buf.writeDouble(e.masteryXp());
		}
	}

	private static ProgressPayload read(FriendlyByteBuf buf) {
		int total = buf.readVarInt();
		int cap = buf.readVarInt();
		double factor = buf.readDouble();
		int maxLevel = buf.readVarInt();
		int ranksMax = buf.readVarInt();
		double perRank = buf.readDouble();
		int n = buf.readVarInt();
		List<Entry> skills = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			skills.add(new Entry(buf.readUtf(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readDouble()));
		}
		return new ProgressPayload(total, cap, factor, maxLevel, ranksMax, perRank, skills);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
