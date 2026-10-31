package com.extremeprogression.synergy;

import static com.extremeprogression.synergy.Synergies.power;
import static com.extremeprogression.synergy.Synergies.trigger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.trunk.Trunks;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * What the twelve synergies and the six duo synergies do, and where their situations happen (the first time with both
 * levels met, that is where they are discovered). Every effect follows {@link Synergies#power}.
 */
public final class SynergyEffects {
	private SynergyEffects() {}

	/** The server tick each player last blocked a hit with a shield (Counterstrike). */
	private static final Map<UUID, Long> BLOCKED = new HashMap<>();
	/** The owner of the tipped arrow whose effects are being given now (Venom Tip), or null. */
	public static Entity arrowOwner = null;
	/** Every mob's last melee hit by a player, and when (Pincer). */
	private record Hit(UUID player, long tick) {}

	private static final Map<Entity, Hit> LAST_MELEE = new java.util.WeakHashMap<>();
	/** Who planted the saplings of a chunk, by packed position (Green Thumb). */
	private static AttachmentType<Map<String, String>> PLANTERS;

	public static void register() {
		PLANTERS = AttachmentRegistry.createPersistent(ExtremeProgression.id("planters"), Codec.unboundedMap(Codec.STRING, Codec.STRING));
	}

	private static long tick(Entity e) {
		MinecraftServer server = e.level().getServer();
		return server == null ? 0 : server.getTickCount();
	}

	// --- Hits ------------------------------------------------------------------------------------

	/** A player blocked a hit with a shield: Counterstrike's window opens. */
	public static void blocked(ServerPlayer p) {
		BLOCKED.put(p.getUUID(), tick(p));
	}

	/** A hit, after everything else: Counterstrike, Charge, High Ground and Loyal Guard. */
	public static float damage(LivingEntity target, DamageSource source, float amount) {
		double out = amount;
		Entity attacker = source.getEntity();
		Entity direct = source.getDirectEntity();
		if (attacker instanceof ServerPlayer p && p != target) {
			if (direct == p) {
				// Counterstrike: the next melee hit within 2 seconds of a block, +20%
				Long at = BLOCKED.remove(p.getUUID());
				if (at != null && tick(p) - at <= 40 && trigger(p, Synergy.COUNTERSTRIKE)) {
					out *= 1 + Synergy.COUNTERSTRIKE.strength * power(p, Synergy.COUNTERSTRIKE);
				}
				// Charge: melee hits while sprinting, +10%
				if (p.isSprinting() && trigger(p, Synergy.CHARGE)) {
					out *= 1 + Synergy.CHARGE.strength * power(p, Synergy.CHARGE);
				}
			} else if (direct instanceof Projectile && !(direct instanceof FishingHook)) {
				// High Ground: a target at least 8 blocks below, +10%
				if (target.getY() <= p.getY() - 8 && trigger(p, Synergy.HIGH_GROUND)) {
					out *= 1 + Synergy.HIGH_GROUND.strength * power(p, Synergy.HIGH_GROUND);
				}
			}
		}
		// Loyal Guard: your wolves take 20% less while you block
		if (target instanceof Wolf wolf && wolf.isTame() && wolf.getOwner() instanceof ServerPlayer owner && owner.isBlocking()
				&& trigger(owner, Synergy.LOYAL_GUARD)) {
			out *= 1 - Math.min(1, Synergy.LOYAL_GUARD.strength * power(owner, Synergy.LOYAL_GUARD));
		}
		return (float) duo(target, source, out);
	}

	/** The duo synergies on a hit: Pincer, Spotter and Shield Wall. */
	private static double duo(LivingEntity target, DamageSource source, double out) {
		Entity attacker = source.getEntity();
		Entity direct = source.getDirectEntity();
		if (attacker instanceof ServerPlayer p && p != target) {
			if (direct == p) {
				// Pincer: a melee hit on a mob a party member hit within 3 seconds, +10%
				Hit last = LAST_MELEE.get(target);
				if (last != null && !last.player().equals(p.getUUID()) && tick(p) - last.tick() <= 60) {
					ServerPlayer other = p.level().getServer().getPlayerList().getPlayer(last.player());
					if (other != null && Synergies.trigger(p, other, Synergy.PINCER)) {
						out *= 1 + Synergy.PINCER.strength * Synergies.power(p, other, Synergy.PINCER);
					}
				}
				LAST_MELEE.put(target, new Hit(p.getUUID(), tick(p)));
			} else if (direct instanceof Projectile && !(direct instanceof FishingHook)) {
				// Spotter: an arrow into a mob that is after a party member, +15%
				if (target instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() instanceof ServerPlayer ally && ally != p
						&& Synergies.trigger(p, ally, Synergy.SPOTTER)) {
					out *= 1 + Synergy.SPOTTER.strength * Synergies.power(p, ally, Synergy.SPOTTER);
				}
			}
		}
		// Shield Wall: a party member within 4 blocks blocks with a shield, -15%
		if (target instanceof ServerPlayer ally && attacker instanceof LivingEntity && attacker != ally) {
			for (ServerPlayer guard : com.extremeprogression.party.Parties.near(ally, 4)) {
				if (guard.isBlocking() && Synergies.trigger(guard, ally, Synergy.SHIELD_WALL)) {
					out *= 1 - Math.min(1, Synergy.SHIELD_WALL.strength * Synergies.power(guard, ally, Synergy.SHIELD_WALL));
					break;
				}
			}
		}
		return out;
	}

	// --- Effects and potions ---------------------------------------------------------------------

	/**
	 * An effect about to be given: Venom Tip makes a tipped arrow's last 50% longer, Field Medic the good effects a
	 * party member's splash potion gives 25% longer.
	 */
	public static MobEffectInstance effect(LivingEntity target, MobEffectInstance effect) {
		if (arrowOwner instanceof ServerPlayer p && p != target && !effect.getEffect().value().isInstantaneous() && trigger(p, Synergy.VENOM_TIP)) {
			int longer = (int) Math.round(effect.getDuration() * (1 + Synergy.VENOM_TIP.strength * power(p, Synergy.VENOM_TIP)));
			return new MobEffectInstance(effect.getEffect(), longer, effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon());
		}
		ServerPlayer thrower = com.extremeprogression.spec.Arcana.thrower();
		if (thrower != null && target instanceof ServerPlayer ally && ally != thrower && effect.getDuration() > 0 && !effect.getEffect().value().isInstantaneous()
				&& effect.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.BENEFICIAL
				&& Synergies.trigger(thrower, ally, Synergy.FIELD_MEDIC)) {
			int longer = (int) Math.round(effect.getDuration() * (1 + Synergy.FIELD_MEDIC.strength * Synergies.power(thrower, ally, Synergy.FIELD_MEDIC)));
			return new MobEffectInstance(effect.getEffect(), longer, effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon());
		}
		return effect;
	}

	/** The duration factor of a potion being drunk: Tidal Magic, Water Breathing 50% longer. */
	public static double drinkFactor(ItemStack potion, LivingEntity drinker) {
		PotionContents contents = potion.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
		if (!(drinker instanceof ServerPlayer p) || contents == null) {
			return 1;
		}
		boolean breathing = false;
		for (MobEffectInstance e : contents.getAllEffects()) {
			breathing |= e.getEffect().equals(MobEffects.WATER_BREATHING);
		}
		return breathing && trigger(p, Synergy.TIDAL_MAGIC) ? 1 + Synergy.TIDAL_MAGIC.strength * power(p, Synergy.TIDAL_MAGIC) : 1;
	}

	/** Herb Lore: glow berries give 10 seconds of Night Vision. Hearty Meal: food near a party member fills 25% more. */
	public static void ate(ServerPlayer p, ItemStack food) {
		if (food.is(Items.GLOW_BERRIES) && trigger(p, Synergy.HERB_LORE)) {
			p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, (int) Math.round(20 * Synergy.HERB_LORE.strength * power(p, Synergy.HERB_LORE)), 0), p);
		}
		net.minecraft.world.food.FoodProperties props = food.get(net.minecraft.core.component.DataComponents.FOOD);
		if (props == null) {
			return;
		}
		for (ServerPlayer cook : com.extremeprogression.party.Parties.near(p, 8)) {
			if (Synergies.trigger(p, cook, Synergy.HEARTY_MEAL)) {
				double share = Synergy.HEARTY_MEAL.strength * Synergies.power(p, cook, Synergy.HEARTY_MEAL);
				int nutrition = (int) Math.round(props.nutrition() * share);
				float saturation = (float) (props.saturation() * share);
				if (nutrition > 0 || saturation > 0) {
					p.getFoodData().eat(new net.minecraft.world.food.FoodProperties(nutrition, saturation, true));
				}
				return;
			}
		}
	}

	// --- Gear and workstations -------------------------------------------------------------------

	private static boolean armor(ItemStack stack) {
		var equippable = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
		if (equippable == null) {
			return false;
		}
		EquipmentSlot slot = equippable.slot();
		return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
	}

	/**
	 * Tailored: armor wears 10% slower (each point of wear a 10% chance to be spared). Whetstone: tools and weapons wear
	 * 15% slower within 16 blocks of a party member.
	 */
	public static int durability(ItemStack stack, ServerPlayer p, int amount) {
		if (amount <= 0) {
			return amount;
		}
		double chance = 0;
		if (armor(stack)) {
			if (trigger(p, Synergy.TAILORED)) {
				chance = Synergy.TAILORED.strength * power(p, Synergy.TAILORED);
			}
		} else {
			for (ServerPlayer smith : com.extremeprogression.party.Parties.near(p, 16)) {
				if (Synergies.trigger(p, smith, Synergy.WHETSTONE)) {
					chance = Synergy.WHETSTONE.strength * Synergies.power(p, smith, Synergy.WHETSTONE);
					break;
				}
			}
		}
		if (chance <= 0) {
			return amount;
		}
		int out = amount;
		for (int i = 0; i < amount; i++) {
			if (p.getRandom().nextDouble() < chance) {
				out--;
			}
		}
		return out;
	}

	/** Raw ore, or an ore block put in whole. */
	private static boolean rawOre(ItemStack stack) {
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return id.startsWith("raw_") && !id.endsWith("_block") || id.endsWith("_ore") || id.equals("ancient_debris");
	}

	/** After a furnace's tick: Forge of the Deep, a 5% chance of one more output from raw ore. */
	public static void furnaceTick(AbstractFurnaceBlockEntity furnace, ItemStack inputBefore, ItemStack outputBefore) {
		ItemStack out = furnace.getItem(2);
		if (out.getCount() <= outputBefore.getCount() || out.getCount() >= out.getMaxStackSize() || !rawOre(inputBefore)) {
			return;
		}
		ServerPlayer p = Trunks.userOf(furnace);
		if (p != null && trigger(p, Synergy.FORGE_OF_THE_DEEP) && p.getRandom().nextDouble() < Synergy.FORGE_OF_THE_DEEP.strength * power(p, Synergy.FORGE_OF_THE_DEEP)) {
			out.grow(1);
		}
	}

	// --- Saplings --------------------------------------------------------------------------------

	/** A block was placed by a player: a sapling remembers who planted it (Green Thumb). */
	public static void placed(Player player, ServerLevel level, BlockPos pos, BlockState state) {
		if (player instanceof ServerPlayer p && state.is(BlockTags.SAPLINGS)) {
			planters(level, pos).put(Long.toString(pos.asLong()), p.getUUID().toString());
			level.getChunkAt(pos).markUnsaved();
		}
	}

	private static Map<String, String> planters(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkAt(pos);
		Map<String, String> map = ((AttachmentTarget) chunk).getAttached(PLANTERS);
		if (map == null || !(map instanceof HashMap)) {
			map = map == null ? new HashMap<>() : new HashMap<>(map);
			((AttachmentTarget) chunk).setAttached(PLANTERS, map);
		}
		return map;
	}

	/**
	 * A sapling's random tick, after vanilla's: Green Thumb gives a sapling its planter planted 20% more growth (while
	 * the planter is on the server). A sapling that grew into a tree forgets its planter.
	 */
	public static void saplingTicked(ServerLevel level, BlockPos pos, Runnable grow) {
		Map<String, String> map = ((AttachmentTarget) level.getChunkAt(pos)).getAttached(PLANTERS);
		String key = Long.toString(pos.asLong());
		String planter = map == null ? null : map.get(key);
		if (planter == null) {
			return;
		}
		if (!level.getBlockState(pos).is(BlockTags.SAPLINGS)) {
			planters(level, pos).remove(key);
			return;
		}
		ServerPlayer p;
		try {
			p = level.getServer().getPlayerList().getPlayer(UUID.fromString(planter));
		} catch (IllegalArgumentException e) {
			return;
		}
		// (vanilla grows a sapling on 1 in 7 random ticks with light: 20% more is a chance of 0.2 / 7 a tick)
		if (p != null && level.getMaxLocalRawBrightness(pos.above()) >= 9 && level.getRandom().nextDouble() < Synergy.GREEN_THUMB.strength / 7 * power(p, Synergy.GREEN_THUMB)
				&& trigger(p, Synergy.GREEN_THUMB)) {
			grow.run();
		}
		if (!level.getBlockState(pos).is(BlockTags.SAPLINGS)) {
			planters(level, pos).remove(key);
		}
	}

	// --- The Deep Dark and the sea --------------------------------------------------------------

	/**
	 * Deep Walker: whether a sculk sensor in the Deep Dark does not notice this vibration, because the player who made it
	 * is within the last 2 blocks of its range.
	 */
	public static boolean unheard(ServerLevel level, net.minecraft.world.level.gameevent.vibrations.VibrationSystem.User user,
			net.minecraft.world.level.gameevent.GameEvent.Context context, net.minecraft.world.phys.Vec3 source) {
		if (!(context.sourceEntity() instanceof ServerPlayer p) || !level.getBiome(p.blockPosition()).is(net.minecraft.world.level.biome.Biomes.DEEP_DARK)) {
			return false;
		}
		var at = user.getPositionSource().getPosition(level);
		// (only sculk sensors, calibrated ones too: a warden listens for itself)
		if (at.isEmpty() || !(level.getBlockState(BlockPos.containing(at.get())).getBlock() instanceof net.minecraft.world.level.block.SculkSensorBlock)) {
			return false;
		}
		double distance = at.get().distanceTo(source);
		return distance > user.getListenerRadius() - Synergy.DEEP_WALKER.strength * power(p, Synergy.DEEP_WALKER) && trigger(p, Synergy.DEEP_WALKER);
	}

	/** Seafarer, on the driver's client: a boat steered forward pulls 10% harder (vanilla adds 0.04 a tick). */
	public static void steered(net.minecraft.world.entity.vehicle.boat.AbstractBoat boat) {
		if (!boat.level().isClientSide() || !(boat.getControllingPassenger() instanceof Player p) || !Synergies.active(p, Synergy.SEAFARER)) {
			return;
		}
		double push = 0.04 * Synergy.SEAFARER.strength * power(p, Synergy.SEAFARER);
		double yaw = Math.toRadians(boat.getYRot());
		boat.setDeltaMovement(boat.getDeltaMovement().add(-Math.sin(yaw) * push, 0, Math.cos(yaw) * push));
	}

	/** Every second: a player steering a boat discovers Seafarer (its effect is felt on their client). */
	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.getVehicle() instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat boat && boat.getControllingPassenger() == p
					&& boat.getDeltaMovement().horizontalDistanceSqr() > 0.01 && !Synergies.known(p, Synergy.SEAFARER)) {
				trigger(p, Synergy.SEAFARER);
			}
		}
	}

	public static void forget(UUID id) {
		BLOCKED.remove(id);
	}
}
