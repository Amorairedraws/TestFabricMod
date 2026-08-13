package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.item.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Plays the smithing-table sound when a repair kit is consumed to craft-repair
 * equipment. The {@code RepairEquipmentRecipe#craft} method has no player/world
 * access, so the sound is hooked here \u2014 the crafting result slot's input is the
 * only reliable, player-agnostic place to detect that a kit was part of the craft.
 */
@Mixin(CraftingResultSlot.class)
public abstract class CraftingResultSlotMixin {
    @Shadow @Final private RecipeInputInventory input;

    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void equipLeveling$playRepairSound(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (player.getEntityWorld().isClient()) return;
        boolean hasKit = false;
        for (ItemStack s : input.getHeldStacks()) {
            if (s.isOf(ModItems.REPAIR_KIT) || s.isOf(ModItems.DIAMOND_REPAIR_KIT)) {
                hasKit = true;
                break;
            }
        }
        if (hasKit) {
            player.getEntityWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_SMITHING_TABLE_USE, SoundCategory.MASTER, 1.0F, 1.0F);
        }
    }
}
