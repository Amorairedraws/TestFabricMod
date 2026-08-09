package com.amorairedraws.equipleveling.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Cloth Config screen exposed through Mod Menu.  Organised into clear,
 * plain-language categories.  Offer generation is tuned through simple weight
 * fields (upgrade / new enchant / legendary) rather than confusing percentages,
 * and each item category has an editable max-slot count.
 */
public final class EquipLevelingConfigScreen {
    private static final String[] CATEGORIES = {
            "sword", "axe", "pickaxe", "shovel", "hoe", "fishing_rod",
            "helmet", "chestplate", "leggings", "boots", "default"
    };

    private EquipLevelingConfigScreen() { }

    public static Screen create(Screen parent) {
        var builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("equip_leveling.config.title"))
                .setDoesConfirmSave(false)
                .setSavingRunnable(EquipLevelingConfig::save);
        var entries = builder.entryBuilder();

        // ------------------------------------------------------------------
        // General
        // ------------------------------------------------------------------
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

        // Anvil
        general.addEntry(entries.startIntField(
                Text.translatable("equip_leveling.config.anvil_base"), EquipLevelingConfig.getAnvilBaseCost())
                .setDefaultValue(1).setMin(0)
                .setTooltip(Text.translatable("equip_leveling.config.anvil_base.tooltip"))
                .setSaveConsumer(value -> EquipLevelingConfig.setAnvilCosts(value, EquipLevelingConfig.getAnvilPerLevelCost())).build());
        general.addEntry(entries.startIntField(
                Text.translatable("equip_leveling.config.anvil_per_level"), EquipLevelingConfig.getAnvilPerLevelCost())
                .setDefaultValue(1).setMin(0)
                .setTooltip(Text.translatable("equip_leveling.config.anvil_per_level.tooltip"))
                .setSaveConsumer(value -> EquipLevelingConfig.setAnvilCosts(EquipLevelingConfig.getAnvilBaseCost(), value)).build());

        // ------------------------------------------------------------------
        // Offer weights (Issue 14: clear weight fields, not confusing %)
        // ------------------------------------------------------------------
        var offers = builder.getOrCreateCategory(Text.translatable("equip_leveling.config.category.offers"));
        offers.addEntry(entries.startTextDescription(
                Text.translatable("equip_leveling.config.category.offers.desc")).build());

        offers.addEntry(entries.startIntField(
                Text.translatable("equip_leveling.config.upgrade_weight"),
                (int) Math.round(EquipLevelingConfig.getUpgradeWeight()))
                .setDefaultValue(60).setMin(0)
                .setTooltip(Text.translatable("equip_leveling.config.upgrade_weight.tooltip"))
                .setSaveConsumer(v -> EquipLevelingConfig.setOfferWeights(v, EquipLevelingConfig.getNewSlotWeight(), EquipLevelingConfig.getLegendaryWeight())).build());
        offers.addEntry(entries.startIntField(
                Text.translatable("equip_leveling.config.new_slot_weight"),
                (int) Math.round(EquipLevelingConfig.getNewSlotWeight()))
                .setDefaultValue(40).setMin(0)
                .setTooltip(Text.translatable("equip_leveling.config.new_slot_weight.tooltip"))
                .setSaveConsumer(v -> EquipLevelingConfig.setOfferWeights(EquipLevelingConfig.getUpgradeWeight(), v, EquipLevelingConfig.getLegendaryWeight())).build());
        offers.addEntry(entries.startIntField(
                Text.translatable("equip_leveling.config.legendary_probability"),
                (int) Math.round(EquipLevelingConfig.getLegendaryWeight()))
                .setDefaultValue(5).setMin(0)
                .setTooltip(Text.translatable("equip_leveling.config.legendary_probability.tooltip"))
                .setSaveConsumer(v -> EquipLevelingConfig.setOfferWeights(EquipLevelingConfig.getUpgradeWeight(), EquipLevelingConfig.getNewSlotWeight(), v)).build());

        // ------------------------------------------------------------------
        // Max slots per category (Issue 7)
        // ------------------------------------------------------------------
        var maxSlotsCat = builder.getOrCreateCategory(Text.translatable("equip_leveling.config.category.max_slots"));
        maxSlotsCat.addEntry(entries.startTextDescription(
                Text.translatable("equip_leveling.config.category.max_slots.desc")).build());
        for (String category : CATEGORIES) {
            maxSlotsCat.addEntry(entries.startIntSlider(
                    Text.translatable("equip_leveling.config.max_slots", pretty(category)),
                    EquipLevelingConfig.getMaxSlotsForCategory(category), 1, 4)
                    .setDefaultValue(defaultMaxSlots(category))
                    .setTooltip(Text.translatable("equip_leveling.config.max_slots.tooltip", pretty(category)))
                    .setSaveConsumer(value -> EquipLevelingConfig.setMaxSlotsForCategory(category, value)).build());
        }

        // ------------------------------------------------------------------
        // XP per action
        // ------------------------------------------------------------------
        var xp = builder.getOrCreateCategory(Text.translatable("equip_leveling.config.category.base_xp"));
        xp.addEntry(entries.startTextDescription(
                Text.translatable("equip_leveling.config.category.base_xp.desc")).build());
        for (String category : CATEGORIES) {
            xp.addEntry(entries.startIntField(
                    Text.translatable("equip_leveling.config.base_xp", pretty(category)),
                    EquipLevelingConfig.getBaseXpForCategory(category))
                    .setDefaultValue(defaultBaseXp(category)).setMin(1)
                    .setTooltip(Text.translatable("equip_leveling.config.base_xp.tooltip", pretty(category)))
                    .setSaveConsumer(value -> EquipLevelingConfig.setBaseXpForCategory(category, value)).build());
        }

