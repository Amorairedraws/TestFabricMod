package com.amorairedraws.equipleveling.config;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mod Menu config screen built entirely with YACL v3 (Yet Another Config Lib).
 * Organised into three plain-language tabs:
 *
 * <ul>
 *   <li><b>Start Here</b> - global XP speed plus the few on/off switches most
 *       people care about.  Fine-tuning is tucked inside a collapsed
 *       "Advanced options" group.</li>
 *   <li><b>Earning XP</b> - how much XP each item needs to level up, and how
 *       much every action/mining reward gives.</li>
 *   <li><b>Leveling Up</b> - what the enchanting table offers (chances, reroll
 *       cost, material ladder, enchantment slots).</li>
 * </ul>
 */
public final class EquipLevelingConfigScreen {
    private static final String[] CATEGORIES = {
            "sword", "axe", "pickaxe", "shovel", "hoe", "fishing_rod", "bow",
            "helmet", "chestplate", "leggings", "boots"
    };

    private EquipLevelingConfigScreen() { }

    public static Screen create(Screen parent) {
        return YetAnotherConfigLib.createBuilder()
                .title(Text.translatable("equip_leveling.config.title"))
                .save(EquipLevelingConfig::save)
                .category(buildStartHere())
                .category(buildEarningXp())
                .category(buildLevelingUp(parent))
                .build()
                .generateScreen(parent);
    }

