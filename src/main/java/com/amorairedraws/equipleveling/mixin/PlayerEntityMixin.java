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
 * restores it immediately.  This is deliberately done at the drop boundary;
 * AFTER_DEATH is too late because vanilla has already spawned the item entities.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Unique
    private final List<ItemStack> equipLeveling$keptStacks = new ArrayList<>();

    @Inject(method = "dropInventory", at = @At("HEAD"))
    private void equipLeveling$hideKeptEquipment(ServerWorld world, CallbackInfo ci) {
        equipLeveling$keptStacks.clear();
        if (!EquipLevelingConfig.isKeepEquipOnDeath()) return;

        PlayerEntity player = (PlayerEntity) (Object) this;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (EquipmentComponent.isTracked(stack) && stack.contains(EquipmentComponent.EQUIPMENT_TYPE)) {
                equipLeveling$keptStacks.add(stack.copy());
                player.getInventory().setStack(i, ItemStack.EMPTY);
            }
        }
    }

    @Inject(method = "dropInventory", at = @At("RETURN"))
    private void equipLeveling$restoreKeptEquipment(ServerWorld world, CallbackInfo ci) {
        if (equipLeveling$keptStacks.isEmpty()) return;
        PlayerEntity player = (PlayerEntity) (Object) this;
        int saved = 0;
        for (int i = 0; i < player.getInventory().size() && saved < equipLeveling$keptStacks.size(); i++) {
            if (player.getInventory().getStack(i).isEmpty()) {
                player.getInventory().setStack(i, equipLeveling$keptStacks.get(saved++));
            }
        }
        equipLeveling$keptStacks.clear();
    }
}
