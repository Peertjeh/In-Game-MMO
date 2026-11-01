package com.extremeprogression.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import com.extremeprogression.config.Config;
import com.extremeprogression.network.PartyActionPayload;
import com.extremeprogression.network.PartyPayload;
import com.extremeprogression.party.Parties;
import com.extremeprogression.party.Party;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /party} (design §16): without anything, the party and its bonus in the chat; {@code invite}, {@code accept},
 * {@code decline}, {@code leave}, and for the leader {@code kick} and {@code leader}. The Party screen's buttons do the
 * same through {@link #act}.
 *
 * <p>Always under {@code /skills party} too. {@code /party} itself is registered only when no other mod has one
 * (BattleHub has its own parties): two mods' {@code /party} would merge into one command, half of each. So this
 * registers after everyone else, and the chat buttons and hints use whichever {@link #root} it got.
 */
public final class PartyCommand {
	private PartyCommand() {}

	/** "party", or "skills party" when another mod has /party. */
	private static String root = "party";

	/** The command the party's chat buttons and hints use: "party", or "skills party" when another mod has /party. */
	public static String root() {
		return root;
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("skills").then(tree(Commands.literal("party"))));
		if (dispatcher.getRoot().getChild("party") == null) {
			dispatcher.register(tree(Commands.literal("party")));
			root = "party";
		} else {
			root = "skills party";
			com.extremeprogression.ExtremeProgression.LOGGER.info("Another mod has /party: Extreme Progression's parties are under /skills party");
		}
	}

	/** The party's subcommands under a literal. */
	static LiteralArgumentBuilder<CommandSourceStack> tree(LiteralArgumentBuilder<CommandSourceStack> party) {
		return party
				.executes(c -> info(c.getSource().getPlayerOrException()))
				.then(Commands.literal("invite")
						.then(Commands.argument("player", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(c.getSource().getOnlinePlayerNames(), b))
								.executes(c -> run(c.getSource(), PartyActionPayload.Action.INVITE, StringArgumentType.getString(c, "player")))))
				.then(Commands.literal("accept")
						.executes(c -> run(c.getSource(), PartyActionPayload.Action.ACCEPT, null))
						.then(Commands.argument("player", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(Parties.payload(c.getSource().getPlayerOrException()).invites(), b))
								.executes(c -> run(c.getSource(), PartyActionPayload.Action.ACCEPT, StringArgumentType.getString(c, "player")))))
				.then(Commands.literal("decline")
						.executes(c -> run(c.getSource(), PartyActionPayload.Action.DECLINE, null))
						.then(Commands.argument("player", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(Parties.payload(c.getSource().getPlayerOrException()).invites(), b))
								.executes(c -> run(c.getSource(), PartyActionPayload.Action.DECLINE, StringArgumentType.getString(c, "player")))))
				.then(Commands.literal("leave")
						.executes(c -> run(c.getSource(), PartyActionPayload.Action.LEAVE, null)))
				.then(Commands.literal("kick")
						.then(Commands.argument("player", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(members(c.getSource()), b))
								.executes(c -> run(c.getSource(), PartyActionPayload.Action.KICK, StringArgumentType.getString(c, "player")))))
				.then(Commands.literal("leader")
						.then(Commands.argument("player", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(members(c.getSource()), b))
								.executes(c -> run(c.getSource(), PartyActionPayload.Action.LEADER, StringArgumentType.getString(c, "player")))));
	}

	private static java.util.List<String> members(CommandSourceStack src) {
		Party party = src.getEntity() instanceof ServerPlayer p ? Parties.of(p.getUUID()) : null;
		return party == null ? java.util.List.of() : party.members.stream().map(party::name).toList();
	}

	private static int run(CommandSourceStack src, PartyActionPayload.Action action, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		return act(src.getPlayerOrException(), action, name) == Parties.Result.OK ? 1 : 0;
	}

	/** Does a party action for a player; anything that went wrong is told to them. */
	public static Parties.Result act(ServerPlayer p, PartyActionPayload.Action action, String name) {
		if (action == null) {
			return Parties.Result.NO_PARTY;
		}
		Parties.Result r = switch (action) {
			case INVITE -> {
				ServerPlayer to = name == null ? null : p.level().getServer().getPlayerList().getPlayerByName(name);
				yield to == null ? Parties.Result.NOT_ONLINE : Parties.invite(p, to);
			}
			case ACCEPT -> Parties.accept(p, name == null || name.isEmpty() ? null : name);
			case DECLINE -> Parties.decline(p, name == null || name.isEmpty() ? null : name);
			case LEAVE -> Parties.leave(p);
			case KICK -> Parties.kick(p, name == null ? "" : name);
			case LEADER -> Parties.promote(p, name == null ? "" : name);
		};
		if (r != Parties.Result.OK) {
			p.sendSystemMessage(Component.translatable(r.key(), name == null ? "" : name, Config.PARTY_MAX_SIZE, root).withColor(0xFF5555), false);
		}
		return r;
	}

	/** The party in the chat: its members, who of them is near and earning, and the bonus now. */
	private static int info(ServerPlayer p) {
		PartyPayload view = Parties.payload(p);
		if (!view.enabled()) {
			p.sendSystemMessage(Component.translatable(Parties.Result.OFF.key()).withColor(0xFF5555), false);
			return 0;
		}
		if (!view.inParty()) {
			p.sendSystemMessage(Component.translatable("message.extremeprogression.party.none", root).withColor(0xFFFF55), false);
			for (String from : view.invites()) {
				p.sendSystemMessage(Component.translatable("message.extremeprogression.party.waiting", from, root).withColor(0xAAAAAA), false);
			}
			return 1;
		}
		p.sendSystemMessage(Component.translatable("message.extremeprogression.party.header", view.members().size(), view.maxSize(), percent(view.bonus()),
				percent(view.bonusMax())).withColor(0xFFAA00), false);
		for (PartyPayload.Member m : view.members()) {
			MutableComponent line = Component.literal(m.name() + " ✦" + m.total()).withColor(m.online() ? 0xFFFFFF : 0x555555);
			if (m.leader()) {
				line.append(Component.literal(" · ").withColor(0x555555)).append(Component.translatable("message.extremeprogression.party.state.leader").withColor(0xFFAA00));
			}
			String state = !m.online() ? "offline" : m.id().equals(p.getUUID()) ? "you" : m.active() ? "active" : m.near() ? "near" : "far";
			line.append(Component.literal(" · ").withColor(0x555555)).append(Component.translatable("message.extremeprogression.party.state." + state)
					.withColor(m.active() ? 0x55FF55 : 0xAAAAAA));
			p.sendSystemMessage(line, false);
		}
		return 1;
	}

	/** "+10%". */
	public static String percent(double share) {
		return "+" + Math.round(share * 100) + "%";
	}
}
