package com.amorairedraws.equipleveling.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;

import java.util.List;
import java.util.Random;

/**
 * Material ladder implementation for vanilla and modded items.
 *
 * <p>The material ladder is now mining-level-based: each mining level (0=wood/gold,
 * 1=stone, 2=iron, 3=diamond, 4=netherite, ...) contains a list of materials.
 * When a legendary upgrade fires, the item upgrades to a <b>random</b> material
 * from the <b>next</b> mining level, giving all materials at that level an even
 * chance. This works well with modded materials (bronze, copper, steel, etc.)
 * that may coexist at the same mining level.
 */
public final class MaterialTierUpgrader {
    private static final Random RNG = new Random();

    private MaterialTierUpgrader() {}

    /** Returns whether this item can be promoted to a higher material tier. */
    public static boolean canPromote(ItemStack old, String category) {
        if (old.isEmpty() || category == null) return false;
        String material = materialOf(old.getItem());
        List<String> nextMaterials = EquipLevelingConfig.getNextLevelMaterials(material);
        if (nextMaterials.isEmpty()) return false;
        // Check at least one next-level material has a corresponding item.
        for (String nextMat : nextMaterials) {
            if (itemFor(category, nextMat, old.getItem()) != null) return true;
        }
        return false;
    }

    /**
     * Returns true when no higher mining level exists for this material.
     * Equipment without a material ladder (e.g., fishing rods) has no
     * legendary promotion to wait for and can still reach MAX level.
     */
    public static boolean isAtMaxTier(ItemStack stack) {
        if (stack.isEmpty()) return true;
        String material = materialOf(stack.getItem());
        List<String> nextMaterials = EquipLevelingConfig.getNextLevelMaterials(material);
        return nextMaterials.isEmpty();
    }

    /** @deprecated Kept for backward compat with old callers passing a ladder array. */
    @Deprecated
    public static boolean isAtMaxTier(ItemStack stack, String[] ignoredLadder) {
        return isAtMaxTier(stack);
    }

    /** @deprecated Kept for backward compat. */
    @Deprecated
    public static boolean canPromote(ItemStack old, String category, String[] ignoredLadder) {
        return canPromote(old, category);
    }

    /** @deprecated Kept for backward compat. */
    @Deprecated
    public static boolean isTierLevelSatisfied(ItemStack stack, int level, String[] ignoredLadder) {
        return isAtMaxTier(stack);
    }

