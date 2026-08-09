package com.amorairedraws.equipleveling.client;

import com.amorairedraws.equipleveling.client.render.BrokenItemRenderer;
import com.amorairedraws.equipleveling.client.tooltip.EquipmentTooltipRenderer;
import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.screen.VanillaEnchantingTableLogic;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
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
            ButtonWidget reroll = ButtonWidget.builder(Text.literal("\u21BA"), button -> {
                if (client.interactionManager != null) {
                    client.interactionManager.clickButton(handler.syncId, 3);
                }
            }).dimensions(left + 34, top + 46, 18, 18).build();
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
}
