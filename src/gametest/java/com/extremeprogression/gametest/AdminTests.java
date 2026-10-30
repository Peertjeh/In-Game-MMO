package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.GatheringTests.near;
import static com.extremeprogression.gametest.SkeletonTests.check;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.extremeprogression.ability.AbilityService;
import com.extremeprogression.command.AdminCommands;
import com.extremeprogression.config.Config;
import com.extremeprogression.config.DataFiles;
import com.extremeprogression.config.Settings;
import com.extremeprogression.config.SkillSettings;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.death.DeathRules;
import com.extremeprogression.network.InspectPayload;
import com.extremeprogression.network.LeaderboardPayload;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.skill.HardRequirement;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.skill.Specialization;
import com.extremeprogression.synergy.Synergies;
import com.extremeprogression.synergy.Synergy;
import com.extremeprogression.synergy.SynergyEffects;
import com.extremeprogression.xp.XpData;
import com.extremeprogression.xp.XpEvents;
import com.extremeprogression.xp.XpService;
import com.extremeprogression.xp.XpTables;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;

/**
 * Administration (milestone 9, design §11): the presets, the data files, the settings shared with clients, XP events,
 * the leaderboard, inspecting and resetting. Every test puts back what it changed before it ends.
 */
public class AdminTests {
	private static JsonObject json(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}

	@GameTest
	public void presetsSetTheOptionsTheyGovern(GameTestHelper helper) {
		try {
			Config.apply(json("{\"preset\":\"normal\"}"));
			check(helper, Config.DEATH_BASE == 0.25 && Config.DEATH_BASE_PRESSURED == 0.5 && !Config.DEATH_WHOLE_LOSS && Config.XP_MULTIPLIER == 1.5,
					"Normal did not halve the loss, drop layer 3 and give 50%% more XP");
			check(helper, !DeathRules.whole(9), "layer 3 counts under Normal");
			Config.apply(json("{\"preset\":\"hardcore\",\"xp_multiplier\":3,\"afk_minutes\":7}"));
			check(helper, Config.XP_MULTIPLIER == 1.0 && Config.AFK_MINUTES == 7, "Hardcore did not keep its XP rate, or touched an option it does not govern");
			Config.apply(json("{\"preset\":\"custom\",\"xp_multiplier\":3}"));
			check(helper, Config.XP_MULTIPLIER == 3 && Config.PRESET.equals("custom"), "Custom did not leave the XP rate to the file");
			Config.apply(json("{\"preset\":\"weird\",\"death_base\":0.4}"));
			check(helper, Config.PRESET.equals("custom") && Config.DEATH_BASE == 0.4, "an unknown preset is not custom");
		} finally {
			Config.reset();
		}
		helper.succeed();
	}

	@GameTest
	public void theXpTablesComeFromTheirFiles(GameTestHelper helper) {
		try {
			boolean missing = XpData.apply(Skill.MINING, json("{\"pickaxe\":{\"minecraft:stone\":7,\"othermod:marble\":4},\"brushing\":300}"));
			check(helper, missing, "a file without every value did not ask to be written back");
			check(helper, XpTables.pickaxe(Blocks.STONE.defaultBlockState()) == 7, "stone gives %s, not the file's 7", XpTables.pickaxe(Blocks.STONE.defaultBlockState()));
			check(helper, XpTables.pickaxe(Blocks.COAL_ORE.defaultBlockState()) == 0, "a block the file's table leaves out still gives XP");
			check(helper, XpTables.BRUSHING == 300 && XpTables.TERRACOTTA == 5, "brushing is %s and terracotta %s", XpTables.BRUSHING, XpTables.TERRACOTTA);
			JsonObject back = XpData.toJson(Skill.MINING);
			check(helper, back.has("terracotta") && back.getAsJsonObject("pickaxe").has("othermod:marble") && back.getAsJsonObject("pickaxe").has("stone"),
					"the written file does not hold every value: %s", back);
			XpData.apply(Skill.EXPLORATION, json("{\"structures\":{\"village\":600}}"));
			check(helper, XpTables.structure("minecraft", "village_plains").xp() == 600, "a village type of 600 XP did not reach the plains village");
		} finally {
			Config.reset();
		}
		check(helper, XpTables.pickaxe(Blocks.STONE.defaultBlockState()) == 3 && XpTables.structure("minecraft", "village_plains").xp() == 300,
				"the defaults did not come back");
		helper.succeed();
	}

