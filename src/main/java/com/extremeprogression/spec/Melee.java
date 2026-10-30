package com.extremeprogression.spec;

import static com.extremeprogression.skill.Specialization.BLADE;
import static com.extremeprogression.skill.Specialization.BRAWLER;
import static com.extremeprogression.skill.Specialization.CRUSHER;
import static com.extremeprogression.spec.Specs.rank;
import static com.extremeprogression.tree.NodeSlot.C1;
import static com.extremeprogression.tree.NodeSlot.C2;
import static com.extremeprogression.tree.NodeSlot.P1;
import static com.extremeprogression.tree.NodeSlot.P2;
import static com.extremeprogression.tree.NodeSlot.P3;
import static com.extremeprogression.tree.NodeSlot.P4;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.ability.AbilityService;
import com.extremeprogression.trunk.Trunks;
import com.extremeprogression.xp.CombatXp;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Melee's three specializations (design §2, skill 1). Every effect is of a hit up close (the attacker is the hit's
 * direct source).
 * <ul>
 * <li><b>Blade</b> (sword, spear, trident up close): Riposte; Keen Edge, Lacerate, Parry, Precision; Deep Cut or Quick
 * Cut. Price: −15% damage against targets in heavy armor.</li>
 * <li><b>Crusher</b> (axe, mace): Shatter; Heavy Blows, Stagger, Sunder, Crater; Earthquake or Cleave. Price: −10%
 * attack speed with every weapon.</li>
 * <li><b>Brawler</b> (empty hand, no chestplate of iron or heavier, for every effect): Flurry; Hardened Knuckles,
 * Quick Hands, Combo, Knockout; Iron Fist or Disarm.</li>
 * </ul>
 */
public final class Melee {
	private Melee() {}

	private static final Identifier QUICK_CUT = ExtremeProgression.id("spec/quick_cut");
	private static final Identifier CRUSHER_PRICE = ExtremeProgression.id("spec/crusher_price");
	private static final Identifier KNUCKLES = ExtremeProgression.id("spec/knuckles");
	private static final Identifier QUICK_HANDS = ExtremeProgression.id("spec/quick_hands");

	/** Combo: the steps of consecutive bare-handed hits, and the tick of the last. */
	private record Combo(int steps, long last) {}

	private static final Map<UUID, Combo> COMBO = new HashMap<>();

	static void register() {
		// Riposte: 3 s (+0.5 s a rank) in which the next hit on you is parried
		AbilityService.register(BLADE, (p, rank) -> Active.start(p, BLADE, rank, 60 + 10 * (rank - 1)));
		// Shatter: the next full hit within 5 seconds
		AbilityService.register(CRUSHER, (p, rank) -> Active.start(p, CRUSHER, rank, 100));
		// Flurry: 6 s (+1 s a rank)
		AbilityService.register(BRAWLER, (p, rank) -> Active.start(p, BRAWLER, rank, 120 + 20 * (rank - 1)));
	}

	/** Bleeding from Blade, in seconds: its base, plus Deep Cut. */
	private static int bleedSeconds(ServerPlayer p, int base) {
		return base + rank(p, BLADE, C1);
	}

	// --- Before a hit ----------------------------------------------------------------------------

