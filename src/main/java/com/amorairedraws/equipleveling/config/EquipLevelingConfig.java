package com.amorairedraws.equipleveling.config;

import java.util.*;

/**
 * Central configuration values for the Equip Leveling mod.
 *
 * <p>This class holds the values (with getters/setters and validation); all
 * file/JSON handling lives in {@link ConfigSerializer}.</p>
 *
 * <h3>Multiplier hierarchy</h3>
 * <pre>
 *   effectiveXp = baseXp &#215; globalGainMultiplier &#215; sourceMultiplier[mob|mining|farming|wood|fishing]
 *   levelRequirement(n) = levelRequirement(n-1) &#215; levelRequirementGrowth
 * </pre>
 */
public class EquipLevelingConfig {

    // ================================================================== //
    // Fields                                                              //
    // ================================================================== //

    // Base XP per category (how much total XP an item needs for its first level-up)
    static Map<String, Integer> baseXp = new LinkedHashMap<>();

    // Level-up requirement growth (was "xpMultiplier" in older versions).
    static double levelRequirementGrowth = 1.2;

    // Global XP gain multiplier — multiplies ALL XP from ALL sources.
    static double globalXpGainMultiplier = 1.0;

    // Per-source XP multipliers. Keys: mob, livestock, mining, farming, wood, fishing.
    static Map<String, Double> sourceMultipliers = new LinkedHashMap<>();

    // Display threshold — the XP message only appears for gains >= this.
    static int xpDisplayThreshold = 10;

    // Durability restored on level-up, as a percentage of max durability.
    static int durabilityRestorePercent = 25;

    // Flat durability restored by a regular Repair Kit (crafting-grid repair).
    static int repairKitRestoreAmount = 100;
    // Percentage of max durability restored by a Diamond Repair Kit.
    static int diamondRepairKitRestorePercent = 50;

    // Reroll costs indexed by number of filled standard slots (0..4).
    static int[] rerollCosts = {5, 10, 15, 20, 25};

    // Probability a visit to the enchanting table offers a legendary upgrade.
    static double legendaryUpgradeProbability = 0.05;

    // Mining-level → materials map for the material ladder.
    static Map<Integer, List<String>> materialLadder = new LinkedHashMap<>();

    // Cached effective ladder: the persisted ladder merged with the auto-derived
    // fallback layer. Rebuilt lazily and dropped whenever either layer changes.
    static volatile Map<Integer, List<String>> effectiveLadderCache = null;

    // Offer weights for the enchanting table.
    static double upgradeWeight = 0.6;
    static double newSlotWeight = 0.4;
    static double legendaryWeight = 0.05;

    // Toggles.
    static boolean keepEquipOnDeath = false;
    static boolean enableBrokenMechanic = true;

    // Custom block ID → XP overrides (takes priority over formula).
    static Map<String, Integer> customBlockXp = new LinkedHashMap<>();

    // Max enchantment slots per category.
    static Map<String, Integer> maxSlots = new LinkedHashMap<>();

    // ================================================================== //
    // Static initialiser                                                  //
    // ================================================================== //

    static {
        initDefaults();
    }

    static void initDefaults() {
        // Base XP per category
        baseXp.put("sword", 400);
        baseXp.put("axe", 450);
        baseXp.put("pickaxe", 500);
        baseXp.put("shovel", 300);
        baseXp.put("hoe", 200);
        baseXp.put("fishing_rod", 300);
        baseXp.put("bow", 350);
        baseXp.put("helmet", 350);
        baseXp.put("chestplate", 400);
        baseXp.put("leggings", 400);
        baseXp.put("boots", 350);
        baseXp.put("default", 350);
        baseXp.put("elytra", 350);
        baseXp.put("shield", 300);

        // Per-source multipliers (all start at 1.0)
        sourceMultipliers.put("mob", 1.0);
        sourceMultipliers.put("livestock", 1.0);
        sourceMultipliers.put("mining", 1.0);
        sourceMultipliers.put("farming", 1.0);
        sourceMultipliers.put("wood", 1.0);
        sourceMultipliers.put("fishing", 1.0);

        // Material ladder defaults. Gold and copper are intentionally NOT listed:
        // they are auto-derived from the item registry (gold → wood tier, copper
        // → stone tier) so they are recognised without being hard-coded.
        materialLadder.put(0, new ArrayList<>(List.of("wood")));
        materialLadder.put(1, new ArrayList<>(List.of("stone")));
        materialLadder.put(2, new ArrayList<>(List.of("iron")));
        materialLadder.put(3, new ArrayList<>(List.of("diamond")));
        materialLadder.put(4, new ArrayList<>(List.of("netherite")));

        // Max slots per category
        maxSlots.put("sword", 4);
        maxSlots.put("axe", 3);
        maxSlots.put("pickaxe", 3);
        maxSlots.put("shovel", 2);
        maxSlots.put("hoe", 2);
        maxSlots.put("fishing_rod", 3);
        maxSlots.put("bow", 3);
        maxSlots.put("helmet", 4);
        maxSlots.put("chestplate", 4);
        maxSlots.put("leggings", 4);
        maxSlots.put("boots", 4);
        maxSlots.put("default", 4);
        maxSlots.put("elytra", 4);
        maxSlots.put("shield", 2);
    }

