package com.extremeprogression.command;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Predicate;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.legends.Boosts;
import com.extremeprogression.legends.Champions;
import com.extremeprogression.legends.Legends;
import com.extremeprogression.legends.LegendsData;
import com.extremeprogression.legends.Tome;
import com.extremeprogression.server.Perms;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;

/**
 * The Legends update's commands (2.0): {@code /skills legends} opens the Legends screen; {@code /skills boost} shows your
 * boost, and for admins gives one ({@code give <player> <percent> <minutes>}), gives tomes ({@code tome <player>
 * <tome> [count]}, e.g. as a vote reward) or takes them all ({@code clear <player>}); {@code /skills champion <affix>}
 * makes the nearest hostile mob a Champion.
 */
public final class LegendsCommands {
	private LegendsCommands() {}

	static void register(LiteralArgumentBuilder<CommandSourceStack> skills) {
		Predicate<CommandSourceStack> boosts = Perms.require(Perms.Node.BOOSTS);
		Predicate<CommandSourceStack> events = Perms.require(Perms.Node.EVENTS);
		skills.then(Commands.literal("legends").executes(c -> {
			Legends.send(c.getSource().getPlayerOrException(), true);
			return 1;
		}));
		skills.then(Commands.literal("boost")
				.executes(c -> info(c.getSource(), c.getSource().getPlayerOrException()))
				.then(Commands.literal("give").requires(boosts)
						.then(player()
								.then(Commands.argument("percent", IntegerArgumentType.integer(1, 1000))
										.then(Commands.argument("minutes", IntegerArgumentType.integer(1, 1440))
												.executes(LegendsCommands::give)))))
				.then(Commands.literal("tome").requires(boosts)
						.then(player()
								.then(Commands.argument("tome", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Tome.values()).map(t -> t.id), b))
										.executes(c -> tome(c, 1))
										.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
												.executes(c -> tome(c, IntegerArgumentType.getInteger(c, "count")))))))
				.then(Commands.literal("clear").requires(boosts)
						.then(player().executes(LegendsCommands::clear))));
		skills.then(Commands.literal("champion").requires(events)
				.then(Commands.argument("affix", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Champions.Affix.values()).map(a -> a.id), b))
						.executes(LegendsCommands::champion)));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> player() {
		return Commands.argument("player", StringArgumentType.word())
				.suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getServer().getPlayerNames(), b));
	}

	private static ServerPlayer target(CommandContext<CommandSourceStack> c) {
		String name = StringArgumentType.getString(c, "player");
		ServerPlayer p = c.getSource().getServer().getPlayerList().getPlayerByName(name);
		if (p == null) {
			c.getSource().sendFailure(Component.translatable("commands.extremeprogression.boost.not_online", name));
		}
		return p;
	}

	/** Your boost now: the running tome, the waiting ones, Well Rested, the streak. */
	static int info(CommandSourceStack src, ServerPlayer p) {
		LegendsData d = PlayerStore.get(p.getUUID()).legends;
		LegendsData.Boost top = Boosts.running(d);
		src.sendSuccess(() -> Component.translatable("commands.extremeprogression.boost.info", Math.round(Boosts.bonus(p) * 100),
				top == null ? 0 : top.percent, top == null ? 0 : (top.seconds + 59) / 60, Math.max(0, d.boosts.size() - (top == null ? 0 : 1)),
				(d.restedSeconds + 59) / 60, d.streak), false);
		return 1;
	}

	private static int give(CommandContext<CommandSourceStack> c) {
		ServerPlayer p = target(c);
		if (p == null) {
			return 0;
		}
		int percent = IntegerArgumentType.getInteger(c, "percent");
		int minutes = IntegerArgumentType.getInteger(c, "minutes");
		Boosts.add(p, percent, minutes * 60, "admin");
		p.sendSystemMessage(Component.translatable("message.extremeprogression.boost.given", percent, minutes).withColor(0x55FF55), false);
		c.getSource().sendSuccess(() -> Component.translatable("commands.extremeprogression.boost.gave", p.getName(), percent, minutes), true);
		return 1;
	}

	private static int tome(CommandContext<CommandSourceStack> c, int count) {
		ServerPlayer p = target(c);
		if (p == null) {
			return 0;
		}
		Tome t = Tome.byId(StringArgumentType.getString(c, "tome").toLowerCase(Locale.ROOT));
		if (t == null) {
			c.getSource().sendFailure(Component.translatable("commands.extremeprogression.boost.no_tome", StringArgumentType.getString(c, "tome")));
			return 0;
		}
		for (int left = count; left > 0; left -= 16) {
			Boosts.give(p, t, Math.min(16, left));
		}
		c.getSource().sendSuccess(() -> Component.translatable("commands.extremeprogression.boost.gave_tome", p.getName(), count,
				Component.translatable(t.translationKey())), true);
		return count;
	}

	private static int clear(CommandContext<CommandSourceStack> c) {
		ServerPlayer p = target(c);
		if (p == null) {
			return 0;
		}
		LegendsData d = PlayerStore.get(p.getUUID()).legends;
		d.boosts.clear();
		d.restedSeconds = 0;
		PlayerStore.markDirty(p.getUUID());
		Legends.dirty(p);
		c.getSource().sendSuccess(() -> Component.translatable("commands.extremeprogression.boost.cleared", p.getName()), true);
		return 1;
	}

	/** The nearest hostile mob within 16 blocks becomes a Champion with this affix. */
	private static int champion(CommandContext<CommandSourceStack> c) {
		Champions.Affix affix = Champions.Affix.byId(StringArgumentType.getString(c, "affix").toLowerCase(Locale.ROOT));
		var pos = c.getSource().getPosition();
		Mob best = null;
		double bestDistance = Double.MAX_VALUE;
		for (var e : c.getSource().getLevel().getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(pos, pos).inflate(16),
				e -> e instanceof Mob m && m.isAlive() && m.getType().getCategory() == MobCategory.MONSTER && !Champions.is(m))) {
			double d = e.position().distanceToSqr(pos);
			if (d < bestDistance) {
				best = (Mob) e;
				bestDistance = d;
			}
		}
		if (affix == null || best == null) {
			c.getSource().sendFailure(Component.translatable(affix == null ? "commands.extremeprogression.champion.no_affix" : "commands.extremeprogression.champion.none"));
			return 0;
		}
		Champions.make(best, affix);
		Mob made = best;
		c.getSource().sendSuccess(() -> Component.translatable("commands.extremeprogression.champion.made", made.getDisplayName()), true);
		return 1;
	}
}
