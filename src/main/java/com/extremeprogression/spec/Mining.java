package com.extremeprogression.spec;

import static com.extremeprogression.skill.Specialization.EXCAVATOR;
import static com.extremeprogression.skill.Specialization.PROSPECTOR;
import static com.extremeprogression.skill.Specialization.TUNNELER;
import static com.extremeprogression.spec.Specs.rank;
import static com.extremeprogression.tree.NodeSlot.C1;
import static com.extremeprogression.tree.NodeSlot.C2;
import static com.extremeprogression.tree.NodeSlot.P1;
import static com.extremeprogression.tree.NodeSlot.P2;
import static com.extremeprogression.tree.NodeSlot.P3;
import static com.extremeprogression.tree.NodeSlot.P4;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.ability.AbilityService;
import com.extremeprogression.network.OreSensePayload;
import com.extremeprogression.trunk.Trunks;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Mining's three specializations (design §2, skill 4).
 * <ul>
 * <li><b>Prospector</b> (pickaxe): Ore Sense; Rich Vein, Prospector's Eye, Quick Pick, Lingering Sense; Gem Eye or
 * Broad Spectrum. Price: −10% breaking speed on plain stone and deepslate.</li>
 * <li><b>Excavator</b> (shovel, brush): Earthmover; Fast Dig, Finds, Shovel Saver, Patient Brush; Treasure Digger or
 * Archaeologist. Price: −15% breaking speed with a pickaxe.</li>
 * <li><b>Tunneler</b> (pickaxe): Tunnel Bore; Momentum, Cave Runner, Strong Back, Bore Master; Deep Delver or
 * Enduring. Price: no double drops at all, and ores give −25% XP.</li>
 * </ul>
 * Earthmover and Tunnel Bore break the blocks around the one you break, across the face you hit, as if you broke each
 * yourself: each gives its own XP and drops and costs durability. They take only blocks of their tool, without a
 * block entity, and no harder than twice the first.
 */
public final class Mining {
	private Mining() {}

	private static final Identifier CAVE_RUNNER = ExtremeProgression.id("spec/cave_runner");
	/** Ore Sense shows at most this many ores. */
	private static final int MAX_ORES = 512;

	/** The face of the block each player is breaking, seen just before it breaks. */
	private static final Map<UUID, Direction> FACE = new HashMap<>();
	/** While the blocks around one are broken: they do not spread further. */
	private static boolean breakingAround = false;

