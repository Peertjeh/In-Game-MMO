package com.extremeprogression.tree;

import java.util.Locale;

import com.extremeprogression.ExtremeProgression;
import com.extremeprogression.data.PlayerProgress;
import com.extremeprogression.data.PlayerStore;
import com.extremeprogression.network.Sync;
import com.extremeprogression.network.TreePayload;
import com.extremeprogression.skill.Skill;
import com.extremeprogression.skill.Specialization;
import com.extremeprogression.xp.XpService;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** The Skill Screen's requests on the server: every rule is checked here again, and the player is told the outcome. */
public final class TreeService {
	private TreeService() {}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(TreePayload.TYPE, (payload, context) -> handle(context.player(), payload));
	}

	public static TreeRules.Result handle(ServerPlayer player, TreePayload request) {
		Skill skill = Skill.byId(request.skill());
		if (skill == null) {
			return TreeRules.Result.NOT_CHOSEN;
		}
		PlayerProgress p = PlayerStore.get(player.getUUID());
		TreeRules.Result result;
		Component done;
		switch (request.action()) {
			case CHOOSE -> {
				Specialization spec = Specialization.byId(request.arg());
				result = spec == null || spec.skill != skill ? TreeRules.Result.NOT_CHOSEN : TreeRules.choose(p, spec);
				done = spec == null ? null : Component.translatable("message.extremeprogression.tree.chosen", Component.translatable(spec.translationKey()));
			}
			case BUY -> {
				int slash = request.arg().indexOf('/');
				Specialization spec = slash < 0 ? null : Specialization.byId(request.arg().substring(0, slash));
				NodeSlot slot = TreeRules.slotOf(request.arg());
				result = spec == null || slot == null || spec.skill != skill ? TreeRules.Result.NOT_CHOSEN : TreeRules.buy(p, spec, slot);
				done = null;
			}
			case RESPEC -> {
				int before = p.get(skill).level;
				result = TreeRules.respec(p, skill, System.currentTimeMillis(), false);
				int after = p.get(skill).level;
				done = Component.translatable("message.extremeprogression.tree.respec", Component.translatable(skill.translationKey()), before, after);
				if (result == TreeRules.Result.OK) {
					ExtremeProgression.LOGGER.info("{} respecced {}: level {} -> {}", player.getName().getString(), skill.id, before, after);
				}
			}
			case PERK -> {
				result = com.extremeprogression.mastery.Mastery.choose(p, skill, request.arg());
				com.extremeprogression.mastery.MasteryPerk perk = com.extremeprogression.mastery.MasteryPerk.of(skill, request.arg());
				done = perk == null ? null : Component.translatable("message.extremeprogression.mastery.chosen", Component.translatable(perk.translationKey()),
						p.get(skill).perks.size());
			}
			default -> {
				result = TreeRules.Result.NOT_CHOSEN;
				done = null;
			}
		}
		if (result == TreeRules.Result.OK) {
			PlayerStore.markDirty(player.getUUID());
			Sync.send(player);
			if (done != null) {
				XpService.notice(player, done, skill);
			}
		} else {
			XpService.notice(player, Component.translatable("message.extremeprogression.tree." + result.name().toLowerCase(Locale.ROOT)), skill);
		}
		return result;
	}
}