	@GameTest
	public void dataFilesAreWrittenCompleteAndBrokenOnesKept(GameTestHelper helper) throws Exception {
		DataFiles.load();
		Path dir = DataFiles.dir();
		for (Skill s : Skill.values()) {
			Path f = dir.resolve("xp").resolve(s.id + ".json");
			check(helper, Files.exists(f), "%s was not written", f.getFileName());
		}
		check(helper, Files.readString(dir.resolve("skills.json")).contains("cooldown_minutes") && Files.readString(dir.resolve("synergies.json")).contains("counterstrike"),
				"skills.json or synergies.json is not complete");
		// A file that cannot be read is left as it is, and its defaults count
		Path synergies = dir.resolve("synergies.json");
		String before = Files.readString(synergies);
		Files.writeString(synergies, "{ half a file");
		try {
			DataFiles.load();
			check(helper, Files.readString(synergies).equals("{ half a file"), "a broken synergies.json was overwritten");
			check(helper, Synergy.COUNTERSTRIKE.enabled && Synergy.COUNTERSTRIKE.strength == 0.20, "a broken file did not leave the defaults");
		} finally {
			Files.writeString(synergies, before);
			Config.reset();
			DataFiles.load();
		}
		helper.succeed();
	}

	@GameTest
	public void skillsJsonTurnsSkillsOffAndSetsCooldownsAndRequirements(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Settings");
		try {
			SkillSettings.apply(json("{\"skills\":{\"mining\":{\"enabled\":false,\"xp_multiplier\":1},\"farming\":{\"enabled\":true,\"xp_multiplier\":2}},"
					+ "\"abilities\":{\"blade\":{\"cooldown_minutes\":1}},\"requirements\":{\"template_saver\":{\"skill\":\"foraging\",\"level\":5}}}"));
			near(helper, XpService.award(p, Skill.MINING, 100), 0, "Mining XP while Mining is off");
			near(helper, XpService.award(p, Skill.FARMING, 100), 200, "Farming XP at ×2");
			check(helper, AbilityService.cooldownTicks(Specialization.BLADE) == 1200, "Riposte's cooldown is not the file's minute");
			check(helper, AbilityService.cooldownTicks(Specialization.CRUSHER) == Specialization.CRUSHER.cooldownMinutes * 1200L, "an ability the file left out changed");
			check(helper, HardRequirement.TEMPLATE_SAVER.skill == Skill.FORAGING && HardRequirement.TEMPLATE_SAVER.level == 5, "the requirement did not change");
		} finally {
			Config.reset();
		}
		check(helper, SkillSettings.enabled(Skill.MINING) && HardRequirement.TEMPLATE_SAVER.skill == Skill.MINING && HardRequirement.TEMPLATE_SAVER.level == 30,
				"the defaults did not come back");
		helper.succeed();
	}

