package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.event.XpDisplay;
import com.amorairedraws.equipleveling.util.EquipmentCategory;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Awards tilling XP only after HoeItem successfully turns a block into
 * farmland. The check runs at RETURN, so the block has already been tilled;
 * we verify the resulting block is farmland rather than reading the old state
 * (which would always fail because it's no longer dirt by then).
 */
@Mixin(HoeItem.class)
public abstract class HoeItemMixin {
    @Inject(method = "useOnBlock", at = @At("RETURN"))
    private void equipLeveling$awardTillingXp(ItemUsageContext context,
            CallbackInfoReturnable<ActionResult> callback) {
        if (!callback.getReturnValue().isAccepted()) return;
        PlayerEntity player = context.getPlayer();
        if (player == null || player.getEntityWorld().isClient()) return;

        ItemStack hoe = context.getStack();
        if (!"hoe".equals(EquipmentCategory.getCategory(hoe))) return;
        // After a successful till the block at the position is farmland.
        BlockState after = player.getEntityWorld().getBlockState(context.getBlockPos());
        boolean tilled = after.isOf(Blocks.FARMLAND);
        if (!tilled) return;

        int xp = 3;
        if (EquipmentComponent.addXp(hoe, xp, player)) {
            XpDisplay.showForPlayer(player, Vec3d.ofCenter(context.getBlockPos()), xp);
        }
    }
}
