package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GrindstoneScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the custom grindstone XP payout at the result-slot take hook. */
@Mixin(targets = "net.minecraft.screen.GrindstoneScreenHandler$4")
public abstract class ScreenHandlerMixin {
    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void equipLeveling$grindstoneXp(PlayerEntity player, ItemStack output, CallbackInfo ci) {
        GrindstoneScreenHandler handler = null;
        // The anonymous result slot stores its parent handler in a synthetic
        // field. Looking it up avoids depending on that compiler-generated name.
        for (java.lang.reflect.Field field : this.getClass().getDeclaredFields()) {
            if (GrindstoneScreenHandler.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    handler = (GrindstoneScreenHandler) field.get(this);
                } catch (ReflectiveOperationException ignored) { }
                break;
            }
        }
        if (handler == null) return;
        int xp = getExperience(handler.getSlot(0).getStack())
                + getExperience(handler.getSlot(1).getStack());
        if (xp > 0) player.addExperience(xp);
    }

    private static int getExperience(ItemStack stack) {
        if (!stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) return 0;
        EquipmentComponent.EquipmentData data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
        int xp = 0;
        for (EquipmentComponent.EquipmentSlot slot : data.slots) if (!slot.isEmpty()) xp += slot.enchantmentLevel;
        for (EquipmentComponent.EquipmentSlot slot : data.bonusSlots) if (!slot.isEmpty()) xp += slot.enchantmentLevel;
        return xp;
    }
}
