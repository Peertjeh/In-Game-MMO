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
			case LOOK_VILLAGER -> lookingAtEntity(player, TRADERS) != null;
			case GOLD_OR_PIGLIN -> held.is(Items.GOLD_INGOT) || player.getOffhandItem().is(Items.GOLD_INGOT) || lookingAtEntity(player, PIGLINS) != null;
			case ANY -> true;
		};
	}

	/** Villagers and wandering traders, by entity id (other mods' merchants are left alone). */
	public static final java.util.Set<String> TRADERS = java.util.Set.of("minecraft:villager", "minecraft:wandering_trader");
	public static final java.util.Set<String> PIGLINS = java.util.Set.of("minecraft:piglin");

	/**
	 * The nearest entity of these types the player looks at within reach + 1 block (its centre within a narrow cone of
	 * the view), or null. On the client too, for the ready icon.
	 */
	public static net.minecraft.world.entity.Entity lookingAtEntity(Player player, java.util.Set<String> types) {
		double reach = player.entityInteractionRange() + 1;
		net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
		net.minecraft.world.phys.Vec3 view = player.getViewVector(1.0f);
		net.minecraft.world.entity.Entity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (net.minecraft.world.entity.Entity e : player.level().getEntities(player, player.getBoundingBox().inflate(reach),
				e -> e.isAlive() && types.contains(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString()))) {
			net.minecraft.world.phys.Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance <= reach && distance > 1e-6 && to.normalize().dot(view) > 0.92 && distance < bestDistance) {
				best = e;
				bestDistance = distance;
			}
		}
		return best;
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
