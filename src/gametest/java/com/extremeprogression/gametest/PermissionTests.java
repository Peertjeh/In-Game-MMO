package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.SkeletonTests.check;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.extremeprogression.admin.AdminService;
import com.extremeprogression.config.Config;
import com.extremeprogression.config.Settings;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.server.Perms;
import com.extremeprogression.skill.Skill;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.permission.v1.PermissionContext;
import net.fabricmc.fabric.api.permission.v1.PermissionEvents;
import net.fabricmc.fabric.api.permission.v1.PermissionNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.6.0: the mod's permissions, and the Players screen for admins. Without a permission mod each permission falls back
 * to the operator level it always needed; a permission mod (here a stand-in, answering only for the test's own players)
 * grants or denies each one alone. The Players screen changes levels, Mastery ranks, deaths and resets, for players
 * online and offline, and only for who may.
 */
public class PermissionTests {
	/** What the stand-in permission mod says, per player: permission path to granted or denied. Unlisted: no answer. */
	private static final Map<UUID, Map<String, Boolean>> ANSWERS = new ConcurrentHashMap<>();
	private static boolean registered;

	/** Registers the stand-in once (events cannot be taken back); it answers only for players in {@link #ANSWERS}. */
	private static synchronized void standIn() {
		if (registered) {
			return;
		}
		registered = true;
		PermissionEvents.ON_REQUEST.register(new PermissionEvents.OnRequest() {
			@Override
			@SuppressWarnings("unchecked")
			public <T> T handlePermissionRequest(PermissionContext context, PermissionNode<T> permission) {
				Map<String, Boolean> mine = ANSWERS.get(context.uuid());
				if (mine == null || !"extremeprogression".equals(permission.key().getNamespace())) {
					return null;
				}
				return (T) mine.get(permission.key().getPath());
			}
		});
	}

	private static ServerPlayer granted(GameTestHelper helper, String name, Perms.Node... nodes) {
		standIn();
		ServerPlayer p = TestPlayers.survivor(helper, name);
		Map<String, Boolean> mine = new ConcurrentHashMap<>();
		for (Perms.Node n : nodes) {
			mine.put(n.id.getPath(), true);
		}
		ANSWERS.put(p.getUUID(), mine);
		return p;
	}

	@GameTest
	public void withoutAPermissionModTheOperatorLevelsStand(GameTestHelper helper) {
		standIn();
		ServerPlayer p = TestPlayers.survivor(helper, "plain");
		CommandSourceStack console = helper.getLevel().getServer().createCommandSourceStack();
		for (Perms.Node n : Perms.Node.values()) {
			check(helper, !Perms.has(p, n), "a player without operator rights holds %s", n.dotted());
			check(helper, !Perms.has(p.createCommandSourceStack(), n), "a player's commands hold %s without operator rights", n.dotted());
			check(helper, Perms.has(console, n), "the server console lacks %s", n.dotted());
		}
		check(helper, !Settings.mayEdit(p) && !AdminService.allowed(p), "a player without operator rights may change settings or open the Players screen");
		check(helper, "extremeprogression.admin.players".equals(Perms.Node.PLAYERS.dotted()), "the Players permission is written %s", Perms.Node.PLAYERS.dotted());
		check(helper, AdminService.act(p, p.getStringUUID(), "level", "mining", 5) != null && PlayerStore.get(p.getUUID()).get(Skill.MINING).level == 0,
				"a player without the permission changed a level");
		helper.succeed();
	}

	@GameTest
	public void aPermissionModGrantsOnePermissionAlone(GameTestHelper helper) {
		Config.reset();
		ServerPlayer mod = granted(helper, "moderator", Perms.Node.PLAYERS);
		ServerPlayer target = TestPlayers.survivor(helper, "target");
		try {
			check(helper, Perms.has(mod, Perms.Node.PLAYERS) && Perms.has(mod.createCommandSourceStack(), Perms.Node.PLAYERS), "the permission granted is not held");
			check(helper, AdminService.allowed(mod) && !Settings.mayEdit(mod) && !AdminService.mayReset(mod) && !Perms.has(mod, Perms.Node.EVENTS),
					"granting the Players screen gave other permissions too, or not the screen");
			// Levels, and past the maximum Mastery ranks
			check(helper, AdminService.act(mod, target.getStringUUID(), "level", "mining", 10) == null && PlayerStore.get(target.getUUID()).get(Skill.MINING).level == 10,
					"+10 did not give Mining level 10");
			Progression.setLevel(PlayerStore.get(target.getUUID()), Skill.MINING, Config.MAX_LEVEL);
			AdminService.act(mod, target.getStringUUID(), "level", "mining", 3);
			check(helper, PlayerStore.get(target.getUUID()).get(Skill.MINING).masteryRanks == 3, "+3 at the maximum level did not give 3 Mastery ranks");
			AdminService.act(mod, target.getStringUUID(), "level", "mining", -1);
			check(helper, PlayerStore.get(target.getUUID()).get(Skill.MINING).masteryRanks == 2 && PlayerStore.get(target.getUUID()).get(Skill.MINING).level == Config.MAX_LEVEL,
					"−1 with Mastery ranks did not take a rank first");
			// Deaths cleared
			PlayerStore.get(target.getUUID()).death.pressure = 3;
			AdminService.act(mod, target.getStringUUID(), "clear_deaths", "", 0);
			check(helper, PlayerStore.get(target.getUUID()).death.pressure == 0, "the death pressure was not cleared");
			// A reset needs its own permission
			check(helper, AdminService.act(mod, target.getStringUUID(), "reset_all", "", 0) != null && PlayerStore.get(target.getUUID()).get(Skill.MINING).level == Config.MAX_LEVEL,
					"a reset went through without the reset permission");
			// Taken away again: a permission mod's no stands
			ANSWERS.get(mod.getUUID()).put(Perms.Node.PLAYERS.id.getPath(), false);
			check(helper, !AdminService.allowed(mod), "the Players screen stays open after the permission was taken away");
		} finally {
			ANSWERS.remove(mod.getUUID());
		}
		helper.succeed();
	}

	@GameTest
	public void thePlayersScreenChangesAnOfflinePlayerToo(GameTestHelper helper) throws Exception {
		Config.reset();
		ServerPlayer admin = granted(helper, "admin", Perms.Node.PLAYERS, Perms.Node.RESET);
		try {
			// A player who is not online: only their file
			UUID away = UUID.randomUUID();
			PlayerProgress file = new PlayerProgress();
			file.name = "Away" + away.toString().substring(0, 4);
			Progression.setLevel(file, Skill.FISHING, 20);
			PlayerStore.replace(away, file);
			PlayerStore.unload(away);
			com.google.gson.JsonObject data = listed(admin, away);
			check(helper, data.get("online").getAsBoolean() == false && data.get("total").getAsInt() == 20, "the offline player is not listed with their total: %s", data);
			check(helper, AdminService.act(admin, away.toString(), "level", "fishing", 5) == null, "a level could not be given to a player offline");
			check(helper, !PlayerStore.loaded(away) && PlayerStore.peek(away).get(Skill.FISHING).level == 25, "the offline player's file does not hold level 25: %s",
					PlayerStore.peek(away).get(Skill.FISHING).level);
			check(helper, AdminService.act(admin, away.toString(), "reset_all", "", 0) == null && PlayerStore.peek(away).totalLevel() == 0,
					"the reset of an offline player did not go through");
			check(helper, PlayerStore.peek(away).name.equals(file.name), "the reset lost the player's name");
			check(helper, AdminService.act(admin, UUID.randomUUID().toString(), "level", "fishing", 5) != null, "a player the world does not know was changed");
		} finally {
			ANSWERS.remove(admin.getUUID());
		}
		helper.succeed();
	}

	/** The row of the Players screen's list for this player, as the admin is sent it. */
	private static com.google.gson.JsonObject listed(ServerPlayer admin, UUID id) {
		com.google.gson.JsonObject data = com.extremeprogression.admin.AdminService.dataFor(admin, id.toString());
		for (com.google.gson.JsonElement e : data.getAsJsonArray("players")) {
			if (e.getAsJsonObject().get("id").getAsString().equals(id.toString())) {
				return e.getAsJsonObject();
			}
		}
		throw new AssertionError("not listed: " + id);
	}
}
