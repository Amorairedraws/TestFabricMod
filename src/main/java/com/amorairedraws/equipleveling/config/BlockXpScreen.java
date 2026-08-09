package com.amorairedraws.equipleveling.config;

import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A dedicated list of every diggable block in the game.  Each row shows the
 * block, which tool is needed to mine it, and an XP value the player can set.
 * XP 0 (blank) = this mod gives no bonus for the block; any positive number
 * adds a custom reward when that block is broken with the matching tool.
 * The list is saved back into EquipLevelingConfig.customBlockXp.
 */
public final class BlockXpScreen extends Screen {
    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Integer> current = new LinkedHashMap<>();
    private int scroll;
    private ButtonWidget doneButton;
    private TextFieldWidget searchField;

    private static final int ROW_HEIGHT = 18;
    private static final int LIST_TOP = 40;

    public BlockXpScreen(Screen parent) {
        super(Text.literal("Block XP"));
        this.parent = parent;
        current.putAll(EquipLevelingConfig.getCustomBlockXp());
    }

    private static final class Row {
        final String id;
        final String tool;
        final TextFieldWidget xpField;
        Row(String id, String tool, TextFieldWidget xpField) { this.id = id; this.tool = tool; this.xpField = xpField; }
    }

    @Override
    protected void init() {
        int w = this.width;
        int h = this.height;
        this.searchField = new TextFieldWidget(this.textRenderer, w / 2 - 110, 10, 220, 18, Text.literal("Search"));
        this.searchField.setPlaceholder(Text.literal("Search blocks..."));
        this.searchField.setChangedListener(s -> rebuildRows());
        this.addDrawableChild(this.searchField);

        this.doneButton = ButtonWidget.builder(Text.literal("Save and go back"), b -> saveAndClose())
                .dimensions(w / 2 - 70, h - 30, 140, 20).build();
        this.addDrawableChild(this.doneButton);

        rebuildRows();
    }

    private void rebuildRows() {
        this.rows.clear();
        this.scroll = 0;
        String query = this.searchField == null || this.searchField.getText() == null
                ? "" : this.searchField.getText().toLowerCase().trim();

        // First: blocks that already have a custom XP value (so they are never
        // hidden by the search).
        for (Map.Entry<String, Integer> e : current.entrySet()) {
            Block block = Registries.BLOCK.get(net.minecraft.util.Identifier.tryParse(e.getKey()));
            if (!query.isEmpty() && !e.getKey().contains(query)) continue;
            rows.add(makeRow(e.getKey(), block, e.getValue()));
        }
        // Then: every diggable block in the registry.
        for (Block block : Registries.BLOCK) {
            String id = Registries.BLOCK.getId(block).toString();
            if (current.containsKey(id)) continue;
            String tool = toolFor(block);
            if (tool == null) continue; // not diggable with a tool
            if (!query.isEmpty() && !id.contains(query)) continue;
            rows.add(makeRow(id, block, 0));
        }
    }

    private Row makeRow(String id, Block block, int xp) {
        String tool = block == null ? "any" : toolFor(block);
        if (tool == null) tool = "any";
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, 0, 0, 48, 14, Text.literal("XP"));
        field.setMaxLength(6);
        field.setTextPredicate(s -> s.matches("\\d*"));
        field.setText(xp > 0 ? Integer.toString(xp) : "");
        return new Row(id, tool, field);
    }

    /** The tool needed to dig this block, or null if no tool is required. */
    private static String toolFor(Block block) {
        if (block == null) return null;
        var state = block.getDefaultState();
        if (state.isIn(BlockTags.PICKAXE_MINEABLE)) return "pickaxe";
        if (state.isIn(BlockTags.AXE_MINEABLE)) return "axe";
        if (state.isIn(BlockTags.SHOVEL_MINEABLE)) return "shovel";
        if (state.isIn(BlockTags.HOE_MINEABLE)) return "hoe";
        return null;
    }

    private void saveAndClose() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (Row row : rows) {
            String text = row.xpField.getText() == null ? "" : row.xpField.getText().trim();
            if (text.isEmpty()) continue;
            try {
                int xp = Integer.parseInt(text);
                if (xp > 0) map.put(row.id, xp);
            } catch (NumberFormatException ignored) { }
        }
        EquipLevelingConfig.setCustomBlockXp(map);
        this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 2, 0xFFFFFF);
        if (this.searchField != null) this.searchField.render(context, mouseX, mouseY, delta);

        int top = LIST_TOP;
        int bottom = this.height - 60;
        int right = this.width - 20;
        context.enableScissor(20, top - 4, right, bottom + 4);

        int y = top - this.scroll;
        for (Row row : rows) {
            if (y + ROW_HEIGHT < top) { y += ROW_HEIGHT; continue; }
            if (y > bottom) break;
            boolean hovered = mouseX >= 20 && mouseX <= right && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (hovered) context.fill(20, y, right, y + ROW_HEIGHT, 0x33FFFFFF);
            context.drawText(this.textRenderer, Text.literal(row.id), 24, y + 3, 0xE0E0E0, false);
            String toolLabel = "needs " + row.tool;
            int toolX = 24 + Math.min(240, this.width - 400);
            context.drawText(this.textRenderer, Text.literal(toolLabel), toolX, y + 3, 0x9A9A9A, false);
            row.xpField.setX(right - 150);
            row.xpField.setY(y + 1);
            row.xpField.render(context, mouseX, mouseY, delta);
            context.drawText(this.textRenderer, Text.literal("XP"), right - 98, y + 3, 0xFFFFFF, false);
            y += ROW_HEIGHT;
        }
        context.disableScissor();

        if (this.doneButton != null) this.doneButton.render(context, mouseX, mouseY, delta);
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