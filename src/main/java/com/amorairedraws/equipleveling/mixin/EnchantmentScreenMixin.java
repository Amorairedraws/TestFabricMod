package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.screen.VanillaEnchantingTableLogic;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.EnchantmentScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fully replaces the vanilla enchanting-table offer rendering for levelable
 * equipment. The background texture and the floating book are kept (via the
 * shadowed drawBook), but the three offer rows are re-drawn from scratch so we
 * control the label text, the hover highlight, and the absence of the vanilla
 * level-number icons. The reroll cost is drawn as a lit/unlit experience orb
 * directly below the reroll button.
 */
@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin extends HandledScreen<EnchantmentScreenHandler> {
    private static final Identifier ENABLED_ROW = Identifier.ofVanilla("container/enchanting_table/enchantment_slot");
    private static final Identifier DISABLED_ROW = Identifier.ofVanilla("container/enchanting_table/enchantment_slot_disabled");
    private static final Identifier HIGHLIGHTED_ROW = Identifier.ofVanilla("container/enchanting_table/enchantment_slot_highlighted");
    private static final Identifier LEVEL_ICON = Identifier.ofVanilla("container/enchanting_table/level_1");
    private static final Identifier LEVEL_ICON_DISABLED = Identifier.ofVanilla("container/enchanting_table/level_1_disabled");
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/gui/container/enchanting_table.png");

    @Shadow
    private void drawBook(DrawContext context, int x, int y) { }

    private EnchantmentScreenMixin(EnchantmentScreenHandler handler, net.minecraft.entity.player.PlayerInventory inventory,
            net.minecraft.text.Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
    private void equipLeveling$drawCustomOffers(DrawContext context, float deltaTicks, int mouseX, int mouseY,
            CallbackInfo ci) {
        int i = (this.width - this.backgroundWidth) / 2;
        int j = (this.height - this.backgroundHeight) / 2;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, TEXTURE, i, j, 0.0F, 0.0F,
                this.backgroundWidth, this.backgroundHeight, 256, 256);
        this.drawBook(context, i, j);
        ci.cancel();

        ItemStackView input = new ItemStackView(handler.getSlot(0).getStack());
        boolean tracked = input.isTracked();

        // Draw the three offer rows.
        for (int row = 0; row < 3; row++) {
            int rowX = i + 60;
            int rowY = j + 14 + row * 19;
            boolean active = tracked && VanillaEnchantingTableLogic.getOfferKind(handler, row)
                    != VanillaEnchantingTableLogic.OfferKind.NONE;

            boolean hovered = mouseX >= rowX && mouseY >= rowY
                    && mouseX < rowX + 108 && mouseY < rowY + 19;

            if (active && hovered) {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HIGHLIGHTED_ROW, rowX, rowY, 108, 19);
            } else {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, active ? ENABLED_ROW : DISABLED_ROW,
                        rowX, rowY, 108, 19);
            }

            if (active && client.world != null) {
                var registry = client.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
                String label = VanillaEnchantingTableLogic.describeOffer(handler, row, registry);
                String clipped = textRenderer.trimToWidth(label, 84);
                context.drawTextWithShadow(textRenderer, clipped, rowX + 12, rowY + 6, 0xFFFFFF);
            }
        }

        // Draw the reroll cost orb below the reroll button (button is at x+34/y+46).
        if (tracked && client.world != null && client.player != null) {
            var registry = client.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
            int cost = VanillaEnchantingTableLogic.getRerollCost(input.stack(), handler, registry);
            boolean affordable = client.player.isInCreativeMode() || client.player.experienceLevel >= cost;
            int orbX = i + 34;
            int orbY = j + 47 + 18 + 1;
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, affordable ? LEVEL_ICON : LEVEL_ICON_DISABLED,
                    orbX, orbY, 16, 16);
            String costStr = Integer.toString(cost);
            context.drawTextWithShadow(textRenderer, costStr,
                    orbX + 17, orbY + 4, affordable ? 0x55FF55 : 0xFF5555);
        }
    }

    /** Keeps the mixin's ItemStack dependency out of the rendering loop's imports. */
    @Unique
    private record ItemStackView(net.minecraft.item.ItemStack stack) {
        boolean isTracked() {
            return EquipmentComponent.isTracked(stack);
        }
    }
}
