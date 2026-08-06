package com.amorairedraws.equipleveling.event;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import com.amorairedraws.equipleveling.util.EquipmentCategory;

/** Preserves component data for leveled equipment when the optional death rule is enabled. */
public final class DeathEventHandler {
    private DeathEventHandler() { }

    public static void handlePlayerDeath(PlayerEntity player) {
        if (!EquipLevelingConfig.isKeepEquipOnDeath()) return;
        // PlayerInventory exposes a single indexed view in modern 1.21.11 mappings.
        // Copying the component makes the keep rule independent of later stack mutations.
        for (int i = 0; i < player.getInventory().size(); i++) {
            mark(player.getInventory().getStack(i));
        }
    }

    private static void mark(ItemStack stack) {
        if (EquipmentCategory.isEquipment(stack) && stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
            stack.set(EquipmentComponent.EQUIPMENT_TYPE,
                    stack.get(EquipmentComponent.EQUIPMENT_TYPE).copy());
        }
    }
}
