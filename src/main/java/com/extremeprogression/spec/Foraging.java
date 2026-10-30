package com.extremeprogression.spec;

import static com.extremeprogression.skill.Specialization.GATHERER;
import static com.extremeprogression.skill.Specialization.HERBALIST;
import static com.extremeprogression.skill.Specialization.LUMBERJACK;
import static com.extremeprogression.spec.Specs.rank;
import static com.extremeprogression.tree.NodeSlot.C1;
import static com.extremeprogression.tree.NodeSlot.C2;
import static com.extremeprogression.tree.NodeSlot.P1;
import static com.extremeprogression.tree.NodeSlot.P2;
import static com.extremeprogression.tree.NodeSlot.P3;
import static com.extremeprogression.tree.NodeSlot.P4;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.extremeprogression.ability.AbilityService;
import com.extremeprogression.antiexploit.PlacedBlocks;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Foraging's three specializations (design §2, skill 5). "Wild plants" are the ones that give Foraging XP: what grew by
 * itself, never placed or planted.
 * <ul>
 * <li><b>Lumberjack</b> (axe): Tree Feller; Sharp Axe, Timber, Woodsman, Steady Swing; Wide Fell or Thrifty. Price: wild
 * plants give −50% XP and no bonus yield.</li>
 * <li><b>Gatherer</b>: Bountiful Harvest; Green Fingers, Plenty, Quick Picking, Season's Bounty; Rare Seeds or Honey
 * Harvest. Price: −15% chopping speed on logs.</li>
 * <li><b>Herbalist</b>: Wild Remedy; Forager's Diet, Herb Knowledge, Strong Stomach, Nourished; Mushroom Lore or Berry
 * Lore. Price: cultivated food fills 20% less.</li>
 * </ul>
 */
public final class Foraging {
	private Foraging() {}

	/** While Tree Feller takes a tree: its logs do not start another, and Thrifty may spare the axe. */
	private static boolean felling = false;
	/** Wild hives a Honey Harvest player is taking honey from this tick: their bees stay calm. */
	private static final Map<BlockPos, Long> CALM = new HashMap<>();

