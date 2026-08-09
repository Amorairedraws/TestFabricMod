package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extends vanilla anvil rules without replacing the vanilla repair algorithm. */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {
    @Shadow @Final private Property levelCost;

    @Inject(method = "updateResult", at = @At("RETURN"))
    private void equipLeveling$applyLevelBasedCost(CallbackInfo ci) {
        AnvilScreenHandler handler = (AnvilScreenHandler)(Object)this;
        ItemStack left = handler.getSlot(0).getStack();
        if (!EquipmentComponent.isTracked(left) || handler.getSlot(2).getStack().isEmpty()) return;
        // The named field is a Property in 1.21.11, not an int. Shadowing it
        // keeps the level cost synchronized to the client and survives remapping.
        this.levelCost.set(EquipmentComponent.repairCost(left));
    }

    @Inject(method = "canTakeOutput", at = @At("HEAD"), cancellable = true)
    private void equipLeveling$removeTooExpensiveLimit(PlayerEntity player, boolean present, CallbackInfoReturnable<Boolean> cir) {
        AnvilScreenHandler handler = (AnvilScreenHandler)(Object)this;
        if (present && EquipmentComponent.isTracked(handler.getSlot(0).getStack())
                && !handler.getSlot(2).getStack().isEmpty()) {
            // Remove only the vanilla 40-level "too expensive" ceiling; a
            // player still has to pay the configured level cost unless creative.
            cir.setReturnValue(player.isCreative() || player.experienceLevel >= handler.getLevelCost());
        }
    }

    @Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
    private void equipLeveling$blockEquipmentCombining(CallbackInfo ci) {
        AnvilScreenHandler handler = (AnvilScreenHandler)(Object)this;
        ItemStack left = handler.getSlot(0).getStack();
        ItemStack right = handler.getSlot(1).getStack();
        if (EquipmentComponent.isTracked(left) && !right.isEmpty()) {
            // A broken item being repaired with its material must be allowed
            // through vanilla's repair path. Vanilla bails out early when
            // canHaveEnchantments() is false, which our broken mechanic makes
            // true by removing the ENCHANTMENTS component. Temporarily restore
            // it so the repair proceeds; onTakeOutput clears the broken flag.
            if (EquipmentComponent.isBroken(left) && left.canRepairWith(right)) {
                EquipmentComponent.restoreEnchantmentsForRepair(left);
                return; // do not cancel; let vanilla repair the item
            }
            if (EquipmentComponent.isTracked(right) || isEnchantedBook(right)) {
                handler.getSlot(2).setStack(ItemStack.EMPTY);
                ci.cancel();
            }
        }
    }

    private static boolean isEnchantedBook(ItemStack stack) {
        return stack.isOf(net.minecraft.item.Items.ENCHANTED_BOOK)
                || stack.contains(net.minecraft.component.DataComponentTypes.STORED_ENCHANTMENTS);
    }

    @Inject(method = "onTakeOutput", at = @At("RETURN"))
    private void equipLeveling$repairBroken(PlayerEntity player, ItemStack output, CallbackInfo ci) {
        if (EquipmentComponent.isTracked(output)) {
            if (!output.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
                EquipmentComponent.getOrCreate(output);
                EquipmentComponent.restoreEnchantments(output,
                        player.getEntityWorld().getRegistryManager());
            }
        }
        if (EquipmentComponent.isTracked(output) && output.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
            var data = output.get(EquipmentComponent.EQUIPMENT_TYPE);
            if (data != null && data.broken) {
                data.broken = false;
                data.refresh(com.amorairedraws.equipleveling.util.EquipmentCategory.getCategory(output));
                output.set(EquipmentComponent.EQUIPMENT_TYPE, data);
                // A broken stack has its mirrored vanilla enchantments removed;
                // rebuild them after the material repair restores functionality.
                EquipmentComponent.restoreEnchantments(output,
                        player.getEntityWorld().getRegistryManager());
            }
        }
    }
}
