package com.extremeprogression.xp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.extremeprogression.antiexploit.AfkTracker;
import com.extremeprogression.antiexploit.MobOrigins;
import com.extremeprogression.antiexploit.PvpLimits;
import com.extremeprogression.config.Config;
import com.extremeprogression.skill.Skill;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;

/**
 * XP from every hit on the server (design §2, Appendix A.2 and A.3):
 * <ul>
 * <li><b>Melee</b> for a fully charged hit, <b>Ranged</b> for a fully drawn arrow, a thrown trident or a crossbow's
 * firework, with a bonus for distance; both count the damage up to the target's remaining health, × its danger
 * factor;</li>
 * <li><b>Farming</b> for the damage your tamed animals deal;</li>
 * <li><b>Defense</b> for what your shield blocked and your armor absorbed, from hostile mobs and players only;</li>
 * <li><b>Exploration</b> for a fall you survived ({@link ExplorationXp}).</li>
 * </ul>
 * Passive animals, mobs from spawners, spawn eggs or commands, player-built iron golems, and iron golems or zombified
 * piglins that were not hostile to you yet give nothing.
 */
public final class CombatXp {
	private CombatXp() {}

	/** The tick of each player's last fully charged attack. */
	private static final Map<UUID, Long> FULL_CHARGE = new HashMap<>();

	/** Defense: hits per attacker, and when the last one came. */
	private record Hits(int count, long last) {}

	private static final Map<UUID, Map<UUID, Hits>> DEFENSE_HITS = new HashMap<>();

	public static void register() {
		// The attack bar is read just before the attack: the attack itself empties it
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer p) {
				long tick = level.getServer().getTickCount();
				AfkTracker.interacted(p.getUUID(), tick);
				FULL_CHARGE.put(p.getUUID(), p.getAttackStrengthScale(0.5f) >= Config.MELEE_FULL_CHARGE ? tick : Long.MIN_VALUE);
			}
			return InteractionResult.PASS;
		});
	}

	/** Before a hit: was the target already after its attacker? (An iron golem or zombified piglin only gives XP then.) */
	public static boolean wasHostile(LivingEntity target, DamageSource source) {
		return target instanceof Mob mob && source.getEntity() != null && mob.getTarget() == source.getEntity();
	}

	/**
	 * After a hit. {@code original} is the damage before a shield, {@code afterShield} after it but before armor,
	 * {@code healthBefore} the target's health and absorption before the hit.
	 */
	public static void hurt(LivingEntity target, DamageSource source, float original, float afterShield, boolean blocked, float healthBefore,
			boolean hostileBefore) {
		if (target.level().isClientSide()) {
			return;
		}
		double dealt = Math.max(0, healthBefore - (target.getHealth() + target.getAbsorptionAmount()));
		Entity attacker = source.getEntity();
		Entity direct = source.getDirectEntity();
		long tick = target.level().getServer().getTickCount();

		if (attacker instanceof ServerPlayer p && attacker != target && dealt > 0) {
			if (direct == p) {
				Long full = FULL_CHARGE.get(p.getUUID());
				if (full != null && full == tick) {
					offence(p, target, Skill.MELEE, dealt, XpTables.MELEE_PER_DAMAGE, hostileBefore, tick);
				}
			} else if (fullShot(direct)) {
				double beyond = Math.max(0, p.distanceTo(target) - XpTables.RANGED_FREE_DISTANCE);
				double bonus = Math.min(XpTables.RANGED_MAX_BONUS, beyond * XpTables.RANGED_BONUS_PER_BLOCK);
				offence(p, target, Skill.RANGED, dealt, XpTables.RANGED_PER_DAMAGE * (1 + bonus), hostileBefore, tick);
			}
		} else if (attacker instanceof TamableAnimal pet && direct == pet && pet.isTame() && pet.getOwner() instanceof ServerPlayer owner
				&& owner != target && dealt > 0) {
			offence(owner, target, Skill.FARMING, dealt, XpTables.PET_PER_DAMAGE, hostileBefore, tick);
		}

		if (target instanceof ServerPlayer victim) {
			if (source.is(DamageTypes.FALL)) {
				if (victim.isAlive()) {
					ExplorationXp.fell(victim, dealt);
				}
			} else if (attacker instanceof LivingEntity enemy && attacker != victim) {
				double absorbed = (blocked ? Math.max(0, original - afterShield) : 0) + Math.max(0, afterShield - dealt);
				defense(victim, enemy, absorbed, tick);
			}
		}
	}

	/** A fully drawn arrow (vanilla marks it critical), a thrown trident, or a firework from a crossbow. */
	private static boolean fullShot(Entity projectile) {
		if (projectile instanceof ThrownTrident || projectile instanceof FireworkRocketEntity) {
			return true;
		}
		return projectile instanceof AbstractArrow arrow && arrow.isCritArrow();
	}

	private static void offence(ServerPlayer earner, LivingEntity target, Skill skill, double dealt, double perDamage, boolean hostileBefore, long tick) {
		double counted = dealt;
		if (target instanceof ServerPlayer other) {
			if (!PvpLimits.allowed(earner, other)) {
				return;
			}
			counted = PvpLimits.take(earner, other, false, dealt, tick);
		}
		double factor = danger(target, hostileBefore);
		if (factor > 0 && counted > 0) {
			XpService.award(earner, skill, counted * factor * perDamage);
		}
	}

	private static void defense(ServerPlayer victim, LivingEntity attacker, double absorbed, long tick) {
		if (absorbed <= 0 || tick - AfkTracker.lastActive(victim.getUUID(), tick) > Math.round(Config.DEFENSE_ACTIVE_SECONDS * 20)) {
			return;
		}
		double factor = danger(attacker, true);
		if (factor <= 0) {
			return;
		}
		Map<UUID, Hits> hits = DEFENSE_HITS.computeIfAbsent(victim.getUUID(), k -> new HashMap<>());
		Hits h = hits.get(attacker.getUUID());
		int count = h == null || tick - h.last > Math.round(Config.DEFENSE_ATTACKER_RESET_SECONDS * 20) ? 1 : h.count + 1;
		hits.put(attacker.getUUID(), new Hits(count, tick));
		if (count > Config.DEFENSE_HITS_PER_ATTACKER) {
			return;
		}
		double counted = absorbed;
		if (attacker instanceof ServerPlayer other) {
			if (!PvpLimits.allowed(victim, other)) {
				return;
			}
			counted = PvpLimits.take(victim, other, true, absorbed, tick);
		}
		if (counted > 0) {
			XpService.award(victim, Skill.DEFENSE, counted * factor * XpTables.DEFENSE_PER_DAMAGE);
		}
	}

	/** How dangerous a mob or player is (Appendix A.2); 0 for anything that gives no combat XP. */
	public static double danger(LivingEntity entity, boolean hostile) {
		if (entity instanceof ServerPlayer) {
			return XpTables.DANGER_PLAYER;
		}
		if (MobOrigins.flagged(entity)) {
			return 0;
		}
		if (entity instanceof IronGolem golem && (golem.isPlayerCreated() || !hostile)) {
			return 0;
		}
		if (entity instanceof ZombifiedPiglin && !hostile) {
			return 0;
		}
		var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		Double listed = XpTables.danger(key.getNamespace(), key.getPath());
		if (listed != null) {
			return listed;
		}
		return entity instanceof Enemy ? XpTables.DANGER_UNLISTED_HOSTILE : 0;
	}

	public static void forget(UUID id) {
		FULL_CHARGE.remove(id);
		DEFENSE_HITS.remove(id);
		PvpLimits.forget(id);
	}
}
