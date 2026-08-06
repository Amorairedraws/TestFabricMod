package com.amorairedraws.equipleveling.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.item.ItemStack;

import com.amorairedraws.equipleveling.component.EquipmentComponent;

public class BrokenItemRenderer {

	public void register() {
		// Register item color provider for broken items
		ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
			if (stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
				EquipmentComponent.EquipmentData data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
				if (data.broken && tintIndex == 0) {
					// Apply red tint
					return 0xFF5555; // Light red
				}
			}
			return 0xFFFFFF; // White (default)
		}, net.minecraft.item.Items.IRON_SWORD, 
		   net.minecraft.item.Items.DIAMOND_SWORD,
		   net.minecraft.item.Items.NETHERITE_SWORD,
		   net.minecraft.item.Items.WOODEN_SWORD,
		   net.minecraft.item.Items.STONE_SWORD,
		   net.minecraft.item.Items.GOLDEN_SWORD);
	}
}
