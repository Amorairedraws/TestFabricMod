package com.amorairedraws.equipleveling.client.screen;

import com.amorairedraws.equipleveling.screen.EquipmentEnchantingScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/** Vanilla-font, no-resource-pack screen for the equipment offers. */
public final class EquipmentEnchantingScreen extends HandledScreen<EquipmentEnchantingScreenHandler> {
    public EquipmentEnchantingScreen(EquipmentEnchantingScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        // Button presses are sent through the vanilla screen-handler button packet,
        // keeping offer selection and rerolls server-authoritative.
        addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("Reroll"),
                b -> client.interactionManager.clickButton(handler.syncId, 3))
                .dimensions(x + 64, y + 22, 48, 16).build());
        for (int i = 0; i < 3; i++) {
            final int offer = i;
            addDrawableChild(net.minecraft.client.gui.widget.ButtonWidget.builder(Text.literal("Select"),
                    b -> client.interactionManager.clickButton(handler.syncId, offer))
                    .dimensions(x + 8, y + 61 + i * 20, 48, 16).build());
        }
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF2B2B2B);
        context.fill(x, y, x + backgroundWidth, y + 1, 0xFF8B8B8B);
        context.fill(x, y + backgroundHeight - 1, x + backgroundWidth, y + backgroundHeight, 0xFF8B8B8B);
        context.fill(x, y, x + 1, y + backgroundHeight, 0xFF8B8B8B);
        context.fill(x + backgroundWidth - 1, y, x + backgroundWidth, y + backgroundHeight, 0xFF8B8B8B);
        context.drawText(textRenderer, Text.translatable("equip_leveling.enchanting.reroll"), x + 70, y + 25, 0xFFFFFF, false);
        for (int i = 0; i < handler.offers.length; i++) {
            var offer = handler.offers[i];
            String label = offer == null ? "—" : offer.getClass().getSimpleName().replace("$", "");
            context.drawText(textRenderer, label, x + 20, y + 65 + i * 20, 0xE0E0E0, false);
            context.drawText(textRenderer, Integer.toString(handler.offerLevels[i]), x + 150, y + 65 + i * 20, 0x55FF55, false);
        }
    }
}
