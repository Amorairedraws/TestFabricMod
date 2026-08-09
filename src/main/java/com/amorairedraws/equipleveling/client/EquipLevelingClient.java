package com.amorairedraws.equipleveling.client;

import com.amorairedraws.equipleveling.client.render.BrokenItemRenderer;
import com.amorairedraws.equipleveling.client.tooltip.EquipmentTooltipRenderer;
import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.screen.VanillaEnchantingTableLogic;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.EnchantmentScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.text.Text;

/** Client-only presentation hooks. The enchanting screen itself remains vanilla. */
public class EquipLevelingClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        new EquipmentTooltipRenderer().register();
        new BrokenItemRenderer().register();

        // The original lapis-slot coordinates are x+35/y+47. Cloth/Fabric's
        // screen API adds a normal vanilla button there without replacing the
        // EnchantmentScreen texture, inventory, book, or option hit boxes.
        // The button is nudged 1px up and left so it sits centered over the slot.
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof EnchantmentScreen enchanting)) return;
            EnchantmentScreenHandler handler = enchanting.getScreenHandler();
            int left = (width - 176) / 2;
            int top = (height - 166) / 2;
            RerollButton reroll = new RerollButton(left + 34, top + 46, 18, 18, button -> {
                if (client.interactionManager != null) {
                    client.interactionManager.clickButton(handler.syncId, 3);
                }
            });
            Screens.getButtons(screen).add(reroll);
            ScreenEvents.afterTick(screen).register(ignored -> reroll.active = canReroll(client, handler));
        });
    }

    private static boolean canReroll(net.minecraft.client.MinecraftClient client,
            EnchantmentScreenHandler handler) {
        if (client.player == null || client.world == null) return false;
        var stack = handler.getSlot(0).getStack();
        var data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
        if (data == null || !data.readyToLevelUp || data.broken || data.maxed) return false;
        int cost = VanillaEnchantingTableLogic.getRerollCost(stack, handler,
                client.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT));
        return client.player.isInCreativeMode() || client.player.experienceLevel >= cost;
    }

    /**
     * A small reroll button that draws the U+21BA glyph much larger than the
     * default button text so it reads clearly inside the 18x18 lapis slot box.
     * The glyph is drawn on a scaled matrix so it floats larger than the button
     * text would normally render.
     */
    private static final class RerollButton extends ButtonWidget.Text {
        RerollButton(int x, int y, int width, int height, PressAction onPress) {
            super(x, y, width, height, net.minecraft.text.Text.literal("\u21BA"), onPress, DEFAULT_NARRATION_SUPPLIER);
        }

        @Override
        protected void drawIcon(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
            this.drawButton(context);
            // Full-alpha colours (0xAARRGGBB); 0xFFFFFF is transparent and would
            // make the glyph invisible.
            int color = this.active ? 0xFFFFFFFF : 0xFFA0A0A0;
            var matrices = context.getMatrices();
            matrices.pushMatrix();
            // Center the glyph in the box, then scale it up ~1.9x.
            matrices.translate(this.getX() + this.getWidth() / 2f, this.getY() + this.getHeight() / 2f);
            matrices.scale(1.9f, 1.9f);
            context.drawCenteredTextWithShadow(
                    net.minecraft.client.MinecraftClient.getInstance().textRenderer,
                    "\u21BA", 0, -4, color);
            matrices.popMatrix();
        }
    }
}
