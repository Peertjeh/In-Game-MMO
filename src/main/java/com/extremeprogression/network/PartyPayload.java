package com.extremeprogression.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.extremeprogression.ExtremeProgression;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A player's party (design §16), server to client: its members (none outside a party), the bonus they give now, the
 * invitations waiting for the player and whom they could invite. Sent on every change, and once a second when who is
 * near or earning changed.
 */
public record PartyPayload(boolean enabled, int maxSize, double bonus, double bonusMax, List<Member> members, List<String> invites,
		List<String> invitable) implements CustomPacketPayload {
	public static final Type<PartyPayload> TYPE = new Type<>(ExtremeProgression.id("party"));
	public static final StreamCodec<FriendlyByteBuf, PartyPayload> CODEC = StreamCodec.ofMember(PartyPayload::write, PartyPayload::read);

	/**
	 * One member: {@code near} within the party range in the same dimension, {@code active} also earning XP of their
	 * own (so giving the bonus); the levels as last seen while offline.
	 */
	public record Member(UUID id, String name, boolean leader, boolean online, boolean near, boolean active, int total, int mastery) {}

	public boolean inParty() {
		return !members.isEmpty();
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeBoolean(enabled);
		buf.writeVarInt(maxSize);
		buf.writeDouble(bonus);
		buf.writeDouble(bonusMax);
		buf.writeVarInt(members.size());
		for (Member m : members) {
			buf.writeUUID(m.id());
			buf.writeUtf(m.name(), 64);
			buf.writeBoolean(m.leader());
			buf.writeBoolean(m.online());
			buf.writeBoolean(m.near());
			buf.writeBoolean(m.active());
			buf.writeVarInt(m.total());
			buf.writeVarInt(m.mastery());
		}
		writeNames(buf, invites);
		writeNames(buf, invitable);
	}

	private static void writeNames(FriendlyByteBuf buf, List<String> names) {
		buf.writeVarInt(names.size());
		for (String n : names) {
			buf.writeUtf(n, 64);
		}
	}

	private static List<String> readNames(FriendlyByteBuf buf) {
		int n = Math.min(buf.readVarInt(), 256);
		List<String> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(buf.readUtf(64));
		}
		return out;
	}

	private static PartyPayload read(FriendlyByteBuf buf) {
		boolean enabled = buf.readBoolean();
		int maxSize = buf.readVarInt();
		double bonus = buf.readDouble();
		double bonusMax = buf.readDouble();
		int n = Math.min(buf.readVarInt(), 256);
		List<Member> members = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			members.add(new Member(buf.readUUID(), buf.readUtf(64), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(),
					buf.readVarInt()));
		}
		List<String> invites = readNames(buf);
		List<String> invitable = readNames(buf);
		return new PartyPayload(enabled, maxSize, bonus, bonusMax, members, invites, invitable);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
