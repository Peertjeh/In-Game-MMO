package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.SkeletonTests.check;

import java.nio.file.Files;
import java.nio.file.Path;

import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.death.DeathService;
import com.extremeprogression.network.SynergyPayload;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.synergy.Chronicle;
import com.extremeprogression.synergy.Synergies;
import com.extremeprogression.synergy.Synergy;
import com.extremeprogression.synergy.SynergyEffects;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Synergies (design §2): both skills at level 30, discovered personally the first time their situation happens, the
 * server's first discoverer in the chronicle, and what each one does.
 */
public class SynergyTests {
	private static boolean close(double a, double b) {
		return Math.abs(a - b) < 1e-4;
	}

	/** A player with these two skills at these levels. */
	private static ServerPlayer with(GameTestHelper helper, String name, Skill a, int levelA, Skill b, int levelB) {
		ServerPlayer p = TestPlayers.survivor(helper, name);
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		Progression.setLevel(progress, a, levelA);
		Progression.setLevel(progress, b, levelB);
		return p;
	}

	private static LivingEntity zombie(GameTestHelper helper) {
		return (LivingEntity) helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1), EntitySpawnReason.NATURAL);
	}

	@GameTest
	public void everySynergyHasTwoSkillsThatDoNotOppose(GameTestHelper helper) {
		check(helper, Synergy.of(false).size() == 12 && Synergy.of(true).size() == 8, "there are %s synergies and %s duo synergies, not 12 and 8",
				Synergy.of(false).size(), Synergy.of(true).size());
		for (Synergy s : Synergy.of(false)) {
			check(helper, s.first != s.second && s.first.opposite() != s.second, "%s pairs opposing skills", s.id);
		}
		helper.succeed();
	}

	@GameTest
	public void aSynergyNeedsBothSkillsAt30AndADiscovery(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = with(helper, "Seeker", Skill.MELEE, 30, Skill.EXPLORATION, 29);
		LivingEntity target = zombie(helper);
		p.setSprinting(true);
		check(helper, !Synergies.trigger(p, Synergy.CHARGE) && !Synergies.known(p, Synergy.CHARGE), "Charge worked or was found with Exploration at 29");
		check(helper, close(SynergyEffects.damage(target, p.damageSources().playerAttack(p), 10), 10), "Charge added damage with Exploration at 29");
		Progression.setLevel(PlayerStore.get(p.getUUID()), Skill.EXPLORATION, 30);
		check(helper, !Synergies.known(p, Synergy.CHARGE), "Charge was known before its situation happened");
		float hit = SynergyEffects.damage(target, p.damageSources().playerAttack(p), 10);
		check(helper, close(hit, 11), "the hit that discovered Charge dealt %s, expected 11", hit);
		PlayerProgress.Discovery d = PlayerStore.get(p.getUUID()).synergies.get(Synergy.CHARGE.id);
		check(helper, d != null && d.time > 0 && d.dimension.equals(p.level().dimension().identifier().toString()), "the discovery was not recorded with its place");
		check(helper, Chronicle.first(Synergy.CHARGE) != null, "the chronicle has no first discoverer of Charge");
		// Below level 30 again: still discovered, but asleep
		Progression.setLevel(PlayerStore.get(p.getUUID()), Skill.MELEE, 29);
		check(helper, Synergies.known(p, Synergy.CHARGE) && !Synergies.active(p, Synergy.CHARGE), "a discovered Charge still works below level 30");
		check(helper, close(SynergyEffects.damage(target, p.damageSources().playerAttack(p), 10), 10), "Charge added damage below level 30");
		helper.succeed();
	}

	@GameTest
	public void counterstrikeFollowsABlockOnce(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = with(helper, "Parry", Skill.MELEE, 30, Skill.DEFENSE, 30);
		LivingEntity target = zombie(helper);
		check(helper, close(SynergyEffects.damage(target, p.damageSources().playerAttack(p), 10), 10), "Counterstrike without a block");
		SynergyEffects.blocked(p);
		check(helper, close(SynergyEffects.damage(target, p.damageSources().playerAttack(p), 10), 12), "the hit after a block was not +20%%");
		check(helper, close(SynergyEffects.damage(target, p.damageSources().playerAttack(p), 10), 10), "a second hit after one block was +20%% too");
		helper.succeed();
	}

	@GameTest
	public void theEffectsOfTheOtherSynergies(GameTestHelper helper) {
		Config.reset();
		// Venom Tip: a tipped arrow's effect 50% longer
		ServerPlayer archer = with(helper, "Venom", Skill.RANGED, 30, Skill.ARCANA, 30);
		LivingEntity target = zombie(helper);
		SynergyEffects.arrowOwner = archer;
		MobEffectInstance poison;
		try {
			poison = SynergyEffects.effect(target, new MobEffectInstance(MobEffects.POISON, 100, 0));
		} finally {
			SynergyEffects.arrowOwner = null;
		}
		check(helper, poison.getDuration() == 150, "a tipped arrow's poison lasts %s ticks, expected 150", poison.getDuration());
		check(helper, SynergyEffects.effect(target, new MobEffectInstance(MobEffects.POISON, 100, 0)).getDuration() == 100, "Venom Tip lengthened an effect without an arrow");
		// Tidal Magic: Water Breathing 50% longer
		ServerPlayer diver = with(helper, "Tidal", Skill.FISHING, 30, Skill.ARCANA, 30);
		ItemStack breathing = PotionContents.createItemStack(Items.POTION, Potions.WATER_BREATHING);
		check(helper, close(SynergyEffects.drinkFactor(breathing, diver), 1.5), "Water Breathing is not 50%% longer");
		check(helper, close(SynergyEffects.drinkFactor(PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS), diver), 1), "Tidal Magic lengthened Swiftness");
		// Herb Lore: glow berries, 10 seconds of Night Vision
		ServerPlayer herbalist = with(helper, "Herbs", Skill.FORAGING, 30, Skill.ARCANA, 30);
		SynergyEffects.ate(herbalist, new ItemStack(Items.GLOW_BERRIES));
		MobEffectInstance night = herbalist.getEffect(MobEffects.NIGHT_VISION);
		check(helper, night != null && night.getDuration() == 200, "glow berries gave %s of Night Vision", night == null ? "nothing" : night.getDuration() + " ticks");
		// Tailored: armor wears 10% slower
		ServerPlayer smith = with(helper, "Tailor", Skill.SMITHING, 30, Skill.DEFENSE, 30);
		int worn = SynergyEffects.durability(new ItemStack(Items.DIAMOND_CHESTPLATE), smith, 2000);
		check(helper, worn > 1700 && worn < 1900, "2,000 points of armor wear became %s, expected about 1,800", worn);
		check(helper, SynergyEffects.durability(new ItemStack(Items.DIAMOND_PICKAXE), smith, 100) == 100, "Tailored spared a pickaxe");
		check(helper, Synergies.known(smith, Synergy.TAILORED) && Synergies.known(herbalist, Synergy.HERB_LORE) && Synergies.known(diver, Synergy.TIDAL_MAGIC)
				&& Synergies.known(archer, Synergy.VENOM_TIP), "the synergies were not discovered by their situations");
		helper.succeed();
	}

	@GameTest
	public void deathFatigueHalvesASynergy(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = with(helper, "Weary", Skill.MELEE, 30, Skill.EXPLORATION, 30);
		LivingEntity target = zombie(helper);
		DeathService.died(p, p.damageSources().generic());
		p.setSprinting(true);
		float hit = SynergyEffects.damage(target, p.damageSources().playerAttack(p), 10);
		check(helper, close(hit, 10.5), "a fatigued Charge dealt %s, expected 10.5", hit);
		helper.succeed();
	}

	@GameTest
	public void theCodexAndTheChronicleReachTheClient(GameTestHelper helper) throws Exception {
		Config.reset();
		ServerPlayer p = with(helper, "Scribe", Skill.FISHING, 30, Skill.EXPLORATION, 30);
		Synergies.discover(p, Synergy.SEAFARER);
		SynergyPayload payload = SynergyPayload.of(PlayerStore.get(p.getUUID()), p.getUUID().toString());
		check(helper, payload.entries().size() == Synergy.values().length, "the payload holds %s synergies", payload.entries().size());
		SynergyPayload.Entry e = payload.entries().stream().filter(x -> x.id().equals("seafarer")).findFirst().orElseThrow();
		check(helper, e.known() && e.foundOnServer(), "the payload does not show Seafarer as found");
		Chronicle.First first = Chronicle.first(Synergy.SEAFARER);
		check(helper, e.firstMine() == first.uuid.equals(p.getUUID().toString()) && e.firstName().equals(first.name), "the first discoverer is not the chronicle's");
		// The chronicle is written to world.json at once
		Path world = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT);
		Path file = world.resolve("extremeprogression").resolve("world.json");
		check(helper, Files.exists(file) && Files.readString(file).contains("seafarer"), "world.json does not hold the first discovery");
		Chronicle.open(world);
		check(helper, Chronicle.first(Synergy.SEAFARER) != null && Chronicle.first(Synergy.SEAFARER).name.equals(first.name), "the chronicle did not load back");
		// A second discovery is never a new first
		ServerPlayer later = with(helper, "Later", Skill.FISHING, 30, Skill.EXPLORATION, 30);
		Synergies.discover(later, Synergy.SEAFARER);
		check(helper, Chronicle.first(Synergy.SEAFARER).name.equals(first.name), "a later discovery replaced the first discoverer");
		helper.succeed();
	}
}
