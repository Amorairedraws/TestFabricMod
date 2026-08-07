package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GrindstoneScreenHandler;
import net.minecraft.component.type.ItemEnchantmentsComponent;
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
        // The common tick synchronizer mirrors custom slots into vanilla's
        // enchantment component. In that case vanilla's result-slot hook already
        // awards the normal grindstone XP; adding it here would double the payout.
        // This fallback is only for a freshly-created component that has not yet
        // been mirrored (for example, an item inserted immediately after login).
        if (!stack.getEnchantments().getEnchantmentEntries().isEmpty()) return 0;
        EquipmentComponent.EquipmentData data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
        int xp = 0;
        for (EquipmentComponent.EquipmentSlot slot : data.slots) xp += slotExperience(slot);
        for (EquipmentComponent.EquipmentSlot slot : data.bonusSlots) xp += slotExperience(slot);
        // Mending is represented separately in the component but still has the
        // same vanilla grindstone value as a normal level-I enchantment.
        if (data.mending) xp += 1;
        return xp;
    }

    private static int slotExperience(EquipmentComponent.EquipmentSlot slot) {
        if (slot.isEmpty()) return 0;
        // The grindstone reward is the sum of the vanilla enchantment levels;
        // the registry is world-scoped in 1.21.11 and is unavailable from this
        // small result-slot mixin without introducing a client/server lookup.
        return slot.enchantmentLevel;
    }
}
