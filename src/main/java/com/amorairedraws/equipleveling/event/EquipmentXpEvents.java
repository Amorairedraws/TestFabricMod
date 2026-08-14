package com.amorairedraws.equipleveling.event;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import com.amorairedraws.equipleveling.component.EquipmentComponent;
import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import com.amorairedraws.equipleveling.util.EquipmentCategory;
import com.amorairedraws.equipleveling.util.PlayerBlockTracker;
import com.amorairedraws.equipleveling.util.XpCalculator;

public class EquipmentXpEvents {

    /**
     * Called after a living entity actually dies.
     *
     * <p>Uses vanilla and Fabric convention item tags to determine whether the
     * held item is a melee or ranged weapon, rather than relying on the item's
     * single {@code EquipmentCategory} label. This means a pickaxe-axe hybrid
     * that is in {@code ItemTags.AXES} earns kill XP, while a pure pickaxe
     * does not — matching player expectations.
     */
    public static void awardKillXp(PlayerEntity player, LivingEntity entity,
            net.minecraft.entity.damage.DamageSource source) {
        if (source.getSource() != player && source.getAttacker() != player) return;
        ItemStack held = player.getMainHandStack();

        if (!EquipmentCategory.isEquipment(held)) return;

        // Melee weapons: vanilla swords/axes + Fabric convention melee/spear tags.
        boolean isMelee = held.isIn(ItemTags.SWORDS) || held.isIn(ItemTags.AXES)
                || held.isIn(cTag("tools/melee_weapons")) || held.isIn(cTag("tools/melee_weapon"))
                || held.isIn(cTag("tools/spears")) || held.isIn(cTag("tools/spear"));

        // Ranged weapons: vanilla bows/crossbows/tridents + Fabric convention ranged tags.
        boolean isRanged = held.isIn(ItemTags.BOW_ENCHANTABLE)
                || held.isIn(cTag("tools/ranged_weapons")) || held.isIn(cTag("tools/ranged_weapon"));

        if (!isMelee && !isRanged) return;

        int baseXp = XpCalculator.calculateEntityKillXp(entity);
        if (baseXp <= 0) return;

        // Use livestock multiplier for passive animals, mob multiplier for everything else.
        boolean isLivestock = XpCalculator.isLivestock(entity);
        double srcMult = isLivestock
                ? EquipLevelingConfig.getSourceMultiplier("livestock")
                : EquipLevelingConfig.getSourceMultiplier("mob");

        int xp = XpCalculator.applyMultipliers(baseXp, srcMult);
        if (EquipmentComponent.addXp(held, xp, player)) {
            XpFeedback.showForPlayer(player, xp);
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

    /**
     * Awards block-break XP based on which tool tags the held item has,
     * NOT its single {@code EquipmentCategory} label.
     *
     * <p>This means a pickaxe-axe hybrid (in both {@code PICKAXES} and
     * {@code AXES} tags) earns ore XP from stone and log XP from wood.
     * A modded drill that is in both {@code PICKAXES} and {@code SHOVELS}
     * earns ore XP from ores and shovel XP from dirt/sand/gravel.
     */
    public static class BlockBreakXpHandler implements PlayerBlockBreakEvents.After {
        @Override
        public void afterBlockBreak(World world, PlayerEntity player, net.minecraft.util.math.BlockPos pos,
                                    net.minecraft.block.BlockState state, net.minecraft.block.entity.BlockEntity breakingEntity) {
            ItemStack heldItem = player.getMainHandStack();
            if (!EquipmentCategory.isEquipment(heldItem)) return;

            // Check for player-placed block abuse.
            if (PlayerBlockTracker.isPlayerPlaced(world, pos, player.getUuid(), state)) {
                PlayerBlockTracker.onBlockBroken(world, pos);
                return; // 0 XP for self-placed blocks
            }

            int baseXp = 0;
            String sourceKey = null;

            // Check each tool tag independently — hybrid tools can earn XP from
            // multiple block types in a single break event, but we take the last
            // non-zero result (which handles the edge case of a log being broken
            // by a pickaxe-axe: the axe check fires after pickaxe and correctly
            // overrides with log XP).

            if (heldItem.isIn(ItemTags.PICKAXES)) {
                int xp = XpCalculator.calculateOreXp(state);
                if (xp > 0) { baseXp = xp; sourceKey = "mining"; }
            }
            if (heldItem.isIn(ItemTags.AXES)) {
                int xp = XpCalculator.calculateLogXp(state);
                if (xp > 0) { baseXp = xp; sourceKey = "wood"; }
            }
            if (heldItem.isIn(ItemTags.SHOVELS)) {
                int xp = XpCalculator.calculateShovelXp(state);
                if (xp > 0) { baseXp = xp; sourceKey = "mining"; }
            }
            if (heldItem.isIn(ItemTags.HOES)) {
                int xp = XpCalculator.calculateHoeXp(state);
                if (xp > 0) { baseXp = xp; sourceKey = "farming"; }
            }

            // Server-authoritative progression.
            if (baseXp > 0 && !world.isClient() && sourceKey != null) {
                double srcMult = EquipLevelingConfig.getSourceMultiplier(sourceKey);
                int xp = XpCalculator.applyMultipliers(baseXp, srcMult);
                if (EquipmentComponent.addXp(heldItem, xp, player)) {
                    XpFeedback.showForPlayer(player, xp);
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

    // --- helpers ---

    /** Shorthand for a Fabric convention item tag ({@code c:...}). */
    private static TagKey<net.minecraft.item.Item> cTag(String path) {
        return TagKey.of(Registries.ITEM.getKey(), Identifier.of("c", path));
    }
}