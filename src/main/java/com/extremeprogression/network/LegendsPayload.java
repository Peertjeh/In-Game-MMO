package com.extremeprogression.network;

import com.extremeprogression.ExtremeProgression;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A player's legends (boosts, streak, contracts, titles, the Skill of the Day), server to client, as the JSON of
 * {@code Legends.View}; {@code open} opens the Legends screen.
 */
public record LegendsPayload(String json, boolean open) implements CustomPacketPayload {
	public static final Type<LegendsPayload> TYPE = new Type<>(ExtremeProgression.id("legends"));
	public static final StreamCodec<FriendlyByteBuf, LegendsPayload> CODEC = StreamCodec.ofMember(LegendsPayload::write, LegendsPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeUtf(json, 1 << 20);
		buf.writeBoolean(open);
	}

	private static LegendsPayload read(FriendlyByteBuf buf) {
		return new LegendsPayload(buf.readUtf(1 << 20), buf.readBoolean());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
