package com.amorairedraws.equipleveling.util;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

/**
 * Material-name utilities shared by the material-ladder derivation and the
 * legendary-upgrade promotion logic.
 */
public final class MaterialHelper {

    private MaterialHelper() {}

    /** Equipment-category suffixes, longest first so multi-part names strip correctly. */
    private static final String[] CATEGORY_SUFFIXES = {
            "fishing_rod", "chestplate", "leggings", "pickaxe", "crossbow",
            "shovel", "helmet", "boots", "sword", "axe", "hoe", "bow"
    };

    /** Extracts the material name from an item's registry id.
     *  "minecraft:wooden_sword" \u2192 "wood", "modid:bronze_pickaxe" \u2192 "bronze". */
    public static String extractMaterialName(Item item) {
        if (item == null) return null;
        String path = Registries.ITEM.getId(item).getPath();
        String stripped = stripCategorySuffix(path);
        if (stripped.isEmpty()) return path;
        return switch (stripped) {
            case "wooden" -> "wood";
            case "golden" -> "gold";
            default -> stripped;
        };
    }

    /** Removes a known equipment-category suffix from an item path, if present. */
    private static String stripCategorySuffix(String path) {
        if (path == null) return null;
        for (String suffix : CATEGORY_SUFFIXES) {
            String marker = "_" + suffix;
            if (path.endsWith(marker)) {
                return path.substring(0, path.length() - marker.length());
            }
        }
        return path;
    }

    /** Formats a material name for display: "rose_gold" -> "Rose Gold", "copper" -> "Copper". */
    public static String displayName(String material) {
        if (material == null || material.isBlank()) return material;
        StringBuilder sb = new StringBuilder();
        for (String word : material.trim().toLowerCase().split("_")) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }
}
