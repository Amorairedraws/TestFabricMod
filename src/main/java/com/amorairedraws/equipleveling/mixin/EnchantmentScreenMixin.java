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
 * level-number icons.
 *
 * <p>Each offer row shows a leading symbol (Issue 4), a smaller two-line label
 * (Issue 2: enchantment name on top, offer kind below in a smaller darker
 * font), and the reroll cost is drawn as an experience orb followed by the
 * number (Issue 3) below the reroll button.
 *
 * <p>Note on colours: Minecraft text colours are 0xAARRGGBB. Values like
 * 0xFFFFFF have a zero alpha byte and are therefore fully transparent, so
 * every colour here deliberately includes a full 0xFF alpha prefix.</p>
 */
@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin extends HandledScreen<EnchantmentScreenHandler> {
    private static final Identifier ENABLED_ROW = Identifier.ofVanilla("container/enchanting_table/enchantment_slot");
    private static final Identifier DISABLED_ROW = Identifier.ofVanilla("container/enchanting_table/enchantment_slot_disabled");
    private static final Identifier HIGHLIGHTED_ROW = Identifier.ofVanilla("container/enchanting_table/enchantment_slot_highlighted");
    private static final Identifier LEVEL_ICON_DISABLED = Identifier.ofVanilla("container/enchanting_table/level_1_disabled");
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/gui/container/enchanting_table.png");

    private static final int WHITE = 0xFFFFFFFF;
    private static final int GOLD = 0xFFFFD700;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int DARK_GREY = 0xFF808080;

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

        // Draw the three offer rows, each expanded a little to the right so the
        // two-line label fits (Issue 2).
        int rowW = 124;
        for (int row = 0; row < 3; row++) {
            int rowX = i + 60;
            int rowY = j + 14 + row * 19;
            VanillaEnchantingTableLogic.OfferKind kind = tracked
                    ? VanillaEnchantingTableLogic.getOfferKind(handler, row)
                    : VanillaEnchantingTableLogic.OfferKind.NONE;
            boolean active = kind != VanillaEnchantingTableLogic.OfferKind.NONE;
            boolean legendary = kind == VanillaEnchantingTableLogic.OfferKind.LEGENDARY;

            boolean hovered = mouseX >= rowX && mouseY >= rowY
                    && mouseX < rowX + rowW && mouseY < rowY + 19;

            if (active && hovered) {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, HIGHLIGHTED_ROW, rowX, rowY, rowW, 19);
            } else {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, active ? ENABLED_ROW : DISABLED_ROW,
                        rowX, rowY, rowW, 19);
            }

            if (active) {
                // Leading symbol (Issue 4): ➕ new, ⏫ upgrade, ◈ legendary.
                String symbol = legendary ? "\u25C8"
                        : (kind == VanillaEnchantingTableLogic.OfferKind.UPGRADE ? "\u23EB" : "\u2795");
                int symbolColor = legendary ? GOLD : WHITE;
                context.drawTextWithShadow(textRenderer, symbol, rowX + 4, rowY + 6, symbolColor);

                // Two-line label (Issue 2). Title is the enchantment name (or
                // "Material Upgrade" for legendary); subtitle is the offer kind.
                String title;
                String subtitle;
                if (client.world != null) {
                    var registry = client.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
                    title = VanillaEnchantingTableLogic.describeOffer(handler, row, registry);
                } else {
                    title = VanillaEnchantingTableLogic.describeOfferFallback(handler, row);
                }
                subtitle = VanillaEnchantingTableLogic.describeOfferSubtitle(handler, row);
                if (title == null || title.isEmpty()) title = "Unknown offer";

                int titleColor = legendary ? GOLD : WHITE;
                // Trim against the wider row (Issue 2) so long names don't spill
                // out of the box, then render smaller so more fits comfortably.
                String clippedTitle = textRenderer.trimToWidth(title, 96);
                String clippedSubtitle = textRenderer.trimToWidth(subtitle, 96);
                drawScaledText(context, clippedTitle, rowX + 18, rowY + 2, 0.7f, titleColor);
                drawScaledText(context, clippedSubtitle, rowX + 18, rowY + 12, 0.6f, DARK_GREY);
            }
        }

        // Reroll cost (Issue 3): an experience orb (unlit, no number) followed by
        // the cost number, drawn just below the reroll button (button at x+34/y+46).
        if (tracked && client.world != null && client.player != null) {
            var registry = client.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
            int cost = VanillaEnchantingTableLogic.getRerollCost(input.stack(), handler, registry);
            boolean affordable = client.player.isInCreativeMode() || client.player.experienceLevel >= cost;
            int costX = i + 34;
            int costY = j + 47 + 18 + 1; // just under the button, nudged up
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, LEVEL_ICON_DISABLED, costX, costY, 11, 11);
            drawScaledText(context, String.valueOf(cost), costX + 13, costY + 2, 0.8f,
                    affordable ? GREEN : RED);
        }
    }

    /** Draws text scaled down so it reads smaller than the default font size. */
    @Unique
    private void drawScaledText(DrawContext context, String text, int x, int y, float scale, int color) {
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate((float) x, (float) y);
        matrices.scale(scale, scale);
        context.drawTextWithShadow(textRenderer, text, 0, 0, color);
        matrices.popMatrix();
    }

    /** Keeps the mixin's ItemStack dependency out of the rendering loop's imports. */
    @Unique
    private record ItemStackView(net.minecraft.item.ItemStack stack) {
        boolean isTracked() {
            return EquipmentComponent.isTracked(stack);
        }
    }
}
