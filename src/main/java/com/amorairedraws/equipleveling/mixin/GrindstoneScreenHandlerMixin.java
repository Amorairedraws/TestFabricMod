package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GrindstoneScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Grindstones cleanse all Equip Leveling progression, while preserving durability. */
@Mixin(GrindstoneScreenHandler.class)
public abstract class GrindstoneScreenHandlerMixin {
    @Inject(method = "onContentChanged", at = @At("RETURN"))
    private void equipLeveling$stripProgression(Inventory inventory, CallbackInfo ci) {
        GrindstoneScreenHandler handler = (GrindstoneScreenHandler)(Object)this;
        ItemStack first = handler.getSlot(0).getStack();
        ItemStack second = handler.getSlot(1).getStack();
        ItemStack output = handler.getSlot(2).getStack();
        if (!output.isEmpty() && (EquipmentComponent.isTracked(first) || EquipmentComponent.isTracked(second))) {
            output.remove(EquipmentComponent.EQUIPMENT_TYPE);
            handler.getSlot(2).setStack(output);
        }
    }

}
