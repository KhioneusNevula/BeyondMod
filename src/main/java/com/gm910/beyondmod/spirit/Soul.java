package com.gm910.beyondmod.spirit;

import java.util.UUID;

import com.gm910.beyondmod.GMDataUtils;
import com.gm910.beyondmod.capability.ModCapabilities;
import com.mojang.datafixers.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Represents the soul system of a being
 * 
 * @author borah
 *
 */
@EventBusSubscriber
public class Soul implements ICapabilitySerializable<CompoundTag> {

	public static Capability<Soul> INSTANCE = CapabilityManager.get(new CapabilityToken<>() {
	});

	public static enum SoulState {
		SOUL, VESSEL,
		/**
		 * integrated is an entity which has its soul in its natural body. Since soul
		 * and body are congruent, no need to treat the entity and its soul as separate
		 **/
		INTEGRATED;

		/**
		 * Returns if this is a soul or integrated
		 * 
		 * @return
		 */
		public boolean hasSoul() {
			return this == SOUL || this == INTEGRATED;

		}

		/**
		 * Returns if this is a vessel or integrated
		 * 
		 * @return
		 */
		public boolean hasVessel() {
			return this == VESSEL || this == INTEGRATED;
		}

		public boolean isOnlySoul() {
			return this == SOUL;
		}

		public boolean isOnlyVessel() {
			return this == VESSEL;
		}

		public boolean isIntegrated() {
			return this == INTEGRATED;
		}
	}

	protected SoulState soulState = SoulState.INTEGRATED;

	/**
	 * Creates an ID of congruence for this soul and vessel. If the soul and
	 * vessel's congruence matches and they are combined, then simply integrate
	 * them.
	 */
	protected UUID congruenceID = UUID.randomUUID();

	/**
	 * The entity itself, regardless of if soul or vessel
	 */
	private LivingEntity owner;

	/**
	 * Only set this if this entity is a vessel
	 */
	protected LivingEntity soul;
	/**
	 * Only set this if this entity is a soul
	 */
	protected SoulTether tether;

	/**
	 * Only if this is a soul; determines if reapers can reap it
	 */
	public boolean isProtectedSoul;

	public Soul(LivingEntity e) {
		this.owner = e;

	}

	public boolean hasCorporealVessel() {
		return tether.isCorporeal();
	}

	public boolean isSoul() {
		return soulState.isOnlySoul();
	}

	public boolean isVessel() {
		return soulState.isOnlyVessel();
	}

	public boolean isIntegrated() {
		return this.soulState.isIntegrated();
	}

	/**
	 * If this VESSEL/INTEGRATED has a soul
	 * 
	 * @return
	 */
	public boolean isSouledVessel() {
		return soulState.isIntegrated() || this.soul != null;
	}

	/**
	 * If this SOUL/INTEGRATED has a vessel
	 * 
	 * @return
	 */
	public boolean isVesseledSoul() {
		return soulState.isIntegrated() || this.tether.isCorporeal();
	}

	/**
	 * Binds this soul/vessel to the other being. Does not work for integrated
	 * beings. Returns the previous soul and then the previous vessel in a pair.
	 * 
	 * @param other
	 * @return
	 */
	public Pair<LivingEntity, LivingEntity> bindSoulToVessel(LivingEntity other) {
		if (this.isIntegrated())
			throw new IllegalStateException("This being is Integrated");
		if (this.isSoul()) {
			if (!(other instanceof Mob))
				throw new IllegalStateException("Cannot bind to a non-mob vessel!");
			Pair<LivingEntity, LivingEntity> prevs = bindToVessel((Mob) other);

			return prevs;
		} else {
			return bindSoul(other);
		}
	}

	/**
	 * Only works if this is a VESSEL. binds the given SOUL to this vessel. Returns
	 * soul, vessel pair for previous soul of the vessel and previous vessel of this
	 * soul.
	 * 
	 * @param vessel
	 * @return
	 */
	private Pair<LivingEntity, LivingEntity> bindSoul(LivingEntity soul) {
		Soul soSys = sys(soul);
		LivingEntity preso = this.soul;

		LivingEntity preve = soSys.tether.isCorporeal() ? soSys.tether.getTetherEntity() : null;
		this.soul = soul;
		soSys.tether.corporealTether((Mob) owner);
		return Pair.of(preso, preve);
	}

