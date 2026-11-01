package com.extremeprogression.spec;

import static com.extremeprogression.skill.Specialization.GOLD_TONGUE;
import static com.extremeprogression.skill.Specialization.MERCHANT;
import static com.extremeprogression.skill.Specialization.QUARTERMASTER;
import static com.extremeprogression.tree.NodeSlot.C1;
import static com.extremeprogression.tree.NodeSlot.C2;
import static com.extremeprogression.tree.NodeSlot.P1;
import static com.extremeprogression.tree.NodeSlot.P2;
import static com.extremeprogression.tree.NodeSlot.P3;
import static com.extremeprogression.tree.NodeSlot.P4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import com.mojang.serialization.Codec;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.ability.AbilityService;
import com.extremeprogression.antiexploit.MobOrigins;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.mastery.Mastery;
import com.extremeprogression.mastery.MasteryPerk;
import com.extremeprogression.mixin.MerchantOfferAccess;
import com.extremeprogression.mixin.PiglinAiAccess;
import com.extremeprogression.party.Parties;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.skill.Specialization;
import com.extremeprogression.synergy.Synergies;
import com.extremeprogression.synergy.Synergy;
import com.extremeprogression.trunk.Trunks;
import com.extremeprogression.xp.XpService;
import com.extremeprogression.xp.XpTables;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * Trading (design round 27, 1.5.0): its XP from villagers, wandering traders, piglins and raids (Appendix A.11), its
 * trunk, its three specializations, its Mastery perks, and the duo synergies Caravan and Market Day.
 * <ul>
 * <li><b>Merchant</b> (a villager in view): Haggle; Thrifty, Bulk Buyer, Regular, Cashback; Bookworm or Armorer's
 * Friend. Price: villagers gain 20% less experience from your trades.</li>
 * <li><b>Quartermaster</b> (a villager in view): Restock; Stockpile, Mentor, Extra Crate, Fresh Start; Career Boost or
 * Second Shift. Price: wandering traders and piglins give 25% less Trading XP.</li>
 * <li><b>Gold Tongue</b> (gold in hand, or a piglin in view): Gilded Offer; Gold Fever, Wanderer's Deal, Treasure Taste,
 * Long Stay; Golden Calm or Fool's Gold. Price: villagers' prices 10% higher.</li>
 * </ul>
 * Prices work as vanilla's Hero of the Village does: a villager's offers are made cheaper (or dearer) for the player who
 * starts trading, and back to normal when they stop. Items the skill gives are dropped at the player's feet.
 */
public final class Trading {
	private Trading() {}

	/** How long a piglin admires gold before it barters, in vanilla (PiglinAi's ADMIRE_DURATION). */
	static final int ADMIRE_TICKS = 119;
	/** No price drops below this share of the offer's price, whatever adds up. */
	static final double MAX_DISCOUNT = 0.75;

	/** The tier (the villager's level when it was added) of each of a villager's offers, by index. */
	private static AttachmentType<List<Integer>> TIERS;
	/** Who gave each piglin the gold it holds. */
	private static final Map<Piglin, UUID> GIVER = new WeakHashMap<>();
	/** Piglins to barter sooner (Gold Fever, Gilded Offer), and the tick. */
	private static final Map<Piglin, Long> EARLY = new WeakHashMap<>();
	/** The piglin whose barter vanilla is making now (between the start and the end of its barter). */
	private static Piglin bartering = null;
	/** Golden Calm: the tick until which piglins leave a player alone. */
	private static final Map<UUID, Long> CALM = new HashMap<>();
	/** Regular: the day each player last traded with each villager. */
	private static final Map<UUID, Map<UUID, Long>> TRADED = new HashMap<>();
	/** Long Stay: wandering traders whose stay was made longer once already. */
	private static final Set<Entity> STAYING = Collections.newSetFromMap(new WeakHashMap<>());

	static void register() {
		TIERS = AttachmentRegistry.createPersistent(ExtremeProgression.id("offer_tiers"), Codec.INT.listOf());
		// Haggle: 30 s
		AbilityService.register(MERCHANT, (p, rank) -> Active.start(p, MERCHANT, rank, 600));
		// Restock: the villager in view, and one more within 8 blocks a rank after I
		AbilityService.register(QUARTERMASTER, Trading::restock);
		// Gilded Offer: 20 s (+4 s a rank)
		AbilityService.register(GOLD_TONGUE, (p, rank) -> Active.start(p, GOLD_TONGUE, rank, 400 + 80 * (rank - 1)));
	}

