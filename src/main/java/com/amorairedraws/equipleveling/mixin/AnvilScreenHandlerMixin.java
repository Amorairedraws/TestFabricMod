package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extends vanilla anvil rules without replacing the vanilla repair algorithm. */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {
    // Intermediary name is used explicitly because this field is private in
    // 1.21.11 and Loom cannot remap its named shadow reliably.
    @Shadow(remap = false) private int field_7770;

    @Inject(method = "updateResult", at = @At("RETURN"))
    private void equipLeveling$applyLevelBasedCost(CallbackInfo ci) {
        AnvilScreenHandler handler = (AnvilScreenHandler)(Object)this;
        ItemStack left = handler.getSlot(0).getStack();
        if (EquipmentComponent.isTracked(left) && !handler.getSlot(2).getStack().isEmpty()) {
            field_7770 = EquipmentComponent.repairCost(left);
        }
    }

    @Inject(method = "canTakeOutput", at = @At("HEAD"), cancellable = true)
    private void equipLeveling$removeTooExpensiveLimit(PlayerEntity player, boolean present, CallbackInfoReturnable<Boolean> cir) {
        AnvilScreenHandler handler = (AnvilScreenHandler)(Object)this;
        if (EquipmentComponent.isTracked(handler.getSlot(0).getStack())
                && !handler.getSlot(2).getStack().isEmpty()) cir.setReturnValue(true);
    }

    @Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
    private void equipLeveling$blockEquipmentCombining(CallbackInfo ci) {
        AnvilScreenHandler handler = (AnvilScreenHandler)(Object)this;
        ItemStack left = handler.getSlot(0).getStack();
        ItemStack right = handler.getSlot(1).getStack();
        if (EquipmentComponent.isTracked(left) && !right.isEmpty()
                && EquipmentComponent.isTracked(right)) {
            handler.getSlot(2).setStack(ItemStack.EMPTY);
            ci.cancel();
        }
    }

    @Inject(method = "onTakeOutput", at = @At("RETURN"))
    private void equipLeveling$repairBroken(PlayerEntity player, ItemStack output, CallbackInfo ci) {
        if (EquipmentComponent.isTracked(output) && output.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
            var data = output.get(EquipmentComponent.EQUIPMENT_TYPE);
            if (data != null && data.broken) {
                data.broken = false;
                output.setDamage(Math.max(0, output.getDamage()));
                output.set(EquipmentComponent.EQUIPMENT_TYPE, data);
            }
        }
    }
}
