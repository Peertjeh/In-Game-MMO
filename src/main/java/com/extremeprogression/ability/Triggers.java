package com.extremeprogression.ability;

import java.util.function.Function;

import com.extremeprogression.skill.Skill;
import com.extremeprogression.skill.Specialization;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Which ability the ability key reaches, from what the player holds and looks at (design §6). The same code runs on
 * the client, to show the ready icon for the ability the key would use.
 */
public final class Triggers {
	private Triggers() {}

	/**
	 * The specialization whose ability the key would use now: among the skills' first (index 0, {@code R}) or second
	 * (index 1, {@code Shift + R}) specializations that are in use, the one whose trigger fits, workstations before
	 * tools before anything; on a tie, the first skill. Null when none fits.
	 */
	public static Specialization resolve(Player player, Function<Skill, Specialization> inUse) {
		Specialization best = null;
		for (Skill skill : Skill.values()) {
			Specialization spec = inUse.apply(skill);
			if (spec != null && matches(spec.trigger, player) && (best == null || spec.trigger.priority() < best.trigger.priority())) {
				best = spec;
			}
		}
		return best;
	}

	public static boolean matches(Trigger trigger, Player player) {
		ItemStack held = player.getMainHandItem();
		String id = BuiltInRegistries.ITEM.getKey(held.getItem()).getPath();
		return switch (trigger) {
			case BLADE_WEAPON -> id.endsWith("_sword") || id.endsWith("_spear");
			case AXE_OR_MACE -> held.is(ItemTags.AXES) || held.is(Items.MACE);
			case EMPTY_HAND -> held.isEmpty();
			case BOW_OR_CROSSBOW -> held.is(Items.BOW) || held.is(Items.CROSSBOW);
			case TRIDENT -> held.is(Items.TRIDENT);
			case SHIELD -> held.is(Items.SHIELD) || player.getOffhandItem().is(Items.SHIELD);
			case PICKAXE -> held.is(ItemTags.PICKAXES);
			case SHOVEL_OR_BRUSH -> held.is(ItemTags.SHOVELS) || held.is(Items.BRUSH);
			case AXE_AT_LOG -> held.is(ItemTags.AXES) && lookingAt(player).is(BlockTags.LOGS);
			case HOE -> held.is(ItemTags.HOES);
			case FOOD -> held.has(DataComponents.FOOD);
			case FISHING_ROD -> held.is(Items.FISHING_ROD);
			case SPLASH_OR_LINGERING_POTION -> held.is(Items.SPLASH_POTION) || held.is(Items.LINGERING_POTION);
			case LOOK_FURNACE -> {
				BlockState s = lookingAt(player);
				yield s.is(Blocks.FURNACE) || s.is(Blocks.BLAST_FURNACE) || s.is(Blocks.SMOKER);
			}
			case LOOK_ANVIL -> {
				BlockState s = lookingAt(player);
				yield s.is(Blocks.ANVIL) || s.is(Blocks.CHIPPED_ANVIL) || s.is(Blocks.DAMAGED_ANVIL);
			}
			case LOOK_SMITHING_TABLE -> lookingAt(player).is(Blocks.SMITHING_TABLE);
			case LOOK_BREWING_STAND -> lookingAt(player).is(Blocks.BREWING_STAND);
			case LOOK_ENCHANTING_TABLE -> lookingAt(player).is(Blocks.ENCHANTING_TABLE);
			case ANY -> true;
		};
	}

	/** The block in the player's crosshair within reach (air when none). */
	private static BlockState lookingAt(Player player) {
		HitResult hit = player.pick(player.blockInteractionRange(), 1.0f, false);
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
			return player.level().getBlockState(block.getBlockPos());
		}
		return Blocks.AIR.defaultBlockState();
	}
}
