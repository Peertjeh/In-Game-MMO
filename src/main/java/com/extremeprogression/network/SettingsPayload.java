package com.extremeprogression.network;

import com.extremeprogression.ExtremeProgression;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The server's settings (the config, skills.json and synergies.json as one JSON text), both ways: to a client on
 * joining and after every change, with whether that player may change them and whether to open the settings screen;
 * from an admin's client, the settings they saved ({@code editor} and {@code open} unused).
 */
public record SettingsPayload(String json, boolean editor, boolean open) implements CustomPacketPayload {
	public static final Type<SettingsPayload> TYPE = new Type<>(ExtremeProgression.id("settings"));
	public static final StreamCodec<FriendlyByteBuf, SettingsPayload> CODEC = StreamCodec.ofMember(SettingsPayload::write, SettingsPayload::read);
	private static final int MAX = 1 << 18;

	private void write(FriendlyByteBuf buf) {
		buf.writeUtf(json, MAX);
		buf.writeBoolean(editor);
		buf.writeBoolean(open);
	}

	private static SettingsPayload read(FriendlyByteBuf buf) {
		return new SettingsPayload(buf.readUtf(MAX), buf.readBoolean(), buf.readBoolean());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
