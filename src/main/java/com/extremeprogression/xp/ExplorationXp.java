package com.extremeprogression.xp;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.extremeprogression.antiexploit.AfkTracker;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.ExplorationData;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.skill.Skill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Exploration XP (design §2, Appendix A.8): the first visit to a chunk (less with an elytra), a chunk left alone for a
 * month, the first time in a biome, the first time inside each structure (the first of each type ×3), and falls you
 * survive. Walking or swimming distance gives nothing. Chunks only count while the player can earn XP (not AFK, not in
 * Creative or Spectator), so nothing is used up while it cannot pay.
 */
public final class ExplorationXp {
	private ExplorationXp() {}

	/** The last chunk each player was in, as {@code "<dimension>|x|z"}. */
	private static final Map<UUID, String> LAST_CHUNK = new HashMap<>();

	private record Fall(String dimension, double x, double y, double z, long tick) {}

	private static final Map<UUID, Deque<Fall>> FALLS = new HashMap<>();

	public static void tick(MinecraftServer server) {
		long tick = server.getTickCount();
		long now = System.currentTimeMillis();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isCreative() || p.isSpectator() || AfkTracker.isAfk(p.getUUID(), tick)) {
				continue;
			}
			ServerLevel level = p.level();
			String dimension = level.dimension().identifier().toString();
			BlockPos pos = p.blockPosition();
			int cx = pos.getX() >> 4;
			int cz = pos.getZ() >> 4;
			String here = dimension + "|" + cx + "|" + cz;
			if (!here.equals(LAST_CHUNK.put(p.getUUID(), here))) {
				enteredChunk(p, dimension, cx, cz, now);
			}
			// (biomes and structures once a second, spread over the players)
			if ((tick + p.getId()) % 20 == 0) {
				biome(p, level, pos);
				structures(p, level, pos, dimension);
			}
		}
	}

	public static void enteredChunk(ServerPlayer p, String dimension, int cx, int cz, long now) {
		ExplorationData data = PlayerStore.get(p.getUUID()).exploration;
		ExplorationData.Visit visit = data.visit(dimension, cx, cz, now, Math.round(Config.REVISIT_DAYS * 24 * 60 * 60 * 1000));
		if (visit == ExplorationData.Visit.OLD) {
			return;
		}
		PlayerStore.markDirty(p.getUUID());
		double xp = visit == ExplorationData.Visit.NEW ? XpTables.NEW_CHUNK : XpTables.REVISITED_CHUNK;
		// With an elytra you see so much so fast that a chunk is worth a quarter
		if (p.isFallFlying()) {
			xp *= XpTables.NEW_CHUNK_ELYTRA / XpTables.NEW_CHUNK;
		}
		XpService.award(p, Skill.EXPLORATION, xp);
	}

	private static void biome(ServerPlayer p, ServerLevel level, BlockPos pos) {
		String biome = level.getBiome(pos).getRegisteredName();
		if (PlayerStore.get(p.getUUID()).exploration.biomes.add(biome)) {
			PlayerStore.markDirty(p.getUUID());
			XpService.award(p, Skill.EXPLORATION, XpTables.NEW_BIOME);
		}
	}

	private static void structures(ServerPlayer p, ServerLevel level, BlockPos pos, String dimension) {
		StructureManager manager = level.structureManager();
		Map<Structure, ?> here = manager.getAllStructuresAt(pos);
		if (here.isEmpty()) {
			return;
		}
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (Structure structure : here.keySet()) {
			StructureStart start = manager.getStructureWithPieceAt(pos.getX(), pos.getY(), pos.getZ(), structure);
			if (start == null || !start.isValid()) {
				continue;
			}
			Identifier id = registry.getKey(structure);
			if (id != null) {
				entered(p, dimension, id.getNamespace(), id.getPath(), start.getChunkPos().x(), start.getChunkPos().z());
			}
		}
	}

	/** The player is inside a piece of this structure, which started in that chunk. */
	public static void entered(ServerPlayer p, String dimension, String namespace, String path, int startX, int startZ) {
		XpTables.StructureType type = XpTables.structure(namespace, path);
		if (type == null) {
			return;
		}
		ExplorationData data = PlayerStore.get(p.getUUID()).exploration;
		if (!data.structures.add(dimension + "|" + namespace + ":" + path + "|" + startX + "," + startZ)) {
			return;
		}
		boolean firstOfType = data.structureTypes.add(type.name());
		PlayerStore.markDirty(p.getUUID());
		XpService.award(p, Skill.EXPLORATION, type.xp() * (firstOfType ? XpTables.FIRST_OF_TYPE : 1));
	}

	/** A fall the player survived: XP for the damage, unless they fell near the same spot a short while ago. */
	public static void fell(ServerPlayer p, double damage) {
		if (damage <= 0) {
			return;
		}
		long tick = p.level().getServer().getTickCount();
		String dimension = p.level().dimension().identifier().toString();
		Deque<Fall> falls = FALLS.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
		long keep = Math.round(Config.FALL_SPOT_MINUTES * 60 * 20);
		falls.removeIf(f -> tick - f.tick > keep);
		double r = Config.FALL_SPOT_RADIUS;
		for (Fall f : falls) {
			if (f.dimension.equals(dimension) && p.distanceToSqr(f.x, f.y, f.z) <= r * r) {
				return;
			}
		}
		falls.add(new Fall(dimension, p.getX(), p.getY(), p.getZ(), tick));
		XpService.award(p, Skill.EXPLORATION, damage * XpTables.FALL_PER_DAMAGE);
	}

	public static void forget(UUID id) {
		LAST_CHUNK.remove(id);
		FALLS.remove(id);
	}
}
