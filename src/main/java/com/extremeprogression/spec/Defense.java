package com.extremeprogression.spec;

import static com.extremeprogression.skill.Specialization.BULWARK;
import static com.extremeprogression.skill.Specialization.JUGGERNAUT;
import static com.extremeprogression.skill.Specialization.RESILIENCE;
import static com.extremeprogression.spec.Specs.rank;
import static com.extremeprogression.tree.NodeSlot.C1;
import static com.extremeprogression.tree.NodeSlot.C2;
import static com.extremeprogression.tree.NodeSlot.P1;
import static com.extremeprogression.tree.NodeSlot.P2;
import static com.extremeprogression.tree.NodeSlot.P3;
import static com.extremeprogression.tree.NodeSlot.P4;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.ability.AbilityService;
import com.extremeprogression.mixin.FoodDataAccess;
import com.extremeprogression.trunk.Trunks;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Defense's three specializations (design §2, skill 3).
 * <ul>
 * <li><b>Bulwark</b> (shield): Bastion; Sturdy Shield, Brace, Guardian, Recovery; Mirror Shield or Shield Bash. Price:
 * +10% damage taken without a shield in hand.</li>
 * <li><b>Juggernaut</b> (heavy armor, for every node): Unbreakable; Plating, Iron Skin, Unyielding, Last Stand; Spikes
 * or Anchor. Price: −10% movement speed in heavy armor.</li>
 * <li><b>Resilience</b>: Second Wind; Thick Blood, Quick Recovery, Steady Heart, Shake It Off; Antidote or Cool Blood.
 * Price: armor protects 10% less.</li>
 * </ul>
 * Allies are other players and tamed animals.
 */
public final class Defense {
	private Defense() {}

	private static final Identifier BRACE = ExtremeProgression.id("spec/brace");
	private static final Identifier PLATING = ExtremeProgression.id("spec/plating");
	private static final Identifier UNYIELDING = ExtremeProgression.id("spec/unyielding");
	private static final Identifier ANCHOR = ExtremeProgression.id("spec/anchor");
	private static final Identifier UNBREAKABLE = ExtremeProgression.id("spec/unbreakable");
	private static final Identifier JUGGERNAUT_PRICE = ExtremeProgression.id("spec/juggernaut_price");
	private static final Identifier RESILIENCE_PRICE = ExtremeProgression.id("spec/resilience_price");

	/** Quick Recovery: the fraction of an extra regeneration tick carried over. */
	private static final Map<Player, Double> RECOVERY = new WeakHashMap<>();
	/** Cool Blood: the fraction of an extra burning tick carried over. */
	private static final Map<Player, Double> COOLING = new WeakHashMap<>();

