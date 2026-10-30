package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.GatheringTests.earned;
import static com.extremeprogression.gametest.GatheringTests.near;
import static com.extremeprogression.gametest.SkeletonTests.check;

import java.nio.file.Files;
import java.nio.file.Path;

import com.extremeprogression.config.Config;
import com.extremeprogression.data.ExplorationData;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.xp.ExplorationXp;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Milestone 4: Exploration XP for chunks, structures and falls, and its place in the player file. */
public class ExplorationTests {
	private static final String DIM = "extremeprogression:test";
	private static final long DAY = 24L * 60 * 60 * 1000;

	@GameTest
	public void chunksCountOncePerPlayerAndAgainAfterAMonth(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "walker");
		ServerPlayer q = TestPlayers.survivor(helper, "other");
		long now = System.currentTimeMillis();
		double before = earned(p, Skill.EXPLORATION);
		ExplorationXp.enteredChunk(p, DIM, 5000, 5000, now);
		near(helper, earned(p, Skill.EXPLORATION) - before, 8, "a new chunk");
		ExplorationXp.enteredChunk(p, DIM, 5000, 5000, now + 1000);
		ExplorationXp.enteredChunk(p, DIM, 5001, 5000, now + 2000);
		near(helper, earned(p, Skill.EXPLORATION) - before, 16, "a chunk again, then a second new one");
		double other = earned(q, Skill.EXPLORATION);
		ExplorationXp.enteredChunk(q, DIM, 5000, 5000, now);
		near(helper, earned(q, Skill.EXPLORATION) - other, 8, "the same chunk for another player");
		ExplorationXp.enteredChunk(p, DIM, 5000, 5000, now + 31 * DAY);
		ExplorationXp.enteredChunk(p, DIM, 5000, 5000, now + 31 * DAY + 1000);
		near(helper, earned(p, Skill.EXPLORATION) - before, 18, "a chunk revisited after a month (once)");
		helper.succeed();
	}

	@GameTest
	public void structuresCountOnceAndTheFirstOfATypeTriple(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "seeker");
		double before = earned(p, Skill.EXPLORATION);
		ExplorationXp.entered(p, DIM, "minecraft", "village_plains", 10, 10);
		near(helper, earned(p, Skill.EXPLORATION) - before, 900, "the first village (×3)");
		ExplorationXp.entered(p, DIM, "minecraft", "village_plains", 10, 10);
		ExplorationXp.entered(p, DIM, "minecraft", "village_desert", 40, 40);
		near(helper, earned(p, Skill.EXPLORATION) - before, 1200, "the same village again, then a second village");
		ExplorationXp.entered(p, DIM, "minecraft", "buried_treasure", 3, 3);
		ExplorationXp.entered(p, DIM, "minecraft", "ancient_city", 60, 60);
		near(helper, earned(p, Skill.EXPLORATION) - before, 1200 + 6000, "buried treasure (nothing) and a first ancient city");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void survivedFallsCountOncePerSpot(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer p = TestPlayers.survivor(helper, "faller");
		double[] gained = new double[3];
		CombatTests.spaced(helper, 3, i -> {
			double before = earned(p, Skill.EXPLORATION);
			p.setHealth(20);
			if (i == 2) {
				p.setPos(p.getX() + 10, p.getY(), p.getZ());
			}
			p.hurtServer(level, level.damageSources().fall(), 5);
			gained[i] = earned(p, Skill.EXPLORATION) - before;
		}, () -> {
			near(helper, gained[0], 200, "a fall of 5 damage");
			near(helper, gained[1], 0, "a second fall on the same spot");
			near(helper, gained[2], 200, "a fall 10 blocks away");
			helper.succeed();
		});
	}

	@GameTest
	public void discoveriesAreSavedWithThePlayer(GameTestHelper helper) throws Exception {
		Config.reset();
		PlayerProgress p = new PlayerProgress();
		long now = System.currentTimeMillis();
		p.exploration.visit(DIM, -1, -1, now, 30 * DAY);
		p.exploration.visit(DIM, 31, 31, now, 30 * DAY);
		p.exploration.biomes.add("minecraft:plains");
		p.exploration.structureTypes.add("village");
		Path file = Files.createTempDirectory("ep-exploration").resolve("player.json");
		PlayerStore.write(file, p);
		PlayerProgress back = PlayerStore.read(file);
		check(helper, back.exploration.visit(DIM, -1, -1, now, 30 * DAY) == ExplorationData.Visit.OLD
				&& back.exploration.visit(DIM, 31, 31, now, 30 * DAY) == ExplorationData.Visit.OLD, "visited chunks were not saved");
		check(helper, back.exploration.visit(DIM, 0, 0, now, 30 * DAY) == ExplorationData.Visit.NEW, "an unvisited chunk came back as visited");
		check(helper, back.exploration.biomes.contains("minecraft:plains") && back.exploration.structureTypes.contains("village"),
				"biomes or structure types were not saved");

		// A file from before exploration existed (schema 1) loads with nothing discovered, and keeps its levels
		Path old = file.resolveSibling("old.json");
		Files.writeString(old, "{\"schema\":1,\"skills\":{\"mining\":{\"level\":7}}}");
		PlayerProgress loaded = PlayerStore.read(old);
		check(helper, loaded.schema == PlayerProgress.SCHEMA && loaded.get(Skill.MINING).level == 7 && loaded.exploration.chunks.isEmpty(),
				"a schema 1 file did not load into schema %s", PlayerProgress.SCHEMA);
		helper.succeed();
	}
}
