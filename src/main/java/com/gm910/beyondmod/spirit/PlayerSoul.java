package com.gm910.beyondmod.spirit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public class PlayerSoul extends Soul {

	private ServerPlayer owner;

	public PlayerSoul(ServerPlayer e) {
		super(e);
		this.owner = e;
		// TODO Auto-generated constructor stub
	}

	@Override
	public void deserializeNBT(CompoundTag nbt) {
		// TODO Auto-generated method stub
		super.deserializeNBT(nbt);
	}

	@Override
	public CompoundTag serializeNBT() {
		// TODO Auto-generated method stub
		return super.serializeNBT();
	}

	@Override
	protected void changeBeingPhysics() {
		super.changeBeingPhysics();
		if (this.soulState.isOnlySoul()) {
			owner.getAbilities().mayfly = true;
			owner.getAbilities().flying = true;
			owner.getAbilities().instabuild = false;
			owner.getAbilities().mayBuild = false;
		} else {
			owner.gameMode.getGameModeForPlayer().updatePlayerAbilities(owner.getAbilities());

		}
	}

	@Override
	protected void keepSoulWithVessel() {
		super.keepSoulWithVessel();
		if (tether.isCorporeal()) {
			this.getOwner().setCamera(tether.getTetherEntity());
		}
	}

	@Override
	public void onUpdatedStateOfBeing() {
		super.onUpdatedStateOfBeing();
		if (this.soulState != SoulState.SOUL) {
			getOwner().setCamera(owner);
		}
	}

	@Override
	public ServerPlayer getOwner() {
		// TODO Auto-generated method stub
		return (ServerPlayer) super.getOwner();
	}

}
