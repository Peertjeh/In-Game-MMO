package com.extremeprogression.network;

import com.extremeprogression.ExtremeProgression;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: open the Character Sheet ({@code /skills}). */
public record OpenSheetPayload() implements CustomPacketPayload {
	public static final Type<OpenSheetPayload> TYPE = new Type<>(ExtremeProgression.id("open_sheet"));
	public static final StreamCodec<FriendlyByteBuf, OpenSheetPayload> CODEC = StreamCodec.ofMember(OpenSheetPayload::write, OpenSheetPayload::read);

	private void write(FriendlyByteBuf buf) {
	}

	private static OpenSheetPayload read(FriendlyByteBuf buf) {
		return new OpenSheetPayload();
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
