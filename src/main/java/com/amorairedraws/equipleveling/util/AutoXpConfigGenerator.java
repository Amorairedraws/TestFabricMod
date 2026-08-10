package com.amorairedraws.equipleveling.util;

import net.minecraft.block.*;
import net.minecraft.registry.tag.BlockTags;

import java.util.*;

/**
 * Generates sensible default XP values for every block.
 *
 * <p>Mining formula: {@code (miningLevel + 1)² × 2}.
 * Stone-type blocks (mining level 0) are clamped to 1 XP.
 * Ancient debris is manually set to 200 XP.
 * Logs: flat 4 XP.
 * Crops: scaled by max age stages.
 */
public final class AutoXpConfigGenerator {

    private AutoXpConfigGenerator() {}

    /**
     * Generates default XP values for ALL registered blocks.
     */
    public static Map<String, Integer> generateAllDefaults() {
        Map<String, Integer> map = new LinkedHashMap<>();

        for (Block block : net.minecraft.registry.Registries.BLOCK) {
            String id = net.minecraft.registry.Registries.BLOCK.getId(block).toString();
            BlockState state = block.getDefaultState();

            // Pickaxe blocks (ores + stone)
            if (state.isIn(BlockTags.PICKAXE_MINEABLE)) {
                int xp = calculatePickaxeXp(state);
                if (xp > 0) map.put(id, xp);
            }

            // Axe blocks (logs)
            if (state.isIn(BlockTags.AXE_MINEABLE) && state.isIn(BlockTags.LOGS)) {
                map.put(id, 4);
            }

            // Crops
            if (state.getBlock() instanceof CropBlock || state.isIn(BlockTags.CROPS)
                    || state.isOf(Blocks.NETHER_WART)) {
                int xp = calculateCropXp(state);
                if (xp > 0) map.put(id, xp);
            }
        }

        // Shovel blocks
        map.put("minecraft:dirt", 1);
        map.put("minecraft:coarse_dirt", 1);
        map.put("minecraft:rooted_dirt", 1);
        map.put("minecraft:sand", 1);
        map.put("minecraft:red_sand", 1);
        map.put("minecraft:gravel", 1);
        map.put("minecraft:snow_block", 1);
        map.put("minecraft:snow", 1);
        map.put("minecraft:clay", 5);

        // Ancient debris manually set to 200 (way too rare for the formula's 14)
        map.put("minecraft:ancient_debris", 200);

        return Collections.unmodifiableMap(map);
    }

    // ------------------------------------------------------------------ //
    // Formulas                                                            //
    // ------------------------------------------------------------------ //

    /**
     * Mining XP: {@code (miningLevel + 1)² × 2}.
     * Uses vanilla block tags to determine mining level.
     * Stone-type blocks are clamped to 1.
     */
    public static int calculatePickaxeXp(BlockState state) {
        int miningLevel = getMiningLevel(state);

        // Stone-type blocks: mining level 0 = baseline 1 XP
        if (miningLevel == 0) return 1;

        int result = (miningLevel + 1) * (miningLevel + 1) * 2;
        return Math.max(1, result);
    }

    /**
     * Crop XP: scaled by max age. Crops with more growth stages (wheat=7)
     * give more XP than quick crops (carrots=3).
     */
    public static int calculateCropXp(BlockState state) {
        if (state.isOf(Blocks.NETHER_WART)) return 7;

        int maxAge = 4; // default
        if (state.getBlock() instanceof CropBlock crop) {
            maxAge = crop.getMaxAge();
        }

        // Simple scaling: base 3 XP, +1 per 2 extra stages beyond 4
        int xp = 3 + Math.max(0, (maxAge - 4) / 2);
        return Math.max(1, Math.min(15, xp));
    }

    /**
     * Determines mining level from vanilla block tags.
     * 0 = wood/gold/stone-type, 1 = stone, 2 = iron, 3 = diamond, 4 = netherite.
     */
    private static int getMiningLevel(BlockState state) {
        if (state.isIn(BlockTags.NEEDS_DIAMOND_TOOL)) return 3;
        if (state.isIn(BlockTags.NEEDS_IRON_TOOL)) return 2;
        if (state.isIn(BlockTags.NEEDS_STONE_TOOL)) return 1;
        return 0; // wood-tier or no tool requirement
    }
}
