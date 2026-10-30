package com.extremeprogression.mastery;

import static com.extremeprogression.mastery.Mastery.bonus;

import java.util.Set;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.trunk.Trunks;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * What the Mastery perks do in a fight and on the move. The yields, fishing, smelting, repairs, brewing and enchanting
 * perks sit beside the specializations' own bonuses for the same thing (Mining, Foraging, Farming, Fishing, Smithing,
 * Arcana and the trunks' furnace tick).
 */
public final class MasteryEffects {
	private MasteryEffects() {}

	private static final Identifier SWIFT_FEET = ExtremeProgression.id("mastery/swift_feet");

	/** The vanilla bosses, and any other mod's mob in the common {@code c:bosses} tag. */
	private static final Set<String> BOSSES = Set.of("ender_dragon", "wither", "warden", "elder_guardian");
	/** Mobs that fly (Skyshot). */
	private static final Set<String> FLYING = Set.of("phantom", "ghast", "happy_ghast", "blaze", "vex", "breeze", "bee", "bat", "parrot", "allay",
			"ender_dragon", "wither");

	private static String path(Entity e) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
	}

	public static boolean boss(Entity e) {
		return e != null && (BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(e.getType()).is(ConventionalEntityTypeTags.BOSSES) || BOSSES.contains(path(e)));
	}

	public static boolean flying(Entity e) {
		return e != null && FLYING.contains(path(e));
	}

	/** A hit, after the specializations: Giant Slayer, Duelist, Skyshot, Marksman, Titan Guard, Duel Guard, Soft Landing and Kindred. */
	public static float damage(LivingEntity target, DamageSource source, float amount) {
		double out = amount;
		Entity attacker = source.getEntity();
		Entity direct = source.getDirectEntity();
		if (attacker instanceof ServerPlayer p && p != target) {
			if (direct == p) {
				out *= 1 + (boss(target) ? bonus(p, MasteryPerk.GIANT_SLAYER) : 0) + (target instanceof Player ? bonus(p, MasteryPerk.DUELIST) : 0);
			} else if (direct instanceof Projectile && !(direct instanceof FishingHook)) {
				out *= 1 + (flying(target) ? bonus(p, MasteryPerk.SKYSHOT) : 0) + (target instanceof Player ? bonus(p, MasteryPerk.MARKSMAN) : 0);
			}
		}
		if (target instanceof ServerPlayer v) {
			if (attacker != v && boss(attacker)) {
				out *= 1 - bonus(v, MasteryPerk.TITAN_GUARD);
			}
			if (attacker instanceof Player && attacker != v) {
				out *= 1 - bonus(v, MasteryPerk.DUEL_GUARD);
			}
			if (source.is(DamageTypeTags.IS_FALL)) {
				out *= 1 - bonus(v, MasteryPerk.SOFT_LANDING);
			}
		}
		if (attacker instanceof TamableAnimal pet && pet.getOwner() instanceof ServerPlayer owner && target != owner) {
			out *= 1 + bonus(owner, MasteryPerk.KINDRED);
		}
		return (float) out;
	}

	/** Every tick, for every player: Swift Feet, an attribute (so it is felt on the client too). */
	public static void tick(ServerPlayer p) {
		Trunks.modifier(p, Attributes.MOVEMENT_SPEED, SWIFT_FEET, bonus(p, MasteryPerk.SWIFT_FEET), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}
}
