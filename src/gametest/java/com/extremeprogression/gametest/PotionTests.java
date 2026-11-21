package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.SkeletonTests.check;

import java.util.List;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.alchemy.SkillPotions;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.skill.Specialization;
import com.extremeprogression.spec.Active;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;

/** Skill potions (2.1): Quickening runs the abilities' cooldowns faster, Persistence makes them last longer. */
public class PotionTests {
	@GameTest
	public void theSkillPotionsAreRegisteredAndBrewed(GameTestHelper helper) {
		Potion strong = BuiltInRegistries.POTION.getValue(ExtremeProgression.id("strong_quickening"));
		Potion longer = BuiltInRegistries.POTION.getValue(ExtremeProgression.id("long_persistence"));
		check(helper, strong != null && strong.getEffects().get(0).getAmplifier() == 1 && strong.getEffects().get(0).getDuration() == 1800,
				"the strong Potion of Quickening is not level II for 1:30");
		check(helper, longer != null && longer.getEffects().get(0).getDuration() == 9600, "the long Potion of Persistence does not last 8:00");
		check(helper, BuiltInRegistries.MOB_EFFECT.getValue(ExtremeProgression.id("quickening")) != null, "the Quickening effect is not registered");
		var recipes = helper.getLevel().getServer().getRecipeManager();
		List<String> missing = new java.util.ArrayList<>();
		for (String effect : new String[] {"quickening", "persistence"}) {
			String reagent = effect.equals("quickening") ? "clock" : "amethyst_shard";
			for (String c : new String[] {"potion", "splash_potion", "lingering_potion"}) {
				for (String id : new String[] {c + "_awkward_" + reagent + "_" + effect, c + "_" + effect + "_redstone", c + "_" + effect + "_glowstone_dust"}) {
					if (recipes.byKey(ResourceKey.create(Registries.RECIPE, ExtremeProgression.id("brewing/" + id))).isEmpty()) {
						missing.add(id);
					}
				}
			}
			for (String v : new String[] {effect, "long_" + effect, "strong_" + effect}) {
				for (String id : new String[] {"potion_" + v + "_gunpowder", "splash_potion_" + v + "_dragon_breath"}) {
					if (recipes.byKey(ResourceKey.create(Registries.RECIPE, ExtremeProgression.id("brewing/" + id))).isEmpty()) {
						missing.add(id);
					}
				}
			}
		}
		check(helper, missing.isEmpty(), "brewing recipes not loaded: %s", missing);
		helper.succeed();
	}

	@GameTest
	public void quickeningRunsTheCooldownsFaster(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Quick");
		SkillData d = PlayerStore.get(p.getUUID()).get(Skill.MELEE);
		long now = helper.getLevel().getServer().overworld().getGameTime();
		d.cooldowns.put(Specialization.BLADE.id, now + 1200);
		check(helper, SkillPotions.quickening(p) == 0, "without the potion the cooldowns run faster");
		p.addEffect(new MobEffectInstance(SkillPotions.QUICKENING, 600, 0));
		check(helper, Math.abs(SkillPotions.quickening(p) - 0.5) < 1e-9, "Quickening I gives %s, expected half again as fast", SkillPotions.quickening(p));
		// Ten seconds under Quickening I take five more seconds off
		SkillPotions.quicken(p, SkillPotions.quickening(p) * 20 * 10);
		check(helper, d.cooldowns.get(Specialization.BLADE.id) == now + 1200 - 100, "ten seconds of Quickening I took %s ticks off, expected 100",
				now + 1200 - d.cooldowns.get(Specialization.BLADE.id));
		// Level II: twice as fast; never below ready
		p.addEffect(new MobEffectInstance(SkillPotions.QUICKENING, 600, 1));
		check(helper, Math.abs(SkillPotions.quickening(p) - 1.0) < 1e-9, "Quickening II gives %s, expected twice as fast", SkillPotions.quickening(p));
		SkillPotions.quicken(p, 5000);
		check(helper, d.cooldowns.get(Specialization.BLADE.id) == now, "the cooldown went past ready: %s", d.cooldowns.get(Specialization.BLADE.id) - now);
		helper.succeed();
	}

	@GameTest
	public void persistenceMakesAbilitiesLastLonger(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Lasting");
		long tick = helper.getLevel().getServer().getTickCount();
		Active.State plain = Active.start(p, Specialization.BLADE, 1, 100);
		check(helper, plain.until() - tick == 100, "without the potion an ability lasted %s ticks", plain.until() - tick);
		p.addEffect(new MobEffectInstance(SkillPotions.PERSISTENCE, 600, 1));
		Active.State longer = Active.start(p, Specialization.BLADE, 1, 100);
		check(helper, longer.until() - tick == 160, "under Persistence II an ability lasted %s ticks, expected 160", longer.until() - tick);
		Active.end(p, Specialization.BLADE);
		helper.succeed();
	}
}
