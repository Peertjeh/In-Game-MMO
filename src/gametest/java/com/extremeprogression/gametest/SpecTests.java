package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.CombatTests.swing;
import static com.extremeprogression.gametest.SkeletonTests.check;

import com.extremeprogression.ability.AbilityService;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.skill.Specialization;
import com.extremeprogression.spec.Active;
import com.extremeprogression.spec.Defense;
import com.extremeprogression.spec.Melee;
import com.extremeprogression.spec.Ranged;
import com.extremeprogression.spec.Specs;
import com.extremeprogression.spec.Wounds;
import com.extremeprogression.tree.NodeSlot;
import com.extremeprogression.tree.TreeRules;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 6, part 1: the abilities, passives, choice nodes and trade-offs of Melee, Ranged, Defense and Exploration.
 * Where vanilla scales a mob's hit on a player with the difficulty, a plain player takes the same hit to compare with.
 */
public class SpecTests {
	/** A player with a specialization chosen at this level, and these nodes bought: slot, rank, slot, rank... */
	static ServerPlayer specialist(GameTestHelper helper, String name, Specialization spec, int level, Object... nodes) {
		ServerPlayer p = TestPlayers.survivor(helper, name);
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		Progression.setLevel(progress, spec.skill, level);
		SkillData d = progress.get(spec.skill);
		d.specs.add(spec.id);
		for (int i = 0; i < nodes.length; i += 2) {
			for (int r = 0; r < (Integer) nodes[i + 1]; r++) {
				d.bought.add(TreeRules.key(spec, (NodeSlot) nodes[i]));
			}
		}
		return p;
	}

	private static void heavyArmor(LivingEntity e) {
		e.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		e.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		e.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1e-3;
	}

	/** The damage a player took from a hit, as a share of what a plain player took from the same hit. */
	private static double ratio(ServerPlayer p, ServerPlayer plain) {
		return (20 - p.getHealth()) / (20 - plain.getHealth());
	}

	// --- Melee -----------------------------------------------------------------------------------

