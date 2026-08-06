package com.amorairedraws.equipleveling.screen;

import com.amorairedraws.equipleveling.component.EquipmentComponent;

public sealed class EquipmentEnchantingOffer permits EquipmentEnchantingOffer.NewEnchantment, 
	EquipmentEnchantingOffer.Upgrade, EquipmentEnchantingOffer.LegendaryUpgrade {

	public static final class NewEnchantment extends EquipmentEnchantingOffer {
		public String enchantmentId;
		public int level = 1;

		public NewEnchantment() {
			this("minecraft:unbreaking");
		}

		public NewEnchantment(String enchantmentId) {
			this.enchantmentId = enchantmentId;
		}
	}

	public static final class Upgrade extends EquipmentEnchantingOffer {
		public EquipmentComponent.EquipmentSlot slot;

		public Upgrade(EquipmentComponent.EquipmentSlot slot) {
			this.slot = slot;
		}
	}

	public static final class LegendaryUpgrade extends EquipmentEnchantingOffer {
		public String nextMaterial;
	}
}
