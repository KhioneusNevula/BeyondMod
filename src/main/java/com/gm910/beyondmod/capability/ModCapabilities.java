package com.gm910.beyondmod.capability;

import com.gm910.beyondmod.Ref;
import com.gm910.beyondmod.spirit.Soul;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

public class ModCapabilities {

	public static final ResourceLocation SOUL = new ResourceLocation(Ref.MODID, "soul_cap");

	@EventBusSubscriber
	public static class EventHandler {

		@SubscribeEvent
		public static void onRegister(RegisterCapabilitiesEvent event) {
			event.register(Soul.class);
		}

		@SubscribeEvent
		public static void onAttachEntity(AttachCapabilitiesEvent<Entity> event) {
			if (!event.getObject().level.isClientSide()) {
				Soul.attachThisCapEvent(event);
			}
		}

	}

}
