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
 *   <li><b>General</b> — level‑requirement growth and advanced mechanics toggles.</li>
 *   <li><b>XP &amp; Leveling</b> — <em>every</em> XP multiplier in one place:
 *       global gain, per‑source multipliers, base‑XP per category, and max enchantment slots.</li>
 *   <li><b>Enchanting Table</b> — offer weights, legendary chance, reroll costs, material ladder.</li>
 * </ul>
 *
 * <p>In multiplayer, non‑OP players see a read‑only view of the server config.
 * OP players (level 2+) can edit and changes are broadcast to all players live.
 */
public final class EquipLevelingConfigScreen {
    private static final String[] CATEGORIES = {
            "sword", "axe", "pickaxe", "shovel", "hoe", "fishing_rod", "bow",
            "helmet", "chestplate", "leggings", "boots", "elytra", "shield"
    };

    private static final String[] SOURCE_KEYS = {"mob", "livestock", "mining", "farming", "wood", "fishing"};

    private EquipLevelingConfigScreen() {}

    public static Screen create(Screen parent) {
        boolean canEdit = canEditConfig();

        return YetAnotherConfigLib.createBuilder()
                .title(Text.translatable("equip_leveling.config.title"))
                .save(() -> {
                    EquipLevelingConfig.save();
                })
                .category(buildGeneral(canEdit))
                .category(buildXpAndLeveling(canEdit))
                .category(buildEnchantingTable(parent, canEdit))
                .build()
                .generateScreen(parent);
    }

    /** Returns true if the player can edit the config (singleplayer only; multiplayer is read‑only). */
    private static boolean canEditConfig() {
        return MinecraftClient.getInstance().isInSingleplayer();
    }

    // ================================================================== //
    // Tab 1 — General                                                     //
    // ================================================================== //

    private static ConfigCategory buildGeneral(boolean canEdit) {
        var builder = ConfigCategory.createBuilder()
                .name(Text.translatable("equip_leveling.config.category.general"))
                .tooltip(Text.translatable("equip_leveling.config.category.general.desc"));

        // Level‑up requirement growth — the most impactful tuning knob, always visible.
        builder.option(Option.<Float>createBuilder()
                .name(Text.translatable("equip_leveling.config.level_req_growth"))
                .description(OptionDescription.of(Text.translatable("equip_leveling.config.level_req_growth.tooltip")))
                .binding(Binding.generic(1.2f,
                        () -> (float) EquipLevelingConfig.getLevelRequirementGrowth(),
                        v -> EquipLevelingConfig.setLevelRequirementGrowth(v)))
                .controller(opt -> FloatSliderControllerBuilder.create(opt).range(1.0f, 10.0f).step(0.1f))
                .available(canEdit)
                .build());

        // Keep on death and broken mechanic — important gameplay toggles, always visible.
        builder.option(Option.<Boolean>createBuilder()
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
                .build());

        // Numeric knobs — collapsed so the tab stays compact.
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
                        .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 100).step(5))
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
    // Tab 2 — XP & Leveling  (ALL multipliers together)                   //
    // ================================================================== //

    private static ConfigCategory buildXpAndLeveling(boolean canEdit) {
        var builder = ConfigCategory.createBuilder()
                .name(Text.translatable("equip_leveling.config.category.xp_leveling"))
                .tooltip(Text.translatable("equip_leveling.config.category.xp_leveling.desc"));

        // Global multiplier — top‑level, always visible.
        builder.option(Option.<Float>createBuilder()
                .name(Text.translatable("equip_leveling.config.global_gain"))
                .description(OptionDescription.of(Text.translatable("equip_leveling.config.global_gain.tooltip")))
                .binding(Binding.generic(1.0f,
                        () -> (float) EquipLevelingConfig.getGlobalXpGainMultiplier(),
                        v -> EquipLevelingConfig.setGlobalXpGainMultiplier(v)))
                .controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.1f, 10.0f).step(0.1f))
                .available(canEdit)
                .build());

        // Per‑source multipliers — uncollapsed so they are immediately visible.
        var sourceMults = OptionGroup.createBuilder()
                .name(Text.translatable("equip_leveling.config.sub.source_multipliers"))
                .description(OptionDescription.of(Text.translatable("equip_leveling.config.sub.source_multipliers.desc")))
                .collapsed(false);
        for (String key : SOURCE_KEYS) {
            sourceMults.option(Option.<Float>createBuilder()
                    .name(Text.translatable("equip_leveling.config.source_mult." + key))
                    .description(OptionDescription.of(Text.translatable("equip_leveling.config.source_mult." + key + ".tooltip")))
                    .binding(1.0f,
                            () -> (float) EquipLevelingConfig.getSourceMultiplier(key),
                            v -> EquipLevelingConfig.setSourceMultiplier(key, v))
                    .controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.0f, 10.0f).step(0.1f))
                    .available(canEdit)
                    .build());
        }
        builder.group(sourceMults.build());

        // Base XP per category — collapsed because it's rarely changed once set.
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

        // Max enchantment slots per category — collapsed.
        var maxSlots = OptionGroup.createBuilder()
                .name(Text.translatable("equip_leveling.config.sub.max_slots"))
                .collapsed(true);
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
    // Tab 3 — Enchanting Table                                            //
    // ================================================================== //

    private static ConfigCategory buildEnchantingTable(Screen parent, boolean canEdit) {
        var builder = ConfigCategory.createBuilder()
                .name(Text.translatable("equip_leveling.config.category.enchanting"))
                .tooltip(Text.translatable("equip_leveling.config.category.enchanting.desc"));

        // Offer weights — uncollapsed, these are the main tuning knobs.
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

        // Reroll prices — collapsed.
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

        // Material ladder editor button — collapsed.
        builder.group(OptionGroup.createBuilder()
                .name(Text.translatable("equip_leveling.config.sub.material_ladder"))
                .option(ButtonOption.createBuilder()
                        .name(Text.translatable("equip_leveling.config.material_ladder"))
                        .text(Text.translatable("equip_leveling.config.material_ladder.button"))
                        .description(OptionDescription.of(Text.translatable("equip_leveling.config.material_ladder.tooltip")))
                        .action((yaclScreen, buttonOption) ->
                                // Defer by one tick to avoid "Can only blur once per frame"
                                // crash from YACL's background blur racing the new screen's blur.
                                MinecraftClient.getInstance().execute(() ->
                                        MinecraftClient.getInstance().setScreen(new MaterialLadderScreen(parent))))
                        .available(canEdit)
                        .build())
                .build());

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
            case "helmet", "boots", "elytra" -> 350;
            case "chestplate", "leggings" -> 400;
            case "shield" -> 300;
            default -> 350;
        };
    }

    private static int defaultMaxSlots(String category) {
        return switch (category) {
            case "sword", "helmet", "chestplate", "leggings", "boots", "elytra" -> 4;
            case "axe", "pickaxe", "fishing_rod", "bow" -> 3;
            case "shovel", "hoe", "shield" -> 2;
            default -> 4;
        };
    }

    private static String pretty(String value) {
        return Arrays.stream(value.split("_"))
                .map(part -> part.isEmpty() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .reduce((a, b) -> a + " " + b).orElse(value);
    }
}
