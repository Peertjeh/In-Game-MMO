package com.extremeprogression.network;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.ExtremeProgression;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Ore Sense, server to client: the ores near the player that may glow for them, and for how many ticks. The server sends
 * only these positions, so the client never learns more of the world than this (design §2: no x-ray).
 */
public record OreSensePayload(List<Ore> ores, int ticks) implements CustomPacketPayload {
	public static final Type<OreSensePayload> TYPE = new Type<>(ExtremeProgression.id("ore_sense"));
	public static final StreamCodec<FriendlyByteBuf, OreSensePayload> CODEC = StreamCodec.ofMember(OreSensePayload::write, OreSensePayload::read);

	/** One ore and the colour of its glow. */
	public record Ore(BlockPos pos, int color) {}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(ticks);
		buf.writeVarInt(ores.size());
		for (Ore ore : ores) {
			buf.writeLong(ore.pos().asLong());
			buf.writeInt(ore.color());
		}
	}

	private static OreSensePayload read(FriendlyByteBuf buf) {
		int ticks = buf.readVarInt();
		int n = buf.readVarInt();
		List<Ore> ores = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			ores.add(new Ore(BlockPos.of(buf.readLong()), buf.readInt()));
		}
		return new OreSensePayload(ores, ticks);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