	static void register() {
		// Tree Feller: the next tree within 6 seconds
		AbilityService.register(LUMBERJACK, (p, rank) -> Active.start(p, LUMBERJACK, rank, 120));
		// Bountiful Harvest: 10 s (+2 s a rank, +1 s a rank of Season's Bounty); Wild Remedy: 30 s (+5 s a rank)
		AbilityService.register(GATHERER, (p, rank) -> Active.start(p, GATHERER, rank, 200 + 40 * (rank - 1) + 20 * rank(p, GATHERER, P4)));
		AbilityService.register(HERBALIST, (p, rank) -> Active.start(p, HERBALIST, rank, 600 + 100 * (rank - 1)));
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (!felling && player instanceof ServerPlayer p && level instanceof ServerLevel server && state.is(BlockTags.LOGS)
					&& p.getMainHandItem().is(ItemTags.AXES)) {
				Active.State feller = Active.get(p, LUMBERJACK);
				if (feller != null) {
					Active.end(p, LUMBERJACK);
					fell(p, server, pos, feller.rank);
				}
			}
		});
	}

	// --- Lumberjack ------------------------------------------------------------------------------

	/** Tree Feller: up to 16 logs (+12 a rank, +8 a rank of Wide Fell), the one just felled included. Never placed logs. */
	private static void fell(ServerPlayer p, ServerLevel level, BlockPos start, int rank) {
		int max = 16 + 12 * (rank - 1) + 8 * rank(p, LUMBERJACK, C1) - 1;
		List<BlockPos> logs = connectedLogs(level, start, max);
		felling = true;
		try {
			for (BlockPos log : logs) {
				if (!p.getMainHandItem().is(ItemTags.AXES)) {
					break;
				}
				p.gameMode.destroyBlock(log);
			}
		} finally {
			felling = false;
		}
	}

	/** The logs touching this place (diagonals too), nearest first, up to a number; placed logs are left out. */
	static List<BlockPos> connectedLogs(Level level, BlockPos start, int max) {
		List<BlockPos> out = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		seen.add(start);
		queue.add(start);
		while (!queue.isEmpty() && out.size() < max) {
			BlockPos at = queue.poll();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos n = at.offset(dx, dy, dz);
						if (out.size() >= max || !seen.add(n)) {
							continue;
						}
						if (level.getBlockState(n).is(BlockTags.LOGS) && (PlacedBlocks.flags(level, n) & PlacedBlocks.PLACED) == 0) {
							out.add(n);
							queue.add(n);
						}
					}
				}
			}
		}
		return out;
	}

	/** Steady Swing: 5% a rank, any axe use; Thrifty: 10% a rank, a log Tree Feller takes. No durability. */
	static int durability(ItemStack stack, ServerPlayer p, int amount) {
		if (stack.is(ItemTags.AXES) && (Specs.roll(p, 0.05 * rank(p, LUMBERJACK, P4)) || felling && Specs.roll(p, 0.10 * rank(p, LUMBERJACK, C2)))) {
			return 0;
		}
		return amount;
	}

	/** Woodsman: logs +3% XP a rank. */
	public static double logXp(ServerPlayer p) {
		return 1 + 0.03 * rank(p, LUMBERJACK, P3);
	}

	/** Timber: +1% a rank on the chance of a double log. */
	public static double doubleLogChance(ServerPlayer p) {
		return 0.01 * rank(p, LUMBERJACK, P2);
	}

	// --- Gatherer --------------------------------------------------------------------------------

	/** Wild plants' XP: Green Fingers +3% a rank; Lumberjack's price −50%. */
	public static double wildXp(ServerPlayer p) {
		double out = 1 + 0.03 * rank(p, GATHERER, P1);
		return Specs.has(p, LUMBERJACK) ? out * 0.5 : out;
	}

	/** How many extra yields a wild harvest gives: Bountiful Harvest 2 (3× in all), Plenty a chance of 1; none for a Lumberjack. */
	private static int extraYields(ServerPlayer p, boolean honey) {
		if (Specs.has(p, LUMBERJACK)) {
			return 0;
		}
		int extra = Active.on(p, GATHERER) ? 2 : 0;
		if (Specs.roll(p, 0.04 * rank(p, GATHERER, P2))) {
			extra++;
		}
		if (honey && Specs.roll(p, 0.20 * rank(p, GATHERER, C2))) {
			extra++;
		}
		return extra;
	}

	/** A wild plant was broken: its extra yields. */
	public static void wildBroken(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state, BlockEntity be) {
		int extra = extraYields(p, false);
		for (int i = 0; i < extra; i++) {
			for (ItemStack drop : Block.getDrops(state, level, pos, be, p, p.getMainHandItem())) {
				Block.popResource(level, pos, drop.copy());
			}
		}
	}

	/** A wild plant was picked (berries, honey): its extra yields, of what the picking gave. */
	public static void wildPicked(ServerPlayer p, Level level, BlockPos pos, Item item, int count) {
		boolean honey = item == Items.HONEY_BOTTLE || item == Items.HONEYCOMB;
		int extra = extraYields(p, honey);
		for (int i = 0; i < extra; i++) {
			Block.popResource(level, pos, new ItemStack(item, count));
		}
	}

	/** Honey Harvest: a Gatherer with it takes honey from this hive now. */
	public static void takingHoney(ServerPlayer p, BlockPos pos) {
		if (rank(p, GATHERER, C2) > 0) {
			CALM.put(pos.immutable(), (long) p.level().getServer().getTickCount());
		}
	}

	/** Whether the bees of this hive stay calm (Honey Harvest, this tick). */
	public static boolean calm(Level level, BlockPos pos) {
		Long tick = CALM.get(pos);
		return tick != null && level.getServer() != null && tick == level.getServer().getTickCount();
	}

	/** Rare Seeds: grass or a fern that grew by itself, a 1% chance a rank of torchflower seeds or a pitcher pod. */
	public static void grass(ServerPlayer p, ServerLevel level, BlockPos pos) {
		if (Specs.roll(p, 0.01 * rank(p, GATHERER, C1))) {
			Block.popResource(level, pos, new ItemStack(p.getRandom().nextBoolean() ? Items.TORCHFLOWER_SEEDS : Items.PITCHER_POD));
		}
	}

	public static boolean isGrass(BlockState state) {
		return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN);
	}

	/** Breaking speed: Sharp Axe and Gatherer's price on logs; Quick Picking on melons, pumpkins, cocoa and mushroom blocks. */
	public static float destroySpeed(Player p, BlockState state, float speed) {
		float out = speed;
		if (state.is(BlockTags.LOGS)) {
			if (p.getMainHandItem().is(ItemTags.AXES)) {
				out *= 1 + 0.03f * rank(p, LUMBERJACK, P1);
			}
			if (Specs.has(p, GATHERER)) {
				out *= 0.85f;
			}
		}
		if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN) || state.is(Blocks.COCOA) || state.is(Blocks.RED_MUSHROOM_BLOCK)
				|| state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.MUSHROOM_STEM)) {
			out *= 1 + 0.05f * rank(p, GATHERER, P3);
		}
		return out;
	}

	// --- Herbalist -------------------------------------------------------------------------------

	private static final Set<String> WILD_FOOD = Set.of("sweet_berries", "glow_berries", "apple", "melon_slice", "mushroom_stew", "suspicious_stew");
	private static final Set<String> CULTIVATED = Set.of("bread", "baked_potato", "potato", "carrot", "golden_carrot", "beetroot", "beetroot_soup",
			"pumpkin_pie", "cookie", "rabbit_stew", "beef", "cooked_beef", "porkchop", "cooked_porkchop", "mutton", "cooked_mutton", "chicken",
			"cooked_chicken");

	/** After eating (the food already counted): Wild Remedy, the passives and choice nodes, and Herbalist's price. */
	public static void ate(ServerPlayer p, ItemStack food) {
		FoodProperties props = food.get(DataComponents.FOOD);
		if (props == null) {
			return;
		}
		String id = BuiltInRegistries.ITEM.getKey(food.getItem()).getPath();
		FoodData data = p.getFoodData();
		float saturation = 0;
		int hunger = 0;
		if (Active.on(p, HERBALIST)) {
			// Wild Remedy: double saturation, and 5 seconds of Regeneration I
			saturation += props.saturation();
			p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
		}
		if (WILD_FOOD.contains(id)) {
			// Forager's Diet: +10% saturation a rank
			saturation += props.saturation() * 0.10f * rank(p, HERBALIST, P1);
		}
		if (id.equals("mushroom_stew") || id.equals("suspicious_stew")) {
			int lore = rank(p, HERBALIST, C1);
			if (lore > 0) {
				p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40 * lore, 0));
			}
		}
		if (id.equals("sweet_berries") || id.equals("glow_berries")) {
			hunger += Math.round(0.4f * rank(p, HERBALIST, C2));
		}
		if (CULTIVATED.contains(id) && Specs.has(p, HERBALIST)) {
			// Herbalist's price: cultivated food fills 20% less
			hunger -= Math.round(props.nutrition() * 0.2f);
			saturation -= props.saturation() * 0.2f;
		}
		data.setFoodLevel(Math.max(0, Math.min(20, data.getFoodLevel() + hunger)));
		data.setSaturation(Math.max(0, Math.min(data.getFoodLevel(), data.getSaturationLevel() + saturation)));
		// Nourished: eating heals 0.4 a rank
		int nourished = rank(p, HERBALIST, P4);
		if (nourished > 0) {
			p.heal(0.4f * nourished);
		}
	}

	/** Herb Knowledge: Regeneration lasts 10% longer a rank. Strong Stomach: Hunger 20% shorter a rank (none at V). */
	static MobEffectInstance effect(LivingEntity target, MobEffectInstance effect) {
		if (!(target instanceof ServerPlayer p) || effect.getDuration() < 0) {
			return effect;
		}
		double factor = 1;
		if (effect.getEffect().equals(MobEffects.REGENERATION)) {
			factor = 1 + 0.10 * rank(p, HERBALIST, P2);
		} else if (effect.getEffect().equals(MobEffects.HUNGER)) {
			factor = 1 - 0.20 * rank(p, HERBALIST, P3);
		}
		if (factor <= 0) {
			return null;
		}
		if (factor == 1) {
			return effect;
		}
		return new MobEffectInstance(effect.getEffect(), Math.max(1, (int) Math.round(effect.getDuration() * factor)), effect.getAmplifier(),
				effect.isAmbient(), effect.isVisible(), effect.showIcon());
	}

	/** Every server tick: the calm hives of earlier ticks are forgotten. */
	static void tick(net.minecraft.server.MinecraftServer server) {
		if (!CALM.isEmpty()) {
			long now = server.getTickCount();
			CALM.values().removeIf(t -> t < now);
		}
	}
}
