package com.extremeprogression.gametest;

import java.lang.reflect.Method;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
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
		clientLoaded(p);
		return p;
	}

	/**
	 * A player takes no damage until their client reports it has loaded the world (ServerboundPlayerLoadedPacket), and a
	 * test player has no client: the server is told so directly.
	 */
	private static void clientLoaded(ServerPlayer p) {
		try {
			Method m = ServerGamePacketListenerImpl.class.getDeclaredMethod("markClientLoaded");
			m.setAccessible(true);
			m.invoke(p.connection);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("could not mark a test player's client as loaded", e);
		}
	}
}
