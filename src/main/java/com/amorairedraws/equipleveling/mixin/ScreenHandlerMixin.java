package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.GrindstoneScreenHandler;
import net.minecraft.util.Identifier;
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
        RegistryWrapper.WrapperLookup lookup = player.getEntityWorld().getRegistryManager();
        int totalEnchantmentPower = getExperience(handler.getSlot(0).getStack(), lookup)
                + getExperience(handler.getSlot(1).getStack(), lookup);
        if (totalEnchantmentPower > 0) {
            // Match GrindstoneScreenHandler's vanilla payout: ceil(total / 2)
            // plus a random value in [0, ceil(total / 2)).
            int base = (totalEnchantmentPower + 1) / 2;
            int payout = base + player.getEntityWorld().getRandom().nextInt(base);
            player.addExperience(payout);
        }
    }

    private static int getExperience(ItemStack stack, RegistryWrapper.WrapperLookup lookup) {
        if (!stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) return 0;
        // The common tick synchronizer mirrors custom slots into vanilla's
        // enchantment component. In that case vanilla's result-slot hook already
        // awards the normal grindstone XP; adding it here would double the payout.
        // This fallback is only for a freshly-created component that has not yet
        // been mirrored (for example, an item inserted immediately after login).
        if (!stack.getEnchantments().getEnchantmentEntries().isEmpty()) return 0;
        EquipmentComponent.EquipmentData data = stack.get(EquipmentComponent.EQUIPMENT_TYPE);
        int xp = 0;
        for (EquipmentComponent.EquipmentSlot slot : data.slots) xp += slotExperience(slot, lookup);
        for (EquipmentComponent.EquipmentSlot slot : data.bonusSlots) xp += slotExperience(slot, lookup);
        if (data.mending) xp += slotExperience(
                new EquipmentComponent.EquipmentSlot("minecraft:mending", 1), lookup);
        return xp;
    }

    private static int slotExperience(EquipmentComponent.EquipmentSlot slot,
            RegistryWrapper.WrapperLookup lookup) {
        if (slot.isEmpty()) return 0;
        try {
            var key = RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of(slot.enchantmentId));
            var entry = lookup.getOrThrow(RegistryKeys.ENCHANTMENT).getOptional(key).orElse(null);
            if (entry == null || entry.isIn(EnchantmentTags.CURSE)) return 0;
            return Math.max(0, entry.value().getMinPower(slot.enchantmentLevel));
        } catch (RuntimeException ignored) {
            // Keep unknown datapack data grindable without crashing the screen.
            return slot.enchantmentLevel;
        }
    }
}
