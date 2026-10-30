package com.extremeprogression.spec;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.mixin.LivingEntityAccess;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.skill.Specialization;
import com.extremeprogression.tree.NodeSlot;
import com.extremeprogression.tree.TreeRules;
import com.extremeprogression.trunk.Trunks;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The specializations' effects (design §2, milestone 6): who has which node at which rank, the gear each one needs,
 * and the hooks the game calls, which pass every hit, tick and effect on to the skills' own classes.
 * <ul>
 * <li>A specialization counts when it is chosen and in use (its level reached); its trade-off counts from then on.</li>
 * <li>Passives and choice nodes count with the ranks bought; Exploration's do not work in heavy armor (design §2,
 * gear conflict a).</li>
 * <li>Damage an effect deals itself (bleeding, a shockwave, spikes) is <i>secondary</i>: it gives no XP, gets no
 * bonuses and leaves the target's invulnerability as it was.</li>
 * </ul>
 */
public final class Specs {
	private Specs() {}

	/** On the client: its own player's skills, for the few effects felt there (movement, view). Set by the client. */
	public static Function<Skill, SkillData> localSkills = skill -> null;
	/** On the client: whether one of its own player's abilities is in effect. Set by the client. */
	public static Predicate<Specialization> localActive = spec -> false;

	public static void register() {
		Melee.register();
		Ranged.register();
		Defense.register();
		Exploration.register();
		Mining.register();
		Foraging.register();
		Farming.register();
		Fishing.register();
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> secondary() || allow(entity, source, amount));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Projectile projectile && entity.tickCount == 0 && projectile.getOwner() instanceof ServerPlayer p) {
				Ranged.shot(p, projectile);
				if (projectile instanceof net.minecraft.world.entity.projectile.FishingHook hook) {
					Fishing.cast(p, hook);
				}
			}
		});
	}

	// --- Who has what ----------------------------------------------------------------------------

	private static SkillData data(Entity e, Skill skill) {
		if (e instanceof ServerPlayer p) {
			return PlayerStore.get(p.getUUID()).get(skill);
		}
		if (e instanceof Player p && p.level().isClientSide() && p.isLocalPlayer()) {
			return localSkills.apply(skill);
		}
		return null;
	}

	/** Whether this player has the specialization chosen and in use: its ability and its trade-off count. */
	public static boolean has(Entity e, Specialization spec) {
		SkillData d = data(e, spec.skill);
		if (d == null) {
			return false;
		}
		int index = TreeRules.indexOf(d, spec);
		return index >= 0 && TreeRules.active(d, index) == spec;
	}

	/**
	 * The rank (0–5) of a passive or choice node: 0 when the specialization is not in use or the node not bought, and
	 * for Exploration in heavy armor.
	 */
	public static int rank(Entity e, Specialization spec, NodeSlot slot) {
		if (!has(e, spec) || spec.skill == Skill.EXPLORATION && e instanceof LivingEntity l && Trunks.heavyArmor(l)) {
			return 0;
		}
		return TreeRules.rank(data(e, spec.skill), spec, slot);
	}

	/** Whether an ability is in effect: on the server, or on the client for its own player. */
	public static boolean active(Entity e, Specialization spec) {
		if (e instanceof ServerPlayer) {
			return Active.on(e, spec);
		}
		return e instanceof Player p && p.level().isClientSide() && p.isLocalPlayer() && localActive.test(spec);
	}

	/** A chance per rank, rolled for this entity. */
	static boolean roll(Entity e, double chance) {
		return chance > 0 && e.getRandom().nextDouble() < chance;
	}

	// --- Gear ------------------------------------------------------------------------------------

	/** Blade's weapons: a sword, a spear, or a trident up close. */
	public static boolean bladeWeapon(ItemStack s) {
		String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
		return id.endsWith("_sword") || id.endsWith("_spear") || s.is(Items.TRIDENT);
	}

	public static boolean axeOrMace(ItemStack s) {
		return s.is(ItemTags.AXES) || s.is(Items.MACE);
	}

	private static final Set<String> HEAVY = Set.of("iron_chestplate", "diamond_chestplate", "netherite_chestplate");

	/** Brawler's condition: an empty hand and no chestplate of iron or heavier. */
	public static boolean brawling(LivingEntity e) {
		return e.getMainHandItem().isEmpty() && !HEAVY.contains(BuiltInRegistries.ITEM.getKey(e.getItemBySlot(EquipmentSlot.CHEST).getItem()).getPath());
	}

	public static boolean holdsShield(LivingEntity e) {
		return e.getMainHandItem().is(Items.SHIELD) || e.getOffhandItem().is(Items.SHIELD);
	}

	// --- Secondary damage ------------------------------------------------------------------------

	private static int secondary = 0;

	/** Whether the damage being dealt now comes from an effect itself. */
	public static boolean secondary() {
		return secondary > 0;
	}

	/** Damage from an effect: no XP and no bonuses, and the target's invulnerability frames stay as they were. */
	public static void deal(LivingEntity target, DamageSource source, float amount) {
		if (!(target.level() instanceof ServerLevel level) || amount <= 0 || !target.isAlive()) {
			return;
		}
		int frames = target.getInvulnerableTime();
		LivingEntityAccess access = (LivingEntityAccess) target;
		float lastHurt = access.extremeprogression$getLastHurt();
		secondary++;
		try {
			target.setInvulnerableTime(0);
			target.hurtServer(level, source, amount);
		} finally {
			secondary--;
			target.setInvulnerableTime(frames);
			access.extremeprogression$setLastHurt(lastHurt);
		}
	}

	// --- Hooks -----------------------------------------------------------------------------------

	/** Before a hit: false cancels it (a parry, a dodge). */
	private static boolean allow(LivingEntity target, DamageSource source, float amount) {
		return Melee.allow(target, source) && Exploration.allow(target, source);
	}

	/** A hit's damage before armor, after the trunks: the attacker's and the target's specializations. */
	public static float damage(LivingEntity target, DamageSource source, float amount) {
		if (amount <= 0 || secondary() || target.level().isClientSide()) {
			return amount;
		}
		float out = amount;
		if (source.getEntity() instanceof ServerPlayer p && p != target) {
			out = Melee.outgoing(p, target, source, out);
			out = Ranged.outgoing(p, target, source, out);
			out = Fishing.outgoing(p, target, source, out);
		}
		out = Farming.damage(target, source, out);
		out = Defense.incoming(target, source, out);
		out = Exploration.incoming(target, source, out);
		return out;
	}

	/**
	 * After a hit landed. {@code original} is the damage before a shield, {@code afterShield} after it, {@code dealt}
	 * what the target lost.
	 */
	public static void after(LivingEntity target, DamageSource source, float original, float afterShield, boolean blocked, double dealt) {
		if (secondary() || target.level().isClientSide()) {
			return;
		}
		if (source.getEntity() instanceof ServerPlayer p && p != target) {
			Melee.after(p, target, source, dealt);
			Ranged.after(p, target, source, dealt);
		}
		Defense.after(target, source, original, afterShield, blocked, dealt);
	}

	/** The strength of a knockback the entity takes. */
	public static double knockback(LivingEntity target, double strength) {
		return Exploration.knockback(target, Defense.knockback(target, strength));
	}

	/** Every server tick, after the trunks. */
	public static void tick(MinecraftServer server) {
		Active.tick(server);
		Wounds.tick(server);
		Foraging.tick(server);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Melee.tick(p);
			Ranged.tick(p);
			Defense.tick(p);
			Exploration.tick(p);
			Mining.tick(p);
			Farming.tick(p);
			Fishing.tick(p);
		}
		Farming.endOfTick();
	}

	/** Hunger from anything the player does. */
	public static float exhaustion(Player player, float amount) {
		return Exploration.exhaustion(player, amount);
	}

	/** An effect about to be given to an entity: changed, or null when it is shaken off. */
	public static MobEffectInstance effect(LivingEntity target, MobEffectInstance effect) {
		MobEffectInstance out = Defense.effect(target, effect);
		return out == null ? null : Foraging.effect(target, out);
	}

	/** A player's breaking speed on a block (on the client too). */
	public static float destroySpeed(Player player, net.minecraft.world.level.block.state.BlockState state, float speed) {
		return Foraging.destroySpeed(player, state, Mining.destroySpeed(player, state, speed));
	}

	/** The durability a use of a tool costs: the gathering specializations' chances of none. */
	public static int durability(ItemStack stack, ServerPlayer player, int amount) {
		if (player == null || amount <= 0) {
			return amount;
		}
		int out = Mining.durability(stack, player, amount);
		out = Foraging.durability(stack, player, out);
		out = Farming.durability(stack, player, out);
		return Fishing.durability(stack, player, out);
	}

	/** Healing an entity receives. */
	public static float heal(LivingEntity target, float amount) {
		return Defense.heal(target, amount);
	}

	public static void forget(java.util.UUID id) {
		Active.forget(id);
		Melee.forget(id);
		Ranged.forget(id);
		Mining.forget(id);
		Farming.forget(id);
		Fishing.forget(id);
	}
}
