package com.amorairedraws.equipleveling.client.screen;

import com.amorairedraws.equipleveling.screen.EquipmentEnchantingOffer;
import com.amorairedraws.equipleveling.screen.EquipmentEnchantingScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/** Vanilla-font, resource-pack-free screen for the three server-generated offers. */
public final class EquipmentEnchantingScreen extends HandledScreen<EquipmentEnchantingScreenHandler> {
    private ButtonWidget rerollButton;
    private final ButtonWidget[] offerButtons = new ButtonWidget[3];

    public EquipmentEnchantingScreen(EquipmentEnchantingScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 222; // includes the player inventory, like vanilla's table
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        rerollButton = addDrawableChild(ButtonWidget.builder(Text.translatable("equip_leveling.enchanting.reroll"),
                b -> client.interactionManager.clickButton(handler.syncId, 3))
                .dimensions(x + 64, y + 22, 48, 16).build());
        for (int i = 0; i < 3; i++) {
            final int offer = i;
            offerButtons[i] = addDrawableChild(ButtonWidget.builder(Text.translatable("equip_leveling.enchanting.select"),
                    b -> client.interactionManager.clickButton(handler.syncId, offer))
                    .dimensions(x + 8, y + 61 + i * 20, 48, 16).build());
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
        boolean usable = handler.offers.length == 3
                && (handler.offers[0] != null || handler.offers[1] != null || handler.offers[2] != null);
        rerollButton.active = usable && client.player != null
                && client.player.experienceLevel >= handler.getRerollCost();
        for (int i = 0; i < 3; i++) {
            offerButtons[i].active = handler.offers.length == 3 && handler.offers[i] != null;
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
        context.drawText(textRenderer, title, x + 8, y + 7, 0xFFFFFF, false);
        context.drawText(textRenderer, Text.translatable("equip_leveling.enchanting.cost", handler.getRerollCost()),
                x + 62, y + 40, 0xAAAAAA, false);
        for (int i = 0; i < 3; i++) {
            int lineY = y + 65 + i * 20;
            EquipmentEnchantingOffer offer = handler.offers.length == 3 ? handler.offers[i] : null;
            context.drawText(textRenderer, offer == null ? Text.literal("—") : describe(offer),
                    x + 60, lineY, 0xE0E0E0, false);
            if (offer != null) {
                context.drawText(textRenderer, Integer.toString(handler.offerLevels[i]),
                        x + 150, lineY, 0x55FF55, false);
            }
        }
    }

    private Text describe(EquipmentEnchantingOffer offer) {
        if (offer instanceof EquipmentEnchantingOffer.NewEnchantment n) {
            return Text.literal("New: " + shortId(n.enchantmentId) + " I");
        }
        if (offer instanceof EquipmentEnchantingOffer.Upgrade u) {
            return Text.literal("Upgrade: " + shortId(u.slot.enchantmentId)
                    + " " + u.slot.enchantmentLevel + "→" + (u.slot.enchantmentLevel + 1));
        }
        return Text.literal("Legendary tier upgrade");
    }

    private static String shortId(String id) {
        int colon = id == null ? -1 : id.indexOf(':');
        return colon < 0 ? String.valueOf(id) : id.substring(colon + 1);
    }
}
