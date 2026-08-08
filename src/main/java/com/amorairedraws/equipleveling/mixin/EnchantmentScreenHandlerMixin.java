package com.amorairedraws.equipleveling.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.EnchantmentScreenHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents the vanilla table from turning books into enchanted books. */
@Mixin(EnchantmentScreenHandler.class)
public abstract class EnchantmentScreenHandlerMixin {
    @Shadow @Final private Inventory inventory;

    @Inject(method = "onContentChanged", at = @At("HEAD"), cancellable = true)
    private void equipLeveling$disableBookOffers(Inventory changedInventory, CallbackInfo ci) {
        if (isBook(inventory.getStack(0))) {
            ((EnchantmentScreenHandler) (Object) this).getSlot(2).setStack(ItemStack.EMPTY);
            ci.cancel();
        }
    }

    @Inject(method = "onButtonClick", at = @At("HEAD"), cancellable = true)
    private void equipLeveling$rejectBookSelection(PlayerEntity player, int id,
            CallbackInfoReturnable<Boolean> cir) {
        if (isBook(inventory.getStack(0))) cir.setReturnValue(false);
    }

    private static boolean isBook(ItemStack stack) {
        return stack.isOf(Items.BOOK) || stack.isOf(Items.ENCHANTED_BOOK)
                || stack.contains(net.minecraft.component.DataComponentTypes.STORED_ENCHANTMENTS);
    }
}
