package com.amorairedraws.equipleveling.client;

import com.amorairedraws.equipleveling.client.render.BrokenItemRenderer;
import com.amorairedraws.equipleveling.client.tooltip.EquipmentTooltipRenderer;
import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import com.amorairedraws.equipleveling.network.ConfigSyncPacket;
import com.amorairedraws.equipleveling.screen.VanillaEnchantingTableLogic;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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

        // Receive server config sync. When we join a multiplayer server, the
        // server sends us its config. We switch to a per-server config file so
        // our personal singleplayer settings are never overwritten.
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                var connection = context.client().getNetworkHandler();
                if (connection != null) {
                    String address = connection.getServerInfo() != null
                            ? connection.getServerInfo().address
                            : "unknown_server";
                    EquipLevelingConfig.loadServerConfig(address, payload.json());
                }
            });
        });

        // When disconnecting from a server, restore our personal config.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            EquipLevelingConfig.restorePersonalConfig();
        });

        // Periodically refresh component state on the client (every 2 seconds).
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            if (++equipLeveling$tickCounter < 40) return;
            equipLeveling$tickCounter = 0;
            var lookup = client.world.getRegistryManager();
            var inventory = client.player.getInventory();
            for (int i = 0; i < inventory.size(); i++) {
                var stack = inventory.getStack(i);
                if (EquipmentComponent.isTracked(stack)) {
                    EquipmentComponent.getOrCreate(stack, lookup);
                }
            }
            for (net.minecraft.entity.EquipmentSlot slot : new net.minecraft.entity.EquipmentSlot[]{
                    net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
                    net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET,
                    net.minecraft.entity.EquipmentSlot.OFFHAND}) {
                var equippedStack = client.player.getEquippedStack(slot);
                if (EquipmentComponent.isTracked(equippedStack)) {
                    EquipmentComponent.getOrCreate(equippedStack, lookup);
                }
            }
        });

        // Add reroll button to the enchanting screen.
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

    private static int equipLeveling$tickCounter = 0;

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

    private static final class RerollButton extends ButtonWidget.Text {
        RerollButton(int x, int y, int width, int height, PressAction onPress) {
            super(x, y, width, height, net.minecraft.text.Text.of("\u21BA"), onPress, DEFAULT_NARRATION_SUPPLIER);
        }

        @Override
        protected void drawIcon(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
            this.drawButton(context);
            int color = this.active ? 0xFFFFFFFF : 0xFFA0A0A0;
            var matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(this.getX() + this.getWidth() / 2f, this.getY() + this.getHeight() / 2f);
            matrices.scale(3.0f, 3.0f);
            context.drawCenteredTextWithShadow(
                    net.minecraft.client.MinecraftClient.getInstance().textRenderer,
                    "\u21BA", 0, -5, color);
            matrices.popMatrix();
        }
    }
}
