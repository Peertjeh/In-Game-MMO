package com.extremeprogression.spec;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import com.extremeprogression.ExtremeProgression;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * What the abilities leave on others: bleeding (1 damage a second), shattered armor (armor 0) and a stun (no moving,
 * no damage). Kept in memory; a shattered armor's modifier is never saved, so a restart ends it too.
 */
public final class Wounds {
	private Wounds() {}

	private static final class Bleed {
		final UUID by;
		int ticks;

		Bleed(UUID by, int ticks) {
			this.by = by;
			this.ticks = ticks;
		}
	}

	private static final Map<LivingEntity, Bleed> BLEEDING = new WeakHashMap<>();
	private static final Map<LivingEntity, Long> SHATTERED = new WeakHashMap<>();
	private static final Identifier SHATTER = ExtremeProgression.id("spec/shatter");
	/** Bleeding: damage each second. */
	public static final float BLEED_DAMAGE = 1.0f;

	/** Makes the target bleed for this many seconds (a longer bleeding already going on is kept). */
	public static void bleed(ServerPlayer by, LivingEntity target, int seconds) {
		if (seconds <= 0 || !target.isAlive()) {
			return;
		}
		Bleed b = BLEEDING.get(target);
		if (b == null || b.ticks < seconds * 20) {
			BLEEDING.put(target, new Bleed(by.getUUID(), seconds * 20));
		}
	}

	public static boolean bleeding(LivingEntity target) {
		return BLEEDING.containsKey(target);
	}

	/** Sets the target's armor to 0 for this many ticks. */
	public static void shatter(LivingEntity target, int ticks) {
		AttributeInstance armor = target.getAttribute(Attributes.ARMOR);
		if (armor == null) {
			return;
		}
		armor.addOrUpdateTransientModifier(new AttributeModifier(SHATTER, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		long until = target.level().getServer().getTickCount() + ticks;
		SHATTERED.merge(target, until, Math::max);
	}

	public static boolean shattered(LivingEntity target) {
		AttributeInstance armor = target.getAttribute(Attributes.ARMOR);
		return armor != null && armor.getModifier(SHATTER) != null;
	}

	/** Stuns the target: it cannot move and deals no damage for this many ticks. */
	public static void stun(LivingEntity target, int ticks) {
		if (ticks <= 0) {
			return;
		}
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 9, false, true));
		target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 9, false, true));
	}

	public static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		// The damage comes after the loop: dealing it runs the game's own hooks, which must not meet a map mid-loop
		java.util.List<Map.Entry<LivingEntity, UUID>> due = new java.util.ArrayList<>();
		for (Iterator<Map.Entry<LivingEntity, Bleed>> it = BLEEDING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<LivingEntity, Bleed> e = it.next();
			LivingEntity target = e.getKey();
			Bleed b = e.getValue();
			if (!target.isAlive() || target.isRemoved() || b.ticks <= 0) {
				it.remove();
				continue;
			}
			b.ticks--;
			if (b.ticks % 20 == 0) {
				due.add(Map.entry(target, b.by));
			}
		}
		for (Map.Entry<LivingEntity, UUID> e : due) {
			ServerPlayer by = server.getPlayerList().getPlayer(e.getValue());
			LivingEntity target = e.getKey();
			DamageSource source = by != null ? target.damageSources().playerAttack(by) : target.damageSources().generic();
			Specs.deal(target, source, BLEED_DAMAGE);
		}
		for (Iterator<Map.Entry<LivingEntity, Long>> it = SHATTERED.entrySet().iterator(); it.hasNext();) {
			Map.Entry<LivingEntity, Long> e = it.next();
			if (e.getValue() <= now || !e.getKey().isAlive()) {
				AttributeInstance armor = e.getKey().getAttribute(Attributes.ARMOR);
				if (armor != null) {
					armor.removeModifier(SHATTER);
				}
				it.remove();
			}
		}
	}
}
