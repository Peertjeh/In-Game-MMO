package com.extremeprogression.spec;

import static com.extremeprogression.skill.Specialization.ARMORER;
import static com.extremeprogression.skill.Specialization.FORGEMASTER;
import static com.extremeprogression.skill.Specialization.TINKERER;
import static com.extremeprogression.spec.Specs.rank;
import static com.extremeprogression.tree.NodeSlot.C1;
import static com.extremeprogression.tree.NodeSlot.C2;
import static com.extremeprogression.tree.NodeSlot.P1;
import static com.extremeprogression.tree.NodeSlot.P2;
import static com.extremeprogression.tree.NodeSlot.P3;
import static com.extremeprogression.tree.NodeSlot.P4;

import java.util.HashMap;
import java.util.Map;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.ability.AbilityService;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.trunk.Trunks;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.equipment.trim.ArmorTrim;

/**
 * Smithing's three specializations (design §2, skill 9). Their abilities are used looking at the workstation.
 * <ul>
 * <li><b>Forgemaster</b> (furnace, blast furnace): Flash Forge; Hot Coals, Smelter's Eye, Coal Miser, Long Forge;
 * Second Smelt or Slow Burn. Price: repairs restore 15% less.</li>
 * <li><b>Armorer</b> (anvil): Masterwork; Fine Repair, Thrifty Hands, Smith's Eye, Gentle Hammer; Preservation or Quick
 * Hammer. Price: smelting gives no bonus yield and −25% XP.</li>
 * <li><b>Tinkerer</b> (smithing table): Reforge; Upgrade Lore, Polish, Spare Material, Patient Craft; Template Saver or
 * Trim Power. Price: anvil repairs restore 20% less.</li>
 * </ul>
 * A furnace counts for the player who opened it last (as the trunk's Fire Master does).
 */
public final class Smithing {
	private Smithing() {}

	private static final String REFORGED = "extremeprogression_reforged";

	static void register() {
		// Flash Forge: 30 s (+5 s a rank, +5 s a rank of Long Forge)
		AbilityService.register(FORGEMASTER, (p, rank) -> Active.start(p, FORGEMASTER, rank, 600 + 100 * (rank - 1) + 100 * rank(p, FORGEMASTER, P4)));
		// Masterwork: the next repair within 30 seconds
		AbilityService.register(ARMORER, (p, rank) -> Active.start(p, ARMORER, rank, 600));
		// Reforge: the next item within 60 seconds (+15 s a rank of Patient Craft)
		AbilityService.register(TINKERER, (p, rank) -> Active.start(p, TINKERER, rank, 1200 + 300 * rank(p, TINKERER, P4)));
	}

	// --- Furnaces --------------------------------------------------------------------------------

	/** The extra progress a furnace makes each tick: Fire Master 5%, Hot Coals 4% a rank, Flash Forge 3 more (4× in all). */
	public static double furnaceSpeed(ServerPlayer user) {
		double extra = Trunks.has(user, Skill.SMITHING, 5) ? 0.05 : 0;
		extra += 0.04 * rank(user, FORGEMASTER, P1);
		if (Active.on(user, FORGEMASTER)) {
			extra += 3;
		}
		return extra;
	}

	/** The chance that fuel is not used up: Fuel Saver 10%, Coal Miser 4% a rank. */
	public static double fuelSaved(ServerPlayer user) {
		return (Trunks.has(user, Skill.SMITHING, 20) ? 0.10 : 0) + 0.04 * rank(user, FORGEMASTER, P3);
	}

	/** Slow Burn: fuel burns 10% longer a rank. */
	public static double slowBurn(ServerPlayer user) {
		return 0.10 * rank(user, FORGEMASTER, C2);
	}

	/** Second Smelt: a 4% chance a rank of a double output; never for an Armorer (their price). */
	public static double secondSmelt(ServerPlayer user) {
		return Specs.has(user, ARMORER) ? 0 : 0.04 * rank(user, FORGEMASTER, C1);
	}

