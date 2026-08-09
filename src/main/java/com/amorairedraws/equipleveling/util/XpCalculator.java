package com.amorairedraws.equipleveling.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.BlockTags;

import com.amorairedraws.equipleveling.config.EquipLevelingConfig;

/** Pure XP calculations shared by the server event handlers and client previews. */
public final class XpCalculator {
    private XpCalculator() {}

    /** Entity kill XP scales gently with the killed entity's maximum health.
     * A zombie (20 HP) awards 10 XP, so a sword needs ~40 kills to level up. */
    public static int calculateEntityKillXp(LivingEntity entity) {
        return Math.max(1, (int) Math.ceil(entity.getMaxHealth() / 2.0));
    }

    /**
     * Returns zero for ordinary stone and other common blocks.  The explicit
     * vanilla cases cover the rarity ladder, while the block tags make the
     * common categories safe to extend through datapacks in the future.
     */
    public static int calculateOreXp(BlockState state) {
        Block block = state.getBlock();
        // Player-configured blocks always take priority so users can extend the
        // rarity ladder (or override a vanilla value) without editing code.
        Integer custom = EquipLevelingConfig.getCustomBlockXp().get(
                net.minecraft.registry.Registries.BLOCK.getId(block).toString());
        if (custom != null) return Math.max(0, custom);
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE
                || block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE
                || block == Blocks.ANCIENT_DEBRIS || block == Blocks.NETHER_QUARTZ_ORE
                || block == Blocks.NETHER_GOLD_ORE) {
            return EquipLevelingConfig.getRareOreXp();
        }
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) {
            return EquipLevelingConfig.getGoldXp();
        }
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) {
            return EquipLevelingConfig.getIronXp();
        }
        if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE
                || block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE
                || block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) {
            return Math.max(0, EquipLevelingConfig.getCoalXp() * 2);
        }
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) {
            return EquipLevelingConfig.getCoalXp();
        }
        return 0;
    }

    /** Every log/stem in the vanilla LOGS tag is an axe action. */
    public static int calculateLogXp(BlockState state) {
        return state.isIn(BlockTags.LOGS) ? 4 : 0;
    }

    /** Shovel XP is tag based so modded dirt, sand, gravel and snow work too. */
    public static int calculateShovelXp(BlockState state) {
        return state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.SAND)
                || state.isIn(BlockTags.SNOW) || state.isOf(Blocks.GRAVEL) ? 1 : 0;
    }

    /** XP is awarded for harvesting mature crop blocks, not destroying seedlings. */
    public static int calculateHoeXp(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMature(state) ? 3 : 0;
        }
        if (state.isIn(BlockTags.CROPS)) return 3;
        return state.isOf(Blocks.NETHER_WART) ? 3 : 0;
    }
}
