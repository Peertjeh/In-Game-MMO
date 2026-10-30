package com.extremeprogression.spec;

import static com.extremeprogression.skill.Specialization.BEASTMASTER;
import static com.extremeprogression.skill.Specialization.GROWER;
import static com.extremeprogression.skill.Specialization.HERDER;
import static com.extremeprogression.spec.Specs.rank;
import static com.extremeprogression.tree.NodeSlot.C1;
import static com.extremeprogression.tree.NodeSlot.C2;
import static com.extremeprogression.tree.NodeSlot.P1;
import static com.extremeprogression.tree.NodeSlot.P2;
import static com.extremeprogression.tree.NodeSlot.P3;
import static com.extremeprogression.tree.NodeSlot.P4;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.ability.AbilityService;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Farming's three specializations (design §2, skill 6).
 * <ul>
 * <li><b>Grower</b> (hoe, crops): Harvest Festival; Bumper Crop, Crop Lore, Tiller, Festival Spirit; Ripening Aura or
 * Seed Saver. Price: breeding gives −50% XP and no bonuses.</li>
 * <li><b>Herder</b> (livestock): Herding Call; Breeder, Quick Growth, Butcher, Calm Herd; Twins or Wool Trade. Price:
 * crops give no extra yield at all.</li>
 * <li><b>Beastmaster</b> (tamed animals): Rally the Pack; Alpha Bond, Thick Fur, Mending Pack, Tamer; Pack Leader or
 * Rider. Price: −10% melee damage while one of your tamed wolves is within 16 blocks.</li>
 * </ul>
 * Your <i>companion</i> is your tamed animal nearest to you, within 16 blocks (two or three with Pack Leader).
 */
public final class Farming {
	private Farming() {}

	private static final Identifier RIDER_SPEED = ExtremeProgression.id("spec/rider_speed");
	private static final Identifier RIDER_JUMP = ExtremeProgression.id("spec/rider_jump");

	/** Each Beastmaster's companions (entity ids), and whether one of their wolves is near; worked out once a second. */
	private static final Map<UUID, Set<UUID>> COMPANIONS = new HashMap<>();
	private static final Set<UUID> WOLF_NEAR = new HashSet<>();
	/** The horse or camel each Rider rides, which carries their modifiers. */
	private static final Map<UUID, AbstractHorse> RIDDEN = new HashMap<>();
	/** Parents whose breeding wait Calm Herd shortens once the game has set it (at the end of the tick). */
	private record Calm(Animal animal, double factor) {}

	private static final List<Calm> CALM = new ArrayList<>();

	static void register() {
		// Harvest Festival: 10 s (+2 s a rank, +1 s a rank of Festival Spirit)
		AbilityService.register(GROWER, (p, rank) -> Active.start(p, GROWER, rank, 200 + 40 * (rank - 1) + (int) Math.round(20 * rank(p, GROWER, P4))));
		// Herding Call: 10 s (+2 s a rank); its value is the twins still to come
		AbilityService.register(HERDER, (p, rank) -> Active.start(p, HERDER, rank, 200 + 40 * (rank - 1)).value = 1);
		// Rally the Pack: 15 s (+3 s a rank)
		AbilityService.register(BEASTMASTER, (p, rank) -> Active.start(p, BEASTMASTER, rank, 300 + 60 * (rank - 1)));
		ServerLivingEntityEvents.AFTER_DEATH.register(Farming::died);
	}

	// --- Crops -----------------------------------------------------------------------------------

	/** Crop Lore: crops +3% XP a rank. */
	public static double cropXp(ServerPlayer p) {
		return 1 + 0.03 * rank(p, GROWER, P2);
	}

	/** Bumper Crop: +1% a rank on the chance of an extra crop. */
	public static double extraCropChance(ServerPlayer p) {
		// (with Golden Harvest, a Mastery perk)
		return 0.01 * rank(p, GROWER, P1) + com.extremeprogression.mastery.Mastery.bonus(p, com.extremeprogression.mastery.MasteryPerk.GOLDEN_HARVEST);
	}

	/** Herder's price: no extra crops at all. */
	public static boolean noExtraCrops(ServerPlayer p) {
		return Specs.has(p, HERDER);
	}

