package com.amorairedraws.equipleveling.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Cloth Config screen exposed through Mod Menu. Organised into three clear
 * tabs (General / XP Rewards / Enchanting), each with collapsible sub-categories
 * so everything is discoverable and nothing is buried in a wall of fields.
 */
public final class EquipLevelingConfigScreen {
    private static final String[] CATEGORIES = {
            "sword", "axe", "pickaxe", "shovel", "hoe", "fishing_rod", "bow",
            "helmet", "chestplate", "leggings", "boots"
    };

    private EquipLevelingConfigScreen() { }

    public static Screen create(Screen parent) {
        var builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("equip_leveling.config.title"))
                .setDoesConfirmSave(false)
                .setSavingRunnable(EquipLevelingConfig::save);
        var entries = builder.entryBuilder();

        buildGeneral(builder, entries);
        buildXpRewards(builder, entries);
        buildEnchanting(builder, entries);

        return builder.build();
    }

    // ------------------------------------------------------------------
    // General
    // ------------------------------------------------------------------
    private static void buildGeneral(ConfigBuilder builder, me.shedaniel.clothconfig2.api.ConfigEntryBuilder entries) {
        var general = builder.getOrCreateCategory(Text.translatable("equip_leveling.config.category.general"));
        general.addEntry(entries.startTextDescription(
                Text.translatable("equip_leveling.config.category.general.desc")).build());

        general.addEntry(entries.startDoubleField(
                Text.translatable("equip_leveling.config.xp_multiplier"), EquipLevelingConfig.getXpMultiplier())
                .setDefaultValue(1.2).setMin(1.0).setMax(10.0)
                .setTooltip(Text.translatable("equip_leveling.config.xp_multiplier.tooltip"))
                .setSaveConsumer(EquipLevelingConfig::setXpMultiplier).build());

        general.addEntry(entries.startIntField(
                Text.translatable("equip_leveling.config.xp_threshold"), EquipLevelingConfig.getXpDisplayThreshold())
                .setDefaultValue(10).setMin(0)
                .setTooltip(Text.translatable("equip_leveling.config.xp_threshold.tooltip"))
                .setSaveConsumer(EquipLevelingConfig::setXpDisplayThreshold).build());

        general.addEntry(entries.startIntSlider(
                Text.translatable("equip_leveling.config.durability_restore"), EquipLevelingConfig.getDurabilityRestorePercent(), 0, 100)
                .setDefaultValue(25)
                .setTooltip(Text.translatable("equip_leveling.config.durability_restore.tooltip"))
                .setSaveConsumer(EquipLevelingConfig::setDurabilityRestorePercent).build());

        general.addEntry(entries.startBooleanToggle(
                Text.translatable("equip_leveling.config.keep_on_death"), EquipLevelingConfig.isKeepEquipOnDeath())
                .setDefaultValue(false)
                .setTooltip(Text.translatable("equip_leveling.config.keep_on_death.tooltip"))
                .setSaveConsumer(EquipLevelingConfig::setKeepEquipOnDeath).build());

        general.addEntry(entries.startBooleanToggle(
                Text.translatable("equip_leveling.config.broken_mechanic"), EquipLevelingConfig.isBrokenMechanicEnabled())
                .setDefaultValue(true)
                .setTooltip(Text.translatable("equip_leveling.config.broken_mechanic.tooltip"))
                .setSaveConsumer(EquipLevelingConfig::setBrokenMechanicEnabled).build());

        // Anvil costs
        var anvil = entries.startSubCategory(Text.translatable("equip_leveling.config.sub.anvil"));
        anvil.add(entries.startIntField(
                Text.translatable("equip_leveling.config.anvil_base"), EquipLevelingConfig.getAnvilBaseCost())
                .setDefaultValue(1).setMin(0)
                .setTooltip(Text.translatable("equip_leveling.config.anvil_base.tooltip"))
                .setSaveConsumer(value -> EquipLevelingConfig.setAnvilCosts(value, EquipLevelingConfig.getAnvilPerLevelCost())).build());
        anvil.add(entries.startIntField(
                Text.translatable("equip_leveling.config.anvil_per_level"), EquipLevelingConfig.getAnvilPerLevelCost())
                .setDefaultValue(1).setMin(0)
                .setTooltip(Text.translatable("equip_leveling.config.anvil_per_level.tooltip"))
                .setSaveConsumer(value -> EquipLevelingConfig.setAnvilCosts(EquipLevelingConfig.getAnvilBaseCost(), value)).build());
        general.addEntry(anvil.setExpanded(false).build());
    }

    // ------------------------------------------------------------------
    // XP Rewards
    // ------------------------------------------------------------------
    private static void buildXpRewards(ConfigBuilder builder, me.shedaniel.clothconfig2.api.ConfigEntryBuilder entries) {
        var xp = builder.getOrCreateCategory(Text.translatable("equip_leveling.config.category.xp_rewards"));
        xp.addEntry(entries.startTextDescription(
                Text.translatable("equip_leveling.config.category.xp_rewards.desc")).build());

        // Level-up requirements (base XP per item type)
        var requirements = entries.startSubCategory(Text.translatable("equip_leveling.config.sub.requirements"));
        requirements.add(entries.startTextDescription(
                Text.translatable("equip_leveling.config.sub.requirements.desc")).build());
        for (String category : CATEGORIES) {
            requirements.add(entries.startIntField(
                    Text.translatable("equip_leveling.config.base_xp", pretty(category)),
                    EquipLevelingConfig.getBaseXpForCategory(category))
                    .setDefaultValue(defaultBaseXp(category)).setMin(1)
                    .setTooltip(Text.translatable("equip_leveling.config.base_xp.tooltip", pretty(category)))
                    .setSaveConsumer(value -> EquipLevelingConfig.setBaseXpForCategory(category, value)).build());
        }
        xp.addEntry(requirements.setExpanded(true).build());

        // Per-action rewards
        var actions = entries.startSubCategory(Text.translatable("equip_leveling.config.sub.actions"));
        actions.add(entries.startTextDescription(
                Text.translatable("equip_leveling.config.sub.actions.desc")).build());
        actions.add(entries.startIntField(
                Text.translatable("equip_leveling.config.entity_kill_xp"), EquipLevelingConfig.getEntityKillXp())
                .setDefaultValue(10).setMin(0)
                .setTooltip(Text.translatable("equip_leveling.config.entity_kill_xp.tooltip"))
                .setSaveConsumer(EquipLevelingConfig::setEntityKillXp).build());
        actions.add(entries.startIntField(
                Text.translatable("equip_leveling.config.log_xp"), EquipLevelingConfig.getLogXp())
                .setDefaultValue(4).setMin(0)
                .setTooltip(Text.translatable("equip_leveling.config.log_xp.tooltip"))
                .setSaveConsumer(EquipLevelingConfig::setLogXp).build());
        actions.add(entries.startIntField(
                Text.translatable("equip_leveling.config.shovel_xp"), EquipLevelingConfig.getShovelXp())
                .setDefaultValue(1).setMin(0)
                .setTooltip(Text.translatable("equip_leveling.config.shovel_xp.tooltip"))
                .setSaveConsumer(EquipLevelingConfig::setShovelXp).build());
        actions.add(entries.startIntField(
                Text.translatable("equip_leveling.config.clay_xp"), EquipLevelingConfig.getClayXp())
                .setDefaultValue(5).setMin(0)
                .setTooltip(Text.translatable("equip_leveling.config.clay_xp.tooltip"))
                .setSaveConsumer(EquipLevelingConfig::setClayXp).build());
        actions.add(entries.startIntField(
                Text.translatable("equip_leveling.config.hoe_xp"), EquipLevelingConfig.getHoeXp())
                .setDefaultValue(3).setMin(0)
                .setTooltip(Text.translatable("equip_leveling.config.hoe_xp.tooltip"))
                .setSaveConsumer(EquipLevelingConfig::setHoeXp).build());
        actions.add(entries.startIntField(
                Text.translatable("equip_leveling.config.stone_xp"), EquipLevelingConfig.getStoneXp())
                .setDefaultValue(1).setMin(0)
                .setTooltip(Text.translatable("equip_leveling.config.stone_xp.tooltip"))
                .setSaveConsumer(EquipLevelingConfig::setStoneXp).build());
        xp.addEntry(actions.setExpanded(true).build());

        // Ore & block rewards
        var ore = entries.startSubCategory(Text.translatable("equip_leveling.config.sub.ore"));
        ore.add(entries.startTextDescription(
                Text.translatable("equip_leveling.config.sub.ore.desc")).build());
        ore.add(entries.startIntField(Text.translatable("equip_leveling.config.coal_xp"), EquipLevelingConfig.getCoalXp())
                .setDefaultValue(3).setMin(0).setTooltip(Text.translatable("equip_leveling.config.coal_xp.tooltip"))
                .setSaveConsumer(value -> saveOre(value, EquipLevelingConfig.getIronXp(), EquipLevelingConfig.getGoldXp(), EquipLevelingConfig.getRareOreXp())).build());
        ore.add(entries.startIntField(Text.translatable("equip_leveling.config.iron_xp"), EquipLevelingConfig.getIronXp())
                .setDefaultValue(8).setMin(0).setTooltip(Text.translatable("equip_leveling.config.iron_xp.tooltip"))
                .setSaveConsumer(value -> saveOre(EquipLevelingConfig.getCoalXp(), value, EquipLevelingConfig.getGoldXp(), EquipLevelingConfig.getRareOreXp())).build());
        ore.add(entries.startIntField(Text.translatable("equip_leveling.config.gold_xp"), EquipLevelingConfig.getGoldXp())
                .setDefaultValue(20).setMin(0).setTooltip(Text.translatable("equip_leveling.config.gold_xp.tooltip"))
                .setSaveConsumer(value -> saveOre(EquipLevelingConfig.getCoalXp(), EquipLevelingConfig.getIronXp(), value, EquipLevelingConfig.getRareOreXp())).build());
        ore.add(entries.startIntField(Text.translatable("equip_leveling.config.rare_ore_xp"), EquipLevelingConfig.getRareOreXp())
                .setDefaultValue(40).setMin(0).setTooltip(Text.translatable("equip_leveling.config.rare_ore_xp.tooltip"))
                .setSaveConsumer(value -> saveOre(EquipLevelingConfig.getCoalXp(), EquipLevelingConfig.getIronXp(), EquipLevelingConfig.getGoldXp(), value)).build());
        xp.addEntry(ore.setExpanded(true).build());

        // Custom block rewards
        var custom = entries.startSubCategory(Text.translatable("equip_leveling.config.sub.custom"));
        custom.add(entries.startTextDescription(
                Text.translatable("equip_leveling.config.sub.custom.desc")).build());
        List<String> customList = new ArrayList<>();
        EquipLevelingConfig.getCustomBlockXp().forEach((id, v) -> customList.add(id + ":" + v));
        custom.add(entries.startStrList(
                Text.translatable("equip_leveling.config.custom_block_xp"), customList)
                .setDefaultValue(List.of())
                .setExpanded(true)
                .setAddButtonTooltip(Text.translatable("equip_leveling.config.custom_block_xp.add"))
                .setRemoveButtonTooltip(Text.translatable("equip_leveling.config.custom_block_xp.remove"))
                .setTooltip(Text.translatable("equip_leveling.config.custom_block_xp.tooltip"))
                .setCellErrorSupplier(EquipLevelingConfigScreen::validateBlockXpEntry)
                .setSaveConsumer(EquipLevelingConfigScreen::saveCustomBlockXp).build());
        xp.addEntry(custom.setExpanded(true).build());
    }

    // ------------------------------------------------------------------
    // Enchanting
    // ------------------------------------------------------------------
    private static void buildEnchanting(ConfigBuilder builder, me.shedaniel.clothconfig2.api.ConfigEntryBuilder entries) {
        var ench = builder.getOrCreateCategory(Text.translatable("equip_leveling.config.category.enchanting"));
        ench.addEntry(entries.startTextDescription(
                Text.translatable("equip_leveling.config.category.enchanting.desc")).build());

        // Offer weights
        var weights = entries.startSubCategory(Text.translatable("equip_leveling.config.sub.weights"));
        weights.add(entries.startTextDescription(
                Text.translatable("equip_leveling.config.sub.weights.desc")).build());
        weights.add(entries.startIntField(
                Text.translatable("equip_leveling.config.upgrade_weight"),
                (int) Math.round(EquipLevelingConfig.getUpgradeWeight()))
                .setDefaultValue(60).setMin(0)
                .setTooltip(Text.translatable("equip_leveling.config.upgrade_weight.tooltip"))
                .setSaveConsumer(v -> EquipLevelingConfig.setOfferWeights(v, EquipLevelingConfig.getNewSlotWeight(), EquipLevelingConfig.getLegendaryWeight())).build());
        weights.add(entries.startIntField(
                Text.translatable("equip_leveling.config.new_slot_weight"),
                (int) Math.round(EquipLevelingConfig.getNewSlotWeight()))
                .setDefaultValue(40).setMin(0)
                .setTooltip(Text.translatable("equip_leveling.config.new_slot_weight.tooltip"))
                .setSaveConsumer(v -> EquipLevelingConfig.setOfferWeights(EquipLevelingConfig.getUpgradeWeight(), v, EquipLevelingConfig.getLegendaryWeight())).build());
        weights.add(entries.startIntSlider(
                Text.translatable("equip_leveling.config.legendary_chance"),
                (int) Math.round(EquipLevelingConfig.getLegendaryUpgradeProbability() * 100), 0, 100)
                .setDefaultValue(5)
                .setTooltip(Text.translatable("equip_leveling.config.legendary_chance.tooltip"))
                .setSaveConsumer(v -> EquipLevelingConfig.setLegendaryUpgradeProbability(v / 100.0)).build());
        ench.addEntry(weights.setExpanded(true).build());

        // Reroll costs
        var reroll = entries.startSubCategory(Text.translatable("equip_leveling.config.sub.reroll"));
        reroll.add(entries.startTextDescription(
                Text.translatable("equip_leveling.config.sub.reroll.desc")).build());
        int[] costs = EquipLevelingConfig.getRerollCosts();
        for (int i = 0; i < costs.length; i++) {
            final int slotCount = i;
            reroll.add(entries.startIntField(
                    Text.translatable("equip_leveling.config.reroll_cost", i), costs[i])
                    .setDefaultValue((i + 1) * 5).setMin(0)
                    .setTooltip(Text.translatable("equip_leveling.config.reroll_cost.tooltip", i))
                    .setSaveConsumer(value -> {
                        int[] updated = EquipLevelingConfig.getRerollCosts();
                        updated[slotCount] = value;
                        EquipLevelingConfig.setRerollCosts(updated);
                    }).build());
        }
        ench.addEntry(reroll.setExpanded(true).build());

        // Material tiers
        var tiers = entries.startSubCategory(Text.translatable("equip_leveling.config.sub.tiers"));
        tiers.add(entries.startTextDescription(
                Text.translatable("equip_leveling.config.sub.tiers.desc")).build());
        tiers.add(entries.startStrList(
                Text.translatable("equip_leveling.config.material_tiers"),
                Arrays.asList(EquipLevelingConfig.getMaterialTiers()))
                .setDefaultValue(List.of("wood", "stone", "iron", "diamond", "netherite"))
                .setExpanded(true)
                .setAddButtonTooltip(Text.translatable("equip_leveling.config.material_tiers.add"))
                .setRemoveButtonTooltip(Text.translatable("equip_leveling.config.material_tiers.remove"))
                .setTooltip(Text.translatable("equip_leveling.config.material_tiers.tooltip"))
                .setSaveConsumer(list -> EquipLevelingConfig.setMaterialTiers(
                        list == null ? new String[0] : list.toArray(new String[0]))).build());
        ench.addEntry(tiers.setExpanded(true).build());

        // Max slots per item
        var maxSlots = entries.startSubCategory(Text.translatable("equip_leveling.config.sub.max_slots"));
        maxSlots.add(entries.startTextDescription(
                Text.translatable("equip_leveling.config.sub.max_slots.desc")).build());
        for (String category : CATEGORIES) {
            maxSlots.add(entries.startIntSlider(
                    Text.translatable("equip_leveling.config.max_slots", pretty(category)),
                    EquipLevelingConfig.getMaxSlotsForCategory(category), 1, 8)
                    .setDefaultValue(defaultMaxSlots(category))
                    .setTooltip(Text.translatable("equip_leveling.config.max_slots.tooltip", pretty(category)))
                    .setSaveConsumer(value -> EquipLevelingConfig.setMaxSlotsForCategory(category, value)).build());
        }
        ench.addEntry(maxSlots.setExpanded(true).build());
    }

    private static java.util.Optional<Text> validateBlockXpEntry(String value) {
        if (value == null || value.isBlank()) return java.util.Optional.of(Text.literal("Empty entry"));
        String[] parts = value.split(":");
        if (parts.length < 2) return java.util.Optional.of(Text.literal("Use format blockid:xp"));
        String xpPart = parts[parts.length - 1];
        try {
            if (Integer.parseInt(xpPart) <= 0) return java.util.Optional.of(Text.literal("XP must be a positive number"));
        } catch (NumberFormatException e) {
            return java.util.Optional.of(Text.literal("XP must be a positive number"));
        }
        return java.util.Optional.empty();
    }

    private static void saveCustomBlockXp(List<String> list) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (list != null) {
            for (String entry : list) {
                if (entry == null || entry.isBlank()) continue;
                String[] parts = entry.split(":");
                if (parts.length < 2) continue;
                String xpPart = parts[parts.length - 1];
                String id = String.join(":", Arrays.copyOf(parts, parts.length - 1));
                try {
                    int xp = Integer.parseInt(xpPart);
                    if (xp > 0 && !id.isBlank()) map.put(id.trim().toLowerCase(), xp);
                } catch (NumberFormatException ignored) { }
            }
        }
        EquipLevelingConfig.setCustomBlockXp(map);
    }

    private static void saveOre(int coal, int iron, int gold, int rare) {
        EquipLevelingConfig.setOreXp(coal, iron, gold, rare);
    }

    private static int defaultBaseXp(String category) {
        return switch (category) {
            case "sword" -> 400;
            case "axe" -> 450;
            case "pickaxe" -> 500;
            case "shovel" -> 300;
            case "hoe" -> 200;
            case "fishing_rod" -> 300;
            case "bow" -> 350;
            case "helmet", "boots" -> 350;
            case "chestplate", "leggings" -> 400;
            default -> 350;
        };
    }

    private static int defaultMaxSlots(String category) {
        return switch (category) {
            case "sword", "helmet", "chestplate", "leggings", "boots" -> 4;
            case "axe", "pickaxe", "fishing_rod", "bow" -> 3;
            case "shovel", "hoe" -> 2;
            default -> 4;
        };
    }

    private static String pretty(String value) {
        return Arrays.stream(value.split("_"))
                .map(part -> part.isEmpty() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .reduce((a, b) -> a + " " + b).orElse(value);
    }
}
