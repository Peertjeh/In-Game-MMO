package com.extremeprogression.client;

import java.util.ArrayList;
import java.util.List;

import com.extremeprogression.mixin.EntityAccess;
import com.extremeprogression.network.OreSensePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;

/**
 * Ore Sense on the client: a glowing copy of every ore the server named, seen through the walls by the glowing
 * outline, until the ability ends. The copies exist only on this client (negative ids, which the server never uses).
 */
public final class OreSense {
	private OreSense() {}

	/** The glowing outline is shared flag 6. */
	private static final int GLOWING = 6;
	private static final List<Entity> SHOWN = new ArrayList<>();
	private static int ticksLeft = 0;
	private static int nextId = -1_000_000;

	public static void show(OreSensePayload payload) {
		clear();
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		for (OreSensePayload.Ore ore : payload.ores()) {
			Display.BlockDisplay display = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
			if (display == null) {
				continue;
			}
			display.setId(nextId--);
			display.setPos(ore.pos().getX(), ore.pos().getY(), ore.pos().getZ());
			display.setBlockState(level.getBlockState(ore.pos()));
			display.setGlowColorOverride(ore.color());
			display.setBrightnessOverride(new Brightness(15, 15));
			((EntityAccess) display).extremeprogression$setSharedFlag(GLOWING, true);
			level.addEntity(display);
			SHOWN.add(display);
		}
		ticksLeft = payload.ticks();
	}

	/** How many ores glow now (for tests). */
	public static int shown() {
		return SHOWN.size();
	}

	/** Every client tick: the glow ends with the ability. */
	public static void tick() {
		if (ticksLeft > 0 && --ticksLeft == 0) {
			clear();
		}
	}

	public static void clear() {
		ClientLevel level = Minecraft.getInstance().level;
		for (Entity e : SHOWN) {
			if (level != null) {
				level.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
			}
		}
		SHOWN.clear();
		ticksLeft = 0;
	}
}
