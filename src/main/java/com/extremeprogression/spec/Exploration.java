package com.extremeprogression.spec;

import static com.extremeprogression.skill.Specialization.ACROBAT;
import static com.extremeprogression.skill.Specialization.SURVIVALIST;
import static com.extremeprogression.skill.Specialization.WANDERER;
import static com.extremeprogression.spec.Specs.rank;
import static com.extremeprogression.tree.NodeSlot.C1;
import static com.extremeprogression.tree.NodeSlot.C2;
import static com.extremeprogression.tree.NodeSlot.P1;
import static com.extremeprogression.tree.NodeSlot.P2;
import static com.extremeprogression.tree.NodeSlot.P3;
import static com.extremeprogression.tree.NodeSlot.P4;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.ability.AbilityService;
import com.extremeprogression.trunk.Trunks;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Exploration's three specializations (design §2, skill 8). Their passives and choice nodes do not work in heavy armor
 * (see {@link Specs#rank}); the abilities do.
 * <ul>
 * <li><b>Wanderer</b>: Stride; Fleet, Explorer's Eye, Rough Terrain, Landmark Hunter; Step Up or Tireless. Price: +10%
 * damage from mobs.</li>
 * <li><b>Survivalist</b>: Endure; Slow Metabolism, Weatherproof, Hearty Eater, Hunger Pangs; Fireproof or Gills.
 * Price: −10% sprint speed.</li>
 * <li><b>Acrobat</b>: Daredevil; Featherfall, Spring, Tumble, Evasive; Dodge or Climber. Price: +25% knockback
 * taken.</li>
 * </ul>
 */
public final class Exploration {
	private Exploration() {}

	private static final Identifier FLEET = ExtremeProgression.id("spec/fleet");
	private static final Identifier ROUGH_TERRAIN = ExtremeProgression.id("spec/rough_terrain");
	private static final Identifier STEP_UP = ExtremeProgression.id("spec/step_up");
	private static final Identifier STRIDE_SPEED = ExtremeProgression.id("spec/stride_speed");
	private static final Identifier STRIDE_JUMP = ExtremeProgression.id("spec/stride_jump");
	private static final Identifier GILLS = ExtremeProgression.id("spec/gills");
	private static final Identifier SURVIVALIST_PRICE = ExtremeProgression.id("spec/survivalist_price");
	private static final Identifier FEATHERFALL = ExtremeProgression.id("spec/featherfall");
	private static final Identifier SPRING = ExtremeProgression.id("spec/spring");
	private static final Identifier DAREDEVIL_JUMP = ExtremeProgression.id("spec/daredevil_jump");

	static void register() {
		// Stride: 10 s (+2 s a rank); Endure: 20 s (+4 s a rank); Daredevil: 15 s (+3 s a rank)
		AbilityService.register(WANDERER, (p, rank) -> Active.start(p, WANDERER, rank, 200 + 40 * (rank - 1)));
		AbilityService.register(SURVIVALIST, (p, rank) -> Active.start(p, SURVIVALIST, rank, 400 + 80 * (rank - 1)));
		AbilityService.register(ACROBAT, (p, rank) -> {
			Active.start(p, ACROBAT, rank, 300 + 60 * (rank - 1));
			((ServerLevel) p.level()).playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.8f, 1.2f);
		});
	}

	/** Fire, lava, freezing and drowning: what Endure shields you from. */
	private static boolean elements(DamageSource source) {
		return source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_FREEZING) || source.is(DamageTypeTags.IS_DROWNING);
	}

	// --- Hits ------------------------------------------------------------------------------------

	/** Daredevil: no fall damage. Evasive: a 2% chance a rank to escape a projectile. Dodge: 2% a rank, a hit up close while sprinting. */
	static boolean allow(LivingEntity target, DamageSource source) {
		if (!(target instanceof ServerPlayer v)) {
			return true;
		}
		if (source.is(DamageTypeTags.IS_FALL) && Active.on(v, ACROBAT)) {
			return false;
		}
		if (source.is(DamageTypeTags.IS_PROJECTILE) && Specs.roll(v, 0.02 * rank(v, ACROBAT, P4))) {
			return false;
		}
		boolean melee = source.getEntity() instanceof LivingEntity && source.getDirectEntity() == source.getEntity();
		return !(melee && v.isSprinting() && Specs.roll(v, 0.02 * rank(v, ACROBAT, C1)));
	}

	static float incoming(LivingEntity target, DamageSource source, float amount) {
		if (!(target instanceof ServerPlayer v)) {
			return amount;
		}
		float out = amount;
		// Wanderer's price
		if (source.getEntity() instanceof Mob && Specs.has(v, WANDERER)) {
			out *= 1.10f;
		}
		// Weatherproof: −4% a rank from the elements, cactus and magma; Fireproof: −10% a rank from fire and lava
		if (elements(source) || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.HOT_FLOOR)) {
			out *= 1 - 0.04f * rank(v, SURVIVALIST, P2);
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			out *= 1 - 0.10f * rank(v, SURVIVALIST, C1);
		}
		// Endure: 75% less from the elements
		if (elements(source) && Active.on(v, SURVIVALIST)) {
			out *= 0.25f;
		}
		// Hunger Pangs: starving hurts 10% less a rank
		if (source.is(DamageTypes.STARVE)) {
			out *= 1 - 0.10f * rank(v, SURVIVALIST, P4);
		}
		return out;
	}

	/** Acrobat's price: +25% knockback. */
	static double knockback(LivingEntity target, double strength) {
		return Specs.has(target, ACROBAT) ? strength * 1.25 : strength;
	}

	/** Tumble (Acrobat): Roll's chance (Exploration 20) +3% a rank. */
	public static double tumble(LivingEntity v) {
		return 0.03 * rank(v, ACROBAT, P3);
	}

	// --- Hunger, food and XP ---------------------------------------------------------------------

	/** Endure: no hunger. Slow Metabolism: 3% less a rank. Tireless: sprinting costs 20% less a rank. */
	static float exhaustion(Player player, float amount) {
		if (Specs.active(player, SURVIVALIST)) {
			return 0;
		}
		float out = amount * (1 - 0.03f * rank(player, SURVIVALIST, P1));
		if (player.isSprinting()) {
			out *= 1 - 0.20f * rank(player, WANDERER, C2);
		}
		return Math.max(0, out);
	}

	/** Hearty Eater (Survivalist): food gives 5% more saturation a rank. */
	public static void ate(ServerPlayer p, ItemStack food) {
		int r = rank(p, SURVIVALIST, P3);
		FoodProperties props = food.get(DataComponents.FOOD);
		if (r > 0 && props != null) {
			FoodData data = p.getFoodData();
			data.setSaturation(Math.min(data.getFoodLevel(), data.getSaturationLevel() + props.saturation() * 0.05f * r));
		}
	}

	/** Explorer's Eye (Wanderer): +5% XP a rank from new and revisited chunks. */
	public static double chunkXp(ServerPlayer p) {
		return 1 + 0.05 * rank(p, WANDERER, P2);
	}

	/** Landmark Hunter (Wanderer): +5% XP a rank from new biomes and structures. */
	public static double landmarkXp(ServerPlayer p) {
		return 1 + 0.05 * rank(p, WANDERER, P4);
	}

	/**
	 * Climber (Acrobat): ladders and vines are climbed 10% faster a rank. Vanilla moves a climber up at exactly 0.2 a
	 * tick; on the client, for its own player, where movement is worked out.
	 */
	public static Vec3 climb(LivingEntity entity, Vec3 movement) {
		if (movement.y == 0.2 && entity instanceof Player p && entity.onClimbable()) {
			int r = rank(p, ACROBAT, C2);
			if (r > 0) {
				return new Vec3(movement.x, 0.2 * (1 + 0.10 * r), movement.z);
			}
		}
		return movement;
	}

	// --- Every tick ------------------------------------------------------------------------------

	static void tick(ServerPlayer p) {
		AttributeModifier.Operation base = AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
		AttributeModifier.Operation add = AttributeModifier.Operation.ADD_VALUE;
		// Wanderer: Fleet +1% speed a rank; Rough Terrain: soul sand, mud and honey slow you 10% less a rank; Step Up
		// +0.08 step height a rank (a full block at rank V)
		Trunks.modifier(p, Attributes.MOVEMENT_SPEED, FLEET, 0.01 * rank(p, WANDERER, P1), base);
		Trunks.modifier(p, Attributes.MOVEMENT_EFFICIENCY, ROUGH_TERRAIN, 0.10 * rank(p, WANDERER, P3), add);
		Trunks.modifier(p, Attributes.STEP_HEIGHT, STEP_UP, 0.08 * rank(p, WANDERER, C1), add);
		// Stride: +30% speed and higher jumps
		boolean stride = Active.on(p, WANDERER);
		Trunks.modifier(p, Attributes.MOVEMENT_SPEED, STRIDE_SPEED, stride ? 0.30 : 0, base);
		Trunks.modifier(p, Attributes.JUMP_STRENGTH, STRIDE_JUMP, stride ? 0.10 : 0, add);
		// Survivalist: Gills +0.2 oxygen bonus a rank (twice as long underwater at V); the price: −10% while sprinting
		Trunks.modifier(p, Attributes.OXYGEN_BONUS, GILLS, 0.20 * rank(p, SURVIVALIST, C2), add);
		Trunks.modifier(p, Attributes.MOVEMENT_SPEED, SURVIVALIST_PRICE, p.isSprinting() && Specs.has(p, SURVIVALIST) ? -0.10 : 0,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		// Acrobat: Featherfall +0.5 block of safe fall a rank; Spring +2% jump strength a rank; Daredevil +50% jump height
		Trunks.modifier(p, Attributes.SAFE_FALL_DISTANCE, FEATHERFALL, 0.5 * rank(p, ACROBAT, P1), add);
		Trunks.modifier(p, Attributes.JUMP_STRENGTH, SPRING, 0.02 * rank(p, ACROBAT, P2), base);
		Trunks.modifier(p, Attributes.JUMP_STRENGTH, DAREDEVIL_JUMP, Active.on(p, ACROBAT) ? 0.10 : 0, add);
	}
}
