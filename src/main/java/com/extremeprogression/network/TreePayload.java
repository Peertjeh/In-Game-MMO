package com.extremeprogression.network;

import com.extremeprogression.ExtremeProgression;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A request from the Skill Screen, client to server: choose a specialization, buy a point in a node, or respec a skill.
 * The server checks everything again; the client only asks.
 */
public record TreePayload(Action action, String skill, String arg) implements CustomPacketPayload {
	public enum Action { CHOOSE, BUY, RESPEC }

	public static final Type<TreePayload> TYPE = new Type<>(ExtremeProgression.id("tree"));
	public static final StreamCodec<FriendlyByteBuf, TreePayload> CODEC = StreamCodec.ofMember(TreePayload::write, TreePayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(action.ordinal());
		buf.writeUtf(skill);
		buf.writeUtf(arg);
	}

	private static TreePayload read(FriendlyByteBuf buf) {
		Action[] actions = Action.values();
		int a = buf.readVarInt();
		return new TreePayload(actions[Math.floorMod(a, actions.length)], buf.readUtf(), buf.readUtf());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
