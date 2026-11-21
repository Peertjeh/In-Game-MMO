package com.extremeprogression.legends;

import java.util.List;

import com.extremeprogression.antiexploit.MobOrigins;
import com.extremeprogression.config.Config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Where relics come from (2.2): treasure chests, mobs slain by a player (never from a spawner, egg or command), raids
 * won and bosses. RELICS turns them all off (and the Oaths with them).
 */
public final class Relics {
	private Relics() {}

	/** A chance of a relic in a chest's loot: every entry whose table the chest's is, or starts with, is rolled. */
	public record Find(String table, Relic relic, double chance) {}

	public static final List<Find> CHESTS = List.of(
			new Find("chests/pillager_outpost", Relic.WARBANNER_SHARD, 0.35),
			new Find("chests/woodland_mansion", Relic.WARBANNER_SHARD, 0.40),
			new Find("chests/woodland_mansion", Relic.VERDANT_IDOL, 0.30),
			new Find("chests/ancient_city", Relic.SCULK_HEART, 0.25),
			new Find("chests/shipwreck_treasure", Relic.TIDAL_PEARL, 0.20),
			new Find("chests/buried_treasure", Relic.TIDAL_PEARL, 0.35),
			new Find("chests/underwater_ruin_big", Relic.TIDAL_PEARL, 0.15),
			new Find("chests/bastion_treasure", Relic.EMBER_CORE, 0.40),
			new Find("chests/bastion_other", Relic.EMBER_CORE, 0.12),
			new Find("chests/bastion_bridge", Relic.EMBER_CORE, 0.12),
			new Find("chests/bastion_hoglin_stable", Relic.EMBER_CORE, 0.12),
			new Find("chests/nether_bridge", Relic.EMBER_CORE, 0.15),
			new Find("chests/end_city_treasure", Relic.VOID_SHARD, 0.30),
			new Find("chests/jungle_temple", Relic.VERDANT_IDOL, 0.30),
			new Find("chests/desert_pyramid", Relic.VERDANT_IDOL, 0.20),
			new Find("chests/trial_chambers/reward_ominous", Relic.CHAMPIONS_CREST, 0.25),
			new Find("chests/trial_chambers/reward", Relic.CHAMPIONS_CREST, 0.10));

	/** A mob's chance of a relic when a player slays it, and how many. */
	public record Drop(String mob, Relic relic, double chance, int count) {}

	public static final List<Drop> MOBS = List.of(
			new Drop("minecraft:warden", Relic.SCULK_HEART, 1.0, 1),
			new Drop("minecraft:elder_guardian", Relic.TIDAL_PEARL, 1.0, 2),
			new Drop("minecraft:drowned", Relic.TIDAL_PEARL, 0.01, 1),
			new Drop("minecraft:blaze", Relic.EMBER_CORE, 0.02, 1),
			new Drop("minecraft:piglin_brute", Relic.EMBER_CORE, 0.15, 1),
			new Drop("minecraft:shulker", Relic.VOID_SHARD, 0.04, 1),
			new Drop("minecraft:ender_dragon", Relic.VOID_SHARD, 1.0, 3));

	/** The share of Champions that drop a Champion's Crest. */
	public static final double CREST_CHANCE = 0.20;

	static boolean on() {
		return Config.RELICS;
	}

	/** A loot table rolled: a chest's may hold relics. */
	public static void loot(String table, RandomSource random, List<ItemStack> drops) {
		if (!on() || table == null || !table.startsWith("minecraft:chests/")) {
			return;
		}
		String path = table.substring("minecraft:".length());
		// (each relic once: the first entry that fits the table, so an ominous trial reward is not rolled twice)
		java.util.Set<Relic> rolled = java.util.EnumSet.noneOf(Relic.class);
		for (Find f : CHESTS) {
			boolean match = path.equals(f.table()) || path.startsWith(f.table() + "_") || path.startsWith(f.table() + "/");
			if (match && rolled.add(f.relic()) && random.nextDouble() < f.chance()) {
				drops.add(f.relic().stack(1));
			}
		}
	}

	/** A mob died: a player who slew it may get its relic; a raid captain always gives a banner shard, a Champion maybe a crest. */
	public static void died(LivingEntity mob, DamageSource source) {
		if (!on() || !(mob.level() instanceof ServerLevel level) || !(source.getEntity() instanceof ServerPlayer) || MobOrigins.flagged(mob)) {
			return;
		}
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
		RandomSource r = mob.getRandom();
		for (Drop d : MOBS) {
			if (d.mob().equals(id) && r.nextDouble() < d.chance()) {
				mob.spawnAtLocation(level, d.relic().stack(d.count()));
			}
		}
		if (captain(mob)) {
			mob.spawnAtLocation(level, Relic.WARBANNER_SHARD.stack(1));
		}
		if (Champions.is(mob) && r.nextDouble() < CREST_CHANCE) {
			mob.spawnAtLocation(level, Relic.CHAMPIONS_CREST.stack(1));
		}
	}

	/** A raid captain: an illager carrying a banner on its head. */
	static boolean captain(LivingEntity mob) {
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
		if (!id.equals("minecraft:pillager") && !id.equals("minecraft:vindicator") && !id.equals("minecraft:evoker")) {
			return false;
		}
		ItemStack head = mob.getItemBySlot(EquipmentSlot.HEAD);
		return !head.isEmpty() && BuiltInRegistries.ITEM.getKey(head.getItem()).getPath().endsWith("_banner");
	}

	/** A raid won (Hero of the Village): banner shards, one more for every omen level; it counts for the Oath of the Raid Breaker. */
	public static void raidWon(ServerPlayer p, int omenLevel) {
		LegendsData d = com.extremeprogression.data.PlayerStore.get(p.getUUID()).legends;
		d.add("raids", 1);
		com.extremeprogression.data.PlayerStore.markDirty(p.getUUID());
		if (on()) {
			int n = 1 + Math.max(0, omenLevel);
			Boosts.giveStack(p, Relic.WARBANNER_SHARD.stack(n));
			p.sendSystemMessage(Component.translatable("message.extremeprogression.relic.raid", n,
					Component.translatable(Relic.WARBANNER_SHARD.translationKey()).withColor(Relic.WARBANNER_SHARD.color)).withColor(0xFFAA00), false);
		}
		Legends.dirty(p);
	}
}
