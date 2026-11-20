package com.extremeprogression.network;

import com.extremeprogression.ExtremeProgression;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A button in the Legends screen, client to server: "open", "reroll" with a contract's index, "title" with its id. */
public record LegendsActionPayload(String action, String arg) implements CustomPacketPayload {
	public static final Type<LegendsActionPayload> TYPE = new Type<>(ExtremeProgression.id("legends_action"));
	public static final StreamCodec<FriendlyByteBuf, LegendsActionPayload> CODEC = StreamCodec.ofMember(LegendsActionPayload::write, LegendsActionPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeUtf(action, 32);
		buf.writeUtf(arg, 64);
	}

	private static LegendsActionPayload read(FriendlyByteBuf buf) {
		return new LegendsActionPayload(buf.readUtf(32), buf.readUtf(64));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
