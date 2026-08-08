package com.amorairedraws.equipleveling.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Material ladder implementation for vanilla and conventionally named modded items. */
public final class MaterialTierUpgrader {
    private MaterialTierUpgrader() {}

    /** Returns whether this category has a real next item in the configured ladder. */
    public static boolean canPromote(ItemStack old, String category, String[] ladder) {
        if (old.isEmpty() || category == null || ladder == null || ladder.length < 2) return false;
        int index = indexOf(ladder, material(old.getItem()));
        return index >= 0 && index + 1 < ladder.length
                && itemFor(category, ladder[index + 1], old.getItem()) != null;
    }

    /** A stack is at the configured ceiling only when its material is the final
     * configured tier. Unknown materials are deliberately not treated as maxed. */
    public static boolean isAtMaxTier(ItemStack stack, String[] ladder) {
        if (stack.isEmpty() || ladder == null || ladder.length == 0) return false;
        return ladder[ladder.length - 1] != null
                && ladder[ladder.length - 1].equalsIgnoreCase(material(stack.getItem()));
    }

    public static ItemStack promote(ItemStack old, String category, String[] ladder) {
        if (!canPromote(old, category, ladder)) return old;
        int index = indexOf(ladder, material(old.getItem()));
        Item next = itemFor(category, ladder[index + 1], old.getItem());
        if (next == null) return old;

        // applyComponentsFrom is important: custom progression, custom names,
        // trim data and every other component survive a legendary promotion.
        ItemStack result = new ItemStack(next, old.getCount());
        result.applyComponentsFrom(old.getComponents());
        // Keep player-facing/custom data (including the Equip Leveling
        // component, name and trim), but restore the promoted item's own combat,
        // tool, armor, durability and repair defaults. Copying those old default
        // components would make a diamond item retain iron stats.
        result.remove(net.minecraft.component.DataComponentTypes.MAX_DAMAGE);
        result.remove(net.minecraft.component.DataComponentTypes.DAMAGE);
        result.remove(net.minecraft.component.DataComponentTypes.ATTRIBUTE_MODIFIERS);
        result.remove(net.minecraft.component.DataComponentTypes.TOOL);
        result.remove(net.minecraft.component.DataComponentTypes.WEAPON);
        result.remove(net.minecraft.component.DataComponentTypes.EQUIPPABLE);
        result.remove(net.minecraft.component.DataComponentTypes.REPAIRABLE);
        if (result.isDamageable()) result.setDamage(0); // legendary upgrades fully restore durability
        return result;
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null && values[i].equalsIgnoreCase(value)) return i;
        }
        return -1;
    }

    /**
     * Vanilla uses "wooden_sword", while most mods use "bronze_sword".  The
     * first path component is therefore the material, with wooden normalized to
     * the configured "wood" tier.
     */
    private static String material(Item item) {
        String path = Registries.ITEM.getId(item).getPath();
        int separator = path.indexOf('_');
        if (separator < 1) return path;
        String prefix = path.substring(0, separator);
        return "wooden".equalsIgnoreCase(prefix) ? "wood" : prefix;
    }

    private static Item itemFor(String category, String tier, Item oldItem) {
        String t = tier == null ? "" : tier.toLowerCase();
        Item vanilla = switch (category) {
            case "sword" -> switch (t) {
                case "wood" -> Items.WOODEN_SWORD; case "stone" -> Items.STONE_SWORD;
                case "iron" -> Items.IRON_SWORD; case "diamond" -> Items.DIAMOND_SWORD;
                case "netherite" -> Items.NETHERITE_SWORD; default -> null;
            };
            case "axe" -> switch (t) {
                case "wood" -> Items.WOODEN_AXE; case "stone" -> Items.STONE_AXE;
                case "iron" -> Items.IRON_AXE; case "diamond" -> Items.DIAMOND_AXE;
                case "netherite" -> Items.NETHERITE_AXE; default -> null;
            };
            case "pickaxe" -> switch (t) {
                case "wood" -> Items.WOODEN_PICKAXE; case "stone" -> Items.STONE_PICKAXE;
                case "iron" -> Items.IRON_PICKAXE; case "diamond" -> Items.DIAMOND_PICKAXE;
                case "netherite" -> Items.NETHERITE_PICKAXE; default -> null;
            };
            case "shovel" -> switch (t) {
                case "wood" -> Items.WOODEN_SHOVEL; case "stone" -> Items.STONE_SHOVEL;
                case "iron" -> Items.IRON_SHOVEL; case "diamond" -> Items.DIAMOND_SHOVEL;
                case "netherite" -> Items.NETHERITE_SHOVEL; default -> null;
            };
            case "hoe" -> switch (t) {
                case "wood" -> Items.WOODEN_HOE; case "stone" -> Items.STONE_HOE;
                case "iron" -> Items.IRON_HOE; case "diamond" -> Items.DIAMOND_HOE;
                case "netherite" -> Items.NETHERITE_HOE; default -> null;
            };
            case "helmet" -> switch (t) {
                case "iron" -> Items.IRON_HELMET; case "diamond" -> Items.DIAMOND_HELMET;
                case "netherite" -> Items.NETHERITE_HELMET; default -> null;
            };
            case "chestplate" -> switch (t) {
                case "iron" -> Items.IRON_CHESTPLATE; case "diamond" -> Items.DIAMOND_CHESTPLATE;
                case "netherite" -> Items.NETHERITE_CHESTPLATE; default -> null;
            };
            case "leggings" -> switch (t) {
                case "iron" -> Items.IRON_LEGGINGS; case "diamond" -> Items.DIAMOND_LEGGINGS;
                case "netherite" -> Items.NETHERITE_LEGGINGS; default -> null;
            };
            case "boots" -> switch (t) {
                case "iron" -> Items.IRON_BOOTS; case "diamond" -> Items.DIAMOND_BOOTS;
                case "netherite" -> Items.NETHERITE_BOOTS; default -> null;
            };
            default -> null;
        };
        if (vanilla != null) return vanilla;

        // Modded tiers work when their items follow the common
        // <material>_<tool-or-armor> naming convention and share a namespace.
        Identifier oldId = Registries.ITEM.getId(oldItem);
        String path = oldId.getPath();
        int separator = path.indexOf('_');
        String suffix = separator >= 0 ? path.substring(separator + 1) : category;
        Identifier candidate = Identifier.of(oldId.getNamespace(), t + "_" + suffix);
        return Registries.ITEM.containsId(candidate) ? Registries.ITEM.get(candidate) : null;
    }
}
