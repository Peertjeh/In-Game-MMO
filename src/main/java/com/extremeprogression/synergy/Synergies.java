package com.extremeprogression.synergy;

import java.util.function.Predicate;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.network.SynergyPayload;
import com.extremeprogression.spec.Specs;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Discovering synergies (design §2): personal and server-wide. A synergy works for a player once both its skills are at
 * level 30 and they discovered it, which happens the first time its situation comes up in their play with both levels
 * met. The first discoverer on the server goes into the {@link Chronicle}, with a chat announcement when the option is
 * on; after that, every player's rune hint for it reads more clearly.
 */
public final class Synergies {
	private Synergies() {}

	/** On the client: whether its own player discovered a synergy. Set by the client. */
	public static Predicate<Synergy> localKnown = s -> false;

	public static boolean eligible(Entity e, Synergy s) {
		SkillData a = Specs.skillData(e, s.first);
		SkillData b = Specs.skillData(e, s.second);
		return a != null && b != null && a.level >= Config.SYNERGY_LEVEL && b.level >= Config.SYNERGY_LEVEL;
	}

	public static boolean known(Entity e, Synergy s) {
		if (e instanceof ServerPlayer p) {
			return PlayerStore.get(p.getUUID()).synergies.containsKey(s.id);
		}
		if (e instanceof Player p && p.level().isClientSide() && p.isLocalPlayer()) {
			return localKnown.test(s);
		}
		return false;
	}

	/** Whether the synergy works for this player now (discovered, both levels met). */
	public static boolean active(Entity e, Synergy s) {
		return eligible(e, s) && known(e, s);
	}

	/** How strongly it works: the weaker of its two skills' powers (death fatigue, an opposing skill). */
	public static double power(Entity e, Synergy s) {
		return Math.min(Specs.power(e, s.first), Specs.power(e, s.second));
	}

	/**
	 * The synergy's situation just happened for this player. With both levels met it works, and the first time it is
	 * discovered on the spot. Returns whether it works now.
	 */
	public static boolean trigger(ServerPlayer p, Synergy s) {
		if (!eligible(p, s)) {
			return false;
		}
		if (!PlayerStore.get(p.getUUID()).synergies.containsKey(s.id)) {
			discover(p, s);
		}
		return true;
	}

	/** Records the discovery, tells the player, and the chronicle when it is the server's first. */
	public static void discover(ServerPlayer p, Synergy s) {
		PlayerProgress progress = PlayerStore.get(p.getUUID());
		PlayerProgress.Discovery d = new PlayerProgress.Discovery();
		d.time = System.currentTimeMillis();
		d.dimension = p.level().dimension().identifier().toString();
		d.x = p.getBlockX();
		d.y = p.getBlockY();
		d.z = p.getBlockZ();
		progress.synergies.put(s.id, d);
		PlayerStore.markDirty(p.getUUID());
		Component name = Component.translatable(s.translationKey());
		p.sendSystemMessage(Component.translatable("message.extremeprogression.synergy.discovered", name,
				Component.translatable(s.first.translationKey()), Component.translatable(s.second.translationKey()),
				Component.translatable(s.translationKey() + ".desc")).withColor(0xFFAA00), false);
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6f, 1.2f);
		Chronicle.First first = new Chronicle.First();
		first.uuid = p.getUUID().toString();
		first.name = p.getName().getString();
		first.time = d.time;
		first.dimension = d.dimension;
		first.x = d.x;
		first.y = d.y;
		first.z = d.z;
		MinecraftServer server = p.level().getServer();
		boolean firstOnServer = Chronicle.record(s, first);
		ExtremeProgression.LOGGER.info("{} discovered the synergy {}{}", first.name, s.id, firstOnServer ? " (first on the server)" : "");
		if (firstOnServer) {
			if (Config.SYNERGY_ANNOUNCE) {
				server.getPlayerList().broadcastSystemMessage(Component.translatable("message.extremeprogression.synergy.first", p.getDisplayName(), name)
						.withColor(0xFFAA00), false);
			} else {
				p.sendSystemMessage(Component.translatable("message.extremeprogression.synergy.first_you").withColor(0xFFAA00), false);
			}
			// (every player's hints for it now read more clearly)
			for (ServerPlayer other : server.getPlayerList().getPlayers()) {
				send(other);
			}
		} else {
			send(p);
		}
	}

	/** Sends a player their codex and the server's chronicle. */
	public static void send(ServerPlayer p) {
		if (ServerPlayNetworking.canSend(p, SynergyPayload.TYPE)) {
			ServerPlayNetworking.send(p, SynergyPayload.of(PlayerStore.get(p.getUUID()), p.getUUID().toString()));
		}
	}
}
