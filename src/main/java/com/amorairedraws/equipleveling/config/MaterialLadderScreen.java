package com.amorairedraws.equipleveling.config;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.*;

/**
 * A dedicated screen for editing the material ladder: mining level \u2192 materials.
 *
 * <p>Each mining level is shown as a distinct group with its own material rows.
 * Materials can be moved up or down between adjacent levels with the \u25B2/\u25BC
 * buttons, edited inline, removed with \u2715, or added with "+ Add Material".
 * Entire levels can be added or removed. Changes are saved on Done.
 */
public final class MaterialLadderScreen extends Screen {
    private final Screen parent;
    private final Map<Integer, List<String>> workingLadder;
    private ButtonWidget doneButton, cancelButton;
    private final List<LevelWidget> levelWidgets = new ArrayList<>();
    private int scrollOffset;
    private int contentBottomY;

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
        clearChildren();
        levelWidgets.clear();

        int y = 40 - scrollOffset;

        for (int level : new TreeSet<>(workingLadder.keySet())) {
            List<String> materials = workingLadder.getOrDefault(level, List.of());
            LevelWidget lw = new LevelWidget(level, materials, y);
            levelWidgets.add(lw);
            y += lw.height();
        }

        contentBottomY = y + scrollOffset + 6;

        // Footer controls (scrolled with the content).
        int footerY = y;
        ButtonWidget addLevel = ButtonWidget.builder(
                Text.translatable("equip_leveling.config.material_ladder.add_level"),
                btn -> addMiningLevel())
                .dimensions(14, footerY, 140, 20).build();
        addDrawableChild(addLevel);

        ButtonWidget autoDetect = ButtonWidget.builder(
                Text.translatable("equip_leveling.config.material_ladder.auto_detect"),
                btn -> autoDetect())
                .dimensions(164, footerY, 150, 20).build();
        addDrawableChild(autoDetect);

        contentBottomY += 26;

        // Fixed bottom buttons.
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

    private int maxScroll() {
        return Math.max(0, contentBottomY - (height - 55));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = maxScroll();
        if (max <= 0) return true;
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) (verticalAmount * 24), max));
        rebuild();
        return true;
    }

    private void saveToConfig() {
        // Flush text fields into the working ladder before saving.
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

    private void moveMaterial(int level, int idx, int delta) {
        List<String> mats = workingLadder.get(level);
        if (mats == null || idx < 0 || idx >= mats.size()) return;
        String mat = mats.remove(idx);
        int target = level + delta;
        workingLadder.computeIfAbsent(target, k -> new ArrayList<>()).add(mat);
        rebuild();
    }

    private void removeMaterial(int level, int idx) {
        List<String> mats = workingLadder.get(level);
        if (mats != null && idx >= 0 && idx < mats.size()) {
            mats.remove(idx);
        }
        rebuild();
    }

    private void removeLevel(int level) {
        workingLadder.remove(level);
        if (workingLadder.isEmpty()) {
            workingLadder.put(0, new ArrayList<>(List.of("wood", "gold")));
            workingLadder.put(1, new ArrayList<>(List.of("stone")));
            workingLadder.put(2, new ArrayList<>(List.of("iron")));
            workingLadder.put(3, new ArrayList<>(List.of("diamond")));
            workingLadder.put(4, new ArrayList<>(List.of("netherite")));
        }
        rebuild();
    }

    private void rebuild() {
        init();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // A flat translucent dim avoids the "Can only blur once per frame" crash
        // that vanilla renderBackground() triggers under YACL/Sodium/Iris.
        ctx.fill(0, 0, width, height, 0xC0101010);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        super.render(ctx, mouseX, mouseY, delta);

        // Render level headers (plain text, not widgets) and group backgrounds.
        for (LevelWidget lw : levelWidgets) {
            int y = lw.baseY;
            ctx.fill(14, y, width - 14, y + lw.height() - 4, 0x22FFFFFF);
            ctx.drawTextWithShadow(textRenderer,
                    Text.translatable("equip_leveling.config.material_ladder.level", lw.level),
                    18, y + 5, 0xFFE0E0E0);
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
        final int baseY;

        LevelWidget(int level, List<String> materials, int y) {
            this.level = level;
            this.baseY = y;

            // Remove-level button in the header row.
            ButtonWidget removeBtn = ButtonWidget.builder(Text.literal("\u2715"), btn -> removeLevel(this.level))
                    .dimensions(width - 70, y + 2, 56, 16).build();
            addDrawableChild(removeBtn);

            // Material rows.
            int rowY = y + 22;
            for (int i = 0; i < materials.size(); i++) {
                final int idx = i;
                String mat = materials.get(i);

                TextFieldWidget tf = new TextFieldWidget(textRenderer, 70, rowY, 140, 16, Text.empty());
                tf.setText(mat);
                tf.setChangedListener(newVal -> {
                    List<String> mats = workingLadder.get(this.level);
                    if (mats != null && idx < mats.size()) {
                        mats.set(idx, newVal.trim().toLowerCase());
                    }
                });
                fields.add(tf);
                addDrawableChild(tf);

                ButtonWidget up = ButtonWidget.builder(Text.literal("\u25B2"), btn -> moveMaterial(this.level, idx, -1))
                        .dimensions(214, rowY, 18, 16).build();
                addDrawableChild(up);

                ButtonWidget down = ButtonWidget.builder(Text.literal("\u25BC"), btn -> moveMaterial(this.level, idx, +1))
                        .dimensions(234, rowY, 18, 16).build();
                addDrawableChild(down);

                ButtonWidget del = ButtonWidget.builder(Text.literal("\u2715"), btn -> removeMaterial(this.level, idx))
                        .dimensions(254, rowY, 18, 16).build();
                addDrawableChild(del);

                rowY += 20;
            }

            // Add-material button.
            ButtonWidget addBtn = ButtonWidget.builder(
                    Text.translatable("equip_leveling.config.material_ladder.add_material"),
                    btn -> {
                        workingLadder.computeIfAbsent(this.level, k -> new ArrayList<>()).add("");
                        rebuild();
                    })
                    .dimensions(70, rowY, 140, 16).build();
            addDrawableChild(addBtn);
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
            return Math.max(48, 26 + (fields.size() + 1) * 20);
        }
    }
}
