package com.extremeprogression.spec;

import static com.extremeprogression.skill.Specialization.ALCHEMIST;
import static com.extremeprogression.skill.Specialization.ENCHANTER;
import static com.extremeprogression.skill.Specialization.HEXER;
import static com.extremeprogression.spec.Specs.rank;
import static com.extremeprogression.tree.NodeSlot.C1;
import static com.extremeprogression.tree.NodeSlot.C2;
import static com.extremeprogression.tree.NodeSlot.P1;
import static com.extremeprogression.tree.NodeSlot.P2;
import static com.extremeprogression.tree.NodeSlot.P3;
import static com.extremeprogression.tree.NodeSlot.P4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.extremeprogression.ability.AbilityService;
import com.extremeprogression.network.InsightPayload;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.trunk.PotentPotions;
import com.extremeprogression.trunk.Trunks;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/**
 * Arcana's three specializations (design §2, skill 10).
 * <ul>
 * <li><b>Alchemist</b> (brewing stand): Catalyst; Quick Brew, Brewer's Eye, Distiller, Long Catalyst; Extended or
 * Potent. With Foraging 30 they also brew Haste, Resistance and Absorption. Price: enchanting gives −50% XP and no
 * bonuses.</li>
 * <li><b>Enchanter</b> (enchanting table): Arcane Insight; Lore, Lapis Thrift, Book Smart, Insightful; Frugal or Rich
 * Choice. Price: brewing gives −50% XP and no bonuses.</li>
 * <li><b>Hexer</b> (splash and lingering potions): Hex; Strong Arm, Concentrated, Careful Aim, Long Hex; Lingering
 * Venom or Wide Mist. Price: potions you drink yourself last 25% shorter.</li>
 * </ul>
 * A brewing stand counts for the player who opened it last. Thrown potions carry their brewer's duration factor
 * (Potent Brews, Extended) and their thrower's Hexer nodes, while they splash and while their clouds linger.
 */
public final class Arcana {
	private Arcana() {}

	/** The reagents of the Alchemist's three potions (data recipes in this mod), which only an Alchemist's stand brews. */
	private static final Set<net.minecraft.world.item.Item> HERBAL = Set.of(Items.COCOA_BEANS, Items.ARMADILLO_SCUTE, Items.HONEYCOMB);

	/** Thrown potions with Hex on them, and its rank. */
	private static final Map<Entity, Integer> HEXED = new WeakHashMap<>();
	/** Clouds a Hexer made, which carry the Hexer nodes while they tick. */
	private static final Set<Entity> HEX_CLOUDS = Collections.newSetFromMap(new WeakHashMap<>());

	/** The thrown potion or cloud applying its effects now, and its thrower. */
	private record Throw(ServerPlayer owner, double factor, boolean hexed, int hexRank, ItemStack item) {}

	private static Throw current = null;

