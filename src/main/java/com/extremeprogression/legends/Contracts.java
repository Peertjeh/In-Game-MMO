package com.extremeprogression.legends;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import com.extremeprogression.config.Config;
import com.extremeprogression.config.SkillSettings;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.xp.XpService;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Contracts (2.0): three a day and one a week for every player, worked on as they play. A daily contract asks for XP in
 * one of their skills or a number of one kind of hostile mob; done, it pays XP in that skill (the bounties in Melee).
 * All three done give a Tome of Insight. The weekly one asks for more (champions, or XP in any skill) and pays a Grand
 * Tome. One unfinished daily may be swapped for another once a day. New ones come with the first login of the day (and
 * the week), on the server's calendar.
 */
public final class Contracts {
	private Contracts() {}

	/** The mobs a bounty may ask for: common hostile mobs of the Overworld. */
	public static final List<String> BOUNTIES = List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:creeper",
			"minecraft:enderman", "minecraft:drowned", "minecraft:witch", "minecraft:husk", "minecraft:stray", "minecraft:slime", "minecraft:pillager");

	static LegendsData.ContractBook book(ServerPlayer p) {
		return PlayerStore.get(p.getUUID()).legends.contracts;
	}

	public static String week(LocalDate day) {
		return day.get(IsoFields.WEEK_BASED_YEAR) + "-W" + day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
	}

	/** Today's contracts, and this week's: new ones when the day (or week) changed. Returns whether any are new. */
	public static boolean ensure(ServerPlayer p, LocalDate today) {
		if (!Config.CONTRACTS) {
			return false;
		}
		LegendsData.ContractBook b = book(p);
		boolean changed = false;
		Random r = new Random(p.getUUID().getMostSignificantBits() ^ today.toEpochDay() * 0x9E3779B97F4A7C15L);
		if (!today.toString().equals(b.day)) {
			b.day = today.toString();
			b.daily = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				b.daily.add(daily(p, r, i, b.daily));
			}
			changed = true;
		}
		String week = week(today);
		if (!week.equals(b.week) || b.weekly == null) {
			b.week = week;
			b.weekly = weekly(p, r);
			changed = true;
		}
		if (changed) {
			PlayerStore.markDirty(p.getUUID());
			Legends.dirty(p);
		}
		return changed;
	}

	/** The skills a player has played most: the highest levels first, the skills that are on. */
	static List<Skill> played(PlayerProgress progress) {
		List<Skill> out = new ArrayList<>();
		for (Skill s : Skill.values()) {
			if (SkillSettings.enabled(s)) {
				out.add(s);
			}
		}
		out.sort(Comparator.comparingDouble((Skill s) -> -(progress.get(s).level + progress.get(s).masteryRanks + Progression.totalXp(progress, s) * 1e-9)));
		return out;
	}

	/** A new daily contract: the first two for XP in skills the player plays (one of their top three, then any), the third a bounty. */
	static LegendsData.Contract daily(ServerPlayer p, Random r, int slot, List<LegendsData.Contract> taken) {
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		LegendsData.Contract c = new LegendsData.Contract();
		List<Skill> skills = played(progress);
		if (slot < 2 && !skills.isEmpty()) {
			List<Skill> pool = new ArrayList<>(slot == 0 ? skills.subList(0, Math.min(3, skills.size())) : skills);
			pool.removeIf(s -> taken.stream().anyMatch(t -> t.target.equals(s.id)));
			if (pool.isEmpty()) {
				pool = skills;
			}
			return xpContract(progress, pool.get(r.nextInt(pool.size())), r);
		}
		String mob = BOUNTIES.get(r.nextInt(BOUNTIES.size()));
		c.kind = "kill";
		c.target = mob;
		c.goal = 8 + r.nextInt(13);
		c.rewardSkill = Skill.MELEE.id;
		c.rewardXp = Math.max(150, 0.25 * need(progress, Skill.MELEE));
		return c;
	}

	/** XP in a skill: about half of what its next level takes (at least 300), paying a quarter of a level. */
	static LegendsData.Contract xpContract(PlayerProgress progress, Skill s, Random r) {
		LegendsData.Contract c = new LegendsData.Contract();
		c.kind = "xp";
		c.target = s.id;
		double need = need(progress, s);
		c.goal = Math.max(300, Math.round(need * (0.4 + 0.2 * r.nextDouble()) / 50.0) * 50.0);
		c.rewardSkill = s.id;
		c.rewardXp = Math.max(150, Math.round(need * 0.25));
		return c;
	}

	/** What the skill's next level (or Mastery rank) takes. */
	static double need(PlayerProgress progress, Skill s) {
		double need = Progression.xpToNext(progress, s);
		return Double.isFinite(need) && need > 0 ? need : 1000;
	}

	/** The weekly contract: five champions, or 25 daily contracts' worth of XP in any skill. */
	static LegendsData.Contract weekly(ServerPlayer p, Random r) {
		LegendsData.Contract c = new LegendsData.Contract();
		if (Config.CHAMPION_CHANCE > 0 && r.nextBoolean()) {
			c.kind = "champion";
			c.target = "any";
			c.goal = 5;
		} else {
			c.kind = "xp";
			c.target = "any";
			c.goal = 20_000;
		}
		c.rewardTome = Tome.GRAND.id;
		return c;
	}

	// --- Progress --------------------------------------------------------------------------------

	/** The player earned XP in a skill (from {@code XpService.award}). */
	public static void xp(ServerPlayer p, Skill skill, double amount) {
		if (!Config.CONTRACTS || !(amount > 0)) {
			return;
		}
		LegendsData.ContractBook b = book(p);
		for (LegendsData.Contract c : b.daily) {
			if ("xp".equals(c.kind) && c.target.equals(skill.id)) {
				progress(p, c, amount, false);
			}
		}
		if (b.weekly != null && "xp".equals(b.weekly.kind)) {
			progress(p, b.weekly, amount, true);
		}
	}

	/** The player slew a mob (and it counted: not from a spawner, egg or command). */
	public static void killed(ServerPlayer p, LivingEntity mob, boolean champion) {
		if (!Config.CONTRACTS) {
			return;
		}
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
		LegendsData.ContractBook b = book(p);
		for (LegendsData.Contract c : b.daily) {
			if ("kill".equals(c.kind) && c.target.equals(id)) {
				progress(p, c, 1, false);
			}
		}
		if (champion && b.weekly != null && "champion".equals(b.weekly.kind)) {
			progress(p, b.weekly, 1, true);
		}
	}

	private static void progress(ServerPlayer p, LegendsData.Contract c, double amount, boolean weekly) {
		if (c.done) {
			return;
		}
		double before = c.share();
		c.progress = Math.min(c.goal, c.progress + amount);
		PlayerStore.markDirty(p.getUUID());
		if (c.progress >= c.goal) {
			complete(p, c, weekly);
		} else if (Math.floor(before * 4) != Math.floor(c.share() * 4)) {
			// (a word at every quarter)
			p.sendSystemMessage(Component.translatable("message.extremeprogression.contract.progress", describe(c), Math.round(c.share() * 100)).withColor(0xFFFF55), true);
			Legends.dirty(p);
		} else {
			Legends.later(p);
		}
	}

	/** A contract is done: its reward, and a tome when all three dailies are. */
	static void complete(ServerPlayer p, LegendsData.Contract c, boolean weekly) {
		c.done = true;
		LegendsData d = PlayerStore.get(p.getUUID()).legends;
		d.add("contracts", 1);
		Skill s = Skill.byId(c.rewardSkill);
		if (s != null && c.rewardXp > 0) {
			XpService.grant(p, s, c.rewardXp);
		}
		Tome t = Tome.byId(c.rewardTome);
		if (t != null && Config.BOOSTS) {
			Boosts.give(p, t, 1);
		}
		p.sendSystemMessage(Component.translatable(weekly ? "message.extremeprogression.contract.weekly_done" : "message.extremeprogression.contract.done", describe(c))
				.withColor(0x55FF55), false);
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.6f);
		LegendsData.ContractBook b = d.contracts;
		if (!weekly && b.daily.size() == 3 && b.daily.stream().allMatch(x -> x.done) && !b.day.equals(b.allDone)) {
			b.allDone = b.day;
			if (Config.BOOSTS) {
				Boosts.give(p, Tome.INSIGHT, 1);
			}
			p.sendSystemMessage(Component.translatable("message.extremeprogression.contract.all_done", Component.translatable(Tome.INSIGHT.translationKey())
					.withColor(Tome.INSIGHT.color)).withColor(0xFFAA00), false);
		}
		PlayerStore.markDirty(p.getUUID());
		Legends.dirty(p);
		Titles.check(p);
	}

	/** Swaps an unfinished daily contract for a new one: once a day. Returns whether it was swapped. */
	public static boolean reroll(ServerPlayer p, int index, LocalDate today) {
		LegendsData.ContractBook b = book(p);
		if (!Config.CONTRACTS || index < 0 || index >= b.daily.size() || b.daily.get(index).done || today.toString().equals(b.rerolled)) {
			return false;
		}
		Random r = new Random(System.nanoTime());
		List<LegendsData.Contract> others = new ArrayList<>(b.daily);
		LegendsData.Contract old = others.remove(index);
		others.add(old);
		LegendsData.Contract fresh = daily(p, r, index, others);
		for (int tries = 0; tries < 6 && fresh.kind.equals(old.kind) && fresh.target.equals(old.target); tries++) {
			fresh = daily(p, r, index, others);
		}
		b.daily.set(index, fresh);
		b.rerolled = today.toString();
		PlayerStore.markDirty(p.getUUID());
		Legends.dirty(p);
		return true;
	}

	/** "Earn 1,200 Mining XP", "Slay 12 Zombies", "Slay 5 Champions". */
	public static Component describe(LegendsData.Contract c) {
		long goal = Math.round(c.goal);
		return switch (c.kind) {
			case "xp" -> "any".equals(c.target) ? Component.translatable("contract.extremeprogression.xp_any", String.format(java.util.Locale.ROOT, "%,d", goal))
					: Component.translatable("contract.extremeprogression.xp", String.format(java.util.Locale.ROOT, "%,d", goal), skillName(c.target));
			case "kill" -> Component.translatable("contract.extremeprogression.kill", goal,
					Component.translatable("entity." + c.target.replace(':', '.')));
			case "champion" -> Component.translatable("contract.extremeprogression.champion", goal);
			default -> Component.literal(c.kind);
		};
	}

	private static Component skillName(String id) {
		Skill s = Skill.byId(id);
		return s == null ? Component.literal(id) : Component.translatable(s.translationKey());
	}
}
