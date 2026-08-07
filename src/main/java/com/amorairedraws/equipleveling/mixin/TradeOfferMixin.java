package com.amorairedraws.equipleveling.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents enchanted books from being offered by villagers, including modded offers. */
@Mixin(TradeOffer.class)
public abstract class TradeOfferMixin {
    @Inject(method = "getSellItem", at = @At("RETURN"), cancellable = true)
    private void equipLeveling$disableEnchantedBookTrades(CallbackInfoReturnable<ItemStack> cir) {
        if (isEnchantedBook(cir.getReturnValue())) cir.setReturnValue(ItemStack.EMPTY);
    }
    @Inject(method = "copySellItem", at = @At("RETURN"), cancellable = true)
    private void equipLeveling$disableCopiedBookTrades(CallbackInfoReturnable<ItemStack> cir) {
        if (isEnchantedBook(cir.getReturnValue())) cir.setReturnValue(ItemStack.EMPTY);
    }

    private static boolean isEnchantedBook(ItemStack stack) {
        return stack.isOf(Items.ENCHANTED_BOOK)
                || stack.contains(DataComponentTypes.STORED_ENCHANTMENTS);
    }
}
