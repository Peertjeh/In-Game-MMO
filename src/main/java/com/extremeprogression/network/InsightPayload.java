package com.extremeprogression.network;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.ExtremeProgression;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Arcane Insight (Enchanter), server to client: every enchantment of each of the three offers at the open enchanting
 * table (empty when Insight is over), and whether the free reroll is still there.
 */
public record InsightPayload(List<List<Line>> offers, boolean reroll) implements CustomPacketPayload {
	public static final Type<InsightPayload> TYPE = new Type<>(ExtremeProgression.id("insight"));
	public static final StreamCodec<FriendlyByteBuf, InsightPayload> CODEC = StreamCodec.ofMember(InsightPayload::write, InsightPayload::read);

	/** One enchantment of an offer: its id and level. */
	public record Line(String enchantment, int level) {}

	private void write(FriendlyByteBuf buf) {
		buf.writeBoolean(reroll);
		buf.writeVarInt(offers.size());
		for (List<Line> offer : offers) {
			buf.writeVarInt(offer.size());
			for (Line l : offer) {
				buf.writeUtf(l.enchantment());
				buf.writeVarInt(l.level());
			}
		}
	}

	private static InsightPayload read(FriendlyByteBuf buf) {
		boolean reroll = buf.readBoolean();
		int n = buf.readVarInt();
		List<List<Line>> offers = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			int m = buf.readVarInt();
			List<Line> offer = new ArrayList<>(m);
			for (int j = 0; j < m; j++) {
				offer.add(new Line(buf.readUtf(), buf.readVarInt()));
			}
			offers.add(offer);
		}
		return new InsightPayload(offers, reroll);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
