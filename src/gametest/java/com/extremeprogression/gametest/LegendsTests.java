package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.SkeletonTests.check;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import com.extremeprogression.compat.PowerTags;
import com.extremeprogression.compat.TabPlaceholders;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.legends.Boosts;
import com.extremeprogression.legends.Champions;
import com.extremeprogression.legends.Contracts;
import com.extremeprogression.legends.Legends;
import com.extremeprogression.legends.LegendsData;
import com.extremeprogression.legends.SkillOfTheDay;
import com.extremeprogression.legends.Titles;
import com.extremeprogression.legends.Tome;
import com.extremeprogression.network.PowerPayload;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.xp.XpService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * The Legends update (2.0): XP boosts (tomes, Well Rested, the streak, milestones, bosses, treasure), Champions,
 * contracts, titles and the Skill of the Day.
 */
public class LegendsTests {
	private static LegendsData legends(ServerPlayer p) {
		return PlayerStore.get(p.getUUID()).legends;
	}

	/** How many tomes of this kind the player carries. */
	private static int tomes(ServerPlayer p, Tome t) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (Tome.of(s) == t) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static boolean close(double a, double b) {
		return Math.abs(a - b) < 1e-6;
	}

	@GameTest
	public void tomesBoostXpAndTheStrongestRunsFirst(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Reader");
		ItemStack greater = Tome.GREATER.stack(2);
		check(helper, Tome.of(greater) == Tome.GREATER && Tome.of(new ItemStack(net.minecraft.world.item.Items.KNOWLEDGE_BOOK)) == null,
				"a tome is not told from a plain knowledge book");
		check(helper, close(Boosts.bonus(p), 0), "a player who read nothing has a boost of %s", Boosts.bonus(p));
		Boosts.read(p, greater, Tome.GREATER);
		check(helper, greater.getCount() == 1 && close(Boosts.bonus(p), 0.5), "a Greater Tome gave %s and left %s", Boosts.bonus(p), greater.getCount());
		// A weaker tome waits behind it; when the Greater runs out, it runs
		Boosts.read(p, Tome.INSIGHT.stack(1), Tome.INSIGHT);
		check(helper, close(Boosts.bonus(p), 0.5) && legends(p).boosts.size() == 2, "a weaker tome did not wait: %s", Boosts.bonus(p));
		Boosts.spend(p, legends(p), 60);
		check(helper, Boosts.running(legends(p)).percent == 50 && Boosts.running(legends(p)).seconds == 30 * 60 - 60, "a minute was not spent from the strongest");
		Boosts.spend(p, legends(p), 30 * 60);
		check(helper, close(Boosts.bonus(p), 0.25), "after the Greater ran out the boost is %s, expected the waiting 25%%", Boosts.bonus(p));
		// Well Rested adds to it; together never above the maximum
		legends(p).restedSeconds = 600;
		Boosts.add(p, 200, 600, "admin");
		check(helper, close(Boosts.bonus(p), Config.BOOST_MAX), "200%% and Well Rested made %s, expected the maximum %s", Boosts.bonus(p), Config.BOOST_MAX);
		// Off: no boost at all
		Config.BOOSTS = false;
		check(helper, close(Boosts.bonus(p), 0), "boosts turned off still gave %s", Boosts.bonus(p));
		Config.reset();
		helper.succeed();
	}

	@GameTest
	public void aBoostAddsToTheXpEarned(GameTestHelper helper) {
		Config.reset();
		ServerPlayer plain = TestPlayers.survivor(helper, "Plain");
		ServerPlayer boosted = TestPlayers.survivor(helper, "Boosted");
		Boosts.add(boosted, 100, 600, "admin");
		double a = XpService.award(plain, Skill.FISHING, 40);
		double b = XpService.award(boosted, Skill.FISHING, 40);
		check(helper, a > 0 && close(b, a * 2), "with +100%% the XP was %s against %s", b, a);
		// A reward (a contract's, a Champion's) is never boosted
		double g = XpService.grant(boosted, Skill.MINING, 100);
		check(helper, close(g, 100), "a reward of 100 XP gave %s", g);
		helper.succeed();
	}