	static void register() {
		// Catalyst: 30 s (+5 s a rank, +6 s a rank of Long Catalyst); Arcane Insight: 30 s (+5 s a rank, +6 s a rank of
		// Insightful), its value 1 once the free reroll is used; Hex: the next thrown potion within 15 s (+3 s a rank of Long Hex)
		AbilityService.register(ALCHEMIST, (p, rank) -> Active.start(p, ALCHEMIST, rank, 600 + 100 * (rank - 1) + (int) Math.round(120 * rank(p, ALCHEMIST, P4))));
		AbilityService.register(ENCHANTER, (p, rank) -> {
			Active.start(p, ENCHANTER, rank, 600 + 100 * (rank - 1) + (int) Math.round(120 * rank(p, ENCHANTER, P4)));
			if (p.containerMenu instanceof EnchantmentMenu menu) {
				insight(p, menu);
			}
		});
		AbilityService.register(HEXER, (p, rank) -> Active.start(p, HEXER, rank, 300 + (int) Math.round(60 * rank(p, HEXER, P4))));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractThrownPotion potion && entity.tickCount == 0 && potion.getOwner() instanceof ServerPlayer p) {
				thrown(p, potion);
			} else if (entity instanceof AreaEffectCloud cloud && current != null) {
				cloud(cloud);
			}
		});
	}

	// --- Brewing ---------------------------------------------------------------------------------

	/** The extra brewing progress a stand makes each tick: Busy Stand 5% (not for an Enchanter), Quick Brew 4% a rank, Catalyst 9 (10×). */
	public static double brewSpeed(ServerPlayer user) {
		double extra = Trunks.has(user, Skill.ARCANA, 5) && !Specs.has(user, ENCHANTER) ? 0.05 * Specs.power(user, Skill.ARCANA) : 0;
		extra += 0.04 * rank(user, ALCHEMIST, P1);
		if (Active.on(user, ALCHEMIST)) {
			extra += 9;
		}
		return extra;
	}

	/** Whether this stand's user may brew the Alchemist's potions: an Alchemist with Foraging 30 (design §2, hard requirement). */
	public static boolean herbalist(BrewingStandBlockEntity stand) {
		ServerPlayer p = Trunks.userOf(stand);
		return p != null && Specs.has(p, ALCHEMIST) && com.extremeprogression.skill.HardRequirement.NEW_POTIONS.met(p);
	}

	/** A stand may brew now: not one of the Alchemist's potions unless its user may. */
	public static boolean mayBrew(BrewingStandBlockEntity stand) {
		return !HERBAL.contains(stand.getItem(3).getItem()) || herbalist(stand);
	}

	/** Brewing XP: Brewer's Eye +3% a rank; the Enchanter's price −50%. */
	public static double brewXp(ServerPlayer p) {
		double out = 1 + 0.03 * rank(p, ALCHEMIST, P2);
		return Specs.has(p, ENCHANTER) ? out * 0.5 : out;
	}

	/** Distiller: a 4% chance a rank that a brew keeps its ingredient. */
	public static boolean distilled(BrewingStandBlockEntity stand) {
		ServerPlayer p = Trunks.userOf(stand);
		return p != null && !Specs.has(p, ENCHANTER) && Specs.roll(p, 0.04 * rank(p, ALCHEMIST, P3));
	}

	/** A brew finished: Potent Brews and Extended mark the potions' durations; Potent may make each one level stronger. */
	public static void brewed(BrewingStandBlockEntity stand) {
		ServerPlayer p = Trunks.userOf(stand);
		if (p == null || Specs.has(p, ENCHANTER)) {
			return;
		}
		// (Long Brew, a Mastery perk, on top)
		double factor = (Trunks.potentBrews(stand) ? 1 + 0.05 * Specs.power(p, Skill.ARCANA) : 1) * (1 + 0.05 * rank(p, ALCHEMIST, C1))
				* (1 + com.extremeprogression.mastery.Mastery.bonus(p, com.extremeprogression.mastery.MasteryPerk.LONG_BREW));
		double potent = rank(p, ALCHEMIST, C2);
		for (int slot = 0; slot < 3; slot++) {
			ItemStack potion = stand.getItem(slot);
			PotionContents contents = potion.get(DataComponents.POTION_CONTENTS);
			if (contents == null) {
				continue;
			}
			if (factor > 1) {
				PotentPotions.mark(potion, factor);
			}
			if (potent > 0 && Specs.roll(p, 0.06 * potent)) {
				PotionContents stronger = contents;
				for (MobEffectInstance e : contents.getAllEffects()) {
					if (!e.getEffect().value().isInstantaneous()) {
						stronger = stronger.withEffectAdded(new MobEffectInstance(e.getEffect(), e.getDuration(), e.getAmplifier() + 1, e.isAmbient(), e.isVisible(),
								e.showIcon()));
					}
				}
				potion.set(DataComponents.POTION_CONTENTS, stronger);
			}
		}
	}

	// --- Enchanting ------------------------------------------------------------------------------

	/** Enchanting XP: Lore +3% a rank; the Alchemist's price −50%. */
	public static double enchantXp(ServerPlayer p) {
		double out = 1 + 0.03 * rank(p, ENCHANTER, P1);
		return Specs.has(p, ALCHEMIST) ? out * 0.5 : out;
	}

	/** After an enchantment: Arcane Insight ends; Lapis Thrift and Frugal (not for an Alchemist, their price). */
	public static void enchanted(Player player, EnchantmentMenu menu) {
		if (!(player instanceof ServerPlayer p)) {
			return;
		}
		if (Active.on(p, ENCHANTER)) {
			Active.end(p, ENCHANTER);
			send(p, List.of());
		}
		if (Specs.has(p, ALCHEMIST)) {
			return;
		}
		if (Specs.roll(p, 0.04 * rank(p, ENCHANTER, P2))) {
			ItemStack lapis = menu.getSlot(1).getItem();
			if (lapis.isEmpty()) {
				menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI));
			} else if (lapis.is(Items.LAPIS_LAZULI) && lapis.getCount() < lapis.getMaxStackSize()) {
				lapis.grow(1);
				menu.getSlot(1).setChanged();
			}
		}
		if (Specs.roll(p, 0.20 * rank(p, ENCHANTER, C1))) {
			p.giveExperienceLevels(1);
		}
	}

	/** Rich Choice: an offer is rolled as if 1 level higher a rank (the price stays). */
	public static int richChoice(EnchantmentMenu menu, int cost) {
		ServerPlayer p = viewer(menu);
		if (p == null || cost <= 0) {
			return cost;
		}
		int out = cost + (int) Math.round(rank(p, ENCHANTER, C2));
		// Bright Offer (Mastery): a chance the offer rolls 1 level higher, the same chance every time this offer is worked out
		double bright = com.extremeprogression.mastery.Mastery.bonus(p, com.extremeprogression.mastery.MasteryPerk.BRIGHT_OFFER);
		if (bright > 0) {
			int seed = ((com.extremeprogression.mixin.EnchantmentMenuAccess) menu).extremeprogression$seed().get();
			if (new java.util.Random(seed * 31L + cost).nextDouble() < bright) {
				out++;
			}
		}
		return out;
	}

	/** Book Smart: combining enchanted books at an anvil costs 4% fewer levels a rank (at least 1). */
	public static int bookCost(Player p, int cost) {
		double smart = rank(p, ENCHANTER, P3);
		return cost > 1 && smart > 0 ? Math.max(1, (int) Math.floor(cost * (1 - 0.04 * smart))) : cost;
	}

	/** The server player whose open menu this is. */
	private static ServerPlayer viewer(EnchantmentMenu menu) {
		if (server == null) {
			return null;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.containerMenu == menu) {
				return p;
			}
		}
		return null;
	}

	private static net.minecraft.server.MinecraftServer server;

	/** The running server, so an enchanting menu can find its player. */
	public static void server(net.minecraft.server.MinecraftServer s) {
		server = s;
	}

	/** The offers changed: during Arcane Insight its player sees every enchantment of each. */
	public static void offersChanged(EnchantmentMenu menu, java.util.function.IntFunction<List<EnchantmentInstance>> offer) {
		ServerPlayer p = viewer(menu);
		if (p != null && Active.on(p, ENCHANTER)) {
			List<List<InsightPayload.Line>> offers = new ArrayList<>();
			for (int slot = 0; slot < 3; slot++) {
				List<InsightPayload.Line> lines = new ArrayList<>();
				if (menu.costs[slot] > 0) {
					for (EnchantmentInstance e : offer.apply(slot)) {
						e.enchantment().unwrapKey().ifPresent(key -> lines.add(new InsightPayload.Line(key.identifier().toString(), e.level())));
					}
				}
				offers.add(lines);
			}
			send(p, offers);
		}
	}

	/** Arcane Insight used at an open enchanting table: its offers show at once. */
	private static void insight(ServerPlayer p, EnchantmentMenu menu) {
		menu.slotsChanged(menu.getSlot(0).container);
	}

	/** Arcane Insight's free reroll (the menu's button 3): new offers, once, without levels. True when it happened. */
	public static boolean reroll(Player player) {
		if (!(player instanceof ServerPlayer p)) {
			return false;
		}
		Active.State insight = Active.get(p, ENCHANTER);
		if (insight == null || insight.value > 0) {
			return false;
		}
		insight.value = 1;
		p.onEnchantmentPerformed(ItemStack.EMPTY, 0);
		return true;
	}

	private static void send(ServerPlayer p, List<List<InsightPayload.Line>> offers) {
		if (ServerPlayNetworking.canSend(p, InsightPayload.TYPE)) {
			Active.State insight = Active.get(p, ENCHANTER);
			ServerPlayNetworking.send(p, new InsightPayload(offers, insight != null && insight.value == 0));
		}
	}

	// --- Thrown potions --------------------------------------------------------------------------

	/** A potion thrown by a player: Strong Arm (5% farther a rank), and Hex marks it. */
	private static void thrown(ServerPlayer p, AbstractThrownPotion potion) {
		double arm = rank(p, HEXER, P1);
		if (arm > 0) {
			potion.setDeltaMovement(potion.getDeltaMovement().scale(1 + 0.05 * arm));
		}
		Active.State hex = Active.get(p, HEXER);
		if (hex != null) {
			HEXED.put(potion, hex.rank);
			Active.end(p, HEXER);
		}
	}

	/** The player whose thrown potion (or Hexed cloud) gives its effects right now, or null. */
	public static ServerPlayer thrower() {
		Throw t = current;
		return t == null ? null : t.owner;
	}

	/** A thrown potion hits (before vanilla's splash or cloud). */
	public static void hitting(AbstractThrownPotion potion) {
		ServerPlayer owner = potion.getOwner() instanceof ServerPlayer p ? p : null;
		Integer hex = HEXED.remove(potion);
		ItemStack item = potion.getItem();
		current = new Throw(owner, PotentPotions.factor(item), hex != null, hex == null ? 0 : hex, item.copy());
	}

	/** After the hit: a Hexed splash potion leaves a cloud; Wide Mist reaches the ones a little farther out. */
	public static void hit(AbstractThrownPotion potion) {
		Throw t = current;
		try {
			if (t == null || !(potion.level() instanceof ServerLevel level)) {
				return;
			}
			PotionContents contents = t.item.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
			if (potion instanceof ThrownSplashPotion && t.hexed && contents.hasEffects()) {
				AreaEffectCloud cloud = new AreaEffectCloud(level, potion.getX(), potion.getY(), potion.getZ());
				if (t.owner != null) {
					cloud.setOwner(t.owner);
				}
				// (as a lingering potion's cloud; the Hex doubling follows when it is added)
				cloud.setRadius(3.0f);
				cloud.setRadiusOnUse(-0.5f);
				cloud.setDuration(600);
				cloud.setWaitTime(10);
				cloud.setRadiusPerTick(-cloud.getRadius() / cloud.getDuration());
				cloud.setPotionDurationScale(0.25f);
				cloud.setPotionContents(contents);
				level.addFreshEntity(cloud);
			}
			double mist = t.owner == null ? 0 : rank(t.owner, HEXER, C2);
			if (potion instanceof ThrownSplashPotion && mist > 0 && contents.hasEffects()) {
				double reach = AbstractThrownPotion.SPLASH_RANGE * (1 + 0.10 * mist);
				for (Entity e : level.getEntities(potion, potion.getBoundingBox().inflate(reach, 2.0, reach), e -> e instanceof LivingEntity)) {
					double d = e.distanceTo(potion);
					if (d > AbstractThrownPotion.SPLASH_RANGE && d <= reach) {
						double falloff = 1 - d / reach;
						for (MobEffectInstance effect : contents.getAllEffects()) {
							if (!effect.getEffect().value().isInstantaneous()) {
								int duration = (int) Math.round(effect.getDuration() * falloff);
								if (duration > 20) {
									((LivingEntity) e).addEffect(new MobEffectInstance(effect.getEffect(), duration, effect.getAmplifier(), effect.isAmbient(),
											effect.isVisible()), t.owner);
								}
							}
						}
					}
				}
			}
		} finally {
			current = null;
		}
	}

	/** A cloud made by a thrown potion: Hex doubles its size and time (+10% size a rank); Wide Mist +10% size a rank. */
	private static void cloud(AreaEffectCloud cloud) {
		Throw t = current;
		float radius = cloud.getRadius();
		if (t.hexed) {
			radius *= 2 * (1 + 0.10f * (t.hexRank - 1));
			cloud.setDuration(cloud.getDuration() * 2);
		}
		if (t.owner != null) {
			radius *= 1 + 0.10f * rank(t.owner, HEXER, C2);
			HEX_CLOUDS.add(cloud);
		}
		cloud.setRadius(radius);
		cloud.setRadiusPerTick(-radius / Math.max(1, cloud.getDuration()));
		if (t.factor != 1) {
			cloud.setPotionDurationScale((float) (0.25 * t.factor));
		}
	}

	/** A cloud's tick: while it applies its effects, its thrower's Hexer nodes count. */
	public static void cloudTicking(AreaEffectCloud cloud, boolean start) {
		if (!start) {
			current = null;
		} else if (HEX_CLOUDS.contains(cloud) && cloud.getOwner() instanceof ServerPlayer p) {
			current = new Throw(p, 1, false, 0, ItemStack.EMPTY);
		}
	}

	/**
	 * An effect from a thrown potion or its cloud: Potent Brews and Extended (splash), Concentrated (+5% a rank), Lingering
	 * Venom (+6% a rank on negative effects), and Careful Aim (20% a rank that the thrower's own negative ones miss them).
	 */
	static MobEffectInstance effect(LivingEntity target, MobEffectInstance effect) {
		Throw t = current;
		if (t == null || effect.getDuration() < 0) {
			return effect;
		}
		boolean harmful = effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL;
		double factor = t.item.isEmpty() ? 1 : t.factor;
		if (t.owner != null) {
			if (harmful && target == t.owner && Specs.roll(t.owner, 0.20 * rank(t.owner, HEXER, P3))) {
				return null;
			}
			factor *= 1 + 0.05 * rank(t.owner, HEXER, P2);
			if (harmful) {
				factor *= 1 + 0.06 * rank(t.owner, HEXER, C1);
			}
		}
		if (factor == 1 || effect.getEffect().value().isInstantaneous()) {
			return effect;
		}
		return new MobEffectInstance(effect.getEffect(), Math.max(1, (int) Math.round(effect.getDuration() * factor)), effect.getAmplifier(), effect.isAmbient(),
				effect.isVisible(), effect.showIcon());
	}

	/** The Hexer's price: potions they drink last 25% shorter. */
	public static double drinkFactor(LivingEntity drinker) {
		return Specs.has(drinker, HEXER) ? 0.75 : 1;
	}
}
