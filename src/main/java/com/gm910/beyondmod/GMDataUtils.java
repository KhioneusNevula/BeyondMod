package com.gm910.beyondmod;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public class GMDataUtils {

	public static Entity fromUUID(UUID id, ServerLevel level) {
		Entity e = level.getEntity(id);
		if (e == null) {
			for (ServerLevel lev : level.getServer().forgeGetWorldMap().values()) {

				if ((e = lev.getEntity(id)) != null) {
					return e;
				}
			}
		}
		return e;

	}

}