	/** Smelting XP: Smelter's Eye +3% a rank; the Armorer's price −25%. */
	public static double smeltXp(ServerPlayer p) {
		double out = 1 + 0.03 * rank(p, FORGEMASTER, P2);
		return Specs.has(p, ARMORER) ? out * 0.75 : out;
	}

	// --- The anvil -------------------------------------------------------------------------------

	/** Thrifty Hands: a repair costs 4% fewer levels a rank (at least 1). */
	public static int repairCost(Player p, int cost) {
		int thrift = rank(p, ARMORER, P2);
		return cost > 1 && thrift > 0 ? Math.max(1, (int) Math.floor(cost * (1 - 0.04 * thrift))) : cost;
	}

	/** Preservation: the "Too Expensive" limit, 40 in vanilla, 2 levels higher a rank. */
	public static int tooExpensive(Player p) {
		return 40 + 2 * rank(p, ARMORER, C1);
	}

	/**
	 * A repair's result, after vanilla and the trunk: the Forgemaster's and Tinkerer's prices (−15%, −20% of what was
	 * restored), Fine Repair and Quick Hammer (+3% and +5% of the item's durability a rank), and Masterwork (fully).
	 */
	public static void repaired(Player p, ItemStack left, ItemStack result) {
		int restored = left.getDamageValue() - result.getDamageValue();
		if (restored <= 0 || !result.isDamageableItem()) {
			return;
		}
		int max = result.getMaxDamage();
		double less = (Specs.has(p, FORGEMASTER) ? 0.15 : 0) + (Specs.has(p, TINKERER) ? 0.20 : 0);
		double more = max * (0.03 * rank(p, ARMORER, P1) + 0.05 * rank(p, ARMORER, C2));
		int damage = result.getDamageValue() + (int) Math.round(restored * less) - (int) Math.round(more);
		if (Active.on(p, ARMORER)) {
			damage = 0;
		}
		result.setDamageValue(Math.max(0, Math.min(max - 1, damage)));
	}

	/** Smith's Eye: repairing +3% XP a rank. */
	public static double repairXp(ServerPlayer p) {
		return 1 + 0.03 * rank(p, ARMORER, P3);
	}

	/** Masterwork, when its repair is taken: it ends, and a 25% chance (+5% a rank) gives the levels back. */
	public static void masterworkTaken(ServerPlayer p, int cost) {
		Active.State work = Active.get(p, ARMORER);
		if (work == null) {
			return;
		}
		Active.end(p, ARMORER);
		if (cost > 0 && Specs.roll(p, 0.25 + 0.05 * (work.rank - 1))) {
			p.giveExperienceLevels(cost);
		}
	}

	/** Gentle Hammer: a 5% chance a rank that the anvil keeps its state. */
	public static boolean gentleHammer(ServerPlayer p) {
		return Specs.roll(p, 0.05 * rank(p, ARMORER, P4));
	}

	/** Spare Material: a 4% chance a rank that a repair keeps its material. */
	public static boolean spareMaterial(ServerPlayer p) {
		return Specs.roll(p, 0.04 * rank(p, TINKERER, P3));
	}

	// --- The smithing table and Reforge ----------------------------------------------------------

	/** Upgrade Lore: upgrades and trims +3% XP a rank. */
	public static double smithXp(ServerPlayer p) {
		return 1 + 0.03 * rank(p, TINKERER, P1);
	}

	/** Polish: an upgraded item is repaired by 4% of its durability a rank. */
	public static void polished(ServerPlayer p, ItemStack result) {
		int polish = rank(p, TINKERER, P2);
		if (polish > 0 && result.isDamageableItem() && result.getDamageValue() > 0) {
			result.setDamageValue(Math.max(0, result.getDamageValue() - (int) Math.round(result.getMaxDamage() * 0.04 * polish)));
		}
	}

