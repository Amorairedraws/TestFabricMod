package com.amorairedraws.equipleveling.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JSON (de)serialization and file handling for {@link EquipLevelingConfig}.
 *
 * <p>Kept separate so {@link EquipLevelingConfig} stays a plain holder of values
 * (with getters/setters) while this class owns everything about where and how
 * those values are persisted and synced.</p>
 */
public final class ConfigSerializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("equip_leveling/config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("equip_leveling");
    private static final Path SERVERS_DIR = CONFIG_DIR.resolve("servers");
    private static Path activeConfigFile = CONFIG_DIR.resolve("config.json");
    private static boolean usingServerConfig = false;

    private ConfigSerializer() {}

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
     * @param serverAddress the server address (e.g. "mc.example.com:25565")
     * @param serverJson    the JSON config from the server
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

    /** Returns the full config as a JSON string (for syncing to clients). */
    public static String toJsonString() {
        return GSON.toJson(buildJson());
    }

    /** Writes the current config values to the active config file. */
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

    private static void parseJson(JsonObject json) {
        // Base XP
        if (json.has("baseXp")) {
            EquipLevelingConfig.baseXp.clear();
            json.getAsJsonObject("baseXp").entrySet().forEach(e ->
                    EquipLevelingConfig.baseXp.put(e.getKey(), e.getValue().getAsInt()));
        }

        // Level requirement growth (backward compat: was "xpMultiplier")
        EquipLevelingConfig.levelRequirementGrowth = getDouble(json, "levelRequirementGrowth",
                getDouble(json, "xpMultiplier", 1.2));

        // Global XP gain multiplier
        EquipLevelingConfig.globalXpGainMultiplier = getDouble(json, "globalXpGainMultiplier", 1.0);

        // Per-source multipliers
        if (json.has("sourceMultipliers")) {
            EquipLevelingConfig.sourceMultipliers.clear();
            json.getAsJsonObject("sourceMultipliers").entrySet().forEach(e ->
                    EquipLevelingConfig.sourceMultipliers.put(e.getKey(), e.getValue().getAsDouble()));
        }

        // Simple fields
        EquipLevelingConfig.xpDisplayThreshold = getInt(json, "xpDisplayThreshold", 10);
        EquipLevelingConfig.durabilityRestorePercent = getInt(json, "durabilityRestorePercent", 25);
        EquipLevelingConfig.repairKitRestoreAmount = getInt(json, "repairKitRestoreAmount", 100);
        EquipLevelingConfig.diamondRepairKitRestorePercent = getInt(json, "diamondRepairKitRestorePercent", 50);
        EquipLevelingConfig.legendaryUpgradeProbability = getDouble(json, "legendaryUpgradeProbability", 0.05);
        EquipLevelingConfig.upgradeWeight = getDouble(json, "upgradeWeight", 0.6);
        EquipLevelingConfig.newSlotWeight = getDouble(json, "newSlotWeight", 0.4);
        EquipLevelingConfig.legendaryWeight = getDouble(json, "legendaryWeight", 0.05);

        EquipLevelingConfig.keepEquipOnDeath = getBool(json, "keepEquipOnDeath", false);
        EquipLevelingConfig.enableBrokenMechanic = getBool(json, "enableBrokenMechanic", true);

        // Reroll costs
        if (json.has("rerollCosts")) {
            int[] loaded = GSON.fromJson(json.get("rerollCosts"), int[].class);
            if (loaded != null && loaded.length == 5) EquipLevelingConfig.rerollCosts = loaded;
        }

        // Material ladder
        if (json.has("materialLadder")) {
            EquipLevelingConfig.materialLadder.clear();
            JsonObject ladderObj = json.getAsJsonObject("materialLadder");
            for (Map.Entry<String, JsonElement> entry : ladderObj.entrySet()) {
                try {
                    int level = Integer.parseInt(entry.getKey());
                    List<String> mats = new ArrayList<>();
                    for (JsonElement e : entry.getValue().getAsJsonArray()) {
                        String mat = e.getAsString().trim().toLowerCase();
                        if (!mat.isBlank()) mats.add(mat);
                    }
                    if (!mats.isEmpty()) EquipLevelingConfig.materialLadder.put(level, mats);
                } catch (NumberFormatException ignored) {}
            }
        } else if (json.has("materialTiers")) {
            // Migrate old flat array
            String[] old = GSON.fromJson(json.get("materialTiers"), String[].class);
            if (old != null && old.length > 0) {
                EquipLevelingConfig.materialLadder.clear();
                for (int i = 0; i < old.length; i++) {
                    String mat = old[i].trim().toLowerCase();
                    if (!mat.isBlank()) {
                        EquipLevelingConfig.materialLadder.put(i, new ArrayList<>(List.of(mat)));
                    }
                }
            }
        }

        // Custom block XP
        EquipLevelingConfig.customBlockXp.clear();
        if (json.has("customBlockXp") && json.get("customBlockXp").isJsonObject()) {
            json.getAsJsonObject("customBlockXp").entrySet().forEach(entry -> {
                try {
                    int v = entry.getValue().getAsInt();
                    if (v > 0) EquipLevelingConfig.customBlockXp.put(entry.getKey(), v);
                } catch (RuntimeException ignored) {}
            });
        }

        // Max slots
        if (json.has("maxSlots")) {
            EquipLevelingConfig.maxSlots.clear();
            json.getAsJsonObject("maxSlots").entrySet().forEach(entry -> {
                try {
                    int v = entry.getValue().getAsInt();
                    if (v >= 1 && v <= 8) EquipLevelingConfig.maxSlots.put(entry.getKey(), v);
                } catch (RuntimeException ignored) {}
            });
        }

        // Validate
        EquipLevelingConfig.validate();
        EquipLevelingConfig.invalidateMaterialCache();
    }

    private static JsonObject buildJson() {
        JsonObject json = new JsonObject();

        JsonObject xpObj = new JsonObject();
        EquipLevelingConfig.baseXp.forEach(xpObj::addProperty);
        json.add("baseXp", xpObj);

        json.addProperty("levelRequirementGrowth", EquipLevelingConfig.levelRequirementGrowth);
        json.addProperty("globalXpGainMultiplier", EquipLevelingConfig.globalXpGainMultiplier);

        JsonObject srcMult = new JsonObject();
        EquipLevelingConfig.sourceMultipliers.forEach(srcMult::addProperty);
        json.add("sourceMultipliers", srcMult);

        json.addProperty("xpDisplayThreshold", EquipLevelingConfig.xpDisplayThreshold);
        json.addProperty("durabilityRestorePercent", EquipLevelingConfig.durabilityRestorePercent);
        json.addProperty("repairKitRestoreAmount", EquipLevelingConfig.repairKitRestoreAmount);
        json.addProperty("diamondRepairKitRestorePercent", EquipLevelingConfig.diamondRepairKitRestorePercent);
        json.add("rerollCosts", GSON.toJsonTree(EquipLevelingConfig.rerollCosts));
        json.addProperty("legendaryUpgradeProbability", EquipLevelingConfig.legendaryUpgradeProbability);

        JsonObject ladderJson = new JsonObject();
        for (Map.Entry<Integer, List<String>> e : EquipLevelingConfig.materialLadder.entrySet()) {
            ladderJson.add(e.getKey().toString(), GSON.toJsonTree(e.getValue()));
        }
        json.add("materialLadder", ladderJson);

        json.addProperty("upgradeWeight", EquipLevelingConfig.upgradeWeight);
        json.addProperty("newSlotWeight", EquipLevelingConfig.newSlotWeight);
        json.addProperty("legendaryWeight", EquipLevelingConfig.legendaryWeight);

        json.addProperty("keepEquipOnDeath", EquipLevelingConfig.keepEquipOnDeath);
        json.addProperty("enableBrokenMechanic", EquipLevelingConfig.enableBrokenMechanic);

        JsonObject custom = new JsonObject();
        EquipLevelingConfig.customBlockXp.forEach(custom::addProperty);
        json.add("customBlockXp", custom);

        JsonObject ms = new JsonObject();
        EquipLevelingConfig.maxSlots.forEach(ms::addProperty);
        json.add("maxSlots", ms);

        return json;
    }

    private static int getInt(JsonObject json, String key, int def) {
        return json.has(key) ? json.get(key).getAsInt() : def;
    }

    private static double getDouble(JsonObject json, String key, double def) {
        return json.has(key) ? json.get(key).getAsDouble() : def;
    }

    private static boolean getBool(JsonObject json, String key, boolean def) {
        return json.has(key) ? json.get(key).getAsBoolean() : def;
    }
}
