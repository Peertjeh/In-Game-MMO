package com.extremeprogression.network;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.data.DeathData;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Deaths, server to client: the history for the Death Register (on joining and after every respawn), and the death to
 * show the summary of now, if any (right after respawning).
 */
public record DeathPayload(List<DeathData.Record> history, DeathData.Record summary) implements CustomPacketPayload {
	public static final Type<DeathPayload> TYPE = new Type<>(ExtremeProgression.id("deaths"));
	public static final StreamCodec<FriendlyByteBuf, DeathPayload> CODEC = StreamCodec.ofMember(DeathPayload::write, DeathPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(history.size());
		for (DeathData.Record r : history) {
			record(buf, r);
		}
		buf.writeBoolean(summary != null);
		if (summary != null) {
			record(buf, summary);
		}
	}

	private static DeathPayload read(FriendlyByteBuf buf) {
		int n = buf.readVarInt();
		List<DeathData.Record> history = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			history.add(record(buf));
		}
		DeathData.Record summary = buf.readBoolean() ? record(buf) : null;
		return new DeathPayload(history, summary);
	}

	private static void record(FriendlyByteBuf buf, DeathData.Record r) {
		buf.writeLong(r.time);
		buf.writeUtf(r.dimension);
		buf.writeVarInt(r.x);
		buf.writeVarInt(r.y);
		buf.writeVarInt(r.z);
		buf.writeUtf(r.cause.length() > 512 ? r.cause.substring(0, 512) : r.cause);
		buf.writeVarInt(r.pressure);
		buf.writeDouble(r.multiplier);
		buf.writeVarInt(r.losses.size());
		for (DeathData.Loss l : r.losses) {
			buf.writeUtf(l.skill);
			buf.writeDouble(l.xp);
			buf.writeBoolean(l.mastery);
			buf.writeVarInt(l.levelBefore);
			buf.writeVarInt(l.levelAfter);
			buf.writeVarInt(l.ranksBefore);
			buf.writeVarInt(l.ranksAfter);
		}
	}

	private static DeathData.Record record(FriendlyByteBuf buf) {
		DeathData.Record r = new DeathData.Record();
		r.time = buf.readLong();
		r.dimension = buf.readUtf();
		r.x = buf.readVarInt();
		r.y = buf.readVarInt();
		r.z = buf.readVarInt();
		r.cause = buf.readUtf();
		r.pressure = buf.readVarInt();
		r.multiplier = buf.readDouble();
		int n = buf.readVarInt();
		for (int i = 0; i < n; i++) {
			DeathData.Loss l = new DeathData.Loss();
			l.skill = buf.readUtf();
			l.xp = buf.readDouble();
			l.mastery = buf.readBoolean();
			l.levelBefore = buf.readVarInt();
			l.levelAfter = buf.readVarInt();
			l.ranksBefore = buf.readVarInt();
			l.ranksAfter = buf.readVarInt();
			r.losses.add(l);
		}
		return r;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
