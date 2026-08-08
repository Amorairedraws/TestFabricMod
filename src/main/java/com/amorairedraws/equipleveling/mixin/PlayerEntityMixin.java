package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes only leveled equipment from the list being dropped on death, then
 * restores it immediately. This is deliberately done at the drop boundary;
 * AFTER_DEATH is too late because vanilla has already spawned the item entities.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Unique
    private final List<Integer> equipLeveling$keptSlots = new ArrayList<>();
    @Unique
    private final List<ItemStack> equipLeveling$keptStacks = new ArrayList<>();

    @Inject(method = "dropInventory", at = @At("HEAD"))
    private void equipLeveling$hideKeptEquipment(ServerWorld world, CallbackInfo ci) {
        equipLeveling$keptSlots.clear();
        equipLeveling$keptStacks.clear();
        if (!EquipLevelingConfig.isKeepEquipOnDeath()) return;

        PlayerEntity player = (PlayerEntity) (Object) this;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (EquipmentComponent.isTracked(stack) && stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
                equipLeveling$keptSlots.add(i);
                equipLeveling$keptStacks.add(stack.copy());
                player.getInventory().setStack(i, ItemStack.EMPTY);
            }
        }
    }

    @Inject(method = "dropInventory", at = @At("RETURN"))
    private void equipLeveling$restoreKeptEquipment(ServerWorld world, CallbackInfo ci) {
        if (equipLeveling$keptStacks.isEmpty()) return;
        PlayerEntity player = (PlayerEntity) (Object) this;
        for (int n = 0; n < equipLeveling$keptStacks.size(); n++) {
            int slot = equipLeveling$keptSlots.get(n);
            // The slot was emptied before vanilla ran. Restore it in place so
            // armor, off-hand and hotbar equipment remain equipped/in-position.
            if (player.getInventory().getStack(slot).isEmpty()) {
                player.getInventory().setStack(slot, equipLeveling$keptStacks.get(n));
            } else {
                // Be defensive about other mods mutating the inventory during
                // dropInventory; do not lose the kept stack in that case.
                player.giveItemStack(equipLeveling$keptStacks.get(n));
            }
        }
        equipLeveling$keptSlots.clear();
        equipLeveling$keptStacks.clear();
    }
}
