package com.amorairedraws.equipleveling.recipe;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import com.amorairedraws.equipleveling.item.ModItems;
import com.amorairedraws.equipleveling.util.EquipmentCategory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

/**
 * A crafting-grid recipe that repairs tracked equipment with a Repair Kit
 * (regular or diamond).
 *
 * <p>Vanilla only lets ingot/gem items be repaired at an anvil, so equipment
 * without a repair material (fishing rods, bows, crossbows, elytra, shields,
 * shears, ...) has no way to regain durability without losing progression.
 * Combining two of the same item also merges/strips data. This recipe instead
 * copies the input stack wholesale \u2014 preserving the custom {@code equipment}
 * component, level, XP, slots, enchantments and name \u2014 and only lowers the
 * damage value. The regular kit restores a flat amount; the diamond kit
 * restores a percentage of max durability.
 */
public class RepairEquipmentRecipe extends SpecialCraftingRecipe {
    public static final RecipeSerializer<RepairEquipmentRecipe> SERIALIZER =
            RecipeSerializer.register("equip_leveling:repair_equipment",
                    new SpecialRecipeSerializer<>(RepairEquipmentRecipe::new));

    public RepairEquipmentRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    /** Triggers static registration (call once from the mod initializer). */
    public static void init() {}

    private static boolean isKit(ItemStack stack) {
        return stack.isOf(ModItems.REPAIR_KIT) || stack.isOf(ModItems.DIAMOND_REPAIR_KIT);
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        ItemStack equipment = ItemStack.EMPTY;
        boolean hasKit = false;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.getCount() != 1) return false;

            if (isKit(stack)) {
                if (hasKit) return false; // only one kit per repair
                hasKit = true;
            } else if (EquipmentComponent.isTracked(stack)
                    && stack.isDamageable() && stack.getDamage() > 0) {
                if (!equipment.isEmpty()) return false; // only one item per repair
                equipment = stack;
            } else {
                return false; // anything else (untracked / undamaged) fails the recipe
            }
        }
        return hasKit && !equipment.isEmpty();
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
        ItemStack equipment = ItemStack.EMPTY;
        boolean diamond = false;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.isOf(ModItems.DIAMOND_REPAIR_KIT)) {
                diamond = true;
            } else if (!isKit(stack)) {
                equipment = stack;
            }
        }

        if (equipment.isEmpty() || !equipment.isDamageable()) return ItemStack.EMPTY;

        ItemStack result = equipment.copy();

        int restore;
        if (diamond) {
            // Percentage of max durability (default 50%).
            restore = (int) Math.round(result.getMaxDamage()
                    * (EquipLevelingConfig.getDiamondRepairKitRestorePercent() / 100.0));
        } else {
            // Flat durability amount (default 100).
            restore = EquipLevelingConfig.getRepairKitRestoreAmount();
        }
        result.setDamage(Math.max(0, result.getDamage() - restore));

        // If the item was [BROKEN], clear the flag and rebuild the mirrored
        // vanilla enchantment component that the broken mechanic had removed.
        EquipmentComponent.EquipmentData data = result.get(EquipmentComponent.EQUIPMENT_TYPE);
        if (data != null && data.broken) {
            data.broken = false;
            data.refresh(EquipmentCategory.getCategory(result));
            result.set(EquipmentComponent.EQUIPMENT_TYPE, data);
            EquipmentComponent.restoreEnchantments(result, lookup);
        }

        return result;
    }

    @Override
    public RecipeSerializer<RepairEquipmentRecipe> getSerializer() {
        return SERIALIZER;
    }
}
