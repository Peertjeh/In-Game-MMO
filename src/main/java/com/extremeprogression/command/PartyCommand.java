package com.extremeprogression.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

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
 */
public final class PartyCommand {
	private PartyCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("party")
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
								.executes(c -> run(c.getSource(), PartyActionPayload.Action.LEADER, StringArgumentType.getString(c, "player"))))));
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
			p.sendSystemMessage(Component.translatable(r.key(), name == null ? "" : name, Config.PARTY_MAX_SIZE).withColor(0xFF5555), false);
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
			p.sendSystemMessage(Component.translatable("message.extremeprogression.party.none").withColor(0xFFFF55), false);
			for (String from : view.invites()) {
				p.sendSystemMessage(Component.translatable("message.extremeprogression.party.waiting", from).withColor(0xAAAAAA), false);
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
