package com.amorairedraws.equipleveling.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.*;

/**
 * A dedicated screen for editing the material ladder: mining level → materials.
 *
 * <p>Each mining level shows its materials with inline text fields.
 * Users can add/remove materials and add/remove entire levels. Changes
 * are saved on Done.
 */
public final class MaterialLadderScreen extends Screen {
    private final Screen parent;
    private final Map<Integer, List<String>> workingLadder;
    private ButtonWidget doneButton, cancelButton, addLevelButton, autoDetectButton;
    private final List<LevelWidget> levelWidgets = new ArrayList<>();
    private int scrollOffset;

    public MaterialLadderScreen(Screen parent) {
        super(Text.translatable("equip_leveling.config.material_ladder.title"));
        this.parent = parent;
        this.workingLadder = deepCopy(EquipLevelingConfig.getMaterialLadder());
    }

    private static Map<Integer, List<String>> deepCopy(Map<Integer, List<String>> source) {
        Map<Integer, List<String>> copy = new LinkedHashMap<>();
        for (var e : source.entrySet()) {
            copy.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return copy;
    }

    @Override
    protected void init() {
        levelWidgets.clear();
        int y = 35;

        // Build level widgets.
        for (int level : new TreeSet<>(workingLadder.keySet())) {
            List<String> materials = workingLadder.getOrDefault(level, List.of());
            LevelWidget lw = new LevelWidget(level, materials, y);
            levelWidgets.add(lw);
            addDrawableChild(lw.removeBtn);
            for (TextFieldWidget tf : lw.fields) addDrawableChild(tf);
            addDrawableChild(lw.addBtn);
            y += lw.height();
        }

        addLevelButton = ButtonWidget.builder(
                Text.translatable("equip_leveling.config.material_ladder.add_level"),
                btn -> addMiningLevel())
                .dimensions(10, y + 2, 150, 20).build();
        addDrawableChild(addLevelButton);

        autoDetectButton = ButtonWidget.builder(
                Text.literal("Auto-Detect from Registry"),
                btn -> autoDetect())
                .dimensions(170, y + 2, 150, 20).build();
        addDrawableChild(autoDetectButton);

        // Bottom buttons.
        int bottomY = height - 25;
        doneButton = ButtonWidget.builder(Text.translatable("gui.done"), btn -> {
            saveToConfig();
            close();
        }).dimensions(width / 2 - 155, bottomY, 150, 20).build();
        addDrawableChild(doneButton);

        cancelButton = ButtonWidget.builder(Text.translatable("gui.cancel"), btn -> close())
                .dimensions(width / 2 + 5, bottomY, 150, 20).build();
        addDrawableChild(cancelButton);
    }

    private void saveToConfig() {
        Map<Integer, List<String>> cleaned = new LinkedHashMap<>();
        for (LevelWidget lw : levelWidgets) {
            List<String> mats = lw.getMaterials();
            if (!mats.isEmpty()) cleaned.put(lw.level, mats);
        }
        workingLadder.clear();
        workingLadder.putAll(cleaned);
        if (workingLadder.isEmpty()) {
            workingLadder.put(0, new ArrayList<>(List.of("wood", "gold")));
            workingLadder.put(1, new ArrayList<>(List.of("stone")));
            workingLadder.put(2, new ArrayList<>(List.of("iron")));
            workingLadder.put(3, new ArrayList<>(List.of("diamond")));
            workingLadder.put(4, new ArrayList<>(List.of("netherite")));
        }
        EquipLevelingConfig.setMaterialLadder(workingLadder);
    }

    private void addMiningLevel() {
        int max = workingLadder.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        int newLevel = max + 1;
        workingLadder.put(newLevel, new ArrayList<>());
        rebuild();
    }

    private void autoDetect() {
        var detected = com.amorairedraws.equipleveling.util.MaterialHelper.detectMaterialLadder();
        workingLadder.clear();
        workingLadder.putAll(detected);
        rebuild();
    }

    private void rebuild() {
        clearChildren();
        init();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Draw a plain translucent dim instead of renderBackground(): vanilla's
        // renderBackground() applies the "menu blur" effect, which throws
        // "Can only blur once per frame" when YACL's config screen has already
        // blurred the background this frame (Sodium/Iris + vanilla accessibility
        // blur). A flat fill gives the same dimming with no blur at all.
        ctx.fill(0, 0, width, height, 0xC0101010);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        super.render(ctx, mouseX, mouseY, delta);

        // Render level labels (not widgets, just text).
        int y = 35;
        for (LevelWidget lw : levelWidgets) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.translatable("equip_leveling.config.material_ladder.level", lw.level),
                    10, y + 5, 0xFFAAAAAA);
            y += lw.height();
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    // ---- Level widget (logical group, not a real widget) ----

    private class LevelWidget {
        final int level;
        final List<TextFieldWidget> fields = new ArrayList<>();
        final ButtonWidget removeBtn, addBtn;
        final int baseY;

        LevelWidget(int level, List<String> materials, int y) {
            this.level = level;
            this.baseY = y;

            // Remove level button (x at right).
            removeBtn = ButtonWidget.builder(Text.literal("✕"), btn -> {
                workingLadder.remove(this.level);
                if (workingLadder.isEmpty()) {
                    workingLadder.put(0, new ArrayList<>(List.of("wood", "gold")));
                    workingLadder.put(1, new ArrayList<>(List.of("stone")));
                    workingLadder.put(2, new ArrayList<>(List.of("iron")));
                    workingLadder.put(3, new ArrayList<>(List.of("diamond")));
                    workingLadder.put(4, new ArrayList<>(List.of("netherite")));
                }
                rebuild();
            }).dimensions(width - 85, y + 2, 60, 16).build();

            // Material text fields.
            int fx = 160;
            int fy = y + 3;
            for (int i = 0; i < materials.size(); i++) {
                final int idx = i;
                String mat = materials.get(i);
                TextFieldWidget tf = new TextFieldWidget(textRenderer, fx, fy, 130, 16, Text.empty());
                tf.setText(mat);
                tf.setChangedListener(newVal -> {
                    List<String> mats = workingLadder.get(this.level);
                    if (mats != null && idx < mats.size()) {
                        mats.set(idx, newVal.trim().toLowerCase());
                    }
                });
                fields.add(tf);

                // Delete button per material.
                ButtonWidget delBtn = ButtonWidget.builder(Text.literal("✕"), btn -> {
                    List<String> mats = workingLadder.get(this.level);
                    if (mats != null && idx < mats.size()) {
                        mats.remove(idx);
                    }
                    rebuild();
                }).dimensions(fx + 134, fy, 16, 16).build();
                addDrawableChild(delBtn);

                fy += 20;
            }

            // Add material button.
            addBtn = ButtonWidget.builder(Text.literal("+ Material"), btn -> {
                workingLadder.computeIfAbsent(this.level, k -> new ArrayList<>()).add("");
                rebuild();
            }).dimensions(fx, fy, 130, 16).build();
        }

        List<String> getMaterials() {
            List<String> mats = new ArrayList<>();
            for (TextFieldWidget tf : fields) {
                String s = tf.getText().trim().toLowerCase();
                if (!s.isBlank()) mats.add(s);
            }
            return mats;
        }

        int height() {
            return Math.max(26, 6 + (fields.size() + 1) * 20);
        }
    }
}
