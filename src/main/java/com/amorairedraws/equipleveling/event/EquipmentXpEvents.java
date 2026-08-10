package com.amorairedraws.equipleveling.event;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import com.amorairedraws.equipleveling.util.EquipmentCategory;
import com.amorairedraws.equipleveling.util.PlayerBlockTracker;
import com.amorairedraws.equipleveling.util.XpCalculator;

public class EquipmentXpEvents {

    /** Called after a living entity actually dies. */
    public static void awardKillXp(PlayerEntity player, LivingEntity entity,
            net.minecraft.entity.damage.DamageSource source) {
        if (source.getSource() != player && source.getAttacker() != player) return;
        ItemStack held = player.getMainHandStack();
        String category = EquipmentCategory.getCategory(held);
        if ("sword".equals(category) || "axe".equals(category) || "bow".equals(category)) {
            int baseXp = XpCalculator.calculateEntityKillXp(entity);
            if (baseXp <= 0) return;

            // Use livestock multiplier for passive animals, mob multiplier for everything else.
            boolean isLivestock = XpCalculator.calculateEntityKillXp(entity) == 5;
            double srcMult = isLivestock
                    ? EquipLevelingConfig.getSourceMultiplier("livestock")
                    : EquipLevelingConfig.getSourceMultiplier("mob");

            int xp = XpCalculator.applyMultipliers(baseXp, srcMult);
            if (EquipmentComponent.addXp(held, xp, player)) {
                XpDisplay.showForPlayer(player, entity.getEntityPos(), xp);
            }
        }
    }

    public static class EntityKillXpHandler implements AttackEntityCallback {
        @Override
        public ActionResult interact(PlayerEntity player, World world, net.minecraft.util.Hand hand,
                                     Entity entity, net.minecraft.util.hit.EntityHitResult hitResult) {
            // Observation only — the actual reward is issued from AFTER_DEATH on the server.
            return ActionResult.PASS;
        }
    }

    public static class BlockBreakXpHandler implements PlayerBlockBreakEvents.After {
        @Override
        public void afterBlockBreak(World world, PlayerEntity player, net.minecraft.util.math.BlockPos pos,
                                    net.minecraft.block.BlockState state, net.minecraft.block.entity.BlockEntity breakingEntity) {
            ItemStack heldItem = player.getMainHandStack();
            String category = EquipmentCategory.getCategory(heldItem);

            if (category == null) return;

            // Check for player-placed block abuse.
            if (PlayerBlockTracker.isPlayerPlaced(world, pos, player.getUuid(), state)) {
                PlayerBlockTracker.onBlockBroken(world, pos);
                return; // 0 XP for self-placed blocks
            }

            int baseXp = 0;
            String sourceKey = null;
            switch (category) {
                case "pickaxe" -> { baseXp = XpCalculator.calculateOreXp(state); sourceKey = "mining"; }
                case "axe"    -> { baseXp = XpCalculator.calculateLogXp(state); sourceKey = "wood"; }
                case "shovel" -> { baseXp = XpCalculator.calculateShovelXp(state); sourceKey = "mining"; }
                case "hoe"    -> { baseXp = XpCalculator.calculateHoeXp(state); sourceKey = "farming"; }
            }

            // Server-authoritative progression.
            if (baseXp > 0 && !world.isClient() && sourceKey != null) {
                double srcMult = EquipLevelingConfig.getSourceMultiplier(sourceKey);
                int xp = XpCalculator.applyMultipliers(baseXp, srcMult);
                if (EquipmentComponent.addXp(heldItem, xp, player)) {
                    XpDisplay.showForPlayer(player, net.minecraft.util.math.Vec3d.ofCenter(pos), xp);
                }
            }
        }
    }

    public static class PlaceTrackingHandler implements net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.Before {
        @Override
        public boolean beforeBlockBreak(World world, PlayerEntity player, net.minecraft.util.math.BlockPos pos,
                                        net.minecraft.block.BlockState state, net.minecraft.block.entity.BlockEntity blockEntity) {
            // Nothing to do here — the check happens in BlockBreakXpHandler.afterBlockBreak.
            return true;
        }
    }
}
