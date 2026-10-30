package com.extremeprogression.xp;

import com.extremeprogression.skill.Skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Animal;

/**
 * Farming XP for animals (design §2, Appendix A.6): per baby bred, and once per animal tamed (vanilla tames an animal
 * only once). Milking and collecting eggs give nothing, because they can be repeated endlessly. Shearing is a
 * harvest ({@link Harvests}).
 */
public final class AnimalXp {
	private AnimalXp() {}

	/** From the vanilla trigger for advancements: the player bred two animals. */
	public static void bred(ServerPlayer player) {
		XpService.award(player, Skill.FARMING, XpTables.BREEDING * com.extremeprogression.spec.Farming.breedXp(player));
	}

	/** From the vanilla trigger for advancements: the player tamed an animal. */
	public static void tamed(ServerPlayer player, Animal animal) {
		XpService.award(player, Skill.FARMING, XpTables.taming(animal.getType()) * com.extremeprogression.spec.Farming.tamerXp(player));
	}
}
