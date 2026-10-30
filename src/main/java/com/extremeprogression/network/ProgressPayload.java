package com.extremeprogression.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.extremeprogression.ExtremeProgression;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The player's whole progression, server to client: sent on joining and after every change. The client only shows it;
 * every number is worked out on the server.
 */
public record ProgressPayload(int total, int cap, double budgetFactor, int maxLevel, int masteryRanksMax, double masteryXpPerRank,
							  List<Entry> skills, Death death) implements CustomPacketPayload {
	public static final Type<ProgressPayload> TYPE = new Type<>(ExtremeProgression.id("progress"));
	public static final StreamCodec<FriendlyByteBuf, ProgressPayload> CODEC = StreamCodec.ofMember(ProgressPayload::write, ProgressPayload::read);

	/**
	 * One skill: its level, the XP within the level and what the next level needs, its Mastery, and its tree: the chosen
	 * specializations, the points bought (and set aside), how long until the next respec (real milliseconds) and how
	 * long each ability still cools down (ticks).
	 */
	/**
	 * The player's deaths: how many, the death pressure and the active play until it drops (ticks), death fatigue left
	 * (ticks), and what the next death here would take: the share of every skill's step (base × the dimension's factor)
	 * and whether a whole level or Mastery rank goes too.
	 */
	public record Death(int deaths, int pressure, long pressureDropsIn, long fatigueLeft, double share, double multiplier, boolean whole) {
		public static final Death NONE = new Death(0, 0, 0, 0, 0.5, 1, false);
	}

	public record Entry(String id, int level, double xp, double need, int masteryRanks, double masteryXp, List<String> specs,
						List<String> bought, List<String> suspended, long respecWait, Map<String, Integer> cooldowns) {}

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(total);
		buf.writeVarInt(cap);
		buf.writeDouble(budgetFactor);
		buf.writeVarInt(maxLevel);
		buf.writeVarInt(masteryRanksMax);
		buf.writeDouble(masteryXpPerRank);
		buf.writeVarInt(skills.size());
		for (Entry e : skills) {
			buf.writeUtf(e.id());
			buf.writeVarInt(e.level());
			buf.writeDouble(e.xp());
			buf.writeDouble(e.need());
			buf.writeVarInt(e.masteryRanks());
			buf.writeDouble(e.masteryXp());
			strings(buf, e.specs());
			strings(buf, e.bought());
			strings(buf, e.suspended());
			buf.writeLong(e.respecWait());
			buf.writeVarInt(e.cooldowns().size());
			for (Map.Entry<String, Integer> c : e.cooldowns().entrySet()) {
				buf.writeUtf(c.getKey());
				buf.writeVarInt(c.getValue());
			}
		}
		buf.writeVarInt(death.deaths());
		buf.writeVarInt(death.pressure());
		buf.writeVarLong(death.pressureDropsIn());
		buf.writeVarLong(death.fatigueLeft());
		buf.writeDouble(death.share());
		buf.writeDouble(death.multiplier());
		buf.writeBoolean(death.whole());
	}

	private static ProgressPayload read(FriendlyByteBuf buf) {
		int total = buf.readVarInt();
		int cap = buf.readVarInt();
		double factor = buf.readDouble();
		int maxLevel = buf.readVarInt();
		int ranksMax = buf.readVarInt();
		double perRank = buf.readDouble();
		int n = buf.readVarInt();
		List<Entry> skills = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			String id = buf.readUtf();
			int level = buf.readVarInt();
			double xp = buf.readDouble();
			double need = buf.readDouble();
			int ranks = buf.readVarInt();
			double masteryXp = buf.readDouble();
			List<String> specs = strings(buf);
			List<String> bought = strings(buf);
			List<String> suspended = strings(buf);
			long respecWait = buf.readLong();
			int c = buf.readVarInt();
			Map<String, Integer> cooldowns = new LinkedHashMap<>();
			for (int j = 0; j < c; j++) {
				cooldowns.put(buf.readUtf(), buf.readVarInt());
			}
			skills.add(new Entry(id, level, xp, need, ranks, masteryXp, specs, bought, suspended, respecWait, cooldowns));
		}
		Death death = new Death(buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readDouble(), buf.readDouble(), buf.readBoolean());
		return new ProgressPayload(total, cap, factor, maxLevel, ranksMax, perRank, skills, death);
	}

	private static void strings(FriendlyByteBuf buf, List<String> list) {
		buf.writeVarInt(list.size());
		for (String s : list) {
			buf.writeUtf(s);
		}
	}

	private static List<String> strings(FriendlyByteBuf buf) {
		int n = buf.readVarInt();
		List<String> list = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			list.add(buf.readUtf());
		}
		return list;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
