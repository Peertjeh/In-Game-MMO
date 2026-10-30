package com.extremeprogression.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremeprogression.antiexploit.MobOrigins;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;

/** A mob is set up for the reason it spawns: spawn eggs, spawners and commands pass theirs here too. */
@Mixin(Mob.class)
public abstract class MobMixin {
	@Inject(method = "finalizeSpawn", at = @At("HEAD"), require = 1)
	private void extremeprogression$spawned(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		MobOrigins.created((Mob) (Object) this, reason);
	}
}