	@GameTest
	public void ripostePariesTheNextHitAndStrikesBack(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "riposte", Specialization.BLADE, 30, NodeSlot.A1, 1);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		LivingEntity zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1), EntitySpawnReason.NATURAL);
		p.setPos(zombie.getX() + 1.5, zombie.getY(), zombie.getZ());
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Riposte was not used with a sword");
		check(helper, !p.hurtServer(level, level.damageSources().mobAttack((net.minecraft.world.entity.Mob) zombie), 6) && p.getHealth() == 20f,
				"the hit during Riposte was not parried: %s health", p.getHealth());
		check(helper, zombie.getHealth() < 20f, "Riposte did not strike back");
		check(helper, Wounds.bleeding(zombie), "the zombie does not bleed after Riposte");
		check(helper, !Active.on(p, Specialization.BLADE), "Riposte did not end with the parry");
		p.hurtServer(level, level.damageSources().mobAttack((net.minecraft.world.entity.Mob) zombie), 6);
		check(helper, p.getHealth() < 20f, "a second hit was parried too");
		check(helper, AbilityService.use(p, false) == AbilityService.Result.COOLDOWN, "Riposte had no cooldown");
		helper.succeed();
	}

	@GameTest
	public void bladeEdgesAndItsPriceAgainstHeavyArmor(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		// Keen Edge V: +10% with a sword
		ServerPlayer p = specialist(helper, "edge", Specialization.BLADE, 30, NodeSlot.P1, 5);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		LivingEntity creeper = helper.spawn(EntityTypes.CREEPER, new BlockPos(1, 1, 1), EntitySpawnReason.NATURAL);
		swing(p, creeper, false);
		creeper.hurtServer(level, level.damageSources().playerAttack(p), 10);
		check(helper, near(creeper.getHealth(), 9f), "Keen Edge V left the creeper at %s, not 9", creeper.getHealth());
		// The price: −15% against a target in heavy armor (a creeper in iron, whose armor then takes its share)
		ServerPlayer plain = TestPlayers.survivor(helper, "noblade");
		LivingEntity a = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 1), EntitySpawnReason.NATURAL);
		LivingEntity b = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 3), EntitySpawnReason.NATURAL);
		heavyArmor(a);
		heavyArmor(b);
		ServerPlayer priced = specialist(helper, "price", Specialization.BLADE, 30);
		swing(priced, a, false);
		a.hurtServer(level, level.damageSources().playerAttack(priced), 10);
		swing(plain, b, false);
		b.hurtServer(level, level.damageSources().playerAttack(plain), 10);
		double share = (20 - a.getHealth()) / (20 - b.getHealth());
		check(helper, share < 0.9 && share > 0.8, "Blade's price against heavy armor came to ×%s, not about ×0.85", share);
		helper.succeed();
	}

	@GameTest
	public void shatterBreaksArmorAroundTheNextFullHit(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "shatter", Specialization.CRUSHER, 30, NodeSlot.A1, 1);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
		LivingEntity first = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1), EntitySpawnReason.NATURAL);
		LivingEntity second = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 1), EntitySpawnReason.NATURAL);
		heavyArmor(first);
		heavyArmor(second);
		check(helper, first.getArmorValue() > 0, "the zombie has no armor to begin with");
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Shatter was not used with an axe");
		swing(p, first, true);
		first.hurtServer(level, level.damageSources().playerAttack(p), 6);
		check(helper, first.getArmorValue() == 0 && Wounds.shattered(first), "the target's armor is %s after Shatter", first.getArmorValue());
		check(helper, second.getArmorValue() == 0 && second.getHealth() < 20f, "the zombie beside it was not hit by the shockwave");
		check(helper, !Active.on(p, Specialization.CRUSHER), "Shatter did not end with the hit");
		// Crusher's price: −10% attack speed with every weapon
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		double before = p.getAttributeValue(Attributes.ATTACK_SPEED);
		Specs.tick(level.getServer());
		check(helper, near(p.getAttributeValue(Attributes.ATTACK_SPEED), before * 0.9), "Crusher's attack speed is %s, not 90%% of %s",
				p.getAttributeValue(Attributes.ATTACK_SPEED), before);
		helper.succeed();
	}

	@GameTest
	public void flurryIgnoresTheAttackBarAndFistsNeedLightGear(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "flurry", Specialization.BRAWLER, 30, NodeSlot.A1, 1, NodeSlot.P1, 5);
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Flurry was not used bare-handed");
		p.resetAttackStrengthTicker();
		check(helper, p.getAttackStrengthScale(0.5f) == 1f && Melee.flurry(p), "the attack bar still counts during Flurry");
		// Hardened Knuckles V: +1.5 damage with fists, but not in an iron chestplate
		double base = p.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
		Specs.tick(level.getServer());
		check(helper, near(p.getAttributeValue(Attributes.ATTACK_DAMAGE), base + 1.5), "fists deal %s, not %s + 1.5", p.getAttributeValue(Attributes.ATTACK_DAMAGE), base);
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		Specs.tick(level.getServer());
		check(helper, near(p.getAttributeValue(Attributes.ATTACK_DAMAGE), base) && !Melee.flurry(p), "Brawler worked in an iron chestplate");
		helper.succeed();
	}

	// --- Ranged ----------------------------------------------------------------------------------

	private static AbstractArrow arrow(ServerLevel level, ServerPlayer owner, boolean crit) {
		AbstractArrow arrow = EntityTypes.ARROW.create(level, EntitySpawnReason.TRIGGERED);
		arrow.setOwner(owner);
		arrow.setCritArrow(crit);
		return arrow;
	}

	@GameTest
	public void deadeyeMakesTheNextShotAPiercingHeadshot(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "deadeye", Specialization.SNIPER, 30, NodeSlot.A1, 1);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
		LivingEntity creeper = helper.spawn(EntityTypes.CREEPER, new BlockPos(1, 1, 1), EntitySpawnReason.NATURAL);
		p.setPos(creeper.getX(), creeper.getY(), creeper.getZ() - 12);
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Deadeye was not used with a bow");
		AbstractArrow shot = arrow(level, p, true);
		shot.setPos(creeper.getX(), creeper.getEyeY(), creeper.getZ() - 1.0);
		level.addFreshEntity(shot);
		shot.setDeltaMovement(0, 0, 1.5);
		check(helper, shot.getPierceLevel() == 1, "Deadeye's arrow pierces %s targets, not 1", shot.getPierceLevel());
		check(helper, !Active.on(p, Specialization.SNIPER), "Deadeye did not end with the shot");
		creeper.hurtServer(level, level.damageSources().arrow(shot, p), 4);
		check(helper, near(creeper.getHealth(), 12f), "Deadeye's headshot left the creeper at %s, not 12", creeper.getHealth());
		shot.discard();
		// Sniper's price: −20% within 8 blocks
		LivingEntity close = helper.spawn(EntityTypes.CREEPER, new BlockPos(3, 1, 3), EntitySpawnReason.NATURAL);
		p.setPos(close.getX() + 4, close.getY(), close.getZ());
		AbstractArrow body = arrow(level, p, true);
		body.setPos(close.getX(), close.getY() + 0.3, close.getZ() - 1.0);
		body.setDeltaMovement(0, 0, 1.5);
		close.hurtServer(level, level.damageSources().arrow(body, p), 10);
		check(helper, near(close.getHealth(), 12f), "a shot from 4 blocks left the creeper at %s, not 12", close.getHealth());
		helper.succeed();
	}

	@GameTest
	public void volleyFiresThreeArrowsInAFan(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "volley", Specialization.SKIRMISHER, 30, NodeSlot.A1, 1);
		ItemStack bow = new ItemStack(Items.BOW);
		p.setItemInHand(InteractionHand.MAIN_HAND, bow);
		check(helper, EnchantmentHelper.processProjectileCount(level, bow, p, 1) == 1, "more than 1 arrow before Volley");
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Volley was not used with a bow");
		check(helper, EnchantmentHelper.processProjectileCount(level, bow, p, 1) == 3, "Volley does not fire 3 arrows");
		check(helper, EnchantmentHelper.processProjectileSpread(level, bow, p, 0f) >= 10f, "Volley's arrows do not fan out");
		check(helper, Ranged.projectiles(TestPlayers.survivor(helper, "single"), 1) == 1, "Volley works for a player without it");
		// Skirmisher's price: −20% beyond 20 blocks
		LivingEntity creeper = helper.spawn(EntityTypes.CREEPER, new BlockPos(1, 1, 1), EntitySpawnReason.NATURAL);
		p.setPos(creeper.getX() + 25, creeper.getY(), creeper.getZ());
		AbstractArrow far = arrow(level, p, true);
		far.setPos(creeper.getX(), creeper.getY() + 0.3, creeper.getZ() - 1.0);
		far.setDeltaMovement(0, 0, 1.5);
		creeper.hurtServer(level, level.damageSources().arrow(far, p), 10);
		check(helper, near(creeper.getHealth(), 12f), "a shot from 25 blocks left the creeper at %s, not 12", creeper.getHealth());
		helper.succeed();
	}

	@GameTest
	public void harpoonPullsTheTargetIn(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "harpoon", Specialization.HARPOONER, 30, NodeSlot.A1, 1);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TRIDENT));
		LivingEntity zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1), EntitySpawnReason.NATURAL);
		p.setPos(zombie.getX() + 10, zombie.getY(), zombie.getZ());
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Harpoon was not used with a trident");
		ThrownTrident trident = new ThrownTrident(level, p, new ItemStack(Items.TRIDENT));
		level.addFreshEntity(trident);
		check(helper, !Active.on(p, Specialization.HARPOONER), "Harpoon did not end with the throw");
		zombie.hurtServer(level, level.damageSources().trident(trident, p), 4);
		Vec3 toward = p.position().subtract(zombie.position()).horizontal().normalize();
		check(helper, zombie.getDeltaMovement().horizontal().dot(toward) > 0.5, "the zombie was not pulled toward the thrower: %s", zombie.getDeltaMovement());
		check(helper, zombie.hasEffect(MobEffects.SLOWNESS), "the harpooned zombie is not slowed");
		trident.discard();
		// Harpooner's price: bows deal −15%
		LivingEntity creeper = helper.spawn(EntityTypes.CREEPER, new BlockPos(3, 1, 3), EntitySpawnReason.NATURAL);
		AbstractArrow shot = arrow(level, p, true);
		shot.setPos(creeper.getX(), creeper.getY() + 0.3, creeper.getZ() - 1.0);
		shot.setDeltaMovement(0, 0, 1.5);
		creeper.hurtServer(level, level.damageSources().arrow(shot, p), 10);
		check(helper, near(creeper.getHealth(), 11.5f), "Harpooner's arrow left the creeper at %s, not 11.5", creeper.getHealth());
		helper.succeed();
	}

	// --- Defense ---------------------------------------------------------------------------------

	@GameTest
	public void bastionGuardsAlliesBehindAndBlocksAllAround(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "bastion", Specialization.BULWARK, 30, NodeSlot.A1, 1);
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		LivingEntity zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1), EntitySpawnReason.NATURAL);
		Vec3 at = zombie.position().add(4, 0, 4);
		p.setPos(at.x, at.y, at.z);
		p.setYRot(0);
		p.setYHeadRot(0);
		ServerPlayer behind = TestPlayers.survivor(helper, "behind");
		ServerPlayer alone = TestPlayers.survivor(helper, "alone");
		behind.setPos(at.x, at.y, at.z - 2);
		alone.setPos(at.x + 40, at.y, at.z);
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Bastion was not used with a shield");
		check(helper, Defense.blockAngle(p, Math.PI) == 0, "Bastion does not block from behind");
		behind.hurtServer(level, level.damageSources().mobAttack((net.minecraft.world.entity.Mob) zombie), 10);
		alone.hurtServer(level, level.damageSources().mobAttack((net.minecraft.world.entity.Mob) zombie), 10);
		check(helper, Math.abs(ratio(behind, alone) - 0.7) < 0.02, "the ally behind the Bastion took ×%s, not ×0.7", ratio(behind, alone));
		// Bulwark's price: +10% without a shield in hand
		ServerPlayer bare = specialist(helper, "bare", Specialization.BULWARK, 30);
		ServerPlayer plain = TestPlayers.survivor(helper, "plainb");
		bare.hurtServer(level, level.damageSources().fall(), 10);
		plain.hurtServer(level, level.damageSources().fall(), 10);
		check(helper, near(bare.getHealth(), 9f) && near(plain.getHealth(), 10f), "without a shield a Bulwark kept %s health, not 9", bare.getHealth());
		helper.succeed();
	}

	@GameTest
	public void unbreakableTakesLessAndStandsFirm(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "unbreak", Specialization.JUGGERNAUT, 30, NodeSlot.A1, 1);
		p.setSprinting(true);
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Unbreakable was not used");
		check(helper, !p.isSprinting() && !p.canSprint(), "you can sprint during Unbreakable");
		p.hurtServer(level, level.damageSources().fall(), 10);
		check(helper, near(p.getHealth(), 14f), "a fall of 10 during Unbreakable left %s health, not 14", p.getHealth());
		Specs.tick(level.getServer());
		check(helper, p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "knockback resistance is %s during Unbreakable",
				p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
		check(helper, Specs.knockback(p, 1.0) == 0, "Unbreakable still takes knockback");
		// Juggernaut's price: −10% speed in heavy armor, and Plating there only
		ServerPlayer plated = specialist(helper, "plated", Specialization.JUGGERNAUT, 30, NodeSlot.P1, 4);
		double speed = plated.getAttributeValue(Attributes.MOVEMENT_SPEED);
		Specs.tick(level.getServer());
		check(helper, near(plated.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 0), "Plating worked without heavy armor");
		heavyArmor(plated);
		Specs.tick(level.getServer());
		check(helper, near(plated.getAttributeValue(Attributes.MOVEMENT_SPEED), speed * 0.9), "heavy armor speed is %s, not 90%% of %s",
				plated.getAttributeValue(Attributes.MOVEMENT_SPEED), speed);
		check(helper, near(plated.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 2), "Plating IV gives %s toughness, not 2",
				plated.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		helper.succeed();
	}

	@GameTest
	public void secondWindCleansesAndHeals(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = specialist(helper, "wind", Specialization.RESILIENCE, 30, NodeSlot.A1, 1);
		p.getFoodData().setFoodLevel(17);
		p.setHealth(4);
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 0));
		p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 400, 0));
		p.addEffect(new MobEffectInstance(MobEffects.SPEED, 400, 0));
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Second Wind was not used");
		check(helper, !p.hasEffect(MobEffects.POISON) && !p.hasEffect(MobEffects.SLOWNESS), "Second Wind left a negative effect");
		check(helper, p.hasEffect(MobEffects.SPEED), "Second Wind took a good effect too");
		// Thick Blood V: negative effects 25% shorter; Antidote V: no poison at all
		ServerPlayer thick = specialist(helper, "thick", Specialization.RESILIENCE, 30, NodeSlot.P1, 5, NodeSlot.C1, 5);
		thick.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 0));
		thick.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 0));
		check(helper, thick.getEffect(MobEffects.WEAKNESS) != null && thick.getEffect(MobEffects.WEAKNESS).getDuration() == 300,
				"Thick Blood V gave weakness for %s ticks, not 300", thick.getEffect(MobEffects.WEAKNESS) == null ? 0 : thick.getEffect(MobEffects.WEAKNESS).getDuration());
		check(helper, !thick.hasEffect(MobEffects.POISON), "Antidote V let poison in");
		// Resilience's price: armor protects 10% less
		heavyArmor(thick);
		double armor = thick.getAttributeValue(Attributes.ARMOR);
		Specs.tick(helper.getLevel().getServer());
		check(helper, near(thick.getAttributeValue(Attributes.ARMOR), armor * 0.9), "Resilience's armor is %s, not 90%% of %s", thick.getAttributeValue(Attributes.ARMOR), armor);
		helper.runAfterDelay(110, () -> {
			check(helper, p.getHealth() >= 15.5f, "Second Wind healed to %s health, not 16", p.getHealth());
			helper.succeed();
		});
	}

	// --- Exploration -----------------------------------------------------------------------------

	@GameTest
	public void strideSpeedsUpAndTheWandererPaysWithMobDamage(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "stride", Specialization.WANDERER, 30, NodeSlot.A1, 1, NodeSlot.P1, 5);
		double base = p.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
		Specs.tick(level.getServer());
		check(helper, near(p.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.05), "Fleet V gives %s speed, not %s", p.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.05);
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Stride was not used");
		Specs.tick(level.getServer());
		check(helper, near(p.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.35), "Stride gives %s speed, not %s", p.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.35);
		check(helper, p.getAttributeValue(Attributes.JUMP_STRENGTH) > p.getAttributeBaseValue(Attributes.JUMP_STRENGTH), "Stride does not jump higher");
		// Fleet does not work in heavy armor; Stride does
		heavyArmor(p);
		Specs.tick(level.getServer());
		check(helper, near(p.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.30), "in heavy armor the speed is %s, not %s", p.getAttributeValue(Attributes.MOVEMENT_SPEED), base * 1.30);
		// Wanderer's price: +10% damage from mobs
		ServerPlayer wanderer = specialist(helper, "wpay", Specialization.WANDERER, 30);
		ServerPlayer plain = TestPlayers.survivor(helper, "wplain");
		LivingEntity zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1), EntitySpawnReason.NATURAL);
		wanderer.hurtServer(level, level.damageSources().mobAttack((net.minecraft.world.entity.Mob) zombie), 8);
		plain.hurtServer(level, level.damageSources().mobAttack((net.minecraft.world.entity.Mob) zombie), 8);
		check(helper, Math.abs(ratio(wanderer, plain) - 1.1) < 0.02, "a Wanderer took ×%s from a zombie, not ×1.1", ratio(wanderer, plain));
		helper.succeed();
	}

	@GameTest
	public void endureStopsHungerAndTheElements(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "endure", Specialization.SURVIVALIST, 30, NodeSlot.A1, 1, NodeSlot.C2, 5);
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Endure was not used");
		check(helper, Specs.exhaustion(p, 4f) == 0f, "Endure still costs hunger");
		p.hurtServer(level, level.damageSources().lava(), 8);
		check(helper, near(p.getHealth(), 18f), "lava of 8 during Endure left %s health, not 18", p.getHealth());
		Specs.tick(level.getServer());
		check(helper, near(p.getAttributeValue(Attributes.OXYGEN_BONUS), 1.0), "Gills V gives %s oxygen bonus, not 1", p.getAttributeValue(Attributes.OXYGEN_BONUS));
		// Survivalist's price: −10% while sprinting
		ServerPlayer runner = specialist(helper, "runner", Specialization.SURVIVALIST, 30);
		runner.setSprinting(true);
		double sprint = runner.getAttributeValue(Attributes.MOVEMENT_SPEED);
		Specs.tick(level.getServer());
		check(helper, near(runner.getAttributeValue(Attributes.MOVEMENT_SPEED), sprint * 0.9), "a Survivalist sprints at %s, not 90%% of %s",
				runner.getAttributeValue(Attributes.MOVEMENT_SPEED), sprint);
		helper.succeed();
	}

	@GameTest
	public void daredevilTakesNoFallDamage(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = specialist(helper, "daredevil", Specialization.ACROBAT, 30, NodeSlot.A1, 1, NodeSlot.P1, 5);
		Specs.tick(level.getServer());
		check(helper, near(p.getAttributeValue(Attributes.SAFE_FALL_DISTANCE), p.getAttributeBaseValue(Attributes.SAFE_FALL_DISTANCE) + 2.5),
				"Featherfall V gives %s safe fall distance", p.getAttributeValue(Attributes.SAFE_FALL_DISTANCE));
		check(helper, AbilityService.use(p, false) == AbilityService.Result.USED, "Daredevil was not used");
		check(helper, !p.hurtServer(level, level.damageSources().fall(), 10) && p.getHealth() == 20f, "a fall during Daredevil hurt: %s health", p.getHealth());
		// Acrobat's price: +25% knockback
		check(helper, near(Specs.knockback(p, 1.0), 1.25), "an Acrobat takes ×%s knockback, not ×1.25", Specs.knockback(p, 1.0));
		helper.succeed();
	}

	@GameTest
	public void aDormantSpecializationDoesNothing(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		// Chosen at 30, then back to level 20: kept, but neither its nodes nor its price count
		ServerPlayer p = specialist(helper, "dormant", Specialization.WANDERER, 30, NodeSlot.P1, 5);
		Progression.setLevel(PlayerStore.get(p.getUUID()), Specialization.WANDERER.skill, 20);
		double base = p.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
		Specs.tick(level.getServer());
		check(helper, near(p.getAttributeValue(Attributes.MOVEMENT_SPEED), base), "a dormant Wanderer still gets Fleet");
		check(helper, !Specs.has(p, Specialization.WANDERER), "a dormant Wanderer still counts as one");
		helper.succeed();
	}
}
