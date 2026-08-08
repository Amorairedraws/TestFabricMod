package com.amorairedraws.equipleveling.loot;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.registry.entry.RegistryEntry;
import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.util.EquipmentCategory;
import java.util.ArrayList;
import java.util.List;

/** Shared loot post-processing. Call processLootItem from a loot-table consumer. */
public final class EquipmentLootModifier {
    private EquipmentLootModifier() { }

    public static ItemStack processLootItem(ItemStack stack) {
        // Do not rely only on the vanilla item ID: modded enchanted books and
        // renamed/custom book items are represented by the stored-enchantments
        // component as well. This keeps the global book ban data-driven.
        if (stack.isEmpty() || stack.getItem() == Items.ENCHANTED_BOOK
                || stack.contains(DataComponentTypes.STORED_ENCHANTMENTS)) return ItemStack.EMPTY;
        if (!EquipmentCategory.isEquipment(stack)) return stack;
        var enchantments = stack.getEnchantments().getEnchantmentEntries();
        if (enchantments.isEmpty()) return stack;

        EquipmentComponent.EquipmentData data = EquipmentComponent.getOrCreate(stack);
        List<EquipmentComponent.EquipmentSlot> bonus = new ArrayList<>();
        int i = 0;
        for (var entry : enchantments) {
            String id = entry.getKey().getKey().map(Object::toString).orElse(null);
            // Mending is never loot-derived.  It is the dedicated completion
            // reward represented by EquipmentData.mending, and must not consume
            // one of the two real bonus slots either.
            if ("minecraft:mending".equals(id)) continue;
            if (id == null) continue;
            if (i++ >= 2) break;
            bonus.add(new EquipmentComponent.EquipmentSlot(id, entry.getIntValue()));
        }
        data.bonusSlots = bonus;
        data.refresh();
        stack.set(EquipmentComponent.EQUIPMENT_TYPE, data);
        // 1.21 stores enchantments in a component; remove them through the typed API.
        stack.remove(DataComponentTypes.ENCHANTMENTS);
        return stack;
    }
}
