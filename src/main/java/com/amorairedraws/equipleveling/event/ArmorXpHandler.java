package com.amorairedraws.equipleveling.event;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.util.EquipmentCategory;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/** Awards the same incoming hit XP to every worn armor piece. */
public final class ArmorXpHandler {
    private ArmorXpHandler() {}
    public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof PlayerEntity player) || amount <= 0 || player.getEntityWorld().isClient()) return true;
        int xp = Math.max(1, (int)Math.ceil(amount * 5));
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armor = player.getEquippedStack(slot);
            if (!armor.isEmpty() && EquipmentCategory.isEquipment(armor)) {
                EquipmentComponent.addXp(armor, xp);
            }
        }
        return true;
    }
}