	/**
	 * Only works if this is a SOUL. binds to the given VESSEL. Returns soul, vessel
	 * pair for previous soul of the vessel and previous vessel of this soul.
	 * 
	 * @param vessel
	 * @return
	 */
	private Pair<LivingEntity, LivingEntity> bindToVessel(Mob vessel) {
		Soul vesSys = sys(vessel);
		LivingEntity preso = vesSys.soul;
		vesSys.soul = this.owner;
		LivingEntity preve = tether.isCorporeal() ? tether.getTetherEntity() : null;
		this.tether.corporealTether(vessel);
		return Pair.of(preso, preve);
	}

	/**
	 * Converts the owning entity into a soul and creates a vessel. If the owning
	 * entity is a vessel, however, then it will remain a vessel but its soul will
	 * be split off. If the entity is already a soul. Returns pair of soul and
	 * vessel with soul first. Gives a null if the soul is noncorporeal or if the
	 * vessel is empty
	 * 
	 * @return
	 */
	public Pair<LivingEntity, LivingEntity> splitSoul() {
		if (soulState == SoulState.INTEGRATED)
			return Pair.of(this.owner, splitIntegratedSoul());
		if (soulState == SoulState.SOUL && tether.getType() == SoulTether.Type.CORPOREAL
				|| soulState == SoulState.VESSEL && soul != null)
			return splitDiscordantSoulAndVessel();
		return null;
	}

	/**
	 * Splits this soul from its (ASSUMED corporeal) vessel, or this vessel from its
	 * soul, ASSUMING THAT THEY ARE NON-INTEGRATED. Returns the soul first, vessel
	 * second in a Pair
	 * 
	 * @return
	 */
	public Pair<LivingEntity, LivingEntity> splitDiscordantSoulAndVessel() {
		if (soulState == SoulState.INTEGRATED || (soulState == SoulState.SOUL && !tether.isCorporeal())
				|| (soulState == SoulState.VESSEL && soul == null)) {
			throw new IllegalStateException("Invalid state for splitting");
		}
		if (soulState == SoulState.SOUL) {
			sys(this.tether.getTetherEntity()).unbindSoul();

			return Pair.of(owner, this.unbindVessel().getTetherEntity());
		}
		if (soulState == SoulState.VESSEL) {
			sys(this.soul).unbindVessel();
			return Pair.of(this.unbindSoul(), owner);
		}
		return null;
	}

	/**
	 * Removes soul from this VESSEL and returns the previous soul
	 * 
	 * @return
	 */
	private LivingEntity unbindSoul() {
		if (soulState != SoulState.VESSEL)
			throw new IllegalStateException("This is a " + soulState + ", not a vessel!");
		LivingEntity so = soul;
		soul = null;
		return so;
	}

	/**
	 * Removes vessel from this SOUL and returns the previous tether
	 * 
	 * @return
	 */
	private SoulTether unbindVessel() {
		if (soulState != SoulState.SOUL)
			throw new IllegalStateException("This is a " + soulState + ", not a soul!");
		SoulTether tether = this.tether;
		this.tether = new SoulTether();
		return tether;
	}

	/**
	 * Splits soul if the owner is integrated and return the vessel, converting the
	 * original entity into the soul
	 * 
	 * @return
	 */
	public Mob splitIntegratedSoul() {
		if (soulState != SoulState.INTEGRATED)
			throw new IllegalStateException("Soul state is " + this.soulState);
		Mob sou = (Mob) owner;
		Mob ves = (Mob) EntityType.create(sou.serializeNBT(), sou.level).orElseThrow();
		ves.setUUID(Mth.createInsecureUUID(ves.getRandom()));
		Soul vesSys = sys(ves);
		Soul souSys = sys(sou);
		vesSys.congruenceID = souSys.congruenceID;

		sou.setHealth(sou.getMaxHealth());
		souSys.soulState = SoulState.SOUL;

		vesSys.soulState = SoulState.VESSEL;
		souSys.onUpdatedStateOfBeing();
		vesSys.onUpdatedStateOfBeing();
		return ves;
	}

