package com.extremeprogression.network;

import com.extremeprogression.ExtremeProgression;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The Players screen's data (1.6.0), server to an admin's client: every player the world knows and, for the one picked,
 * their overview and every skill, as one JSON text; and whether to open the screen.
 */
public record AdminPayload(String json, boolean open) implements CustomPacketPayload {
	public static final Type<AdminPayload> TYPE = new Type<>(ExtremeProgression.id("admin"));
	public static final StreamCodec<FriendlyByteBuf, AdminPayload> CODEC = StreamCodec.ofMember(AdminPayload::write, AdminPayload::read);
	private static final int MAX = 1 << 20;

	private void write(FriendlyByteBuf buf) {
		buf.writeUtf(json, MAX);
		buf.writeBoolean(open);
	}

	private static AdminPayload read(FriendlyByteBuf buf) {
		return new AdminPayload(buf.readUtf(MAX), buf.readBoolean());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
