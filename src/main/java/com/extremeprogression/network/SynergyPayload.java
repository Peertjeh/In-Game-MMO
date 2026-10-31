package com.extremeprogression.network;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.synergy.Chronicle;
import com.extremeprogression.synergy.Synergy;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The Codex and the Chronicle, server to client (on joining and after every discovery): per synergy, whether and where
 * the player discovered it, and who discovered it first on the server.
 */
public record SynergyPayload(List<Entry> entries) implements CustomPacketPayload {
	public static final Type<SynergyPayload> TYPE = new Type<>(ExtremeProgression.id("synergies"));
	public static final StreamCodec<FriendlyByteBuf, SynergyPayload> CODEC = StreamCodec.ofMember(SynergyPayload::write, SynergyPayload::read);

	/**
	 * One synergy: {@code time} 0 when the player has not discovered it, else when and where (and for a duo synergy
	 * {@code with} whom); {@code firstName} empty when nobody on the server has yet ("Steve & Alex" for a duo synergy),
	 * {@code firstMine} when the player was among the first.
	 */
	public record Entry(String id, long time, String dimension, int x, int y, int z, String with, String firstName, long firstTime, boolean firstMine) {
		public boolean known() {
			return time > 0;
		}

		public boolean foundOnServer() {
			return !firstName.isEmpty();
		}
	}

	public static SynergyPayload of(PlayerProgress p, String uuid) {
		List<Entry> out = new ArrayList<>();
		for (Synergy s : Synergy.values()) {
			PlayerProgress.Discovery d = p.synergies.get(s.id);
			Chronicle.First f = Chronicle.first(s);
			out.add(new Entry(s.id, d == null ? 0 : Math.max(1, d.time), d == null ? "" : d.dimension, d == null ? 0 : d.x, d == null ? 0 : d.y,
					d == null ? 0 : d.z, d == null || d.with == null ? "" : d.with, f == null ? "" : f.names(), f == null ? 0 : f.time, f != null && f.by(uuid)));
		}
		return new SynergyPayload(out);
	}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(entries.size());
		for (Entry e : entries) {
			buf.writeUtf(e.id());
			buf.writeLong(e.time());
			buf.writeUtf(e.dimension());
			buf.writeVarInt(e.x());
			buf.writeVarInt(e.y());
			buf.writeVarInt(e.z());
			buf.writeUtf(e.with());
			buf.writeUtf(e.firstName());
			buf.writeLong(e.firstTime());
			buf.writeBoolean(e.firstMine());
		}
	}

	private static SynergyPayload read(FriendlyByteBuf buf) {
		int n = buf.readVarInt();
		List<Entry> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			out.add(new Entry(buf.readUtf(), buf.readLong(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readUtf(),
					buf.readLong(), buf.readBoolean()));
		}
		return new SynergyPayload(out);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