    // ================================================================== //
    // Validation                                                          //
    // ================================================================== //

    static void validate() {
        levelRequirementGrowth = clamp(levelRequirementGrowth, 1.0, 10.0);
        globalXpGainMultiplier = clamp(globalXpGainMultiplier, 0.1, 10.0);
        xpDisplayThreshold = Math.max(0, xpDisplayThreshold);
        durabilityRestorePercent = clamp(durabilityRestorePercent, 0, 100);
        repairKitRestoreAmount = clamp(repairKitRestoreAmount, 0, 10000);
        diamondRepairKitRestorePercent = clamp(diamondRepairKitRestorePercent, 0, 100);
        legendaryUpgradeProbability = clamp(legendaryUpgradeProbability, 0.0, 1.0);
        upgradeWeight = Math.max(0, upgradeWeight);
        newSlotWeight = Math.max(0, newSlotWeight);
        legendaryWeight = Math.max(0, legendaryWeight);

        for (int i = 0; i < rerollCosts.length; i++) rerollCosts[i] = Math.max(0, rerollCosts[i]);
        if (materialLadder.isEmpty()) initDefaults();
        if (maxSlots.isEmpty()) initDefaults();

        // Ensure all source multiplier keys exist
        for (String key : List.of("mob", "livestock", "mining", "farming", "wood", "fishing")) {
            sourceMultipliers.putIfAbsent(key, 1.0);
            sourceMultipliers.put(key, clamp(sourceMultipliers.get(key), 0.0, 100.0));
        }
    }

    // ================================================================== //
    // Getters                                                             //
    // ================================================================== //

    public static int getBaseXpForCategory(String category) {
        Integer fallback = baseXp.getOrDefault("default", 350);
        Integer value = baseXp.get(category == null ? "default" : category.toLowerCase());
        return Math.max(1, value != null ? value : fallback);
    }

    /** @deprecated Renamed to {@link #getLevelRequirementGrowth()}. */
    @Deprecated
    public static double getXpMultiplier() { return levelRequirementGrowth; }

