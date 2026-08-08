package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.screen.GrindstoneScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Grindstones cleanse all Equip Leveling progression without changing durability. */
@Mixin(GrindstoneScreenHandler.class)
public abstract class GrindstoneScreenHandlerMixin {
    /**
     * Vanilla decides whether a one-item grind is possible by inspecting the
     * vanilla enchantment component. Broken equipment intentionally has that
     * component removed, so provide the equivalent one-item result directly
     * from the custom component instead of making broken gear impossible to
     * cleanse.
     */
    @Inject(method = "getOutputStack", at = @At("HEAD"), cancellable = true)
    private void equipLeveling$grindCustomProgression(ItemStack first, ItemStack second,
            CallbackInfoReturnable<ItemStack> cir) {
        if (!first.isEmpty() && !second.isEmpty()) return;
        ItemStack input = first.isEmpty() ? second : first;
        if (!EquipmentComponent.isTracked(input) || !input.contains(EquipmentComponent.EQUIPMENT_TYPE)) return;

        ItemStack output = input.copy();
        output.remove(EquipmentComponent.EQUIPMENT_TYPE);
        output.remove(DataComponentTypes.ENCHANTMENTS);
        cir.setReturnValue(output);
    }

    @Inject(method = "onContentChanged", at = @At("RETURN"))
    private void equipLeveling$stripProgression(Inventory inventory, CallbackInfo ci) {
        GrindstoneScreenHandler handler = (GrindstoneScreenHandler) (Object) this;
        ItemStack first = handler.getSlot(0).getStack();
        ItemStack second = handler.getSlot(1).getStack();
        ItemStack output = handler.getSlot(2).getStack();
        if (!output.isEmpty() && (EquipmentComponent.isTracked(first) || EquipmentComponent.isTracked(second))) {
            output.remove(EquipmentComponent.EQUIPMENT_TYPE);
            handler.getSlot(2).setStack(output);
        }
    }
}
