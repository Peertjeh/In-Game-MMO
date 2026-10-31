package com.extremeprogression.network;

import com.extremeprogression.ExtremeProgression;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A button on the Party screen, client to server: the same as the /party command of that name. */
public record PartyActionPayload(Action action, String name) implements CustomPacketPayload {
	public static final Type<PartyActionPayload> TYPE = new Type<>(ExtremeProgression.id("party_action"));
	public static final StreamCodec<FriendlyByteBuf, PartyActionPayload> CODEC = StreamCodec.ofMember(PartyActionPayload::write, PartyActionPayload::read);

	public enum Action {
		INVITE, ACCEPT, DECLINE, LEAVE, KICK, LEADER
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(action.ordinal());
		buf.writeUtf(name, 64);
	}

	private static PartyActionPayload read(FriendlyByteBuf buf) {
		int a = buf.readVarInt();
		String name = buf.readUtf(64);
		Action[] all = Action.values();
		// (an unknown action does nothing)
		return new PartyActionPayload(a >= 0 && a < all.length ? all[a] : null, name);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
