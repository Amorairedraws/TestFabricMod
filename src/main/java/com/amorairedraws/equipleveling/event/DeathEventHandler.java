package com.amorairedraws.equipleveling.event;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import com.amorairedraws.equipleveling.util.EquipmentCategory;

/** Death handling is kept separate so it can be called from a server death callback. */
public final class DeathEventHandler {
    private DeathEventHandler() { }

    public static void handlePlayerDeath(PlayerEntity player) {
        if (!EquipLevelingConfig.isKeepEquipOnDeath()) return;
        // Fabric's ServerPlayerEvents.ALLOW_DEATH/ServerLivingEntityEvents allow the
        // inventory to be marked before vanilla drops are generated. The component is
        // deliberately preserved; no NBT writes are needed with 1.21 data components.
        for (ItemStack stack : player.getInventory().main) mark(stack);
        for (ItemStack stack : player.getInventory().armor) mark(stack);
        mark(player.getInventory().offHand.get(0));
    }

    private static void mark(ItemStack stack) {
        if (EquipmentCategory.isEquipment(stack) && stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
            // Touching the component ensures a defensive copy is retained by inventory code.
            stack.set(EquipmentComponent.EQUIPMENT_TYPE, stack.get(EquipmentComponent.EQUIPMENT_TYPE).copy());
        }
    }
}
