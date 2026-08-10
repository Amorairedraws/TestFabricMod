package com.amorairedraws.equipleveling.config;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.*;

import com.amorairedraws.equipleveling.network.ConfigSyncPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Mod Menu config screen built with YACL v3.
 *
 * <h3>Layout</h3>
 * <ul>
 *   <li><b>Start Here</b> — level-up requirement growth, global XP gain, advanced toggles.</li>
 *   <li><b>Earning XP</b> — base XP per category + per-source multipliers in a collapsed group.</li>
 *   <li><b>Leveling Up</b> — enchanting weights, reroll costs, material ladder, enchantment slots.</li>
 * </ul>
 *
 * <p>In multiplayer, non-OP players see a read-only view of the server config.
 * OP players (level 2+) can edit and changes are broadcast to all players live.
 */
public final class EquipLevelingConfigScreen {
    private static final String[] CATEGORIES = {
            "sword", "axe", "pickaxe", "shovel", "hoe", "fishing_rod", "bow",
            "helmet", "chestplate", "leggings", "boots"
    };

    private static final String[] SOURCE_KEYS = {"mob", "livestock", "mining", "farming", "wood", "fishing"};

    private EquipLevelingConfigScreen() {}

    public static Screen create(Screen parent) {
        boolean canEdit = canEditConfig();

        return YetAnotherConfigLib.createBuilder()
                .title(Text.translatable("equip_leveling.config.title"))
                .save(() -> {
                    EquipLevelingConfig.save();
                    // In singleplayer, no sync needed. In multiplayer, the client
                    // is read-only anyway (config edits are done server-side).
                })
                .category(buildStartHere(canEdit))
                .category(buildEarningXp(canEdit))
                .category(buildLevelingUp(parent, canEdit))
                .build()
                .generateScreen(parent);
    }

    /** Returns true if the player can edit the config (singleplayer only; multiplayer is read-only). */
    private static boolean canEditConfig() {
        return MinecraftClient.getInstance().isInSingleplayer();
    }

    // ================================================================== //
    // Tab 1 — Start Here                                                  //
    // ================================================================== //

