package com.amorairedraws.equipleveling.mixin;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import com.amorairedraws.equipleveling.event.XpDisplay;
import com.amorairedraws.equipleveling.util.EquipmentCategory;
import com.amorairedraws.equipleveling.util.XpCalculator;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Adds right-click harvesting for hoes and keeps the existing tilling reward.
 *
 * <p>Right-clicking a <em>mature</em> crop (wheat/carrots/potatoes/beetroot and
 * nether wart) with a hoe instantly harvests it: the block's drops are spawned
 * (respecting Fortune on the hoe), the hoe gains farming XP, and the block is
 * reset to its first growth stage so the field stays planted. Tilling dirt into
 * farmland still awards the original small XP amount.
 */
@Mixin(HoeItem.class)
public abstract class HoeItemMixin {

    /**
     * Intercept at HEAD so a mature crop is harvested instead of being left to
     * vanilla (which does nothing for crops) or, worse, attempted as a till.
     */
    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void equipLeveling$harvestCrop(ItemUsageContext context, CallbackInfoReturnable<ActionResult> callback) {
        PlayerEntity player = context.getPlayer();
        if (player == null) return;

        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (!isHarvestable(state)) return;

        ItemStack hoe = context.getStack();
        if (!"hoe".equals(EquipmentCategory.getCategory(hoe))) return;

        // Server-authoritative: spawn drops (with Fortune), reset the crop, award XP.
        if (!world.isClient()) {
            ServerWorld serverWorld = (ServerWorld) world;
            BlockEntity blockEntity = world.getBlockEntity(pos);
            List<ItemStack> drops = Block.getDroppedStacks(state, serverWorld, pos, blockEntity, player, hoe);
            for (ItemStack drop : drops) {
                if (!drop.isEmpty()) Block.dropStack(world, pos, drop);
            }
            world.setBlockState(pos, resetToSeed(state), Block.NOTIFY_ALL);

            int baseXp = XpCalculator.calculateHoeXp(state);
            if (baseXp > 0) {
                double srcMult = EquipLevelingConfig.getSourceMultiplier("farming");
                int xp = XpCalculator.applyMultipliers(baseXp, srcMult);
                if (EquipmentComponent.addXp(hoe, xp, player)) {
                    XpDisplay.showForPlayer(player, Vec3d.ofCenter(pos), xp);
                }
            }
        }

        callback.setReturnValue(ActionResult.SUCCESS);
    }

    /** Awards tilling XP only after HoeItem successfully turns a block into farmland. */
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

    private static boolean isHarvestable(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.isMature(state);
        }
        if (block instanceof NetherWartBlock) {
            return state.get(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        }
        return false;
    }

    private static BlockState resetToSeed(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.withAge(0);
        }
        if (block instanceof NetherWartBlock) {
            return state.with(NetherWartBlock.AGE, 0);
        }
        return state;
    }
}