	private static double rank(Entity e, Specialization spec, com.extremeprogression.tree.NodeSlot slot) {
		return Specs.rank(e, spec, slot);
	}

	private static double trunk(ServerPlayer p, int level, double share) {
		return Trunks.has(p, Skill.TRADING, level) ? share * Specs.power(p, Skill.TRADING) : 0;
	}

	private static long tick(Entity e) {
		MinecraftServer server = e.level().getServer();
		return server == null ? 0 : server.getTickCount();
	}

	/** A game day, for Regular. */
	private static long day(ServerPlayer p) {
		return p.level().getServer().overworld().getGameTime() / 24000L;
	}

	private static void give(ServerPlayer p, ItemStack stack) {
		if (!stack.isEmpty() && p.level() instanceof ServerLevel level) {
			p.spawnAtLocation(level, stack);
		}
	}

	// --- Prices ----------------------------------------------------------------------------------

	/** Haggle's price drop: 20% at rank I, 5% more a rank. */
	static double haggle(ServerPlayer p) {
		Active.State s = Active.get(p, MERCHANT);
		return s == null ? 0 : 0.20 + 0.05 * (s.rank - 1);
	}

	/** A duo synergy's share with a party member within 32 blocks who has its second skill, or 0. */
	private static double duo(ServerPlayer p, Synergy s) {
		for (ServerPlayer member : Parties.near(p, 32)) {
			if (Synergies.trigger(p, member, s)) {
				return s.strength * Synergies.power(p, member, s);
			}
		}
		return 0;
	}

	/** Makes an offer cheaper by this share of its price (dearer for a negative share), at most {@link #MAX_DISCOUNT}. */
	static void discount(MerchantOffer offer, double share) {
		share = Math.max(-1, Math.min(MAX_DISCOUNT, share));
		int n = (int) Math.round(share * offer.getBaseCostA().getCount());
		if (n != 0) {
			offer.addToSpecialPriceDiff(-n);
		}
	}

	/**
	 * A villager's prices for the player who starts trading, after vanilla's reputation and Hero of the Village: Fair
	 * Price, Thrifty, Bulk Buyer, Regular, Bookworm or Armorer's Friend, Haggle, Patron and Market Day; Gold Tongue's
	 * price.
	 */
	public static void villagerPrices(Villager v, Player player) {
		if (!(player instanceof ServerPlayer p)) {
			return;
		}
		double share = trunk(p, 5, 0.05) + 0.02 * rank(p, MERCHANT, P1) + Mastery.bonus(p, MasteryPerk.PATRON) + (Active.on(p, MERCHANT) ? haggle(p) : 0);
		if (regular(p, v)) {
			share += 0.02 * rank(p, MERCHANT, P3);
		}
		Holder<VillagerProfession> job = v.getVillagerData().profession();
		if (job.is(VillagerProfession.LIBRARIAN)) {
			share += 0.04 * rank(p, MERCHANT, C1);
		}
		if (job.is(VillagerProfession.ARMORER) || job.is(VillagerProfession.TOOLSMITH) || job.is(VillagerProfession.WEAPONSMITH)) {
			share += 0.04 * rank(p, MERCHANT, C2);
		}
		if (job.is(VillagerProfession.FARMER) || job.is(VillagerProfession.BUTCHER)) {
			share += duo(p, Synergy.MARKET_DAY);
		}
		if (Specs.has(p, GOLD_TONGUE)) {
			share -= 0.10;
		}
		double bulk = 0.03 * rank(p, MERCHANT, P2);
		for (MerchantOffer offer : v.getOffers()) {
			discount(offer, share + (offer.getBaseCostA().getCount() >= 16 ? bulk : 0));
		}
	}

	/** A wandering trader's prices: Wanderer's Deal and Caravan for its player, and back to normal when they stop. */
	public static void tradingWith(AbstractVillager merchant, Player player) {
		if (!(merchant instanceof WanderingTrader trader) || merchant.level().isClientSide()) {
			return;
		}
		double share = player instanceof ServerPlayer p ? 0.04 * rank(p, GOLD_TONGUE, P2) + duo(p, Synergy.CARAVAN) : 0;
		for (MerchantOffer offer : trader.getOffers()) {
			offer.resetSpecialPriceDiff();
			if (share != 0) {
				discount(offer, share);
			}
		}
	}