    public static double getLevelRequirementGrowth() { return levelRequirementGrowth; }
    public static double getGlobalXpGainMultiplier() { return globalXpGainMultiplier; }
    public static double getSourceMultiplier(String key) {
        return sourceMultipliers.getOrDefault(key, 1.0);
    }
    public static Map<String, Double> getSourceMultipliers() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(sourceMultipliers));
    }
    public static int getXpDisplayThreshold() { return xpDisplayThreshold; }
    public static int getDurabilityRestorePercent() { return durabilityRestorePercent; }
    public static int getRepairKitRestoreAmount() { return repairKitRestoreAmount; }
    public static int getDiamondRepairKitRestorePercent() { return diamondRepairKitRestorePercent; }
    public static int[] getRerollCosts() { return Arrays.copyOf(rerollCosts, rerollCosts.length); }
    public static double getLegendaryUpgradeProbability() { return legendaryUpgradeProbability; }

    /** The persisted (config-file) ladder, before any auto-derived fallback is
     *  merged in. Used by {@code MaterialTierDeriver} to locate anchor levels. */
    public static Map<Integer, List<String>> getConfiguredMaterialLadder() {
        Map<Integer, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<String>> e : materialLadder.entrySet()) {
            copy.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return copy;
    }

    /** The effective ladder: the persisted ladder merged with the auto-derived
     *  fallback layer. Config entries always win; derived materials only fill
     *  levels the config does not already define. */
    public static Map<Integer, List<String>> getMaterialLadder() {
        Map<Integer, List<String>> effective = effectiveLadder();
        Map<Integer, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<String>> e : effective.entrySet()) {
            copy.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return copy;
    }

    public static List<String> getMaterialsForLevel(int level) {
        List<String> mats = effectiveLadder().get(level);
        return mats == null ? List.of() : List.copyOf(mats);
    }

    public static int getMiningLevel(String material) {
        if (material == null || material.isBlank()) return -1;
        String m = material.trim().toLowerCase();
        for (Map.Entry<Integer, List<String>> e : effectiveLadder().entrySet()) {
            if (e.getValue().stream().anyMatch(s -> s.equalsIgnoreCase(m))) return e.getKey();
        }
        return -1;
    }

    public static List<String> getNextLevelMaterials(String material) {
        int level = getMiningLevel(material);
        if (level < 0) return List.of();
        Integer nextLevel = effectiveLadder().keySet().stream()
                .filter(l -> l > level).min(Integer::compareTo).orElse(null);
        return nextLevel == null ? List.of() : getMaterialsForLevel(nextLevel);
    }

    // ================================================================== //
    // Effective ladder (merge)                                             //
    // ================================================================== //

    /** Merges the persisted ladder with the auto-derived fallback layer. */
    private static Map<Integer, List<String>> effectiveLadder() {
        Map<Integer, List<String>> cached = effectiveLadderCache;
        if (cached == null) {
            synchronized (EquipLevelingConfig.class) {
                cached = effectiveLadderCache;
                if (cached == null) {
                    cached = mergeLadders(materialLadder,
                            com.amorairedraws.equipleveling.util.MaterialTierDeriver.getDerivedLadder());
                    effectiveLadderCache = cached;
                }
            }
        }
        return cached;
    }

    private static Map<Integer, List<String>> mergeLadders(
            Map<Integer, List<String>> config, Map<Integer, List<String>> derived) {
        Map<Integer, List<String>> merged = new LinkedHashMap<>();
        Set<String> present = new LinkedHashSet<>();

        // Layer A: persisted config (always wins).
        for (Map.Entry<Integer, List<String>> e : config.entrySet()) {
            List<String> mats = new ArrayList<>();
            for (String m : e.getValue()) {
                String clean = m.trim().toLowerCase();
                if (clean.isBlank()) continue;
                if (present.add(clean)) mats.add(clean);
            }
            if (!mats.isEmpty()) merged.put(e.getKey(), mats);
        }

        // Layer B: auto-derived fallback (only fills gaps).
        for (Map.Entry<Integer, List<String>> e : derived.entrySet()) {
            List<String> mats = merged.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
            for (String m : e.getValue()) {
                String clean = m.trim().toLowerCase();
                if (clean.isBlank()) continue;
                if (present.add(clean)) mats.add(clean);
            }
        }

        return merged;
    }

    /** Drops the cached effective ladder so it is rebuilt from the current
     *  config + derived layers. Called on config load/edit and by lifecycle
     *  hooks after the item registry/tags settle. */
    public static void invalidateMaterialCache() {
        synchronized (EquipLevelingConfig.class) {
            effectiveLadderCache = null;
        }
        com.amorairedraws.equipleveling.util.MaterialTierDeriver.invalidate();
        com.amorairedraws.equipleveling.util.EquipmentCategory.invalidateCache();
    }

    public static double getUpgradeWeight() { return upgradeWeight; }
    public static double getNewSlotWeight() { return newSlotWeight; }
    public static double getLegendaryWeight() { return legendaryWeight; }

    public static boolean isKeepEquipOnDeath() { return keepEquipOnDeath; }
    public static boolean isBrokenMechanicEnabled() { return enableBrokenMechanic; }

    public static Map<String, Integer> getCustomBlockXp() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(customBlockXp));
    }

    public static int getMaxSlotsForCategory(String category) {
        Integer fallback = maxSlots.getOrDefault("default", 4);
        Integer value = maxSlots.get(category == null ? "default" : category.toLowerCase());
        return value == null ? fallback : Math.min(8, Math.max(1, value));
    }

    // ================================================================== //
    // Setters                                                             //
    // ================================================================== //

    public static void setBaseXpForCategory(String category, int xp) {
        if (category == null || category.isBlank()) return;
        baseXp.put(category.toLowerCase(), Math.max(1, xp));
        ConfigSerializer.save();
    }

    @Deprecated
    public static void setXpMultiplier(double v) { setLevelRequirementGrowth(v); }

    public static void setLevelRequirementGrowth(double v) {
        if (!Double.isFinite(v)) return;
        levelRequirementGrowth = clamp(v, 1.0, 10.0);
        ConfigSerializer.save();
    }

    public static void setGlobalXpGainMultiplier(double v) {
        if (!Double.isFinite(v)) return;
        globalXpGainMultiplier = clamp(v, 0.1, 10.0);
        ConfigSerializer.save();
    }

    public static void setSourceMultiplier(String key, double v) {
        if (!Double.isFinite(v)) return;
        sourceMultipliers.put(key, clamp(v, 0.0, 100.0));
        ConfigSerializer.save();
    }

    public static void setXpDisplayThreshold(int v) { xpDisplayThreshold = Math.max(0, v); ConfigSerializer.save(); }
    public static void setDurabilityRestorePercent(int v) { durabilityRestorePercent = clamp(v, 0, 100); ConfigSerializer.save(); }
    public static void setRepairKitRestoreAmount(int v) { repairKitRestoreAmount = clamp(v, 0, 10000); ConfigSerializer.save(); }
    public static void setDiamondRepairKitRestorePercent(int v) { diamondRepairKitRestorePercent = clamp(v, 0, 100); ConfigSerializer.save(); }
    public static void setKeepEquipOnDeath(boolean v) { keepEquipOnDeath = v; ConfigSerializer.save(); }
    public static void setBrokenMechanicEnabled(boolean v) { enableBrokenMechanic = v; ConfigSerializer.save(); }

    public static void setRerollCosts(int[] costs) {
        if (costs == null || costs.length != 5) return;
        rerollCosts = Arrays.stream(costs).map(v -> Math.max(0, v)).toArray();
        ConfigSerializer.save();
    }

    public static void setLegendaryUpgradeProbability(double v) {
        if (!Double.isFinite(v)) return;
        legendaryUpgradeProbability = clamp(v, 0.0, 1.0);
        ConfigSerializer.save();
    }

    public static void setMaterialLadder(Map<Integer, List<String>> ladder) {
        materialLadder.clear();
        if (ladder != null) {
            for (Map.Entry<Integer, List<String>> e : ladder.entrySet()) {
                List<String> clean = new ArrayList<>();
                for (String s : e.getValue()) {
                    if (s != null && !s.isBlank()) clean.add(s.trim().toLowerCase());
                }
                if (!clean.isEmpty()) materialLadder.put(e.getKey(), clean);
            }
        }
        if (materialLadder.isEmpty()) initDefaults();
        invalidateMaterialCache();
        ConfigSerializer.save();
    }

    public static void setOfferWeights(double upgrade, double newSlot) {
        setOfferWeights(upgrade, newSlot, legendaryWeight);
    }

    public static void setOfferWeights(double upgrade, double newSlot, double legendary) {
        if (!Double.isFinite(upgrade) || !Double.isFinite(newSlot) || !Double.isFinite(legendary)) return;
        upgradeWeight = Math.max(0, upgrade);
        newSlotWeight = Math.max(0, newSlot);
        legendaryWeight = Math.max(0, legendary);
        ConfigSerializer.save();
    }

    public static void setCustomBlockXp(Map<String, Integer> map) {
        customBlockXp.clear();
        if (map != null) {
            map.forEach((k, v) -> {
                if (k != null && !k.isBlank() && v != null && v > 0) {
                    customBlockXp.put(k.trim().toLowerCase(), v);
                }
            });
        }
        ConfigSerializer.save();
    }

    public static void setMaxSlotsForCategory(String category, int slots) {
        if (category == null || category.isBlank()) return;
        maxSlots.put(category.toLowerCase(), Math.min(8, Math.max(1, slots)));
        ConfigSerializer.save();
    }

    /** @deprecated Use {@link #getMaterialLadder()} instead. */
    @Deprecated
    public static String[] getMaterialTiers() {
        List<String> flat = new ArrayList<>();
        Map<Integer, List<String>> effective = effectiveLadder();
        effective.keySet().stream().sorted().forEach(level ->
                flat.addAll(effective.get(level)));
        return flat.toArray(new String[0]);
    }

    /** @deprecated Use {@link #setMaterialLadder(Map)} instead. */
    @Deprecated
    public static void setMaterialTiers(String[] tiers) {
        materialLadder.clear();
        if (tiers != null && tiers.length > 0) {
            for (int i = 0; i < tiers.length; i++) {
                if (tiers[i] != null && !tiers[i].isBlank()) {
                    materialLadder.put(i, new ArrayList<>(List.of(tiers[i].trim().toLowerCase())));
                }
            }
        }
        if (materialLadder.isEmpty()) initDefaults();
        invalidateMaterialCache();
        ConfigSerializer.save();
    }

    // ================================================================== //
    // Helpers                                                             //
    // ================================================================== //

    static double clamp(double value, double min, double max) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
