package com.extremeprogression.legends;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.antiexploit.AfkTracker;
import com.extremeprogression.antiexploit.MobOrigins;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.skill.Skill;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * XP boosts (2.0, the owner's wish: +25, +50, +100% and more to get and earn). A player's boost is the strongest tome
 * they read plus Well Rested, at most BOOST_MAX, added to the party bonus in {@code XpService.award}. Tome and rested
 * time runs only in seconds the player earns XP, so none is lost while building, trading or away.
 *
 * <p>Where tomes come from: the daily streak (the first login of each day), skill milestones (every 25 levels, and the
 * Mastery ranks), bosses (once a day each), treasure chests, Champions and contracts; admins give them with
 * {@code /skills boost}.
 */
public final class Boosts {
	private Boosts() {}

	/** When each player last earned XP (server tick): their boost time runs within a minute of it. */
	private static final Map<UUID, Long> LAST_EARN = new HashMap<>();

	static LegendsData data(ServerPlayer p) {
		return PlayerStore.get(p.getUUID()).legends;
	}

	/** The tome boost that runs now: the strongest (the first read among equals). */
	public static LegendsData.Boost running(LegendsData d) {
		return d.boosts.stream().max(Comparator.comparingInt((LegendsData.Boost b) -> b.percent)).orElse(null);
	}

	/** The player's boost now: the running tome and Well Rested, at most BOOST_MAX (0.5 = +50%). */
	public static double bonus(ServerPlayer p) {
		if (!Config.BOOSTS) {
			return 0;
		}
		LegendsData d = data(p);
		LegendsData.Boost top = running(d);
		double b = (top == null ? 0 : top.percent / 100.0) + (d.restedSeconds > 0 ? Config.RESTED_BONUS : 0);
		return Math.min(Config.BOOST_MAX, b);
	}

	/** The player earned XP just now (from {@code XpService.award}). */
	public static void earned(ServerPlayer p) {
		LAST_EARN.put(p.getUUID(), (long) p.level().getServer().getTickCount());
	}

	public static boolean earning(ServerPlayer p, long tick) {
		Long last = LAST_EARN.get(p.getUUID());
		return last != null && tick - last <= 1200 && !AfkTracker.isAfk(p.getUUID(), tick);
	}

	public static void forget(UUID id) {
		LAST_EARN.remove(id);
	}

	/** Every second: the running tome and Well Rested lose a second for every player earning XP. */
	public static void tick(MinecraftServer server) {
		long tick = server.getTickCount();
		if (tick % 20 != 0) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			LegendsData d = data(p);
			if (tick % 1200 == 0) {
				// (seen now: a crash forgets at most a minute of the time offline)
				d.lastSeen = System.currentTimeMillis();
				PlayerStore.markDirty(p.getUUID());
			}
			if (!Config.BOOSTS || d.boosts.isEmpty() && d.restedSeconds <= 0 || !earning(p, tick)) {
				continue;
			}
			spend(p, d, 1);
		}
	}

	/** Spends this many seconds of the running tome and of Well Rested (for the tick, and for tests). */
	public static void spend(ServerPlayer p, LegendsData d, int seconds) {
		LegendsData.Boost top = running(d);
		if (top != null) {
			top.seconds -= seconds;
			if (top.seconds <= 0) {
				d.boosts.remove(top);
				LegendsData.Boost next = running(d);
				p.sendSystemMessage(next == null ? Component.translatable("message.extremeprogression.boost.ended", top.percent).withColor(0xAAAAAA)
						: Component.translatable("message.extremeprogression.boost.next", top.percent, next.percent, minutes(next.seconds)).withColor(0x55FF55), false);
			}
		}
		if (d.restedSeconds > 0) {
			d.restedSeconds = Math.max(0, d.restedSeconds - seconds);
			if (d.restedSeconds == 0) {
				p.sendSystemMessage(Component.translatable("message.extremeprogression.boost.rested_ended").withColor(0xAAAAAA), false);
			}
		}
		PlayerStore.markDirty(p.getUUID());
		Legends.dirty(p);
	}

	static int minutes(int seconds) {
		return Math.max(1, (int) Math.ceil(seconds / 60.0));
	}

	// --- Joining and leaving: Well Rested and the daily streak -----------------------------------

	/** A player joined: Well Rested for the time offline, and today's streak reward. */
	public static void joined(ServerPlayer p, long now, LocalDate today) {
		LegendsData d = data(p);
		if (d.lastSeen > 0 && now > d.lastSeen && Config.BOOSTS) {
			double hours = (now - d.lastSeen) / 3_600_000.0;
			int gained = (int) Math.floor(hours * Config.RESTED_MINUTES_PER_HOUR * 60);
			int max = (int) Math.round(Config.RESTED_MAX_MINUTES * 60);
			if (gained >= 60 && d.restedSeconds < max) {
				d.restedSeconds = Math.min(max, d.restedSeconds + gained);
				p.sendSystemMessage(Component.translatable("message.extremeprogression.boost.rested", Math.round(Config.RESTED_BONUS * 100),
						minutes(d.restedSeconds)).withColor(0x55FFFF), false);
			}
		}
		d.lastSeen = now;
		daily(p, today);
		PlayerStore.markDirty(p.getUUID());
	}

	public static void left(ServerPlayer p) {
		data(p).lastSeen = System.currentTimeMillis();
		PlayerStore.markDirty(p.getUUID());
		forget(p.getUUID());
	}

	/** The first login of a day: the streak goes on (or starts again), with a tome; a Greater every 7th day, a Grand every 30th. */
	public static boolean daily(ServerPlayer p, LocalDate today) {
		LegendsData d = data(p);
		String day = today.toString();
		if (day.equals(d.streakDay)) {
			return false;
		}
		d.streak = today.minusDays(1).toString().equals(d.streakDay) ? d.streak + 1 : 1;
		d.streakDay = day;
		d.bestStreak = Math.max(d.bestStreak, d.streak);
		PlayerStore.markDirty(p.getUUID());
		if (Config.DAILY_REWARDS && Config.BOOSTS) {
			Tome t = d.streak % 30 == 0 ? Tome.GRAND : d.streak % 7 == 0 ? Tome.GREATER : Tome.INSIGHT;
			give(p, t, 1);
			p.sendSystemMessage(Component.translatable("message.extremeprogression.boost.daily", d.streak, Component.translatable(t.translationKey()).withColor(t.color))
					.withColor(0xFFAA00), false);
		}
		Titles.check(p);
		return true;
	}

	// --- Reading and giving tomes ----------------------------------------------------------------

	/** A tome read with a right click: its boost joins the player's, and the tome is used up. */
	public static void read(ServerPlayer p, ItemStack stack, Tome t) {
		if (!Config.BOOSTS) {
			p.sendSystemMessage(Component.translatable("message.extremeprogression.boost.off").withColor(0xFF5555), true);
			return;
		}
		LegendsData d = data(p);
		LegendsData.Boost before = running(d);
		add(p, t.percent, t.minutes * 60, t.id);
		stack.consume(1, p);
		boolean waits = before != null && before.percent >= t.percent;
		p.sendSystemMessage(Component.translatable(waits ? "message.extremeprogression.boost.read_waits" : "message.extremeprogression.boost.read",
				Component.translatable(t.translationKey()).withColor(t.color), t.percent, t.minutes).withColor(0x55FF55), false);
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
	}

	/** Adds a boost of this many percent for this many seconds (a tome, or an admin's). */
	public static void add(ServerPlayer p, int percent, int seconds, String source) {
		LegendsData d = data(p);
		d.boosts.add(new LegendsData.Boost(percent, seconds, source));
		PlayerStore.markDirty(p.getUUID());
		Legends.dirty(p);
	}

	/** Gives the player tomes: into the inventory, or at their feet when it is full. */
	public static void give(ServerPlayer p, Tome t, int count) {
		ItemStack s = t.stack(count);
		if (!p.addItem(s) && !s.isEmpty()) {
			p.spawnAtLocation(p.level(), s);
		}
	}

	// --- Milestones and bosses -------------------------------------------------------------------

	/** A skill reached a new level, or a new Mastery rank: every 25th level gives a tome (a Grand one at 100), every 5th rank a Greater. */
	public static void leveled(ServerPlayer p, Skill skill, int fromLevel, int toLevel, int fromRanks, int toRanks) {
		if (!Config.MILESTONE_TOMES || !Config.BOOSTS) {
			return;
		}
		LegendsData d = data(p);
		for (int l = fromLevel + 1; l <= toLevel; l++) {
			if (l % 25 == 0 && d.milestones.add(skill.id + ":" + l)) {
				Tome t = l >= 100 ? Tome.GRAND : Tome.INSIGHT;
				give(p, t, 1);
				p.sendSystemMessage(Component.translatable("message.extremeprogression.boost.milestone", Component.translatable(skill.translationKey()), l,
						Component.translatable(t.translationKey()).withColor(t.color)).withColor(0xFFAA00), false);
			}
		}
		for (int r = fromRanks + 1; r <= toRanks; r++) {
			if (r % 5 == 0 && d.milestones.add(skill.id + ":m" + r)) {
				give(p, Tome.GREATER, 1);
				p.sendSystemMessage(Component.translatable("message.extremeprogression.boost.milestone_rank", Component.translatable(skill.translationKey()), r,
						Component.translatable(Tome.GREATER.translationKey()).withColor(Tome.GREATER.color)).withColor(0xFFAA00), false);
			}
		}
		PlayerStore.markDirty(p.getUUID());
	}

	/** The bosses and the tome each gives every player near when it dies (once a day each). */
	public static final Map<String, Tome> BOSSES = Map.of("minecraft:ender_dragon", Tome.LEGENDARY, "minecraft:wither", Tome.GRAND,
			"minecraft:warden", Tome.GRAND, "minecraft:elder_guardian", Tome.GREATER);

	/** A boss died: every player within 96 blocks who is not away gets its tome, once a day for each boss, and it counts for titles. */
	public static void bossDied(LivingEntity boss, LocalDate today) {
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).toString();
		Tome t = BOSSES.get(id);
		if (t == null || MobOrigins.flagged(boss) || !(boss.level() instanceof ServerLevel level)) {
			return;
		}
		long tick = level.getServer().getTickCount();
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || p.distanceToSqr(boss) > 96 * 96 || AfkTracker.isAfk(p.getUUID(), tick)) {
				continue;
			}
			LegendsData d = data(p);
			d.add("boss." + id.substring(id.indexOf(':') + 1), 1);
			PlayerStore.markDirty(p.getUUID());
			if (Config.BOSS_TOMES && Config.BOOSTS && !today.toString().equals(d.bossDays.get(id))) {
				d.bossDays.put(id, today.toString());
				give(p, t, 1);
				p.sendSystemMessage(Component.translatable("message.extremeprogression.boost.boss", boss.getDisplayName(),
						Component.translatable(t.translationKey()).withColor(t.color)).withColor(0xFFAA00), false);
			}
			Titles.check(p);
		}
	}

	// --- Treasure --------------------------------------------------------------------------------

	/** A chest's loot table and the chance of a tome in it, and which. */
	public record Treasure(String table, double chance, Tome tome) {}

	public static final List<Treasure> TREASURE = List.of(
			new Treasure("chests/simple_dungeon", 0.12, Tome.INSIGHT),
			new Treasure("chests/abandoned_mineshaft", 0.08, Tome.INSIGHT),
			new Treasure("chests/desert_pyramid", 0.12, Tome.INSIGHT),
			new Treasure("chests/jungle_temple", 0.15, Tome.INSIGHT),
			new Treasure("chests/pillager_outpost", 0.12, Tome.INSIGHT),
			new Treasure("chests/shipwreck_treasure", 0.15, Tome.INSIGHT),
			new Treasure("chests/buried_treasure", 0.30, Tome.GREATER),
			new Treasure("chests/stronghold_library", 0.30, Tome.GREATER),
			new Treasure("chests/woodland_mansion", 0.30, Tome.GREATER),
			new Treasure("chests/ancient_city", 0.15, Tome.GREATER),
			new Treasure("chests/bastion_treasure", 0.30, Tome.GREATER),
			new Treasure("chests/end_city_treasure", 0.15, Tome.GRAND),
			new Treasure("chests/trial_chambers/reward_ominous", 0.10, Tome.GRAND),
			new Treasure("chests/trial_chambers/reward", 0.08, Tome.GREATER));

	/** The tome a chest's loot may hold (null for other tables): the first entry whose table this one is or starts with. */
	public static Treasure treasure(String path) {
		for (Treasure t : TREASURE) {
			if (path.equals(t.table()) || path.startsWith(t.table() + "_")) {
				return t;
			}
		}
		return null;
	}

	/** A loot table rolled: a treasure chest's may hold a tome. */
	public static void loot(String table, RandomSource random, List<ItemStack> drops) {
		if (!Config.LOOT_TOMES || !Config.BOOSTS || table == null || !table.startsWith("minecraft:chests/")) {
			return;
		}
		Treasure t = treasure(table.substring("minecraft:".length()));
		if (t != null && random.nextDouble() < t.chance()) {
			drops.add(t.tome().stack(1));
			ExtremeProgression.LOGGER.debug("A {} in {}", t.tome().id, table);
		}
	}
}