	private static boolean regular(ServerPlayer p, Villager v) {
		Map<UUID, Long> mine = TRADED.get(p.getUUID());
		Long when = mine == null ? null : mine.get(v.getUUID());
		return when != null && when == day(p);
	}

	// --- Trades ----------------------------------------------------------------------------------

	/** The tier of a villager's offer: the level it was added at, or for older offers 2 a level from novice up. */
	public static int tier(AbstractVillager merchant, MerchantOffer offer) {
		int index = merchant.getOffers().indexOf(offer);
		if (index < 0) {
			return 1;
		}
		List<Integer> tiers = ((AttachmentTarget) merchant).getAttached(TIERS);
		if (tiers != null && index < tiers.size()) {
			return Math.max(1, Math.min(5, tiers.get(index)));
		}
		return Math.min(5, index / 2 + 1);
	}

	/** A villager added offers from this index on: they are of its level now. */
	public static void tradesAdded(Villager v, int from) {
		if (v.level().isClientSide()) {
			return;
		}
		int size = v.getOffers().size();
		List<Integer> old = ((AttachmentTarget) v).getAttached(TIERS);
		List<Integer> tiers = new ArrayList<>(old == null ? List.of() : old);
		while (tiers.size() < from) {
			tiers.add(Math.min(5, tiers.size() / 2 + 1));
		}
		while (tiers.size() > from) {
			tiers.remove(tiers.size() - 1);
		}
		int level = v.getVillagerData().level();
		for (int i = from; i < size; i++) {
			tiers.add(level);
		}
		((AttachmentTarget) v).setAttached(TIERS, List.copyOf(tiers));
	}

	/** The emeralds paid and received in a trade. */
	static int emeralds(MerchantOffer offer) {
		int n = 0;
		for (ItemStack s : new ItemStack[] {offer.getCostA(), offer.getCostB(), offer.getResult()}) {
			if (s.is(Items.EMERALD)) {
				n += s.getCount();
			}
		}
		return n;
	}

	/** The XP a trade gives (Appendix A.11): the tier or the wandering trader's, the emeralds, the same offer's share. */
	public static double tradeXp(AbstractVillager merchant, MerchantOffer offer) {
		boolean wandering = merchant instanceof WanderingTrader;
		double base = wandering ? XpTables.TRADE_JOURNEYMAN * XpTables.WANDERING_FACTOR : XpTables.trade(tier(merchant, offer));
		double share = offer.getUses() <= XpTables.SAME_OFFER_FULL ? 1 : XpTables.SAME_OFFER_FACTOR;
		return (base + XpTables.TRADE_PER_EMERALD * emeralds(offer)) * share;
	}

	/**
	 * After a trade (vanilla used up the offer and gave the villager its experience): the Trading XP, and Well
	 * Stocked, Stockpile, Cashback, Extra Crate and Golden Touch.
	 */
	public static void traded(AbstractVillager merchant, MerchantOffer offer) {
		if (merchant.level().isClientSide() || !(merchant.getTradingPlayer() instanceof ServerPlayer p)) {
			return;
		}
		boolean wandering = merchant instanceof WanderingTrader;
		if (!MobOrigins.flagged(merchant)) {
			double xp = tradeXp(merchant, offer) * (wandering && Specs.has(p, QUARTERMASTER) ? 0.75 : 1);
			XpService.award(p, Skill.TRADING, xp);
		}
		double keep = trunk(p, 20, 0.05) + 0.04 * rank(p, QUARTERMASTER, P1);
		if (offer.getUses() > 0 && Specs.roll(p, keep)) {
			((MerchantOfferAccess) offer).extremeprogression$setUses(offer.getUses() - 1);
		}
		if (Specs.roll(p, 0.03 * rank(p, MERCHANT, P4))) {
			give(p, new ItemStack(Items.EMERALD));
		}
		ItemStack result = offer.getResult();
		if (result.getMaxStackSize() > 1 && Specs.roll(p, 0.03 * rank(p, QUARTERMASTER, P3))) {
			give(p, result.copyWithCount(1));
		}
		if (wandering && Specs.roll(p, Mastery.bonus(p, MasteryPerk.GOLDEN_TOUCH))) {
			give(p, result.copy());
		}
		if (merchant instanceof Villager) {
			TRADED.computeIfAbsent(p.getUUID(), k -> new HashMap<>()).put(merchant.getUUID(), day(p));
		}
	}