    // ======================================================================
    // Tab 1 - Start Here
    // ======================================================================
    private static ConfigCategory buildStartHere() {
        var builder = ConfigCategory.createBuilder()
                .name(Text.translatable("equip_leveling.config.category.general"))
                .tooltip(Text.translatable("equip_leveling.config.category.general.desc"));

        // The one setting most people change: global XP speed.
        builder.option(Option.<Float>createBuilder()
                .name(Text.translatable("equip_leveling.config.xp_multiplier"))
                .description(OptionDescription.of(Text.translatable("equip_leveling.config.xp_multiplier.tooltip")))
                .binding(
                        Binding.generic(
                                1.2f,
                                () -> (float) EquipLevelingConfig.getXpMultiplier(),
                                v -> EquipLevelingConfig.setXpMultiplier(v)
                        )
                )
                .controller(opt -> FloatSliderControllerBuilder.create(opt)
                        .range(0.1f, 10.0f)
                        .step(0.1f))
                .build());

        var advanced = OptionGroup.createBuilder()
                .name(Text.translatable("equip_leveling.config.sub.general_advanced"))
                .collapsed(true)
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.xp_threshold"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.xp_threshold.tooltip")))
                        .binding(10, EquipLevelingConfig::getXpDisplayThreshold, EquipLevelingConfig::setXpDisplayThreshold)
                        .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 100))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.durability_restore"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.durability_restore.tooltip")))
                        .binding(25, EquipLevelingConfig::getDurabilityRestorePercent, EquipLevelingConfig::setDurabilityRestorePercent)
                        .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 100))
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Text.translatable("equip_leveling.config.keep_on_death"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.keep_on_death.tooltip")))
                        .binding(false, EquipLevelingConfig::isKeepEquipOnDeath, EquipLevelingConfig::setKeepEquipOnDeath)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Text.translatable("equip_leveling.config.broken_mechanic"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.broken_mechanic.tooltip")))
                        .binding(true, EquipLevelingConfig::isBrokenMechanicEnabled, EquipLevelingConfig::setBrokenMechanicEnabled)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.anvil_base"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.anvil_base.tooltip")))
                        .binding(1, EquipLevelingConfig::getAnvilBaseCost,
                                v -> EquipLevelingConfig.setAnvilCosts(v, EquipLevelingConfig.getAnvilPerLevelCost()))
                        .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, Integer.MAX_VALUE))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.anvil_per_level"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.anvil_per_level.tooltip")))
                        .binding(1, EquipLevelingConfig::getAnvilPerLevelCost,
                                v -> EquipLevelingConfig.setAnvilCosts(EquipLevelingConfig.getAnvilBaseCost(), v))
                        .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, Integer.MAX_VALUE))
                        .build())
                .build();

        builder.group(advanced);
        return builder.build();
    }

    // ======================================================================
    // Tab 2 - Earning XP
    // ======================================================================
    private static ConfigCategory buildEarningXp() {
        var builder = ConfigCategory.createBuilder()
                .name(Text.translatable("equip_leveling.config.category.xp_rewards"))
                .tooltip(Text.translatable("equip_leveling.config.category.xp_rewards.desc"));

        // How much XP each item needs to level up.
        var requirements = OptionGroup.createBuilder()
                .name(Text.translatable("equip_leveling.config.sub.requirements"))
                .collapsed(true);
        for (String category : CATEGORIES) {
            final String cat = category;
            requirements.option(Option.<Integer>createBuilder()
                    .name(Text.translatable("equip_leveling.config.base_xp", pretty(cat)))
                    .description(OptionDescription.of(Text.translatable("equip_leveling.config.base_xp.tooltip", pretty(cat))))
                    .binding(defaultBaseXp(cat),
                            () -> EquipLevelingConfig.getBaseXpForCategory(cat),
                            v -> EquipLevelingConfig.setBaseXpForCategory(cat, v))
                    .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(1, Integer.MAX_VALUE))
                    .build());
        }
        builder.group(requirements.build());

        // XP for doing things.
        var actions = OptionGroup.createBuilder()
                .name(Text.translatable("equip_leveling.config.sub.actions"))
                .collapsed(true)
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.entity_kill_xp"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.entity_kill_xp.tooltip")))
                        .binding(10, EquipLevelingConfig::getEntityKillXp, EquipLevelingConfig::setEntityKillXp)
                        .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, Integer.MAX_VALUE))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.log_xp"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.log_xp.tooltip")))
                        .binding(4, EquipLevelingConfig::getLogXp, EquipLevelingConfig::setLogXp)
                        .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, Integer.MAX_VALUE))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.shovel_xp"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.shovel_xp.tooltip")))
                        .binding(1, EquipLevelingConfig::getShovelXp, EquipLevelingConfig::setShovelXp)
                        .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, Integer.MAX_VALUE))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.clay_xp"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.clay_xp.tooltip")))
                        .binding(5, EquipLevelingConfig::getClayXp, EquipLevelingConfig::setClayXp)
                        .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, Integer.MAX_VALUE))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.hoe_xp"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.hoe_xp.tooltip")))
                        .binding(3, EquipLevelingConfig::getHoeXp, EquipLevelingConfig::setHoeXp)
                        .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, Integer.MAX_VALUE))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.stone_xp"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.stone_xp.tooltip")))
                        .binding(1, EquipLevelingConfig::getStoneXp, EquipLevelingConfig::setStoneXp)
                        .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, Integer.MAX_VALUE))
                        .build())
                .build();
        builder.group(actions);

        // XP for mining ores.
        var ore = OptionGroup.createBuilder()
                .name(Text.translatable("equip_leveling.config.sub.ore"))
                .collapsed(true)
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.coal_xp"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.coal_xp.tooltip")))
                        .binding(3, EquipLevelingConfig::getCoalXp,
                                v -> saveOre(v, EquipLevelingConfig.getIronXp(), EquipLevelingConfig.getGoldXp(), EquipLevelingConfig.getRareOreXp()))
                        .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, Integer.MAX_VALUE))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.iron_xp"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.iron_xp.tooltip")))
                        .binding(8, EquipLevelingConfig::getIronXp,
                                v -> saveOre(EquipLevelingConfig.getCoalXp(), v, EquipLevelingConfig.getGoldXp(), EquipLevelingConfig.getRareOreXp()))
                        .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, Integer.MAX_VALUE))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.gold_xp"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.gold_xp.tooltip")))
                        .binding(20, EquipLevelingConfig::getGoldXp,
                                v -> saveOre(EquipLevelingConfig.getCoalXp(), EquipLevelingConfig.getIronXp(), v, EquipLevelingConfig.getRareOreXp()))
                        .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, Integer.MAX_VALUE))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.rare_ore_xp"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.rare_ore_xp.tooltip")))
                        .binding(40, EquipLevelingConfig::getRareOreXp,
                                v -> saveOre(EquipLevelingConfig.getCoalXp(), EquipLevelingConfig.getIronXp(), EquipLevelingConfig.getGoldXp(), v))
                        .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, Integer.MAX_VALUE))
                        .build())
                .build();
        builder.group(ore);

        // Extra blocks that give XP, entered as blockid:xp strings.
        var custom = OptionGroup.createBuilder()
                .name(Text.translatable("equip_leveling.config.sub.custom"))
                .collapsed(false)
                .option(ListOption.<String>createBuilder()
                        .name(Text.translatable("equip_leveling.config.custom_block_xp"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.custom_block_xp.tooltip")))
                        .binding(customBlockXpBinding())
                        .controller(StringControllerBuilder::create)
                        .collapsed(false)
                        .build())
                .build();
        builder.group(custom);

        return builder.build();
    }

    // ======================================================================
    // Tab 3 - Leveling Up
    // ======================================================================
    private static ConfigCategory buildLevelingUp(Screen parent) {
        var builder = ConfigCategory.createBuilder()
                .name(Text.translatable("equip_leveling.config.category.enchanting"))
                .tooltip(Text.translatable("equip_leveling.config.category.enchanting.desc"));

        // How often each reward appears.
        var weights = OptionGroup.createBuilder()
                .name(Text.translatable("equip_leveling.config.sub.weights"))
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.upgrade_weight"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.upgrade_weight.tooltip")))
                        .binding(60,
                                () -> (int) Math.round(EquipLevelingConfig.getUpgradeWeight()),
                                v -> EquipLevelingConfig.setOfferWeights(v, EquipLevelingConfig.getNewSlotWeight(), EquipLevelingConfig.getLegendaryWeight()))
                        .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, Integer.MAX_VALUE))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.new_slot_weight"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.new_slot_weight.tooltip")))
                        .binding(40,
                                () -> (int) Math.round(EquipLevelingConfig.getNewSlotWeight()),
                                v -> EquipLevelingConfig.setOfferWeights(EquipLevelingConfig.getUpgradeWeight(), v, EquipLevelingConfig.getLegendaryWeight()))
                        .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, Integer.MAX_VALUE))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.legendary_chance"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.legendary_chance.tooltip")))
                        .binding(5,
                                () -> (int) Math.round(EquipLevelingConfig.getLegendaryUpgradeProbability() * 100),
                                v -> EquipLevelingConfig.setLegendaryUpgradeProbability(v / 100.0))
                        .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 100))
                        .build())
                .build();
        builder.group(weights);

        // Reroll prices.
        var reroll = OptionGroup.createBuilder()
                .name(Text.translatable("equip_leveling.config.sub.reroll"))
                .collapsed(true);
        int[] costs = EquipLevelingConfig.getRerollCosts();
        for (int i = 0; i < costs.length; i++) {
            final int slotCount = i;
            final int defaultValue = (i + 1) * 5;
            reroll.option(Option.<Integer>createBuilder()
                    .name(Text.translatable("equip_leveling.config.reroll_cost", i))
                    .description(OptionDescription.of(Text.translatable("equip_leveling.config.reroll_cost.tooltip", i)))
                    .binding(defaultValue,
                            () -> EquipLevelingConfig.getRerollCosts()[slotCount],
                            value -> {
                                int[] updated = EquipLevelingConfig.getRerollCosts();
                                updated[slotCount] = value;
                                EquipLevelingConfig.setRerollCosts(updated);
                            })
                    .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, Integer.MAX_VALUE))
                    .build());
        }
        builder.group(reroll.build());

        // Material upgrade order - opens a dedicated editing screen.
        builder.group(OptionGroup.createBuilder()
                .name(Text.translatable("equip_leveling.config.sub.material_ladder"))
                .option(ButtonOption.createBuilder()
                        .name(Text.translatable("equip_leveling.config.material_ladder"))
                        .text(Text.translatable("equip_leveling.config.material_ladder.button"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.material_ladder.tooltip")))
                        .action((yaclScreen, buttonOption) ->
                                MinecraftClient.getInstance().setScreen(new MaterialLadderScreen(parent)))
                        .build())
                .build());

        // Enchantment slots per item.
        var maxSlots = OptionGroup.createBuilder()
                .name(Text.translatable("equip_leveling.config.sub.max_slots"))
                .collapsed(false);
        for (String category : CATEGORIES) {
            final String cat = category;
            maxSlots.option(Option.<Integer>createBuilder()
                    .name(Text.translatable("equip_leveling.config.max_slots", pretty(cat)))
                    .description(OptionDescription.of(Text.translatable("equip_leveling.config.max_slots.tooltip", pretty(cat))))
                    .binding(defaultMaxSlots(cat),
                            () -> EquipLevelingConfig.getMaxSlotsForCategory(cat),
                            v -> EquipLevelingConfig.setMaxSlotsForCategory(cat, v))
                    .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(1, 8))
                    .build());
        }
        builder.group(maxSlots.build());

        return builder.build();
    }

    // ======================================================================
    // Helpers
    // ======================================================================

    /**
     * Builds the binding for the custom block XP list. The config stores a
     * {@code Map<String,Integer>} keyed by block id; the screen edits a list of
     * {@code "id:xp"} strings. This binding converts between the two.
     */
    private static Binding<List<String>> customBlockXpBinding() {
        return Binding.generic(
                List.of(),
                () -> {
                    List<String> list = new ArrayList<>();
                    EquipLevelingConfig.getCustomBlockXp().forEach((id, v) -> list.add(id + ":" + v));
                    return list;
                },
                EquipLevelingConfigScreen::saveCustomBlockXp
        );
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
