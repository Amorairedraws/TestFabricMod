package com.amorairedraws.equipleveling.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

/**
 * Maps an item to one of the mod's equipment categories (sword, axe, pickaxe,
 * ... or the generic "default" bucket).
 *
 * <p>The lookup is deliberately layered so that modded weapons, tools and
 * armour work out of the box without needing Java code or datapack edits:
 *
 * <ol>
 *   <li>Vanilla item tags (fast path for vanilla + well-behaved mods).</li>
 *   <li>Mod-owned {@code equip_leveling:*} tags, which datapacks can extend.</li>
 *   <li>Fabric convention tags ({@code c:tools/melee_weapons},
 *       {@code c:tools/mining_tools}, {@code c:tools/ranged_weapons},
 *       {@code c:tools/spears}, {@code c:armors}, {@code c:tools}) - these are
 *       the ecosystem-standard tags that most mods add their items to, so a
 *       modded greatsword, battleaxe, hammer or spear is picked up here.</li>
 *   <li>Attribute-based fallback: any item that grants attack damage is a
 *       melee weapon, any item that grants armour is armour.  This catches
 *       items whose mods don't use tags at all.</li>
 *   <li>The mod's own {@code upgradeable_equipment} tag as a final opt-in.</li>
 * </ol>
 */
public class EquipmentCategory {
    private static TagKey<net.minecraft.item.Item> tag(String path) {
        return TagKey.of(Registries.ITEM.getKey(), Identifier.of("equip_leveling", path));
    }

    private static TagKey<net.minecraft.item.Item> cTag(String path) {
        return TagKey.of(Registries.ITEM.getKey(), Identifier.of("c", path));
    }

    public static String getCategory(ItemStack stack) {
        if (stack.isEmpty()) return null;

        // ---- 1. Armour (vanilla tags) ----
        if (stack.isIn(ItemTags.HEAD_ARMOR)) return "helmet";
        if (stack.isIn(ItemTags.CHEST_ARMOR)) return "chestplate";
        if (stack.isIn(ItemTags.LEG_ARMOR)) return "leggings";
        if (stack.isIn(ItemTags.FOOT_ARMOR)) return "boots";

        // ---- 2. Mod-owned category tags (extensible by datapacks) ----
        if (stack.isIn(tag("swords")) || stack.isIn(ItemTags.SWORDS)) return "sword";
        if (stack.isIn(tag("axes")) || stack.isIn(ItemTags.AXES)) return "axe";
        if (stack.isIn(tag("pickaxes")) || stack.isIn(ItemTags.PICKAXES)) return "pickaxe";
        if (stack.isIn(tag("shovels")) || stack.isIn(ItemTags.SHOVELS)) return "shovel";
        if (stack.isIn(tag("hoes")) || stack.isIn(ItemTags.HOES)) return "hoe";
        if (stack.isIn(tag("fishing_rods")) || stack.getItem() == Items.FISHING_ROD) return "fishing_rod";
        if (stack.getItem() == Items.ELYTRA) return "elytra";
        if (stack.getItem() == Items.SHIELD) return "shield";
        if (stack.isIn(tag("bows")) || stack.isIn(ItemTags.BOW_ENCHANTABLE)
                || stack.getItem() == Items.BOW || stack.getItem() == Items.CROSSBOW) return "bow";

        // ---- 3. Fabric convention tags (modded equipment) ----
        // Melee weapons: swords, axes, maces, greatswords, battleaxes, hammers...
        if (stack.isIn(cTag("tools/melee_weapons")) || stack.isIn(cTag("tools/melee_weapon"))) return "sword";
        // Spears are melee weapons too.
        if (stack.isIn(cTag("tools/spears")) || stack.isIn(cTag("tools/spear"))) return "sword";
        // Mining tools: pickaxes, hammers, drills...
        if (stack.isIn(cTag("tools/mining_tools")) || stack.isIn(cTag("tools/mining_tool"))) return "pickaxe";
        // Ranged weapons: bows, crossbows, guns...
        if (stack.isIn(cTag("tools/ranged_weapons")) || stack.isIn(cTag("tools/ranged_weapon"))) return "bow";
        // Generic armour tag.
        if (stack.isIn(cTag("armors")) || stack.isIn(cTag("armor"))) return "helmet";
        // Any other tool (shears, shields, wrenches, brushes...) - still equipment.
        if (stack.isIn(cTag("tools"))) return "default";

        // ---- 4. Attribute-based fallback ----
        // Any item that grants attack damage is a melee weapon.
        if (hasAttribute(stack, EntityAttributes.ATTACK_DAMAGE)) return "sword";
        // Any item that grants armour is armour.
        if (hasAttribute(stack, EntityAttributes.ARMOR)) return "helmet";

        // ---- 5. Explicit opt-in tag ----
        if (stack.isIn(tag("upgradeable_equipment"))) return "default";

        return null;
    }

    public static boolean isEquipment(ItemStack stack) {
        return getCategory(stack) != null;
    }

    /** True if the stack carries a modifier for the given attribute. */
    private static boolean hasAttribute(ItemStack stack, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute) {
        AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) return false;
        for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
            if (entry.attribute() == attribute) return true;
        }
        return false;
    }
}
