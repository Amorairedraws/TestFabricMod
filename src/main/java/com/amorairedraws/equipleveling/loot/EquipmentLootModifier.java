package com.amorairedraws.equipleveling.loot;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.util.EquipmentCategory;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/** Converts generated equipment immediately, while it is still in loot. */
public final class EquipmentLootModifier {
    private EquipmentLootModifier() { }

    public static ItemStack processLootItem(ItemStack stack,
            net.minecraft.registry.DynamicRegistryManager registries, Random random) {
        if (stack.isEmpty() || stack.getItem() == Items.ENCHANTED_BOOK
                || stack.contains(DataComponentTypes.STORED_ENCHANTMENTS)) return ItemStack.EMPTY;
        if (!EquipmentCategory.isEquipment(stack)) return stack;

        // Every generated piece of equipment gets data before it reaches an item
        // entity/chest. Its level tooltip is therefore present before pickup.
        EquipmentComponent.EquipmentData data = EquipmentComponent.getOrCreate(stack);
        Registry<Enchantment> enchantments = registries.getOrThrow(RegistryKeys.ENCHANTMENT);
        var vanillaEnchantments = stack.getEnchantments().getEnchantmentEntries();
        if (!vanillaEnchantments.isEmpty() && data.bonusSlots.isEmpty()) {
            List<RegistryEntry<Enchantment>> candidates = new ArrayList<>();
            for (var entry : vanillaEnchantments) {
                if (entry.getKey().isIn(EnchantmentTags.CURSE)
                        || "minecraft:mending".equals(idOf(entry.getKey()))) continue;
                candidates.add(entry.getKey());
            }

            // Loot bonuses are deliberately modest: they start at I, never use
            // curses, and most enchanted finds (75%) gain a second compatible
            // bonus rather than almost always presenting one lonely slot.
            List<EquipmentComponent.EquipmentSlot> bonus = new ArrayList<>();
            int target = candidates.size() > 1 && random.nextInt(4) != 0 ? 2 : 1;
            while (!candidates.isEmpty() && bonus.size() < target) {
                RegistryEntry<Enchantment> chosen = candidates.remove(random.nextInt(candidates.size()));
                String id = idOf(chosen);
                if (id != null && compatibleWithBonus(chosen, bonus, enchantments)) {
                    bonus.add(new EquipmentComponent.EquipmentSlot(id, 1));
                }
            }

            // Vanilla loot frequently has exactly one enchantment. Fill the
            // second bonus from the table-compatible registry when possible so
            // 1–2 bonus slots is a genuine distribution rather than an accident
            // of individual loot tables.
            while (bonus.size() < target) {
                List<RegistryEntry.Reference<Enchantment>> fill = new ArrayList<>();
                for (Identifier id : enchantments.getIds()) {
                    RegistryEntry.Reference<Enchantment> entry = enchantments.getEntry(id).orElse(null);
                    if (entry != null && !entry.isIn(EnchantmentTags.CURSE)
                            && !"minecraft:mending".equals(id.toString())
                            && entry.value().isAcceptableItem(stack)
                            && compatibleWithBonus(entry, bonus, enchantments)) {
                        fill.add(entry);
                    }
                }
                if (fill.isEmpty()) break;
                String id = idOf(fill.get(random.nextInt(fill.size())));
                if (id == null) break;
                bonus.add(new EquipmentComponent.EquipmentSlot(id, 1));
            }
            data.bonusSlots = bonus;
        }

        // Curse and Mending are not retained from generated gear. Mending is
        // earned only by filling all standard slots; curses are banned entirely.
        stack.remove(DataComponentTypes.ENCHANTMENTS);
        data.refresh(EquipmentCategory.getCategory(stack));
        stack.set(EquipmentComponent.EQUIPMENT_TYPE, data);
        EquipmentComponent.restoreEnchantments(stack, registries);
        return stack;
    }

    private static boolean compatibleWithBonus(RegistryEntry<Enchantment> candidate,
            List<EquipmentComponent.EquipmentSlot> bonus, Registry<Enchantment> enchantments) {
        for (EquipmentComponent.EquipmentSlot existing : bonus) {
            try {
                RegistryEntry.Reference<Enchantment> entry = enchantments.getEntry(Identifier.of(existing.enchantmentId)).orElse(null);
                if (entry != null && !Enchantment.canBeCombined(candidate, entry)) return false;
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        return true;
    }

    private static String idOf(RegistryEntry<Enchantment> entry) {
        return entry.getKey().map(key -> key.getValue().toString()).orElse(null);
    }
}
