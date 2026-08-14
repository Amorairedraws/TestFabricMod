package com.amorairedraws.equipleveling.util;

import net.minecraft.block.*;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.intprovider.IntProvider;

import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import com.amorairedraws.equipleveling.mixin.ExperienceDroppingBlockAccessor;

import java.util.Set;

/**
 * Central XP calculator using formula-driven defaults.
 *
 * <h3>Formulas</h3>
 * <ul>
 *   <li><b>Mining:</b> {@code (miningLevel + 1)² × 2 + vanillaXpMedian × 5}.
 *       Stone-type blocks = 1, ancient debris = 200.</li>
 *   <li><b>Wood:</b> 4 XP per log.</li>
 *   <li><b>Shovel:</b> 1 XP for dirt/sand/gravel/snow, 5 for clay.</li>
 *   <li><b>Mobs:</b> Danger score — {@code XP = 0.45 × (Offense × Defense)^0.9}</li>
 *   <li><b>Crops:</b> 3 XP base + bonus for longer growth stages.</li>
 * </ul>
 */
public final class XpCalculator {
    private XpCalculator() {}

    private static final Set<EntityType<?>> EXCLUDED_MOBS = Set.of(
            EntityType.VILLAGER, EntityType.WANDERING_TRADER,
            EntityType.IRON_GOLEM, EntityType.SNOW_GOLEM,
            EntityType.WARDEN
    );

    // ================================================================== //
    // Entity Kill XP (Danger Score)                                       //
    // ================================================================== //

    /** Danger score formula: XP = 0.45 × Danger^0.9. Excluded mobs return 0. */
    public static int calculateEntityKillXp(LivingEntity entity) {
        if (entity == null) return 0;
        if (EXCLUDED_MOBS.contains(entity.getType())) return 0;
        if (isLivestock(entity)) return 5;

        double attackDamage = entity.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        if (attackDamage < 1.0) attackDamage = 3.0;

        double maxHealth = entity.getMaxHealth();
        double armor = entity.getAttributeValue(EntityAttributes.ARMOR);

        double offense = attackDamage;
        double defense = maxHealth + armor;
        double danger = offense * defense;

        return (int) Math.round(0.45 * Math.pow(danger, 0.9));
    }

    /** True for passive farm animals that use the "livestock" XP multiplier. */
    public static boolean isLivestock(LivingEntity entity) {
        return entity instanceof CowEntity || entity instanceof PigEntity
                || entity instanceof SheepEntity || entity instanceof ChickenEntity
                || entity instanceof RabbitEntity || entity instanceof GoatEntity
                || entity instanceof HorseEntity || entity instanceof DonkeyEntity
                || entity instanceof MuleEntity || entity instanceof LlamaEntity
                || entity instanceof FoxEntity || entity instanceof TurtleEntity;
    }

    // ================================================================== //
    // Block Break XP                                                      //
    // ================================================================== //

    /**
     * Mining XP using the full formula:
     * {@code (miningLevel + 1)² × 2 + vanillaXpMedian × 5}
     *
     * <ul>
     *   <li>Stone-type blocks (miningLevel 0, no vanilla XP): clamped to 1.</li>
     *   <li>Ancient debris: always 200.</li>
     *   <li>Custom per-block config overrides take priority.</li>
     * </ul>
     */
    public static int calculateOreXp(BlockState state) {
        Block block = state.getBlock();
        String id = net.minecraft.registry.Registries.BLOCK.getId(block).toString();

        // Custom block XP overrides take priority.
        Integer custom = EquipLevelingConfig.getCustomBlockXp().get(id);
        if (custom != null) return Math.max(0, custom);

        // Ancient debris always 200.
        if (block == Blocks.ANCIENT_DEBRIS) return 200;

        int miningLevel = getMiningLevel(state);

        // Vanilla XP value for this block (median of the uniform int range).
        int vanillaXpMedian = 0;
        if (block instanceof ExperienceDroppingBlock edb) {
            IntProvider provider = ((ExperienceDroppingBlockAccessor) edb).equipleveling$getExperienceDropped();
            vanillaXpMedian = (provider.getMin() + provider.getMax()) / 2;
        }

        // Stone-type blocks: mining level 0 with no vanilla XP → always 1.
        if (miningLevel == 0 && vanillaXpMedian == 0) return 1;

        // Full formula: (miningLevel + 1)² × 2 + vanilla median × 5
        int xp = (miningLevel + 1) * (miningLevel + 1) * 2 + vanillaXpMedian * 5;
        return Math.max(1, xp);
    }

    /** Flat 4 XP per log. */
    public static int calculateLogXp(BlockState state) {
        return state.isIn(BlockTags.LOGS) ? 4 : 0;
    }

    /** 1 XP for dirt/sand/gravel/snow, 5 for clay. */
    public static int calculateShovelXp(BlockState state) {
        if (state.isOf(Blocks.CLAY)) return 5;
        if (state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.SAND)
                || state.isIn(BlockTags.SNOW) || state.isOf(Blocks.GRAVEL)) {
            return 1;
        }
        return 0;
    }

    /** Crop XP: 3 base + bonus for longer growth. Nether wart = 7. */
    public static int calculateHoeXp(BlockState state) {
        if (state.isOf(Blocks.NETHER_WART)) return 7;

        if (state.getBlock() instanceof CropBlock crop) {
            if (!crop.isMature(state)) return 0;
            int maxAge = crop.getMaxAge();
            int xp = 3 + Math.max(0, (maxAge - 4) / 2);
            return Math.max(1, Math.min(15, xp));
        }

        if (state.isIn(BlockTags.CROPS)) return 3;
        return 0;
    }

    // ================================================================== //
    // Multiplier application                                              //
    // ================================================================== //

    public static int applyMultipliers(int baseXp, double sourceMultiplier) {
        if (baseXp <= 0) return 0;
        double globalGain = EquipLevelingConfig.getGlobalXpGainMultiplier();
        double result = baseXp * globalGain * sourceMultiplier;
        return Math.max(1, (int) Math.round(result));
    }

    // ================================================================== //
    // Helpers                                                             //
    // ================================================================== //

    /** Determines mining level from vanilla block tags. */
    private static int getMiningLevel(BlockState state) {
        if (state.isIn(BlockTags.NEEDS_DIAMOND_TOOL)) return 3;
        if (state.isIn(BlockTags.NEEDS_IRON_TOOL)) return 2;
        if (state.isIn(BlockTags.NEEDS_STONE_TOOL)) return 1;
        return 0;
    }
}