	public UUID getCongruenceID() {
		return congruenceID;
	}

	public void update() {
		if (this.soulState == SoulState.SOUL) {
			this.owner.noPhysics = this.soulState.isOnlySoul();
			keepSoulWithVessel();
		}
	}

	public void onUpdatedStateOfBeing() {
		this.changeBeingPhysics();
		if (soulState == SoulState.SOUL) {
			initSoul();
		}
		if (soulState == SoulState.VESSEL) {
			initVessel();
		}

	}

	public SoulState getSoulState() {
		return soulState;
	}

	/**
	 * Gets the entity itself, whether it is a soul or vessel
	 * 
	 * @return
	 */
	public LivingEntity getOwner() {
		return owner;
	}

	public LivingEntity getSoul() {
		if (this.soulState == SoulState.INTEGRATED)
			return owner;
		if (!this.soulState.isOnlyVessel())
			throw new IllegalStateException("Cannot obtain soul as this entity is itself a soul");
		return soul;
	}

	/**
	 * Only checks if a VESSEL has a soul
	 * 
	 * @return
	 */
	public boolean hasSoul() {
		if (this.soulState != SoulState.VESSEL) {
			throw new IllegalStateException(this.owner.getDisplayName().getString() + " is not a vessel");
		}
		return soul != null;
	}

	/**
	 * Only works if this entity is fully a soul, not integrated
	 * 
	 * @return
	 */
	public SoulTether getTether() {
		if (this.soulState.hasVessel())
			throw new IllegalStateException("Cannot obtain tether as this entity is a vessel ");
		return tether;
	}

	public LivingEntity getVessel() {
		if (this.soulState.isOnlySoul())
			return tether.getTetherEntity();
		if (this.soulState == SoulState.INTEGRATED)
			return this.owner;
		throw new IllegalStateException("Cannot obtain vessel as this entity is itself a vessel");
	}

	protected void changeBeingPhysics() {

		owner.setInvulnerable(this.soulState.isOnlySoul());
		if (this.soulState.isOnlySoul()) {
			if (!this.owner.isInvisible()) {
				this.owner.setInvisible(true);
			}

		} else {
			if (this.owner.isInvisible()) {
				this.owner.setInvisible(false);
			}

		}

		if (this.owner.isNoGravity() != this.soulState.isOnlySoul())
			this.owner.setNoGravity(this.soulState.isOnlySoul());

	}

	/**
	 * To ensure this soul-state entity remains in the appropriate place in relation
	 * to its tether
	 */
	protected void keepSoulWithVessel() {

		if (this.tether.isCorporeal()) {
			owner.teleportTo(getVessel().getX(), getVessel().getY(), getVessel().getZ());
			// getVessel().teleportTo(owner.getX(), owner.getY(), owner.getZ());
			if (this.owner instanceof Mob) {
				Mob spirit = (Mob) owner;

				if (spirit.getNavigation().getTargetPos() != null) {
					Path o = spirit.getNavigation().getPath();
					if (o != null) {
						BlockPos targ = o.getTarget();
						System.out.println("" + spirit.getDisplayName().getString() + " attempting to move to " + targ);
						Path p = this.tether.getTetherEntity().getNavigation().createPath(targ,
								(int) o.getDistToTarget() + 100);
						if (p != null && !p.sameAs(tether.getTetherEntity().getNavigation().getPath())) {
							this.tether.getTetherEntity().getNavigation().moveTo(p, 1);
							System.out.println("" + tether.getTetherEntity().getDisplayName().getString()
									+ " vessel attempting to move along path " + p + " to " + p.getTarget());
						}

					}
				}
				tether.getTetherEntity().setXRot(spirit.getXRot());
				tether.getTetherEntity().setYHeadRot(spirit.getYHeadRot());

				tether.getTetherEntity().setYRot(spirit.getYRot());

			}
		}
		if (this.tether.isAstral()) {

		}
		if (this.tether.isUnbound()) {
			// TODO try spawn reaper or something like that
			if (!this.isProtectedSoul) {

			} else {

			}
		}
	}

