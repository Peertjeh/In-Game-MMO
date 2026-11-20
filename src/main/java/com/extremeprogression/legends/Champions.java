package com.extremeprogression.legends;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.mojang.serialization.Codec;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.antiexploit.MobOrigins;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.xp.XpService;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;

/**
 * Champions (2.0): now and then a hostile mob that spawns naturally is a Champion: its name above its head in gold,
 * three times the health, half again the damage, and one affix. Slain, it gives its slayer CHAMPION_KILL_XP on top of
 * the fight's own XP and may drop a tome; it counts for the weekly contract and for titles. Never a boss, never a mob
 * from a spawner, egg or command.
 */
public final class Champions {
	private Champions() {}

	/** What makes a Champion more than its kind. */
	public enum Affix {
		/** Moves 35% faster. */
		FRENZIED(0xFF5555),
		/** Twice a Champion's health again, larger, never knocked back, a little slower. */
		JUGGERNAUT(0xAA0000),
		/** Heals half the damage it deals. */
		VAMPIRIC(0xAA00AA),
		/** Its hits poison for 5 seconds. */
		VENOMOUS(0x55FF55),
		/** Its hits slow for 4 seconds. */
		FROSTBOUND(0x55FFFF),
		/** Its hits set on fire; fire does not hurt it. */
		INFERNAL(0xFFAA00),
		/** Resistance, and armor. */
		WARDED(0xAAAAAA);

		public final int color;
		public final String id;

		Affix(int color) {
			this.color = color;
			this.id = name().toLowerCase(Locale.ROOT);
		}

		public String translationKey() {
			return "champion.extremeprogression." + id;
		}

		public static Affix byId(String id) {
			for (Affix a : values()) {
				if (a.id.equals(id)) {
					return a;
				}
			}
			return null;
		}
	}

	/** The mobs that are never Champions: bosses, and mobs a Champion would make no sense of. */
	static final Set<String> NEVER = Set.of("minecraft:ender_dragon", "minecraft:wither", "minecraft:warden", "minecraft:elder_guardian",
			"minecraft:giant", "minecraft:creaking", "minecraft:shulker", "minecraft:vex", "minecraft:endermite", "minecraft:silverfish");

	/** The spawns a Champion may come from. */
	static final Set<EntitySpawnReason> NATURAL = Set.of(EntitySpawnReason.NATURAL, EntitySpawnReason.CHUNK_GENERATION, EntitySpawnReason.STRUCTURE,
			EntitySpawnReason.PATROL);

	private static AttachmentType<String> CHAMPION;

	public static void register() {
		CHAMPION = AttachmentRegistry.createPersistent(ExtremeProgression.id("champion"), Codec.STRING);
	}

	/** The affix of a Champion, or null for any other entity. */
	public static Affix affix(Entity e) {
		String id = e == null || CHAMPION == null ? null : ((AttachmentTarget) e).getAttached(CHAMPION);
		return id == null ? null : Affix.byId(id);
	}

	public static boolean is(Entity e) {
		return affix(e) != null;
	}

	/** The chance in this dimension: half again in the Nether, double in the End. */
	static double chance(Level level) {
		String dim = level.dimension().identifier().toString();
		double c = Config.CHAMPION_CHANCE;
		return dim.equals("minecraft:the_nether") ? c * 1.5 : dim.equals("minecraft:the_end") ? c * 2 : c;
	}

	/** A mob was set up for its spawn (after {@code finalizeSpawn}): it may become a Champion. */
	public static void spawned(Mob mob, EntitySpawnReason reason) {
		if (!(mob.level() instanceof ServerLevel) || !NATURAL.contains(reason) || is(mob) || mob.getType().getCategory() != MobCategory.MONSTER
				|| NEVER.contains(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString())) {
			return;
		}
		RandomSource r = mob.getRandom();
		if (r.nextDouble() < chance(mob.level())) {
			make(mob, Affix.values()[r.nextInt(Affix.values().length)]);
		}
	}

	private static final Identifier HEALTH = ExtremeProgression.id("champion_health");
	private static final Identifier DAMAGE = ExtremeProgression.id("champion_damage");
	private static final Identifier SPEED = ExtremeProgression.id("champion_speed");
	private static final Identifier SIZE = ExtremeProgression.id("champion_size");
	private static final Identifier STEADY = ExtremeProgression.id("champion_steady");
	private static final Identifier ARMOR = ExtremeProgression.id("champion_armor");

