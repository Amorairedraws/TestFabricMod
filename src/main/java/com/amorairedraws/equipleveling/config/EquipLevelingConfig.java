package com.amorairedraws.equipleveling.config;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central configuration for the Equip Leveling mod.
 *
 * <h3>Config file locations</h3>
 * <ul>
 *   <li>Singleplayer / personal: {@code config/equip_leveling/config.json}</li>
 *   <li>Per-server (multiplayer): {@code config/equip_leveling/servers/&lt;address&gt;.json}</li>
 * </ul>
 *
 * <h3>Multiplier hierarchy</h3>
 * <pre>
 *   effectiveXp = baseXp × globalGainMultiplier × sourceMultiplier[mob|mining|farming|wood|fishing]
 *   levelRequirement(n) = levelRequirement(n-1) × levelRequirementGrowth
 * </pre>
 */
public class EquipLevelingConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("equip_leveling/config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("equip_leveling");
    private static final Path SERVERS_DIR = CONFIG_DIR.resolve("servers");
    private static Path activeConfigFile = CONFIG_DIR.resolve("config.json");

    // ================================================================== //
    // Fields                                                              //
    // ================================================================== //

    // Base XP per category (how much total XP an item needs for its first level-up)
    private static Map<String, Integer> baseXp = new LinkedHashMap<>();

    // Level-up requirement growth (was "xpMultiplier" in older versions).
    // Each level requires this many times the previous level's XP.
    private static double levelRequirementGrowth = 1.2;

    // Global XP gain multiplier — multiplies ALL XP from ALL sources.
    private static double globalXpGainMultiplier = 1.0;

    // Per-source XP multipliers. Keys: mob, mining, farming, wood, fishing.
    private static Map<String, Double> sourceMultipliers = new LinkedHashMap<>();

    // Display threshold — floating XP numbers only appear for gains >= this.
    private static int xpDisplayThreshold = 10;

    // Durability restored on level-up, as a percentage of max durability.
    private static int durabilityRestorePercent = 25;

    // Reroll costs indexed by number of filled standard slots (0..4).
    private static int[] rerollCosts = {5, 10, 15, 20, 25};

    // Probability a visit to the enchanting table offers a legendary upgrade.
    private static double legendaryUpgradeProbability = 0.05;

    // Mining-level → materials map for the material ladder.
    private static Map<Integer, List<String>> materialLadder = new LinkedHashMap<>();

    // Cached effective ladder: the persisted ladder merged with the auto-derived
    // fallback layer. Rebuilt lazily and dropped whenever either layer changes.
    private static volatile Map<Integer, List<String>> effectiveLadderCache = null;

    // Offer weights for the enchanting table.
    private static double upgradeWeight = 0.6;
    private static double newSlotWeight = 0.4;
    private static double legendaryWeight = 0.05;

    // Anvil costs.
    private static int anvilBaseCost = 1;
    private static int anvilPerLevelCost = 1;

    // Toggles.
    private static boolean keepEquipOnDeath = false;
    private static boolean enableBrokenMechanic = true;

    // Custom block ID → XP overrides (takes priority over formula).
    private static Map<String, Integer> customBlockXp = new LinkedHashMap<>();

    // Max enchantment slots per category.
    private static Map<String, Integer> maxSlots = new LinkedHashMap<>();

    // Whether we're currently using a server-synced config (multiplayer).
    private static boolean usingServerConfig = false;

    // ================================================================== //
    // Static initialiser                                                  //
    // ================================================================== //

    static {
        initDefaults();
    }

    private static void initDefaults() {
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

        // Material ladder defaults
        materialLadder.put(0, new ArrayList<>(List.of("wood", "gold")));
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
    // Loading                                                             //
    // ================================================================== //

    /** Loads the personal config file. Called on mod init. */
    public static void load() {
        activeConfigFile = CONFIG_DIR.resolve("config.json");
        usingServerConfig = false;
        loadFrom(activeConfigFile);
    }

    /**
     * Switches to a server-specific config file, creating it from the server's
     * synced JSON if needed. Called when the client receives a ConfigSyncPacket.
     *
     * @param serverAddress  the server address (e.g. "mc.example.com:25565")
     * @param serverJson     the JSON config from the server
     */
    public static void loadServerConfig(String serverAddress, String serverJson) {
        try {
            Files.createDirectories(SERVERS_DIR);
            String safeName = serverAddress.replaceAll("[^a-zA-Z0-9._-]", "_");
            activeConfigFile = SERVERS_DIR.resolve(safeName + ".json");

            // Write the server's config to disk so it persists across restarts.
            Files.writeString(activeConfigFile, serverJson);

            usingServerConfig = true;
            loadFrom(activeConfigFile);
            LOGGER.info("Switched to server config: {}", activeConfigFile);
        } catch (IOException e) {
            LOGGER.error("Failed to save server config", e);
        }
    }

    /** Restores the personal config after disconnecting from a server. */
    public static void restorePersonalConfig() {
        activeConfigFile = CONFIG_DIR.resolve("config.json");
        usingServerConfig = false;
        loadFrom(activeConfigFile);
        LOGGER.info("Restored personal config");
    }

    public static boolean isUsingServerConfig() {
        return usingServerConfig;
    }

    /** Returns the full config as a JSON string (for syncing to clients). */
    public static String toJsonString() {
        return GSON.toJson(buildJson());
    }

    /** Replaces all config values from a JSON string (used by client sync). */
    public static void fromJsonString(String json) {
        try {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            parseJson(obj);
            save();
        } catch (Exception e) {
            LOGGER.error("Failed to parse synced config", e);
        }
    }

    private static void loadFrom(Path file) {
        try {
            Files.createDirectories(CONFIG_DIR);
            if (Files.exists(file)) {
                try (FileReader reader = new FileReader(file.toFile())) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    parseJson(json);
                }
            } else {
                save();
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to load config; using defaults", e);
            save(); // write valid defaults so the broken file is replaced
        }
    }

    @SuppressWarnings("deprecation")
    private static void parseJson(JsonObject json) {
        // Base XP
        if (json.has("baseXp")) {
            baseXp.clear();
            json.getAsJsonObject("baseXp").entrySet().forEach(e ->
                    baseXp.put(e.getKey(), e.getValue().getAsInt()));
        }

        // Level requirement growth (backward compat: was "xpMultiplier")
        levelRequirementGrowth = getDouble(json, "levelRequirementGrowth",
                getDouble(json, "xpMultiplier", 1.2));

        // Global XP gain multiplier (new)
        globalXpGainMultiplier = getDouble(json, "globalXpGainMultiplier", 1.0);

        // Per-source multipliers
        if (json.has("sourceMultipliers")) {
            sourceMultipliers.clear();
            json.getAsJsonObject("sourceMultipliers").entrySet().forEach(e ->
                    sourceMultipliers.put(e.getKey(), e.getValue().getAsDouble()));
        }

        // Simple fields
        xpDisplayThreshold = getInt(json, "xpDisplayThreshold", 10);
        durabilityRestorePercent = getInt(json, "durabilityRestorePercent", 25);
        legendaryUpgradeProbability = getDouble(json, "legendaryUpgradeProbability", 0.05);
        upgradeWeight = getDouble(json, "upgradeWeight", 0.6);
        newSlotWeight = getDouble(json, "newSlotWeight", 0.4);
        legendaryWeight = getDouble(json, "legendaryWeight", 0.05);
        anvilBaseCost = getInt(json, "anvilBaseCost", 1);
        anvilPerLevelCost = getInt(json, "anvilPerLevelCost", 1);
        keepEquipOnDeath = getBool(json, "keepEquipOnDeath", false);
        enableBrokenMechanic = getBool(json, "enableBrokenMechanic", true);

        // Reroll costs
        if (json.has("rerollCosts")) {
            int[] loaded = GSON.fromJson(json.get("rerollCosts"), int[].class);
            if (loaded != null && loaded.length == 5) rerollCosts = loaded;
        }

        // Material ladder
        if (json.has("materialLadder")) {
            materialLadder.clear();
            JsonObject ladderObj = json.getAsJsonObject("materialLadder");
            for (Map.Entry<String, com.google.gson.JsonElement> entry : ladderObj.entrySet()) {
                try {
                    int level = Integer.parseInt(entry.getKey());
                    List<String> mats = new ArrayList<>();
                    for (com.google.gson.JsonElement e : entry.getValue().getAsJsonArray()) {
                        String mat = e.getAsString().trim().toLowerCase();
                        if (!mat.isBlank()) mats.add(mat);
                    }
                    if (!mats.isEmpty()) materialLadder.put(level, mats);
                } catch (NumberFormatException ignored) {}
            }
        } else if (json.has("materialTiers")) {
            // Migrate old flat array
            String[] old = GSON.fromJson(json.get("materialTiers"), String[].class);
            if (old != null && old.length > 0) {
                materialLadder.clear();
                for (int i = 0; i < old.length; i++) {
                    String mat = old[i].trim().toLowerCase();
                    if (!mat.isBlank()) materialLadder.put(i, new ArrayList<>(List.of(mat)));
                }
            }
        }

        // Custom block XP
        customBlockXp.clear();
        if (json.has("customBlockXp") && json.get("customBlockXp").isJsonObject()) {
            json.getAsJsonObject("customBlockXp").entrySet().forEach(entry -> {
                try {
                    int v = entry.getValue().getAsInt();
                    if (v > 0) customBlockXp.put(entry.getKey(), v);
                } catch (RuntimeException ignored) {}
            });
        }

        // Max slots
        if (json.has("maxSlots")) {
            maxSlots.clear();
            json.getAsJsonObject("maxSlots").entrySet().forEach(entry -> {
                try {
                    int v = entry.getValue().getAsInt();
                    if (v >= 1 && v <= 8) maxSlots.put(entry.getKey(), v);
                } catch (RuntimeException ignored) {}
            });
        }

        // Validate
        validate();
        invalidateMaterialCache();
    }

    private static void validate() {
        levelRequirementGrowth = clamp(levelRequirementGrowth, 1.0, 10.0);
        globalXpGainMultiplier = clamp(globalXpGainMultiplier, 0.1, 10.0);
        xpDisplayThreshold = Math.max(0, xpDisplayThreshold);
        durabilityRestorePercent = clamp(durabilityRestorePercent, 0, 100);
        legendaryUpgradeProbability = clamp(legendaryUpgradeProbability, 0.0, 1.0);
        upgradeWeight = Math.max(0, upgradeWeight);
        newSlotWeight = Math.max(0, newSlotWeight);
        legendaryWeight = Math.max(0, legendaryWeight);
        anvilBaseCost = Math.max(0, anvilBaseCost);
        anvilPerLevelCost = Math.max(0, anvilPerLevelCost);
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
    // Saving                                                              //
    // ================================================================== //

    public static void save() {
        try {
            Files.createDirectories(activeConfigFile.getParent());
            try (FileWriter writer = new FileWriter(activeConfigFile.toFile())) {
                GSON.toJson(buildJson(), writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    private static JsonObject buildJson() {
        JsonObject json = new JsonObject();

        JsonObject xpObj = new JsonObject();
        baseXp.forEach(xpObj::addProperty);
        json.add("baseXp", xpObj);

        json.addProperty("levelRequirementGrowth", levelRequirementGrowth);
        json.addProperty("globalXpGainMultiplier", globalXpGainMultiplier);

        JsonObject srcMult = new JsonObject();
        sourceMultipliers.forEach(srcMult::addProperty);
        json.add("sourceMultipliers", srcMult);

        json.addProperty("xpDisplayThreshold", xpDisplayThreshold);
        json.addProperty("durabilityRestorePercent", durabilityRestorePercent);
        json.add("rerollCosts", GSON.toJsonTree(rerollCosts));
        json.addProperty("legendaryUpgradeProbability", legendaryUpgradeProbability);

        JsonObject ladderJson = new JsonObject();
        for (Map.Entry<Integer, List<String>> e : materialLadder.entrySet()) {
            ladderJson.add(e.getKey().toString(), GSON.toJsonTree(e.getValue()));
        }
        json.add("materialLadder", ladderJson);

        json.addProperty("upgradeWeight", upgradeWeight);
        json.addProperty("newSlotWeight", newSlotWeight);
        json.addProperty("legendaryWeight", legendaryWeight);
        json.addProperty("anvilBaseCost", anvilBaseCost);
        json.addProperty("anvilPerLevelCost", anvilPerLevelCost);
        json.addProperty("keepEquipOnDeath", keepEquipOnDeath);
        json.addProperty("enableBrokenMechanic", enableBrokenMechanic);

        JsonObject custom = new JsonObject();
        customBlockXp.forEach(custom::addProperty);
        json.add("customBlockXp", custom);

        JsonObject ms = new JsonObject();
        maxSlots.forEach(ms::addProperty);
        json.add("maxSlots", ms);

        return json;
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
    }

    public static double getUpgradeWeight() { return upgradeWeight; }
    public static double getNewSlotWeight() { return newSlotWeight; }
    public static double getLegendaryWeight() { return legendaryWeight; }
    public static int getAnvilBaseCost() { return anvilBaseCost; }
    public static int getAnvilPerLevelCost() { return anvilPerLevelCost; }
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
        save();
    }

    @Deprecated
    public static void setXpMultiplier(double v) { setLevelRequirementGrowth(v); }

    public static void setLevelRequirementGrowth(double v) {
        if (!Double.isFinite(v)) return;
        levelRequirementGrowth = clamp(v, 1.0, 10.0);
        save();
    }

    public static void setGlobalXpGainMultiplier(double v) {
        if (!Double.isFinite(v)) return;
        globalXpGainMultiplier = clamp(v, 0.1, 10.0);
        save();
    }

    public static void setSourceMultiplier(String key, double v) {
        if (!Double.isFinite(v)) return;
        sourceMultipliers.put(key, clamp(v, 0.0, 100.0));
        save();
    }

    public static void setXpDisplayThreshold(int v) { xpDisplayThreshold = Math.max(0, v); save(); }
    public static void setDurabilityRestorePercent(int v) { durabilityRestorePercent = clamp(v, 0, 100); save(); }
    public static void setKeepEquipOnDeath(boolean v) { keepEquipOnDeath = v; save(); }
    public static void setBrokenMechanicEnabled(boolean v) { enableBrokenMechanic = v; save(); }

    public static void setRerollCosts(int[] costs) {
        if (costs == null || costs.length != 5) return;
        rerollCosts = Arrays.stream(costs).map(v -> Math.max(0, v)).toArray();
        save();
    }

    public static void setLegendaryUpgradeProbability(double v) {
        if (!Double.isFinite(v)) return;
        legendaryUpgradeProbability = clamp(v, 0.0, 1.0);
        save();
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
        save();
    }

    public static void setOfferWeights(double upgrade, double newSlot) {
        setOfferWeights(upgrade, newSlot, legendaryWeight);
    }

    public static void setOfferWeights(double upgrade, double newSlot, double legendary) {
        if (!Double.isFinite(upgrade) || !Double.isFinite(newSlot) || !Double.isFinite(legendary)) return;
        upgradeWeight = Math.max(0, upgrade);
        newSlotWeight = Math.max(0, newSlot);
        legendaryWeight = Math.max(0, legendary);
        save();
    }

    public static void setAnvilCosts(int base, int perLevel) {
        anvilBaseCost = Math.max(0, base);
        anvilPerLevelCost = Math.max(0, perLevel);
        save();
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
        save();
    }

    public static void setMaxSlotsForCategory(String category, int slots) {
        if (category == null || category.isBlank()) return;
        maxSlots.put(category.toLowerCase(), Math.min(8, Math.max(1, slots)));
        save();
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
        save();
    }

    // ================================================================== //
    // Helpers                                                             //
    // ================================================================== //

    private static int getInt(JsonObject json, String key, int def) {
        return json.has(key) ? json.get(key).getAsInt() : def;
    }

    private static double getDouble(JsonObject json, String key, double def) {
        return json.has(key) ? json.get(key).getAsDouble() : def;
    }

    private static boolean getBool(JsonObject json, String key, boolean def) {
        return json.has(key) ? json.get(key).getAsBoolean() : def;
    }

    private static double clamp(double value, double min, double max) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
