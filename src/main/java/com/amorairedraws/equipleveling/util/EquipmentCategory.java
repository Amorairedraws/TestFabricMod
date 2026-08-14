package com.amorairedraws.equipleveling.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.block.Block;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 *   <li><b>Class/component fallback</b> - 1.21.11 flattened swords, pickaxes and
 *       armour into plain {@link net.minecraft.item.Item} instances, so there are
 *       no {@code SwordItem}/{@code PickaxeItem}/{@code ArmorItem} subclasses to
 *       test. Instead we inspect the item's own data components: the
 *       {@link ToolComponent} mining rules (which reference {@code BlockTags}
 *       such as {@code PICKAXE_MINEABLE}), the {@link EquippableComponent} slot,
 *       and the item class itself for fishing rods / bows / crossbows. This is
 *       what makes a modded copper, ruby or emerald tool, or a modded fishing
 *       rod, register as equipment without any hardcoding.</li>
 *   <li>Attribute-based fallback: any item that grants attack damage is a
 *       melee weapon, any item that grants armour is armour.</li>
 *   <li>The mod's own {@code upgradeable_equipment} tag as a final opt-in.</li>
 * </ol>
 */
public class EquipmentCategory {
    /** Per-item category cache. The empty string is a sentinel for "not equipment"
     *  (ConcurrentHashMap rejects null values). Dropped on {@link #invalidateCache()}. */
    private static final Map<Item, String> CATEGORY_CACHE = new ConcurrentHashMap<>();

    private static TagKey<net.minecraft.item.Item> tag(String path) {
        return TagKey.of(Registries.ITEM.getKey(), Identifier.of("equip_leveling", path));
    }

    private static TagKey<net.minecraft.item.Item> cTag(String path) {
        return TagKey.of(Registries.ITEM.getKey(), Identifier.of("c", path));
    }

    public static String getCategory(ItemStack stack) {
        if (stack.isEmpty()) return null;
        // Category depends only on the item type (tags, class and the item's own
        // default components), so cache per item to avoid re-running the multi-layer
        // fallback on every tooltip frame and server tick.
        String cached = CATEGORY_CACHE.get(stack.getItem());
        if (cached != null) return cached.isEmpty() ? null : cached;
        String category = computeCategory(stack);
        CATEGORY_CACHE.put(stack.getItem(), category == null ? "" : category);
        return category;
    }

    /** Drops the cached categories. Call after the item registry or tags change. */
    public static void invalidateCache() {
        CATEGORY_CACHE.clear();
    }

    private static String computeCategory(ItemStack stack) {

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
        if (stack.isIn(cTag("tools/melee_weapons")) || stack.isIn(cTag("tools/melee_weapon"))) return "sword";
        if (stack.isIn(cTag("tools/spears")) || stack.isIn(cTag("tools/spear"))) return "sword";
        if (stack.isIn(cTag("tools/mining_tools")) || stack.isIn(cTag("tools/mining_tool"))) return "pickaxe";
        if (stack.isIn(cTag("tools/ranged_weapons")) || stack.isIn(cTag("tools/ranged_weapon"))) return "bow";
        if (stack.isIn(cTag("armors")) || stack.isIn(cTag("armor"))) return "helmet";
        if (stack.isIn(cTag("tools"))) return "default";

        // ---- 4. Class/component fallback (modded items that skip tags) ----
        if (stack.getItem() instanceof FishingRodItem) return "fishing_rod";
        if (stack.getItem() instanceof TridentItem) return "sword";
        if (stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem
                || stack.getItem() instanceof RangedWeaponItem) return "bow";

        // Armour via the equippable component slot (HEAD/CHEST/LEGS/FEET).
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable != null) {
            String armorCategory = switch (equippable.slot()) {
                case HEAD -> "helmet";
                case CHEST -> "chestplate";
                case LEGS -> "leggings";
                case FEET -> "boots";
                default -> null;
            };
            if (armorCategory != null) return armorCategory;
        }

        // Tool category via the ToolComponent mining rules.
        ToolComponent tool = stack.get(DataComponentTypes.TOOL);
        if (tool != null) {
            if (hasToolRule(tool, BlockTags.PICKAXE_MINEABLE)) return "pickaxe";
            if (hasToolRule(tool, BlockTags.AXE_MINEABLE)) return "axe";
            if (hasToolRule(tool, BlockTags.SHOVEL_MINEABLE)) return "shovel";
            if (hasToolRule(tool, BlockTags.HOE_MINEABLE)) return "hoe";
            if (hasToolRule(tool, BlockTags.SWORD_EFFICIENT)
                    || hasToolRule(tool, BlockTags.SWORD_INSTANTLY_MINES)) return "sword";
        }

        // ---- 5. Attribute-based fallback ----
        if (hasAttribute(stack, EntityAttributes.ATTACK_DAMAGE)) return "sword";
        if (hasAttribute(stack, EntityAttributes.ARMOR)) return "helmet";

        // ---- 6. Explicit opt-in tag ----
        if (stack.isIn(tag("upgradeable_equipment"))) return "default";

        return null;
    }

    public static boolean isEquipment(ItemStack stack) {
        return getCategory(stack) != null;
    }

    /** True if the stack carries a modifier for the given attribute. */
    private static boolean hasAttribute(ItemStack stack, RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute) {
        AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) return false;
        for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
            if (entry.attribute() == attribute) return true;
        }
        return false;
    }

    /** True if one of the tool's mining rules targets the given block tag. */
    private static boolean hasToolRule(ToolComponent tool, TagKey<Block> blockTag) {
        for (ToolComponent.Rule rule : tool.rules()) {
            Optional<TagKey<Block>> key = rule.blocks().getTagKey();
            if (key.isPresent() && key.get().equals(blockTag)) return true;
        }
        return false;
    }
}
