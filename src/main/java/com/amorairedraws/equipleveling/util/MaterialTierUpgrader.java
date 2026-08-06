package com.amorairedraws.equipleveling.util;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Vanilla material ladder implementation used by the legendary offer. */
public final class MaterialTierUpgrader {
    private MaterialTierUpgrader() {}

    public static ItemStack promote(ItemStack old, String category, String[] ladder) {
        if (ladder == null || ladder.length < 2) return old;
        String current = material(old.getItem());
        int index = indexOf(ladder, current);
        if (index < 0 || index + 1 >= ladder.length) return old;
        Item next = itemFor(category, ladder[index + 1]);
        if (next == null) return old;
        ItemStack result = new ItemStack(next, old.getCount());
        result.applyComponentsFrom(old.getComponents());
        result.setDamage(0);
        return result;
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) if (values[i].equalsIgnoreCase(value)) return i;
        return -1;
    }

    private static String material(Item item) {
        String id = net.minecraft.registry.Registries.ITEM.getId(item).getPath();
        for (String tier : new String[]{"wood", "stone", "iron", "diamond", "netherite"})
            if (id.startsWith(tier + "_") || id.equals(tier)) return tier;
        return "";
    }

    private static Item itemFor(String category, String tier) {
        String t = tier.toLowerCase();
        return switch (category) {
            case "sword" -> switch (t) { case "wood" -> Items.WOODEN_SWORD; case "stone" -> Items.STONE_SWORD; case "iron" -> Items.IRON_SWORD; case "diamond" -> Items.DIAMOND_SWORD; case "netherite" -> Items.NETHERITE_SWORD; default -> null; };
            case "axe" -> switch (t) { case "wood" -> Items.WOODEN_AXE; case "stone" -> Items.STONE_AXE; case "iron" -> Items.IRON_AXE; case "diamond" -> Items.DIAMOND_AXE; case "netherite" -> Items.NETHERITE_AXE; default -> null; };
            case "pickaxe" -> switch (t) { case "wood" -> Items.WOODEN_PICKAXE; case "stone" -> Items.STONE_PICKAXE; case "iron" -> Items.IRON_PICKAXE; case "diamond" -> Items.DIAMOND_PICKAXE; case "netherite" -> Items.NETHERITE_PICKAXE; default -> null; };
            case "shovel" -> switch (t) { case "wood" -> Items.WOODEN_SHOVEL; case "stone" -> Items.STONE_SHOVEL; case "iron" -> Items.IRON_SHOVEL; case "diamond" -> Items.DIAMOND_SHOVEL; case "netherite" -> Items.NETHERITE_SHOVEL; default -> null; };
            case "hoe" -> switch (t) { case "wood" -> Items.WOODEN_HOE; case "stone" -> Items.STONE_HOE; case "iron" -> Items.IRON_HOE; case "diamond" -> Items.DIAMOND_HOE; case "netherite" -> Items.NETHERITE_HOE; default -> null; };
            default -> null;
        };
    }
}
