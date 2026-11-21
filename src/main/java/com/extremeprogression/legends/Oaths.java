package com.extremeprogression.legends;

import java.util.EnumMap;
import java.util.Map;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.data.PlayerStore;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Swearing the Oaths (2.2) and what they give: the relics are taken from the inventory, the Oath kept for good in the
 * player's file, and its buff applied: an attribute modifier (put on again at every login and respawn), or a share the
 * mod reads where it counts (cooldowns, ability time, XP).
 */
public final class Oaths {
	private Oaths() {}

	/** Why an Oath cannot be sworn now, or OK. */
	public enum Result {
		OK, OFF, UNKNOWN, SWORN, FEAT, RELICS
	}

	static Identifier modifierId(Oath o) {
		return ExtremeProgression.id("oath_" + o.id);
	}

	public static boolean sworn(ServerPlayer p, Oath o) {
		return PlayerStore.get(p.getUUID()).legends.oaths.contains(o.id);
	}

	/** The relics of each kind the player carries. */
	public static Map<Relic, Integer> carried(Inventory inv) {
		Map<Relic, Integer> out = new EnumMap<>(Relic.class);
		for (int i = 0; i < inv.getContainerSize(); i++) {
			Relic r = Relic.of(inv.getItem(i));
			if (r != null) {
				out.merge(r, inv.getItem(i).getCount(), Integer::sum);
			}
		}
		return out;
	}

	/** Whether the player could swear the Oath now. */
	public static Result check(ServerPlayer p, Oath o) {
		if (!Oath.on()) {
			return Result.OFF;
		}
		if (o == null) {
			return Result.UNKNOWN;
		}
		if (sworn(p, o)) {
			return Result.SWORN;
		}
		if (!o.feat.test(p)) {
			return Result.FEAT;
		}
		Map<Relic, Integer> have = carried(p.getInventory());
		for (Map.Entry<Relic, Integer> e : o.relics.entrySet()) {
			if (have.getOrDefault(e.getKey(), 0) < e.getValue()) {
				return Result.RELICS;
			}
		}
		return Result.OK;
	}

	/** Swears the Oath: its relics are taken, its buff given for good. */
	public static Result swear(ServerPlayer p, Oath o) {
		Result r = check(p, o);
		if (r != Result.OK) {
			p.sendSystemMessage(Component.translatable("message.extremeprogression.oath." + r.name().toLowerCase(java.util.Locale.ROOT)).withColor(0xFF5555), true);
			return r;
		}
		Inventory inv = p.getInventory();
		for (Map.Entry<Relic, Integer> e : o.relics.entrySet()) {
			int left = e.getValue();
			for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
				ItemStack s = inv.getItem(i);
				if (Relic.of(s) == e.getKey()) {
					int take = Math.min(left, s.getCount());
					s.shrink(take);
					left -= take;
				}
			}
		}
		LegendsData d = PlayerStore.get(p.getUUID()).legends;
		d.oaths.add(o.id);
		PlayerStore.markDirty(p.getUUID());
		apply(p);
		p.sendSystemMessage(Component.translatable("message.extremeprogression.oath.done", Component.translatable(o.translationKey()).withColor(0xFFAA00),
				Component.translatable(o.translationKey() + ".reward")).withColor(0x55FF55), false);
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.0f);
		Titles.check(p);
		Legends.dirty(p);
		return Result.OK;
	}

	/** Puts every sworn Oath's attribute on the player (and takes off those not sworn): at login, respawn and every Oath. */
	public static void apply(ServerPlayer p) {
		for (Oath o : Oath.values()) {
			if (o.buff.attribute == null) {
				continue;
			}
			AttributeInstance a = p.getAttribute(o.buff.attribute);
			if (a == null) {
				continue;
			}
			Identifier id = modifierId(o);
			if (sworn(p, o) && Oath.on()) {
				a.addOrUpdateTransientModifier(new AttributeModifier(id, o.buff.amount, o.buff.op));
			} else {
				a.removeModifier(id);
			}
		}
	}

	private static double share(ServerPlayer p, Oath.Buff buff) {
		if (p == null || !Oath.on()) {
			return 0;
		}
		double out = 0;
		for (Oath o : Oath.values()) {
			if (o.buff == buff && sworn(p, o)) {
				out += buff.amount;
			}
		}
		return out;
	}

	/** How much shorter the player's ability cooldowns are (0.1: 10%). */
	public static double cooldownCut(ServerPlayer p) {
		return share(p, Oath.Buff.COOLDOWN);
	}

	/** How much longer the player's abilities last (0.15: 15%). */
	public static double lasting(ServerPlayer p) {
		return share(p, Oath.Buff.DURATION);
	}

	/** How much more XP the player earns in every skill (0.1: 10%). */
	public static double xp(ServerPlayer p) {
		return share(p, Oath.Buff.XP);
	}
}
