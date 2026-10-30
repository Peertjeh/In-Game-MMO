package com.extremeprogression.network;

import com.extremeprogression.ExtremeProgression;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The ability key, client to server: {@code R} for a skill's first specialization, {@code Shift + R} for its second. */
public record AbilityPayload(boolean second) implements CustomPacketPayload {
	public static final Type<AbilityPayload> TYPE = new Type<>(ExtremeProgression.id("ability"));
	public static final StreamCodec<FriendlyByteBuf, AbilityPayload> CODEC = StreamCodec.ofMember(AbilityPayload::write, AbilityPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeBoolean(second);
	}

	private static AbilityPayload read(FriendlyByteBuf buf) {
		return new AbilityPayload(buf.readBoolean());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
