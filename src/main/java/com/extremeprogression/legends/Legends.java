package com.extremeprogression.legends;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;

import com.extremeprogression.antiexploit.MobOrigins;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.network.LegendsActionPayload;
import com.extremeprogression.network.LegendsPayload;
import com.extremeprogression.skill.Skill;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;

/**
 * The Legends update (2.0): XP boosts, Champions, contracts, titles and the Skill of the Day, wired to the game here,
 * and what the client is told of them ({@link View}, sent as JSON in a {@link LegendsPayload}).
 */
public final class Legends {
	private Legends() {}

	/**
	 * Set by the game tests: the daily reward, Well Rested and new contracts at login, Champion spawns and the Skill of
	 * the Day stay out of the way of tests that count XP and items. Their own tests call them directly.
	 */
	public static volatile boolean quiet = false;

	private static final Gson GSON = new Gson();
	private static final Set<UUID> DIRTY = new HashSet<>();
	private static final Set<UUID> LATER = new HashSet<>();
	private static String lastDay = "";

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(LegendsPayload.TYPE, LegendsPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(LegendsActionPayload.TYPE, LegendsActionPayload.CODEC);
		Champions.register();
		ServerPlayNetworking.registerGlobalReceiver(LegendsActionPayload.TYPE, (payload, context) -> act(context.player(), payload.action(), payload.arg()));
		// Reading a tome (on both sides: the client hands the right click to the server)
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			Tome t = Tome.of(stack);
			if (t == null) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide() && player instanceof ServerPlayer p) {
				Boosts.read(p, stack, t);
			}
			return InteractionResult.SUCCESS;
		});
		// Deaths: bosses, Champions, and every other mob a player slew for the bounties
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> died(entity, source));
		// A Champion's hits
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> Champions.hit(entity, source, taken));
		// Treasure chests may hold a tome
		LootTableEvents.MODIFY_DROPS.register((holder, context, drops) ->
				holder.unwrapKey().ifPresent(key -> Boosts.loot(key.identifier().toString(), context.getRandom(), drops)));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> joined(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.executeIfPossible(() -> Boosts.left(handler.getPlayer())));
		ServerTickEvents.END_SERVER_TICK.register(Legends::tick);
	}

	static void died(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
		if (entity.level().isClientSide()) {
			return;
		}
		Boosts.bossDied(entity, LocalDate.now());
		if (Champions.is(entity)) {
			Champions.died(entity, source);
		} else if (source.getEntity() instanceof ServerPlayer p && !MobOrigins.flagged(entity) && entity.getType().getCategory() == MobCategory.MONSTER) {
			Contracts.killed(p, entity, false);
		}
	}

	/** A player joined: Well Rested, the streak, today's contracts, titles, the Skill of the Day; then what the client shows. */
	public static void joined(ServerPlayer p) {
		LocalDate today = LocalDate.now();
		if (quiet) {
			PlayerStore.get(p.getUUID()).legends.lastSeen = System.currentTimeMillis();
		} else {
			Boosts.joined(p, System.currentTimeMillis(), today);
			Contracts.ensure(p, today);
			Skill s = SkillOfTheDay.today();
			if (s != null) {
				p.sendSystemMessage(Component.translatable("message.extremeprogression.skill_of_the_day", Component.translatable(s.translationKey()),
						Math.round(Config.SKILL_OF_THE_DAY_BONUS * 100)).withColor(0xFFFF55), false);
			}
		}
		Titles.check(p);
		dirty(p);
	}

	public static void tick(MinecraftServer server) {
		Boosts.tick(server);
		long tick = server.getTickCount();
		// (a new day for those online across midnight: the streak and new contracts)
		if (tick % 1200 == 0 && !quiet) {
			String today = LocalDate.now().toString();
			if (!today.equals(lastDay)) {
				boolean first = lastDay.isEmpty();
				lastDay = today;
				if (!first) {
					for (ServerPlayer p : server.getPlayerList().getPlayers()) {
						Boosts.daily(p, LocalDate.now());
						Contracts.ensure(p, LocalDate.now());
						dirty(p);
					}
				}
			}
		}
		if (tick % 100 == 0 && !LATER.isEmpty()) {
			DIRTY.addAll(LATER);
			LATER.clear();
		}
		if (DIRTY.isEmpty()) {
			return;
		}
		for (UUID id : DIRTY) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				send(p, false);
			}
		}
		DIRTY.clear();
	}

	/** The player's legends changed: they are sent at the end of the tick. */
	public static void dirty(ServerPlayer p) {
		DIRTY.add(p.getUUID());
	}

	/** A small change (a contract's progress, a boost's seconds): sent within five seconds. */
	public static void later(ServerPlayer p) {
		LATER.add(p.getUUID());
	}

	public static void send(ServerPlayer p, boolean open) {
		if (ServerPlayNetworking.canSend(p, LegendsPayload.TYPE)) {
			ServerPlayNetworking.send(p, new LegendsPayload(GSON.toJson(view(p)), open));
		}
	}

	// --- What the client is told -----------------------------------------------------------------

	/** Everything the Legends screen and the HUD show. */
	public static class View {
		public boolean boostsOn;
		public boolean contractsOn;
		public boolean titlesOn;
		public List<LegendsData.Boost> boosts = new ArrayList<>();
		public int rested;
		public double restedBonus;
		public double boost;
		public double boostMax;
		public int streak;
		public int bestStreak;
		public String skillOfTheDay = "";
		public double skillOfTheDayBonus;
		public LegendsData.ContractBook contracts = new LegendsData.ContractBook();
		public boolean rerollFree;
		public List<String> titles = new ArrayList<>();
		public String title = "";
		public Map<String, Long> stats = new LinkedHashMap<>();
		/** Champions on this server: the chance of one (0 when they are off). */
		public double championChance;
	}

	public static View view(ServerPlayer p) {
		LegendsData d = PlayerStore.get(p.getUUID()).legends;
		View v = new View();
		v.boostsOn = Config.BOOSTS;
		v.contractsOn = Config.CONTRACTS;
		v.titlesOn = Config.TITLES;
		v.boosts = new ArrayList<>(d.boosts);
		v.rested = d.restedSeconds;
		v.restedBonus = Config.RESTED_BONUS;
		v.boost = Boosts.bonus(p);
		v.boostMax = Config.BOOST_MAX;
		v.streak = d.streak;
		v.bestStreak = d.bestStreak;
		Skill s = quiet ? null : SkillOfTheDay.today();
		v.skillOfTheDay = s == null ? "" : s.id;
		v.skillOfTheDayBonus = Config.SKILL_OF_THE_DAY_BONUS;
		v.contracts = d.contracts;
		v.rerollFree = !LocalDate.now().toString().equals(d.contracts.rerolled);
		v.titles = new ArrayList<>(d.titles);
		v.title = d.title;
		v.stats = d.stats;
		v.championChance = Config.CHAMPION_CHANCE;
		return v;
	}

	public static View parse(String json) {
		View v = GSON.fromJson(json, View.class);
		return v == null ? new View() : v;
	}

	// --- What the client asks for ----------------------------------------------------------------

	/** A button in the Legends screen: "open", "reroll" (arg: the contract's index), "title" (arg: its id, "" for none). */
	public static void act(ServerPlayer p, String action, String arg) {
		switch (action) {
			case "open" -> send(p, true);
			case "reroll" -> {
				int index;
				try {
					index = Integer.parseInt(arg);
				} catch (NumberFormatException e) {
					return;
				}
				if (!Contracts.reroll(p, index, LocalDate.now())) {
					p.sendSystemMessage(Component.translatable("message.extremeprogression.contract.no_reroll").withColor(0xFF5555), true);
				}
				send(p, false);
			}
			case "title" -> {
				Titles.equip(p, arg);
				send(p, false);
			}
			default -> {}
		}
	}
}
