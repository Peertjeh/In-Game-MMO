package com.extremeprogression.network;

import com.extremeprogression.ExtremeProgression;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A button of the Players screen (1.6.0), client to server: the player it is about (their UUID), what to do
 * ({@code select}, {@code level}, {@code respec}, {@code cooldowns}, {@code clear_deaths}, {@code reset_skill},
 * {@code reset_all}), the skill (or empty) and an amount (levels, for {@code level}). The server checks it all.
 */
public record AdminActionPayload(String target, String action, String skill, int amount) implements CustomPacketPayload {
	public static final Type<AdminActionPayload> TYPE = new Type<>(ExtremeProgression.id("admin_action"));
	public static final StreamCodec<FriendlyByteBuf, AdminActionPayload> CODEC = StreamCodec.ofMember(AdminActionPayload::write, AdminActionPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeUtf(target, 64);
		buf.writeUtf(action, 32);
		buf.writeUtf(skill, 32);
		buf.writeVarInt(amount);
	}

	private static AdminActionPayload read(FriendlyByteBuf buf) {
		return new AdminActionPayload(buf.readUtf(64), buf.readUtf(32), buf.readUtf(32), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
