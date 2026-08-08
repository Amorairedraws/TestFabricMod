package com.amorairedraws.equipleveling.client.screen;

import com.amorairedraws.equipleveling.screen.EquipmentEnchantingOffer;
import com.amorairedraws.equipleveling.screen.EquipmentEnchantingScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/** A compact, vanilla-style enchanting-table inventory screen. */
public final class EquipmentEnchantingScreen extends HandledScreen<EquipmentEnchantingScreenHandler> {
    private ButtonWidget rerollButton;
    private final ButtonWidget[] offerButtons = new ButtonWidget[3];

    public EquipmentEnchantingScreen(EquipmentEnchantingScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = EquipmentEnchantingScreenHandler.WIDTH;
        this.backgroundHeight = EquipmentEnchantingScreenHandler.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        // The square replaces the vanilla lapis slot and stays in the same place.
        rerollButton = addDrawableChild(ButtonWidget.builder(Text.literal("↻"),
                b -> client.interactionManager.clickButton(handler.syncId, 3))
                .dimensions(x + 35, y + 47, 18, 18).build());
        for (int i = 0; i < offerButtons.length; i++) {
            final int offer = i;
            offerButtons[i] = addDrawableChild(ButtonWidget.builder(Text.literal(""),
                    b -> client.interactionManager.clickButton(handler.syncId, offer))
                    .dimensions(x + 60, y + 25 + i * 29, 108, 20).build());
        }
        refreshButtonState();
    }

    @Override
    public void handledScreenTick() {
        super.handledScreenTick();
        refreshButtonState();
    }

    private void refreshButtonState() {
        if (rerollButton == null) return;
        boolean hasOffers = handler.offers.length == 3
                && (handler.offers[0] != null || handler.offers[1] != null || handler.offers[2] != null);
        rerollButton.active = hasOffers && client.player != null
                && client.player.experienceLevel >= handler.getRerollCost();
        rerollButton.setMessage(Text.literal("↻"));
        for (int i = 0; i < offerButtons.length; i++) {
            EquipmentEnchantingOffer offer = handler.offers.length == 3 ? handler.offers[i] : null;
            offerButtons[i].active = offer != null;
            offerButtons[i].setMessage(offer == null ? Text.literal("—") : describe(offer));
        }
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        // Vanilla enchanting-table palette, with explicit slot grids so the
        // input and inventory cannot blend into the background.
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFFC6C6C6);
        context.fill(x, y, x + backgroundWidth, y + 22, 0xFF8B8B8B);
        context.fill(x, y + 22, x + backgroundWidth, y + 23, 0xFF555555);
        drawSlot(context, x + 15, y + 47);
        // The reroll widget covers this second vanilla slot.
        drawSlot(context, x + 35, y + 47);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(context, x + 8 + column * 18, y + 140 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlot(context, x + 8 + column * 18, y + 198);
        }

        context.drawText(textRenderer, title, x + 8, y + 7, 0xFFFFFFFF, false);
        context.drawText(textRenderer, Text.translatable("equip_leveling.enchanting.options"),
                x + 60, y + 8, 0xFFFFFFFF, false);
        context.drawText(textRenderer,
                Text.translatable("equip_leveling.enchanting.xp_cost", handler.getRerollCost()),
                x + 57, y + 51, 0xFF35A336, false);
        context.drawText(textRenderer, Text.translatable("container.inventory"),
                x + 8, y + 130, 0xFF404040, false);
    }

    private static void drawSlot(DrawContext context, int x, int y) {
        context.fill(x, y, x + 18, y + 18, 0xFF373737);
        context.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
        context.fill(x + 2, y + 2, x + 16, y + 16, 0xFF202020);
    }

    private Text describe(EquipmentEnchantingOffer offer) {
        if (offer instanceof EquipmentEnchantingOffer.NewEnchantment n) {
            return Text.literal(formatName(n.enchantmentId) + " I");
        }
        if (offer instanceof EquipmentEnchantingOffer.Upgrade u) {
            return Text.literal("Upgrade " + formatName(u.slot.enchantmentId) + " "
                    + u.slot.enchantmentLevel + " → " + (u.slot.enchantmentLevel + 1));
        }
        return Text.translatable("equip_leveling.enchanting.legendary");
    }

    private static String formatName(String id) {
        String name = id == null ? "Unknown" : id.substring(id.indexOf(':') + 1)
                .replace('_', ' ').replace('-', ' ');
        StringBuilder result = new StringBuilder();
        for (String word : name.split("\\s+")) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