    /**
     * Promotes the equipment to a random material from the next mining level.
     * Returns the old stack unchanged if no promotion is possible.
     */
    public static ItemStack promote(ItemStack old, String category) {
        if (!canPromote(old, category)) return old;

        String material = materialOf(old.getItem());
        List<String> nextMaterials = EquipLevelingConfig.getNextLevelMaterials(material);
        if (nextMaterials.isEmpty()) return old;

        // Build a list of actually attainable items.
        List<Item> attainable = nextMaterials.stream()
                .map(m -> itemFor(category, m, old.getItem()))
                .filter(it -> it != null)
                .distinct()
                .toList();

        if (attainable.isEmpty()) return old;

        // Random selection with even chance.
        Item next = attainable.get(RNG.nextInt(attainable.size()));

        // Build the new item from pristine defaults, copying only progression data.
        ItemStack result = new ItemStack(next, old.getCount());
        if (old.contains(com.amorairedraws.equipleveling.component.EquipmentComponent.EQUIPMENT_TYPE)) {
            result.set(com.amorairedraws.equipleveling.component.EquipmentComponent.EQUIPMENT_TYPE,
                    old.get(com.amorairedraws.equipleveling.component.EquipmentComponent.EQUIPMENT_TYPE));
        }
        if (old.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME)) {
            result.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                    old.get(net.minecraft.component.DataComponentTypes.CUSTOM_NAME));
        }
        if (old.contains(net.minecraft.component.DataComponentTypes.TRIM)) {
            result.set(net.minecraft.component.DataComponentTypes.TRIM,
                    old.get(net.minecraft.component.DataComponentTypes.TRIM));
        }
        if (result.isDamageable()) result.setDamage(0);
        return result;
    }

    /** @deprecated Kept for backward compat with old callers. */
    @Deprecated
    public static ItemStack promote(ItemStack old, String category, String[] ignoredLadder) {
        return promote(old, category);
    }

    // ---- private helpers ----

    /** Extracts material name from an item: "wooden_sword" 2192 "wood".  Public for UI. */
    public static String materialNameOf(Item item) {
        return materialOf(item);
    }

    private static String materialOf(Item item) {
        return MaterialHelper.extractMaterialName(item);
    }

    /** Finds the item for a given material + category combination. */
    private static Item itemFor(String category, String tier, Item oldItem) {
        String t = tier == null ? "" : tier.toLowerCase();

        // Preserve the old item's subtype: a greatsword stays a greatsword.
        // Resolve <material>_<suffix> in the old item's own namespace first.
        Identifier oldId = Registries.ITEM.getId(oldItem);
        String path = oldId.getPath();
        int separator = path.indexOf('_');
        String suffix = separator >= 0 ? path.substring(separator + 1) : category;

        Identifier candidate = Identifier.of(oldId.getNamespace(), t + "_" + suffix);
        if (Registries.ITEM.containsId(candidate)) return Registries.ITEM.get(candidate);

        // Also try in the minecraft namespace (some mods register items there).
        candidate = Identifier.ofVanilla(t + "_" + suffix);
        if (Registries.ITEM.containsId(candidate)) return Registries.ITEM.get(candidate);

        // Vanilla hard-coded fallback (handles wood->wooden, gold->golden, armour).
        return vanillaItem(category, t);
    }

    private static Item vanillaItem(String category, String tier) {
        return switch (category) {
            case "sword" -> switch (tier) {
                case "wood" -> Items.WOODEN_SWORD; case "stone" -> Items.STONE_SWORD;
                case "iron" -> Items.IRON_SWORD; case "diamond" -> Items.DIAMOND_SWORD;
                case "netherite" -> Items.NETHERITE_SWORD; default -> null;
            };
            case "axe" -> switch (tier) {
                case "wood" -> Items.WOODEN_AXE; case "stone" -> Items.STONE_AXE;
                case "iron" -> Items.IRON_AXE; case "diamond" -> Items.DIAMOND_AXE;
                case "netherite" -> Items.NETHERITE_AXE; default -> null;
            };
            case "pickaxe" -> switch (tier) {
                case "wood" -> Items.WOODEN_PICKAXE; case "stone" -> Items.STONE_PICKAXE;
                case "iron" -> Items.IRON_PICKAXE; case "diamond" -> Items.DIAMOND_PICKAXE;
                case "netherite" -> Items.NETHERITE_PICKAXE; default -> null;
            };
            case "shovel" -> switch (tier) {
                case "wood" -> Items.WOODEN_SHOVEL; case "stone" -> Items.STONE_SHOVEL;
                case "iron" -> Items.IRON_SHOVEL; case "diamond" -> Items.DIAMOND_SHOVEL;
                case "netherite" -> Items.NETHERITE_SHOVEL; default -> null;
            };
            case "hoe" -> switch (tier) {
                case "wood" -> Items.WOODEN_HOE; case "stone" -> Items.STONE_HOE;
                case "iron" -> Items.IRON_HOE; case "diamond" -> Items.DIAMOND_HOE;
                case "netherite" -> Items.NETHERITE_HOE; default -> null;
            };
            case "helmet" -> switch (tier) {
                case "iron" -> Items.IRON_HELMET; case "diamond" -> Items.DIAMOND_HELMET;
                case "netherite" -> Items.NETHERITE_HELMET; default -> null;
            };
            case "chestplate" -> switch (tier) {
                case "iron" -> Items.IRON_CHESTPLATE; case "diamond" -> Items.DIAMOND_CHESTPLATE;
                case "netherite" -> Items.NETHERITE_CHESTPLATE; default -> null;
            };
            case "leggings" -> switch (tier) {
                case "iron" -> Items.IRON_LEGGINGS; case "diamond" -> Items.DIAMOND_LEGGINGS;
                case "netherite" -> Items.NETHERITE_LEGGINGS; default -> null;
            };
            case "boots" -> switch (tier) {
                case "iron" -> Items.IRON_BOOTS; case "diamond" -> Items.DIAMOND_BOOTS;
                case "netherite" -> Items.NETHERITE_BOOTS; default -> null;
            };
            default -> null;
        };
    }
}
