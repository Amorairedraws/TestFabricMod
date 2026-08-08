package com.amorairedraws.equipleveling.config;

import java.util.Arrays;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Cloth Config screen exposed through Mod Menu.  Keeping the screen in Cloth
 * Config means it gets the same lifecycle and background rendering as other
 * config screens, rather than manually rendering a second blurred background.
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

        var general = builder.getOrCreateCategory(Text.translatable("equip_leveling.config.category.general"));
        general.addEntry(entries.startDoubleField(
                Text.translatable("equip_leveling.config.xp_multiplier"), EquipLevelingConfig.getXpMultiplier())
                .setDefaultValue(1.2).setMin(1.0).setMax(10.0)
                .setSaveConsumer(EquipLevelingConfig::setXpMultiplier).build());
        general.addEntry(entries.startIntField(
                Text.translatable("equip_leveling.config.xp_threshold"), EquipLevelingConfig.getXpDisplayThreshold())
                .setDefaultValue(10).setMin(0)
                .setSaveConsumer(EquipLevelingConfig::setXpDisplayThreshold).build());
        general.addEntry(entries.startIntField(
                Text.translatable("equip_leveling.config.durability_restore"), EquipLevelingConfig.getDurabilityRestorePercent())
                .setDefaultValue(25).setMin(0).setMax(100)
                .setSaveConsumer(EquipLevelingConfig::setDurabilityRestorePercent).build());
        general.addEntry(entries.startDoubleField(
                Text.translatable("equip_leveling.config.legendary_probability"), EquipLevelingConfig.getLegendaryUpgradeProbability())
                .setDefaultValue(0.05).setMin(0.0).setMax(1.0)
                .setSaveConsumer(EquipLevelingConfig::setLegendaryUpgradeProbability).build());
        general.addEntry(entries.startDoubleField(
                Text.translatable("equip_leveling.config.upgrade_weight"), EquipLevelingConfig.getUpgradeWeight())
                .setDefaultValue(0.6).setMin(0.0)
                .setSaveConsumer(value -> EquipLevelingConfig.setOfferWeights(value, EquipLevelingConfig.getNewSlotWeight())).build());
        general.addEntry(entries.startDoubleField(
                Text.translatable("equip_leveling.config.new_slot_weight"), EquipLevelingConfig.getNewSlotWeight())
                .setDefaultValue(0.4).setMin(0.0)
                .setSaveConsumer(value -> EquipLevelingConfig.setOfferWeights(EquipLevelingConfig.getUpgradeWeight(), value)).build());
        general.addEntry(entries.startIntField(
                Text.translatable("equip_leveling.config.anvil_base"), EquipLevelingConfig.getAnvilBaseCost())
                .setDefaultValue(1).setMin(0)
                .setSaveConsumer(value -> EquipLevelingConfig.setAnvilCosts(value, EquipLevelingConfig.getAnvilPerLevelCost())).build());
        general.addEntry(entries.startIntField(
                Text.translatable("equip_leveling.config.anvil_per_level"), EquipLevelingConfig.getAnvilPerLevelCost())
                .setDefaultValue(1).setMin(0)
                .setSaveConsumer(value -> EquipLevelingConfig.setAnvilCosts(EquipLevelingConfig.getAnvilBaseCost(), value)).build());
        general.addEntry(entries.startBooleanToggle(
                Text.translatable("equip_leveling.config.keep_on_death"), EquipLevelingConfig.isKeepEquipOnDeath())
                .setDefaultValue(false).setSaveConsumer(EquipLevelingConfig::setKeepEquipOnDeath).build());
        general.addEntry(entries.startBooleanToggle(
                Text.translatable("equip_leveling.config.broken_mechanic"), EquipLevelingConfig.isBrokenMechanicEnabled())
                .setDefaultValue(true).setSaveConsumer(EquipLevelingConfig::setBrokenMechanicEnabled).build());
        general.addEntry(entries.startStrField(
                Text.translatable("equip_leveling.config.material_tiers"), String.join(", ", EquipLevelingConfig.getMaterialTiers()))
                .setDefaultValue("wood, stone, iron, diamond, netherite")
                .setSaveConsumer(value -> EquipLevelingConfig.setMaterialTiers(value.split(","))).build());

        var reroll = builder.getOrCreateCategory(Text.translatable("equip_leveling.config.category.reroll"));
        int[] costs = EquipLevelingConfig.getRerollCosts();
        for (int i = 0; i < costs.length; i++) {
            final int slotCount = i;
            reroll.addEntry(entries.startIntField(
                    Text.translatable("equip_leveling.config.reroll_cost", i), costs[i])
                    .setDefaultValue((i + 1) * 5).setMin(0)
                    .setSaveConsumer(value -> {
                        int[] updated = EquipLevelingConfig.getRerollCosts();
                        updated[slotCount] = value;
                        EquipLevelingConfig.setRerollCosts(updated);
                    }).build());
        }

        var xp = builder.getOrCreateCategory(Text.translatable("equip_leveling.config.category.base_xp"));
        for (String category : CATEGORIES) {
            xp.addEntry(entries.startIntField(
                    Text.translatable("equip_leveling.config.base_xp", pretty(category)),
                    EquipLevelingConfig.getBaseXpForCategory(category))
                    .setDefaultValue(100).setMin(1)
                    .setSaveConsumer(value -> EquipLevelingConfig.setBaseXpForCategory(category, value)).build());
        }

        var ore = builder.getOrCreateCategory(Text.translatable("equip_leveling.config.category.ore_xp"));
        ore.addEntry(entries.startIntField(Text.translatable("equip_leveling.config.coal_xp"), EquipLevelingConfig.getCoalXp())
                .setDefaultValue(5).setMin(0).setSaveConsumer(value -> saveOre(value, EquipLevelingConfig.getIronXp(), EquipLevelingConfig.getGoldXp(), EquipLevelingConfig.getRareOreXp())).build());
        ore.addEntry(entries.startIntField(Text.translatable("equip_leveling.config.iron_xp"), EquipLevelingConfig.getIronXp())
                .setDefaultValue(40).setMin(0).setSaveConsumer(value -> saveOre(EquipLevelingConfig.getCoalXp(), value, EquipLevelingConfig.getGoldXp(), EquipLevelingConfig.getRareOreXp())).build());
        ore.addEntry(entries.startIntField(Text.translatable("equip_leveling.config.gold_xp"), EquipLevelingConfig.getGoldXp())
                .setDefaultValue(80).setMin(0).setSaveConsumer(value -> saveOre(EquipLevelingConfig.getCoalXp(), EquipLevelingConfig.getIronXp(), value, EquipLevelingConfig.getRareOreXp())).build());
        ore.addEntry(entries.startIntField(Text.translatable("equip_leveling.config.rare_ore_xp"), EquipLevelingConfig.getRareOreXp())
                .setDefaultValue(150).setMin(0).setSaveConsumer(value -> saveOre(EquipLevelingConfig.getCoalXp(), EquipLevelingConfig.getIronXp(), EquipLevelingConfig.getGoldXp(), value)).build());

        return builder.build();
    }

    private static void saveOre(int coal, int iron, int gold, int rare) {
        EquipLevelingConfig.setOreXp(coal, iron, gold, rare);
    }

    private static String pretty(String value) {
        return Arrays.stream(value.split("_"))
                .map(part -> part.isEmpty() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .reduce((a, b) -> a + " " + b).orElse(value);
    }
}
