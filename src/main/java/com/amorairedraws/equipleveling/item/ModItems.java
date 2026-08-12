package com.amorairedraws.equipleveling.item;

import com.amorairedraws.equipleveling.EquipLevelingMod;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/** Items registered by Equip Leveling. */
public final class ModItems {
    /**
     * Repair Kit \u2014 combine with damaged equipment in a crafting grid to
     * restore a flat amount of durability without touching the item's level,
     * XP, enchantment slots or any other data.
     */
    public static final Item REPAIR_KIT = register("repair_kit",
            new Item.Settings().maxCount(16));

    /**
     * Diamond Repair Kit \u2014 premium variant that restores a percentage of
     * max durability (50% by default) while preserving all equipment data.
     */
    public static final Item DIAMOND_REPAIR_KIT = register("diamond_repair_kit",
            new Item.Settings().maxCount(16));

    private ModItems() {}

    /** Triggers static registration (call once from the mod initializer). */
    public static void init() {}

    /**
     * Registers an item under the {@code equip_leveling} namespace.
     *
     * <p>In 1.21.11 an {@link Item.Settings} must carry its {@link RegistryKey}
     * before the item is constructed; otherwise {@code Item}'s constructor throws
     * {@code "Item id not set"}. {@code Items.register(key, factory, settings)}
     * assigns the key and registers the item in one step.
     */
    private static Item register(String name, Item.Settings settings) {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM,
                Identifier.of(EquipLevelingMod.MOD_ID, name));
        return Items.register(key, Item::new, settings);
    }
}
