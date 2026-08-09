package com.amorairedraws.equipleveling.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-detects the material ladder ("wood" -> "stone" -> ... ) from the live
 * item registry instead of requiring the ladder to be typed in by hand.
 *
 * <p>Modded materials that follow the common {@code <material>_<tool>} naming
 * convention (e.g. {@code bronze_sword}) are picked up automatically.  Each
 * material's position is derived from its tools' durability (the strongest,
 * most meaningful "how far along the tech tree is this?" metric that exists
 * on the item data itself), with vanilla tiers naturally falling into their
 * canonical order: wood &lt; stone &lt; iron &lt; diamond &lt; netherite.
 *
 * <p>The result is cached for the lifetime of the client session; the item
 * registry does not change while the game is running.
 */
public final class MaterialLadderDetector {
    private static List<String> CACHE = null;

    private MaterialLadderDetector() {}

    /** The vanilla fallback, used when the registry is not yet populated. */
    public static List<String> vanillaLadder() {
        return List.of("wood", "stone", "iron", "diamond", "netherite");
    }

    /** Returns the auto-detected ladder ordered weakest -> strongest. */
    public static List<String> detectLadder() {
        if (CACHE != null) return CACHE;
        if (Registries.ITEM.stream().count() < 100) {
            CACHE = vanillaLadder();
            return CACHE;
        }

        Map<String, Integer> durabilityByMaterial = new HashMap<>();
        for (Item item : Registries.ITEM.stream().toList()) {
            ItemStack stack = item.getDefaultStack();
            if (stack.isEmpty() || !stack.isDamageable()) continue;
            // Only equipment participates in the ladder.
            if (EquipmentCategory.getCategory(stack) == null) continue;
            Identifier id = Registries.ITEM.getId(item);
            String path = id.getPath();
            int separator = path.indexOf('_');
            if (separator < 1) continue;
            String material = path.substring(0, separator);
            if ("wooden".equalsIgnoreCase(material)) material = "wood";
            int durability = stack.getMaxDamage();
            if (durability <= 0) continue;
            durabilityByMaterial.merge(material, durability, Math::max);
        }

        List<String> ladder = new ArrayList<>(durabilityByMaterial.keySet());
        // Weakest tools first => weakest material first in the ladder.
        ladder.sort(Comparator.comparingInt(m -> durabilityByMaterial.getOrDefault(m, Integer.MAX_VALUE)));

        // Guarantee the canonical vanilla five are always present.
        for (String vanilla : vanillaLadder()) {
            if (!ladder.contains(vanilla)) ladder.add(vanilla);
        }

        CACHE = ladder;
        return CACHE;
    }

    /** Clears the cache (useful after a registry reload in dev). */
    public static void invalidate() {
        CACHE = null;
    }
}