	static void register() {
		// Bastion: 6 s (+1 s a rank)
		AbilityService.register(BULWARK, (p, rank) -> Active.start(p, BULWARK, rank, 120 + 20 * (rank - 1)));
		// Unbreakable: 8 s (+1 s a rank)
		AbilityService.register(JUGGERNAUT, (p, rank) -> {
			Active.start(p, JUGGERNAUT, rank, 160 + 20 * (rank - 1));
			p.setSprinting(false);
		});
		// Second Wind: every negative effect gone, and 6 hearts (+1 a rank) healed over 5 seconds
		AbilityService.register(RESILIENCE, (p, rank) -> {
			List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> bad = new ArrayList<>();
			for (MobEffectInstance e : p.getActiveEffects()) {
				if (e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
					bad.add(e.getEffect());
				}
			}
			bad.forEach(p::removeEffect);
			Active.start(p, RESILIENCE, rank, 100).value = 12 + 2 * (rank - 1);
			((ServerLevel) p.level()).playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5f, 1.4f);
		});
	}

	private static boolean heavy(LivingEntity e) {
		return Trunks.heavyArmor(e);
	}

	/** A Juggernaut node's rank: they work in heavy armor only. */
	private static double plate(LivingEntity e, com.extremeprogression.tree.NodeSlot slot) {
		return heavy(e) ? rank(e, JUGGERNAUT, slot) : 0;
	}

	private static boolean melee(DamageSource source) {
		return source.getEntity() instanceof LivingEntity && source.getDirectEntity() == source.getEntity();
	}

	// --- Hits ------------------------------------------------------------------------------------

	static float incoming(LivingEntity target, DamageSource source, float amount) {
		float out = amount;
		Entity attacker = source.getEntity();
		if (target instanceof ServerPlayer v) {
			// Bulwark's price
			if (Specs.has(v, BULWARK) && !Specs.holdsShield(v)) {
				out *= 1.10f;
			}
			// Iron Skin: −2% a rank from hits up close; Last Stand: −3% a rank below 30% health
			if (melee(source)) {
				out *= 1 - 0.02f * plate(v, P2);
			}
			if (v.getHealth() < v.getMaxHealth() * 0.3f) {
				out *= 1 - 0.03f * plate(v, P4);
			}
			// Unbreakable: 40% less
			if (Active.on(v, JUGGERNAUT)) {
				out *= 0.60f;
			}
		}
		if (attacker != null && attacker != target && (target instanceof Player || target instanceof OwnableEntity o && o.getOwner() instanceof Player)) {
			out *= 1 - protection(target, attacker);
		}
		return out;
	}

	/**
	 * What Bulwarks nearby take off a hit on an ally: Bastion 30% for one standing up to 3 blocks behind them, Guardian 2%
	 * a rank from mobs within 4 blocks while their shield is raised. The best one counts.
	 */
	private static float protection(LivingEntity ally, Entity attacker) {
		float best = 0;
		for (ServerPlayer b : ((ServerLevel) ally.level()).players()) {
			if (b == ally || b.distanceToSqr(ally) > 16 || !Specs.has(b, BULWARK)) {
				continue;
			}
			if (Active.on(b, BULWARK) && b.distanceToSqr(ally) <= 9) {
				Vec3 look = b.getLookAngle().horizontal();
				Vec3 toAlly = ally.position().subtract(b.position()).horizontal();
				if (look.lengthSqr() > 0 && toAlly.dot(look) < 0) {
					best = Math.max(best, 0.30f);
				}
			}
			if (attacker instanceof Mob && b.isBlocking()) {
				best = (float) Math.max(best, 0.02 * rank(b, BULWARK, P3));
			}
		}
		return best;
	}

	static void after(LivingEntity target, DamageSource source, float original, float afterShield, boolean blocked, double dealt) {
		if (!(target instanceof ServerPlayer v) || !(source.getEntity() instanceof LivingEntity attacker) || attacker == v) {
			return;
		}
		// Spikes (Juggernaut): 2% a rank of a hit up close goes back to the attacker
		double spikes = plate(v, C1);
		if (spikes > 0 && melee(source) && original > 0) {
			Specs.deal(attacker, v.damageSources().thorns(v), (float) (original * 0.02 * spikes));
		}
		if (!blocked) {
			return;
		}
		float stopped = Math.max(0, original - afterShield);
		// Mirror Shield (Bulwark): 5% a rank of a blocked shot goes back to the shooter
		double mirror = rank(v, BULWARK, C1);
		if (mirror > 0 && source.is(DamageTypeTags.IS_PROJECTILE) && stopped > 0) {
			Specs.deal(attacker, v.damageSources().thorns(v), (float) (stopped * 0.05 * mirror));
		}
		// Shield Bash (Bulwark): a hit up close blocked within half a second of raising the shield stuns for 0.2 s a rank
		double bash = rank(v, BULWARK, C2);
		if (bash > 0 && melee(source) && v.isUsingItem() && v.getTicksUsingItem() <= 10 + 5) {
			Wounds.stun(attacker, (int) Math.round(4 * bash));
			((ServerLevel) v.level()).sendParticles(ParticleTypes.CRIT, attacker.getX(), attacker.getEyeY(), attacker.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
		}
	}

	/** Unbreakable: no knockback at all. */
	static double knockback(LivingEntity target, double strength) {
		return Active.on(target, JUGGERNAUT) ? 0 : strength;
	}

	/** Bastion: a raised shield blocks from every direction (the angle to the hit counts as straight ahead). */
	public static double blockAngle(LivingEntity blocker, double angle) {
		return Active.on(blocker, BULWARK) ? 0 : angle;
	}

	/** Recovery (Bulwark): a shield an axe disabled is ready again 10% sooner a rank. */
	public static float disabledFor(LivingEntity blocker, float seconds) {
		return (float) (seconds * (1 - 0.10 * rank(blocker, BULWARK, P4)));
	}

	/** Sturdy Shield (Bulwark): a 10% chance a rank that a block costs the shield no durability. */
	public static int shieldDurability(ItemStack stack, ServerPlayer player, int amount) {
		if (amount > 0 && stack.is(Items.SHIELD) && Specs.roll(player, 0.10 * rank(player, BULWARK, P1))) {
			return 0;
		}
		return amount;
	}

	// --- Effects and healing ---------------------------------------------------------------------

	/**
	 * Resilience. Thick Blood: negative effects last 5% shorter a rank. Antidote: poison 20% shorter a rank (none at V).
	 * Shake It Off: a 4% chance a rank to shake a negative effect off entirely.
	 */
	static MobEffectInstance effect(LivingEntity target, MobEffectInstance effect) {
		if (!(target instanceof ServerPlayer p) || effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL || !Specs.has(p, RESILIENCE)) {
			return effect;
		}
		double factor = 1 - 0.05 * rank(p, RESILIENCE, P1);
		if (effect.getEffect().equals(MobEffects.POISON)) {
			factor *= 1 - 0.20 * rank(p, RESILIENCE, C1);
		}
		if (factor <= 0 || Specs.roll(p, 0.04 * rank(p, RESILIENCE, P4))) {
			return null;
		}
		if (factor >= 1 || effect.getDuration() < 0) {
			return effect;
		}
		int duration = Math.max(1, (int) Math.round(effect.getDuration() * factor));
		return new MobEffectInstance(effect.getEffect(), duration, effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon());
	}

	/** Steady Heart (Resilience): healing is 3% stronger a rank. */
	static float heal(LivingEntity target, float amount) {
		return (float) (amount * (1 + 0.03 * rank(target, RESILIENCE, P3)));
	}

	// --- Every tick ------------------------------------------------------------------------------

	static void tick(ServerPlayer p) {
		boolean heavy = heavy(p);
		// Brace (Bulwark): +10% knockback resistance a rank while blocking
		Trunks.modifier(p, Attributes.KNOCKBACK_RESISTANCE, BRACE, p.isBlocking() ? 0.10 * rank(p, BULWARK, P2) : 0, AttributeModifier.Operation.ADD_VALUE);
		// Juggernaut, in heavy armor: Plating +0.5 armor toughness a rank, Unyielding +4% knockback resistance a rank,
		// Anchor +20% a rank while sneaking; the price: −10% movement speed
		Trunks.modifier(p, Attributes.ARMOR_TOUGHNESS, PLATING, 0.5 * plate(p, P1), AttributeModifier.Operation.ADD_VALUE);
		Trunks.modifier(p, Attributes.KNOCKBACK_RESISTANCE, UNYIELDING, 0.04 * plate(p, P3), AttributeModifier.Operation.ADD_VALUE);
		Trunks.modifier(p, Attributes.KNOCKBACK_RESISTANCE, ANCHOR, p.isShiftKeyDown() ? 0.20 * plate(p, C2) : 0, AttributeModifier.Operation.ADD_VALUE);
		Trunks.modifier(p, Attributes.MOVEMENT_SPEED, JUGGERNAUT_PRICE, heavy && Specs.has(p, JUGGERNAUT) ? -0.10 : 0,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		// Unbreakable: full knockback resistance, and no sprinting
		boolean unbreakable = Active.on(p, JUGGERNAUT);
		Trunks.modifier(p, Attributes.KNOCKBACK_RESISTANCE, UNBREAKABLE, unbreakable ? 1.0 : 0, AttributeModifier.Operation.ADD_VALUE);
		if (unbreakable && p.isSprinting()) {
			p.setSprinting(false);
		}
		// Resilience's price: armor protects 10% less
		Trunks.modifier(p, Attributes.ARMOR, RESILIENCE_PRICE, Specs.has(p, RESILIENCE) ? -0.10 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		// Second Wind: the healing, spread over its 5 seconds
		Active.State wind = Active.get(p, RESILIENCE);
		if (wind != null && wind.value > 0) {
			float now = (float) Math.min(wind.value, (12 + 2 * (wind.rank - 1)) / 100.0);
			wind.value -= now;
			p.heal(now);
		}
		// Quick Recovery (Resilience): natural regeneration 5% faster a rank
		double quick = rank(p, RESILIENCE, P2);
		if (quick > 0 && p.getHealth() < p.getMaxHealth() && p.getFoodData().getFoodLevel() >= 18) {
			double carry = RECOVERY.getOrDefault(p, 0.0) + 0.05 * quick;
			if (carry >= 1) {
				FoodDataAccess food = (FoodDataAccess) p.getFoodData();
				food.extremeprogression$setTickTimer(food.extremeprogression$getTickTimer() + 1);
				carry -= 1;
			}
			RECOVERY.put(p, carry);
		}
		// Cool Blood (Resilience): you burn 10% shorter a rank (the fire burns out 1 / (1 − 0.1 × rank) times as fast)
		double cool = rank(p, RESILIENCE, C2);
		if (cool > 0 && p.getRemainingFireTicks() > 0) {
			double carry = COOLING.getOrDefault(p, 0.0) + 0.10 * cool / (1 - 0.10 * cool);
			int whole = (int) carry;
			if (whole > 0) {
				p.setRemainingFireTicks(Math.max(0, p.getRemainingFireTicks() - whole));
			}
			COOLING.put(p, carry - whole);
		}
	}
}
