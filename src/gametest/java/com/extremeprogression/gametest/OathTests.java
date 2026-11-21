package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.SkeletonTests.check;

import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.legends.LegendsData;
import com.extremeprogression.legends.Oath;
import com.extremeprogression.legends.Oaths;
import com.extremeprogression.legends.Relic;
import com.extremeprogression.legends.Relics;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.xp.XpService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/** Relics and Oaths (2.2): where relics come from, and the Oaths that take them for permanent buffs. */
public class OathTests {
	private static LegendsData legends(ServerPlayer p) {
		return PlayerStore.get(p.getUUID()).legends;
	}

	private static int carried(ServerPlayer p, Relic r) {
		return Oaths.carried(p.getInventory()).getOrDefault(r, 0);
	}

	@GameTest
	public void relicsAreFoundInChestsAndGivenForRaids(GameTestHelper helper) {
		Config.reset();
		ItemStack pearl = Relic.TIDAL_PEARL.stack(3);
		check(helper, Relic.of(pearl) == Relic.TIDAL_PEARL && pearl.getCount() == 3 && Relic.of(com.extremeprogression.legends.Tome.INSIGHT.stack(1)) == null,
				"a relic is not told from other items");
		var random = net.minecraft.util.RandomSource.create(7L);
		int found = 0, ominous = 0;
		for (int i = 0; i < 2000; i++) {
			java.util.List<ItemStack> drops = new java.util.ArrayList<>();
			Relics.loot("minecraft:chests/ancient_city", random, drops);
			found += drops.size();
			java.util.List<ItemStack> vault = new java.util.ArrayList<>();
			Relics.loot("minecraft:chests/trial_chambers/reward_ominous_rare", random, vault);
			ominous += vault.size();
		}
		check(helper, found > 400 && found < 600, "an ancient city held a Sculk Heart %s times in 2000, expected about 500", found);
		check(helper, ominous > 400 && ominous < 600, "an ominous vault held %s crests in 2000, expected about 500 (rolled once)", ominous);
		// A raid won: banner shards, one more for every omen level
		ServerPlayer p = TestPlayers.survivor(helper, "Raider");
		Relics.raidWon(p, 2);
		check(helper, carried(p, Relic.WARBANNER_SHARD) == 3 && legends(p).stat("raids") == 1, "a raid at omen level III gave %s shards",
				carried(p, Relic.WARBANNER_SHARD));
		helper.succeed();
	}

	@GameTest
	public void anOathTakesItsRelicsAndGivesItsBuffForGood(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Sworn");
		double health = p.getAttribute(Attributes.MAX_HEALTH).getValue();
		// Not the relics, and not the feat: no Oath
		check(helper, Oaths.check(p, Oath.RAID_BREAKER) == Oaths.Result.FEAT, "the Raid Breaker could be sworn without a raid won");
		legends(p).add("raids", 1);
		check(helper, Oaths.check(p, Oath.RAID_BREAKER) == Oaths.Result.RELICS, "the Raid Breaker could be sworn without its shards");
		p.addItem(Relic.WARBANNER_SHARD.stack(7));
		check(helper, Oaths.swear(p, Oath.RAID_BREAKER) == Oaths.Result.OK, "the Raid Breaker was not sworn with a raid and 7 shards");
		check(helper, carried(p, Relic.WARBANNER_SHARD) == 2, "swearing took %s shards, expected 5", 7 - carried(p, Relic.WARBANNER_SHARD));
		check(helper, Math.abs(p.getAttribute(Attributes.MAX_HEALTH).getValue() - health - 4) < 1e-9, "the Raid Breaker gave %s more health, expected 4",
				p.getAttribute(Attributes.MAX_HEALTH).getValue() - health);
		check(helper, Oaths.swear(p, Oath.RAID_BREAKER) == Oaths.Result.SWORN, "an Oath could be sworn twice");
		check(helper, legends(p).titles.contains("oathsworn"), "the first Oath did not earn the title Oathsworn");
		// Applied again on a new body (a respawn, a login), and taken off when not sworn
		p.getAttribute(Attributes.MAX_HEALTH).removeModifier(com.extremeprogression.ExtremeProgression.id("oath_raid_breaker"));
		Oaths.apply(p);
		check(helper, Math.abs(p.getAttribute(Attributes.MAX_HEALTH).getValue() - health - 4) < 1e-9, "the Oath's health was not put on again");
		// The Deep Dark: shorter cooldowns; the Void: longer abilities
		Progression.setLevel(PlayerStore.get(p.getUUID()), Skill.EXPLORATION, 50);
		p.addItem(Relic.SCULK_HEART.stack(3));
		p.addItem(Relic.VOID_SHARD.stack(4));
		check(helper, Oaths.swear(p, Oath.DEEP_DARK) == Oaths.Result.OK && Oaths.swear(p, Oath.VOID) == Oaths.Result.OK, "the Deep Dark or the Void was not sworn");
		check(helper, Math.abs(Oaths.cooldownCut(p) - 0.10) < 1e-9 && Math.abs(Oaths.lasting(p) - 0.15) < 1e-9, "the Oaths give %s shorter cooldowns and %s longer abilities",
				Oaths.cooldownCut(p), Oaths.lasting(p));
		check(helper, com.extremeprogression.alchemy.SkillPotions.lasting(p, 100) == 115, "an ability of 100 ticks lasts %s under the Void",
				com.extremeprogression.alchemy.SkillPotions.lasting(p, 100));
		// Off: no buffs
		Config.RELICS = false;
		check(helper, Oaths.cooldownCut(p) == 0 && Oaths.check(p, Oath.TIDES) == Oaths.Result.OFF, "with relics off the Oaths still work");
		Config.reset();
		helper.succeed();
	}

	@GameTest
	public void theOathOfLegendsAsksForEverything(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Legend");
		for (Relic r : Relic.values()) {
			p.addItem(r.stack(1));
		}
		check(helper, Oaths.check(p, Oath.LEGENDS) == Oaths.Result.FEAT, "the Oath of Legends could be sworn without the other Oaths");
		for (Oath o : Oath.values()) {
			if (o != Oath.LEGENDS) {
				legends(p).oaths.add(o.id);
			}
		}
		check(helper, Oaths.swear(p, Oath.LEGENDS) == Oaths.Result.OK, "the Oath of Legends was not sworn with every relic and the other Oaths");
		check(helper, Oaths.carried(p.getInventory()).isEmpty(), "the Oath of Legends did not take one of each relic");
		check(helper, Math.abs(Oaths.xp(p) - 0.10) < 1e-9 && legends(p).titles.contains("oathkeeper"), "the Oath of Legends gave %s XP, or no title", Oaths.xp(p));
		// +10% XP on top of what is earned
		ServerPlayer plain = TestPlayers.survivor(helper, "Unsworn");
		double a = XpService.award(plain, Skill.FISHING, 40);
		double b = XpService.award(p, Skill.FISHING, 40);
		check(helper, a > 0 && Math.abs(b - a * 1.1) < 1e-6, "the Oath of Legends made %s of %s", b, a);
		helper.succeed();
	}
}
