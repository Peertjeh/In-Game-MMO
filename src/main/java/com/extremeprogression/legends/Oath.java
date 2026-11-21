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
 * The Oaths (2.2, the owner's wish: quests with the relics that unlock permanent buffs). Each asks for relics, handed
 * in, and a feat; sworn, it gives a buff for good. The last one asks for every relic and the seven others.
 */
public enum Oath {
	/** Five banner shards and a raid won: two more hearts. */
	RAID_BREAKER(relics(Relic.WARBANNER_SHARD, 5), p -> legends(p).stat("raids") >= 1, Buff.HEALTH),
	/** Three sculk hearts and Exploration 30: the abilities' cooldowns 10% shorter. */
	DEEP_DARK(relics(Relic.SCULK_HEART, 3), p -> level(p, Skill.EXPLORATION) >= 30, Buff.COOLDOWN),
	/** Four tidal pearls and Fishing 30: breath for longer underwater, and quicker swimming. */
	TIDES(relics(Relic.TIDAL_PEARL, 4), p -> level(p, Skill.FISHING) >= 30, Buff.WATER),
	/** Five ember cores and Defense 30: fire burns half as long. */
	EMBERS(relics(Relic.EMBER_CORE, 5), p -> level(p, Skill.DEFENSE) >= 30, Buff.FIRE),
	/** Four void shards and any skill at 50: abilities last 15% longer. */
	VOID(relics(Relic.VOID_SHARD, 4), p -> highest(p) >= 50, Buff.DURATION),
	/** Four verdant idols and Foraging or Farming at 30: 5% quicker on foot. */
	WILDS(relics(Relic.VERDANT_IDOL, 4), p -> Math.max(level(p, Skill.FORAGING), level(p, Skill.FARMING)) >= 30, Buff.SPEED),
	/** Five champion's crests and ten Champions slain: two more armor and one more toughness. */
	CHAMPIONS(relics(Relic.CHAMPIONS_CREST, 5), p -> legends(p).stat("champions") >= 10, Buff.ARMOR),
	/** One of every relic and the seven other Oaths sworn: +10% XP in every skill, and the title Oathkeeper. */
	LEGENDS(everyRelic(), p -> sworn(p) >= Oath.values().length - 1, Buff.XP);

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

	private static Map<Relic, Integer> everyRelic() {
		Map<Relic, Integer> m = new EnumMap<>(Relic.class);
		for (Relic r : Relic.values()) {
			m.put(r, 1);
		}
		return m;
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