	/**
	 * A fully grown crop was harvested. Harvest Festival: its drops once more, and it replants itself. Seed Saver: a
	 * 20% chance a rank that it replants itself.
	 */
	public static void harvested(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state, BlockEntity be) {
		boolean festival = Active.on(p, GROWER);
		if (festival && !noExtraCrops(p)) {
			for (ItemStack drop : Block.getDrops(state, level, pos, be, p, p.getMainHandItem())) {
				Block.popResource(level, pos, drop.copy());
			}
		}
		if ((festival || Specs.roll(p, 0.20 * rank(p, GROWER, C2))) && level.getBlockState(pos).isAir()
				&& (state.getBlock() instanceof CropBlock || state.getBlock() instanceof NetherWartBlock)) {
			BlockState young = state.getBlock().defaultBlockState();
			if (young.canSurvive(level, pos)) {
				level.setBlockAndUpdate(pos, young);
			}
		}
	}

	/** Tiller: a 5% chance a rank that a hoe use costs no durability. */
	static int durability(ItemStack stack, ServerPlayer p, int amount) {
		return stack.is(net.minecraft.tags.ItemTags.HOES) && Specs.roll(p, 0.05 * rank(p, GROWER, P3)) ? 0 : amount;
	}

	/** Ripening Aura: once a second, crops within 4 blocks get 3% more growth a rank (vanilla's random ticks, × 0.03 a rank). */
	private static void ripen(ServerPlayer p, double rank) {
		ServerLevel level = (ServerLevel) p.level();
		// a block gets about 3 / 4096 random ticks a tick, so 20 × 3 / 4096 a second; 3% more a rank of that
		double chance = 20 * 3 / 4096.0 * 0.03 * rank;
		BlockPos center = p.blockPosition();
		for (BlockPos at : BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, 1, 4))) {
			BlockState state = level.getBlockState(at);
			if (state.is(BlockTags.CROPS) && state.isRandomlyTicking() && p.getRandom().nextDouble() < chance) {
				state.randomTick(level, at.immutable(), level.getRandom());
			}
		}
	}

	// --- Animals ---------------------------------------------------------------------------------

	/** Breeder: breeding and shearing +3% XP a rank; Grower's price: breeding −50%. */
	public static double breedXp(ServerPlayer p) {
		double out = 1 + 0.03 * rank(p, HERDER, P1);
		return Specs.has(p, GROWER) ? out * 0.5 : out;
	}

	public static double shearXp(ServerPlayer p) {
		return 1 + 0.03 * rank(p, HERDER, P1);
	}

	/** Grower's price: breeding gets no bonuses (Growing Up, Quick Growth, Calm Herd, twins). */
	public static boolean noBreedingBonus(ServerPlayer p) {
		return Specs.has(p, GROWER);
	}

	/**
	 * Two animals were bred (before the game set their wait). Quick Growth: the baby grows up 4% faster a rank. Calm
	 * Herd: the parents may breed again 5% sooner a rank. Twins (4% a rank) or Herding Call's next breeding: a second
	 * baby.
	 */
	public static void bred(ServerPlayer p, Animal parent, Animal partner, AgeableMob child) {
		if (child == null || noBreedingBonus(p)) {
			return;
		}
		double quick = rank(p, HERDER, P2);
		if (quick > 0 && child.getAge() < 0) {
			child.setAge((int) (child.getAge() * (1 - 0.04 * quick)));
		}
		double calm = rank(p, HERDER, P4);
		if (calm > 0) {
			CALM.add(new Calm(parent, 1 - 0.05 * calm));
			CALM.add(new Calm(partner, 1 - 0.05 * calm));
		}
		Active.State call = Active.get(p, HERDER);
		boolean twins = call != null && call.value > 0;
		if (twins) {
			call.value = 0;
		}
		if ((twins || Specs.roll(p, 0.04 * rank(p, HERDER, C1))) && parent.level() instanceof ServerLevel level) {
			AgeableMob twin = parent.getBreedOffspring(level, partner);
			if (twin != null) {
				twin.setBaby(true);
				twin.setAge(child.getAge());
				twin.setPos(child.getX(), child.getY(), child.getZ());
				level.addFreshEntity(twin);
			}
		}
	}

	/** Wool Trade: a 20% chance a rank that a sheep you shear gives one more wool. */
	public static void sheared(ServerPlayer p, Sheep sheep) {
		if (Specs.roll(p, 0.20 * rank(p, HERDER, C2)) && sheep.level() instanceof ServerLevel level) {
			Item wool = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", sheep.getColor().getName() + "_wool"));
			if (wool != Items.AIR) {
				sheep.spawnAtLocation(level, new ItemStack(wool));
			}
		}
	}

	/** The meat a grown animal of this kind drops, or null. */
	private static Item meat(Entity e) {
		return switch (BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath()) {
			case "cow", "mooshroom" -> e.isOnFire() ? Items.COOKED_BEEF : Items.BEEF;
			case "pig" -> e.isOnFire() ? Items.COOKED_PORKCHOP : Items.PORKCHOP;
			case "sheep" -> e.isOnFire() ? Items.COOKED_MUTTON : Items.MUTTON;
			case "chicken" -> e.isOnFire() ? Items.COOKED_CHICKEN : Items.CHICKEN;
			case "rabbit" -> e.isOnFire() ? Items.COOKED_RABBIT : Items.RABBIT;
			default -> null;
		};
	}

	/** Butcher: a 10% chance a rank of one more meat; Wool Trade: 20% a rank of one more leather from a cow. */
	private static void died(LivingEntity entity, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer p) || !(entity instanceof Animal animal) || animal.isBaby()
				|| !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Item meat = meat(entity);
		if (meat != null && Specs.roll(p, 0.10 * rank(p, HERDER, P3))) {
			entity.spawnAtLocation(level, new ItemStack(meat));
		}
		String kind = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
		if ((kind.equals("cow") || kind.equals("mooshroom")) && Specs.roll(p, 0.20 * rank(p, HERDER, C2))) {
			entity.spawnAtLocation(level, new ItemStack(Items.LEATHER));
		}
	}

	// --- Tamed animals ---------------------------------------------------------------------------

	/** Tamer: taming and your animals' fighting +5% XP a rank. */
	public static double tamerXp(ServerPlayer p) {
		return 1 + 0.05 * rank(p, BEASTMASTER, P4);
	}

	private static boolean companion(ServerPlayer owner, Entity animal) {
		Set<UUID> set = COMPANIONS.get(owner.getUUID());
		return set != null && set.contains(animal.getUUID());
	}

	/**
	 * A hit by or on a tamed animal. Alpha Bond: a companion deals +4% a rank (Pack Leader +2% a rank more). Thick Fur:
	 * a companion takes 4% less a rank. Beastmaster's price: −10% on your own hits up close while a wolf of yours is near.
	 */
	static float damage(LivingEntity target, DamageSource source, float amount) {
		float out = amount;
		Entity attacker = source.getEntity();
		if (attacker instanceof TamableAnimal pet && pet.getOwner() instanceof ServerPlayer owner && companion(owner, pet)) {
			out *= 1 + 0.04f * rank(owner, BEASTMASTER, P1) + 0.02f * rank(owner, BEASTMASTER, C1);
		}
		if (target instanceof TamableAnimal pet && pet.getOwner() instanceof ServerPlayer owner && companion(owner, pet)) {
			out *= 1 - 0.04f * rank(owner, BEASTMASTER, P2);
		}
		if (attacker instanceof ServerPlayer p && source.getDirectEntity() == p && WOLF_NEAR.contains(p.getUUID()) && Specs.has(p, BEASTMASTER)) {
			out *= 0.90f;
		}
		return out;
	}

	/** How many companions: 1, or with Pack Leader 2 from rank I and 3 from rank III. */
	private static int companions(ServerPlayer p) {
		double leader = rank(p, BEASTMASTER, C1);
		return leader >= 3 ? 3 : leader >= 1 ? 2 : 1;
	}

	// --- Every tick ------------------------------------------------------------------------------

	static void tick(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		boolean second = (level.getServer().getTickCount() + p.getId()) % 20 == 0;
		// Herding Call: animals within 16 blocks follow you
		if (Active.on(p, HERDER) && (level.getServer().getTickCount() + p.getId()) % 10 == 0) {
			for (Entity e : level.getEntities(p, p.getBoundingBox().inflate(16), e -> e instanceof Animal a && !(a instanceof TamableAnimal t && t.isTame()))) {
				Animal animal = (Animal) e;
				if (animal.distanceTo(p) > 3) {
					animal.getNavigation().moveTo(p, 1.2);
				}
			}
		}
		if (second) {
			beasts(p, level);
			double aura = rank(p, GROWER, C1);
			if (aura > 0) {
				ripen(p, aura);
			}
		}
		rider(p);
	}

	/** Once a second: companions, Mending Pack, Rally the Pack, and whether a wolf is near. */
	private static void beasts(ServerPlayer p, ServerLevel level) {
		if (!Specs.has(p, BEASTMASTER)) {
			COMPANIONS.remove(p.getUUID());
			WOLF_NEAR.remove(p.getUUID());
			return;
		}
		List<TamableAnimal> mine = new ArrayList<>();
		for (Entity e : level.getEntities(p, p.getBoundingBox().inflate(24), e -> e instanceof TamableAnimal t && t.isTame() && t.isOwnedBy(p) && e.isAlive())) {
			mine.add((TamableAnimal) e);
		}
		mine.sort(Comparator.comparingDouble(t -> t.distanceToSqr(p)));
		Set<UUID> chosen = new HashSet<>();
		boolean wolfNear = false;
		double mend = rank(p, BEASTMASTER, P3);
		for (TamableAnimal t : mine) {
			boolean within16 = t.distanceToSqr(p) <= 16 * 16;
			if (within16 && t instanceof Wolf) {
				wolfNear = true;
			}
			if (within16 && chosen.size() < companions(p)) {
				chosen.add(t.getUUID());
				if (mend > 0 && t.getHealth() < t.getMaxHealth()) {
					t.heal((float) (0.1 * mend));
				}
			}
		}
		COMPANIONS.put(p.getUUID(), chosen);
		if (wolfNear) {
			WOLF_NEAR.add(p.getUUID());
		} else {
			WOLF_NEAR.remove(p.getUUID());
		}
		// Rally the Pack: Strength and Resistance, and your target
		if (Active.on(p, BEASTMASTER)) {
			LivingEntity target = p.getLastHurtMob() != null && p.getLastHurtMob().isAlive() ? p.getLastHurtMob() : p.getLastHurtByMob();
			for (TamableAnimal t : mine) {
				if (t instanceof Wolf wolf) {
					wolf.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, 0), p);
					wolf.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 0), p);
					if (target != null && target.isAlive() && target != wolf && !(target instanceof TamableAnimal tt && tt.isOwnedBy(p))
							&& target.distanceToSqr(wolf) <= 24 * 24) {
						wolf.setTarget(target);
					}
				}
			}
		}
	}

	/** Rider: the horse or camel you ride is 3% faster and jumps 2% higher a rank. */
	private static void rider(ServerPlayer p) {
		// (Rider needs Exploration 30: design §2, hard requirement)
		double rider = com.extremeprogression.skill.HardRequirement.RIDER.met(p)
				? rank(p, BEASTMASTER, C2) : 0;
		AbstractHorse now = rider > 0 && p.getVehicle() instanceof AbstractHorse h ? h : null;
		AbstractHorse before = RIDDEN.get(p.getUUID());
		if (before != null && before != now) {
			com.extremeprogression.trunk.Trunks.modifier(before, Attributes.MOVEMENT_SPEED, RIDER_SPEED, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			com.extremeprogression.trunk.Trunks.modifier(before, Attributes.JUMP_STRENGTH, RIDER_JUMP, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			RIDDEN.remove(p.getUUID());
		}
		if (now != null) {
			com.extremeprogression.trunk.Trunks.modifier(now, Attributes.MOVEMENT_SPEED, RIDER_SPEED, 0.03 * rider, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			com.extremeprogression.trunk.Trunks.modifier(now, Attributes.JUMP_STRENGTH, RIDER_JUMP, 0.02 * rider, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			RIDDEN.put(p.getUUID(), now);
		}
	}

	/** At the end of every server tick: Calm Herd shortens the waits the game has just set. */
	static void endOfTick() {
		for (Calm c : CALM) {
			if (c.animal.isAlive() && c.animal.getAge() > 0) {
				c.animal.setAge((int) (c.animal.getAge() * c.factor));
			}
		}
		CALM.clear();
	}

	static void forget(UUID id) {
		COMPANIONS.remove(id);
		WOLF_NEAR.remove(id);
		AbstractHorse horse = RIDDEN.remove(id);
		if (horse != null) {
			com.extremeprogression.trunk.Trunks.modifier(horse, Attributes.MOVEMENT_SPEED, RIDER_SPEED, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			com.extremeprogression.trunk.Trunks.modifier(horse, Attributes.JUMP_STRENGTH, RIDER_JUMP, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		}
	}
}
