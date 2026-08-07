package com.amorairedraws.equipleveling.config;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Small hand-rolled Mod Menu screen.  It intentionally edits the same JSON
 * backed config as the server, without introducing Cloth Config as a dependency.
 */
public final class EquipLevelingConfigScreen extends Screen {
    private static final String[] CATEGORIES = {"sword", "axe", "pickaxe", "shovel", "hoe",
            "helmet", "chestplate", "leggings", "boots", "fishing_rod", "default"};
    private final Screen parent;
    private final List<TextFieldWidget> fields = new ArrayList<>();
    private int page;

    public EquipLevelingConfigScreen(Screen parent) {
        super(Text.translatable("equip_leveling.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        buildPage();
    }

    private void buildPage() {
        clearChildren();
        fields.clear();
        if (page == 0) buildGeneralPage();
        else buildBaseXpPage();
    }

    private TextFieldWidget field(String label, String value, int x, int y) {
        ButtonWidget labelWidget = addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {})
                .dimensions(x, y, 120, 20).build());
        labelWidget.active = false;
        TextFieldWidget field = new TextFieldWidget(textRenderer, x + 124, y, 100, 20, Text.literal(label));
        field.setText(value);
        fields.add(field);
        addDrawableChild(field);
        return field;
    }

    private void buildGeneralPage() {
        field("XP multiplier", Double.toString(EquipLevelingConfig.getXpMultiplier()), 20, 42);
        field("Display threshold", Integer.toString(EquipLevelingConfig.getXpDisplayThreshold()), 250, 42);
        field("Restore percent", Integer.toString(EquipLevelingConfig.getDurabilityRestorePercent()), 20, 72);
        field("Legendary chance", Double.toString(EquipLevelingConfig.getLegendaryUpgradeProbability()), 250, 72);
        field("Upgrade weight", Double.toString(EquipLevelingConfig.getUpgradeWeight()), 20, 102);
        field("New-slot weight", Double.toString(EquipLevelingConfig.getNewSlotWeight()), 250, 102);
        field("Anvil base cost", Integer.toString(EquipLevelingConfig.getAnvilBaseCost()), 20, 132);
        field("Anvil per level", Integer.toString(EquipLevelingConfig.getAnvilPerLevelCost()), 250, 132);
        field("Reroll costs", join(EquipLevelingConfig.getRerollCosts()), 20, 162);
        field("Material ladder", String.join(",", EquipLevelingConfig.getMaterialTiers()), 250, 162);

        addDrawableChild(ButtonWidget.builder(Text.literal(EquipLevelingConfig.isKeepEquipOnDeath()
                ? "Keep equipment: ON" : "Keep equipment: OFF"), b -> {
            EquipLevelingConfig.setKeepEquipOnDeath(!EquipLevelingConfig.isKeepEquipOnDeath());
            b.setMessage(Text.literal(EquipLevelingConfig.isKeepEquipOnDeath()
                    ? "Keep equipment: ON" : "Keep equipment: OFF"));
        }).dimensions(20, 202, 224, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(EquipLevelingConfig.isBrokenMechanicEnabled()
                ? "Broken mechanic: ON" : "Broken mechanic: OFF"), b -> {
            EquipLevelingConfig.setBrokenMechanicEnabled(!EquipLevelingConfig.isBrokenMechanicEnabled());
            b.setMessage(Text.literal(EquipLevelingConfig.isBrokenMechanicEnabled()
                    ? "Broken mechanic: ON" : "Broken mechanic: OFF"));
        }).dimensions(250, 202, 224, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Edit category base XP"), b -> {
            page = 1;
            clearAndInit();
        }).dimensions(20, 240, 224, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Save & Exit"), b -> {
            applyGeneral();
            client.setScreen(parent);
        }).dimensions(250, 240, 224, 20).build());
    }

    private void buildBaseXpPage() {
        for (int i = 0; i < CATEGORIES.length; i++) {
            int x = i < 6 ? 20 : 250;
            int y = 42 + (i % 6) * 28;
            field(CATEGORIES[i] + " base XP", Integer.toString(EquipLevelingConfig.getBaseXpForCategory(CATEGORIES[i])), x, y);
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> {
            applyBaseXp();
            page = 0;
            clearAndInit();
        }).dimensions(20, 235, 224, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Save & Exit"), b -> {
            applyBaseXp();
            client.setScreen(parent);
        }).dimensions(250, 235, 224, 20).build());
    }

    private void applyGeneral() {
        try {
            EquipLevelingConfig.setXpMultiplier(Double.parseDouble(fields.get(0).getText()));
            EquipLevelingConfig.setXpDisplayThreshold(Integer.parseInt(fields.get(1).getText()));
            EquipLevelingConfig.setDurabilityRestorePercent(Integer.parseInt(fields.get(2).getText()));
            EquipLevelingConfig.setLegendaryUpgradeProbability(Double.parseDouble(fields.get(3).getText()));
            EquipLevelingConfig.setOfferWeights(Double.parseDouble(fields.get(4).getText()), Double.parseDouble(fields.get(5).getText()));
            EquipLevelingConfig.setAnvilCosts(Integer.parseInt(fields.get(6).getText()), Integer.parseInt(fields.get(7).getText()));
            EquipLevelingConfig.setRerollCosts(parseInts(fields.get(8).getText(), 5));
            EquipLevelingConfig.setMaterialTiers(fields.get(9).getText().split(","));
        } catch (RuntimeException ignored) {
            // Individual setters validate values; a malformed field leaves its
            // previous setting intact rather than breaking the config screen.
        }
    }

    private void applyBaseXp() {
        for (int i = 0; i < CATEGORIES.length && i < fields.size(); i++) {
            try {
                EquipLevelingConfig.setBaseXpForCategory(CATEGORIES[i], Integer.parseInt(fields.get(i).getText()));
            } catch (NumberFormatException ignored) { }
        }
    }

    private static int[] parseInts(String text, int count) {
        String[] values = text.split(",");
        if (values.length != count) throw new IllegalArgumentException("expected " + count + " values");
        int[] result = new int[count];
        for (int i = 0; i < count; i++) result[i] = Integer.parseInt(values[i].trim());
        return result;
    }

    private static String join(int[] values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) result.append(',');
            result.append(values[i]);
        }
        return result.toString();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 15, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(page == 0 ? "General settings" : "Base XP by category"), width / 2, 28, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
