package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.client.render.BrokenItemRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Applies a strong red tint to baked item quads for broken equipment. */
@Mixin(targets = "net.minecraft.client.render.item.ItemRenderState$LayerRenderState")
public abstract class ItemRenderLayerMixin {
    @Shadow @Final private ItemRenderState field_55345;

    @ModifyArg(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitItem(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/item/ItemRenderState$Glint;)V"),
            index = 5)
    private int[] equipLeveling$tintBrokenItems(int[] original) {
        if (!((BrokenItemRenderState) field_55345).equipLeveling$isBroken() || original == null) return original;
        int[] tinted = original.clone();
        for (int i = 0; i < tinted.length; i++) tinted[i] = redTint(tinted[i]);
        return tinted;
    }

    private static int redTint(int color) {
        int alpha = color & 0xFF000000;
        int red = 0xFF;
        int green = ((color >>> 8) & 0xFF) * 2 / 5;
        int blue = (color & 0xFF) * 2 / 5;
        return alpha | red << 16 | green << 8 | blue;
    }
}
