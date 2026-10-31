package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.SkeletonTests.check;

import java.nio.file.Files;
import java.nio.file.Path;

import com.extremeprogression.antiexploit.AfkTracker;
import com.extremeprogression.compat.TabPlaceholders;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.network.PartyPayload;
import com.extremeprogression.network.SynergyPayload;
import com.extremeprogression.party.Parties;
import com.extremeprogression.party.Party;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.synergy.Chronicle;
import com.extremeprogression.synergy.Synergies;
import com.extremeprogression.synergy.Synergy;
import com.extremeprogression.synergy.SynergyEffects;
import com.extremeprogression.xp.XpService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Playing together (design §16, 1.4.0): parties and their invitations, the bonus for party members near who earn XP of
 * their own, everyone's share of a fight, and the duo synergies discovered together.
 */
public class PartyTests {
	private static boolean close(double a, double b) {
		return Math.abs(a - b) < 1e-4;
	}

	/** A test player next to the others, with these skills at these levels. */
	private static ServerPlayer player(GameTestHelper helper, String name, Object... skillsAndLevels) {
		ServerPlayer p = TestPlayers.survivor(helper, name);
		BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
		p.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		for (int i = 0; i + 1 < skillsAndLevels.length; i += 2) {
			Progression.setLevel(progress, (Skill) skillsAndLevels[i], (Integer) skillsAndLevels[i + 1]);
		}
		return p;
	}

	/** Two players in one party, the first leading. */
	private static void party(ServerPlayer leader, ServerPlayer member) {
		Parties.invite(leader, member);
		Parties.accept(member, null);
	}

	private static String name(ServerPlayer p) {
		return p.getName().getString();
	}

	@GameTest
	public void aPartyStartsWhenAnInvitationIsAccepted(GameTestHelper helper) {
		Config.reset();
		ServerPlayer a = player(helper, "Lead"), b = player(helper, "Mate"), c = player(helper, "Other");
		check(helper, Parties.invite(a, a) == Parties.Result.SELF, "a player invited themselves");
		check(helper, Parties.invite(a, b) == Parties.Result.OK && Parties.of(b.getUUID()) == null, "an invitation alone made a party");
		check(helper, Parties.payload(b).invites().contains(name(a)), "the invited player does not see the invitation");
		check(helper, Parties.accept(b, null) == Parties.Result.OK, "the invitation could not be accepted");
		Party party = Parties.of(a.getUUID());
		check(helper, party != null && party == Parties.of(b.getUUID()) && party.isLeader(a.getUUID()) && party.size() == 2,
				"accepting did not make a party of two led by the inviter");
		check(helper, Parties.together(a, b) && !Parties.together(a, c) && !Parties.together(a, a), "together() is wrong");
		check(helper, Parties.invite(b, a) == Parties.Result.ALREADY_MEMBER, "a member could be invited again");
		check(helper, Parties.invite(c, a) == Parties.Result.THEY_ARE_IN_A_PARTY, "a player in a party could be invited into another");
		check(helper, Parties.accept(c, null) == Parties.Result.NO_INVITE, "a player without an invitation joined");
		// Any member invites; turning it down leaves nothing behind
		check(helper, Parties.invite(b, c) == Parties.Result.OK && Parties.decline(c, name(b)) == Parties.Result.OK, "the invitation was not turned down");
		check(helper, Parties.accept(c, null) == Parties.Result.NO_INVITE && Parties.of(c.getUUID()) == null, "a declined invitation could still be accepted");
		// The party in the payload, the TAB placeholders
		PartyPayload view = Parties.payload(b);
		check(helper, view.inParty() && view.members().size() == 2 && view.members().stream().anyMatch(m -> m.leader() && m.id().equals(a.getUUID())),
				"the payload does not show the party with its leader");
		var m = TabPlaceholders.values(b);
		check(helper, "2".equals(m.get("party_size").getString()) && name(a).equals(m.get("party_leader").getString())
				&& m.get("party_members").getString().contains(name(b)), "the party placeholders are %s, %s, %s", m.get("party_size").getString(),
				m.get("party_leader").getString(), m.get("party_members").getString());
		check(helper, TabPlaceholders.values(c).get("party_size").getString().isEmpty(), "a player in no party has a party size");
		helper.succeed();
	}

