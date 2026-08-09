package com.amorairedraws.equipleveling.mixin;

import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes the existing second enchanting-table slot display-only. */
@Mixin(targets = "net.minecraft.screen.EnchantmentScreenHandler$3")
public abstract class EnchantmentLapisSlotMixin {
    @Inject(method = "canInsert", at = @At("HEAD"), cancellable = true)
    private void equipLeveling$disableLapis(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