	/** The experience a trade gave the villager: Good Word +10%, Mentor +5% a rank; the Merchant's price −20%. */
	public static void villagerXp(Villager v, MerchantOffer offer) {
		if (v.level().isClientSide() || !(v.getTradingPlayer() instanceof ServerPlayer p)) {
			return;
		}
		double more = trunk(p, 10, 0.10) + 0.05 * rank(p, QUARTERMASTER, P2) - (Specs.has(p, MERCHANT) ? 0.20 : 0);
		double extra = offer.getXp() * more;
		int whole = (int) Math.floor(extra);
		if (p.getRandom().nextDouble() < extra - whole) {
			whole++;
		}
		if (whole != 0) {
			v.setVillagerXp(Math.max(0, v.getVillagerXp() + whole));
		}
	}

	/** A villager reached a new level through a player's trade: Trading XP (Career Boost +10% a rank). */
	public static void levelledUp(Villager v, Player last) {
		if (!(last instanceof ServerPlayer p) || MobOrigins.flagged(v)) {
			return;
		}
		double xp = XpTables.levelUp(v.getVillagerData().level()) * (1 + 0.10 * rank(p, QUARTERMASTER, C1));
		if (xp > 0) {
			XpService.award(p, Skill.TRADING, xp);
		}
	}

	/** A villager restocked: Fresh Start undoes the rise of its prices from demand. */
	public static void restocked(Villager v, Player last) {
		if (last instanceof ServerPlayer p && Specs.roll(p, 0.20 * rank(p, QUARTERMASTER, P4))) {
			for (MerchantOffer offer : v.getOffers()) {
				((MerchantOfferAccess) offer).extremeprogression$setDemand(0);
			}
		}
	}