	static void register() {
		AbilityService.register(PROSPECTOR, Mining::oreSense);
		// Earthmover: 8 s (+1 s a rank); Tunnel Bore: 8 s (+1 s a rank, +1 s a rank of Bore Master)
		AbilityService.register(EXCAVATOR, (p, rank) -> Active.start(p, EXCAVATOR, rank, 160 + 20 * (rank - 1)));
		AbilityService.register(TUNNELER, (p, rank) -> Active.start(p, TUNNELER, rank, 160 + 20 * (rank - 1) + 20 * rank(p, TUNNELER, P4)));
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (!level.isClientSide() && !breakingAround && player instanceof ServerPlayer p && (Active.on(p, EXCAVATOR) || Active.on(p, TUNNELER))) {
				HitResult hit = p.pick(p.blockInteractionRange(), 1.0f, false);
				FACE.put(p.getUUID(), hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK ? b.getDirection()
						: facing(p).getOpposite());
			}
			return true;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (!breakingAround && player instanceof ServerPlayer p && level instanceof ServerLevel server) {
				around(p, server, pos, state);
			}
		});
	}

	// --- Ores ------------------------------------------------------------------------------------

	public static boolean isOre(BlockState state) {
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return id.endsWith("_ore") || state.is(Blocks.ANCIENT_DEBRIS);
	}

	private static boolean isGem(BlockState state) {
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return id.endsWith("diamond_ore") || id.endsWith("emerald_ore") || state.is(Blocks.ANCIENT_DEBRIS);
	}

	/** The colour an ore glows in. */
	private static int color(BlockState state) {
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (id.contains("diamond")) return 0x55FFFF;
		if (id.contains("emerald")) return 0x55FF55;
		if (id.contains("gold")) return 0xFFD700;
		if (id.contains("iron")) return 0xE0C0A0;
		if (id.contains("copper")) return 0xE77C56;
		if (id.contains("redstone")) return 0xFF3030;
		if (id.contains("lapis")) return 0x3060FF;
		if (id.contains("quartz")) return 0xF0F0F0;
		if (state.is(Blocks.ANCIENT_DEBRIS)) return 0xA0522D;
		return 0x808080;
	}

	/**
	 * Ore Sense: for 10 seconds the ores within 12 blocks (+2 a rank) glow for you. Gem Eye: only diamond, emerald and
	 * ancient debris, 4 blocks further a rank. Broad Spectrum: +2 s a rank. Lingering Sense: +1 s a rank.
	 */
	private static void oreSense(ServerPlayer p, int rank) {
		int ticks = 20 * (10 + rank(p, PROSPECTOR, P4) + 2 * rank(p, PROSPECTOR, C2));
		Active.start(p, PROSPECTOR, rank, ticks);
		List<OreSensePayload.Ore> ores = sense(p, rank);
		if (ServerPlayNetworking.canSend(p, OreSensePayload.TYPE)) {
			ServerPlayNetworking.send(p, new OreSensePayload(ores, ticks));
		}
	}

	/** The ores Ore Sense shows this player at a rank (at most 512). */
	public static List<OreSensePayload.Ore> sense(ServerPlayer p, int rank) {
		int gem = rank(p, PROSPECTOR, C1);
		int radius = 12 + 2 * (rank - 1) + 4 * gem;
		List<OreSensePayload.Ore> ores = new ArrayList<>();
		ServerLevel level = (ServerLevel) p.level();
		BlockPos center = p.blockPosition();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int r2 = radius * radius;
		scan:
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (dx * dx + dy * dy + dz * dz > r2) {
						continue;
					}
					at.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					BlockState state = level.getBlockState(at);
					if (gem > 0 ? isGem(state) : isOre(state)) {
						ores.add(new OreSensePayload.Ore(at.immutable(), color(state)));
						if (ores.size() >= MAX_ORES) {
							break scan;
						}
					}
				}
			}
		}
		return ores;
	}

	// --- Breaking --------------------------------------------------------------------------------

	/** Earthmover and Tunnel Bore: the blocks around the one just broken. */
	private static void around(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state) {
		Direction face = FACE.remove(p.getUUID());
		ItemStack tool = p.getMainHandItem();
		Active.State earth = Active.get(p, EXCAVATOR);
		Active.State bore = Active.get(p, TUNNELER);
		TagKey<Block> tag;
		boolean wide;
		if (earth != null && tool.is(ItemTags.SHOVELS) && state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
			tag = BlockTags.MINEABLE_WITH_SHOVEL;
			wide = true;
		} else if (bore != null && tool.is(ItemTags.PICKAXES) && state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			tag = BlockTags.MINEABLE_WITH_PICKAXE;
			wide = bore.rank >= 5;
		} else {
			return;
		}
		if (face == null) {
			face = facing(p).getOpposite();
		}
		float hardest = Math.max(0, state.getDestroySpeed(level, pos)) * 2 + 0.5f;
		breakingAround = true;
		try {
			for (BlockPos q : pattern(pos, face, wide, p)) {
				if (p.getMainHandItem().isEmpty()) {
					break;
				}
				BlockState s = level.getBlockState(q);
				float hardness = s.getDestroySpeed(level, q);
				if (s.isAir() || !s.is(tag) || hardness < 0 || hardness > hardest || level.getBlockEntity(q) != null || !p.hasCorrectToolForDrops(s)
						&& tag == BlockTags.MINEABLE_WITH_PICKAXE) {
					continue;
				}
				p.gameMode.destroyBlock(q);
			}
		} finally {
			breakingAround = false;
		}
	}

	/** The direction the player looks in most: up, down, or the way they face. */
	private static Direction facing(Player p) {
		double y = p.getLookAngle().y;
		return y > 0.7 ? Direction.UP : y < -0.7 ? Direction.DOWN : p.getDirection();
	}

	/**
	 * The other blocks across the face: 3 × 3 around it, or 1 × 3 (the block above and below; looking up or down, the
	 * ones before and behind it in the direction you face).
	 */
	static List<BlockPos> pattern(BlockPos pos, Direction face, boolean wide, Player p) {
		List<BlockPos> out = new ArrayList<>();
		Direction.Axis axis = face.getAxis();
		if (wide) {
			for (int a = -1; a <= 1; a++) {
				for (int b = -1; b <= 1; b++) {
					if (a == 0 && b == 0) {
						continue;
					}
					out.add(switch (axis) {
						case X -> pos.offset(0, a, b);
						case Y -> pos.offset(a, 0, b);
						case Z -> pos.offset(a, b, 0);
					});
				}
			}
		} else if (axis == Direction.Axis.Y) {
			Direction facing = p.getDirection();
			out.add(pos.relative(facing));
			out.add(pos.relative(facing.getOpposite()));
		} else {
			out.add(pos.above());
			out.add(pos.below());
		}
		return out;
	}

	/** Breaking speed: the prices and passives of all three. */
	public static float destroySpeed(Player p, BlockState state, float speed) {
		ItemStack tool = p.getMainHandItem();
		boolean pickaxe = tool.is(ItemTags.PICKAXES);
		float out = speed;
		if (Specs.has(p, PROSPECTOR) && state.is(BlockTags.BASE_STONE_OVERWORLD)) {
			out *= 0.90f;
		}
		if (pickaxe && isOre(state)) {
			out *= 1 + 0.03f * rank(p, PROSPECTOR, P3);
		}
		if (pickaxe && Specs.has(p, EXCAVATOR)) {
			out *= 0.85f;
		}
		if (tool.is(ItemTags.SHOVELS)) {
			out *= 1 + 0.03f * rank(p, EXCAVATOR, P1);
		}
		if (pickaxe) {
			out *= 1 + 0.02f * rank(p, TUNNELER, P1);
			if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("deepslate")) {
				out *= 1 + 0.20f * rank(p, TUNNELER, C1);
			}
		}
		return out;
	}

	/** Shovel Saver, Strong Back, and Enduring during Tunnel Bore: a use that costs no durability. */
	static int durability(ItemStack stack, ServerPlayer p, int amount) {
		if (stack.is(ItemTags.SHOVELS) || stack.is(Items.BRUSH)) {
			return Specs.roll(p, 0.05 * rank(p, EXCAVATOR, P3)) ? 0 : amount;
		}
		if (stack.is(ItemTags.PICKAXES)) {
			if (Specs.roll(p, 0.05 * rank(p, TUNNELER, P3)) || Active.on(p, TUNNELER) && Specs.roll(p, 0.10 * rank(p, TUNNELER, C2))) {
				return 0;
			}
		}
		return amount;
	}

	// --- XP and yields ---------------------------------------------------------------------------

	/** Mining XP for a block: Prospector's Eye +3% a rank on ores; Tunneler's price −25% on ores. */
	public static double xp(ServerPlayer p, BlockState state, double xp) {
		if (!isOre(state)) {
			return xp;
		}
		double out = xp * (1 + 0.03 * rank(p, PROSPECTOR, P2));
		return Specs.has(p, TUNNELER) ? out * 0.75 : out;
	}

	/** Rich Vein: +1% a rank on the chance of a double ore. */
	public static double doubleOreChance(ServerPlayer p) {
		return 0.01 * rank(p, PROSPECTOR, P1);
	}

	/** Tunneler's price: no double drops at all. */
	public static boolean noDoubles(ServerPlayer p) {
		return Specs.has(p, TUNNELER);
	}

	private static final Item[] FINDS = {Items.FLINT, Items.CLAY_BALL, Items.BONE, Items.GOLD_NUGGET, Items.STICK, Items.STRING};
	private static final Item[] RARE_FINDS = {Items.ARCHER_POTTERY_SHERD, Items.ARMS_UP_POTTERY_SHERD, Items.PRIZE_POTTERY_SHERD, Items.SKULL_POTTERY_SHERD,
			Items.MINER_POTTERY_SHERD, Items.HEART_POTTERY_SHERD, Items.EMERALD};

	/** Finds and Treasure Digger: a block dug with a shovel (never a placed one) may hold a find. */
	public static void dug(ServerPlayer p, ServerLevel level, BlockPos pos) {
		int finds = rank(p, EXCAVATOR, P2);
		int treasure = rank(p, EXCAVATOR, C1);
		if (!Specs.roll(p, 0.01 * (finds + treasure))) {
			return;
		}
		boolean rare = treasure > 0 && p.getRandom().nextInt(10) == 0;
		Item[] from = rare ? RARE_FINDS : FINDS;
		Block.popResource(level, pos, new ItemStack(from[p.getRandom().nextInt(from.length)]));
	}

	/** Patient Brush: +10% a rank on brushing XP. */
	public static double brushXp(ServerPlayer p) {
		return 1 + 0.10 * rank(p, EXCAVATOR, P4);
	}

	/** Archaeologist: a 10% chance a rank that a brushed find comes twice. */
	public static void brushed(ServerPlayer p, BrushableBlockEntity be) {
		ItemStack find = be.getItem();
		if (find.isEmpty() || be.getLevel() == null || !Specs.roll(p, 0.10 * rank(p, EXCAVATOR, C2))) {
			return;
		}
		BlockPos at = be.getBlockPos();
		ItemEntity copy = new ItemEntity(be.getLevel(), at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, find.copy());
		be.getLevel().addFreshEntity(copy);
	}

	// --- Every tick ------------------------------------------------------------------------------

	static void tick(ServerPlayer p) {
		// Cave Runner (Tunneler): +2% speed a rank where the sky is out of sight
		int runner = rank(p, TUNNELER, P2);
		boolean under = runner > 0 && !p.level().canSeeSky(p.blockPosition().above());
		Trunks.modifier(p, Attributes.MOVEMENT_SPEED, CAVE_RUNNER, under ? 0.02 * runner : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	static void forget(UUID id) {
		FACE.remove(id);
	}
}
