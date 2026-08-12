package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Completely disables the anvil for tracked equipment.
 *
 * <p>Equipment progression (leveling, enchantment slots, repair) is owned
 * entirely by this mod: the custom enchanting-table offers and the Repair Kit /
 * Diamond Repair Kit recipes. Vanilla anvil interactions \u2014 material repair,
 * combining two items, applying enchanted books, and renaming \u2014 are all
 * turned off so they can never strip or duplicate a tracked item's data.
 */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {
    @Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
    private void equipLeveling$blockTrackedEquipment(CallbackInfo ci) {
        AnvilScreenHandler handler = (AnvilScreenHandler)(Object)this;
        ItemStack left = handler.getSlot(0).getStack();
        ItemStack right = handler.getSlot(1).getStack();

        // If either input slot holds tracked equipment, produce no output.
        if (EquipmentComponent.isTracked(left) || EquipmentComponent.isTracked(right)) {
            handler.getSlot(2).setStack(ItemStack.EMPTY);
            ci.cancel();
        }
    }
}
