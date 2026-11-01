package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.spec.Trading;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;

/**
 * Trading (1.5.0) at the villager: its prices for the player who starts trading, the experience a trade gives it,
 * its level-ups and restocks, and the tier of every offer it adds.
 */
@Mixin(Villager.class)
public abstract class VillagerMixin extends AbstractVillager {
	@Shadow
	private Player lastTradedPlayer;

	@Unique
	private int extremeprogression$offersBefore;

	protected VillagerMixin(EntityType<? extends AbstractVillager> type, Level level) {
		super(type, level);
	}

	@Inject(method = "updateSpecialPrices", at = @At("TAIL"), require = 1)
	private void extremeprogression$prices(Player player, CallbackInfo ci) {
		Trading.villagerPrices((Villager) (Object) this, player);
	}

	@Inject(method = "rewardTradeXp", at = @At("TAIL"), require = 1)
	private void extremeprogression$villagerXp(MerchantOffer offer, CallbackInfo ci) {
		Trading.villagerXp((Villager) (Object) this, offer);
	}

	@Inject(method = "increaseMerchantCareer", at = @At("TAIL"), require = 1)
	private void extremeprogression$career(ServerLevel level, CallbackInfo ci) {
		Trading.levelledUp((Villager) (Object) this, lastTradedPlayer);
	}

	@Inject(method = "restock", at = @At("TAIL"), require = 1)
	private void extremeprogression$restocked(CallbackInfo ci) {
		Trading.restocked((Villager) (Object) this, lastTradedPlayer);
	}

	@Inject(method = "updateTrades", at = @At("HEAD"), require = 1)
	private void extremeprogression$tradesBefore(ServerLevel level, CallbackInfo ci) {
		extremeprogression$offersBefore = this.offers == null ? 0 : this.offers.size();
	}

	@Inject(method = "updateTrades", at = @At("TAIL"), require = 1)
	private void extremeprogression$tradesAdded(ServerLevel level, CallbackInfo ci) {
		Trading.tradesAdded((Villager) (Object) this, extremeprogression$offersBefore);
	}
}
