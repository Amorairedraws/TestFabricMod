package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A broken tool must never break blocks faster than a bare hand. Even though
 * ItemStack#getMiningSpeedMultiplier is already forced to 1.0 for broken tools,
 * the 1.21+ Efficiency enchantment applies its bonus through the player's
 * mining-efficiency attribute (and other mods may add further speed sources).
 * Forcing the held-item breaking speed to the hand baseline (1.0) here makes
 * the broken tool behave exactly like an empty hand no matter what bonuses are
 * attached.
 */
@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {

    @Shadow
    @Final
    private int selectedSlot;

    @Inject(method = "getBlockBreakingSpeed", at = @At("HEAD"), cancellable = true)
    private void suppressBrokenToolBreakingSpeed(BlockState block, CallbackInfoReturnable<Float> cir) {
        PlayerInventory inventory = (PlayerInventory) (Object) this;
        ItemStack held = inventory.getStack(this.selectedSlot);
        if (held.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
            EquipmentComponent.EquipmentData data = held.get(EquipmentComponent.EQUIPMENT_TYPE);
            if (data != null && data.broken) {
                cir.setReturnValue(1.0f);
            }
        }
    }
}
