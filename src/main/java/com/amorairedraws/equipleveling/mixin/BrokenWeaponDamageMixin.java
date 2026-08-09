package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A broken weapon attacks exactly like an empty hand, even before attributes refresh. */
@Mixin(PlayerEntity.class)
public abstract class BrokenWeaponDamageMixin {
    @Redirect(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerEntity;getAttributeValue(Lnet/minecraft/registry/entry/RegistryEntry;)D"))
    private double equipLeveling$useFistDamage(PlayerEntity player, RegistryEntry<?> attribute) {
        ItemStack weapon = player.getMainHandStack();
        EquipmentComponent.EquipmentData data = weapon.get(EquipmentComponent.EQUIPMENT_TYPE);
        if (attribute == EntityAttributes.ATTACK_DAMAGE && data != null && data.broken) return 1.0D;
        return player.getAttributeValue((RegistryEntry) attribute);
    }

    @Redirect(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerEntity;getWeaponStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack equipLeveling$useEmptyWeapon(PlayerEntity player) {
        EquipmentComponent.EquipmentData data = player.getMainHandStack().get(EquipmentComponent.EQUIPMENT_TYPE);
        return data != null && data.broken ? ItemStack.EMPTY : player.getWeaponStack();
    }
}
