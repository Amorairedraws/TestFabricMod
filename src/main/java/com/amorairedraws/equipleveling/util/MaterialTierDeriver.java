package com.amorairedraws.equipleveling.util;

import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Automatically derives a material ladder for <b>modded</b> materials by
 * inspecting the qualities of the items each material defines.
 *
 * <p>The vanilla materials (wood, stone, iron, diamond, netherite) act as fixed
 * anchors. Every other (modded) material is given a single "power score" from
 * the median of its equipment, then either:
 *
 * <ul>
 *   <li><b>grouped</b> with the nearest vanilla anchor (so it shares that
 *       anchor's level and upgrades into the same next tier), or</li>
 *   <li><b>extended</b> to a new level above netherite, spaced by the actual
 *       score gap between diamond and netherite (one "high tier" step).</li>
 * </ul>
 *
 * <p>This layer is intentionally kept <em>separate</em> from the persisted
 * config ladder. {@link EquipLevelingConfig} merges the two at read time so
 * manual edits always win and the derived layer acts as a fallback that is
 * recomputed each session (auto-pruning removed mods, auto-adding new ones).
 */
public final class MaterialTierDeriver {

    private MaterialTierDeriver() {}

    /** Cache of the derived ladder, recomputed on {@link #invalidate()}. */
    private static volatile Map<Integer, List<String>> cached;

    /**
     * Returns the derived ladder (lazily computed and cached). Safe to call from
     * both the server and render threads.
     */
    public static Map<Integer, List<String>> getDerivedLadder() {
        Map<Integer, List<String>> result = cached;
        if (result == null) {
            synchronized (MaterialTierDeriver.class) {
                result = cached;
                if (result == null) {
                    result = derive();
                    cached = result;
                }
            }
        }
        return result;
    }

    /** Drops the cached derivation. Call after the config or item registry changes. */
    public static void invalidate() {
        synchronized (MaterialTierDeriver.class) {
            cached = null;
        }
    }

    /** Computes a fresh derived ladder from the current item registry. */
    public static Map<Integer, List<String>> derive() {
        // ---- 1. Anchor scores from vanilla items (deterministic, always present). ----
        double woodScore = anchorScore("wood");
        double stoneScore = anchorScore("stone");
        double ironScore = anchorScore("iron");
        double diamondScore = anchorScore("diamond");
        double netheriteScore = anchorScore("netherite");

        double gap = netheriteScore - diamondScore;
        if (gap < 1.0) gap = 100.0;

        Map<String, Integer> anchorLevels = anchorLevelsFromConfig();

        // ---- 2. Group non-vanilla equipment by material + categories. ----
        Map<String, List<Item>> materialItems = new LinkedHashMap<>();
        Map<String, Set<String>> materialCategories = new LinkedHashMap<>();
        for (Item item : Registries.ITEM) {
            Identifier id = Registries.ITEM.getId(item);
            if ("minecraft".equals(id.getNamespace())) continue;
            String material = MaterialHelper.extractMaterialName(item);
            if (material == null || material.isBlank()) continue;
            ItemStack stack = new ItemStack(item);
            String category = EquipmentCategory.getCategory(stack);
            if (category == null) continue;
            materialItems.computeIfAbsent(material, k -> new ArrayList<>()).add(item);
            materialCategories.computeIfAbsent(material, k -> new LinkedHashSet<>()).add(category);
        }

        // ---- 3. Score each tiered material and assign a level. ----
        Map<Integer, List<String>> result = new java.util.TreeMap<>();
        for (Map.Entry<String, List<Item>> e : materialItems.entrySet()) {
            String material = e.getKey();
            // A material is "tiered" when it spans at least two equipment
            // categories (a tool/armour set). Single items (bow, fishing rod,
            // elytra, shield, ...) are excluded so they don't pollute the ladder.
            if (materialCategories.getOrDefault(material, Set.of()).size() < 2) continue;
            // Never emit vanilla anchor names (they live in the config layer).
            if (isAnchor(material)) continue;

            Double score = materialScore(e.getValue());
            if (score == null) continue;

            int level = assignLevel(score, anchorLevels,
                    woodScore, stoneScore, ironScore, diamondScore, netheriteScore, gap);
            result.computeIfAbsent(level, k -> new ArrayList<>()).add(material);
        }

        for (List<String> mats : result.values()) Collections.sort(mats);
        return result;
    }

    // ================================================================== //
    // Scoring                                                             //
    // ================================================================== //

    /** Composite power score for one item, dominated by durability with
     *  secondary terms for mining speed, attack damage and armour. */
    private static double itemScore(ItemStack stack) {
        double score = stack.getMaxDamage();

        ToolComponent tool = stack.get(DataComponentTypes.TOOL);
        if (tool != null) {
            score += 30.0 * tool.defaultMiningSpeed();
        }

        AttributeModifiersComponent mods = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (mods != null) {
            for (AttributeModifiersComponent.Entry entry : mods.modifiers()) {
                double value = entry.modifier().value();
                if (entry.attribute() == EntityAttributes.ATTACK_DAMAGE) {
                    score += 40.0 * value;
                } else if (entry.attribute() == EntityAttributes.ARMOR) {
                    score += 40.0 * value;
                } else if (entry.attribute() == EntityAttributes.ARMOR_TOUGHNESS) {
                    score += 20.0 * value;
                }
            }
        }
        return score;
    }

    /** Median power score across all of a material's equipment, pruning outliers. */
    private static Double materialScore(List<Item> items) {
        List<Double> scores = new ArrayList<>();
        for (Item item : items) {
            double s = itemScore(new ItemStack(item));
            if (s > 0) scores.add(s);
        }
        if (scores.isEmpty()) return null;
        return medianWithPruning(scores);
    }

    /** Score for a vanilla anchor material, drawn only from minecraft items. */
    private static double anchorScore(String material) {
        List<Double> scores = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            Identifier id = Registries.ITEM.getId(item);
            if (!"minecraft".equals(id.getNamespace())) continue;
            if (!material.equals(MaterialHelper.extractMaterialName(item))) continue;
            ItemStack stack = new ItemStack(item);
            if (!EquipmentCategory.isEquipment(stack)) continue;
            double s = itemScore(stack);
            if (s > 0) scores.add(s);
        }
        if (scores.isEmpty()) {
            // Defensive fallback (vanilla anchors always exist, so this is
            // effectively dead code): preserve canonical ordering.
            return switch (material) {
                case "wood" -> 1; case "stone" -> 2; case "iron" -> 3;
                case "diamond" -> 4; default -> 5; // netherite
            };
        }
        return medianWithPruning(scores);
    }

    /** Median of the values after dropping far-out outliers (IQR-based). */
    private static double medianWithPruning(List<Double> values) {
        Collections.sort(values);
        double median = percentile(values, 50);
        double q1 = percentile(values, 25);
        double q3 = percentile(values, 75);
        double iqr = q3 - q1;

        // When scores are tightly clustered (iqr == 0), any deviation is
        // suspicious; use a relative threshold instead. This drops a lone
        // "14" sitting among a material whose real tools all score 5-7.
        double threshold = iqr > 0 ? 2.5 * iqr : Math.abs(median) * 0.6;

        List<Double> pruned = new ArrayList<>();
        for (double v : values) {
            if (Math.abs(v - median) <= threshold) pruned.add(v);
        }
        return pruned.isEmpty() ? median : percentile(pruned, 50);
    }

    private static double percentile(List<Double> sorted, double p) {
        double idx = p / 100.0 * (sorted.size() - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) return sorted.get(lo);
        double frac = idx - lo;
        return sorted.get(lo) * (1 - frac) + sorted.get(hi) * frac;
    }

    // ================================================================== //
    // Level assignment                                                    //
    // ================================================================== //

    private static int assignLevel(double score, Map<String, Integer> anchorLevels,
            double wood, double stone, double iron, double diamond, double netherite, double gap) {
        int netheriteLevel = anchorLevels.getOrDefault("netherite", 4);

        // Genuinely above netherite: extend the ladder by actual score gaps.
        if (score >= netherite + 0.5 * gap) {
            int steps = (int) Math.round((score - netherite) / gap);
            if (steps < 1) steps = 1;
            return netheriteLevel + steps;
        }

        // Otherwise group with the nearest vanilla anchor.
        int bestLevel = anchorLevels.getOrDefault("wood", 0);
        double bestDist = Double.MAX_VALUE;
        double[][] anchors = {
                { anchorLevels.getOrDefault("wood", 0), wood },
                { anchorLevels.getOrDefault("stone", 1), stone },
                { anchorLevels.getOrDefault("iron", 2), iron },
                { anchorLevels.getOrDefault("diamond", 3), diamond },
                { netheriteLevel, netherite },
        };
        for (double[] a : anchors) {
            double dist = Math.abs(score - a[1]);
            if (dist < bestDist) {
                bestDist = dist;
                bestLevel = (int) a[0];
            }
        }
        return bestLevel;
    }

    private static Map<String, Integer> anchorLevelsFromConfig() {
        Map<String, Integer> levels = new HashMap<>();
        for (Map.Entry<Integer, List<String>> e
                : EquipLevelingConfig.getConfiguredMaterialLadder().entrySet()) {
            for (String material : e.getValue()) {
                levels.putIfAbsent(material.trim().toLowerCase(), e.getKey());
            }
        }
        levels.putIfAbsent("wood", 0);
        levels.putIfAbsent("stone", 1);
        levels.putIfAbsent("iron", 2);
        levels.putIfAbsent("diamond", 3);
        levels.putIfAbsent("netherite", 4);
        return levels;
    }

    private static boolean isAnchor(String material) {
        return switch (material.trim().toLowerCase()) {
            case "wood", "wooden", "stone", "iron", "diamond", "netherite" -> true;
            default -> false;
        };
    }
}