	/**
	 * Upon changing this mob to a soul, call this
	 */
	protected void initSoul() {
		owner.removeAllEffects();
		owner.clearFire();
		System.out.println("Initialized new soul for " + owner.getDisplayName().getString());
		if (this.tether == null) {
			this.tether = new SoulTether();
		}
		owner.setGlowingTag(true);
		owner.moveTo(owner.getX(), owner.getY() + 3, owner.getZ());
	}

	/**
	 * Upon changing this mob to a Vessel, call this
	 */
	protected void initVessel() {

		if (this.owner instanceof Mob) {
			Mob body = (Mob) this.owner;
			body.goalSelector.removeAllGoals();
			body.targetSelector.removeAllGoals();
			body.getBrain().removeAllBehaviors();
		} else {
			throw new IllegalStateException(
					"Soul state is Vessel but owner is a " + owner.getType().getRegistryName() + " which is not a mob");
		}
	}

	@Override
	public CompoundTag serializeNBT() {
		CompoundTag tag = new CompoundTag();

		tag.putString("State", this.soulState.toString());
		tag.putUUID("Congruence", congruenceID);
		tag.putBoolean("Protected", isProtectedSoul);
		if (soulState.isOnlySoul()) {
			tag.put("Tether", this.tether.serializeNBT());
		}
		if (soulState.isOnlyVessel() && soul != null) {
			tag.putUUID("Soul", this.soul.getUUID());
		}
		return tag;
	}

	@Override
	public void deserializeNBT(CompoundTag nbt) {
		this.soulState = SoulState.valueOf(nbt.getString("State"));
		this.congruenceID = nbt.getUUID("Congruence");
		this.isProtectedSoul = nbt.getBoolean("Protected");
		if (soulState.isOnlySoul()) {
			if (tether == null)
				this.tether = new SoulTether();
			this.tether.deserializeNBT(nbt, (ServerLevel) this.owner.getLevel());
		}
		if (soulState.isOnlyVessel() && nbt.contains("Soul")) {

			this.soul = (LivingEntity) GMDataUtils.fromUUID(nbt.getUUID("Soul"), (ServerLevel) this.owner.getLevel());
		}
	}

	private final LazyOptional<Soul> hol = LazyOptional.of(() -> this);

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {

		return INSTANCE.orEmpty(cap, hol);
	}

	/**
	 * Gets the soul system capability for the given entity
	 * 
	 * @param entity
	 * @return
	 */
	public static Soul sys(Entity entity) {

		return entity.getCapability(INSTANCE).orElseThrow(() -> new RuntimeException(
				"Entity " + entity.getDisplayName().getString() + " has no soul capability"));
	}

	/**
	 * Checks if this entity has a soul system
	 * 
	 * @param entity
	 * @return
	 */
	public static boolean hasSys(Entity entity) {
		return (entity instanceof LivingEntity && !((LivingEntity) entity).isDeadOrDying())
				? entity.getCapability(INSTANCE).isPresent()
				: false;
	}

	public static void attachThisCapEvent(AttachCapabilitiesEvent<Entity> event) {
		if (event.getObject() instanceof LivingEntity && !(event.getObject() instanceof ArmorStand)) {
			LivingEntity e = (LivingEntity) event.getObject();
			Soul sys;
			if (event.getObject() instanceof ServerPlayer) {
				sys = new PlayerSoul((ServerPlayer) e);
			} else {
				sys = new Soul(e);
			}
			event.addCapability(ModCapabilities.SOUL, sys);
		}
	}

	@SubscribeEvent
	public static void joinedWorld(EntityJoinWorldEvent event) {
		if (hasSys(event.getEntity())) {
			sys(event.getEntity()).onUpdatedStateOfBeing();
		}
	}

	@SubscribeEvent
	public static void handleCloneEvent(PlayerEvent.Clone event) {
		if (event.isWasDeath())
			event.getPlayer().getCapability(INSTANCE).orElse(null)
					.deserializeNBT(event.getOriginal().getCapability(INSTANCE).orElse(null).serializeNBT());
	}

	@SubscribeEvent
	public static void livingupdateevent(LivingUpdateEvent event) {
		if (event.getEntityLiving().level.isClientSide() || !hasSys(event.getEntity()))
			return;
		sys(event.getEntityLiving()).update();
	}
}
