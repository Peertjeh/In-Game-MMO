package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.CombatTests.swing;
import static com.extremeprogression.gametest.SkeletonTests.check;
import static com.extremeprogression.gametest.SpecTests.specialist;

import com.extremeprogression.config.Config;
import com.extremeprogression.skill.Specialization;
import com.extremeprogression.spec.Active;
import com.extremeprogression.spec.Defense;
import com.extremeprogression.spec.Exploration;
import com.extremeprogression.spec.Melee;
import com.extremeprogression.spec.Ranged;
import com.extremeprogression.spec.Specs;
import com.extremeprogression.tree.NodeSlot;
import com.extremeprogression.trunk.Trunks;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 6, part 1: the passives and choice nodes whose effect is fixed (not a chance), at rank V or the rank given.
 * A test player does not tick by itself, so its gear's own attributes are not on it unless a test ticks it; the
 * specializations' attributes come from {@link Specs#tick}. Every player here is at level 30, so the trunks count too.
 */
public class SpecNodeTests {
	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1e-3;
	}

	private static void heavyArmor(LivingEntity e) {
		e.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		e.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		e.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
	}

	private static AbstractArrow arrow(ServerLevel level, ServerPlayer owner, LivingEntity at) {
		AbstractArrow arrow = EntityTypes.ARROW.create(level, EntitySpawnReason.TRIGGERED);
		arrow.setOwner(owner);
		arrow.setCritArrow(true);
		// flying at the target's body, below its eyes (no headshot)
		arrow.setPos(at.getX(), at.getY() + 0.3, at.getZ() - 1.0);
		arrow.setDeltaMovement(0, 0, 1.5);
		return arrow;
	}

	private static LivingEntity creeper(GameTestHelper helper, int x, int z) {
		return helper.spawn(EntityTypes.CREEPER, new BlockPos(x, 1, z), EntitySpawnReason.NATURAL);
	}

	@GameTest(maxTicks = 100)
	public void meleeNodes(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		// Precision V: a critical blade hit +20% (not fully charged, so Finishing Blow stays out)
		ServerPlayer blade = specialist(helper, "precise", Specialization.BLADE, 30, NodeSlot.P4, 5, NodeSlot.C2, 5);
		blade.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		LivingEntity a = creeper(helper, 1, 1);
		blade.fallDistance = 1.0;
		blade.setOnGround(false);
		swing(blade, a, false);
		a.hurtServer(level, level.damageSources().playerAttack(blade), 10);
		check(helper, near(a.getHealth(), 8f), "Precision V left the creeper at %s, not 8", a.getHealth());
		// Quick Cut V: +10% attack speed with a sword
		double speed = blade.getAttributeBaseValue(Attributes.ATTACK_SPEED);
		Specs.tick(level.getServer());
		check(helper, near(blade.getAttributeValue(Attributes.ATTACK_SPEED), speed * 1.10), "Quick Cut V gives %s attack speed, not %s",
				blade.getAttributeValue(Attributes.ATTACK_SPEED), speed * 1.10);

		// Sunder V: a full hit on an armored target +15%, against a Crusher without it
		ServerPlayer sunder = specialist(helper, "sunder", Specialization.CRUSHER, 30, NodeSlot.P3, 5, NodeSlot.C2, 5);
		ServerPlayer plain = specialist(helper, "nosunder", Specialization.CRUSHER, 30);
		sunder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
		plain.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
		LivingEntity z1 = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 1), EntitySpawnReason.NATURAL);
		LivingEntity z2 = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 3), EntitySpawnReason.NATURAL);
		swing(sunder, z1, true);
		z1.hurtServer(level, level.damageSources().playerAttack(sunder), 10);
		swing(plain, z2, true);
		z2.hurtServer(level, level.damageSources().playerAttack(plain), 10);
		double share = (20 - z1.getHealth()) / (20 - z2.getHealth());
		check(helper, Math.abs(share - 1.15) < 0.01, "Sunder V came to ×%s, not ×1.15", share);
		// Cleave V: an axe disables a shield 5 seconds longer
		check(helper, near(Melee.disableSeconds(sunder, 5f), 10f), "Cleave V disables a shield for %s s, not 10", Melee.disableSeconds(sunder, 5f));

		// Brawler: Hardened Knuckles V and Iron Fist V +3 damage; Quick Hands V +20% attack speed
		ServerPlayer fists = specialist(helper, "fists", Specialization.BRAWLER, 30, NodeSlot.P1, 5, NodeSlot.P2, 5, NodeSlot.C1, 5, NodeSlot.P3, 5);
		double damage = fists.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
		double fistSpeed = fists.getAttributeBaseValue(Attributes.ATTACK_SPEED);
		Specs.tick(level.getServer());
		check(helper, near(fists.getAttributeValue(Attributes.ATTACK_DAMAGE), damage + 3), "fists deal %s, not %s + 3", fists.getAttributeValue(Attributes.ATTACK_DAMAGE), damage);
		check(helper, near(fists.getAttributeValue(Attributes.ATTACK_SPEED), fistSpeed * 1.2), "Quick Hands V gives %s attack speed",
				fists.getAttributeValue(Attributes.ATTACK_SPEED));
		// Combo V: a punch within 2 seconds of the last is a step, +10% each
		LivingEntity b = creeper(helper, 5, 1);
		LivingEntity c = creeper(helper, 5, 3);
		swing(fists, b, false);
		b.hurtServer(level, level.damageSources().playerAttack(fists), 10);
		helper.runAfterDelay(20, () -> {
			swing(fists, c, false);
			c.hurtServer(level, level.damageSources().playerAttack(fists), 10);
			check(helper, near(b.getHealth(), 10f) && near(c.getHealth(), 9f), "Combo V: the punches left %s and %s, not 10 and 9", b.getHealth(), c.getHealth());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void rangedNodes(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		// Marksman V and Long Shot V from 30 blocks: 10 × 1.05 (Keen Eye) × 1.10 × 1.15
		ServerPlayer sniper = specialist(helper, "marks", Specialization.SNIPER, 30, NodeSlot.P1, 5, NodeSlot.P2, 5, NodeSlot.P3, 5, NodeSlot.C1, 5);
		LivingEntity far = creeper(helper, 1, 1);
		sniper.setPos(far.getX() + 30, far.getY(), far.getZ());
		far.hurtServer(level, level.damageSources().arrow(arrow(level, sniper, far), sniper), 10);
		check(helper, near(far.getHealth(), 20 - 10 * 1.05 * 1.10 * 1.15), "Marksman and Long Shot left the creeper at %s", far.getHealth());
		// Hunter's Mark: a full hit makes the target glow
		check(helper, far.hasEffect(MobEffects.GLOWING), "Hunter's Mark did not mark the target");
		// Piercing V: every full shot pierces one more target
		AbstractArrow pierce = arrow(level, sniper, far);
		level.addFreshEntity(pierce);
		check(helper, pierce.getPierceLevel() == 1, "Piercing V gave a pierce of %s, not 1", pierce.getPierceLevel());
		pierce.discard();

		// Close Range V within 15 blocks; Hit and Run V: +15% speed after a hit
		ServerPlayer skirm = specialist(helper, "close", Specialization.SKIRMISHER, 30, NodeSlot.P1, 5, NodeSlot.P2, 5, NodeSlot.P3, 5, NodeSlot.C1, 5);
		LivingEntity near = creeper(helper, 3, 3);
		skirm.setPos(near.getX() + 10, near.getY(), near.getZ());
		near.hurtServer(level, level.damageSources().arrow(arrow(level, skirm, near), skirm), 10);
		check(helper, near(near.getHealth(), 20 - 10 * 1.05 * 1.10), "Close Range V left the creeper at %s", near.getHealth());
		double walk = skirm.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
		Specs.tick(level.getServer());
		check(helper, near(skirm.getAttributeValue(Attributes.MOVEMENT_SPEED), walk * 1.15), "Hit and Run V gives %s speed", skirm.getAttributeValue(Attributes.MOVEMENT_SPEED));
		// Quick Draw V: a bow drawn for 20 ticks counts 4 more (Steady Hand 1)
		ItemStack bow = new ItemStack(Items.BOW);
		int left = bow.getUseDuration(skirm) - 20;
		check(helper, Trunks.bowTimeLeft(bow, skirm, left) == left - 4, "Quick Draw V counted the draw as %s ticks", bow.getUseDuration(skirm) - Trunks.bowTimeLeft(bow, skirm, left));
		// Quick Reload V: a crossbow loads 25% faster
		ServerPlayer slow = specialist(helper, "reload", Specialization.SKIRMISHER, 30);
		ItemStack crossbow = new ItemStack(Items.CROSSBOW);
		int vanilla = CrossbowItem.getChargeDuration(crossbow, slow);
		check(helper, CrossbowItem.getChargeDuration(crossbow, skirm) == Math.round(vanilla * 0.75f), "Quick Reload V loads in %s ticks, vanilla %s",
				CrossbowItem.getChargeDuration(crossbow, skirm), vanilla);
		// Running Shot V: drawing a bow does not slow you; Deadeye zooms while drawing
		ServerPlayer runner = specialist(helper, "running", Specialization.SKIRMISHER, 30, NodeSlot.C2, 5);
		runner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
		runner.startUsingItem(InteractionHand.MAIN_HAND);
		check(helper, near(Ranged.runningShot(runner), 5f), "Running Shot V moves you at ×%s of the slowed speed, not ×5", Ranged.runningShot(runner));
		ServerPlayer zoom = specialist(helper, "zoom", Specialization.SNIPER, 30, NodeSlot.A1, 1);
		zoom.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
		zoom.startUsingItem(InteractionHand.MAIN_HAND);
		check(helper, !Ranged.zoomed(zoom), "the view zooms without Deadeye");
		Active.start(zoom, Specialization.SNIPER, 1, 160);
		check(helper, Ranged.zoomed(zoom), "Deadeye does not zoom while drawing");

		// Harpooner: Trident Arm V (10 × 1.05 × 1.10), Strong Throw V (+20% speed), Barbed (slowed)
		ServerPlayer harp = specialist(helper, "arm", Specialization.HARPOONER, 30, NodeSlot.P1, 5, NodeSlot.P2, 5, NodeSlot.P3, 5);
		LivingEntity target = creeper(helper, 5, 5);
		ThrownTrident trident = new ThrownTrident(level, harp, new ItemStack(Items.TRIDENT));
		trident.setDeltaMovement(0, 0, 2);
		level.addFreshEntity(trident);
		check(helper, near(trident.getDeltaMovement().z, 2.4), "Strong Throw V: the trident flies at %s, not 2.4", trident.getDeltaMovement().z);
		target.hurtServer(level, level.damageSources().trident(trident, harp), 10);
		check(helper, near(target.getHealth(), 20 - 10 * 1.05 * 1.10), "Trident Arm V left the creeper at %s", target.getHealth());
		check(helper, target.hasEffect(MobEffects.SLOWNESS), "Barbed did not slow the target");
		trident.discard();
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void defenseNodes(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		// Recovery V: a disabled shield is ready in half the time
		ServerPlayer bulwark = specialist(helper, "recover", Specialization.BULWARK, 30, NodeSlot.P4, 5, NodeSlot.C1, 5);
		check(helper, near(Defense.disabledFor(bulwark, 5f), 2.5f), "Recovery V: disabled for %s s, not 2.5", Defense.disabledFor(bulwark, 5f));
		// Mirror Shield V: 25% of a blocked shot goes back to the shooter
		LivingEntity skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(1, 1, 1), EntitySpawnReason.NATURAL);
		AbstractArrow shot = EntityTypes.ARROW.create(level, EntitySpawnReason.TRIGGERED);
		shot.setOwner(skeleton);
		Specs.after(bulwark, level.damageSources().arrow(shot, skeleton), 8, 0, true, 0);
		check(helper, skeleton.getHealth() < 20f, "Mirror Shield sent nothing back to the skeleton");

		// Juggernaut in heavy armor: Iron Skin V −10% up close (and Hardened −3%), against a plain player in the same armor
		ServerPlayer jugg = specialist(helper, "skin", Specialization.JUGGERNAUT, 30, NodeSlot.P2, 5, NodeSlot.P3, 5, NodeSlot.P4, 5, NodeSlot.C1, 5);
		ServerPlayer plain = TestPlayers.survivor(helper, "skinless");
		heavyArmor(jugg);
		heavyArmor(plain);
		Mob zombie = (Mob) helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 3), EntitySpawnReason.NATURAL);
		jugg.hurtServer(level, level.damageSources().mobAttack(zombie), 8);
		plain.hurtServer(level, level.damageSources().mobAttack(zombie), 8);
		double share = (20 - jugg.getHealth()) / (20 - plain.getHealth());
		check(helper, Math.abs(share - 0.97 * 0.90) < 0.01, "Iron Skin V came to ×%s, not ×0.873", share);
		// Spikes V: 10% of the hit went back to the zombie
		check(helper, zombie.getHealth() < 20f, "Spikes sent nothing back");
		// Unyielding V +20% knockback resistance, Anchor V +100% while sneaking
		Specs.tick(level.getServer());
		check(helper, near(jugg.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.20), "Unyielding V gives %s", jugg.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
		ServerPlayer anchor = specialist(helper, "anchor", Specialization.JUGGERNAUT, 30, NodeSlot.C2, 5);
		heavyArmor(anchor);
		anchor.setShiftKeyDown(true);
		Specs.tick(level.getServer());
		check(helper, anchor.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "Anchor V gives %s while sneaking", anchor.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
		// Last Stand V: below 30% health, 15% less
		ServerPlayer last = specialist(helper, "last", Specialization.JUGGERNAUT, 30, NodeSlot.P4, 5);
		heavyArmor(last);
		last.setHealth(5);
		last.hurtServer(level, level.damageSources().fall(), 2);
		check(helper, near(last.getHealth(), 3.3f), "Last Stand V: a fall of 2 at 5 health left %s, not 3.3", last.getHealth());

		// Resilience: Steady Heart V heals 15% more; Cool Blood V burns out twice as fast
		ServerPlayer heart = specialist(helper, "heart", Specialization.RESILIENCE, 30, NodeSlot.P3, 5, NodeSlot.C2, 5);
		heart.setHealth(10);
		heart.heal(4);
		check(helper, near(heart.getHealth(), 14.6f), "Steady Heart V healed to %s, not 14.6", heart.getHealth());
		heart.setRemainingFireTicks(100);
		for (int i = 0; i < 10; i++) {
			Specs.tick(level.getServer());
		}
		check(helper, heart.getRemainingFireTicks() == 90, "Cool Blood V: %s fire ticks left after 10 extra, not 90", heart.getRemainingFireTicks());
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void explorationNodes(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		// Wanderer: Explorer's Eye V and Landmark Hunter V +25% XP; Rough Terrain V; Step Up V a full block; Tireless V
		ServerPlayer wanderer = specialist(helper, "eye", Specialization.WANDERER, 30, NodeSlot.P2, 5, NodeSlot.P3, 5, NodeSlot.P4, 5, NodeSlot.C1, 5);
		check(helper, near(Exploration.chunkXp(wanderer), 1.25) && near(Exploration.landmarkXp(wanderer), 1.25), "Explorer's Eye and Landmark Hunter are not +25%%");
		double step = wanderer.getAttributeBaseValue(Attributes.STEP_HEIGHT);
		Specs.tick(level.getServer());
		check(helper, near(wanderer.getAttributeValue(Attributes.MOVEMENT_EFFICIENCY), 0.5), "Rough Terrain V gives %s movement efficiency",
				wanderer.getAttributeValue(Attributes.MOVEMENT_EFFICIENCY));
		check(helper, near(wanderer.getAttributeValue(Attributes.STEP_HEIGHT), step + 0.4), "Step Up V gives a step of %s", wanderer.getAttributeValue(Attributes.STEP_HEIGHT));
		ServerPlayer tireless = specialist(helper, "tireless", Specialization.WANDERER, 30, NodeSlot.C2, 5);
		tireless.setSprinting(true);
		check(helper, Specs.exhaustion(tireless, 1f) == 0f, "Tireless V: sprinting still costs %s", Specs.exhaustion(tireless, 1f));

		// Survivalist: Slow Metabolism V −15% hunger; Weatherproof V and Fireproof V on fire; Hunger Pangs V; Hearty Eater V
		ServerPlayer survivor = specialist(helper, "weather", Specialization.SURVIVALIST, 30, NodeSlot.P1, 5, NodeSlot.P2, 5, NodeSlot.P3, 5, NodeSlot.P4, 5, NodeSlot.C1, 5);
		check(helper, near(Specs.exhaustion(survivor, 1f), 0.85), "Slow Metabolism V: hunger ×%s, not ×0.85", Specs.exhaustion(survivor, 1f));
		survivor.hurtServer(level, level.damageSources().inFire(), 10);
		check(helper, near(survivor.getHealth(), 16f), "fire of 10 left %s health, not 16 (×0.8 × 0.5)", survivor.getHealth());
		ServerPlayer hungry = specialist(helper, "pangs", Specialization.SURVIVALIST, 30, NodeSlot.P4, 5);
		hungry.hurtServer(level, level.damageSources().starve(), 2);
		check(helper, near(hungry.getHealth(), 19f), "Hunger Pangs V: starving for 2 left %s health, not 19", hungry.getHealth());
		ItemStack bread = new ItemStack(Items.BREAD);
		survivor.getFoodData().setFoodLevel(10);
		survivor.getFoodData().setSaturation(0);
		Exploration.ate(survivor, bread);
		float expected = bread.get(DataComponents.FOOD).saturation() * 0.25f;
		check(helper, near(survivor.getFoodData().getSaturationLevel(), expected), "Hearty Eater V gave %s saturation, not %s", survivor.getFoodData().getSaturationLevel(), expected);

		// Acrobat: Spring V +10% jump strength; Tumble V +15% to Roll; Climber V +50% up a ladder
		ServerPlayer acrobat = specialist(helper, "spring", Specialization.ACROBAT, 30, NodeSlot.P2, 5, NodeSlot.P3, 5, NodeSlot.C2, 5);
		double jump = acrobat.getAttributeBaseValue(Attributes.JUMP_STRENGTH);
		Specs.tick(level.getServer());
		check(helper, near(acrobat.getAttributeValue(Attributes.JUMP_STRENGTH), jump * 1.10), "Spring V gives %s jump strength", acrobat.getAttributeValue(Attributes.JUMP_STRENGTH));
		check(helper, near(Exploration.tumble(acrobat), 0.15), "Tumble V adds %s to Roll", Exploration.tumble(acrobat));
		BlockPos ladder = new BlockPos(1, 1, 1);
		helper.setBlock(ladder, Blocks.LADDER);
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(ladder));
		acrobat.setPos(at.x, at.y, at.z);
		check(helper, acrobat.onClimbable(), "the acrobat is not on the ladder");
		check(helper, near(Exploration.climb(acrobat, new Vec3(0, 0.2, 0)).y, 0.3), "Climber V climbs at %s, not 0.3", Exploration.climb(acrobat, new Vec3(0, 0.2, 0)).y);
		helper.succeed();
	}
}