        // ------------------------------------------------------------------
        // Ore / block rewards
        // ------------------------------------------------------------------
        var ore = builder.getOrCreateCategory(Text.translatable("equip_leveling.config.category.ore_xp"));
        ore.addEntry(entries.startTextDescription(
                Text.translatable("equip_leveling.config.category.ore_xp.desc")).build());
        ore.addEntry(entries.startIntField(Text.translatable("equip_leveling.config.coal_xp"), EquipLevelingConfig.getCoalXp())
                .setDefaultValue(3).setMin(0).setTooltip(Text.translatable("equip_leveling.config.coal_xp.tooltip"))
                .setSaveConsumer(value -> saveOre(value, EquipLevelingConfig.getIronXp(), EquipLevelingConfig.getGoldXp(), EquipLevelingConfig.getRareOreXp())).build());
        ore.addEntry(entries.startIntField(Text.translatable("equip_leveling.config.iron_xp"), EquipLevelingConfig.getIronXp())
                .setDefaultValue(8).setMin(0).setTooltip(Text.translatable("equip_leveling.config.iron_xp.tooltip"))
                .setSaveConsumer(value -> saveOre(EquipLevelingConfig.getCoalXp(), value, EquipLevelingConfig.getGoldXp(), EquipLevelingConfig.getRareOreXp())).build());
        ore.addEntry(entries.startIntField(Text.translatable("equip_leveling.config.gold_xp"), EquipLevelingConfig.getGoldXp())
                .setDefaultValue(20).setMin(0).setTooltip(Text.translatable("equip_leveling.config.gold_xp.tooltip"))
                .setSaveConsumer(value -> saveOre(EquipLevelingConfig.getCoalXp(), EquipLevelingConfig.getIronXp(), value, EquipLevelingConfig.getRareOreXp())).build());
        ore.addEntry(entries.startIntField(Text.translatable("equip_leveling.config.rare_ore_xp"), EquipLevelingConfig.getRareOreXp())
                .setDefaultValue(40).setMin(0).setTooltip(Text.translatable("equip_leveling.config.rare_ore_xp.tooltip"))
                .setSaveConsumer(value -> saveOre(EquipLevelingConfig.getCoalXp(), EquipLevelingConfig.getIronXp(), EquipLevelingConfig.getGoldXp(), value)).build());

        List<String> custom = new ArrayList<>();
        EquipLevelingConfig.getCustomBlockXp().forEach((id, v) -> custom.add(id + ":" + v));
        ore.addEntry(entries.startStrList(
                Text.translatable("equip_leveling.config.custom_block_xp"), custom)
                .setDefaultValue(List.of())
                .setExpanded(true)
                .setAddButtonTooltip(Text.translatable("equip_leveling.config.custom_block_xp.add"))
                .setRemoveButtonTooltip(Text.translatable("equip_leveling.config.custom_block_xp.remove"))
                .setTooltip(Text.translatable("equip_leveling.config.custom_block_xp.tooltip"))
                .setCellErrorSupplier(EquipLevelingConfigScreen::validateBlockXpEntry)
                .setSaveConsumer(EquipLevelingConfigScreen::saveCustomBlockXp).build());

        // ------------------------------------------------------------------
        // Material tiers
        // ------------------------------------------------------------------
        var tiers = builder.getOrCreateCategory(Text.translatable("equip_leveling.config.category.tiers"));
        tiers.addEntry(entries.startTextDescription(
                Text.translatable("equip_leveling.config.category.tiers.desc")).build());
        tiers.addEntry(entries.startStrList(
                Text.translatable("equip_leveling.config.material_tiers"),
                Arrays.asList(EquipLevelingConfig.getMaterialTiers()))
                .setDefaultValue(List.of("wood", "stone", "iron", "diamond", "netherite"))
                .setExpanded(true)
                .setAddButtonTooltip(Text.translatable("equip_leveling.config.material_tiers.add"))
                .setRemoveButtonTooltip(Text.translatable("equip_leveling.config.material_tiers.remove"))
                .setTooltip(Text.translatable("equip_leveling.config.material_tiers.tooltip"))
                .setSaveConsumer(list -> EquipLevelingConfig.setMaterialTiers(
                        list == null ? new String[0] : list.toArray(new String[0]))).build());

        // ------------------------------------------------------------------
        // Reroll costs
        // ------------------------------------------------------------------
        var reroll = builder.getOrCreateCategory(Text.translatable("equip_leveling.config.category.reroll"));
        reroll.addEntry(entries.startTextDescription(
                Text.translatable("equip_leveling.config.category.reroll.desc")).build());
        int[] costs = EquipLevelingConfig.getRerollCosts();
        for (int i = 0; i < costs.length; i++) {
            final int slotCount = i;
            reroll.addEntry(entries.startIntField(
                    Text.translatable("equip_leveling.config.reroll_cost", i), costs[i])
                    .setDefaultValue((i + 1) * 5).setMin(0)
                    .setTooltip(Text.translatable("equip_leveling.config.reroll_cost.tooltip", i))
                    .setSaveConsumer(value -> {
                        int[] updated = EquipLevelingConfig.getRerollCosts();
                        updated[slotCount] = value;
                        EquipLevelingConfig.setRerollCosts(updated);
                    }).build());
        }

        return builder.build();
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
            case "helmet", "boots" -> 350;
            case "chestplate", "leggings" -> 400;
            default -> 350;
        };
    }

    private static int defaultMaxSlots(String category) {
        return switch (category) {
            case "sword", "helmet", "chestplate", "leggings", "boots", "default" -> 4;
            case "axe", "pickaxe", "fishing_rod" -> 3;
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
