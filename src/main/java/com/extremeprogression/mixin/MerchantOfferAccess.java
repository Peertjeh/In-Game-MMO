package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.item.trading.MerchantOffer;

/** An offer's uses (a trade that does not use it up) and its demand (prices back down after a restock). */
@Mixin(MerchantOffer.class)
public interface MerchantOfferAccess {
	@Accessor("uses")
	void extremeprogression$setUses(int uses);

	@Accessor("demand")
	void extremeprogression$setDemand(int demand);
}
