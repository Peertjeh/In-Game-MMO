package com.extremeprogression.trunk;

import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import com.mojang.serialization.Codec;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.mixin.FoodDataAccess;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.spec.Specs;
import com.extremeprogression.xp.CombatXp;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The trunks: the passive bonuses every skill unlocks by itself at levels 5, 10, 15 and 20 (design §2). Each is
 * applied where the game decides it: an attribute, a hit, a draw, a durability loss, a furnace tick. Exploration's do
 * not work in heavy armor (3 or more pieces of iron or heavier).
 */
public final class Trunks {
	private Trunks() {}

	/** The player who last opened a furnace or brewing stand: its trunk bonuses are the ones that count there. */
	private static AttachmentType<String> USER;

	public static void register() {
		USER = AttachmentRegistry.createPersistent(ExtremeProgression.id("user"), Codec.STRING);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide()) {
				BlockEntity be = level.getBlockEntity(hit.getBlockPos());
				if (be instanceof AbstractFurnaceBlockEntity || be instanceof BrewingStandBlockEntity) {
					((AttachmentTarget) be).setAttached(USER, player.getUUID().toString());
					be.setChanged();
				}
			}
			return InteractionResult.PASS;
		});
		// Forest Foot (Foraging 15): berry bushes no longer prick
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(source.is(DamageTypes.SWEET_BERRY_BUSH) && has(entity, Skill.FORAGING, 15) && full((ServerPlayer) entity, Skill.FORAGING)));
		// Wind Reader (Ranged 20): arrows fly 5% faster
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractArrow arrow && !(arrow instanceof ThrownTrident) && arrow.tickCount == 0 && has(arrow.getOwner(), Skill.RANGED, 20)) {
				arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1 + 0.05 * power(arrow.getOwner(), Skill.RANGED)));
			}
		});
		// Arrow Recovery (Ranged 10): a 10% chance to recover your arrow from a slain mob
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getDirectEntity() instanceof AbstractArrow arrow && !(arrow instanceof ThrownTrident) && !(entity instanceof Player)
					&& arrow.pickup == AbstractArrow.Pickup.ALLOWED && has(arrow.getOwner(), Skill.RANGED, 10) && entity.getRandom().nextDouble() < 0.10 * power(arrow.getOwner(), Skill.RANGED)) {
				ItemStack back = arrow.getPickupItemStackOrigin().copy();
				back.setCount(1);
				Block.popResource(entity.level(), entity.blockPosition(), back);
			}
		});
	}

	// --- Who has what ----------------------------------------------------------------------------

	/** Whether this entity is a player on the server who has reached the skill's trunk level (and, for Exploration, travels light). */
	public static boolean has(Entity entity, Skill skill, int level) {
		if (!(entity instanceof ServerPlayer p)) {
			return false;
		}
		if (PlayerStore.get(p.getUUID()).get(skill).level < level) {
			return false;
		}
		return skill != Skill.EXPLORATION || !heavyArmor(p);
	}

	/** How strong a skill's trunk bonuses are now: 1, or less during death fatigue (design §7). */
	public static double power(Entity entity, Skill skill) {
		return Specs.power(entity, skill);
	}

	/** An all-or-nothing bonus: always at full power, a chance equal to the power during death fatigue. */
	private static boolean full(ServerPlayer p, Skill skill) {
		double power = power(p, skill);
		return power >= 1 || p.getRandom().nextDouble() < power;
	}

	private static final Set<String> HEAVY = Set.of("iron_", "diamond_", "netherite_");

	/** 3 or more pieces of iron armor or heavier. */
	public static boolean heavyArmor(LivingEntity e) {
		int heavy = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			String id = BuiltInRegistries.ITEM.getKey(e.getItemBySlot(slot).getItem()).getPath();
			if (HEAVY.stream().anyMatch(id::startsWith)) {
				heavy++;
			}
		}
		return heavy >= 3;
	}

	private static ServerPlayer user(BlockEntity be) {
		String id = ((AttachmentTarget) be).getAttached(USER);
		if (id == null || be.getLevel() == null || be.getLevel().getServer() == null) {
			return null;
		}
		try {
			return be.getLevel().getServer().getPlayerList().getPlayer(UUID.fromString(id));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// --- Attributes (synced to the client, so they are felt there too) --------------------------

	private static final Identifier COMBAT_RHYTHM = ExtremeProgression.id("trunk/combat_rhythm");
	private static final Identifier BREAK_SPEED = ExtremeProgression.id("trunk/break_speed");
	private static final Identifier STEADFAST = ExtremeProgression.id("trunk/steadfast");
	private static final Identifier SWIMMER = ExtremeProgression.id("trunk/swimmer");

	/** Every tick, for every player: the bonuses that are attributes. */
	public static void tick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			// Combat Rhythm (Melee 5): the attack bar charges 3% faster
			modifier(p, Attributes.ATTACK_SPEED, COMBAT_RHYTHM, has(p, Skill.MELEE, 5) ? 0.03 * power(p, Skill.MELEE) : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			// Miner's Rhythm (Mining 5) and Woodcutter's Rhythm (Foraging 5): +3% with the skill's tools
			ItemStack held = p.getMainHandItem();
			double speed = 0;
			if ((held.is(ItemTags.PICKAXES) || held.is(ItemTags.SHOVELS)) && has(p, Skill.MINING, 5)) {
				speed += 0.03 * power(p, Skill.MINING);
			}
			if (held.is(ItemTags.AXES) && has(p, Skill.FORAGING, 5)) {
				speed += 0.03 * power(p, Skill.FORAGING);
			}
			modifier(p, Attributes.BLOCK_BREAK_SPEED, BREAK_SPEED, speed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			// Steadfast (Defense 20): 5% knockback resistance
			modifier(p, Attributes.KNOCKBACK_RESISTANCE, STEADFAST, has(p, Skill.DEFENSE, 20) ? 0.05 * power(p, Skill.DEFENSE) : 0, AttributeModifier.Operation.ADD_VALUE);
			// Swimmer (Exploration 15): +5% swimming speed
			modifier(p, Attributes.WATER_MOVEMENT_EFFICIENCY, SWIMMER, has(p, Skill.EXPLORATION, 15) ? 0.05 * power(p, Skill.EXPLORATION) : 0, AttributeModifier.Operation.ADD_VALUE);
		}
	}

	/** Sets a transient attribute modifier to this amount, or removes it at 0 (only touching it when it changes). */
	public static void modifier(LivingEntity p, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance instance = p.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier current = instance.getModifier(id);
		if (amount == 0) {
			if (current != null) {
				instance.removeModifier(id);
			}
		} else if (current == null || current.amount() != amount) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	// --- Hits ------------------------------------------------------------------------------------

	/** The damage a hit deals, before armor: the trunks of the attacker and of the target. */
	public static float damage(LivingEntity target, DamageSource source, float amount) {
		if (amount <= 0 || target.level().isClientSide()) {
			return amount;
		}
		float out = amount;
		Entity attacker = source.getEntity();
		Entity direct = source.getDirectEntity();
		if (attacker instanceof ServerPlayer p) {
			if (direct == p && CombatXp.fullCharge(p)) {
				// Full Swing (Melee 10): +5%; Finishing Blow (Melee 20): critical hits +10%
				if (has(p, Skill.MELEE, 10)) {
					out *= 1 + 0.05 * power(p, Skill.MELEE);
				}
				if (has(p, Skill.MELEE, 20) && critical(p)) {
					out *= 1 + 0.10 * power(p, Skill.MELEE);
				}
			} else if (direct != p && CombatXp.fullShot(direct) && has(p, Skill.RANGED, 15)) {
				// Keen Eye (Ranged 15): fully drawn shots +5%
				out *= 1 + 0.05 * power(p, Skill.RANGED);
			}
		}
		if (target instanceof ServerPlayer v) {
			// Hardened (Defense 10): 3% less damage from hostile mobs
			if (attacker instanceof Enemy && has(v, Skill.DEFENSE, 10)) {
				out *= 1 - 0.03 * power(v, Skill.DEFENSE);
			}
			// Cave Lore (Mining 15): 10% less from dripstone, magma blocks and falling blocks
			if ((source.is(DamageTypes.HOT_FLOOR) || source.is(DamageTypes.STALAGMITE) || source.is(DamageTypes.FALLING_STALACTITE)
					|| source.is(DamageTypes.FALLING_BLOCK)) && has(v, Skill.MINING, 15)) {
				out *= 1 - 0.10 * power(v, Skill.MINING);
			}
			if (source.is(DamageTypes.FALL)) {
				// Light-Footed (Exploration 5): 5% less; Roll (Exploration 20): 10% chance of half (sneaking 20%)
				if (has(v, Skill.EXPLORATION, 5)) {
					out *= 1 - 0.05 * power(v, Skill.EXPLORATION);
				}
				if (has(v, Skill.EXPLORATION, 20) && v.getRandom().nextDouble()
						< (0.10 * power(v, Skill.EXPLORATION) + com.extremeprogression.spec.Exploration.tumble(v)) * (v.isShiftKeyDown() ? 2 : 1)) {
					out *= 0.5f;
				}
			}
		}
		return out;
	}

	/** Vanilla's critical hit: falling, not on the ground, a ladder or in water, not riding, not sprinting. */
	public static boolean critical(ServerPlayer p) {
		return p.fallDistance > 0 && !p.onGround() && !p.onClimbable() && !p.isInWater() && !p.isPassenger() && !p.isSprinting();
	}

	/** Sure Footing (Melee 15): 10% less knockback while attacking (within 2 seconds of your own attack). */
	public static double knockback(LivingEntity target, double strength) {
		if (target instanceof ServerPlayer p && has(p, Skill.MELEE, 15) && CombatXp.attackedWithin(p, 40)) {
			return strength * (1 - 0.10 * power(p, Skill.MELEE));
		}
		return strength;
	}

	/** Shield Reflex (Defense 5): the shield is ready one tick sooner than vanilla's delay (not during death fatigue: a tick does not halve). */
	public static boolean shieldReady(LivingEntity entity, int ticksUsing, int delay) {
		return delay > 0 && ticksUsing >= delay - 1 && has(entity, Skill.DEFENSE, 5) && power(entity, Skill.DEFENSE) >= 1;
	}

	/** Catch Breath (Defense 15): after a hit in a fight, natural regeneration comes 2 seconds sooner. */
	public static void inAFight(ServerPlayer p) {
		if (has(p, Skill.DEFENSE, 15)) {
			FoodDataAccess food = (FoodDataAccess) p.getFoodData();
			food.extremeprogression$setTickTimer(Math.min(79, food.extremeprogression$getTickTimer() + (int) Math.round(40 * power(p, Skill.DEFENSE))));
		}
	}

	// --- Tools -----------------------------------------------------------------------------------

	/** Sturdy Tools (Mining 10) and Steady Line (Fishing 10): a chance that a use costs no durability. */
	public static int durability(ItemStack stack, ServerPlayer player, int amount) {
		if (player == null || amount <= 0) {
			return amount;
		}
		if ((stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS)) && has(player, Skill.MINING, 10) && player.getRandom().nextDouble() < 0.05 * power(player, Skill.MINING)) {
			return 0;
		}
		if (stack.is(Items.FISHING_ROD) && has(player, Skill.FISHING, 10) && player.getRandom().nextDouble() < 0.10 * power(player, Skill.FISHING)) {
			return 0;
		}
		return amount;
	}

	/** Steady Hand (Ranged 5): a bow is drawn 5% faster (the draw counts 5% more ticks). */
	public static int bowTimeLeft(ItemStack bow, LivingEntity user, int timeLeft) {
		if (!has(user, Skill.RANGED, 5)) {
			return timeLeft;
		}
		int drawn = bow.getUseDuration(user) - timeLeft;
		return timeLeft - (int) Math.round(drawn * (0.05 * power(user, Skill.RANGED) + com.extremeprogression.spec.Ranged.quickDraw(user)));
	}

	/** Patience (Fishing 5): fish bite 5% sooner (vanilla's wait averages 17.5 seconds; this takes 0.875 off). */
	public static float fishingTime(Entity angler, float seconds) {
		return has(angler, Skill.FISHING, 5) ? seconds + (float) (0.875 * power(angler, Skill.FISHING)) : seconds;
	}

	/** Sea Luck (Fishing 20): +5% treasure chance (2 points of luck: treasure from 5% to about 9%). */
	public static int fishingLuck(Entity angler, int luck) {
		return has(angler, Skill.FISHING, 20) ? luck + (int) Math.round(2 * power(angler, Skill.FISHING)) : luck;
	}

	// --- Yields ----------------------------------------------------------------------------------

	/** What the extra drops of the trunks are for. */
	public enum Yield { ORE, LOG, CROP, LEAVES }

	/**
	 * Double Find (Mining 20), Double Timber (Foraging 20), Rich Harvest (Farming 10), Leaf Shaker (Foraging 10): a
	 * chance of the block's drops once more. Only for blocks that also gave XP (never placed ones).
	 */
	public static void extraDrops(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state, BlockEntity be, Yield yield) {
		// (with the specializations' passives on top, and their prices: Tunneler no double ores, Herder no extra crops)
		boolean twice = switch (yield) {
			case ORE -> !state.is(net.minecraft.world.level.block.Blocks.ANCIENT_DEBRIS) && !com.extremeprogression.spec.Mining.noDoubles(p)
					&& p.getRandom().nextDouble() < (has(p, Skill.MINING, 20) ? 0.05 * power(p, Skill.MINING) : 0) + com.extremeprogression.spec.Mining.doubleOreChance(p);
			case LOG -> p.getRandom().nextDouble() < (has(p, Skill.FORAGING, 20) ? 0.05 * power(p, Skill.FORAGING) : 0) + com.extremeprogression.spec.Foraging.doubleLogChance(p);
			case CROP -> !com.extremeprogression.spec.Farming.noExtraCrops(p)
					&& p.getRandom().nextDouble() < (has(p, Skill.FARMING, 10) ? 0.05 * power(p, Skill.FARMING) : 0) + com.extremeprogression.spec.Farming.extraCropChance(p);
			case LEAVES -> has(p, Skill.FORAGING, 10);
		};
		if (!twice) {
			return;
		}
		boolean first = true;
		for (ItemStack drop : Block.getDrops(state, level, pos, be, p, p.getMainHandItem())) {
			if (yield == Yield.LEAVES) {
				// 10% more saplings, apples and sticks
				String id = BuiltInRegistries.ITEM.getKey(drop.getItem()).getPath();
				boolean leafy = id.endsWith("_sapling") || id.equals("mangrove_propagule") || drop.is(Items.APPLE) || drop.is(Items.STICK);
				if (leafy && p.getRandom().nextDouble() < 0.10 * power(p, Skill.FORAGING)) {
					Block.popResource(level, pos, drop.copy());
				}
			} else if (yield != Yield.CROP || first) {
				// (a crop gives one extra of what it yields first: the wheat, not the seeds)
				ItemStack extra = drop.copy();
				if (yield == Yield.CROP) {
					extra.setCount(1);
				}
				Block.popResource(level, pos, extra);
				first = false;
			}
		}
	}

	/** Growing Up (Farming 15): a baby you bred grows up 10% faster. */
	public static void bred(ServerPlayer p, AgeableMob child) {
		if (child != null && child.getAge() < 0 && has(p, Skill.FARMING, 15) && !com.extremeprogression.spec.Farming.noBreedingBonus(p)) {
			child.setAge((int) (child.getAge() * (1 - 0.10 * power(p, Skill.FARMING))));
		}
	}

	private static final Set<String> CROP_FOOD = Set.of("bread", "cookie", "pumpkin_pie", "baked_potato", "beetroot_soup", "rabbit_stew");
	private static final Set<String> FISH_FOOD = Set.of("cooked_cod", "cooked_salmon");

	/** Farmhouse Fare (Farming 20) and Fisher's Fare (Fishing 15): food made from crops, or cooked fish, fills +1 hunger. */
	public static void ate(LivingEntity eater, ItemStack food) {
		if (!(eater instanceof ServerPlayer p) || food.isEmpty()) {
			return;
		}
		String id = BuiltInRegistries.ITEM.getKey(food.getItem()).getPath();
		if (CROP_FOOD.contains(id) && has(p, Skill.FARMING, 20) && full(p, Skill.FARMING)
				|| FISH_FOOD.contains(id) && has(p, Skill.FISHING, 15) && full(p, Skill.FISHING)) {
			p.getFoodData().eat(1, 0f);
		}
		com.extremeprogression.spec.Exploration.ate(p, food);
		com.extremeprogression.spec.Foraging.ate(p, food);
		com.extremeprogression.spec.Fishing.ate(p, food);
	}

	/** Stamina (Exploration 10): sprinting costs 5% less hunger. */
	public static float exhaustion(Player player, float amount) {
		return player.isSprinting() && has(player, Skill.EXPLORATION, 10) ? (float) (amount * (1 - 0.05 * power(player, Skill.EXPLORATION))) : amount;
	}

	// --- Workstations ----------------------------------------------------------------------------

	private static final WeakHashMap<BlockEntity, Double> SPEED_UP = new WeakHashMap<>();

	/** A speed-up of this many extra ticks a tick (0.05 for 5% faster, 3 for 4×): the whole ticks due now, the rest carried. */
	private static int extraTicks(BlockEntity be, double rate) {
		double carry = SPEED_UP.getOrDefault(be, 0.0) + rate;
		int whole = (int) carry;
		SPEED_UP.put(be, carry - whole);
		return whole;
	}

	/**
	 * After a furnace's tick, for the player who last opened it. Fire Master (Smithing 5): 5% faster, with the
	 * Forgemaster's Hot Coals and Flash Forge on top. Fuel Saver (Smithing 20): a 10% chance that the fuel it just took is
	 * not consumed (Coal Miser more). Slow Burn: that fuel burns longer. Second Smelt: a chance of one more output.
	 */
	public static void furnaceTick(AbstractFurnaceBlockEntity furnace, ItemStack fuelBefore, int litBefore, ItemStack outputBefore) {
		ServerPlayer p = user(furnace);
		if (p == null) {
			return;
		}
		com.extremeprogression.mixin.FurnaceAccess f = (com.extremeprogression.mixin.FurnaceAccess) furnace;
		int timer = f.extremeprogression$getCookingTimer();
		int total = f.extremeprogression$getCookingTotalTime();
		double speed = com.extremeprogression.spec.Smithing.furnaceSpeed(p);
		if (timer > 0 && timer < total - 1 && speed > 0) {
			int extra = extraTicks(furnace, speed);
			if (extra > 0) {
				f.extremeprogression$setCookingTimer(Math.min(total - 1, timer + extra));
			}
		}
		int lit = f.extremeprogression$getLitTimeRemaining();
		if (lit > litBefore && !fuelBefore.isEmpty() && !ItemStack.matches(fuelBefore, furnace.getItem(1))) {
			if (p.getRandom().nextDouble() < com.extremeprogression.spec.Smithing.fuelSaved(p)) {
				furnace.setItem(1, fuelBefore);
			}
			double slow = com.extremeprogression.spec.Smithing.slowBurn(p);
			if (slow > 0) {
				int longer = lit + (int) Math.round(lit * slow);
				f.extremeprogression$setLitTimeRemaining(longer);
				f.extremeprogression$setLitTotalTime(longer);
			}
		}
		ItemStack out = furnace.getItem(2);
		if (out.getCount() > outputBefore.getCount() && out.getCount() < out.getMaxStackSize() && com.extremeprogression.xp.XpTables.smelted(out) > 0
				&& p.getRandom().nextDouble() < com.extremeprogression.spec.Smithing.secondSmelt(p) + com.extremeprogression.mastery.Mastery.bonus(p, com.extremeprogression.mastery.MasteryPerk.RICH_SMELT)) {
			out.grow(1);
		}
	}

	/** After a brewing stand's tick. Busy Stand (Arcana 5): the stands you use brew 5% faster (the Alchemist's more). */
	public static void brewingTick(BrewingStandBlockEntity stand) {
		ServerPlayer p = user(stand);
		com.extremeprogression.mixin.BrewingStandAccess b = (com.extremeprogression.mixin.BrewingStandAccess) stand;
		int time = b.extremeprogression$getBrewTime();
		if (p != null && time > 1) {
			int extra = extraTicks(stand, com.extremeprogression.spec.Arcana.brewSpeed(p));
			if (extra > 0) {
				b.extremeprogression$setBrewTime(Math.max(1, time - extra));
			}
		}
	}

	/** The player who last opened this brewing stand or furnace, if online. */
	public static ServerPlayer userOf(BlockEntity be) {
		return user(be);
	}

	/** Whether the player who last used this brewing stand has Potent Brews (Arcana 15); never an Enchanter (their price). */
	public static boolean potentBrews(BrewingStandBlockEntity stand) {
		ServerPlayer p = user(stand);
		return p != null && has(p, Skill.ARCANA, 15) && !com.extremeprogression.spec.Specs.has(p, com.extremeprogression.skill.Specialization.ENCHANTER);
	}

	/** Thrifty Anvil (Smithing 10): 5% fewer levels. Tempered Steel (Smithing 15): a repair restores 5% more. */
	public static int anvilCost(Player player, int cost) {
		return cost > 1 && has(player, Skill.SMITHING, 10) ? Math.max(1, (int) Math.floor(cost * (1 - 0.05 * power(player, Skill.SMITHING)))) : cost;
	}

	public static void anvilRepair(Player player, ItemStack left, ItemStack result) {
		if (!result.isEmpty() && left.isDamageableItem() && result.getDamageValue() < left.getDamageValue() && has(player, Skill.SMITHING, 15)) {
			result.setDamageValue(Math.max(0, result.getDamageValue() - (int) Math.ceil(result.getMaxDamage() * 0.05 * power(player, Skill.SMITHING))));
		}
		// (the Smithing specializations' repairs come after the trunk's)
		if (!result.isEmpty() && left.isDamageableItem()) {
			com.extremeprogression.spec.Smithing.repaired(player, left, result);
		}
	}

	/** Lapis Saver (Arcana 10) and Thrifty Magic (Arcana 20): after an enchantment, a chance of a lapis or a level back. */
	public static void enchanted(Player player, net.minecraft.world.inventory.EnchantmentMenu menu) {
		com.extremeprogression.spec.Arcana.enchanted(player, menu);
		// The Alchemist's price: enchanting gets no bonuses
		if (com.extremeprogression.spec.Specs.has(player, com.extremeprogression.skill.Specialization.ALCHEMIST)) {
			return;
		}
		if (has(player, Skill.ARCANA, 10) && player.getRandom().nextDouble() < 0.10 * power(player, Skill.ARCANA)) {
			ItemStack lapis = menu.getSlot(1).getItem();
			if (lapis.isEmpty()) {
				menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI));
			} else if (lapis.is(Items.LAPIS_LAZULI) && lapis.getCount() < lapis.getMaxStackSize()) {
				lapis.grow(1);
				menu.getSlot(1).setChanged();
			}
		}
		if (has(player, Skill.ARCANA, 20) && player.getRandom().nextDouble() < 0.10 * power(player, Skill.ARCANA)) {
			player.giveExperienceLevels(1);
		}
	}
}
