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
     * cancelled damage do not award progression.
     *
     * <p>Armor pieces (head/chest/legs/feet) receive XP when the player takes
     * unblocked damage. The offhand item (e.g. shield) receives XP when damage
     * is successfully blocked — the shield did its job.
     */
    public static void afterDamage(LivingEntity entity, DamageSource source, float attempted,
            float actual, boolean blocked) {
        if (!(entity instanceof PlayerEntity player) || actual <= 0.0f) return;

        // The server owns progression and sends the floating label only after
        // each eligible stack accepts the reward.
        if (player.getEntityWorld().isClient()) return;

        boolean awarded = false;

        if (blocked) {
            // Shield (or other offhand equipment) blocked the hit — award XP to it.
            ItemStack offhand = player.getOffHandStack();
            if (!offhand.isEmpty() && EquipmentCategory.isEquipment(offhand)) {
                int xp = Math.max(1, (int) Math.ceil(actual * 2.0f));
                awarded = EquipmentComponent.addXp(offhand, xp, player);
                if (awarded) XpDisplay.showForPlayer(player, player.getEntityPos(), xp);
            }
        } else {
            // Armor took the hit — award XP to worn equipment.
            int xp = Math.max(1, (int) Math.ceil(actual * 2.0f));
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack armor = player.getEquippedStack(slot);
                if (!armor.isEmpty() && EquipmentCategory.isEquipment(armor)) {
                    awarded |= EquipmentComponent.addXp(armor, xp, player);
                }
            }
            if (awarded) XpDisplay.showForPlayer(player, player.getEntityPos(), xp);
        }
    }
}