package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;

/** A villager's prices for a player and its level-up, as trading works them out (Trading, and its tests). */
@Mixin(Villager.class)
public interface VillagerAccess {
	@Invoker("updateSpecialPrices")
	void extremeprogression$updateSpecialPrices(Player player);

	@Invoker("increaseMerchantCareer")
	void extremeprogression$increaseMerchantCareer(ServerLevel level);

	@Accessor("lastTradedPlayer")
	void extremeprogression$setLastTradedPlayer(Player player);
}
