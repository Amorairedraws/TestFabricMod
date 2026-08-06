package com.amorairedraws.equipleveling.util;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.block.BlockState;

import com.amorairedraws.equipleveling.config.EquipLevelingConfig;

public class XpCalculator {
	
	// Entity kill XP based on max health
	public static int calculateEntityKillXp(LivingEntity entity) {
		float maxHealth = entity.getMaxHealth();
		int xp = Math.max(5, (int) (maxHealth * 2));
		return Math.max(xp, EquipLevelingConfig.getXpDisplayThreshold());
	}

	// Ore XP calculation
	public static int calculateOreXp(BlockState state) {
		Block block = state.getBlock();
		
		// Diamond, emerald, ancient debris - high XP
		if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE ||
			block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE ||
			block == Blocks.ANCIENT_DEBRIS) {
			return 150;
		}
		
		// Gold - medium-high XP
		if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE) {
			return 80;
		}
		
		// Iron - medium XP
		if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) {
			return 40;
		}
		
		// Copper, redstone, lapis - low-medium XP
		if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE ||
			block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE ||
			block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) {
			return 25;
		}
		
		// Coal - low XP
		if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) {
			return 15;
		}
		
		return 0;
	}

	// Log XP calculation
	public static int calculateLogXp(BlockState state) {
		if (state.isIn(BlockTags.LOGS)) {
			return 10;
		}
		return 0;
	}

	// Shovel XP calculation (dirt, sand, gravel, snow)
	public static int calculateShovelXp(BlockState state) {
		Block block = state.getBlock();
		if (block == Blocks.DIRT || block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT ||
			block == Blocks.SAND || block == Blocks.RED_SAND || block == Blocks.GRAVEL ||
			block == Blocks.SNOW_BLOCK || block == Blocks.SNOW) {
			return 5;
		}
		return 0;
	}

	// Hoe XP calculation (crop harvests)
	public static int calculateHoeXp(BlockState state) {
		Block block = state.getBlock();
		// Check if it's a crop-like block
		if (block == Blocks.WHEAT || block == Blocks.CARROTS || block == Blocks.POTATOES ||
			block == Blocks.BEETROOTS || block == Blocks.NETHER_WART) {
			return 15;
		}
		// Tilling soil
		if (block == Blocks.DIRT || block == Blocks.GRASS_BLOCK || block == Blocks.COARSE_DIRT) {
			return 3;
		}
		return 0;
	}
}
