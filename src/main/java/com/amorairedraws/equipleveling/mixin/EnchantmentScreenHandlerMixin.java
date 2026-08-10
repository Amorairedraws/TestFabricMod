package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.screen.VanillaEnchantingTableLogic;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rewires the existing enchantment-table handler rather than replacing it with
 * a separate menu. Its native inventory slots and three offer button IDs stay
 * intact; only their behavior changes for levelable equipment.
 */
@Mixin(EnchantmentScreenHandler.class)
public abstract class EnchantmentScreenHandlerMixin {
    @Shadow @Final private Inventory inventory;
    @Shadow @Final private ScreenHandlerContext context;

    @Unique private PlayerEntity equipLeveling$owner;
    @Unique private final Random equipLeveling$random = Random.create();
    @Unique private boolean equipLeveling$generating;

    @Inject(method = "<init>(ILnet/minecraft/entity/player/PlayerInventory;Lnet/minecraft/screen/ScreenHandlerContext;)V",
            at = @At("TAIL"))
    private void equipLeveling$rememberOwner(int syncId, PlayerInventory inventory,
            ScreenHandlerContext context, CallbackInfo ci) {
        this.equipLeveling$owner = inventory.player;
    }

    @Inject(method = "onContentChanged", at = @At("HEAD"), cancellable = true)
    private void equipLeveling$createLevelingOffers(Inventory changedInventory, CallbackInfo ci) {
        if (changedInventory != this.inventory) return;
        ItemStack input = inventory.getStack(0);
        if (isBook(input)) {
            clearVanillaOffers();
            ci.cancel();
            return;
        }
        if (!EquipmentComponent.isTracked(input)) return;

        // The real lapis slot remains visible as part of the vanilla texture,
        // but cannot be used by leveled equipment. Return legacy/previously
        // inserted lapis rather than silently deleting it.
        ItemStack lapis = inventory.getStack(1);
        if (!lapis.isEmpty() && equipLeveling$owner != null) {
            inventory.setStack(1, ItemStack.EMPTY);
            if (!equipLeveling$owner.getInventory().insertStack(lapis)) {
                equipLeveling$owner.dropItem(lapis, false);
            }
        }

        // On a client the ScreenHandlerContext is EMPTY, so this is a no-op and
        // the normal property packets supply the authoritative rows from server.
        if (equipLeveling$owner != null && !equipLeveling$generating) {
            equipLeveling$generating = true;
            try {
                context.run((world, pos) -> VanillaEnchantingTableLogic.generateOffers(
                        (EnchantmentScreenHandler) (Object) this, equipLeveling$owner, equipLeveling$random));
            } finally {
                equipLeveling$generating = false;
            }
        }
        ci.cancel();
    }

    @Inject(method = "onButtonClick", at = @At("HEAD"), cancellable = true)
    private void equipLeveling$useVanillaRowsForLeveling(PlayerEntity player, int id,
            CallbackInfoReturnable<Boolean> cir) {
        ItemStack input = inventory.getStack(0);
        if (isBook(input)) {
            cir.setReturnValue(false);
            return;
        }
        if (!EquipmentComponent.isTracked(input)) return;

        EnchantmentScreenHandler handler = (EnchantmentScreenHandler) (Object) this;
        if (id >= 0 && id < 3) {
            // Client-side invocation only acknowledges the existing vanilla row
            // hit test so it sends clickButton; the server is the sole mutator.
            if (player.getEntityWorld().isClient()) {
                cir.setReturnValue(VanillaEnchantingTableLogic.getOfferKind(handler, id)
                        != VanillaEnchantingTableLogic.OfferKind.NONE);
            } else {
                cir.setReturnValue(VanillaEnchantingTableLogic.selectOffer(handler, player, id, equipLeveling$random));
            }
            return;
        }
        if (id == 3) {
            cir.setReturnValue(VanillaEnchantingTableLogic.reroll(handler, player, equipLeveling$random));
            return;
        }
        cir.setReturnValue(false);
    }

    @Unique
    private void clearVanillaOffers() {
        EnchantmentScreenHandler handler = (EnchantmentScreenHandler) (Object) this;
        for (int i = 0; i < 3; i++) {
            handler.enchantmentPower[i] = 0;
            handler.enchantmentId[i] = -1;
            handler.enchantmentLevel[i] = -1;
        }
        handler.sendContentUpdates();
    }

    @Unique
    private static boolean isBook(ItemStack stack) {
        return stack.isOf(Items.BOOK) || stack.isOf(Items.ENCHANTED_BOOK)
                || stack.contains(net.minecraft.component.DataComponentTypes.STORED_ENCHANTMENTS);
    }
}
