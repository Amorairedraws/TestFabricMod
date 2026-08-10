package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.util.PlayerBlockTracker;
import net.minecraft.block.SaplingBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into BlockItem.place() to track player-placed blocks.
 * <p>
 * Previously injected into {@code useOnBlock(ItemUsageContext)} where the
 * parameter is always the base {@code ItemUsageContext}, never an
 * {@code ItemPlacementContext}, so the instanceof check always failed and
 * no blocks were ever tracked. Injecting into {@code place} guarantees the
 * context is an {@code ItemPlacementContext} and carries the exact placed
 * position via {@link ItemPlacementContext#getBlockPos()}.
 */
@Mixin(BlockItem.class)
public class BlockItemPlaceMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void equipLeveling$recordPlacement(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (cir.getReturnValue() != ActionResult.SUCCESS) return;
        if (context.getWorld().isClient()) return;
        PlayerEntity player = context.getPlayer();
        if (player == null || player.isSpectator()) return;
        var state = context.getWorld().getBlockState(context.getBlockPos());
        if (state.isAir()) return;
        if (state.getBlock() instanceof SaplingBlock) return;
        PlayerBlockTracker.onBlockPlaced(context.getWorld(), context.getBlockPos(), player.getUuid(), state);
    }
}
