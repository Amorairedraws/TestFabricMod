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
	private static int coalXp = 3, ironXp = 8, goldXp = 20, rareOreXp = 40;
	// Per-action XP rewards (Issue 5/6). These let players tune how much XP each
	// activity grants without editing code.
	private static int entityKillXp = 10;   // base XP per kill (scaled by health)
	private static int logXp = 4;           // per log/stem broken with an axe
	private static int shovelXp = 1;        // per dirt/sand/gravel/snow block
	private static int hoeXp = 3;           // per mature crop harvested
	private static int stoneXp = 1;         // per stone-type block broken with a pickaxe
	private static int clayXp = 5;          // per clay block broken with a shovel
	// Cost is indexed by the number of filled standard slots (0..4).
	// Defaults match the documented 5 / 10 / 15 / 20 / 25 level progression.
	private static int[] rerollCosts = {5, 10, 15, 20, 25};
	private static double legendaryUpgradeProbability = 0.05;
	private static String[] materialTiers = {"wood", "stone", "iron", "diamond", "netherite"};
	private static double upgradeWeight = 0.6;
	private static double newSlotWeight = 0.4;
	private static double legendaryWeight = 0.05;
	private static int anvilBaseCost = 1;
	private static int anvilPerLevelCost = 1;
	private static boolean keepEquipOnDeath = false;
	private static boolean enableBrokenMechanic = true;
	// Custom block -> XP rewards added by the player through the config screen.
	// Keyed by block id (e.g. "minecraft:deepslate"), value is the XP granted
	// when that block is broken with a pickaxe.
	private static Map<String, Integer> customBlockXp = new HashMap<>();
	// Maximum number of standard enchantment slots per item category. Some tools
	// (pickaxes, shovels, hoes) simply don't have enough compatible enchantments
	// to fill all 4 slots, so this lets players/balance dictate a lower cap.
	// Mending is awarded once this configured max is reached.
	private static Map<String, Integer> maxSlots = new HashMap<>();

	static {
		// Initialize base XP values. These represent how much focused play is
		// needed before an item is ready to level up. Tools used constantly
		// (pickaxe, axe) have higher requirements; tools used rarely (hoe) are
		// quicker so they still feel rewarding. Values are tuned so a level-up
		// lands around 15-30 minutes of that item's normal use.
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

		// Default max slots: most equipment can use 4, but tools with few
		// compatible enchantments cap lower by default so they can still max out.
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
			coalXp = json.has("coalXp") ? json.get("coalXp").getAsInt() : 3;
			ironXp = json.has("ironXp") ? json.get("ironXp").getAsInt() : 8;
			goldXp = json.has("goldXp") ? json.get("goldXp").getAsInt() : 20;
			rareOreXp = json.has("rareOreXp") ? json.get("rareOreXp").getAsInt() : 40;
			entityKillXp = json.has("entityKillXp") ? json.get("entityKillXp").getAsInt() : 10;
			logXp = json.has("logXp") ? json.get("logXp").getAsInt() : 4;
			shovelXp = json.has("shovelXp") ? json.get("shovelXp").getAsInt() : 1;
			hoeXp = json.has("hoeXp") ? json.get("hoeXp").getAsInt() : 3;
			stoneXp = json.has("stoneXp") ? json.get("stoneXp").getAsInt() : 1;
			clayXp = json.has("clayXp") ? json.get("clayXp").getAsInt() : 5;
			
			if (json.has("rerollCosts")) {
				int[] loaded = GSON.fromJson(json.get("rerollCosts"), int[].class);
				if (loaded != null && loaded.length == 5) rerollCosts = loaded;
			} else {
				rerollCosts = new int[]{5, 10, 15, 20, 25};
			}
			
			legendaryUpgradeProbability = json.has("legendaryUpgradeProbability") ? json.get("legendaryUpgradeProbability").getAsDouble() : 0.05;
			
			if (json.has("materialTiers")) {
				materialTiers = GSON.fromJson(json.get("materialTiers"), String[].class);
			}
			
			upgradeWeight = json.has("upgradeWeight") ? json.get("upgradeWeight").getAsDouble() : 0.6;
			newSlotWeight = json.has("newSlotWeight") ? json.get("newSlotWeight").getAsDouble() : 0.4;
			legendaryWeight = json.has("legendaryWeight") ? json.get("legendaryWeight").getAsDouble() : 0.05;
			anvilBaseCost = json.has("anvilBaseCost") ? json.get("anvilBaseCost").getAsInt() : 1;
			anvilPerLevelCost = json.has("anvilPerLevelCost") ? json.get("anvilPerLevelCost").getAsInt() : 1;
			keepEquipOnDeath = json.has("keepEquipOnDeath") ? json.get("keepEquipOnDeath").getAsBoolean() : false;
			enableBrokenMechanic = json.has("enableBrokenMechanic") ? json.get("enableBrokenMechanic").getAsBoolean() : true;

			// Custom block XP list. Each entry maps a block id to a positive XP
			// reward. Invalid or non-positive entries are dropped on load.
			customBlockXp.clear();
			if (json.has("customBlockXp") && json.get("customBlockXp").isJsonObject()) {
				JsonObject custom = json.getAsJsonObject("customBlockXp");
				custom.entrySet().forEach(entry -> {
					try {
						int v = entry.getValue().getAsInt();
						if (v > 0) customBlockXp.put(entry.getKey(), v);
					} catch (RuntimeException ignored) { }
				});
			}
			
			// Max slots per category
			if (json.has("maxSlots")) {
				JsonObject msObj = json.getAsJsonObject("maxSlots");
				msObj.entrySet().forEach(entry -> {
					try {
						int v = entry.getValue().getAsInt();
						if (v >= 1 && v <= 4) maxSlots.put(entry.getKey(), v);
					} catch (RuntimeException ignored) { }
				});
			}

			// Treat hand-edited config files as untrusted input.  Invalid values
			// should never make XP requirements, costs, or weighted offers unusable.
			xpMultiplier = Double.isFinite(xpMultiplier) ? Math.max(1.0, Math.min(10.0, xpMultiplier)) : 1.2;
			xpDisplayThreshold = Math.max(0, xpDisplayThreshold);
			durabilityRestorePercent = Math.max(0, Math.min(100, durabilityRestorePercent));
			coalXp = Math.max(0, coalXp);
			ironXp = Math.max(0, ironXp);
			goldXp = Math.max(0, goldXp);
			rareOreXp = Math.max(0, rareOreXp);
			legendaryUpgradeProbability = Double.isFinite(legendaryUpgradeProbability)
					? Math.max(0, Math.min(1, legendaryUpgradeProbability)) : 0.05;
			upgradeWeight = Double.isFinite(upgradeWeight) ? Math.max(0, upgradeWeight) : 0.6;
			newSlotWeight = Double.isFinite(newSlotWeight) ? Math.max(0, newSlotWeight) : 0.4;
			legendaryWeight = Double.isFinite(legendaryWeight) ? Math.max(0, legendaryWeight) : 0.05;
			anvilBaseCost = Math.max(0, anvilBaseCost);
			anvilPerLevelCost = Math.max(0, anvilPerLevelCost);
			for (int i = 0; i < rerollCosts.length; i++) rerollCosts[i] = Math.max(0, rerollCosts[i]);
			if (materialTiers == null || materialTiers.length == 0) {
				materialTiers = new String[]{"wood", "stone", "iron", "diamond", "netherite"};
			} else {
				materialTiers = java.util.Arrays.stream(materialTiers)
						.filter(s -> s != null && !s.isBlank())
						.map(s -> s.trim().toLowerCase()).toArray(String[]::new);
				if (materialTiers.length == 0) {
					materialTiers = new String[]{"wood", "stone", "iron", "diamond", "netherite"};
				}
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
			json.addProperty("entityKillXp", entityKillXp);
			json.addProperty("logXp", logXp);
			json.addProperty("shovelXp", shovelXp);
			json.addProperty("hoeXp", hoeXp);
			json.addProperty("stoneXp", stoneXp);
			json.addProperty("clayXp", clayXp);
			json.add("rerollCosts", GSON.toJsonTree(rerollCosts));
			json.addProperty("legendaryUpgradeProbability", legendaryUpgradeProbability);
			json.add("materialTiers", GSON.toJsonTree(materialTiers));
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
		Integer fallback = baseXp.get("default");
		if (fallback == null) fallback = 100;
		Integer value = baseXp.get(category == null ? "default" : category.toLowerCase());
		return Math.max(1, value == null ? fallback : value);
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

	public static int getEntityKillXp() { return entityKillXp; }
	public static int getLogXp() { return logXp; }
	public static int getShovelXp() { return shovelXp; }
	public static int getHoeXp() { return hoeXp; }
	public static int getStoneXp() { return stoneXp; }
	public static int getClayXp() { return clayXp; }

	public static void setEntityKillXp(int v) { entityKillXp = Math.max(0, v); save(); }
	public static void setLogXp(int v) { logXp = Math.max(0, v); save(); }
	public static void setShovelXp(int v) { shovelXp = Math.max(0, v); save(); }
	public static void setHoeXp(int v) { hoeXp = Math.max(0, v); save(); }
	public static void setStoneXp(int v) { stoneXp = Math.max(0, v); save(); }
	public static void setClayXp(int v) { clayXp = Math.max(0, v); save(); }

	public static int[] getRerollCosts() {
		return java.util.Arrays.copyOf(rerollCosts, rerollCosts.length);
	}

	public static double getLegendaryUpgradeProbability() {
		return legendaryUpgradeProbability;
	}

	public static String[] getMaterialTiers() {
		return java.util.Arrays.copyOf(materialTiers, materialTiers.length);
	}

	public static double getUpgradeWeight() {
		return upgradeWeight;
	}

	public static double getNewSlotWeight() {
		return newSlotWeight;
	}

	public static double getLegendaryWeight() {
		return legendaryWeight;
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

	/** Returns an immutable copy of the custom block -> XP map. */
	public static java.util.Map<String, Integer> getCustomBlockXp() {
		return java.util.Collections.unmodifiableMap(new HashMap<>(customBlockXp));
	}

	/** Sets the whole custom block XP map (validated) and saves. */
	public static void setCustomBlockXp(java.util.Map<String, Integer> map) {
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

	/** Adds or updates one custom block XP entry. */
	public static void addCustomBlockXp(String blockId, int xp) {
		if (blockId == null || blockId.isBlank() || xp <= 0) return;
		customBlockXp.put(blockId.trim().toLowerCase(), xp);
		save();
	}

	/** Removes a custom block XP entry; returns true if it existed. */
	public static boolean removeCustomBlockXp(String blockId) {
		if (blockId == null) return false;
		boolean removed = customBlockXp.remove(blockId.trim().toLowerCase()) != null;
		if (removed) save();
		return removed;
	}

	public static int getMaxSlotsForCategory(String category) {
		Integer fallback = maxSlots.get("default");
		if (fallback == null) fallback = 4;
		Integer value = maxSlots.get(category == null ? "default" : category.toLowerCase());
		// Allow up to 8 slots so players with large enchantment mods can raise the cap.
		return value == null ? fallback : Math.min(8, Math.max(1, value));
	}

	public static void setMaxSlotsForCategory(String category, int slots) {
		if (category == null || category.isBlank()) return;
		maxSlots.put(category.toLowerCase(), Math.min(8, Math.max(1, slots)));
		save();
	}

	// Setters
	public static void setBaseXpForCategory(String category, int xp) {
		if (category == null || category.isBlank()) return;
		baseXp.put(category.toLowerCase(), Math.max(1, xp));
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
		if (!Double.isFinite(value)) return;
		legendaryUpgradeProbability = Math.max(0, Math.min(1, value));
		save();
	}

	public static void setMaterialTiers(String[] tiers) {
		if (tiers == null || tiers.length == 0) throw new IllegalArgumentException("At least one material tier is required");
		materialTiers = java.util.Arrays.stream(tiers).filter(s -> s != null && !s.isBlank())
				.map(s -> s.trim().toLowerCase()).toArray(String[]::new);
		if (materialTiers.length == 0) throw new IllegalArgumentException("At least one material tier is required");
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
}
