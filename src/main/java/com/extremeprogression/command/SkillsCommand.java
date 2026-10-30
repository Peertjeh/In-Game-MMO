package com.extremeprogression.command;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Predicate;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.config.Config;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.data.SkillData;
import com.extremeprogression.network.Sync;
import com.extremeprogression.progression.Budget;
import com.extremeprogression.progression.Progression;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.tree.TreeRules;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /skills} and its subcommands (design §11): {@code /skills} opens the Character Sheet, and operators set levels,
 * give XP and grant a free respec.
 *
 * <p>An operator's change is never silent: the player is told, and the change goes to the server log.
 */
public final class SkillsCommand {
	private SkillsCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		Predicate<CommandSourceStack> op = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
		dispatcher.register(Commands.literal("skills")
				.executes(c -> overview(c.getSource()))
				.then(Commands.literal("admin").requires(op)
						.then(Commands.literal("set")
								.then(Commands.argument("player", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b))
										.then(Commands.argument("skill", StringArgumentType.word())
												.suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Skill.values()).map(s -> s.id), b))
												.then(Commands.argument("level", IntegerArgumentType.integer(0))
														.executes(c -> set(c, IntegerArgumentType.getInteger(c, "level")))))))
						.then(Commands.literal("add")
								.then(Commands.argument("player", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b))
										.then(Commands.argument("skill", StringArgumentType.word())
												.suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Skill.values()).map(s -> s.id), b))
												.then(Commands.argument("xp", DoubleArgumentType.doubleArg())
														.executes(c -> add(c, DoubleArgumentType.getDouble(c, "xp")))))))
						.then(Commands.literal("respec")
								.then(Commands.argument("player", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b))
										.then(Commands.argument("skill", StringArgumentType.word())
												.suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Skill.values()).map(s -> s.id), b))
												.executes(SkillsCommand::respec))))));
	}

	/** A free respec: the skill's choices and points are reset, without the XP cost or the waiting time. */
	private static int respec(CommandContext<CommandSourceStack> c) {
		CommandSourceStack src = c.getSource();
		ServerPlayer target = target(c);
		Skill skill = skill(c);
		if (target == null || skill == null) {
			return 0;
		}
		PlayerProgress p = PlayerStore.get(target.getUUID());
		if (TreeRules.respec(p, skill, System.currentTimeMillis(), true) != TreeRules.Result.OK) {
			src.sendFailure(Component.translatable("commands.extremeprogression.admin.respec_nothing", target.getName(), Component.translatable(skill.translationKey())));
			return 0;
		}
		changed(src, target, Component.translatable("message.extremeprogression.admin_respec", Component.translatable(skill.translationKey())),
				skill.id + ": free respec");
		src.sendSuccess(() -> Component.translatable("commands.extremeprogression.admin.respec", target.getName(), Component.translatable(skill.translationKey())), true);
		return 1;
	}

	/** Opens the Character Sheet; a client that cannot show it gets every skill's level in chat instead. */
	private static int overview(CommandSourceStack src) throws CommandSyntaxException {
		ServerPlayer player = src.getPlayerOrException();
		if (Sync.openSheet(player)) {
			return 1;
		}
		PlayerProgress p = PlayerStore.get(player.getUUID());
		int total = p.totalLevel();
		src.sendSuccess(() -> Component.translatable("commands.extremeprogression.overview.total", total, Config.TOTAL_LEVEL_CAP,
				String.format(Locale.ROOT, "%.2f", Budget.factor(total))), false);
		for (Skill s : Skill.values()) {
			SkillData d = p.get(s);
			Component line;
			if (d.level >= Config.MAX_LEVEL) {
				line = Component.translatable("commands.extremeprogression.overview.mastery", Component.translatable(s.translationKey()), d.level,
						d.masteryRanks, Config.MASTERY_RANKS);
			} else {
				double need = Progression.xpToNext(p, s);
				int percent = need > 0 ? (int) Math.floor(100 * d.xp / need) : 0;
				line = Component.translatable("commands.extremeprogression.overview.skill", Component.translatable(s.translationKey()), d.level, percent);
			}
			src.sendSuccess(() -> line, false);
		}
		return 1;
	}

	private static int set(CommandContext<CommandSourceStack> c, int level) {
		CommandSourceStack src = c.getSource();
		ServerPlayer target = target(c);
		Skill skill = skill(c);
		if (target == null || skill == null) {
			return 0;
		}
		PlayerProgress p = PlayerStore.get(target.getUUID());
		int old = Progression.setLevel(p, skill, level);
		int now = p.get(skill).level;
		changed(src, target, Component.translatable("message.extremeprogression.admin_set", Component.translatable(skill.translationKey()), old, now),
				skill.id + ": level " + old + " -> " + now);
		src.sendSuccess(() -> Component.translatable("commands.extremeprogression.admin.set", target.getName(), Component.translatable(skill.translationKey()), now, old), true);
		return 1;
	}

	private static int add(CommandContext<CommandSourceStack> c, double xp) {
		CommandSourceStack src = c.getSource();
		ServerPlayer target = target(c);
		Skill skill = skill(c);
		if (target == null || skill == null) {
			return 0;
		}
		PlayerProgress p = PlayerStore.get(target.getUUID());
		int old = p.get(skill).level;
		Progression.Change change = Progression.add(p, skill, xp);
		int now = p.get(skill).level;
		double applied = xp < 0 ? -change.applied() : change.applied();
		String shown = String.format(Locale.ROOT, "%+.0f", applied);
		changed(src, target, Component.translatable("message.extremeprogression.admin_xp", shown, Component.translatable(skill.translationKey()), old, now),
				skill.id + ": " + shown + " XP, level " + old + " -> " + now);
		src.sendSuccess(() -> Component.translatable("commands.extremeprogression.admin.add", target.getName(), Component.translatable(skill.translationKey()), shown, now), true);
		return 1;
	}

	/** Saves, syncs, tells the player and writes the log line: an operator's change is never silent. */
	private static void changed(CommandSourceStack src, ServerPlayer target, Component told, String logged) {
		PlayerStore.markDirty(target.getUUID());
		Sync.send(target);
		target.sendSystemMessage(told, false);
		ExtremeProgression.LOGGER.info("{} changed the progression of {}: {}", src.getTextName(), target.getName().getString(), logged);
	}

	private static ServerPlayer target(CommandContext<CommandSourceStack> c) {
		String name = StringArgumentType.getString(c, "player");
		ServerPlayer player = c.getSource().getServer().getPlayerList().getPlayerByName(name);
		if (player == null) {
			c.getSource().sendFailure(Component.translatable("commands.extremeprogression.no_player", name));
		}
		return player;
	}

	private static Skill skill(CommandContext<CommandSourceStack> c) {
		String id = StringArgumentType.getString(c, "skill");
		Skill skill = Skill.byId(id.toLowerCase(Locale.ROOT));
		if (skill == null) {
			c.getSource().sendFailure(Component.translatable("commands.extremeprogression.unknown_skill", id));
		}
		return skill;
	}
}
