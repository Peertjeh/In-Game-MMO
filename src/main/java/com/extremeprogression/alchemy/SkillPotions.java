package com.extremeprogression.alchemy;

import java.util.Map;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.network.Sync;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.alchemy.Potion;

/**
 * Skill potions (2.1, the owner's wish: potions that shorten the abilities' cooldowns and make them last longer).
 *
 * <p>Two effects, brewed like any potion (the recipes are data, in {@code data/extremeprogression/recipe/brewing}):
 * <ul>
 * <li><b>Quickening</b> (an Awkward Potion and a clock): while it lasts, every ability's cooldown runs
 * QUICKENING_PER_LEVEL faster a level (I: half again as fast, II: twice as fast).</li>
 * <li><b>Persistence</b> (an Awkward Potion and an amethyst shard): an ability set off while it lasts stays in effect
 * PERSISTENCE_PER_LEVEL longer a level (I: +30%, II: +60%).</li>
 * </ul>
 * Each comes as a potion of 3:00, a long one of 8:00 (redstone) and a strong one, level II, of 1:30 (glowstone); as
 * splash (gunpowder) and lingering potions (dragon's breath), and so as tipped arrows too.
 */
public final class SkillPotions {
	private SkillPotions() {}

	public static Holder<MobEffect> QUICKENING;
	public static Holder<MobEffect> PERSISTENCE;
	public static Holder<Potion> QUICKENING_POTION, LONG_QUICKENING, STRONG_QUICKENING;
	public static Holder<Potion> PERSISTENCE_POTION, LONG_PERSISTENCE, STRONG_PERSISTENCE;

	/** An effect whose work is done elsewhere: its holder is what the mod looks for. */
	static final class SkillEffect extends MobEffect {
		SkillEffect(int color) {
			super(MobEffectCategory.BENEFICIAL, color);
		}
	}

	public static void register() {
		QUICKENING = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ExtremeProgression.id("quickening"), (MobEffect) new SkillEffect(0xE8B931));
		PERSISTENCE = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ExtremeProgression.id("persistence"), (MobEffect) new SkillEffect(0x9A5CC6));
		QUICKENING_POTION = potion("quickening", "ep_quickening", QUICKENING, 3600, 0);
		LONG_QUICKENING = potion("long_quickening", "ep_quickening", QUICKENING, 9600, 0);
		STRONG_QUICKENING = potion("strong_quickening", "ep_quickening", QUICKENING, 1800, 1);
		PERSISTENCE_POTION = potion("persistence", "ep_persistence", PERSISTENCE, 3600, 0);
		LONG_PERSISTENCE = potion("long_persistence", "ep_persistence", PERSISTENCE, 9600, 0);
		STRONG_PERSISTENCE = potion("strong_persistence", "ep_persistence", PERSISTENCE, 1800, 1);
		ServerTickEvents.END_SERVER_TICK.register(SkillPotions::tick);
	}

	/** A potion: its id, its name (the lang key {@code item.minecraft.potion.effect.<name>}), its effect, time and level. */
	private static Holder<Potion> potion(String id, String name, Holder<MobEffect> effect, int ticks, int amplifier) {
		return Registry.registerForHolder(BuiltInRegistries.POTION, ExtremeProgression.id(id), new Potion(name, new MobEffectInstance(effect, ticks, amplifier)));
	}

	private static double level(LivingEntity e, Holder<MobEffect> effect) {
		MobEffectInstance i = effect == null || e == null ? null : e.getEffect(effect);
		return i == null ? 0 : i.getAmplifier() + 1;
	}

	/** How much faster the player's cooldowns run now (0.5: half again as fast), 0 without Quickening. */
	public static double quickening(LivingEntity e) {
		return Config.QUICKENING_PER_LEVEL * level(e, QUICKENING);
	}

	/** How much longer an ability set off now lasts (0.3: +30%), 0 without Persistence. */
	public static double persistence(LivingEntity e) {
		return Config.PERSISTENCE_PER_LEVEL * level(e, PERSISTENCE);
	}

	/** An ability's time in effect, made longer by Persistence. */
	public static int lasting(ServerPlayer p, int ticks) {
		double more = persistence(p);
		return more > 0 ? (int) Math.round(ticks * (1 + more)) : ticks;
	}

	/** Every second: Quickening takes its share more off every ability's cooldown. */
	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			double q = quickening(p);
			if (q > 0) {
				quicken(p, q * 20);
			}
		}
	}

	/** Takes this many ticks off every running cooldown of the player. Returns whether any ran. */
	public static boolean quicken(ServerPlayer p, double ticks) {
		long now = p.level().getServer().overworld().getGameTime();
		long cut = Math.round(ticks);
		if (cut <= 0) {
			return false;
		}
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		boolean changed = false;
		for (SkillData d : progress.skills.values()) {
			for (Map.Entry<String, Long> e : d.cooldowns.entrySet()) {
				Long ready = e.getValue();
				if (ready != null && ready > now) {
					e.setValue(Math.max(now, ready - cut));
					changed = true;
				}
			}
		}
		if (changed) {
			PlayerStore.markDirty(p.getUUID());
			Sync.send(p);
		}
		return changed;
	}
}
