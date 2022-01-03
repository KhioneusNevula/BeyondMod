package com.gm910.beyondmod.spirit;

import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.ObjectUtils.Null;

import com.gm910.beyondmod.GMDataUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

public class SoulTether {

	public static class Type<T> {
		private static final Map<String, Type<?>> all = new TreeMap<>();
		/**
		 * A cord tether; no control over the vessel but the soul can move freely in
		 * surrounding space
		 */
		public static final Type<GlobalPos> ASTRAL = new Type<>(GlobalPos.class, "Astral");

		/**
		 * Bodily tether; a vessel to control
		 */
		public static final Type<Mob> CORPOREAL = new Type<>(Mob.class, "Corporeal");
		/**
		 * No tether; this soul is to be reaped but may have protection
		 */
		public static final Type<Null> UNBOUND = new Type<>(Null.class, "Unbound");
		Class<T> clazz;
		String name;

		private Type(Class<T> clazz, String name) {
			this.clazz = clazz;
			this.name = name;
			all.put(name, this);
		}

		@Override
		public String toString() {
			return name;
		}

		public static Type<?> fromString(String name) {
			return all.getOrDefault(name, UNBOUND);
		}
	}

	private Type<?> type;
	private GlobalPos tetherPos;
	private Mob tetherEntity;

	/**
	 * Creates a corporeal soul tether
	 * 
	 * @param entity
	 */
	public SoulTether(Mob entity) {
		this.corporealTether(entity);
	}

	/**
	 * Creates an astral soul tether
	 * 
	 * @param pos
	 */
	public SoulTether(GlobalPos pos) {
		this.astralTether(pos);
	}

	/**
	 * Creates an unbound soul tether
	 */
	public SoulTether() {
		this.unbindTether();
	}

	public Type<?> getType() {
		return type;
	}

	public void corporealTether(Mob tetherEntity) {
		this.type = Type.CORPOREAL;
		this.tetherEntity = tetherEntity;
		this.tetherPos = null;
	}

	public void astralTether(GlobalPos pos) {
		this.type = Type.ASTRAL;
		this.tetherPos = pos;
		this.tetherEntity = null;
	}

	public void astralTether(ResourceKey<Level> level, BlockPos pos) {
		this.astralTether(GlobalPos.of(level, pos));
	}

	public boolean isAstral() {
		return this.type == Type.ASTRAL;
	}

	public boolean isCorporeal() {
		return this.type == Type.CORPOREAL;
	}

	public boolean isUnbound() {
		return this.type == Type.UNBOUND;
	}

	public void unbindTether() {
		this.type = Type.UNBOUND;
		this.tetherEntity = null;
		this.tetherPos = null;
	}

	public Object getTether() {
		return getTether(this.type);
	}

	public <T> T getTether(Type<T> type) {
		if (type == Type.ASTRAL)
			return (T) this.tetherPos;
		else if (type == Type.CORPOREAL)
			return (T) this.tetherEntity;
		return null;
	}

	public Mob getTetherEntity() {
		if (this.type != Type.CORPOREAL)
			throw new IllegalStateException("Cannot access tethered entity when tether is " + type);
		return tetherEntity;
	}

	public GlobalPos getTetherPos() {
		if (this.type != Type.ASTRAL)
			throw new IllegalStateException("Cannot access tethered entity when tether is " + type);
		return tetherPos;
	}

	public CompoundTag serializeNBT() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Type", type.toString());

		if (type == Type.CORPOREAL) {
			tag.putUUID("Tether", this.tetherEntity.getUUID());
		}
		if (type == Type.ASTRAL) {
			CompoundTag pos = new CompoundTag();
			pos.put("Pos", NbtUtils.writeBlockPos(tetherPos.pos()));
			pos.putString("Dim", tetherPos.dimension().location().toString());
			tag.put("Tether", pos);
		}

		return tag;
	}

	public void deserializeNBT(CompoundTag nbt, ServerLevel level) {
		this.type = Type.fromString(nbt.getString("Type"));
		if (type == Type.CORPOREAL) {
			this.tetherEntity = (Mob) GMDataUtils.fromUUID(nbt.getUUID("Tether"), level);
		}

		if (type == Type.ASTRAL) {
			CompoundTag pos = nbt.getCompound("Tether");
			tetherPos = GlobalPos.of(ResourceKey.elementKey(Registry.DIMENSION_REGISTRY)
					.apply(new ResourceLocation(pos.getString("Dim"))), NbtUtils.readBlockPos(pos.getCompound("Pos")));
		}
	}

}