	/** Template Saver: a 4% chance a rank to keep the template; it needs Mining 30 (design §2, hard requirement). */
	public static boolean templateSaved(ServerPlayer p) {
		return PlayerStore.get(p.getUUID()).get(Skill.MINING).level >= 30 && Specs.roll(p, 0.04 * rank(p, TINKERER, C1));
	}

	public static boolean reforged(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(REFORGED);
	}

	/** Reforge, when an upgraded or repaired item is taken: +10% maximum durability (+2% a rank) for good, once per item. */
	public static void reforge(ServerPlayer p, ItemStack item) {
		Active.State reforge = Active.get(p, TINKERER);
		if (reforge == null || !item.isDamageableItem() || reforged(item)) {
			return;
		}
		Active.end(p, TINKERER);
		int max = item.getMaxDamage();
		item.set(DataComponents.MAX_DAMAGE, (int) Math.round(max * (1.10 + 0.02 * (reforge.rank - 1))));
		CompoundTag tag = item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.putBoolean(REFORGED, true);
		item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	// --- Trim Power ------------------------------------------------------------------------------

	/** What one trimmed piece gives at rank V, by trim material; a fifth of it a rank. */
	private record TrimBonus(Holder<Attribute> attribute, double atV, AttributeModifier.Operation op) {}

	private static final Map<String, TrimBonus> TRIMS = new HashMap<>();

	static {
		AttributeModifier.Operation add = AttributeModifier.Operation.ADD_VALUE;
		AttributeModifier.Operation base = AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
		TRIMS.put("iron", new TrimBonus(Attributes.ARMOR, 0.5, add));
		TRIMS.put("diamond", new TrimBonus(Attributes.ARMOR_TOUGHNESS, 0.5, add));
		TRIMS.put("netherite", new TrimBonus(Attributes.KNOCKBACK_RESISTANCE, 0.05, add));
		TRIMS.put("copper", new TrimBonus(Attributes.MOVEMENT_SPEED, 0.02, base));
		TRIMS.put("redstone", new TrimBonus(Attributes.ATTACK_SPEED, 0.02, base));
		TRIMS.put("quartz", new TrimBonus(Attributes.ATTACK_DAMAGE, 0.25, add));
		TRIMS.put("amethyst", new TrimBonus(Attributes.MAX_HEALTH, 0.5, add));
		TRIMS.put("lapis", new TrimBonus(Attributes.BLOCK_BREAK_SPEED, 0.02, base));
		TRIMS.put("emerald", new TrimBonus(Attributes.LUCK, 0.5, add));
		TRIMS.put("gold", new TrimBonus(Attributes.MOVEMENT_EFFICIENCY, 0.1, add));
		TRIMS.put("resin", new TrimBonus(Attributes.OXYGEN_BONUS, 0.2, add));
	}

	private static Identifier trimId(String material) {
		return ExtremeProgression.id("spec/trim_" + material);
	}

	/** Every tick: Trim Power's bonuses, from the trimmed armor worn. */
	static void tick(ServerPlayer p) {
		int power = rank(p, TINKERER, C2);
		Map<String, Integer> pieces = new HashMap<>();
		if (power > 0) {
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				ArmorTrim trim = p.getItemBySlot(slot).get(DataComponents.TRIM);
				if (trim != null) {
					trim.material().unwrapKey().ifPresent(key -> pieces.merge(key.identifier().getPath(), 1, Integer::sum));
				}
			}
		}
		for (Map.Entry<String, TrimBonus> e : TRIMS.entrySet()) {
			TrimBonus b = e.getValue();
			int n = pieces.getOrDefault(e.getKey(), 0);
			Trunks.modifier(p, b.attribute, trimId(e.getKey()), n * b.atV * power / 5.0, b.op);
		}
	}

	/** The bonus Trim Power gives now for one material (for tests). */
	public static double trimBonus(String material, int pieces, int rank) {
		TrimBonus b = TRIMS.get(material);
		return b == null ? 0 : pieces * b.atV * rank / 5.0;
	}
}
