package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.SkeletonTests.check;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import com.google.gson.JsonObject;

import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.data.SafeFiles;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.skill.Skill;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Saving never loses progression (design §10): files survive a round trip, keep three backups, fall back to a backup
 * when damaged, and files from older or newer versions load without losing anything. Each test works in a folder of
 * its own, apart from the world's real files.
 */
public class StoreTests {
	private static Path folder(String name) throws Exception {
		return Files.createTempDirectory("extremeprogression-" + name);
	}

	@GameTest
	public void aFileSurvivesARoundTrip(GameTestHelper helper) throws Exception {
		Config.reset();
		Path file = folder("roundtrip").resolve("player.json");
		PlayerProgress p = new PlayerProgress();
		p.name = "Tester";
		Progression.setLevel(p, Skill.MELEE, 47);
		p.get(Skill.MELEE).xp = 123.5;
		Progression.setLevel(p, Skill.ARCANA, 100);
		p.get(Skill.ARCANA).masteryRanks = 3;
		p.get(Skill.ARCANA).masteryXp = 2500;
		PlayerStore.write(file, p);
		PlayerProgress back = PlayerStore.read(file);
		check(helper, back.name.equals("Tester") && back.schema == PlayerProgress.SCHEMA, "the name or schema did not come back");
		check(helper, back.get(Skill.MELEE).level == 47 && back.get(Skill.MELEE).xp == 123.5, "Melee did not come back");
		check(helper, back.get(Skill.ARCANA).masteryRanks == 3 && back.get(Skill.ARCANA).masteryXp == 2500, "Mastery did not come back");
		helper.succeed();
	}

	@GameTest
	public void threeBackupsRotate(GameTestHelper helper) throws Exception {
		Config.reset();
		Path file = folder("backups").resolve("player.json");
		PlayerProgress p = new PlayerProgress();
		for (int level = 1; level <= 5; level++) {
			Progression.setLevel(p, Skill.MINING, level);
			PlayerStore.write(file, p);
		}
		check(helper, PlayerStore.read(file).get(Skill.MINING).level == 5, "the file is not the newest");
		check(helper, PlayerStore.read(SafeFiles.backup(file, 1)).get(Skill.MINING).level == 4, ".bak1 is not the one before");
		check(helper, PlayerStore.read(SafeFiles.backup(file, 3)).get(Skill.MINING).level == 2, ".bak3 is not the oldest kept");
		check(helper, !Files.exists(SafeFiles.backup(file, 4)), "more than three backups were kept");
		helper.succeed();
	}

	@GameTest
	public void aDamagedFileFallsBackToItsBackup(GameTestHelper helper) throws Exception {
		Config.reset();
		Path dir = folder("damaged");
		Path file = dir.resolve("player.json");
		PlayerProgress p = new PlayerProgress();
		Progression.setLevel(p, Skill.MINING, 30);
		PlayerStore.write(file, p);
		Progression.setLevel(p, Skill.MINING, 31);
		PlayerStore.write(file, p);
		Files.writeString(file, "{ half a file");
		check(helper, PlayerStore.read(file).get(Skill.MINING).level == 30, "the damaged file did not fall back to its backup");
		long aside;
		try (Stream<Path> files = Files.list(dir)) {
			aside = files.filter(f -> f.getFileName().toString().contains(".unreadable-")).count();
		}
		check(helper, aside == 1, "the damaged file was not kept aside for the admin");
		helper.succeed();
	}

	/** The migration test: a file from before the schema field loads, and what this version does not know is kept. */
	@GameTest
	public void olderFilesLoseNothing(GameTestHelper helper) throws Exception {
		Config.reset();
		Path file = folder("older").resolve("player.json");
		Files.writeString(file, "{\"skills\":{\"melee\":{\"level\":12,\"xp\":50.0},\"necromancy\":{\"level\":5}}}");
		PlayerProgress p = PlayerStore.read(file);
		check(helper, p.schema == PlayerProgress.SCHEMA, "the old file was not brought up to schema %s", PlayerProgress.SCHEMA);
		check(helper, p.get(Skill.MELEE).level == 12 && p.get(Skill.MELEE).xp == 50, "the old file's Melee was lost");
		check(helper, p.skills.containsKey("necromancy") && p.skills.get("necromancy").level == 5, "an unknown skill was dropped");
		PlayerStore.write(file, p);
		check(helper, Files.readString(file).contains("necromancy"), "an unknown skill was dropped on saving");
		helper.succeed();
	}

	@GameTest
	public void newerFilesAreKeptBeforeTheyAreRewritten(GameTestHelper helper) throws Exception {
		Config.reset();
		Path dir = folder("newer");
		Path file = dir.resolve("player.json");
		Files.writeString(file, "{\"schema\":99,\"skills\":{\"melee\":{\"level\":3}},\"something_new\":true}");
		check(helper, PlayerStore.read(file).get(Skill.MELEE).level == 3, "a newer file's Melee was lost");
		check(helper, Files.exists(dir.resolve("player.json.schema99")), "the newer file was not kept as .schema99");
		helper.succeed();
	}

	@GameTest
	public void badValuesAreRepairedNotKept(GameTestHelper helper) throws Exception {
		Config.reset();
		Path file = folder("bad").resolve("player.json");
		Files.writeString(file, "{\"skills\":{\"melee\":{\"level\":-4,\"xp\":-10}}}");
		PlayerProgress p = PlayerStore.read(file);
		check(helper, p.get(Skill.MELEE).level == 0 && p.get(Skill.MELEE).xp == 0, "negative values were kept");
		helper.succeed();
	}

	@GameTest
	public void theConfigListsEveryOptionAndRejectsBadValues(GameTestHelper helper) {
		JsonObject json = new JsonObject();
		json.addProperty("hours_to_max", 800);
		json.addProperty("max_level", -3);
		boolean missing = Config.apply(json);
		check(helper, missing, "missing keys were not reported");
		check(helper, Config.HOURS_TO_MAX == 800, "a value from the file was not taken");
		check(helper, Config.MAX_LEVEL == 100, "a bad value was taken: max_level %s", Config.MAX_LEVEL);
		check(helper, Config.toJson().has("total_level_cap") && Config.toJson().size() == 50, "the config does not list every option: %s", Config.toJson());
		Config.reset();
		check(helper, Config.HOURS_TO_MAX == 400, "the config did not reset");
		helper.succeed();
	}
}
