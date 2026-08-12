package com.amorairedraws.equipleveling.util;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * Utilities for working with materials (wood, stone, iron, etc.) and their
 * corresponding items.
 */
public final class MaterialHelper {

    private MaterialHelper() {}

    // Known vanilla material name → mining level.
    public static final Map<String, Integer> VANILLA_MINING_LEVELS = Map.of(
            "wood", 0, "wooden", 0, "gold", 0, "golden", 0,
            "stone", 1, "cobblestone", 1,
            "copper", 1,
            "iron", 2,
            "diamond", 3,
            "netherite", 4
    );

    /** Tries to find an item matching a material name + category suffix
     *  (e.g., material="wood" category="sword" → wooden_sword). */
    public static Item findItemForMaterial(String material, String category) {
        if (material == null || material.isBlank() || category == null) return null;
        String mat = material.trim().toLowerCase();

        // Vanilla named items (wooden_sword, stone_pickaxe, etc.)
        String vanillaName = ("wood".equals(mat) || "gold".equals(mat) ? mat + "en" : mat)
                + "_" + suffixForCategory(category);
        Identifier id = Identifier.ofVanilla(vanillaName);
        if (Registries.ITEM.containsId(id)) return Registries.ITEM.get(id);

        // Try the exact material name as-is.
        id = Identifier.ofVanilla(mat + "_" + suffixForCategory(category));
        if (Registries.ITEM.containsId(id)) return Registries.ITEM.get(id);

        return null;
    }

    private static String suffixForCategory(String category) {
        return switch (category) {
            case "sword" -> "sword";
            case "axe" -> "axe";
            case "pickaxe" -> "pickaxe";
            case "shovel" -> "shovel";
            case "hoe" -> "hoe";
            case "fishing_rod" -> "fishing_rod";
            case "bow" -> "bow";
            case "helmet" -> "helmet";
            case "chestplate" -> "chestplate";
            case "leggings" -> "leggings";
            case "boots" -> "boots";
            default -> category;
        };
    }

    /**
     * Auto-detects a material ladder. This now returns the <b>effective</b>
     * ladder: the persisted config ladder merged with the quality-based
     * auto-derived fallback layer (see {@link MaterialTierDeriver}), so the
     * editor's "Auto-Detect from Registry" button shows the full picture.
     */
    public static Map<Integer, List<String>> detectMaterialLadder() {
        return com.amorairedraws.equipleveling.config.EquipLevelingConfig.getMaterialLadder();
    }

    /** Extracts the material name from an item's registry id.
     *  "minecraft:wooden_sword" → "wood", "modid:bronze_pickaxe" → "bronze". */
    public static String extractMaterialName(Item item) {
        String path = Registries.ITEM.getId(item).getPath();
        int separator = path.indexOf('_');
        if (separator < 1) return path;
        String prefix = path.substring(0, separator);
        return "wooden".equalsIgnoreCase(prefix) ? "wood" : prefix;
    }

    /** Guesses the mining level of a material name using known vanilla values. */
    public static int guessMiningLevel(String materialName) {
        if (materialName == null || materialName.isBlank()) return -1;
        Integer known = VANILLA_MINING_LEVELS.get(materialName.trim().toLowerCase());
        return known != null ? known : -1;
    }
}
