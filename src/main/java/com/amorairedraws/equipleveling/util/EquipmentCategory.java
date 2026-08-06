package com.amorairedraws.equipleveling.util;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

public class EquipmentCategory {
	
	public static String getCategory(ItemStack stack) {
		if (stack.isEmpty()) return null;

		// Check armor
		if (stack.isIn(ItemTags.HEAD_ARMOR)) return "helmet";
		if (stack.isIn(ItemTags.CHEST_ARMOR)) return "chestplate";
		if (stack.isIn(ItemTags.LEG_ARMOR)) return "leggings";
		if (stack.isIn(ItemTags.FOOT_ARMOR)) return "boots";

		// Check swords and axes
		if (stack.isIn(ItemTags.SWORDS)) return "sword";
		if (stack.isIn(ItemTags.AXES)) return "axe";

		// Check tools
		if (stack.isIn(ItemTags.PICKAXES)) return "pickaxe";
		if (stack.isIn(ItemTags.SHOVELS)) return "shovel";
		if (stack.isIn(ItemTags.HOES)) return "hoe";

		// Check fishing rod
		if (stack.getItem() == Items.FISHING_ROD) return "fishing_rod";

		// Check for modded equipment via tag
		if (stack.isIn(net.minecraft.registry.tag.TagKey.of(
			net.minecraft.registry.Registries.ITEM.getKey(),
			net.minecraft.util.Identifier.of("equip_leveling", "upgradeable_equipment")
		))) {
			return "default";
		}

		return null;
	}

	public static boolean isEquipment(ItemStack stack) {
		return getCategory(stack) != null;
	}
}
