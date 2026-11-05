package com.extremeprogression.server;

import java.util.function.Predicate;

import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

/**
 * The mod's permissions (1.6.0, as Extreme Dimensions' 3.29), through Fabric API's permission API: a permission mod
 * such as LuckPerms grants or denies each one to whom it will (LuckPerms writes them with dots:
 * {@code extremeprogression.admin.players}). Without one, each falls back to the operator level it always needed, so
 * nothing changes for a server that has none; in a world of your own you hold them all.
 *
 * <p>The levels are fixed (not settings), so that whoever may change the settings cannot lower the bar for a permission
 * they lack.
 */
public final class Perms {
	private Perms() {}

	/** One permission: its id ({@code extremeprogression:admin/...}) and the operator level that stands in without a permission mod. */
	public enum Node {
		/** The Players screen, {@code /skills players}, {@code /skills inspect}, and {@code /skills admin set|add|respec|cooldowns}. */
		PLAYERS("admin/players", PermissionLevel.GAMEMASTERS),
		/** Wiping a player's progression: {@code /skills admin reset}, and Reset in the Players screen. */
		RESET("admin/reset", PermissionLevel.ADMINS),
		/** Changing the settings (the settings screen), {@code /skills settings}, {@code preset} and {@code reload}. */
		SETTINGS("admin/settings", PermissionLevel.ADMINS),
		/** XP events: {@code /skills xprate}. */
		EVENTS("admin/events", PermissionLevel.ADMINS);

		public final Identifier id;
		public final PermissionLevel level;

		Node(String path, PermissionLevel level) {
			this.id = Identifier.fromNamespaceAndPath("extremeprogression", path);
			this.level = level;
		}

		/** As a permission mod such as LuckPerms writes it: {@code extremeprogression.admin.players}. */
		public String dotted() {
			return id.getNamespace() + "." + id.getPath().replace('/', '.');
		}
	}

	/** Whether this command source holds the permission (granted by a permission mod, or by its operator level without one). */
	public static boolean has(CommandSourceStack src, Node node) {
		return ((PermissionContextOwner) (Object) src).checkPermission(node.id, node.level);
	}

	/** Whether this player holds the permission; the player of a world of their own (not opened to LAN) always does. */
	public static boolean has(ServerPlayer p, Node node) {
		MinecraftServer s = p.level().getServer();
		if (s != null && s.isSingleplayer() && !s.isPublished()) {
			return true;
		}
		return ((PermissionContextOwner) (Object) p).checkPermission(node.id, node.level);
	}

	/** For a command: only those who hold the permission see it and may run it. */
	public static Predicate<CommandSourceStack> require(Node node) {
		return src -> has(src, node);
	}
}
