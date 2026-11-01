package com.extremeprogression.gametest;

import static com.extremeprogression.gametest.SkeletonTests.check;
import static com.extremeprogression.gametest.SpecTests.specialist;

import com.extremeprogression.ability.AbilityService;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.mixin.PiglinAiAccess;
import com.extremeprogression.mixin.VillagerAccess;
import com.extremeprogression.party.Parties;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.skill.Specialization;
import com.extremeprogression.spec.Active;
import com.extremeprogression.spec.Trading;
import com.extremeprogression.synergy.Synergies;
import com.extremeprogression.synergy.Synergy;
import com.extremeprogression.tree.NodeSlot;
import com.extremeprogression.xp.XpService;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Trading (design round 27, 1.5.0): its XP from villagers, wandering traders, level-ups, piglins and raids, the same
 * offer's rule and spawn eggs, the prices of its trunk, Haggle and Gold Tongue, the experience villagers gain, Restock
 * and Stockpile, Golden Calm, and the duo synergy Caravan.
 */
public class TradingTests {
	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1e-3;
	}

	/** A novice offer: this many emeralds for a loaf of bread, 1000 uses, 1 villager experience (no level-up). */
	private static MerchantOffer offer(int emeralds) {
		return offer(emeralds, 1);
	}

	private static MerchantOffer offer(int emeralds, int villagerXp) {
		return new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), new ItemStack(Items.BREAD), 1000, villagerXp, 0.05f);
	}

	/** A wandering trader's offers are its own: the test's offer replaces them in its list. */
	private static void sells(WanderingTrader trader, MerchantOffer offer) {
		trader.getOffers().clear();
		trader.getOffers().add(offer);
	}

	private static MerchantOffers offers(MerchantOffer... offers) {
		MerchantOffers list = new MerchantOffers();
		for (MerchantOffer o : offers) {
			list.add(o);
		}
		return list;
	}

	private static Villager villager(GameTestHelper helper, EntitySpawnReason reason, MerchantOffer... offers) {
		Villager v = (Villager) helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1), reason);
		v.setOffers(offers(offers));
		return v;
	}

	/** The Trading XP counted for this player so far (before the multipliers, which are 1 here). */
	private static double xp(ServerPlayer p) {
		return XpService.recent(p, Skill.TRADING);
	}

	@GameTest
	public void tradesGiveXpByTierAndEmeraldsAndTheSameOfferLess(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Trader");
		MerchantOffer bread = offer(2);
		Villager v = villager(helper, EntitySpawnReason.NATURAL, bread);
		v.setTradingPlayer(p);
		v.notifyTrade(bread);
		check(helper, near(xp(p), 33), "a novice trade of 2 emeralds gave %s Trading XP, expected 33", xp(p));
		for (int i = 0; i < 4; i++) {
			v.notifyTrade(bread);
		}
		check(helper, near(xp(p), 33 * 4 + 33 * 0.25), "five trades of the same offer gave %s, expected four full and one at 25%%", xp(p));
		check(helper, Trading.tier(v, bread) == 1, "the first offer is not novice");
		helper.succeed();
	}

	@GameTest
	public void wanderingTradersCountMoreAndSpawnEggVillagersNothing(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Wanderer");
		WanderingTrader trader = (WanderingTrader) helper.spawn(EntityTypes.WANDERING_TRADER, new BlockPos(1, 2, 1), EntitySpawnReason.NATURAL);
		MerchantOffer deal = offer(5);
		sells(trader, deal);
		trader.setTradingPlayer(p);
		trader.notifyTrade(deal);
		check(helper, near(xp(p), 65 * 1.5 + 1.5 * 5), "a wandering trader's trade of 5 emeralds gave %s, expected 105", xp(p));
		trader.setTradingPlayer(null);
		MerchantOffer egg = offer(2);
		Villager fromEgg = villager(helper, EntitySpawnReason.SPAWN_ITEM_USE, egg);
		fromEgg.setTradingPlayer(p);
		fromEgg.notifyTrade(egg);
		check(helper, near(xp(p), 105), "a villager from a spawn egg gave Trading XP: %s", xp(p));
		helper.succeed();
	}

	@GameTest
	public void levelUpsRaidsAndBartersGiveXp(GameTestHelper helper) {
		Config.reset();
		ServerPlayer p = TestPlayers.survivor(helper, "Hero");
		Villager v = villager(helper, EntitySpawnReason.NATURAL, offer(1));
		((VillagerAccess) v).extremeprogression$setLastTradedPlayer(p);
		((VillagerAccess) v).extremeprogression$increaseMerchantCareer(helper.getLevel());
		check(helper, v.getVillagerData().level() == 2 && near(xp(p), 150), "a villager reaching apprentice gave %s, expected 150", xp(p));
		p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 48000, 2));
		check(helper, near(xp(p), 150 + 3000), "a won raid of omen level 3 gave %s in all, expected 3150", xp(p));
		// A piglin barters the gold the player handed it
		Piglin piglin = (Piglin) helper.spawn(EntityTypes.PIGLIN, new BlockPos(1, 2, 2), EntitySpawnReason.NATURAL);
		piglin.setBaby(false);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT));
		Trading.piglinGiven(piglin, p, InteractionHand.MAIN_HAND);
		piglin.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.GOLD_INGOT));
		PiglinAiAccess.extremeprogression$stopHoldingOffHandItem(helper.getLevel(), piglin, true);
		check(helper, near(xp(p), 3150 + 20) && piglin.getOffhandItem().isEmpty(), "a barter gave %s in all, expected 3170", xp(p));
		helper.succeed();
	}

	@GameTest
	public void pricesFollowTheTrunkHaggleAndGoldTongue(GameTestHelper helper) {
		Config.reset();
		MerchantOffer dear = offer(20);
		Villager v = villager(helper, EntitySpawnReason.NATURAL, dear);
		ServerPlayer fair = TestPlayers.survivor(helper, "Fair");
		Progression.setLevel(PlayerStore.get(fair.getUUID()), Skill.TRADING, 5);
		((VillagerAccess) v).extremeprogression$updateSpecialPrices(fair);
		check(helper, dear.getCostA().getCount() == 19, "Fair Price made 20 emeralds %s, expected 19", dear.getCostA().getCount());
		dear.resetSpecialPriceDiff();
		ServerPlayer haggler = specialist(helper, "Haggler", Specialization.MERCHANT, 25);
		Active.start(haggler, Specialization.MERCHANT, 1, 600);
		((VillagerAccess) v).extremeprogression$updateSpecialPrices(haggler);
		check(helper, dear.getCostA().getCount() == 15, "Haggle and Fair Price made 20 emeralds %s, expected 15", dear.getCostA().getCount());
		dear.resetSpecialPriceDiff();
		ServerPlayer gold = specialist(helper, "Golden", Specialization.GOLD_TONGUE, 25);
		((VillagerAccess) v).extremeprogression$updateSpecialPrices(gold);
		check(helper, dear.getCostA().getCount() == 21, "Gold Tongue's price made 20 emeralds %s, expected 21", dear.getCostA().getCount());
		helper.succeed();
	}

	@GameTest
	public void villagersGainExperienceAsTheSkillSays(GameTestHelper helper) {
		Config.reset();
		MerchantOffer trade = offer(1, 10);
		Villager v = villager(helper, EntitySpawnReason.NATURAL, trade);
		ServerPlayer friend = TestPlayers.survivor(helper, "Friend");
		Progression.setLevel(PlayerStore.get(friend.getUUID()), Skill.TRADING, 10);
		v.setTradingPlayer(friend);
		v.notifyTrade(trade);
		check(helper, v.getVillagerXp() == 11, "Good Word gave the villager %s experience, expected 11", v.getVillagerXp());
		Villager other = villager(helper, EntitySpawnReason.NATURAL, trade);
		ServerPlayer merchant = specialist(helper, "Seller", Specialization.MERCHANT, 25);
		other.setTradingPlayer(merchant);
		other.notifyTrade(trade);
		check(helper, other.getVillagerXp() == 9, "the Merchant's price gave the villager %s experience, expected 9", other.getVillagerXp());
		helper.succeed();
	}

	@GameTest
	public void restockAndStockpile(GameTestHelper helper) {
		Config.reset();
		MerchantOffer bulk = offer(1);
		Villager v = villager(helper, EntitySpawnReason.NATURAL, bulk);
		ServerPlayer keeper = specialist(helper, "Keeper", Specialization.QUARTERMASTER, 25, NodeSlot.A1, 1, NodeSlot.P1, 5);
		v.setTradingPlayer(keeper);
		for (int i = 0; i < 400; i++) {
			v.notifyTrade(bulk);
		}
		// (Well Stocked 5% and Stockpile 20%: about 300 of 400 trades use the offer up)
		check(helper, bulk.getUses() > 255 && bulk.getUses() < 345, "400 trades used the offer %s times, expected about 300", bulk.getUses());
		v.setTradingPlayer(null);
		// Restock, looking at the villager
		keeper.setPos(v.getX(), v.getY(), v.getZ() + 2.5);
		keeper.setYRot(180);
		keeper.setYHeadRot(180);
		keeper.setXRot(15);
		check(helper, AbilityService.use(keeper, false) == AbilityService.Result.USED, "Restock was not used");
		check(helper, bulk.getUses() == 0, "Restock left %s uses", bulk.getUses());
		helper.succeed();
	}

	@GameTest
	public void goldenCalmKeepsPiglinsAwayAfterABarter(GameTestHelper helper) {
		Config.reset();
		ServerPlayer calm = specialist(helper, "Calm", Specialization.GOLD_TONGUE, 25, NodeSlot.C1, 1);
		check(helper, !PiglinAi.isWearingSafeArmor(calm), "a player without gold counted as wearing it");
		Piglin piglin = (Piglin) helper.spawn(EntityTypes.PIGLIN, new BlockPos(1, 2, 2), EntitySpawnReason.NATURAL);
		piglin.setBaby(false);
		calm.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT));
		Trading.piglinGiven(piglin, calm, InteractionHand.MAIN_HAND);
		piglin.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.GOLD_INGOT));
		PiglinAiAccess.extremeprogression$stopHoldingOffHandItem(helper.getLevel(), piglin, true);
		check(helper, PiglinAi.isWearingSafeArmor(calm), "Golden Calm did not keep piglins away after the barter");
		helper.succeed();
	}

	@GameTest
	public void caravanMakesAWanderingTraderCheaperTogether(GameTestHelper helper) {
		Config.reset();
		ServerPlayer buyer = TestPlayers.survivor(helper, "Buyer"), scout = TestPlayers.survivor(helper, "Scout");
		Progression.setLevel(PlayerStore.get(buyer.getUUID()), Skill.TRADING, 30);
		Progression.setLevel(PlayerStore.get(scout.getUUID()), Skill.EXPLORATION, 30);
		BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
		buyer.setPos(at.getX(), at.getY(), at.getZ());
		scout.setPos(at.getX() + 1, at.getY(), at.getZ());
		Parties.invite(buyer, scout);
		Parties.accept(scout, null);
		WanderingTrader trader = (WanderingTrader) helper.spawn(EntityTypes.WANDERING_TRADER, new BlockPos(1, 2, 2), EntitySpawnReason.NATURAL);
		MerchantOffer deal = offer(20);
		sells(trader, deal);
		trader.setTradingPlayer(buyer);
		check(helper, deal.getCostA().getCount() == 18, "Caravan made 20 emeralds %s, expected 18", deal.getCostA().getCount());
		check(helper, Synergies.known(buyer, Synergy.CARAVAN) && Synergies.known(scout, Synergy.CARAVAN), "the two did not discover Caravan");
		trader.setTradingPlayer(null);
		check(helper, deal.getCostA().getCount() == 20, "the price did not go back after trading: %s", deal.getCostA().getCount());
		Parties.leave(buyer);
		helper.succeed();
	}
}
