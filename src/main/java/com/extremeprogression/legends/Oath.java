package com.extremeprogression.legends;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.skill.Skill;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The Oaths (2.2, the owner's wish: quests with the relics that unlock permanent buffs; 2.2.1: much harder). Each asks
 * for many relics, handed in, and a hard feat; it is sworn at a beacon, and only one a week. Sworn, it gives a buff for
 * good. The last one asks for five of every relic, the seven others, the total level cap and 30 Mastery ranks.
 */
public enum Oath {
	/** 20 banner shards, 10 raids won and Defense 50: two more hearts. */
	RAID_BREAKER(relics(Relic.WARBANNER_SHARD, 20), p -> legends(p).stat("raids") >= 10 && level(p, Skill.DEFENSE) >= 50, Buff.HEALTH),
	/** 12 sculk hearts, Exploration 75 and three Wardens: the abilities' cooldowns 10% shorter. */
	DEEP_DARK(relics(Relic.SCULK_HEART, 12), p -> level(p, Skill.EXPLORATION) >= 75 && legends(p).stat("boss.warden") >= 3, Buff.COOLDOWN),
	/** 15 tidal pearls, Fishing 75 and three Elder Guardians: breath for three times as long underwater. */
	TIDES(relics(Relic.TIDAL_PEARL, 15), p -> level(p, Skill.FISHING) >= 75 && legends(p).stat("boss.elder_guardian") >= 3, Buff.WATER),
	/** 20 ember cores, Defense 75 and the Wither: fire burns half as long. */
	EMBERS(relics(Relic.EMBER_CORE, 20), p -> level(p, Skill.DEFENSE) >= 75 && legends(p).stat("boss.wither") >= 1, Buff.FIRE),
	/** 12 void shards, a skill at 100 and the Ender Dragon: abilities last 15% longer. */
	VOID(relics(Relic.VOID_SHARD, 12), p -> highest(p) >= 100 && legends(p).stat("boss.ender_dragon") >= 1, Buff.DURATION),
	/** 20 verdant idols, Foraging and Farming both at 75: 5% quicker on foot. */
	WILDS(relics(Relic.VERDANT_IDOL, 20), p -> level(p, Skill.FORAGING) >= 75 && level(p, Skill.FARMING) >= 75, Buff.SPEED),
	/** 15 champion's crests and 100 Champions slain: two more armor. */
	CHAMPIONS(relics(Relic.CHAMPIONS_CREST, 15), p -> legends(p).stat("champions") >= 100, Buff.ARMOR),
	/**
	 * Five of every relic, the seven other Oaths, the total level at its cap and 30 Mastery ranks: +10% XP in every
	 * skill, and the title Oathkeeper.
	 */
	LEGENDS(everyRelic(5), p -> sworn(p) >= Oath.values().length - 1 && PlayerStore.get(p.getUUID()).totalLevel() >= Config.TOTAL_LEVEL_CAP
			&& PlayerStore.get(p.getUUID()).totalMasteryRanks() >= 30, Buff.XP);

	/** What an Oath gives for good. */
	public enum Buff {
		HEALTH(Attributes.MAX_HEALTH, 4, AttributeModifier.Operation.ADD_VALUE),
		COOLDOWN(null, 0.10, null),
		WATER(Attributes.OXYGEN_BONUS, 2, AttributeModifier.Operation.ADD_VALUE),
		FIRE(Attributes.BURNING_TIME, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
		DURATION(null, 0.15, null),
		SPEED(Attributes.MOVEMENT_SPEED, 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
		ARMOR(Attributes.ARMOR, 2, AttributeModifier.Operation.ADD_VALUE),
		XP(null, 0.10, null);

		/** The attribute it raises (null for the buffs the mod works out itself), by how much and how. */
		public final Holder<Attribute> attribute;
		public final double amount;
		public final AttributeModifier.Operation op;

		Buff(Holder<Attribute> attribute, double amount, AttributeModifier.Operation op) {
			this.attribute = attribute;
			this.amount = amount;
			this.op = op;
		}
	}

	public final Map<Relic, Integer> relics;
	public final Predicate<ServerPlayer> feat;
	public final Buff buff;
	public final String id;

	Oath(Map<Relic, Integer> relics, Predicate<ServerPlayer> feat, Buff buff) {
		this.relics = relics;
		this.feat = feat;
		this.buff = buff;
		this.id = name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "oath.extremeprogression." + id;
	}

	public static Oath byId(String id) {
		for (Oath o : values()) {
			if (o.id.equals(id)) {
				return o;
			}
		}
		return null;
	}

	private static Map<Relic, Integer> relics(Relic r, int n) {
		Map<Relic, Integer> m = new EnumMap<>(Relic.class);
		m.put(r, n);
		return m;
	}

	private static Map<Relic, Integer> everyRelic(int n) {
		Map<Relic, Integer> m = new EnumMap<>(Relic.class);
		for (Relic r : Relic.values()) {
			m.put(r, n);
		}
		return m;
	}

	/** How many of a relic the Oath asks for, with OATH_RELIC_FACTOR (at least one when it asks for any). */
	public int needed(Relic r) {
		Integer n = relics.get(r);
		return n == null ? 0 : Math.max(1, (int) Math.round(n * Config.OATH_RELIC_FACTOR));
	}

	static LegendsData legends(ServerPlayer p) {
		return PlayerStore.get(p.getUUID()).legends;
	}

	static int level(ServerPlayer p, Skill s) {
		return PlayerStore.get(p.getUUID()).get(s).level;
	}

	static int highest(ServerPlayer p) {
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		int best = 0;
		for (Skill s : Skill.values()) {
			best = Math.max(best, progress.get(s).level);
		}
		return best;
	}

	/** How many Oaths the player swore (other than the Oath of Legends). */
	static int sworn(ServerPlayer p) {
		int n = 0;
		for (String id : legends(p).oaths) {
			n += "legends".equals(id) ? 0 : 1;
		}
		return n;
	}

	/** Whether Oaths can be sworn on this server. */
	public static boolean on() {
		return Config.RELICS;
	}
}
