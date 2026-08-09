package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.screen.VanillaEnchantingTableLogic;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.EnchantmentScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws readable leveling labels over vanilla's own three enchanting rows. */
@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin extends HandledScreen<EnchantmentScreenHandler> {
    private static final Identifier ENABLED_ROW = Identifier.ofVanilla("container/enchanting_table/enchantment_slot");
    private static final Identifier DISABLED_ROW = Identifier.ofVanilla("container/enchanting_table/enchantment_slot_disabled");
    private static final Identifier LEVEL_ICON = Identifier.ofVanilla("container/enchanting_table/level_1");

    private EnchantmentScreenMixin(EnchantmentScreenHandler handler, net.minecraft.entity.player.PlayerInventory inventory,
            net.minecraft.text.Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "drawBackground", at = @At("RETURN"))
    private void equipLeveling$drawReadableOfferNames(DrawContext context, float deltaTicks, int mouseX, int mouseY,
            CallbackInfo ci) {
        ItemStackView input = new ItemStackView(handler.getSlot(0).getStack());
        if (!input.isTracked() || client.world == null) return;
        var registry = client.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        int rerollCost = VanillaEnchantingTableLogic.getRerollCost(input.stack(), handler, registry);
        // One clear number sits directly beside the ↺ button over the old lapis slot.
        context.drawTextWithShadow(textRenderer, Integer.toString(rerollCost), this.x + 56, this.y + 52, 0x55FF55);
        for (int row = 0; row < 3; row++) {
            int rowX = this.x + 60;
            int rowY = this.y + 14 + row * 19;
            boolean active = VanillaEnchantingTableLogic.getOfferKind(handler, row)
                    != VanillaEnchantingTableLogic.OfferKind.NONE;
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, active ? ENABLED_ROW : DISABLED_ROW,
                    rowX, rowY, 108, 19);
            if (!active) continue;
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, LEVEL_ICON, rowX + 1, rowY + 1, 16, 16);
            String label = VanillaEnchantingTableLogic.describeOffer(handler, row, registry);
            int available = 84;
            String clipped = textRenderer.trimToWidth(label, available);
            context.drawTextWithShadow(textRenderer, clipped, rowX + 20, rowY + 6, 0xFFFFFF);
        }
    }

    /** Keeps the mixin's ItemStack dependency out of the rendering loop's imports. */
    private record ItemStackView(net.minecraft.item.ItemStack stack) {
        boolean isTracked() {
            return EquipmentComponent.isTracked(stack);
        }
    }
}
