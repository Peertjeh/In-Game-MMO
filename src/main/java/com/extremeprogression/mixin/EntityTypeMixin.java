package com.extremeprogression.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.extremeprogression.antiexploit.MobOrigins;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;

/**
 * Every entity is made by one of these two factories, with the reason it is made: a spawn egg, a spawner, a command.
 * (Spawners and {@code /summon} with data go through the second one.)
 */
@Mixin(EntityType.class)
public abstract class EntityTypeMixin {
	@Inject(method = "create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnReason;)Lnet/minecraft/world/entity/Entity;",
			at = @At("RETURN"), require = 1)
	private void extremeprogression$created(Level level, EntitySpawnReason reason, CallbackInfoReturnable<Entity> cir) {
		MobOrigins.created(cir.getReturnValue(), reason);
	}

	@Inject(method = "create(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/storage/ValueInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnReason;)Ljava/util/Optional;",
			at = @At("RETURN"), require = 1)
	private static void extremeprogression$loaded(EntityType<?> type, ValueInput input, Level level, EntitySpawnReason reason,
			CallbackInfoReturnable<Optional<Entity>> cir) {
		cir.getReturnValue().ifPresent(e -> MobOrigins.created(e, reason));
	}
}
