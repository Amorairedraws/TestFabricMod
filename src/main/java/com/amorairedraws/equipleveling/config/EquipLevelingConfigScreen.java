package com.amorairedraws.equipleveling.config;

import com.amorairedraws.equipleveling.util.MaterialLadderDetector;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mod Menu config screen, structured so a third grader could navigate it:
 *
 * <ul>
 *   <li><b>Start Here</b> - one big slider (global XP speed) plus the few
 *       other on/off switches most people will care about.  The fine-tuning
 *       is tucked inside collapsed "Advanced ..." groups.</li>
 *   <li><b>Earning XP</b> - how much XP each item needs to level up, and how
 *       much every action/mining reward gives.  The full block browser is one
 *       click away.</li>
 *   <li><b>Leveling Up</b> - what the enchanting table offers (chances,
 *       reroll cost, material ladder, enchantment slots).</li>
 * </ul>
 *
 * Every sub-category is collapsible, so the screen reads as a short list of
 * plain-language switches unless you deliberately open an "Advanced" group.
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

        buildStartHere(builder, entries);
        buildEarningXp(builder, entries);
        buildLevelingUp(builder, entries);

        return builder.build();
    }

    // ======================================================================
    // Tab 1 - Start Here
    // ======================================================================
    private static void buildStartHere(ConfigBuilder builder, me.shedaniel.clothconfig2.api.ConfigEntryBuilder entries) {
        var tab = builder.getOrCreateCategory(Text.translatable("equip_leveling.config.category.general"));
        tab.addEntry(entries.startTextDescription(
                Text.translatable("equip_leveling.config.category.general.desc")).build());

        // The one setting most people change: global XP speed.
        tab.addEntry(entries.startDoubleField(
                Text.translatable("equip_leveling.config.xp_multiplier"), EquipLevelingConfig.getXpMultiplier())
                .setDefaultValue(1.2).setMin(0.1).setMax(10.0)
                .setTooltip(Text.translatable("equip_leveling.config.xp_multiplier.tooltip"))
                .setSaveConsumer(EquipLevelingConfig::setXpMultiplier).build());

        var advanced = entries.startSubCategory(Text.translatable("equip_leveling.config.sub.general_advanced"));

        advanced.add(entries.startIntSlider(
                Text.translatable("equip_leveling.config.xp_threshold"), EquipLevelingConfig.getXpDisplayThreshold(), 0, 100)
                .setDefaultValue(10)
                .setTooltip(Text.translatable("equip_leveling.config.xp_threshold.tooltip"))
                .setSaveConsumer(EquipLevelingConfig::setXpDisplayThreshold).build());

        advanced.add(entries.startIntSlider(
                Text.translatable("equip_leveling.config.durability_restore"), EquipLevelingConfig.getDurabilityRestorePercent(), 0, 100)
                .setDefaultValue(25)
                .setTooltip(Text.translatable("equip_leveling.config.durability_restore.tooltip"))
                .setSaveConsumer(EquipLevelingConfig::setDurabilityRestorePercent).build());

        advanced.add(entries.startBooleanToggle(
                Text.translatable("equip_leveling.config.keep_on_death"), EquipLevelingConfig.isKeepEquipOnDeath())
                .setDefaultValue(false)
                .setTooltip(Text.translatable("equip_leveling.config.keep_on_death.tooltip"))
                .setSaveConsumer(EquipLevelingConfig::setKeepEquipOnDeath).build());

        advanced.add(entries.startBooleanToggle(
                Text.translatable("equip_leveling.config.broken_mechanic"), EquipLevelingConfig.isBrokenMechanicEnabled())
                .setDefaultValue(true)
                .setTooltip(Text.translatable("equip_leveling.config.broken_mechanic.tooltip"))
                .setSaveConsumer(EquipLevelingConfig::setBrokenMechanicEnabled).build());

        advanced.add(entries.startIntField(
                Text.translatable("equip_leveling.config.anvil_base"), EquipLevelingConfig.getAnvilBaseCost())
                .setDefaultValue(1).setMin(0)
                .setTooltip(Text.translatable("equip_leveling.config.anvil_base.tooltip"))
                .setSaveConsumer(value -> EquipLevelingConfig.setAnvilCosts(value, EquipLevelingConfig.getAnvilPerLevelCost())).build());

        advanced.add(entries.startIntField(
                Text.translatable("equip_leveling.config.anvil_per_level"), EquipLevelingConfig.getAnvilPerLevelCost())
                .setDefaultValue(1).setMin(0)
                .setTooltip(Text.translatable("equip_leveling.config.anvil_per_level.tooltip"))
                .setSaveConsumer(value -> EquipLevelingConfig.setAnvilCosts(EquipLevelingConfig.getAnvilBaseCost(), value)).build());

        tab.addEntry(advanced.setExpanded(false).build());
    }

    // ======================================================================
    // Tab 2 - Earning XP
    // ======================================================================
    private static void buildEarningXp(ConfigBuilder builder, me.shedaniel.clothconfig2.api.ConfigEntryBuilder entries) {
        var tab = builder.getOrCreateCategory(Text.translatable("equip_leveling.config.category.xp_rewards"));
        tab.addEntry(entries.startTextDescription(
                Text.translatable("equip_leveling.config.category.xp_rewards.desc")).build());

        // How much XP each item needs to level up.
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
        tab.addEntry(requirements.setExpanded(false).build());

        // XP for doing things.
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
        tab.addEntry(actions.setExpanded(false).build());

        // XP for mining ores.
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
        tab.addEntry(ore.setExpanded(false).build());

        // Extra blocks that give XP - with the full block browser one click away.
        var custom = entries.startSubCategory(Text.translatable("equip_leveling.config.sub.custom"));
        custom.add(entries.startTextDescription(
                Text.translatable("equip_leveling.config.sub.custom.desc")).build());
        custom.add(new OpenScreenEntry(Text.translatable("equip_leveling.config.open_block_list"),
                BlockXpScreen::new));
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
        tab.addEntry(custom.setExpanded(true).build());
    }

    // ======================================================================
    // Tab 3 - Leveling Up
    // ======================================================================
    private static void buildLevelingUp(ConfigBuilder builder, me.shedaniel.clothconfig2.api.ConfigEntryBuilder entries) {
        var tab = builder.getOrCreateCategory(Text.translatable("equip_leveling.config.category.enchanting"));
        tab.addEntry(entries.startTextDescription(
                Text.translatable("equip_leveling.config.category.enchanting.desc")).build());

        // How often each reward appears.
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
        tab.addEntry(weights.setExpanded(true).build());

        // Reroll prices.
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
        tab.addEntry(reroll.setExpanded(false).build());

        // Material upgrade order - auto-detected from the registry, editable.
        var tiers = entries.startSubCategory(Text.translatable("equip_leveling.config.sub.tiers"));
        tiers.add(entries.startTextDescription(
                Text.translatable("equip_leveling.config.sub.tiers.desc")).build());
        tiers.add(new OpenScreenEntry(Text.translatable("equip_leveling.config.open_ladder_editor"),
                MaterialLadderScreen::new));
        List<String> detected = MaterialLadderDetector.detectLadder();
        // Start from the configured ladder (if the player customized it), otherwise
        // the auto-detected one.
        List<String> currentTiers = new ArrayList<>(Arrays.asList(EquipLevelingConfig.getMaterialTiers()));
        tiers.add(entries.startStrList(
                Text.translatable("equip_leveling.config.material_tiers"), currentTiers)
                .setDefaultValue(detected)
                .setExpanded(true)
                .setAddButtonTooltip(Text.translatable("equip_leveling.config.material_tiers.add"))
                .setRemoveButtonTooltip(Text.translatable("equip_leveling.config.material_tiers.remove"))
                .setTooltip(Text.translatable("equip_leveling.config.material_tiers.tooltip"))
                .setSaveConsumer(list -> EquipLevelingConfig.setMaterialTiers(
                        list == null ? new String[0] : list.toArray(new String[0]))).build());
        tab.addEntry(tiers.setExpanded(true).build());

        // Enchantment slots per item.
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
        tab.addEntry(maxSlots.setExpanded(true).build());
    }

    /**
     * A single clickable row that opens a sub-screen (block XP browser, material
     * ladder editor, ...).  Rendered as a button-like bar so it stands out from
     * the surrounding text rows.
     */
    private static final class OpenScreenEntry extends AbstractConfigListEntry<String> {
        private final java.util.function.Function<Screen, Screen> factory;

        private OpenScreenEntry(Text label, java.util.function.Function<Screen, Screen> factory) {
            super(label, false);
            this.factory = factory;
        }

        @Override public String getValue() { return ""; }
        public void setValue(String value) { }
        @Override public java.util.Optional<String> getDefaultValue() { return java.util.Optional.empty(); }
        @Override public boolean isRequiresRestart() { return false; }
        @Override public void setRequiresRestart(boolean requiresRestart) { }
        @Override public int getItemHeight() { return 22; }

        @Override
        public void render(DrawContext matrices, int index, int y, int x, int entryWidth,
                int entryHeight, int mouseX, int mouseY, boolean isHovered, float delta) {
            int left = x + 2;
            int right = x + entryWidth - 2;
            matrices.fill(left, y + 1, right, y + getItemHeight() - 1, isHovered ? 0xFF3A5A7A : 0xFF2A3A4A);
            // Draw a fake button border
            matrices.fill(left, y + 1, right, y + 2, 0xFF6A9AC0);
            matrices.fill(left, y + getItemHeight() - 2, right, y + getItemHeight() - 1, 0xFF101018);
            matrices.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                    this.getDisplayedFieldName(), left + (right - left) / 2, y + 6, 0xFFFFFF);
        }

        public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean onlyIncludeWithin) {
            if (click.button() == 0) {
                Screen current = MinecraftClient.getInstance().currentScreen;
                MinecraftClient.getInstance().setScreen(factory.apply(current));
                return true;
            }
            return false;
        }

        @Override
        public java.util.List<? extends net.minecraft.client.gui.Selectable> narratables() {
            return java.util.List.of();
        }

        @Override
        public java.util.List<? extends net.minecraft.client.gui.Element> children() {
            return java.util.List.of();
        }
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