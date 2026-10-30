package com.extremeprogression.gametest;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

/**
 * Survival test players, joined the way a real player joins (a server-side connection to nowhere), as in Extreme
 * Dimensions' tests. Every name gets a random suffix, so tests never share a player.
 */
final class TestPlayers {
	private TestPlayers() {}

	static ServerPlayer survivor(GameTestHelper helper, String name) {
		ServerLevel level = helper.getLevel();
		String unique = (name + UUID.randomUUID().toString().substring(0, 6)).replace("-", "");
		GameProfile profile = new GameProfile(UUID.randomUUID(), unique.length() > 16 ? unique.substring(0, 16) : unique);
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
		ServerPlayer p = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
		p.setGameMode(GameType.SURVIVAL);
		LOADED = clientLoaded(p);
		return p;
	}

	/** What {@link #clientLoaded} found the last time, for failure messages. */
	static String LOADED = "";

	/**
	 * A player takes no damage until their client reports it has loaded the world, and a test player has no client: the
	 * flag is set here, wherever this version keeps it (found by name, as it moved between versions).
	 */
	private static String clientLoaded(ServerPlayer p) {
		StringBuilder found = new StringBuilder();
		for (Object target : new Object[] {p, p.connection}) {
			for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
				for (Field f : c.getDeclaredFields()) {
					String name = f.getName().toLowerCase(Locale.ROOT);
					if (!name.contains("loaded") || java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
						continue;
					}
					try {
						f.setAccessible(true);
						if (f.getType() == boolean.class) {
							f.setBoolean(target, true);
							found.append(c.getSimpleName()).append('.').append(f.getName()).append("=true ");
						} else if (f.getType() == int.class && name.contains("timeout")) {
							f.setInt(target, 0);
							found.append(c.getSimpleName()).append('.').append(f.getName()).append("=0 ");
						}
					} catch (ReflectiveOperationException | RuntimeException e) {
						found.append(f.getName()).append("(").append(e.getClass().getSimpleName()).append(") ");
					}
				}
			}
		}
		return found.toString();
	}
}
