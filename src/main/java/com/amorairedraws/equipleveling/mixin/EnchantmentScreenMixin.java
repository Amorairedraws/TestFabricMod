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
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/gui/container/enchanting_table.png");

    private static final int WHITE = 0xFFFFFFFF;
    private static final int GOLD = 0xFFFFD700;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;

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

        // The offer-row background panels stay at the vanilla width (108) so they
        // don't poke out of the UI. The text box (trim width) is widened instead
        // so the two-line label can extend further right without being cut off.
        int rowW = 108;
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
                // The text box (trim width) is widened so long names extend
                // further right than the panel without being cut off early. The
                // title and subtitle are drawn slightly larger and closer
                // together, with a lighter subtitle.
                String clippedTitle = textRenderer.trimToWidth(title, 130);
                String clippedSubtitle = textRenderer.trimToWidth(subtitle, 130);
                drawScaledText(context, clippedTitle, rowX + 18, rowY + 4, 0.75f, titleColor);
                drawScaledText(context, clippedSubtitle, rowX + 18, rowY + 11, 0.65f, 0xFFA0A0A0);
            }
        }

        // Reroll cost: a unicode filled circle followed by the number, drawn
        // directly under the reroll button (button at x+34/y+46, 18x18, so it
        // spans y+46..y+64). The left edge of the circle+number is aligned with
        // the button's left edge (x+34). The circle shares the number's colour.
        if (tracked && client.world != null && client.player != null) {
            var registry = client.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
            int cost = VanillaEnchantingTableLogic.getRerollCost(input.stack(), handler, registry);
            boolean affordable = client.player.isInCreativeMode() || client.player.experienceLevel >= cost;
            int color = affordable ? GREEN : RED;
            String costText = "\u25CF " + cost;
            int costX = i + 34; // left-aligned with the button's left edge
            int costY = j + 66; // just under the button
            drawScaledText(context, costText, costX, costY, 0.8f, color);
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