    private static ConfigCategory buildStartHere(boolean canEdit) {
        var builder = ConfigCategory.createBuilder()
                .name(Text.translatable("equip_leveling.config.category.general"))
                .tooltip(Text.translatable("equip_leveling.config.category.general.desc"));

        // Level-up requirement growth.
        builder.option(Option.<Float>createBuilder()
                .name(Text.translatable("equip_leveling.config.level_req_growth"))
                .description(OptionDescription.of(Text.translatable("equip_leveling.config.level_req_growth.tooltip")))
                .binding(
                        Binding.generic(1.2f,
                                () -> (float) EquipLevelingConfig.getLevelRequirementGrowth(),
                                v -> EquipLevelingConfig.setLevelRequirementGrowth(v))
                )
                .controller(opt -> FloatSliderControllerBuilder.create(opt).range(1.0f, 10.0f).step(0.1f))
                .available(canEdit)
                .build());

        // Global XP gain multiplier.
        builder.option(Option.<Float>createBuilder()
                .name(Text.translatable("equip_leveling.config.global_gain"))
                .description(OptionDescription.of(Text.translatable("equip_leveling.config.global_gain.tooltip")))
                .binding(
                        Binding.generic(1.0f,
                                () -> (float) EquipLevelingConfig.getGlobalXpGainMultiplier(),
                                v -> EquipLevelingConfig.setGlobalXpGainMultiplier(v))
                )
                .controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.1f, 10.0f).step(0.1f))
                .available(canEdit)
                .build());

        // Advanced options (collapsed).
        var advanced = OptionGroup.createBuilder()
                .name(Text.translatable("equip_leveling.config.sub.general_advanced"))
                .collapsed(true)
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.xp_threshold"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.xp_threshold.tooltip")))
                        .binding(10, EquipLevelingConfig::getXpDisplayThreshold, EquipLevelingConfig::setXpDisplayThreshold)
                        .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 100).step(1))
                        .available(canEdit)
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.durability_restore"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.durability_restore.tooltip")))
                        .binding(25, EquipLevelingConfig::getDurabilityRestorePercent, EquipLevelingConfig::setDurabilityRestorePercent)
                        .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 100).step(1))
                        .available(canEdit)
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Text.translatable("equip_leveling.config.keep_on_death"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.keep_on_death.tooltip")))
                        .binding(false, EquipLevelingConfig::isKeepEquipOnDeath, EquipLevelingConfig::setKeepEquipOnDeath)
                        .controller(TickBoxControllerBuilder::create)
                        .available(canEdit)
                        .build())
                .option(Option.<Boolean>createBuilder()
                        .name(Text.translatable("equip_leveling.config.broken_mechanic"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.broken_mechanic.tooltip")))
                        .binding(true, EquipLevelingConfig::isBrokenMechanicEnabled, EquipLevelingConfig::setBrokenMechanicEnabled)
                        .controller(TickBoxControllerBuilder::create)
                        .available(canEdit)
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.anvil_base"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.anvil_base.tooltip")))
                        .binding(1, EquipLevelingConfig::getAnvilBaseCost,
                                v -> EquipLevelingConfig.setAnvilCosts(v, EquipLevelingConfig.getAnvilPerLevelCost()))
                        .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, Integer.MAX_VALUE))
                        .available(canEdit)
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.anvil_per_level"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.anvil_per_level.tooltip")))
                        .binding(1, EquipLevelingConfig::getAnvilPerLevelCost,
                                v -> EquipLevelingConfig.setAnvilCosts(EquipLevelingConfig.getAnvilBaseCost(), v))
                        .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(0, Integer.MAX_VALUE))
                        .available(canEdit)
                        .build())
                .build();

        builder.group(advanced);
        return builder.build();
    }

    // ================================================================== //
    // Tab 2 — Earning XP                                                  //
    // ================================================================== //

    private static ConfigCategory buildEarningXp(boolean canEdit) {
        var builder = ConfigCategory.createBuilder()
                .name(Text.translatable("equip_leveling.config.category.xp_rewards"))
                .tooltip(Text.translatable("equip_leveling.config.category.xp_rewards.desc"));

        // Base XP required to level up per category.
        var requirements = OptionGroup.createBuilder()
                .name(Text.translatable("equip_leveling.config.sub.requirements"))
                .description(OptionDescription.of(Text.translatable("equip_leveling.config.sub.requirements.desc")))
                .collapsed(true);
        for (String cat : CATEGORIES) {
            final String category = cat;
            requirements.option(Option.<Integer>createBuilder()
                    .name(Text.translatable("equip_leveling.config.base_xp", pretty(cat)))
                    .description(OptionDescription.of(Text.translatable("equip_leveling.config.base_xp.tooltip", pretty(cat))))
                    .binding(defaultBaseXp(cat),
                            () -> EquipLevelingConfig.getBaseXpForCategory(category),
                            v -> EquipLevelingConfig.setBaseXpForCategory(category, v))
                    .controller(opt -> IntegerFieldControllerBuilder.create(opt).range(1, Integer.MAX_VALUE))
                    .available(canEdit)
                    .build());
        }
        builder.group(requirements.build());

        // Advanced XP multipliers (per-source).
        var multipliers = OptionGroup.createBuilder()
                .name(Text.translatable("equip_leveling.config.sub.source_multipliers"))
                .description(OptionDescription.of(Text.translatable("equip_leveling.config.sub.source_multipliers.desc")))
                .collapsed(true);

        for (String key : SOURCE_KEYS) {
            multipliers.option(Option.<Float>createBuilder()
                    .name(Text.translatable("equip_leveling.config.source_mult." + key))
                    .description(OptionDescription.of(Text.translatable("equip_leveling.config.source_mult." + key + ".tooltip")))
                    .binding(1.0f,
                            () -> (float) EquipLevelingConfig.getSourceMultiplier(key),
                            v -> EquipLevelingConfig.setSourceMultiplier(key, v))
                    .controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.0f, 100.0f).step(0.1f))
                    .available(canEdit)
                    .build());
        }
        builder.group(multipliers.build());

        return builder.build();
    }

    // ================================================================== //
    // Tab 3 — Leveling Up                                                 //
    // ================================================================== //

    private static ConfigCategory buildLevelingUp(Screen parent, boolean canEdit) {
        var builder = ConfigCategory.createBuilder()
                .name(Text.translatable("equip_leveling.config.category.enchanting"))
                .tooltip(Text.translatable("equip_leveling.config.category.enchanting.desc"));

        // Offer weights.
        var weights = OptionGroup.createBuilder()
                .name(Text.translatable("equip_leveling.config.sub.weights"))
                .description(OptionDescription.of(Text.translatable("equip_leveling.config.sub.weights.desc")))
                .collapsed(false)
                .option(Option.<Float>createBuilder()
                        .name(Text.translatable("equip_leveling.config.upgrade_weight"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.upgrade_weight.tooltip")))
                        .binding(0.6f,
                                () -> (float) EquipLevelingConfig.getUpgradeWeight(),
                                v -> EquipLevelingConfig.setOfferWeights(v, (float) EquipLevelingConfig.getNewSlotWeight()))
                        .controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.0f, 10.0f).step(0.05f))
                        .available(canEdit)
                        .build())
                .option(Option.<Float>createBuilder()
                        .name(Text.translatable("equip_leveling.config.new_slot_weight"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.new_slot_weight.tooltip")))
                        .binding(0.4f,
                                () -> (float) EquipLevelingConfig.getNewSlotWeight(),
                                v -> EquipLevelingConfig.setOfferWeights((float) EquipLevelingConfig.getUpgradeWeight(), v))
                        .controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.0f, 10.0f).step(0.05f))
                        .available(canEdit)
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.translatable("equip_leveling.config.legendary_chance"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.legendary_chance.tooltip")))
                        .binding(5,
                                () -> (int) Math.round(EquipLevelingConfig.getLegendaryUpgradeProbability() * 100),
                                v -> EquipLevelingConfig.setLegendaryUpgradeProbability(v / 100.0))
                        .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 100).step(1))
                        .available(canEdit)
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
                    .available(canEdit)
                    .build());
        }
        builder.group(reroll.build());

        // Material ladder editor button.
        builder.group(OptionGroup.createBuilder()
                .name(Text.translatable("equip_leveling.config.sub.material_ladder"))
                .option(ButtonOption.createBuilder()
                        .name(Text.translatable("equip_leveling.config.material_ladder"))
                        .text(Text.translatable("equip_leveling.config.material_ladder.button"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.material_ladder.tooltip")))
                        .action((yaclScreen, buttonOption) ->
                                MinecraftClient.getInstance().setScreen(new MaterialLadderScreen(parent)))
                        .available(canEdit)
                        .build())
                .build());

        // Max enchantment slots per category.
        var maxSlots = OptionGroup.createBuilder()
                .name(Text.translatable("equip_leveling.config.sub.max_slots"))
                .collapsed(false);
        for (String cat : CATEGORIES) {
            final String category = cat;
            maxSlots.option(Option.<Integer>createBuilder()
                    .name(Text.translatable("equip_leveling.config.max_slots", pretty(cat)))
                    .description(OptionDescription.of(Text.translatable("equip_leveling.config.max_slots.tooltip", pretty(cat))))
                    .binding(defaultMaxSlots(cat),
                            () -> EquipLevelingConfig.getMaxSlotsForCategory(category),
                            v -> EquipLevelingConfig.setMaxSlotsForCategory(category, v))
                    .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(1, 8).step(1))
                    .available(canEdit)
                    .build());
        }
        builder.group(maxSlots.build());

        return builder.build();
    }

    // ================================================================== //
    // Helpers                                                             //
    // ================================================================== //

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
