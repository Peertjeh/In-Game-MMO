package com.extremeprogression.network;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.xp.XpEvents;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The running XP events, server to client (on joining and whenever they change), for the Character Sheet. */
public record XpEventPayload(List<Entry> events) implements CustomPacketPayload {
	public static final Type<XpEventPayload> TYPE = new Type<>(ExtremeProgression.id("xp_events"));
	public static final StreamCodec<FriendlyByteBuf, XpEventPayload> CODEC = StreamCodec.ofMember(XpEventPayload::write, XpEventPayload::read);

	/** One event: {@code skill} empty for every skill; {@code millisLeft} 0 when it runs until reset. */
	public record Entry(String skill, double multiplier, long millisLeft) {}

	public static XpEventPayload now() {
		long now = System.currentTimeMillis();
		List<Entry> out = new ArrayList<>();
		for (XpEvents.Event e : XpEvents.active()) {
			out.add(new Entry(e.skill() == null ? "" : e.skill().id, e.multiplier(), e.endsAt() == 0 ? 0 : Math.max(1, e.endsAt() - now)));
		}
		return new XpEventPayload(out);
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(events.size());
		for (Entry e : events) {
			buf.writeUtf(e.skill());
			buf.writeDouble(e.multiplier());
			buf.writeVarLong(e.millisLeft());
		}
	}

	private static XpEventPayload read(FriendlyByteBuf buf) {
		int n = buf.readVarInt();
		List<Entry> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(new Entry(buf.readUtf(), buf.readDouble(), buf.readVarLong()));
		}
		return new XpEventPayload(out);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
