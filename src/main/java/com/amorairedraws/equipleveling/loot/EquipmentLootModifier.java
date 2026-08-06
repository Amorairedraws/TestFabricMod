package com.amorairedraws.equipleveling.loot;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.entry.RegistryEntry;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.util.EquipmentCategory;

import java.util.ArrayList;
import java.util.List;

public class EquipmentLootModifier implements LootTableEvents.Modify {

	@Override
	public void modifyLootTable(LootTable.Builder supplier, net.minecraft.loot.LootManager lootManager, 
							   net.minecraft.util.Identifier id, net.minecraft.loot.LootContextParameterSet paramSet,
							   net.minecraft.loot.context.LootContext.Builder contextBuilder) {
		// This would be implemented with loot pool manipulation
		// For now, we'll handle enchanted item conversion in a post-processing step
	}

	public static ItemStack processLootItem(ItemStack stack) {
		// Skip enchanted books entirely
		if (stack.getItem() == Items.ENCHANTED_BOOK) {
			return ItemStack.EMPTY;
		}

		// Check if it's equipment
		if (!EquipmentCategory.isEquipment(stack)) {
			return stack;
		}

		// Convert vanilla enchantments to bonus slots
		List<EnchantmentLevelEntry> enchantments = stack.getEnchantments().getEnchantmentEntries().stream()
			.toList();
		
		if (enchantments.isEmpty()) {
			return stack;
		}

		// Create equipment component if not present
		EquipmentComponent.EquipmentData data = EquipmentComponent.getOrCreate(stack);
		
		// Convert vanilla enchantments to bonus slots (up to 2)
		List<EquipmentComponent.EquipmentSlot> bonusSlots = new ArrayList<>();
		for (int i = 0; i < Math.min(2, enchantments.size()); i++) {
			EnchantmentLevelEntry entry = enchantments.get(i);
			String enchId = entry.holder().value().getTranslationKey();
			bonusSlots.add(new EquipmentComponent.EquipmentSlot(enchId, entry.level()));
		}
		
		data.bonusSlots = bonusSlots;
		
		// Remove vanilla enchantments
		stack.removeSubNbt("Enchantments");
		stack.set(EquipmentComponent.EQUIPMENT_TYPE, data);
		
		return stack;
	}
}
