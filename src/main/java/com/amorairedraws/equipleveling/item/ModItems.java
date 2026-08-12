package com.amorairedraws.equipleveling.item;

import com.amorairedraws.equipleveling.EquipLevelingMod;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Items registered by Equip Leveling. */
public final class ModItems {
    /**
     * Repair Kit \u2014 combine with damaged equipment in a crafting grid to
     * restore a flat amount of durability without touching the item's level,
     * XP, enchantment slots or any other data.
     */
    public static final Item REPAIR_KIT = register("repair_kit",
            new Item(new Item.Settings().maxCount(16)));

    /**
     * Diamond Repair Kit \u2014 premium variant that restores a percentage of
     * max durability (50% by default) while preserving all equipment data.
     */
    public static final Item DIAMOND_REPAIR_KIT = register("diamond_repair_kit",
            new Item(new Item.Settings().maxCount(16)));

    private ModItems() {}

    /** Triggers static registration (call once from the mod initializer). */
    public static void init() {}

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(EquipLevelingMod.MOD_ID, name), item);
    }
}
