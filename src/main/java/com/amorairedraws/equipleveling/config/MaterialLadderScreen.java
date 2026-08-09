package com.amorairedraws.equipleveling.config;

import com.amorairedraws.equipleveling.util.MaterialLadderDetector;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * A small editor for the material upgrade ladder.  Each row shows one material
 * with buttons to move it up, move it down, or delete it, plus a text field to
 * rename it.  A "Detect from game" button rebuilds the list from the registry.
 */
public final class MaterialLadderScreen extends Screen {
    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private int scroll;
    private ButtonWidget doneButton;
    private ButtonWidget detectButton;

    private static final int ROW_HEIGHT = 22;
    private static final int LIST_TOP = 40;

    private static final class Row {
        final TextFieldWidget nameField;
        Row(TextFieldWidget nameField) { this.nameField = nameField; }
    }

    public MaterialLadderScreen(Screen parent) {
        super(Text.literal("Material Upgrade Order"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = this.width;
        int h = this.height;

        this.detectButton = ButtonWidget.builder(
                Text.literal("Detect from game"),
                b -> { loadFromDetector(); })
                .dimensions(w / 2 - 110, 10, 100, 20).build();
        this.addDrawableChild(this.detectButton);

        this.doneButton = ButtonWidget.builder(Text.literal("Save and go back"), b -> saveAndClose())
                .dimensions(w / 2 - 70, h - 30, 140, 20).build();
        this.addDrawableChild(this.doneButton);

        // Load the current configured ladder.
        String[] current = EquipLevelingConfig.getMaterialTiers();
        rows.clear();
        for (String tier : current) {
            if (tier != null && !tier.isBlank()) rows.add(makeRow(tier));
        }
    }

    private void loadFromDetector() {
        rows.clear();
        for (String tier : MaterialLadderDetector.detectLadder()) {
            rows.add(makeRow(tier));
        }
    }

    private Row makeRow(String name) {
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, 0, 0, 120, 16, Text.literal("Material"));
        field.setMaxLength(40);
        field.setText(name);
        return new Row(field);
    }

    private void saveAndClose() {
        List<String> ladder = new ArrayList<>();
        for (Row row : rows) {
            String name = row.nameField.getText() == null ? "" : row.nameField.getText().trim().toLowerCase();
            if (!name.isEmpty()) ladder.add(name);
        }
        if (ladder.isEmpty()) {
            ladder.addAll(MaterialLadderDetector.vanillaLadder());
        }
        EquipLevelingConfig.setMaterialTiers(ladder.toArray(new String[0]));
        this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 2, 0xFFFFFF);

        if (this.detectButton != null) this.detectButton.render(context, mouseX, mouseY, delta);
        if (this.doneButton != null) this.doneButton.render(context, mouseX, mouseY, delta);

        int top = LIST_TOP;
        int bottom = this.height - 60;
        int right = this.width - 20;
        context.enableScissor(20, top - 4, right, bottom + 4);

        int y = top - this.scroll;
        for (int i = 0; i < rows.size(); i++) {
            if (y + ROW_HEIGHT < top) { y += ROW_HEIGHT; continue; }
            if (y > bottom) break;
            Row row = rows.get(i);
            boolean hovered = mouseX >= 20 && mouseX <= right && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) context.fill(20, y, right, y + ROW_HEIGHT, 0x33FFFFFF);

            // Name field
            row.nameField.setX(24);
            row.nameField.setY(y + 2);
            row.nameField.setWidth(120);
            row.nameField.render(context, mouseX, mouseY, delta);

            // Move up / down / delete buttons
            int bx = right - 150;
            drawMiniButton(context, bx, y, "Up", i > 0, mouseX, mouseY, i, 0);
            drawMiniButton(context, bx + 40, y, "Dn", i < rows.size() - 1, mouseX, mouseY, i, 1);
            drawMiniButton(context, bx + 80, y, "Del", true, mouseX, mouseY, i, 2);

            y += ROW_HEIGHT;
        }
        context.disableScissor();
    }

    private void drawMiniButton(DrawContext context, int x, int y, String label, boolean enabled,
            int mouseX, int mouseY, int rowIndex, int action) {
        int color = enabled ? (isHovered(x, y, 34, 18, mouseX, mouseY) ? 0xFF4A6A8A : 0xFF3A4A5A) : 0xFF222222;
        context.fill(x, y, x + 34, y + 18, color);
        context.drawCenteredTextWithShadow(this.textRenderer, label, x + 17, y + 5, enabled ? 0xFFFFFF : 0x666666);
    }

    private boolean isHovered(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean onlyIncludeWithin) {
        if (click.button() != 0) return false;
        int mx = (int) click.x();
        int my = (int) click.y();
        int top = LIST_TOP;
        int y = top - this.scroll;
        int right = this.width - 20;
        for (int i = 0; i < rows.size(); i++) {
            if (y + ROW_HEIGHT < top) { y += ROW_HEIGHT; continue; }
            if (y > this.height - 60) break;
            int bx = right - 150;
            if (isHovered(bx, y, 34, 18, mx, my) && i > 0) { move(i, -1); return true; }
            if (isHovered(bx + 40, y, 34, 18, mx, my) && i < rows.size() - 1) { move(i, 1); return true; }
            if (isHovered(bx + 80, y, 34, 18, mx, my)) { rows.remove(i); return true; }
            y += ROW_HEIGHT;
        }
        return super.mouseClicked(click, onlyIncludeWithin);
    }

    private void move(int index, int delta) {
        int target = index + delta;
        if (target < 0 || target >= rows.size()) return;
        Row tmp = rows.get(index);
        rows.set(index, rows.get(target));
        rows.set(target, tmp);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int listHeight = this.height - 60 - LIST_TOP;
        int maxScroll = Math.max(0, rows.size() * ROW_HEIGHT - listHeight);
        this.scroll = (int) Math.max(0, Math.min(maxScroll, this.scroll - verticalAmount * ROW_HEIGHT * 2));
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput keyInput) {
        if (keyInput.key() == 256) { // ESC
            this.client.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
