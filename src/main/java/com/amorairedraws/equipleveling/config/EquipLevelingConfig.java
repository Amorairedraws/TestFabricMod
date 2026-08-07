package com.amorairedraws.equipleveling.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EquipLevelingConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("equip_leveling/config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("equip_leveling");
	private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");

	// Default values
	private static Map<String, Integer> baseXp = new HashMap<>();
	private static double xpMultiplier = 1.2;
	private static int xpDisplayThreshold = 10;
	private static int durabilityRestorePercent = 25;
	// Ore/action rewards are configurable independently of equipment material.
	private static int coalXp = 15, ironXp = 40, goldXp = 80, rareOreXp = 150;
	// Cost is indexed by the number of filled standard slots (0..4).
	private static int[] rerollCosts = {5, 10, 15, 20, 25};
	private static double legendaryUpgradeProbability = 0.05;
	private static String[] materialTiers = {"wood", "stone", "iron", "diamond", "netherite"};
	private static double upgradeWeight = 0.6;
	private static double newSlotWeight = 0.4;
	private static int anvilBaseCost = 1;
	private static int anvilPerLevelCost = 1;
	private static boolean keepEquipOnDeath = false;
	private static boolean enableBrokenMechanic = true;

	static {
		// Initialize base XP values
		baseXp.put("sword", 100);
		baseXp.put("axe", 120);
		baseXp.put("pickaxe", 80);
		baseXp.put("shovel", 70);
		baseXp.put("hoe", 60);
		baseXp.put("fishing_rod", 90);
		baseXp.put("helmet", 80);
		baseXp.put("chestplate", 100);
		baseXp.put("leggings", 100);
		baseXp.put("boots", 80);
		baseXp.put("default", 100);
	}

	public static void load() {
		try {
			Files.createDirectories(CONFIG_DIR);
			if (Files.exists(CONFIG_FILE)) {
				loadFromFile();
			} else {
				save();
			}
		} catch (IOException | RuntimeException e) {
			// A hand-edited config must never prevent the mod from starting. Keep
			// the validated defaults when JSON is malformed or has an unexpected
			// value, and report the problem for the user to correct.
			LOGGER.error("Failed to load config; using defaults for invalid values", e);
		}
	}

	private static void loadFromFile() {
		try (FileReader reader = new FileReader(CONFIG_FILE.toFile())) {
			JsonObject json = GSON.fromJson(reader, JsonObject.class);
			
			// Load base XP values
			if (json.has("baseXp")) {
				JsonObject xpObj = json.getAsJsonObject("baseXp");
				xpObj.entrySet().forEach(entry -> 
					baseXp.put(entry.getKey(), entry.getValue().getAsInt())
				);
			}

			xpMultiplier = json.has("xpMultiplier") ? json.get("xpMultiplier").getAsDouble() : 1.2;
			xpDisplayThreshold = json.has("xpDisplayThreshold") ? json.get("xpDisplayThreshold").getAsInt() : 10;
			durabilityRestorePercent = json.has("durabilityRestorePercent") ? json.get("durabilityRestorePercent").getAsInt() : 25;
			coalXp = json.has("coalXp") ? json.get("coalXp").getAsInt() : 15;
			ironXp = json.has("ironXp") ? json.get("ironXp").getAsInt() : 40;
			goldXp = json.has("goldXp") ? json.get("goldXp").getAsInt() : 80;
			rareOreXp = json.has("rareOreXp") ? json.get("rareOreXp").getAsInt() : 150;
			
			if (json.has("rerollCosts")) {
				int[] loaded = GSON.fromJson(json.get("rerollCosts"), int[].class);
				if (loaded != null && loaded.length == 5) rerollCosts = loaded;
			}
			
			legendaryUpgradeProbability = json.has("legendaryUpgradeProbability") ? json.get("legendaryUpgradeProbability").getAsDouble() : 0.05;
			
			if (json.has("materialTiers")) {
				materialTiers = GSON.fromJson(json.get("materialTiers"), String[].class);
			}
			
			upgradeWeight = json.has("upgradeWeight") ? json.get("upgradeWeight").getAsDouble() : 0.6;
			newSlotWeight = json.has("newSlotWeight") ? json.get("newSlotWeight").getAsDouble() : 0.4;
			anvilBaseCost = json.has("anvilBaseCost") ? json.get("anvilBaseCost").getAsInt() : 1;
			anvilPerLevelCost = json.has("anvilPerLevelCost") ? json.get("anvilPerLevelCost").getAsInt() : 1;
			keepEquipOnDeath = json.has("keepEquipOnDeath") ? json.get("keepEquipOnDeath").getAsBoolean() : false;
			enableBrokenMechanic = json.has("enableBrokenMechanic") ? json.get("enableBrokenMechanic").getAsBoolean() : true;
			
			// Treat hand-edited config files as untrusted input.  Invalid values
			// should never make XP requirements, costs, or weighted offers unusable.
			xpMultiplier = Double.isFinite(xpMultiplier) ? Math.max(1.0, Math.min(10.0, xpMultiplier)) : 1.2;
			xpDisplayThreshold = Math.max(0, xpDisplayThreshold);
			durabilityRestorePercent = Math.max(0, Math.min(100, durabilityRestorePercent));
			legendaryUpgradeProbability = Double.isFinite(legendaryUpgradeProbability)
					? Math.max(0, Math.min(1, legendaryUpgradeProbability)) : 0.05;
			upgradeWeight = Double.isFinite(upgradeWeight) ? Math.max(0, upgradeWeight) : 0.6;
			newSlotWeight = Double.isFinite(newSlotWeight) ? Math.max(0, newSlotWeight) : 0.4;
			anvilBaseCost = Math.max(0, anvilBaseCost);
			anvilPerLevelCost = Math.max(0, anvilPerLevelCost);
			for (int i = 0; i < rerollCosts.length; i++) rerollCosts[i] = Math.max(0, rerollCosts[i]);
			if (materialTiers == null || materialTiers.length == 0) {
				materialTiers = new String[]{"wood", "stone", "iron", "diamond", "netherite"};
			}
			LOGGER.info("Config loaded successfully");
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Failed to load config from file", e);
		}
	}

	public static void save() {
		try {
			Files.createDirectories(CONFIG_DIR);
			JsonObject json = new JsonObject();
			
			JsonObject xpObj = new JsonObject();
			baseXp.forEach((key, value) -> xpObj.addProperty(key, value));
			json.add("baseXp", xpObj);
			
			json.addProperty("xpMultiplier", xpMultiplier);
			json.addProperty("xpDisplayThreshold", xpDisplayThreshold);
			json.addProperty("durabilityRestorePercent", durabilityRestorePercent);
			json.addProperty("coalXp", coalXp);
			json.addProperty("ironXp", ironXp);
			json.addProperty("goldXp", goldXp);
			json.addProperty("rareOreXp", rareOreXp);
			json.add("rerollCosts", GSON.toJsonTree(rerollCosts));
			json.addProperty("legendaryUpgradeProbability", legendaryUpgradeProbability);
			json.add("materialTiers", GSON.toJsonTree(materialTiers));
			json.addProperty("upgradeWeight", upgradeWeight);
			json.addProperty("newSlotWeight", newSlotWeight);
			json.addProperty("anvilBaseCost", anvilBaseCost);
			json.addProperty("anvilPerLevelCost", anvilPerLevelCost);
			json.addProperty("keepEquipOnDeath", keepEquipOnDeath);
			json.addProperty("enableBrokenMechanic", enableBrokenMechanic);
			
			try (FileWriter writer = new FileWriter(CONFIG_FILE.toFile())) {
				GSON.toJson(json, writer);
			}
			LOGGER.info("Config saved successfully");
		} catch (IOException e) {
			LOGGER.error("Failed to save config", e);
		}
	}

	// Getters
	public static int getBaseXpForCategory(String category) {
		return baseXp.getOrDefault(category, baseXp.get("default"));
	}

	public static double getXpMultiplier() {
		return xpMultiplier;
	}

	public static int getXpDisplayThreshold() {
		return xpDisplayThreshold;
	}

	public static int getDurabilityRestorePercent() {
		return durabilityRestorePercent;
	}

	public static int getCoalXp() { return coalXp; }
	public static int getIronXp() { return ironXp; }
	public static int getGoldXp() { return goldXp; }
	public static int getRareOreXp() { return rareOreXp; }
	public static void setOreXp(int coal, int iron, int gold, int rare) {
		coalXp = Math.max(0, coal); ironXp = Math.max(0, iron);
		goldXp = Math.max(0, gold); rareOreXp = Math.max(0, rare); save();
	}

	public static int[] getRerollCosts() {
		return rerollCosts;
	}

	public static double getLegendaryUpgradeProbability() {
		return legendaryUpgradeProbability;
	}

	public static String[] getMaterialTiers() {
		return materialTiers;
	}

	public static double getUpgradeWeight() {
		return upgradeWeight;
	}

	public static double getNewSlotWeight() {
		return newSlotWeight;
	}

	public static int getAnvilBaseCost() {
		return anvilBaseCost;
	}

	public static int getAnvilPerLevelCost() {
		return anvilPerLevelCost;
	}

	public static boolean isKeepEquipOnDeath() {
		return keepEquipOnDeath;
	}

	public static boolean isBrokenMechanicEnabled() {
		return enableBrokenMechanic;
	}

	// Setters
	public static void setBaseXpForCategory(String category, int xp) {
		baseXp.put(category, xp);
		save();
	}

	public static void setXpMultiplier(double multiplier) {
		if (!Double.isFinite(multiplier)) return;
		xpMultiplier = Math.max(1.0, Math.min(10.0, multiplier));
		save();
	}

	public static void setXpDisplayThreshold(int threshold) {
		xpDisplayThreshold = Math.max(0, threshold);
		save();
	}

	public static void setKeepEquipOnDeath(boolean keep) {
		keepEquipOnDeath = keep;
		save();
	}

	public static void setBrokenMechanicEnabled(boolean enabled) {
		enableBrokenMechanic = enabled;
		save();
	}

	public static void setDurabilityRestorePercent(int value) {
		durabilityRestorePercent = Math.max(0, Math.min(100, value));
		save();
	}

	public static void setRerollCosts(int[] costs) {
		if (costs == null || costs.length != 5) throw new IllegalArgumentException("Five reroll costs are required");
		rerollCosts = java.util.Arrays.stream(costs).map(v -> Math.max(0, v)).toArray();
		save();
	}

	public static void setLegendaryUpgradeProbability(double value) {
		legendaryUpgradeProbability = Math.max(0, Math.min(1, value));
		save();
	}

	public static void setMaterialTiers(String[] tiers) {
		if (tiers == null || tiers.length == 0) throw new IllegalArgumentException("At least one material tier is required");
		materialTiers = java.util.Arrays.stream(tiers).filter(s -> s != null && !s.isBlank()).map(String::toLowerCase).toArray(String[]::new);
		if (materialTiers.length == 0) throw new IllegalArgumentException("At least one material tier is required");
		save();
	}

	public static void setOfferWeights(double upgrade, double newSlot) {
		upgradeWeight = Math.max(0, upgrade);
		newSlotWeight = Math.max(0, newSlot);
		save();
	}

	public static void setAnvilCosts(int base, int perLevel) {
		anvilBaseCost = Math.max(0, base);
		anvilPerLevelCost = Math.max(0, perLevel);
		save();
	}
}
