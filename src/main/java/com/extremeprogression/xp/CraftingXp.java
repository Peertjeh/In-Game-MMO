package com.extremeprogression.xp;

import java.util.Objects;

import com.mojang.serialization.Codec;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.skill.Skill;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/**
 * Smithing and Arcana XP (design §2, Appendix A.9 and A.10), always for what a player takes out by hand: a hopper
 * emptying a furnace or brewing stand gives nothing.
 * <ul>
 * <li>Smithing: metal smelted in a furnace or blast furnace, repairs at the anvil by material, netherite upgrades and
 * armor trims at the smithing table;</li>
 * <li>Arcana: potions the stand brewed, enchanting at the table, combining enchantments at the anvil.</li>
 * </ul>
 */
public final class CraftingXp {
	private CraftingXp() {}

	/** Which of a brewing stand's three bottle slots hold what it brewed itself (a bit per slot), kept on the stand. */
	private static AttachmentType<Integer> BREWED;

	public static void register() {
		BREWED = AttachmentRegistry.createPersistent(ExtremeProgression.id("brewed"), Codec.INT);
	}

	// --- Smithing --------------------------------------------------------------------------------

	/** A player took smelted items out of a furnace's output slot (by hand or shift-click). */
	public static void smelted(Player player, ItemStack stack, int count) {
		if (player instanceof ServerPlayer p && count > 0) {
			XpService.award(p, Skill.SMITHING, XpTables.smelted(stack) * count * com.extremeprogression.spec.Smithing.smeltXp(p));
		}
	}

	/** A player took the result of an anvil: a repair, combined enchantments, or both. */
	public static void anvil(Player player, ItemStack left, ItemStack right, ItemStack result, int cost) {
		if (!(player instanceof ServerPlayer p) || left.isEmpty() || right.isEmpty()) {
			// (renaming alone, with nothing in the second slot, gives nothing)
			return;
		}
		if (left.isDamageableItem() && left.getMaxDamage() > 0) {
			double restored = (double) (left.getDamageValue() - result.getDamageValue()) / left.getMaxDamage();
			if (restored > 0) {
				XpService.award(p, Skill.SMITHING, XpTables.REPAIR * XpTables.material(left) * restored * com.extremeprogression.spec.Smithing.repairXp(p));
			}
		}
		boolean enchanted = !Objects.equals(left.get(DataComponents.ENCHANTMENTS), result.get(DataComponents.ENCHANTMENTS))
				|| !Objects.equals(left.get(DataComponents.STORED_ENCHANTMENTS), result.get(DataComponents.STORED_ENCHANTMENTS));
		if (enchanted && cost > 0) {
			XpService.award(p, Skill.ARCANA, XpTables.ANVIL_ENCHANTING_PER_LEVEL * cost);
		}
	}

	/** A player took the result of a smithing table. */
	public static void smithed(Player player, ItemStack template) {
		if (!(player instanceof ServerPlayer p) || template.isEmpty()) {
			return;
		}
		String id = BuiltInRegistries.ITEM.getKey(template.getItem()).getPath();
		if (id.equals("netherite_upgrade_smithing_template")) {
			XpService.award(p, Skill.SMITHING, XpTables.NETHERITE_UPGRADE * com.extremeprogression.spec.Smithing.smithXp(p));
		} else if (id.endsWith("_armor_trim_smithing_template")) {
			XpService.award(p, Skill.SMITHING, XpTables.ARMOR_TRIM * com.extremeprogression.spec.Smithing.smithXp(p));
		}
	}

	// --- Arcana ----------------------------------------------------------------------------------

	/** A player enchanted at an enchanting table an offer that required this level. */
	public static void enchanted(Player player, int requiredLevel) {
		if (player instanceof ServerPlayer p && requiredLevel > 0) {
			XpService.award(p, Skill.ARCANA, XpTables.ENCHANTING_PER_LEVEL * requiredLevel * com.extremeprogression.spec.Arcana.enchantXp(p));
		}
	}

	private static int brewed(BrewingStandBlockEntity stand) {
		Integer bits = ((AttachmentTarget) stand).getAttached(BREWED);
		return bits == null ? 0 : bits;
	}

	private static void setBrewed(BrewingStandBlockEntity stand, int bits) {
		if (bits != brewed(stand)) {
			((AttachmentTarget) stand).setAttached(BREWED, bits == 0 ? null : bits);
			stand.setChanged();
		}
	}

	/** The stand finished brewing: every bottle in it now is its own work. */
	public static void brewed(BrewingStandBlockEntity stand, boolean server) {
		if (!server) {
			return;
		}
		int bits = 0;
		for (int slot = 0; slot < 3; slot++) {
			if (!stand.getItem(slot).isEmpty()) {
				bits |= 1 << slot;
			}
		}
		setBrewed(stand, bits);
	}

	/** Something was put into a bottle slot (by a player or a hopper): that bottle was not brewed here. */
	public static void placed(BrewingStandBlockEntity stand, int slot, ItemStack stack) {
		if (slot < 3 && !stack.isEmpty() && stand.getLevel() != null && !stand.getLevel().isClientSide()) {
			setBrewed(stand, brewed(stand) & ~(1 << slot));
		}
	}

	/** A player took a bottle out of a brewing stand. */
	public static void potionTaken(Player player, BrewingStandBlockEntity stand, int slot, ItemStack stack) {
		if (!(player instanceof ServerPlayer p) || slot < 0 || slot >= 3 || (brewed(stand) & (1 << slot)) == 0) {
			return;
		}
		setBrewed(stand, brewed(stand) & ~(1 << slot));
		XpService.award(p, Skill.ARCANA, potionXp(stack) * com.extremeprogression.spec.Arcana.brewXp(p));
	}

	/** A potion's Arcana XP by its form and kind (Appendix A.10). */
	public static double potionXp(ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents == null) {
			return 0;
		}
		if (contents.potion().isEmpty()) {
			// (the Alchemist's own potions have their effects without a vanilla potion: they count as one with an effect)
			return contents.customEffects().isEmpty() ? 0 : XpTables.potion(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), "custom");
		}
		String potion = contents.potion().get().getRegisteredName();
		potion = potion.substring(potion.indexOf(':') + 1);
		return XpTables.potion(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), potion);
	}
}
