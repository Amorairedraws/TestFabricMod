package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.util.PlayerBlockTracker;
import net.minecraft.block.SaplingBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into BlockItem.useOnBlock() to track player-placed blocks.
 * Fires at RETURN so we only record SUCCESSful placements.
 */
@Mixin(BlockItem.class)
public class BlockItemPlaceMixin {
    @Inject(method = "useOnBlock", at = @At("RETURN"))
    private void equipLeveling$recordPlacement(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (cir.getReturnValue() != ActionResult.SUCCESS) return;
        if (context.getWorld().isClient()) return;
        PlayerEntity player = context.getPlayer();
        if (player == null || player.isSpectator()) return;
        // Only ItemPlacementContext carries placement-specific data.
        if (!(context instanceof ItemPlacementContext ipc)) return;
        var state = ipc.getWorld().getBlockState(ipc.getBlockPos());
        if (state.isAir()) return;
        if (state.getBlock() instanceof SaplingBlock) return;
        PlayerBlockTracker.onBlockPlaced(ipc.getWorld(), ipc.getBlockPos(), player.getUuid(), state);
    }
}