	@GameTest
	public void wellRestedGrowsOfflineAndTheStreakGivesTomes(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Sleeper");
		long now = System.currentTimeMillis();
		LocalDate day = LocalDate.of(2026, 3, 1);
		legends(p).lastSeen = now - 5 * 3_600_000L;
		Boosts.joined(p, now, day);
		check(helper, legends(p).restedSeconds == 30 * 60, "5 hours offline gave %s s of Well Rested, expected 30 minutes", legends(p).restedSeconds);
		legends(p).lastSeen = now - 50 * 3_600_000L;
		Boosts.joined(p, now, day);
		check(helper, legends(p).restedSeconds == 60 * 60, "Well Rested went to %s s, past the hour", legends(p).restedSeconds);
		check(helper, close(Boosts.bonus(p), Config.RESTED_BONUS), "Well Rested gave %s", Boosts.bonus(p));
		// The streak: day 1 had its tome at the join above; the same day gives none again
		check(helper, legends(p).streak == 1 && tomes(p, Tome.INSIGHT) == 1, "the first day gave a streak of %s and %s tomes", legends(p).streak, tomes(p, Tome.INSIGHT));
		check(helper, !Boosts.daily(p, day) && tomes(p, Tome.INSIGHT) == 1, "the same day gave a second reward");
		for (int i = 1; i < 7; i++) {
			Boosts.daily(p, day.plusDays(i));
		}
		check(helper, legends(p).streak == 7 && tomes(p, Tome.GREATER) == 1 && tomes(p, Tome.INSIGHT) == 6,
				"7 days in a row gave a streak of %s, %s Greater and %s plain tomes", legends(p).streak, tomes(p, Tome.GREATER), tomes(p, Tome.INSIGHT));
		check(helper, legends(p).titles.contains("devoted"), "a 7-day streak did not earn the title Devoted");
		// A day missed: the streak starts again
		Boosts.daily(p, day.plusDays(9));
		check(helper, legends(p).streak == 1 && legends(p).bestStreak == 7, "after a missed day the streak is %s (best %s)", legends(p).streak, legends(p).bestStreak);
		helper.succeed();
	}

	@GameTest
	public void milestonesBossesAndTreasureGiveTomes(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Hero");
		Boosts.leveled(p, Skill.MINING, 24, 26, 0, 0);
		Boosts.leveled(p, Skill.MINING, 24, 26, 0, 0);
		check(helper, tomes(p, Tome.INSIGHT) == 1, "level 25 gave %s tomes (twice reached), expected 1", tomes(p, Tome.INSIGHT));
		Boosts.leveled(p, Skill.MINING, 99, 100, 0, 5);
		check(helper, tomes(p, Tome.GRAND) == 1 && tomes(p, Tome.GREATER) == 1, "level 100 and Mastery rank 5 gave %s Grand and %s Greater",
				tomes(p, Tome.GRAND), tomes(p, Tome.GREATER));
		// A boss: every player near, once a day
		ServerLevel level = helper.getLevel();
		Mob guardian = (Mob) helper.spawn(EntityTypes.ELDER_GUARDIAN, new BlockPos(1, 2, 1), EntitySpawnReason.NATURAL);
		p.setPos(guardian.getX() + 3, guardian.getY(), guardian.getZ());
		LocalDate today = LocalDate.of(2026, 4, 2);
		Boosts.bossDied(guardian, today);
		Boosts.bossDied(guardian, today);
		check(helper, tomes(p, Tome.GREATER) == 2 && legends(p).stat("boss.elder_guardian") == 2, "an Elder Guardian gave %s Greater tomes",
				tomes(p, Tome.GREATER) - 1);
		check(helper, legends(p).titles.contains("tidebreaker"), "being there when an Elder Guardian died did not earn Tidebreaker");
		guardian.discard();
		// Treasure: the tables, and about the chance
		check(helper, Boosts.treasure("chests/ancient_city").tome() == Tome.GREATER && Boosts.treasure("chests/end_city_treasure").tome() == Tome.GRAND
				&& Boosts.treasure("chests/village/village_weaponsmith") == null, "the treasure tables are wrong");
		var random = net.minecraft.util.RandomSource.create(42L);
		int found = 0;
		for (int i = 0; i < 2000; i++) {
			java.util.List<ItemStack> drops = new java.util.ArrayList<>();
			Boosts.loot("minecraft:chests/buried_treasure", random, drops);
			found += drops.size();
			Boosts.loot("minecraft:entities/zombie", random, drops);
		}
		check(helper, found > 480 && found < 720, "buried treasure held a tome %s times in 2000, expected about 600", found);
		helper.succeed();
	}

