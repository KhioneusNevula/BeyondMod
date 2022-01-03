package com.gm910.beyondmod.spirit;

import com.gm910.beyondmod.spirit.Soul.SoulState;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.BlockEvent.EntityPlaceEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class PrimarySoulEventHandler {

	private static void cancelEventIfSoul(EntityEvent event) {
		cancelEventIfSoul(event, event.getEntity());
	}

	private static void cancelEventIfSoul(Event event, Entity en) {
		if (Soul.hasSys(en) && Soul.sys(en).isSoul()) {
			if (event.isCancelable())
				event.setCanceled(true);
			if (event.hasResult())
				event.setResult(Result.DENY);
		}
	}

	@SubscribeEvent
	public static void death(LivingDeathEvent event) {
		if (Soul.hasSys(event.getEntity())) {
			Soul sys = Soul.sys(event.getEntity());
			if (sys.isSouledVessel() && !sys.isIntegrated()) {
				sys.splitSoul().getFirst();
			}
		}
	}

	@SubscribeEvent
	public static void onEntityEvent(LivingEntityUseItemEvent.Start event) {
		cancelEventIfSoul(event);
	}

	@SubscribeEvent
	public static void onEntityEvent(LivingDestroyBlockEvent event) {
		cancelEventIfSoul(event);
	}

	@SubscribeEvent
	public static void onEntityEvent(ExplosionEvent event) {
		LivingEntity sour = event.getExplosion().getSourceMob();
		if (sour != null) {
			cancelEventIfSoul(event, sour);
		}
	}

	@SubscribeEvent
	public static void onEntityEvent(LivingDropsEvent event) {
		cancelEventIfSoul(event);
	}

	@SubscribeEvent
	public static void onEntityEvent(LivingKnockBackEvent event) {
		cancelEventIfSoul(event);
	}

	@SubscribeEvent
	public static void onEntityEvent(EntityMobGriefingEvent event) {
		cancelEventIfSoul(event);
	}

	@SubscribeEvent
	public static void onEntityEvent(EntityPlaceEvent event) {
		cancelEventIfSoul(event, event.getEntity());
	}

	@SubscribeEvent
	public static void onEntityEvent(EntityTeleportEvent event) {
		cancelEventIfSoul(event);
	}

	@SubscribeEvent
	public static void targetSet(LivingSetAttackTargetEvent event) {
		if (Soul.hasSys(event.getEntity()) && Soul.hasSys(((Mob) event.getEntity()).getTarget())) {
			Soul sou = Soul.sys(event.getEntity());
			Soul sout = Soul.sys(((Mob) event.getEntity()).getTarget());
			if (sou.hasCorporealVessel() && sou.tether.isCorporeal()
					&& sou.tether.getTetherEntity() == sout.getOwner()) {
				((Mob) sou.getOwner()).setTarget(null);
			}
		}
	}

	@SubscribeEvent
	public static void onAttackEntity(LivingAttackEvent event) {
		cancelEventIfSoul(event);
		cancelEventIfSoul(event, event.getSource().getEntity());
		if (!(event.getEntityLiving() instanceof Player) && Soul.hasSys(event.getEntityLiving())) {
			if (event.getSource().getEntity() instanceof Player) {
				debugSoulEvent(event); // TODO obviously eventually get rid of this
				return;
			} else if (event.getSource().getEntity() instanceof LivingEntity) {
				debugSwitchSoulEvent(event);
				return;
			}
		}

	}

	private static void debugSoulEvent(LivingAttackEvent event) {
		Soul sys = Soul.sys(event.getEntityLiving());
		System.out.println("This entity " + event.getEntityLiving() + " is a " + sys.soulState);
		if (sys.getSoulState() != SoulState.INTEGRATED)
			return;
		LivingEntity ves = sys.splitIntegratedSoul();
		ves.level.addFreshEntity(ves);
		ves.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 2000));
	}

	private static void debugSwitchSoulEvent(LivingAttackEvent event) {

		Soul sys = Soul.sys(event.getEntityLiving());
		Soul sysA = Soul.sys((LivingEntity) event.getSource().getEntity());

		if (sys.getSoulState() != SoulState.INTEGRATED || !sysA.isIntegrated()) {
			return;
		}
		LivingEntity ves = sys.splitIntegratedSoul();
		ves.level.addFreshEntity(ves);
		ves.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 2000));
		LivingEntity vesA = sysA.splitIntegratedSoul();
		vesA.level.addFreshEntity(vesA);
		vesA.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 2000));

		sys.bindSoulToVessel(vesA);
		sysA.bindSoulToVessel(ves);

	}

	@SubscribeEvent
	public static void onWorld(LivingSpawnEvent.SpecialSpawn event) {

		if (Soul.hasSys(event.getEntity())) {
			LivingEntity liv = (LivingEntity) event.getEntity();
			Soul sys = Soul.sys(liv);
			sys.onUpdatedStateOfBeing();
			System.out.println("" + liv.getDisplayName().getString() + " is " + sys.getSoulState());
		}
	}
}
