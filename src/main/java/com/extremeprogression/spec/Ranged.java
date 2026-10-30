package com.extremeprogression.spec;

import static com.extremeprogression.skill.Specialization.HARPOONER;
import static com.extremeprogression.skill.Specialization.SKIRMISHER;
import static com.extremeprogression.skill.Specialization.SNIPER;
import static com.extremeprogression.spec.Specs.rank;
import static com.extremeprogression.tree.NodeSlot.C1;
import static com.extremeprogression.tree.NodeSlot.C2;
import static com.extremeprogression.tree.NodeSlot.P1;
import static com.extremeprogression.tree.NodeSlot.P2;
import static com.extremeprogression.tree.NodeSlot.P3;
import static com.extremeprogression.tree.NodeSlot.P4;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.ability.AbilityService;
import com.extremeprogression.mixin.AbstractArrowAccess;
import com.extremeprogression.trunk.Trunks;
import com.extremeprogression.xp.CombatXp;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Ranged's three specializations (design §2, skill 2). "A shot" is an arrow or a crossbow's firework; Harpooner's
 * effects are of a thrown trident.
 * <ul>
 * <li><b>Sniper</b>: Deadeye; Marksman, Long Shot, Hunter's Mark, Patient Aim; Piercing or Headshot. Price: −20% damage
 * on targets closer than 8 blocks.</li>
 * <li><b>Skirmisher</b>: Volley; Quick Draw, Hit and Run, Close Range, Scavenger; Quick Reload or Running Shot. Price:
 * −20% damage on targets beyond 20 blocks.</li>
 * <li><b>Harpooner</b>: Harpoon; Trident Arm, Strong Throw, Barbed, Hunter of the Deep; Return or Tidal Power. Price:
 * bow and crossbow deal −15% damage.</li>
 * </ul>
 * A headshot is a hit whose line meets the target at its eyes or higher.
 */
public final class Ranged {
	private Ranged() {}

	private static final Identifier HIT_AND_RUN_SPEED = ExtremeProgression.id("spec/hit_and_run");

	/** Marks on projectiles in flight: Deadeye's and Harpoon's rank, and Patient Aim. */
	private static final Map<Entity, Integer> DEADEYE = new WeakHashMap<>();
	private static final Map<Entity, Integer> HARPOON = new WeakHashMap<>();
	private static final Set<Entity> PATIENT = Collections.newSetFromMap(new WeakHashMap<>());
	/** The tick of a player's last bow shot held a second past the full draw (Patient Aim). */
	private static final Map<UUID, Long> PATIENT_RELEASE = new HashMap<>();
	/** Until which tick Hit and Run's speed lasts. */
	private static final Map<UUID, Long> HIT_AND_RUN = new HashMap<>();

	static void register() {
		// Deadeye: the next full shot within 8 seconds
		AbilityService.register(SNIPER, (p, rank) -> Active.start(p, SNIPER, rank, 160));
		// Volley: 5 s (+1 s a rank)
		AbilityService.register(SKIRMISHER, (p, rank) -> Active.start(p, SKIRMISHER, rank, 100 + 20 * (rank - 1)));
		// Harpoon: the next trident thrown within 10 seconds
		AbilityService.register(HARPOONER, (p, rank) -> Active.start(p, HARPOONER, rank, 200));
	}

	private static boolean shotFromBow(Entity direct) {
		return direct instanceof FireworkRocketEntity || direct instanceof AbstractArrow && !(direct instanceof ThrownTrident);
	}

	// --- Shooting --------------------------------------------------------------------------------

	/** A projectile a player just shot or threw. */
	static void shot(ServerPlayer p, Projectile projectile) {
		long tick = p.level().getServer().getTickCount();
		if (projectile instanceof ThrownTrident trident) {
			// Strong Throw: +4% speed a rank
			double strong = rank(p, HARPOONER, P2);
			if (strong > 0) {
				trident.setDeltaMovement(trident.getDeltaMovement().scale(1 + 0.04 * strong));
			}
			Active.State harpoon = Active.get(p, HARPOONER);
			if (harpoon != null) {
				HARPOON.put(trident, harpoon.rank);
				Active.end(p, HARPOONER);
			}
			return;
		}
		if (!(projectile instanceof AbstractArrow arrow)) {
			return;
		}
		ItemStack weapon = arrow.getWeaponItem();
		boolean full = arrow.isCritArrow() || weapon != null && weapon.is(Items.CROSSBOW);
		if (!full) {
			return;
		}
		// Deadeye: a guaranteed crit that pierces 1 target, and a headshot deals double
		Active.State deadeye = Active.get(p, SNIPER);
		if (deadeye != null) {
			arrow.setCritArrow(true);
			pierce(arrow);
			DEADEYE.put(arrow, deadeye.rank);
			Active.end(p, SNIPER);
		}
		// Piercing: a 20% chance a rank to pierce 1 more target
		if (Specs.roll(p, 0.20 * rank(p, SNIPER, C1))) {
			pierce(arrow);
		}
		Long patient = PATIENT_RELEASE.get(p.getUUID());
		if (patient != null && patient == tick) {
			PATIENT.add(arrow);
		}
	}