	@GameTest
	public void theLeaderManagesThePartyAndOneAloneIsNoParty(GameTestHelper helper) {
		Config.reset();
		try {
			Config.PARTY_MAX_SIZE = 2;
			ServerPlayer a = player(helper, "Chief"), b = player(helper, "Second"), c = player(helper, "Third");
			party(a, b);
			check(helper, Parties.invite(a, c) == Parties.Result.FULL, "a full party took an invitation");
			check(helper, Parties.kick(b, name(a)) == Parties.Result.NOT_LEADER, "a member who does not lead removed the leader");
			check(helper, Parties.promote(a, name(b)) == Parties.Result.OK && Parties.of(a.getUUID()).isLeader(b.getUUID()), "the lead was not handed on");
			check(helper, Parties.kick(b, "nobody") == Parties.Result.NOT_A_MEMBER, "a stranger could be removed");
			check(helper, Parties.leave(b) == Parties.Result.OK && Parties.of(a.getUUID()) == null && Parties.of(b.getUUID()) == null,
					"a party of one stayed a party");
			check(helper, Parties.leave(a) == Parties.Result.NO_PARTY, "a player in no party could leave one");
			// A leader who leaves a party of three hands it to the longest member
			Config.PARTY_MAX_SIZE = 5;
			party(a, b);
			Parties.invite(a, c);
			Parties.accept(c, null);
			Parties.leave(a);
			Party left = Parties.of(b.getUUID());
			check(helper, left != null && left.isLeader(b.getUUID()) && left.size() == 2 && left.has(c.getUUID()), "the party did not go on under its longest member");
			Parties.leave(b);
		} finally {
			Config.reset();
		}
		helper.succeed();
	}

