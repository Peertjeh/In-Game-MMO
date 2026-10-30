package com.extremeprogression.network;

import com.extremeprogression.ExtremeProgression;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Another player's progression for an operator's read-only look ({@code /skills inspect}), server to client: their
 * name, whether they are online, everything the Character Sheet shows, and how many synergies they discovered.
 */
public record InspectPayload(String name, boolean online, ProgressPayload progress, int synergies) implements CustomPacketPayload {
	public static final Type<InspectPayload> TYPE = new Type<>(ExtremeProgression.id("inspect"));
	public static final StreamCodec<FriendlyByteBuf, InspectPayload> CODEC = StreamCodec.ofMember(InspectPayload::write, InspectPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeUtf(name);
		buf.writeBoolean(online);
		ProgressPayload.CODEC.encode(buf, progress);
		buf.writeVarInt(synergies);
	}

	private static InspectPayload read(FriendlyByteBuf buf) {
		return new InspectPayload(buf.readUtf(), buf.readBoolean(), ProgressPayload.CODEC.decode(buf), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