	private static void pierce(AbstractArrow arrow) {
		((AbstractArrowAccess) arrow).extremeprogression$setPierceLevel((byte) Math.min(127, arrow.getPierceLevel() + 1));
	}

	/** Before a bow shot: Patient Aim counts a draw held a second past full (40 ticks). */
	public static void released(ItemStack bow, LivingEntity user, int timeLeft) {
		if (user instanceof ServerPlayer p && bow.getUseDuration(user) - timeLeft >= 40) {
			PATIENT_RELEASE.put(p.getUUID(), (long) p.level().getServer().getTickCount());
		}
	}

	/** Quick Draw (Skirmisher): a bow is drawn 3% faster a rank (added to Steady Hand). */
	public static double quickDraw(LivingEntity user) {
		return 0.03 * rank(user, SKIRMISHER, P1);
	}

	/** Volley: every shot fires 3 arrows. */
	public static int projectiles(Entity shooter, int count) {
		return Active.on(shooter, SKIRMISHER) ? Math.max(count, 3) : count;
	}

	/** Volley: the arrows fan out as with Multishot. */
	public static float spread(Entity shooter, float spread) {
		return Active.on(shooter, SKIRMISHER) ? Math.max(spread, 10f) : spread;
	}

	/** Scavenger (Skirmisher): a 4% chance a rank that a shot costs no arrow. */
	public static boolean freeShot(LivingEntity shooter, boolean free) {
		return free || Specs.roll(shooter, 0.04 * rank(shooter, SKIRMISHER, P4));
	}

	/** Quick Reload (Skirmisher): a crossbow loads 5% faster a rank. */
	public static int chargeDuration(LivingEntity user, int ticks) {
		double r = user == null ? 0 : rank(user, SKIRMISHER, C1);
		return r == 0 ? ticks : Math.max(1, (int) Math.round(ticks * (1 - 0.05 * r)));
	}

	/**
	 * Running Shot (Skirmisher): drawing a bow or crossbow slows you 20% less a rank (vanilla leaves 20% of your speed;
	 * rank V none). The factor on the slowed movement.
	 */
	public static float runningShot(Player player) {
		double r = rank(player, SKIRMISHER, C2);
		ItemStack use = player.getUseItem();
		if (r == 0 || !(use.is(Items.BOW) || use.is(Items.CROSSBOW))) {
			return 1f;
		}
		return (float) ((0.2f + 0.16f * r) / 0.2f);
	}

	/** Return (Harpooner): a 20% chance a rank that a trident without Loyalty comes back as with Loyalty I. */
	public static byte loyalty(ThrownTrident trident, byte loyalty) {
		if (loyalty == 0 && trident.getOwner() instanceof ServerPlayer p && Specs.roll(p, 0.20 * rank(p, HARPOONER, C1))) {
			return 1;
		}
		return loyalty;
	}

	/** Deadeye zooms in while drawing (on the client, for its own player). */
	public static boolean zoomed(Player player) {
		ItemStack use = player.getUseItem();
		return Specs.active(player, SNIPER) && player.isUsingItem() && (use.is(Items.BOW) || use.is(Items.CROSSBOW));
	}

	// --- Hits ------------------------------------------------------------------------------------

	static boolean headshot(Entity projectile, LivingEntity target) {
		Vec3 pos = projectile.position();
		Vec3 step = projectile.getDeltaMovement();
		double y = target.getBoundingBox().inflate(0.3).clip(pos.subtract(step), pos.add(step)).map(hit -> hit.y).orElse(projectile.getY());
		return y >= target.getEyeY() - 0.35;
	}

