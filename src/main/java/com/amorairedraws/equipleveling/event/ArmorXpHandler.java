package com.amorairedraws.equipleveling.event;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.util.EquipmentCategory;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/** Awards XP after damage has actually been applied to a player. */
public final class ArmorXpHandler {
    private ArmorXpHandler() {}

    /**
     * Fabric calls this after the damage pipeline, so invulnerability frames and
     * cancelled damage do not award progression.  Every worn piece receives the
     * same reward, as specified by the equipment-leveling rules.
     */
    public static void afterDamage(LivingEntity entity, DamageSource source, float attempted,
            float actual, boolean blocked) {
        if (!(entity instanceof PlayerEntity player) || player.getEntityWorld().isClient()
                || blocked || actual <= 0.0f) return;

        int xp = Math.max(1, (int) Math.ceil(actual * 5.0f));
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armor = player.getEquippedStack(slot);
            if (!armor.isEmpty() && EquipmentCategory.isEquipment(armor)) {
                EquipmentComponent.addXp(armor, xp);
            }
        }
    }
}