	/** Restock: the villager in view restocks, and one more within 8 blocks a rank after I; Second Shift −20 s a rank. */
	private static void restock(ServerPlayer p, int rank) {
		List<Villager> villagers = new ArrayList<>();
		if (com.extremeprogression.ability.Triggers.lookingAtEntity(p, com.extremeprogression.ability.Triggers.TRADERS) instanceof Villager v) {
			villagers.add(v);
		}
		List<Villager> near = new ArrayList<>(p.level().getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(8), v -> v.isAlive() && !villagers.contains(v)));
		near.sort(Comparator.comparingDouble(v -> v.distanceToSqr(p)));
		for (Villager v : near) {
			if (villagers.size() >= rank) {
				break;
			}
			villagers.add(v);
		}
		for (Villager v : villagers) {
			v.restock();
		}
		double sooner = 400 * rank(p, QUARTERMASTER, C2);
		if (sooner > 0) {
			SkillData d = PlayerStore.get(p.getUUID()).get(Skill.TRADING);
			Long until = d.cooldowns.get(QUARTERMASTER.id);
			if (until != null) {
				d.cooldowns.put(QUARTERMASTER.id, until - Math.round(sooner));
				PlayerStore.markDirty(p.getUUID());
				com.extremeprogression.network.Sync.send(p);
			}
		}
	}

	// --- Piglins ---------------------------------------------------------------------------------

	/** A piglin picks up gold a player threw. */
	public static void piglinPicksUp(Piglin piglin, ItemEntity item) {
		if (!piglin.level().isClientSide() && item.getItem().is(Items.GOLD_INGOT) && item.getOwner() instanceof ServerPlayer p) {
			given(piglin, p);
		}
	}

	/** A player hands a piglin gold. */
	public static void piglinGiven(Piglin piglin, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer p && p.getItemInHand(hand).is(Items.GOLD_INGOT)) {
			given(piglin, p);
		}
	}

	private static void given(Piglin piglin, ServerPlayer p) {
		GIVER.put(piglin, p.getUUID());
		long now = tick(p);
		double fever = 0.10 * rank(p, GOLD_TONGUE, P1);
		if (Active.on(p, GOLD_TONGUE)) {
			EARLY.put(piglin, now + 2);
		} else if (fever > 0) {
			EARLY.put(piglin, now + Math.max(2, Math.round(ADMIRE_TICKS * (1 - fever))));
		}
	}

	/** Vanilla starts to end a piglin's admiring: it is a barter when it holds gold and barters. */
	public static void barterStart(Piglin piglin, boolean barter) {
		bartering = !piglin.level().isClientSide() && barter && !piglin.isBaby() && piglin.getOffhandItem().is(Items.GOLD_INGOT) ? piglin : null;
	}

	/**
	 * After a piglin's barter for a player's gold: Trading XP (the Quartermaster's price −25%), more rolls (Barterer,
	 * Gilded Offer, Treasure Taste, Golden Touch), Fool's Gold and Golden Calm.
	 */
	public static void barterEnd(ServerLevel level, Piglin piglin) {
		if (bartering != piglin) {
			return;
		}
		bartering = null;
		EARLY.remove(piglin);
		UUID id = GIVER.remove(piglin);
		ServerPlayer p = id == null ? null : level.getServer().getPlayerList().getPlayer(id);
		if (p == null) {
			return;
		}
		if (!MobOrigins.flagged(piglin)) {
			XpService.award(p, Skill.TRADING, XpTables.BARTER_PER_INGOT * (Specs.has(p, QUARTERMASTER) ? 0.75 : 1));
		}
		int rolls = (Active.on(p, GOLD_TONGUE) ? 1 : 0) + (Specs.roll(p, trunk(p, 15, 0.10)) ? 1 : 0) + (Specs.roll(p, 0.03 * rank(p, GOLD_TONGUE, P3)) ? 1 : 0)
				+ (Specs.roll(p, Mastery.bonus(p, MasteryPerk.GOLDEN_TOUCH)) ? 1 : 0);
		for (int i = 0; i < rolls; i++) {
			PiglinAiAccess.extremeprogression$throwItems(piglin, PiglinAiAccess.extremeprogression$getBarterResponseItems(piglin));
		}
		if (Specs.roll(p, 0.04 * rank(p, GOLD_TONGUE, C2))) {
			give(p, new ItemStack(Items.GOLD_INGOT));
		}
		double calm = 200 * rank(p, GOLD_TONGUE, C1);
		if (calm > 0) {
			CALM.put(p.getUUID(), tick(p) + Math.round(calm));
		}
	}

	/** Golden Calm: whether piglins leave this player alone now, as if they wore gold. */
	public static boolean calm(LivingEntity entity) {
		Long until = entity instanceof ServerPlayer p ? CALM.get(p.getUUID()) : null;
		return until != null && until > tick(entity);
	}

	// --- Raids, and every tick -------------------------------------------------------------------

	/** A raid won: Hero of the Village gives Trading XP by the raid's omen level. */
	public static void effect(LivingEntity target, MobEffectInstance effect) {
		if (target instanceof ServerPlayer p && effect.getEffect().equals(MobEffects.HERO_OF_THE_VILLAGE) && effect.getDuration() >= 40000) {
			XpService.award(p, Skill.TRADING, XpTables.RAID_PER_OMEN_LEVEL * (effect.getAmplifier() + 1));
		}
	}

	/** Every tick: the piglins that barter sooner; every 5 seconds, Long Stay. */
	public static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		List<Piglin> due = new ArrayList<>();
		EARLY.entrySet().removeIf(e -> e.getKey() == null || !e.getKey().isAlive());
		for (Map.Entry<Piglin, Long> e : EARLY.entrySet()) {
			if (now >= e.getValue()) {
				due.add(e.getKey());
			}
		}
		for (Piglin piglin : due) {
			EARLY.remove(piglin);
			if (piglin.getOffhandItem().is(Items.GOLD_INGOT) && piglin.level() instanceof ServerLevel level) {
				PiglinAiAccess.extremeprogression$stopHoldingOffHandItem(level, piglin, true);
			}
		}
		if (now % 100 != 0) {
			return;
		}
		CALM.values().removeIf(until -> until <= now);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			double stay = 0.10 * rank(p, GOLD_TONGUE, P4);
			if (stay <= 0) {
				continue;
			}
			for (WanderingTrader trader : p.level().getEntitiesOfClass(WanderingTrader.class, p.getBoundingBox().inflate(32), WanderingTrader::isAlive)) {
				if (STAYING.add(trader)) {
					trader.setDespawnDelay((int) Math.round(trader.getDespawnDelay() * (1 + stay)));
				}
			}
		}
	}

	public static void forget(UUID id) {
		CALM.remove(id);
		TRADED.remove(id);
	}
}