	@GameTest
	public void synergiesJsonTurnsSynergiesOffAndSetsTheirStrength(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Strong");
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		Progression.setLevel(progress, Skill.MELEE, 30);
		Progression.setLevel(progress, Skill.DEFENSE, 30);
		Progression.setLevel(progress, Skill.EXPLORATION, 30);
		LivingEntity target = (LivingEntity) helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 1), EntitySpawnReason.NATURAL);
		try {
			Synergy.apply(json("{\"charge\":{\"enabled\":false,\"strength\":0.1},\"counterstrike\":{\"enabled\":true,\"strength\":0.5}}"));
			check(helper, !Synergies.eligible(p, Synergy.CHARGE), "a synergy that is off can still be found");
			SynergyEffects.blocked(p);
			float hit = SynergyEffects.damage(target, p.damageSources().playerAttack(p), 10);
			check(helper, Math.abs(hit - 15) < 1e-4, "a Counterstrike of strength 0.5 dealt %s, expected 15", hit);
		} finally {
			Config.reset();
		}
		helper.succeed();
	}

	@GameTest
	public void theSettingsTravelAsOneTextAndOnlyAdminsChangeThem(GameTestHelper helper) {
		Config.reset();
		String shared = Settings.shared();
		JsonObject parsed = json(shared);
		check(helper, parsed.has("config") && parsed.has("skills") && parsed.has("synergies"), "the shared settings lack a part");
		try {
			check(helper, Settings.applyShared(shared) && Config.XP_MULTIPLIER == 1 && Synergy.CHARGE.enabled, "applying the settings as they are changed something");
			check(helper, !Settings.applyShared("not json"), "a broken text was taken as settings");
			// A player who is no admin cannot change them from the settings screen
			ServerPlayer p = TestPlayers.survivor(helper, "NotAdmin");
			check(helper, !Settings.mayEdit(p), "a test player may change the settings");
			JsonObject changed = json(shared);
			changed.getAsJsonObject("config").addProperty("preset", "custom");
			changed.getAsJsonObject("config").addProperty("xp_multiplier", 9);
			AdminCommands.settingsSaved(p, changed.toString());
			check(helper, Config.XP_MULTIPLIER == 1, "a player who is no admin changed the XP rate");
		} finally {
			Config.reset();
		}
		helper.succeed();
	}

	@GameTest
	public void xpEventsMultiplyAndEnd(GameTestHelper helper) {
		Config.reset();
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer p = TestPlayers.survivor(helper, "Eventful");
		try {
			XpEvents.start(server, Skill.MINING, 2, 30, "test");
			check(helper, XpEvents.multiplier(Skill.MINING) == 2 && XpEvents.multiplier(Skill.FARMING) == 1, "a Mining event is not Mining's alone");
			near(helper, XpService.award(p, Skill.MINING, 100), 200, "Mining XP during a ×2 event");
			XpEvents.start(server, null, 3, 0, "test");
			check(helper, XpEvents.multiplier(Skill.MINING) == 6 && XpEvents.multiplier(Skill.FARMING) == 3, "an event for every skill does not multiply with one for Mining");
			XpEvents.start(server, Skill.MINING, 1.5, 10, "test");
			check(helper, XpEvents.active().size() == 2 && XpEvents.multiplier(Skill.MINING) == 4.5, "a new Mining event did not replace the old one");
			check(helper, XpEvents.reset(server, "test") && XpEvents.multiplier(Skill.MINING) == 1, "the reset did not end the events");
			check(helper, !XpEvents.reset(server, "test"), "a reset without events reported one");
		} finally {
			XpEvents.clear();
		}
		helper.succeed();
	}

	@GameTest
	public void theLeaderboardSortsByTotalAndBySkill(GameTestHelper helper) {
		Config.reset();
		ServerPlayer high = TestPlayers.survivor(helper, "High");
		ServerPlayer low = TestPlayers.survivor(helper, "Low");
		Progression.setLevel(PlayerStore.get(high.getUUID()), Skill.MINING, 90);
		Progression.setLevel(PlayerStore.get(low.getUUID()), Skill.MINING, 10);
		Progression.setLevel(PlayerStore.get(low.getUUID()), Skill.FISHING, 60);
		PlayerStore.get(high.getUUID()).name = high.getName().getString();
		PlayerStore.get(low.getUUID()).name = low.getName().getString();
		List<LeaderboardPayload.Row> rows = AdminCommands.leaderboard(100000);
		int h = index(rows, high), l = index(rows, low);
		check(helper, h >= 0 && l >= 0 && h < l, "the total ranking has %s at %s and %s at %s", high.getName().getString(), h, low.getName().getString(), l);
		List<LeaderboardPayload.Row> fishing = AdminCommands.bySkill(rows, Skill.FISHING);
		check(helper, index(fishing, low) < index(fishing, high), "the Fishing ranking does not put the better fisher first");
		helper.succeed();
	}

	private static int index(List<LeaderboardPayload.Row> rows, ServerPlayer p) {
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).name().equals(p.getName().getString())) {
				return i;
			}
		}
		return -1;
	}

	@GameTest
	public void aResetWaitsForItsConfirmation(GameTestHelper helper) {
		Config.reset();
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer p = TestPlayers.survivor(helper, "Resetme");
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		progress.name = p.getName().getString();
		Progression.setLevel(progress, Skill.MINING, 40);
		Progression.setLevel(progress, Skill.MELEE, 20);
		var src = server.createCommandSourceStack();
		server.getCommands().performPrefixedCommand(src, "skills admin reset " + p.getName().getString() + " mining");
		check(helper, PlayerStore.get(p.getUUID()).get(Skill.MINING).level == 40, "the reset did not wait for its confirmation");
		server.getCommands().performPrefixedCommand(src, "skills admin reset confirm");
		PlayerProgress after = PlayerStore.get(p.getUUID());
		check(helper, after.get(Skill.MINING).level == 0 && after.get(Skill.MELEE).level == 20, "a Mining reset left Mining at %s or took Melee to %s",
				after.get(Skill.MINING).level, after.get(Skill.MELEE).level);
		server.getCommands().performPrefixedCommand(src, "skills admin reset confirm");
		AdminCommands.reset(server, p.getUUID(), null, "test");
		PlayerProgress all = PlayerStore.get(p.getUUID());
		check(helper, all.totalLevel() == 0 && all.name.equals(p.getName().getString()), "a full reset left %s levels or lost the name", all.totalLevel());
		check(helper, PlayerStore.read(PlayerStore.fileOf(p.getUUID())).totalLevel() == 0, "the reset was not saved at once");
		helper.succeed();
	}

	@GameTest
	public void anInspectionCarriesTheWholeSheet(GameTestHelper helper) {
		Config.reset();
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer p = TestPlayers.survivor(helper, "Looked");
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		progress.name = p.getName().getString();
		Progression.setLevel(progress, Skill.ARCANA, 55);
		InspectPayload payload = AdminCommands.inspectPayload(server, p.getUUID(), p.getName().getString());
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		InspectPayload.CODEC.encode(buf, payload);
		InspectPayload back = InspectPayload.CODEC.decode(buf);
		check(helper, back.name().equals(p.getName().getString()) && back.online() && back.progress().total() == 55, "the inspection did not come through whole");
		helper.succeed();
	}
}
