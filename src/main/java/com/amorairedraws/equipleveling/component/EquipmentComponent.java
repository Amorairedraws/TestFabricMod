package com.amorairedraws.equipleveling.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.component.ComponentType;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import com.amorairedraws.equipleveling.EquipLevelingMod;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;

import java.util.ArrayList;
import java.util.List;

public class EquipmentComponent {
	public static ComponentType<EquipmentData> EQUIPMENT_TYPE;
	
	public static void register() {
		EQUIPMENT_TYPE = Registry.register(
			Registries.DATA_COMPONENT_TYPE,
			Identifier.of(EquipLevelingMod.MOD_ID, "equipment"),
			ComponentType.builder()
				.codec(EquipmentData.CODEC)
				.cache()
				.build()
		);
	}

	public static class EquipmentData {
		public static final Codec<EquipmentData> CODEC = RecordCodecBuilder.create(instance ->
			instance.group(
				Codec.INT.fieldOf("level").forGetter(d -> d.level),
				Codec.INT.fieldOf("xp").forGetter(d -> d.xp),
				Codec.INT.fieldOf("xp_required").forGetter(d -> d.xpRequired),
				Codec.BOOL.fieldOf("mending").forGetter(d -> d.mending),
				EquipmentSlot.CODEC.listOf().fieldOf("slots").forGetter(d -> d.slots),
				EquipmentSlot.CODEC.listOf().fieldOf("bonus_slots").forGetter(d -> d.bonusSlots),
				Codec.BOOL.fieldOf("ready_to_level_up").forGetter(d -> d.readyToLevelUp),
				Codec.BOOL.fieldOf("broken").forGetter(d -> d.broken),
				Codec.BOOL.fieldOf("maxed").forGetter(d -> d.maxed)
			).apply(instance, EquipmentData::new)
		);

		public int level;
		public int xp;
		public int xpRequired;
		public boolean mending;
		public List<EquipmentSlot> slots;
		public List<EquipmentSlot> bonusSlots;
		public boolean readyToLevelUp;
		public boolean broken;
		public boolean maxed;

		public EquipmentData(int level, int xp, int xpRequired, boolean mending, 
							 List<EquipmentSlot> slots, List<EquipmentSlot> bonusSlots,
							 boolean readyToLevelUp, boolean broken, boolean maxed) {
			this.level = level;
			this.xp = xp;
			this.xpRequired = xpRequired;
			this.mending = mending;
			this.slots = slots;
			this.bonusSlots = bonusSlots;
			this.readyToLevelUp = readyToLevelUp;
			this.broken = broken;
			this.maxed = maxed;
		}

		public static EquipmentData create() {
			return new EquipmentData(0, 0, 
				EquipLevelingConfig.getBaseXpForCategory("default"),
				false, new ArrayList<>(), new ArrayList<>(),
				false, false, false);
		}

		public void addXp(int amount) {
			if (broken || readyToLevelUp || maxed) return;
			this.xp += amount;
			if (this.xp >= this.xpRequired) {
				this.readyToLevelUp = true;
			}
		}

		public void levelUp() {
			this.level++;
			this.xp = 0;
			this.readyToLevelUp = false;
			double multiplier = EquipLevelingConfig.getXpMultiplier();
			this.xpRequired = (int) (EquipLevelingConfig.getBaseXpForCategory("default") * Math.pow(multiplier, level));
		}

		public int getFilledSlots() {
			return (int) slots.stream().filter(s -> s.enchantmentId != null).count();
		}

		public int getTotalSlots() {
			return 4 + bonusSlots.size();
		}

		public EquipmentData copy() {
			return new EquipmentData(level, xp, xpRequired, mending,
				new ArrayList<>(slots), new ArrayList<>(bonusSlots),
				readyToLevelUp, broken, maxed);
		}
	}

	public static class EquipmentSlot {
		public static final Codec<EquipmentSlot> CODEC = RecordCodecBuilder.create(instance ->
			instance.group(
				Codec.STRING.optionalFieldOf("enchantment_id").forGetter(s -> java.util.Optional.ofNullable(s.enchantmentId)),
				Codec.INT.fieldOf("level").forGetter(s -> s.enchantmentLevel)
			).apply(instance, (id, level) -> new EquipmentSlot(id.orElse(null), level))
		);

		public String enchantmentId;
		public int enchantmentLevel;

		public EquipmentSlot(String enchantmentId, int level) {
			this.enchantmentId = enchantmentId;
			this.enchantmentLevel = level;
		}

		public boolean isEmpty() {
			return enchantmentId == null;
		}
	}

	public static EquipmentData getOrCreate(ItemStack stack) {
		if (!stack.contains(EQUIPMENT_TYPE)) {
			stack.set(EQUIPMENT_TYPE, EquipmentData.create());
		}
		return stack.get(EQUIPMENT_TYPE);
	}
}