	@GameTest
	public void partiesAreKeptInTheWorld(GameTestHelper helper) throws Exception {
		Config.reset();
		ServerPlayer a = player(helper, "Keeper"), b = player(helper, "Kept");
		party(a, b);
		String id = Parties.of(a.getUUID()).id;
		Path world = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT);
		Path file = world.resolve("extremeprogression").resolve("parties.json");
		check(helper, Files.exists(file) && Files.readString(file).contains(id), "parties.json does not hold the party");
		Parties.open(world);
		Party back = Parties.of(b.getUUID());
		check(helper, back != null && back.id.equals(id) && back.isLeader(a.getUUID()) && back.name(b.getUUID().toString()).equals(name(b)),
				"the party did not load back whole");
		Parties.leave(a);
		helper.succeed();
	}

	@GameTest
	public void thePartyBonusNeedsMembersNearWhoEarnThemselves(GameTestHelper helper) {
		Config.reset();
		ServerPlayer a = player(helper, "Earner"), b = player(helper, "Helper"), c = player(helper, "Idle"), d = player(helper, "Fourth"),
				solo = player(helper, "Solo");
		party(a, b);
		check(helper, Parties.bonus(a) == 0, "a member who earned nothing gave a bonus");
		Parties.earned(b);
		check(helper, close(Parties.bonus(a), 0.05), "one active member gave %s, Hardcore says +5%%", Parties.bonus(a));
		// At most +15% under Hardcore
		Parties.invite(a, c);
		Parties.accept(c, null);
		Parties.invite(a, d);
		Parties.accept(d, null);
		Parties.earned(c);
		Parties.earned(d);
		check(helper, close(Parties.bonus(a), 0.15), "three active members gave %s, expected the cap of 15%%", Parties.bonus(a));
		// Too far, or idle, counts for nothing
		ServerLevel level = helper.getLevel();
		long tick = level.getServer().getTickCount();
		c.setPos(c.getX() + Config.PARTY_RANGE + 10, c.getY(), c.getZ());
		AfkTracker.idleFor(d, tick, Math.round(Config.AFK_MINUTES * 1200) + 20);
		check(helper, close(Parties.bonus(a), 0.05), "a member too far or idle still counted: %s", Parties.bonus(a));
		// The bonus in what the XP comes to: a fresh player alone earns the base
		Parties.leave(c);
		Parties.leave(d);
		double together = XpService.award(a, Skill.MINING, 100);
		double alone = XpService.award(solo, Skill.MINING, 100);
		check(helper, close(together, alone * 1.05), "together %s, alone %s: not +5%%", together, alone);
		// Normal gives twice as much
		try {
			Config.apply(com.google.gson.JsonParser.parseString("{\"preset\":\"normal\"}").getAsJsonObject());
			check(helper, close(Parties.bonus(a), 0.10) && Config.PARTY_BONUS_MAX == 0.30, "Normal's bonus is %s up to %s", Parties.bonus(a), Config.PARTY_BONUS_MAX);
		} finally {
			Config.reset();
		}
		Parties.leave(a);
		helper.succeed();
	}

	@GameTest
	public void everyoneInAFightEarnsTheirOwnShare(GameTestHelper helper) {
		Config.reset();
		ServerLevel level = helper.getLevel();
		ServerPlayer a = player(helper, "Blade"), b = player(helper, "Shield");
		party(a, b);
		LivingEntity zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 2), EntitySpawnReason.NATURAL);
		CombatTests.swing(a, zombie, true);
		zombie.hurtServer(level, level.damageSources().playerAttack(a), 6);
		helper.runAfterDelay(21, () -> {
			CombatTests.swing(b, zombie, true);
			zombie.hurtServer(level, level.damageSources().playerAttack(b), 100);
			double xpA = Progression.totalXp(PlayerStore.get(a.getUUID()), Skill.MELEE);
			double xpB = Progression.totalXp(PlayerStore.get(b.getUUID()), Skill.MELEE);
			check(helper, !zombie.isAlive() && xpA > 0 && xpB > 0, "the fight gave %s and %s Melee XP", xpA, xpB);
			Parties.leave(a);
			helper.succeed();
		});
	}

	@GameTest
	public void duoSynergiesNeedAPartyAndEachTheirPart(GameTestHelper helper) {
		Config.reset();
		ServerPlayer archer = player(helper, "Archer", Skill.RANGED, 30), tank = player(helper, "Tank", Skill.DEFENSE, 30);
		check(helper, !Synergies.trigger(archer, tank, Synergy.SPOTTER), "Spotter worked outside a party");
		party(archer, tank);
		check(helper, !Synergies.trigger(tank, archer, Synergy.SPOTTER), "Spotter worked with the parts the wrong way round");
		check(helper, !Synergies.trigger(archer, Synergy.SPOTTER), "a duo synergy worked for one player alone");
		Progression.setLevel(PlayerStore.get(tank.getUUID()), Skill.DEFENSE, 29);
		check(helper, !Synergies.trigger(archer, tank, Synergy.SPOTTER), "Spotter worked with the partner at 29");
		Progression.setLevel(PlayerStore.get(tank.getUUID()), Skill.DEFENSE, 30);
		check(helper, Synergies.trigger(archer, tank, Synergy.SPOTTER), "Spotter did not work with both parts met");
		check(helper, Synergies.known(archer, Synergy.SPOTTER) && Synergies.known(tank, Synergy.SPOTTER), "the two did not discover it together");
		check(helper, name(tank).equals(PlayerStore.get(archer.getUUID()).synergies.get(Synergy.SPOTTER.id).with), "the discovery does not say with whom");
		Parties.leave(archer);
		helper.succeed();
	}

	@GameTest
	public void pincerAndTheChronicleKeepBoth(GameTestHelper helper) {
		Config.reset();
		ServerPlayer first = player(helper, "Left", Skill.MELEE, 30), second = player(helper, "Right", Skill.MELEE, 30);
		party(first, second);
		Mob zombie = (Mob) helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 2, 2), EntitySpawnReason.NATURAL);
		float opening = SynergyEffects.damage(zombie, first.damageSources().playerAttack(first), 10);
		float follow = SynergyEffects.damage(zombie, second.damageSources().playerAttack(second), 10);
		check(helper, close(opening, 10) && close(follow, 11), "the opening hit dealt %s and the follow-up %s, expected 10 and 11", opening, follow);
		Chronicle.First f = Chronicle.first(Synergy.PINCER);
		check(helper, f != null && f.by(first.getUUID().toString()) && f.by(second.getUUID().toString()) && f.names().contains(" & "),
				"the chronicle does not keep both discoverers of Pincer");
		SynergyPayload.Entry e = SynergyPayload.of(PlayerStore.get(first.getUUID()), first.getUUID().toString()).entries().stream()
				.filter(x -> x.id().equals("pincer")).findFirst().orElseThrow();
		check(helper, e.known() && e.with().equals(name(second)), "the Codex does not show Pincer found with the other");
		// Not in a party: no Pincer
		Parties.leave(first);
		float apart = SynergyEffects.damage(zombie, first.damageSources().playerAttack(first), 10);
		check(helper, close(apart, 10), "Pincer worked after the party ended: %s", apart);
		helper.succeed();
	}

	@GameTest
	public void whetstoneAndHeartyMealWorkNearAPartyMember(GameTestHelper helper) {
		Config.reset();
		ServerPlayer miner = player(helper, "Miner", Skill.MINING, 30, Skill.EXPLORATION, 30), smith = player(helper, "Smith", Skill.SMITHING, 30, Skill.FARMING, 30);
		ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
		check(helper, SynergyEffects.durability(pick, miner, 1000) == 1000, "Whetstone spared wear without a party");
		party(miner, smith);
		int worn = SynergyEffects.durability(pick, miner, 1000);
		check(helper, worn > 780 && worn < 920, "Whetstone left %s of 1000 points of wear, expected about 850", worn);
		check(helper, SynergyEffects.durability(new ItemStack(Items.IRON_CHESTPLATE), miner, 1000) == 1000, "Whetstone spared armor");
		miner.getFoodData().setFoodLevel(10);
		miner.getFoodData().setSaturation(0);
		ItemStack bread = new ItemStack(Items.BREAD);
		SynergyEffects.ate(miner, bread);
		var props = bread.get(net.minecraft.core.component.DataComponents.FOOD);
		int food = miner.getFoodData().getFoodLevel();
		float saturation = miner.getFoodData().getSaturationLevel();
		check(helper, food == 10 + Math.round(props.nutrition() * 0.25) && Math.abs(saturation - props.saturation() * 0.25f) < 1e-3,
				"Hearty Meal gave food %s and saturation %s", food, saturation);
		// Too far: nothing
		smith.setPos(smith.getX() + 30, smith.getY(), smith.getZ());
		check(helper, SynergyEffects.durability(pick, miner, 1000) == 1000, "Whetstone worked 30 blocks away");
		Parties.leave(miner);
		helper.succeed();
	}
}
