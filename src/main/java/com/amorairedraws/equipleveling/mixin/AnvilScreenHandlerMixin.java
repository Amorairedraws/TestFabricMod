package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restricts the anvil to <b>renaming only</b> for tracked equipment.
 *
 * <p>Equipment progression (leveling, enchantment slots, durability repair) is
 * owned entirely by this mod: the custom enchanting-table offers and the Repair
 * Kit / Diamond Repair Kit recipes. Material repair, combining two items, and
 * applying enchanted books would all strip or duplicate a tracked item's data,
 * so they are turned off. Renaming is harmless \\u2014 it only writes the vanilla
 * {@code CUSTOM_NAME} component, which the rest of the mod (leveling, legendary
 * material promotion, and Repair Kit repair) already preserves verbatim.
 */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {
    @Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
    private void equipLeveling$blockTrackedEquipment(CallbackInfo ci) {
        AnvilScreenHandler handler = (AnvilScreenHandler)(Object)this;
        ItemStack left = handler.getSlot(0).getStack();
        ItemStack right = handler.getSlot(1).getStack();

        // A tracked item in the second slot is always a material / combine /
        // enchanted-book source, never a rename. A tracked item in the first slot
        // is only legal when the second slot is empty (a pure rename).
        if (EquipmentComponent.isTracked(right)
                || (EquipmentComponent.isTracked(left) && !right.isEmpty())) {
            handler.getSlot(2).setStack(ItemStack.EMPTY);
            ci.cancel();
        }
    }
}
