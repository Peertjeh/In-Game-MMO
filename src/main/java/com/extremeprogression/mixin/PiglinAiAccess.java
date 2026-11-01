package com.extremeprogression.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;

/** A piglin's barter as vanilla does it: ending the admiring (bartering), a roll of its loot, throwing items. */
@Mixin(PiglinAi.class)
public interface PiglinAiAccess {
	@Invoker("stopHoldingOffHandItem")
	static void extremeprogression$stopHoldingOffHandItem(ServerLevel level, Piglin piglin, boolean barter) {
		throw new AssertionError();
	}

	@Invoker("getBarterResponseItems")
	static List<ItemStack> extremeprogression$getBarterResponseItems(Piglin piglin) {
		throw new AssertionError();
	}

	@Invoker("throwItems")
	static void extremeprogression$throwItems(Piglin piglin, List<ItemStack> items) {
		throw new AssertionError();
	}
}
