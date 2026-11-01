package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.extremeprogression.spec.Trading;

import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;

/** Trading (1.5.0): every trade with a villager or wandering trader, and a wandering trader's prices for its player. */
@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerMixin {
	@Inject(method = "notifyTrade", at = @At("TAIL"), require = 1)
	private void extremeprogression$traded(MerchantOffer offer, CallbackInfo ci) {
		Trading.traded((AbstractVillager) (Object) this, offer);
	}

	@Inject(method = "setTradingPlayer", at = @At("TAIL"), require = 1)
	private void extremeprogression$tradingWith(Player player, CallbackInfo ci) {
		Trading.tradingWith((AbstractVillager) (Object) this, player);
	}
}