	/** Riposte: the next hit by a living attacker is parried, and you strike back with a crit and bleeding. */
	static boolean allow(LivingEntity target, DamageSource source) {
		if (!(target instanceof ServerPlayer v) || !(source.getEntity() instanceof LivingEntity attacker) || attacker == v) {
			return true;
		}
		Active.State riposte = Active.get(v, BLADE);
		if (riposte == null) {
			return true;
		}
		Active.end(v, BLADE);
		ServerLevel level = (ServerLevel) v.level();
		level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 1.2f);
		if (v.distanceTo(attacker) <= v.entityInteractionRange() + 1) {
			v.crit(attacker);
			level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 1.0f);
			Specs.deal(attacker, v.damageSources().playerAttack(v), (float) v.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.5f);
			Wounds.bleed(v, attacker, bleedSeconds(v, 4 + (riposte.rank - 1)));
		}
		return false;
	}

	/** Parry (Blade): a chance to halve a hit up close while you hold a blade weapon. */
	static float incoming(LivingEntity target, DamageSource source, float amount) {
		if (target instanceof ServerPlayer v && source.getEntity() instanceof LivingEntity && source.getDirectEntity() == source.getEntity()
				&& Specs.bladeWeapon(v.getMainHandItem()) && Specs.roll(v, 0.03 * rank(v, BLADE, P3))) {
			return amount * 0.5f;
		}
		return amount;
	}

	static float outgoing(ServerPlayer p, LivingEntity target, DamageSource source, float amount) {
		if (source.getDirectEntity() != p) {
			return amount;
		}
		ItemStack held = p.getMainHandItem();
		boolean full = CombatXp.fullCharge(p);
		float out = amount;
		// Blade's price, with any weapon
		if (Specs.has(p, BLADE) && Trunks.heavyArmor(target)) {
			out *= 0.85f;
		}
		if (Specs.bladeWeapon(held)) {
			// Keen Edge +2% a rank; Precision: critical hits +4% a rank
			out *= 1 + 0.02f * rank(p, BLADE, P1);
			if (Trunks.critical(p)) {
				out *= 1 + 0.04f * rank(p, BLADE, P4);
			}
		}
		if (Specs.axeOrMace(held)) {
			// Heavy Blows +2% a rank; Sunder: full hits on armored targets +3% a rank; Crater: mace smashes +4% a rank
			out *= 1 + 0.02f * rank(p, CRUSHER, P1);
			if (full && target.getArmorValue() > 0) {
				out *= 1 + 0.03f * rank(p, CRUSHER, P3);
			}
			if (source.is(DamageTypeTags.IS_MACE_SMASH)) {
				out *= 1 + 0.04f * rank(p, CRUSHER, P4);
			}
			Active.State shatter = Active.get(p, CRUSHER);
			if (shatter != null && full) {
				Active.end(p, CRUSHER);
				shockwave(p, target, out, shatter.rank);
			}
		}
		if (Specs.has(p, BRAWLER) && Specs.brawling(p)) {
			// Combo: +2% a rank for each step of consecutive hits (within 2 seconds, up to 5 steps)
			long tick = p.level().getServer().getTickCount();
			Combo c = COMBO.get(p.getUUID());
			int steps;
			if (c == null || tick - c.last > 40) {
				steps = 0;
			} else if (c.last == tick) {
				// (a sweep hitting several in the same tick is one step)
				steps = c.steps;
			} else {
				steps = Math.min(5, c.steps + 1);
			}
			COMBO.put(p.getUUID(), new Combo(steps, tick));
			out *= 1 + 0.02f * rank(p, BRAWLER, P3) * steps;
			// Flurry: +4% for every hit already landed in it, up to +40%
			Active.State flurry = Active.get(p, BRAWLER);
			if (flurry != null) {
				out *= 1 + 0.04f * (float) Math.min(10, flurry.value);
				flurry.value++;
			}
		}
		return out;
	}

	/**
	 * Shatter: a shockwave around the target. It and every enemy within 3 blocks (+0.5 a rank) lose their armor for 8
	 * seconds (+1 a rank) and are knocked back; the others take half the hit's damage.
	 */
	private static void shockwave(ServerPlayer p, LivingEntity target, float damage, int rank) {
		double radius = 3 + 0.5 * (rank - 1);
		int ticks = (8 + (rank - 1)) * 20;
		ServerLevel level = (ServerLevel) p.level();
		Wounds.shatter(target, ticks);
		push(p, target, 0.6);
		for (LivingEntity e : enemiesAround(p, target, radius)) {
			Wounds.shatter(e, ticks);
			Specs.deal(e, p.damageSources().playerAttack(p), damage * 0.5f);
			push(p, e, 0.8);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 0.5, target.getZ(), 1, 0, 0, 0, 0);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.0f, 0.8f);
	}

	/** Hostile mobs (or mobs after this player) near a target, besides the target itself. Never other players. */
	static java.util.List<LivingEntity> enemiesAround(ServerPlayer p, LivingEntity center, double radius) {
		java.util.List<LivingEntity> out = new java.util.ArrayList<>();
		for (Entity e : p.level().getEntities(p, center.getBoundingBox().inflate(radius), e -> e != center && e.isAlive())) {
			if (e instanceof LivingEntity l && !(e instanceof Player) && (e instanceof Enemy || e instanceof Mob m && m.getTarget() == p)
					&& e.distanceTo(center) <= radius) {
				out.add(l);
			}
		}
		return out;
	}

	/** Pushes an entity away from the player, also on its client. */
	static void push(Entity from, Entity e, double strength) {
		Vec3 away = e.position().subtract(from.position()).horizontal();
		Vec3 dir = away.lengthSqr() < 1e-4 ? Vec3.ZERO : away.normalize();
		e.push(dir.x * strength, 0.25, dir.z * strength);
		e.needsSync = true;
		e.syncVelocity = true;
	}

	// --- After a hit -----------------------------------------------------------------------------

	static void after(ServerPlayer p, LivingEntity target, DamageSource source, double dealt) {
		if (source.getDirectEntity() != p || dealt <= 0) {
			return;
		}
		boolean full = CombatXp.fullCharge(p);
		ItemStack held = p.getMainHandItem();
		// Lacerate (Blade): a chance of 3 seconds of bleeding
		if (full && Specs.bladeWeapon(held) && Specs.roll(p, 0.04 * rank(p, BLADE, P2))) {
			Wounds.bleed(p, target, bleedSeconds(p, 3));
		}
		if (Specs.axeOrMace(held)) {
			// Stagger (Crusher): a chance of Slowness II for 2 seconds
			if (full && Specs.roll(p, 0.04 * rank(p, CRUSHER, P2))) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), p);
			}
			// Earthquake (Crusher): a mace smash also hits the enemies around it, for 6% a rank of its damage
			int quake = rank(p, CRUSHER, C1);
			if (quake > 0 && source.is(DamageTypeTags.IS_MACE_SMASH)) {
				for (LivingEntity e : enemiesAround(p, target, 3)) {
					Specs.deal(e, p.damageSources().playerAttack(p), (float) (dealt * 0.06 * quake));
				}
			}
		}
		if (Specs.has(p, BRAWLER) && Specs.brawling(p)) {
			// Knockout: a chance to stun for 1 second; Disarm: a chance that a player drops their weapon
			if (full && Specs.roll(p, 0.03 * rank(p, BRAWLER, P4))) {
				Wounds.stun(target, 20);
			}
			if (target instanceof ServerPlayer victim && Specs.roll(p, 0.03 * rank(p, BRAWLER, C2))) {
				disarm(victim);
			}
		}
	}

	private static void disarm(ServerPlayer victim) {
		ItemStack weapon = victim.getMainHandItem();
		if (weapon.isEmpty()) {
			return;
		}
		ItemEntity drop = new ItemEntity(victim.level(), victim.getX(), victim.getEyeY() - 0.3, victim.getZ(), weapon.copy());
		drop.setPickUpDelay(40);
		victim.level().addFreshEntity(drop);
		victim.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
	}

	// --- Every tick ------------------------------------------------------------------------------

	static void tick(ServerPlayer p) {
		ItemStack held = p.getMainHandItem();
		// Quick Cut (Blade): +2% attack speed a rank with a blade weapon; Crusher's price: −10% with every weapon
		Trunks.modifier(p, Attributes.ATTACK_SPEED, QUICK_CUT, Specs.bladeWeapon(held) ? 0.02 * rank(p, BLADE, C2) : 0,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		Trunks.modifier(p, Attributes.ATTACK_SPEED, CRUSHER_PRICE, Specs.has(p, CRUSHER) ? -0.10 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		// Hardened Knuckles and Iron Fist (Brawler): +0.3 damage a rank each; Quick Hands: +4% attack speed a rank
		boolean brawling = Specs.has(p, BRAWLER) && Specs.brawling(p);
		Trunks.modifier(p, Attributes.ATTACK_DAMAGE, KNUCKLES, brawling ? 0.3 * (rank(p, BRAWLER, P1) + rank(p, BRAWLER, C1)) : 0,
				AttributeModifier.Operation.ADD_VALUE);
		Trunks.modifier(p, Attributes.ATTACK_SPEED, QUICK_HANDS, brawling ? 0.04 * rank(p, BRAWLER, P2) : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	/** Flurry: the attack bar is ignored (every hit counts as fully charged) while it lasts and you fight bare-handed. */
	public static boolean flurry(Player player) {
		return Specs.active(player, BRAWLER) && Specs.brawling(player);
	}

	/** Cleave (Crusher): your axe disables a shield 1 second longer a rank (vanilla: 5 seconds). */
	public static float disableSeconds(LivingEntity attacker, float seconds) {
		if (seconds > 0 && attacker.getMainHandItem().is(net.minecraft.tags.ItemTags.AXES)) {
			return seconds + rank(attacker, CRUSHER, C2);
		}
		return seconds;
	}

	static void forget(UUID id) {
		COMBO.remove(id);
	}
}
