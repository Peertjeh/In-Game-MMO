package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;

import com.extremeprogression.spec.Arcana;
import com.extremeprogression.spec.Smithing;
import com.extremeprogression.trunk.Trunks;
import com.extremeprogression.xp.CraftingXp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The anvil: the trunk's and the specializations' costs and repairs on the result shown, and what happens when a player
 * takes it (Smithing XP for a repair, Arcana for combined enchantments; Masterwork, Gentle Hammer, Spare Material,
 * Reforge).
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
	@Shadow
	@Final
	private DataSlot cost;

	/** The voices the result shown now needs when Echo Forging raised it (1.8.0; the ritual, 1.9.0), or -1. */
	@org.spongepowered.asm.mixin.Unique
	private int extremeprogression$echo = -1;

	/** Where the anvil stands (where the player stands, for a menu without a place). */
	@org.spongepowered.asm.mixin.Unique
	private BlockPos extremeprogression$anvil(Player player) {
		return ((ItemCombinerMenuAccess) (Object) this).extremeprogression$access().evaluate((level, pos) -> pos)
				.orElse(player == null ? BlockPos.ZERO : player.blockPosition());
	}

	/** The trunk and the specializations at the anvil: fewer levels, more (or less) restored. */
	@Inject(method = "createResult", at = @At("TAIL"), require = 1)
	private void extremeprogression$trunk(CallbackInfo ci) {
		AnvilMenu self = (AnvilMenu) (Object) this;
		Player player = ((ItemCombinerMenuAccess) self).extremeprogression$player();
		// Echo Forging: beyond the maximum; and what is beyond it already is never lost
		this.extremeprogression$echo = com.extremeprogression.synergy.SynergyEffects.echoForge(self, player, extremeprogression$anvil(player), this.cost);
		com.extremeprogression.synergy.SynergyEffects.keepBeyond(self);
		ItemStack result = self.getSlot(2).getItem();
		if (result.isEmpty()) {
			return;
		}
		ItemStack left = self.getSlot(0).getItem();
		int levels = Trunks.anvilCost(player, this.cost.get());
		if (left.isDamageableItem() && result.getDamageValue() < left.getDamageValue()) {
			levels = Smithing.repairCost(player, levels);
		}
		if (self.getSlot(1).getItem().has(DataComponents.STORED_ENCHANTMENTS)) {
			levels = Arcana.bookCost(player, levels);
		}
		this.cost.set(levels);
		Trunks.anvilRepair(player, left, result);
	}

	/** Preservation (Armorer): the "Too Expensive" limit rises. */
	@ModifyConstant(method = "createResult", constant = @Constant(intValue = 40), require = 1)
	private int extremeprogression$tooExpensive(int limit) {
		return Smithing.tooExpensive(((ItemCombinerMenuAccess) (Object) this).extremeprogression$player());
	}

	/** Echo Forging's result stays where it is when its voices left (or the forger let go of the echo shard). */
	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true, require = 1)
	private void extremeprogression$echoHeard(Player player, boolean hasItem, CallbackInfoReturnable<Boolean> cir) {
		if (this.extremeprogression$echo >= 0
				&& !com.extremeprogression.synergy.SynergyEffects.echoHeard(player, extremeprogression$anvil(player), this.extremeprogression$echo)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "onTake", at = @At("HEAD"), require = 1)
	private void extremeprogression$echoTaken(Player player, ItemStack result, CallbackInfo ci) {
		if (this.extremeprogression$echo >= 0 && player instanceof ServerPlayer p) {
			int needed = this.extremeprogression$echo;
			this.extremeprogression$echo = -1;
			com.extremeprogression.synergy.SynergyEffects.echoForged(p, extremeprogression$anvil(p), needed);
		}
	}

	@Inject(method = "onTake", at = @At("HEAD"), require = 1)
	private void extremeprogression$taken(Player player, ItemStack result, CallbackInfo ci, @Share("cost") LocalIntRef paid,
			@Share("anvil") LocalRef<BlockState> anvil, @Share("material") LocalRef<ItemStack> material, @Share("repair") LocalBooleanRef repair) {
		AnvilMenu self = (AnvilMenu) (Object) this;
		ItemStack left = self.getSlot(0).getItem();
		CraftingXp.anvil(player, left.copy(), self.getSlot(1).getItem().copy(), result, self.getCost());
		paid.set(self.getCost());
		material.set(self.getSlot(1).getItem().copy());
		repair.set(left.isDamageableItem() && result.getDamageValue() < left.getDamageValue());
		anvil.set(((ItemCombinerMenuAccess) self).extremeprogression$access().evaluate((level, pos) -> level.getBlockState(pos)).orElse(null));
		if (player instanceof ServerPlayer p && repair.get()) {
			Smithing.reforge(p, result);
		}
	}

	@Inject(method = "onTake", at = @At("TAIL"), require = 1)
	private void extremeprogression$tookIt(Player player, ItemStack result, CallbackInfo ci, @Share("cost") LocalIntRef paid,
			@Share("anvil") LocalRef<BlockState> anvil, @Share("material") LocalRef<ItemStack> material, @Share("repair") LocalBooleanRef repair) {
		if (!(player instanceof ServerPlayer p) || !repair.get()) {
			return;
		}
		AnvilMenu self = (AnvilMenu) (Object) this;
		// Masterwork: it ends, and may give the levels back
		Smithing.masterworkTaken(p, paid.get());
		// Gentle Hammer: the anvil keeps the state it had
		BlockState before = anvil.get();
		if (before != null) {
			((ItemCombinerMenuAccess) self).extremeprogression$access().execute((level, pos) -> {
				if (level.getBlockState(pos) != before && Smithing.gentleHammer(p)) {
					level.setBlockAndUpdate(pos, before);
				}
			});
		}
		// Spare Material: the material comes back
		ItemStack spent = material.get();
		if (!spent.isEmpty() && !ItemStack.matches(spent, self.getSlot(1).getItem()) && Smithing.spareMaterial(p)) {
			self.getSlot(1).set(spent);
		}
	}
}
