package com.extremeprogression.spec;

import static com.extremeprogression.skill.Specialization.ANGLER;
import static com.extremeprogression.skill.Specialization.HOOKMASTER;
import static com.extremeprogression.skill.Specialization.TREASURE_HUNTER;
import static com.extremeprogression.spec.Specs.rank;
import static com.extremeprogression.tree.NodeSlot.C1;
import static com.extremeprogression.tree.NodeSlot.C2;
import static com.extremeprogression.tree.NodeSlot.P1;
import static com.extremeprogression.tree.NodeSlot.P2;
import static com.extremeprogression.tree.NodeSlot.P3;
import static com.extremeprogression.tree.NodeSlot.P4;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import com.extremeprogression.ability.AbilityService;
import com.extremeprogression.mixin.FishingHookAccess;
import com.extremeprogression.xp.XpTables;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Fishing's three specializations (design §2, skill 7). A catch is fish, treasure or junk as the XP table counts it.
 * <ul>
 * <li><b>Angler</b>: Feeding Frenzy; Quick Bite, Fishmonger, Double Catch, Strong Line; Ice Fishing or Big Catch. Price:
 * −50% treasure (half the treasure catches become fish).</li>
 * <li><b>Treasure Hunter</b>: Lucky Cast; Treasure Sense, Appraiser, Clean Water, Old Salt; Deep Sea or Bookworm.
 * Price: fish bite 25% slower.</li>
 * <li><b>Hookmaster</b>: Reel In; Barbed Hook, Long Cast, Snag, Hook Fighter; Shake or Drag Hook. Price: catches give
 * −30% XP.</li>
 * </ul>
 * A different catch is rolled from vanilla's own fishing tables (fish, treasure, junk), so data packs keep their say.
 * Lucky Cast only works in open water, where the XP rule lets treasure count.
 */
public final class Fishing {
	private Fishing() {}

	/** What each player's hook had hooked last tick, so a new hook is seen once. */
	private static final Map<UUID, Entity> HOOKED = new HashMap<>();
	/** The fraction of a tick of waiting carried over, per player (faster or slower bites). */
	private static final Map<UUID, Double> WAIT = new HashMap<>();
	/** Mobs that have already given their Shake drop. */
	private static final Set<Entity> SHAKEN = Collections.newSetFromMap(new WeakHashMap<>());
	/** Hooks that already melted their ice. */
	private static final Set<Entity> MELTED = Collections.newSetFromMap(new WeakHashMap<>());

	public enum Kind { FISH, TREASURE, JUNK }

	static void register() {
		// Feeding Frenzy: 20 s (+4 s a rank)
		AbilityService.register(ANGLER, (p, rank) -> Active.start(p, ANGLER, rank, 400 + 80 * (rank - 1)));
		// Lucky Cast: 30 s (+10 s a rank); its value is the treasure catches still to come (2 from rank III, 3 at V)
		AbilityService.register(TREASURE_HUNTER, (p, rank) -> Active.start(p, TREASURE_HUNTER, rank, 600 + 200 * (rank - 1)).value = 1 + (rank - 1) / 2);
		// Reel In: the next mob hooked within 10 seconds
		AbilityService.register(HOOKMASTER, (p, rank) -> Active.start(p, HOOKMASTER, rank, 200));
	}

	public static Kind kind(ItemStack stack) {
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (id.equals("cod") || id.equals("salmon") || id.equals("pufferfish") || id.equals("tropical_fish")) {
			return Kind.FISH;
		}
		return XpTables.caught(stack) > XpTables.JUNK ? Kind.TREASURE : Kind.JUNK;
	}

	private static Kind kind(Collection<ItemStack> items) {
		Kind out = Kind.JUNK;
		for (ItemStack stack : items) {
			Kind k = kind(stack);
			if (k == Kind.TREASURE) {
				return k;
			}
			if (k == Kind.FISH) {
				out = k;
			}
		}
		return out;
	}