	@GameTest
	public void championsAreStrongerAndPayTheirSlayer(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "Slayer");
		Mob zombie = (Mob) helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1), EntitySpawnReason.NATURAL);
		check(helper, !Champions.is(zombie), "a plain zombie is a Champion");
		Champions.make(zombie, Champions.Affix.JUGGERNAUT);
		check(helper, Champions.is(zombie) && Champions.affix(zombie) == Champions.Affix.JUGGERNAUT && zombie.getMaxHealth() >= 99 && zombie.getHealth() >= 99,
				"a Juggernaut has %s of %s health", zombie.getHealth(), zombie.getMaxHealth());
		check(helper, zombie.getCustomName() != null, "a Champion has no name above its head");
		// A Venomous one poisons what it hits
		Mob spider = (Mob) helper.spawn(EntityTypes.SPIDER, new BlockPos(2, 2, 1), EntitySpawnReason.NATURAL);
		Champions.make(spider, Champions.Affix.VENOMOUS);
		Champions.hit(p, level.damageSources().mobAttack(spider), 2);
		check(helper, p.hasEffect(MobEffects.POISON), "a Venomous Champion's hit did not poison");
		// Slain by a player: XP in Melee on top, counted for titles and the weekly contract
		Contracts.ensure(p, LocalDate.of(2026, 5, 4));
		LegendsData.Contract weekly = legends(p).contracts.weekly;
		weekly.kind = "champion";
		weekly.goal = 1;
		weekly.progress = 0;
		double before = Progression.totalXp(PlayerStore.get(p.getUUID()), Skill.MELEE);
		Champions.died(zombie, level.damageSources().playerAttack(p));
		double gained = Progression.totalXp(PlayerStore.get(p.getUUID()), Skill.MELEE) - before;
		check(helper, close(gained, Config.CHAMPION_KILL_XP) || gained >= Config.CHAMPION_KILL_XP, "a Champion slain gave %s Melee XP, expected %s",
				gained, Config.CHAMPION_KILL_XP);
		check(helper, legends(p).stat("champions") == 1 && weekly.done && tomes(p, Tome.GRAND) == 1, "the Champion was not counted, or the weekly contract not paid");
		// Spawns: only natural ones become Champions, never bosses
		Config.CHAMPION_CHANCE = 1;
		Mob natural = (Mob) helper.spawn(EntityTypes.SKELETON, new BlockPos(3, 2, 1), EntitySpawnReason.NATURAL);
		Champions.spawned(natural, EntitySpawnReason.NATURAL);
		Mob caged = (Mob) helper.spawn(EntityTypes.SKELETON, new BlockPos(3, 2, 3), EntitySpawnReason.NATURAL);
		Champions.spawned(caged, EntitySpawnReason.SPAWNER);
		Mob cow = (Mob) helper.spawn(EntityTypes.COW, new BlockPos(1, 2, 3), EntitySpawnReason.NATURAL);
		Champions.spawned(cow, EntitySpawnReason.NATURAL);
		Config.reset();
		check(helper, Champions.is(natural) && !Champions.is(caged) && !Champions.is(cow), "Champions came from %s, %s, %s (natural, spawner, cow)",
				Champions.is(natural), Champions.is(caged), Champions.is(cow));
		helper.succeed();
	}

	@GameTest
	public void contractsComeDailyAndPay(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Contractor");
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		Progression.setLevel(progress, Skill.MINING, 20);
		LocalDate day = LocalDate.of(2026, 6, 1);
		check(helper, Contracts.ensure(p, day) && !Contracts.ensure(p, day), "contracts were not made once a day");
		LegendsData.ContractBook book = legends(p).contracts;
		check(helper, book.daily.size() == 3 && book.weekly != null && "kill".equals(book.daily.get(2).kind)
				&& "xp".equals(book.daily.get(0).kind) && "xp".equals(book.daily.get(1).kind), "the contracts are %s", book.daily.stream().map(c -> c.kind).toList());
		check(helper, !book.daily.get(0).target.equals(book.daily.get(1).target), "both XP contracts ask for the same skill");
		// The XP contracts fill with XP in their skill and pay XP in it
		for (int i = 0; i < 2; i++) {
			LegendsData.Contract c = book.daily.get(i);
			Skill s = Skill.byId(c.target);
			double before = Progression.totalXp(progress, s);
			Contracts.xp(p, s, c.goal);
			check(helper, c.done && Progression.totalXp(progress, s) - before >= c.rewardXp - 1e-6, "an XP contract was not done or paid %s",
					Progression.totalXp(progress, s) - before);
		}
		// A bounty: one kill short is not done; swapping it once a day
		LegendsData.Contract bounty = book.daily.get(2);
		check(helper, Contracts.reroll(p, 2, day) && !Contracts.reroll(p, 2, day), "the swap did not work exactly once a day");
		bounty = book.daily.get(2);
		bounty.kind = "kill";
		bounty.target = "minecraft:zombie";
		bounty.goal = 2;
		Mob zombie = (Mob) helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1), EntitySpawnReason.NATURAL);
		Contracts.killed(p, zombie, false);
		check(helper, !bounty.done && bounty.progress == 1, "the bounty is at %s after one kill", bounty.progress);
		Contracts.killed(p, zombie, false);
		check(helper, bounty.done && tomes(p, Tome.INSIGHT) == 1, "all three done did not give a Tome of Insight (%s)", tomes(p, Tome.INSIGHT));
		check(helper, legends(p).stat("contracts") == 3, "%s contracts counted, expected 3", legends(p).stat("contracts"));
		// A new day: new contracts
		check(helper, Contracts.ensure(p, day.plusDays(1)) && book.daily.stream().noneMatch(c -> c.done), "a new day kept the old contracts");
		zombie.discard();
		helper.succeed();
	}

	@GameTest
	public void titlesAreEarnedShownAndSent(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Titled");
		check(helper, Titles.check(p) == 0 && legends(p).titles.isEmpty(), "a new player earned titles: %s", legends(p).titles);
		check(helper, !Titles.equip(p, "mining_grandmaster"), "a title not earned could be shown");
		Progression.setLevel(PlayerStore.get(p.getUUID()), Skill.MINING, Config.MAX_LEVEL);
		check(helper, Titles.check(p) >= 1 && legends(p).titles.contains("mining_grandmaster"), "Mining at its highest level did not earn its title");
		check(helper, Titles.equip(p, "mining_grandmaster") && Titles.shown(PlayerStore.get(p.getUUID())).id().equals("mining_grandmaster"), "the title is not shown");
		boolean sent = PowerTags.now(helper.getLevel().getServer()).stream()
				.anyMatch(e -> e.player().equals(p.getUUID()) && "mining_grandmaster".equals(e.title()));
		check(helper, sent, "the title is not sent for the name above the head");
		Map<String, net.minecraft.network.chat.Component> m = TabPlaceholders.values(p);
		check(helper, m.get("title").getString().equals("the Deep Miner"), "the title placeholder is %s", m.get("title").getString());
		check(helper, Titles.equip(p, "") && Titles.shown(PlayerStore.get(p.getUUID())) == null, "the title could not be taken off");
		// Every title has a unique id and a name
		check(helper, Titles.all().stream().map(Titles.Def::id).distinct().count() == Titles.all().size(), "two titles share an id");
		helper.succeed();
	}

	@GameTest
	public void theSkillOfTheDayChangesDailyAndComesRound(GameTestHelper helper) {
		Config.reset();
		Map<Skill, Integer> seen = new EnumMap<>(Skill.class);
		LocalDate start = LocalDate.of(2026, 1, 1);
		Skill last = null;
		int n = Skill.values().length;
		for (int i = 0; i < 4 * n; i++) {
			Skill s = SkillOfTheDay.of(start.plusDays(i));
			check(helper, s != null && s != last, "day %s has %s again", i, s);
			seen.merge(s, 1, Integer::sum);
			last = s;
		}
		check(helper, seen.size() == n, "in %s days only %s skills came", 4 * n, seen.size());
		check(helper, SkillOfTheDay.of(start) == SkillOfTheDay.of(start), "the same day gave two skills");
		Config.SKILL_OF_THE_DAY_BONUS = 0;
		check(helper, SkillOfTheDay.of(start) == null, "the Skill of the Day is there with its bonus at 0");
		Config.reset();
		// The view the client is sent reads back
		ServerPlayer p = TestPlayers.survivor(helper, "Viewer");
		Boosts.add(p, 50, 120, "greater");
		Legends.View v = Legends.parse(new com.google.gson.Gson().toJson(Legends.view(p)));
		check(helper, v.boostsOn && close(v.boost, 0.5) && v.boosts.size() == 1 && v.boosts.get(0).seconds == 120, "the view read back as boost %s", v.boost);
		helper.succeed();
	}
}
