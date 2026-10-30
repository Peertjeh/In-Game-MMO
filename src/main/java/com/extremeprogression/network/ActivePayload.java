package com.extremeprogression.network;

import java.util.LinkedHashMap;
import java.util.Map;

import com.extremeprogression.ExtremeProgression;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The abilities in effect, server to client, sent whenever one starts or ends: each specialization's id and the ticks it
 * still lasts. The client shows them, and feels the few that act on its own movement and view (no sprint, zoom).
 */
public record ActivePayload(Map<String, Integer> ticks) implements CustomPacketPayload {
	public static final Type<ActivePayload> TYPE = new Type<>(ExtremeProgression.id("active"));
	public static final StreamCodec<FriendlyByteBuf, ActivePayload> CODEC = StreamCodec.ofMember(ActivePayload::write, ActivePayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(ticks.size());
		ticks.forEach((spec, left) -> {
			buf.writeUtf(spec);
			buf.writeVarInt(left);
		});
	}

	private static ActivePayload read(FriendlyByteBuf buf) {
		int n = buf.readVarInt();
		Map<String, Integer> ticks = new LinkedHashMap<>();
		for (int i = 0; i < n; i++) {
			ticks.put(buf.readUtf(), buf.readVarInt());
		}
		return new ActivePayload(ticks);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
