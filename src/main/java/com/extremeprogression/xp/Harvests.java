package com.extremeprogression.xp;

import static com.extremeprogression.antiexploit.PlacedBlocks.BONEMEALED;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.extremeprogression.antiexploit.AfkTracker;
import com.extremeprogression.antiexploit.PlacedBlocks;
import com.extremeprogression.skill.Skill;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Harvests by hand that leave the block in place: picking berries, taking honey from a wild hive, shearing a sheep.
 * The use is noted when the player right-clicks, and the XP given at the end of the tick only if the harvest really
 * happened (another mod, or the game, may have stopped it). A dispenser shearing a sheep is no player, so no XP.
 */
public final class Harvests {
	private Harvests() {}

	private enum Kind { SWEET_BERRIES, GLOW_BERRIES, HONEY, SHEARING }

	/** A harvest noted at the click, with what it gives (for the wild bonuses of Foraging's specializations). */
	private record Pending(ServerPlayer player, Level level, BlockPos pos, Kind kind, Sheep sheep, Item item, int count) {}

	/** (one entry per block or sheep per tick: the event fires for both hands) */
	private static final Map<Object, Pending> PENDING = new LinkedHashMap<>();

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer p) {
				AfkTracker.interacted(p.getUUID(), level.getServer().getTickCount());
				noteBlock(p, level, hit.getBlockPos(), p.getItemInHand(hand));
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer p) {
				AfkTracker.interacted(p.getUUID(), level.getServer().getTickCount());
				if (entity instanceof Sheep sheep && sheep.readyForShearing() && p.getItemInHand(hand).is(Items.SHEARS)) {
					PENDING.putIfAbsent(sheep.getUUID(), new Pending(p, level, sheep.blockPosition(), Kind.SHEARING, sheep, null, 0));
				}
			}
			return InteractionResult.PASS;
		});
	}

	private static void noteBlock(ServerPlayer p, Level level, BlockPos pos, ItemStack held) {
		BlockState state = level.getBlockState(pos);
		Kind kind = null;
		Item item = null;
		int count = 1;
		if (state.getBlock() instanceof SweetBerryBushBlock && state.getValue(SweetBerryBushBlock.AGE) > 1) {
			kind = Kind.SWEET_BERRIES;
			item = Items.SWEET_BERRIES;
			count = state.getValue(SweetBerryBushBlock.AGE) == 3 ? 2 : 1;
		} else if (state.getBlock() instanceof CaveVines && CaveVines.hasGlowBerries(state)) {
			kind = Kind.GLOW_BERRIES;
			item = Items.GLOW_BERRIES;
		} else if (state.is(Blocks.BEE_NEST) && state.getValue(BeehiveBlock.HONEY_LEVEL) >= BeehiveBlock.MAX_HONEY_LEVELS
				&& (held.is(Items.GLASS_BOTTLE) || held.is(Items.SHEARS))) {
			kind = Kind.HONEY;
			item = held.is(Items.GLASS_BOTTLE) ? Items.HONEY_BOTTLE : Items.HONEYCOMB;
			count = held.is(Items.GLASS_BOTTLE) ? 1 : 3;
			com.extremeprogression.spec.Foraging.takingHoney(p, pos);
		}
		if (kind != null) {
			BlockPos at = pos.immutable();
			PENDING.putIfAbsent(at, new Pending(p, level, at, kind, null, item, count));
		}
	}

	/** At the end of the tick: which of the noted harvests happened. */
	public static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		for (Pending h : PENDING.values()) {
			if (h.player.isRemoved()) {
				continue;
			}
			check(h);
		}
		PENDING.clear();
	}

	private static void check(Pending h) {
		if (h.kind == Kind.SHEARING) {
			if (h.sheep.isSheared()) {
				XpService.award(h.player, Skill.FARMING, XpTables.SHEARING * com.extremeprogression.spec.Farming.shearXp(h.player));
				com.extremeprogression.spec.Farming.sheared(h.player, h.sheep);
			}
			return;
		}
		BlockState now = h.level.getBlockState(h.pos);
		boolean harvested = switch (h.kind) {
			case SWEET_BERRIES -> now.getBlock() instanceof SweetBerryBushBlock && now.getValue(SweetBerryBushBlock.AGE) == 1;
			case GLOW_BERRIES -> now.getBlock() instanceof CaveVines && !CaveVines.hasGlowBerries(now);
			case HONEY -> now.is(Blocks.BEE_NEST) && now.getValue(BeehiveBlock.HONEY_LEVEL) == 0;
			default -> false;
		};
		if (!harvested) {
			return;
		}
		int flags = PlacedBlocks.flags(h.level, h.pos);
		boolean wild = switch (h.kind) {
			case SWEET_BERRIES -> GatheringXp.wildOrPlanted(h.player, flags, XpTables.WILD_SWEET_BERRIES, XpTables.PLANTED_SWEET_BERRIES);
			case GLOW_BERRIES -> GatheringXp.wildOrPlanted(h.player, flags, XpTables.GLOW_BERRIES, 0);
			case HONEY -> GatheringXp.wild(h.player, flags, XpTables.WILD_HONEY);
			default -> false;
		};
		if (wild && h.item != null) {
			com.extremeprogression.spec.Foraging.wildPicked(h.player, h.level, h.pos, h.item, h.count);
		}
		// The berries bonemeal made are picked: what grows back by itself counts again
		if ((flags & BONEMEALED) != 0) {
			PlacedBlocks.set(h.level, h.pos, flags & ~BONEMEALED);
		}
	}

	public static void forget(UUID id) {
		PENDING.values().removeIf(h -> h.player.getUUID().equals(id));
	}
}
