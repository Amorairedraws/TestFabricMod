package com.amorairedraws.equipleveling.util;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

public class EquipmentCategory {
    private static net.minecraft.registry.tag.TagKey<net.minecraft.item.Item> tag(String path) {
        return net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.Registries.ITEM.getKey(),
                net.minecraft.util.Identifier.of("equip_leveling", path));
    }
	
	public static String getCategory(ItemStack stack) {
		if (stack.isEmpty()) return null;

		// Check armor
		if (stack.isIn(ItemTags.HEAD_ARMOR)) return "helmet";
		if (stack.isIn(ItemTags.CHEST_ARMOR)) return "chestplate";
		if (stack.isIn(ItemTags.LEG_ARMOR)) return "leggings";
		if (stack.isIn(ItemTags.FOOT_ARMOR)) return "boots";

		// Check the mod-owned category tags first.  They include vanilla tags and
		// are extensible by datapacks, so modded equipment does not need Java code.
		if (stack.isIn(tag("swords")) || stack.isIn(ItemTags.SWORDS)) return "sword";
		if (stack.isIn(tag("axes")) || stack.isIn(ItemTags.AXES)) return "axe";

		// Check tools
		if (stack.isIn(tag("pickaxes")) || stack.isIn(ItemTags.PICKAXES)) return "pickaxe";
		if (stack.isIn(tag("shovels")) || stack.isIn(ItemTags.SHOVELS)) return "shovel";
		if (stack.isIn(tag("hoes")) || stack.isIn(ItemTags.HOES)) return "hoe";

		// Check fishing rod
		if (stack.isIn(tag("fishing_rods")) || stack.getItem() == Items.FISHING_ROD) return "fishing_rod";

		// Check bow / crossbow. There is no dedicated ItemTags.BOWS field, so we
		// rely on the vanilla enchantable/bow tag (which covers the bow) plus a
		// mod-owned tag that can be extended by datapacks to include crossbows
		// and modded ranged weapons.
		if (stack.isIn(tag("bows")) || stack.isIn(ItemTags.BOW_ENCHANTABLE)
				|| stack.getItem() == Items.BOW || stack.getItem() == Items.CROSSBOW) return "bow";

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