	// --- Catches ---------------------------------------------------------------------------------

	/** A rolled catch from one of vanilla's fishing tables. */
	private static List<ItemStack> roll(ServerPlayer p, FishingHook hook, ItemStack rod, ResourceKey<LootTable> table) {
		ServerLevel level = (ServerLevel) hook.level();
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, hook.position())
				.withParameter(LootContextParams.TOOL, rod).withParameter(LootContextParams.THIS_ENTITY, hook).withLuck(p.getLuck())
				.create(LootContextParamSets.FISHING);
		return level.getServer().reloadableRegistries().getLootTable(table).getRandomItems(params);
	}

	private static void replace(Collection<ItemStack> items, List<ItemStack> with) {
		items.clear();
		items.addAll(with);
	}

	/**
	 * A catch about to be reeled in (its items are the ones the hook will throw to you): Lucky Cast, Treasure Sense,
	 * Angler's price, Clean Water, Deep Sea, Double Catch and Bookworm change it. Runs before the XP is counted.
	 */
	public static void caught(ServerPlayer p, FishingHook hook, ItemStack rod, Collection<ItemStack> items) {
		if (items.isEmpty()) {
			return;
		}
		try {
			Kind kind = kind(items);
			Active.State lucky = Active.get(p, TREASURE_HUNTER);
			if (lucky != null && lucky.value > 0 && hook.isOpenWaterFishing()) {
				replace(items, roll(p, hook, rod, BuiltInLootTables.FISHING_TREASURE));
				if (--lucky.value <= 0) {
					Active.end(p, TREASURE_HUNTER);
				}
			} else if (kind == Kind.FISH && Specs.roll(p, 0.01 * rank(p, TREASURE_HUNTER, P1))) {
				replace(items, roll(p, hook, rod, BuiltInLootTables.FISHING_TREASURE));
			} else if (kind == Kind.TREASURE && Specs.has(p, ANGLER) && p.getRandom().nextBoolean()) {
				replace(items, roll(p, hook, rod, BuiltInLootTables.FISHING_FISH));
			} else if (kind == Kind.JUNK && Specs.roll(p, 0.10 * rank(p, TREASURE_HUNTER, P3))) {
				replace(items, roll(p, hook, rod, BuiltInLootTables.FISHING_FISH));
			}
			// Deep Sea needs Exploration 40 (design §2, hard requirement)
			if (com.extremeprogression.data.PlayerStore.get(p.getUUID()).get(com.extremeprogression.skill.Skill.EXPLORATION).level >= 40
					&& Specs.roll(p, 0.002 * rank(p, TREASURE_HUNTER, C1))) {
				replace(items, List.of(new ItemStack(p.getRandom().nextBoolean() ? Items.HEART_OF_THE_SEA : Items.TRIDENT)));
			}
			if (kind(items) == Kind.FISH && Specs.roll(p, 0.03 * rank(p, ANGLER, P3))) {
				items.add(items.iterator().next().copy());
			}
			int bookworm = rank(p, TREASURE_HUNTER, C2);
			if (bookworm > 0) {
				for (ItemStack stack : items) {
					if (stack.is(Items.ENCHANTED_BOOK)) {
						EnchantmentHelper.updateEnchantments(stack, book -> {
							for (Holder<Enchantment> e : List.copyOf(book.keySet())) {
								if (Specs.roll(p, 0.20 * bookworm) && book.getLevel(e) < e.value().getMaxLevel()) {
									book.set(e, book.getLevel(e) + 1);
								}
							}
						});
					}
				}
			}
		} catch (UnsupportedOperationException fixed) {
			// (a catch another mod made unchangeable stays as it is)
		}
	}

	/** Fishing XP of one item: Fishmonger +3% a rank on fish, Appraiser +4% a rank on treasure; Hookmaster's price −30%. */
	public static double xp(ServerPlayer p, ItemStack stack) {
		double out = switch (kind(stack)) {
			case FISH -> 1 + 0.03 * rank(p, ANGLER, P2);
			case TREASURE -> 1 + 0.04 * rank(p, TREASURE_HUNTER, P2);
			case JUNK -> 1;
		};
		return Specs.has(p, HOOKMASTER) ? out * 0.7 : out;
	}

	/** Strong Line: a 5% chance a rank that a catch costs the rod no durability. */
	static int durability(ItemStack stack, ServerPlayer p, int amount) {
		return stack.is(Items.FISHING_ROD) && Specs.roll(p, 0.05 * rank(p, ANGLER, P4)) ? 0 : amount;
	}

	/** Big Catch: fish fill 0.4 more hunger a rank, rounded. */
	public static void ate(ServerPlayer p, ItemStack food) {
		int big = rank(p, ANGLER, C2);
		String id = BuiltInRegistries.ITEM.getKey(food.getItem()).getPath();
		if (big > 0 && (id.endsWith("cod") || id.endsWith("salmon") || id.equals("tropical_fish"))) {
			FoodData data = p.getFoodData();
			data.setFoodLevel(Math.min(20, data.getFoodLevel() + Math.round(0.4f * big)));
		}
	}

	// --- The hook --------------------------------------------------------------------------------

	/** A cast: Long Cast, the bobber flies 5% farther a rank. */
	static void cast(ServerPlayer p, FishingHook hook) {
		int far = rank(p, HOOKMASTER, P2);
		if (far > 0) {
			hook.setDeltaMovement(hook.getDeltaMovement().scale(1 + 0.05 * far));
		}
	}

	/**
	 * How long the wait for a bite is, as a factor: Feeding Frenzy 0.2; Quick Bite −3% a rank; Old Salt −4% a rank;
	 * Treasure Hunter's price ×1.25.
	 */
	public static double waitFactor(ServerPlayer p) {
		double m = Active.on(p, ANGLER) ? 0.2 : 1;
		m *= 1 - 0.03 * rank(p, ANGLER, P1);
		m *= 1 - 0.04 * rank(p, TREASURE_HUNTER, P4);
		if (Specs.has(p, TREASURE_HUNTER)) {
			m *= 1.25;
		}
		return m;
	}

	static void tick(ServerPlayer p) {
		FishingHook hook = p.fishing;
		if (hook == null || hook.isRemoved()) {
			HOOKED.remove(p.getUUID());
			return;
		}
		// The wait for a bite counts down 1 / factor as fast (the fraction carried over to the next tick)
		FishingHookAccess access = (FishingHookAccess) hook;
		int wait = access.extremeprogression$getTimeUntilLured();
		double factor = waitFactor(p);
		if (wait > 1 && factor != 1) {
			double carry = WAIT.getOrDefault(p.getUUID(), 0.0) + 1 / factor - 1;
			int whole = (int) carry;
			if (whole != 0) {
				access.extremeprogression$setTimeUntilLured(Math.max(1, wait - whole));
				carry -= whole;
			}
			WAIT.put(p.getUUID(), carry);
		}
		// Something newly hooked
		Entity in = hook.getHookedIn();
		if (in != null && in != HOOKED.get(p.getUUID())) {
			hooked(p, hook, in);
		}
		HOOKED.put(p.getUUID(), in);
		iceFishing(p, hook);
	}

	/** Barbed Hook, Snag, Shake and Reel In, when the hook catches a mob or player. */
	private static void hooked(ServerPlayer p, FishingHook hook, Entity in) {
		if (!(in instanceof LivingEntity target)) {
			return;
		}
		int barbed = rank(p, HOOKMASTER, P1);
		if (barbed > 0) {
			Specs.deal(target, p.damageSources().playerAttack(p), 0.5f * barbed);
		}
		int snag = rank(p, HOOKMASTER, P3);
		if (snag > 0) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * snag, 0), p);
		}
		if (target instanceof Mob mob && !SHAKEN.contains(mob) && Specs.roll(p, 0.20 * rank(p, HOOKMASTER, C1))) {
			SHAKEN.add(mob);
			shake(p, mob);
		}
		Active.State reel = Active.get(p, HOOKMASTER);
		if (reel != null && !(target instanceof Player)) {
			Active.end(p, HOOKMASTER);
			Vec3 toward = p.position().subtract(target.position());
			double dist = toward.horizontalDistance();
			if (dist > 1.5) {
				Vec3 flat = toward.horizontal().normalize().scale(Math.min(2.0, (dist - 1.5) * 0.14));
				target.setDeltaMovement(flat.x, 0.4, flat.z);
				target.needsSync = true;
				target.syncVelocity = true;
			}
			Wounds.stun(target, 40 + 10 * (reel.rank - 1));
		}
	}

	/** Shake: one of the mob's normal drops, from its own loot table. */
	private static void shake(ServerPlayer p, Mob mob) {
		if (!(mob.level() instanceof ServerLevel level) || mob.getLootTable().isEmpty()) {
			return;
		}
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, mob)
				.withParameter(LootContextParams.ORIGIN, mob.position()).withParameter(LootContextParams.DAMAGE_SOURCE, p.damageSources().playerAttack(p))
				.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, p).withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, p)
				.withLuck(p.getLuck()).create(LootContextParamSets.ENTITY);
		List<ItemStack> drops = level.getServer().reloadableRegistries().getLootTable(mob.getLootTable().get()).getRandomItems(params);
		if (!drops.isEmpty()) {
			ItemStack one = drops.get(p.getRandom().nextInt(drops.size())).copy();
			one.setCount(1);
			mob.spawnAtLocation(level, one);
		}
	}

	/** Drag Hook: pulling a mob or player is 20% stronger a rank (after vanilla's own pull). */
	public static void pulled(FishingHook hook, Entity target) {
		if (!(hook.getOwner() instanceof ServerPlayer p)) {
			return;
		}
		int drag = rank(p, HOOKMASTER, C2);
		if (drag > 0) {
			Vec3 pull = p.position().subtract(target.position()).scale(0.1 * 0.2 * drag);
			target.setDeltaMovement(target.getDeltaMovement().add(pull));
			target.needsSync = true;
			target.syncVelocity = true;
		}
	}

	/** Hook Fighter: +3% a rank on your hits up close against what is on your hook. */
	static float outgoing(ServerPlayer p, LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (source.getDirectEntity() == p && p.fishing != null && p.fishing.getHookedIn() == target) {
			return amount * (1 + 0.03f * rank(p, HOOKMASTER, P4));
		}
		return amount;
	}

	/**
	 * Ice Fishing: a bobber resting on ice melts it into water: 3 × 3 at rank I, 5 × 5 from II, 7 × 7 from IV; packed ice
	 * too from rank III, blue ice at V. Once per cast.
	 */
	private static void iceFishing(ServerPlayer p, FishingHook hook) {
		int ice = rank(p, ANGLER, C1);
		if (ice == 0 || MELTED.contains(hook) || hook.isInWater()) {
			return;
		}
		BlockPos under = BlockPos.containing(hook.getX(), hook.getY() - 0.1, hook.getZ());
		if (!meltable(hook.level().getBlockState(under), ice)) {
			return;
		}
		MELTED.add(hook);
		int radius = ice >= 4 ? 3 : ice >= 2 ? 2 : 1;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				BlockPos at = under.offset(dx, 0, dz);
				if (meltable(hook.level().getBlockState(at), ice)) {
					hook.level().setBlockAndUpdate(at, Blocks.WATER.defaultBlockState());
				}
			}
		}
	}

	private static boolean meltable(BlockState state, int rank) {
		return state.is(Blocks.ICE) || rank >= 3 && state.is(Blocks.PACKED_ICE) || rank >= 5 && state.is(Blocks.BLUE_ICE);
	}

	static void forget(UUID id) {
		HOOKED.remove(id);
		WAIT.remove(id);
	}

}