	static float outgoing(ServerPlayer p, LivingEntity target, DamageSource source, float amount) {
		Entity direct = source.getDirectEntity();
		if (direct == null || direct == p) {
			return amount;
		}
		double dist = p.distanceTo(target);
		float out = amount;
		if (shotFromBow(direct)) {
			if (Specs.has(p, SNIPER) && dist < 8) {
				out *= 0.80f;
			}
			if (Specs.has(p, SKIRMISHER) && dist > 20) {
				out *= 0.80f;
			}
			if (Specs.has(p, HARPOONER)) {
				out *= 0.85f;
			}
			if (CombatXp.fullShot(direct)) {
				// Marksman +2% a rank; Long Shot +3% a rank from 25 blocks; Patient Aim +3% a rank
				out *= 1 + 0.02f * rank(p, SNIPER, P1);
				if (dist >= 25) {
					out *= 1 + 0.03f * rank(p, SNIPER, P2);
				}
				if (PATIENT.contains(direct)) {
					out *= 1 + 0.03f * rank(p, SNIPER, P4);
				}
				if (headshot(direct, target)) {
					// Headshot +6% a rank; Deadeye's shot ×2 (+20% a rank)
					out *= 1 + 0.06f * rank(p, SNIPER, C2);
					Integer deadeye = DEADEYE.get(direct);
					if (deadeye != null) {
						out *= 2.0f + 0.2f * (deadeye - 1);
						((ServerLevel) p.level()).playSound(null, target.getX(), target.getEyeY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT,
								SoundSource.PLAYERS, 1.0f, 1.4f);
					}
				}
			}
			// Close Range (Skirmisher): +2% a rank within 15 blocks
			if (dist <= 15) {
				out *= 1 + 0.02f * rank(p, SKIRMISHER, P3);
			}
		} else if (direct instanceof ThrownTrident) {
			// Trident Arm +2% a rank; Hunter of the Deep +4% a rank on a target in water; Tidal Power +4% a rank in water or rain
			out *= 1 + 0.02f * rank(p, HARPOONER, P1);
			if (target.isInWater()) {
				out *= 1 + 0.04f * rank(p, HARPOONER, P4);
			}
			if (p.isInWaterOrRain()) {
				out *= 1 + 0.04f * rank(p, HARPOONER, C2);
			}
		}
		return out;
	}

	static void after(ServerPlayer p, LivingEntity target, DamageSource source, double dealt) {
		Entity direct = source.getDirectEntity();
		if (dealt <= 0 || direct == null || direct == p) {
			return;
		}
		long tick = p.level().getServer().getTickCount();
		if (shotFromBow(direct)) {
			// Hunter's Mark (Sniper): a full shot's target glows 1 second a rank
			double mark = rank(p, SNIPER, P3);
			if (mark > 0 && CombatXp.fullShot(direct)) {
				target.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) Math.round(20 * mark), 0), p);
			}
			// Hit and Run (Skirmisher): +3% speed a rank for 3 seconds
			if (rank(p, SKIRMISHER, P2) > 0) {
				HIT_AND_RUN.put(p.getUUID(), tick + 60);
			}
		} else if (direct instanceof ThrownTrident) {
			// Barbed (Harpooner): Slowness I for 0.5 second a rank
			double barbed = rank(p, HARPOONER, P3);
			if (barbed > 0) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) Math.round(10 * barbed), 0), p);
			}
			Integer harpoon = HARPOON.remove(direct);
			if (harpoon != null) {
				pull(p, target, harpoon);
			}
		}
	}

	/** Harpoon: pulls the target to you, then slows it (Slowness II) for 1 second a rank. */
	private static void pull(ServerPlayer p, LivingEntity target, int rank) {
		Vec3 toward = p.position().subtract(target.position());
		double dist = toward.horizontalDistance();
		if (dist > 1.5) {
			Vec3 flat = toward.horizontal().normalize().scale(Math.min(2.0, (dist - 1.5) * 0.14));
			target.setDeltaMovement(flat.x, 0.35 + Math.max(-0.2, Math.min(0.6, toward.y * 0.05)), flat.z);
			target.needsSync = true;
			target.syncVelocity = true;
		}
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * rank, 1), p);
		((ServerLevel) p.level()).playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1.0f, 0.8f);
	}

	static void tick(ServerPlayer p) {
		Long until = HIT_AND_RUN.get(p.getUUID());
		boolean running = until != null && until > p.level().getServer().getTickCount();
		Trunks.modifier(p, Attributes.MOVEMENT_SPEED, HIT_AND_RUN_SPEED, running ? 0.03 * rank(p, SKIRMISHER, P2) : 0,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		if (!running && until != null) {
			HIT_AND_RUN.remove(p.getUUID());
		}
	}

	static void forget(UUID id) {
		PATIENT_RELEASE.remove(id);
		HIT_AND_RUN.remove(id);
	}
}
