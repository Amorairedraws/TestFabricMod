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
        if (stack.isEmpty() || stack.getItem() == Items.ENCHANTED_BOOK) return ItemStack.EMPTY;
        if (!EquipmentCategory.isEquipment(stack)) return stack;
        var enchantments = stack.getEnchantments().getEnchantmentEntries();
        if (enchantments.isEmpty()) return stack;

        EquipmentComponent.EquipmentData data = EquipmentComponent.getOrCreate(stack);
        List<EquipmentComponent.EquipmentSlot> bonus = new ArrayList<>();
        int i = 0;
        for (var entry : enchantments) {
            if (i++ >= 2) break;
            String id = entry.getKey().getKey().map(Object::toString).orElse("minecraft:unknown");
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