	/** Makes a mob a Champion with this affix (also for tests and admins). */
	public static void make(Mob mob, Affix affix) {
		((AttachmentTarget) mob).setAttached(CHAMPION, affix.id);
		modifier(mob, Attributes.MAX_HEALTH, HEALTH, affix == Affix.JUGGERNAUT ? 4.0 : 2.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(mob, Attributes.ATTACK_DAMAGE, DAMAGE, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		switch (affix) {
			case FRENZIED -> modifier(mob, Attributes.MOVEMENT_SPEED, SPEED, 0.35, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			case JUGGERNAUT -> {
				modifier(mob, Attributes.MOVEMENT_SPEED, SPEED, -0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
				modifier(mob, Attributes.SCALE, SIZE, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
				modifier(mob, Attributes.KNOCKBACK_RESISTANCE, STEADY, 1.0, AttributeModifier.Operation.ADD_VALUE);
			}
			case INFERNAL -> mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
			case WARDED -> {
				mob.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, -1, 0, false, false));
				modifier(mob, Attributes.ARMOR, ARMOR, 6, AttributeModifier.Operation.ADD_VALUE);
			}
			default -> {}
		}
		mob.setHealth(mob.getMaxHealth());
		mob.setCustomName(Component.translatable("champion.extremeprogression.name", Component.translatable(affix.translationKey()), mob.getType().getDescription())
				.withColor(affix.color));
		mob.setCustomNameVisible(true);
	}

	private static void modifier(LivingEntity e, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance a = e.getAttribute(attribute);
		if (a != null && !a.hasModifier(id)) {
			a.addPermanentModifier(new AttributeModifier(id, amount, op));
		}
	}

	/** A Champion hurt something: its affix's touch (venom, frost, fire, or its own healing). */
	public static void hit(LivingEntity target, DamageSource source, float damage) {
		Entity attacker = source.getEntity();
		Affix a = affix(attacker);
		if (a == null || !(attacker instanceof LivingEntity champion) || damage <= 0 || target == attacker) {
			return;
		}
		switch (a) {
			case VAMPIRIC -> champion.heal(damage * 0.5f);
			case VENOMOUS -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), champion);
			case FROSTBOUND -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1), champion);
			case INFERNAL -> target.igniteForSeconds(5);
			default -> {}
		}
	}

	/** The tomes a Champion may drop, each rolled on its own. */
	static final List<Object[]> DROPS = List.of(new Object[] {Tome.INSIGHT, 0.35}, new Object[] {Tome.GREATER, 0.10}, new Object[] {Tome.GRAND, 0.02});

	/** A Champion died: its slayer's reward (XP in the skill they slew it with, tomes, counts). */
	public static void died(LivingEntity champion, DamageSource source) {
		if (!is(champion) || !(champion.level() instanceof ServerLevel level) || MobOrigins.flagged(champion)) {
			return;
		}
		ServerPlayer slayer = source.getEntity() instanceof ServerPlayer p ? p : null;
		if (slayer == null) {
			return;
		}
		Skill skill = source.getDirectEntity() instanceof Projectile ? Skill.RANGED : Skill.MELEE;
		XpService.grant(slayer, skill, Config.CHAMPION_KILL_XP);
		LegendsData d = PlayerStore.get(slayer.getUUID()).legends;
		d.add("champions", 1);
		PlayerStore.markDirty(slayer.getUUID());
		if (Config.BOOSTS) {
			RandomSource r = champion.getRandom();
			for (Object[] drop : DROPS) {
				if (r.nextDouble() < (double) drop[1]) {
					champion.spawnAtLocation(level, ((Tome) drop[0]).stack(1));
				}
			}
		}
		slayer.sendSystemMessage(Component.translatable("message.extremeprogression.champion.slain", champion.getDisplayName(), Math.round(Config.CHAMPION_KILL_XP),
				Component.translatable(skill.translationKey())).withColor(0xFFAA00), true);
		Contracts.killed(slayer, champion, true);
		Titles.check(slayer);
	}
}
