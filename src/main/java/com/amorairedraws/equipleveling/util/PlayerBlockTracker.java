package com.amorairedraws.equipleveling.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.SaplingBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks blocks placed by players so the mod can award zero XP when a player
 * breaks their own placed blocks. This prevents silk-touch-abuse and
 * place-mine-repeat cheese.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Saplings are NOT tracked, so trees that naturally grow from player-planted
 *       saplings still award XP when chopped. Only blocks the player explicitly
 *       placed (logs, ores, etc.) are tracked.</li>
 *   <li>Entries expire after 30 minutes — long enough to prevent combat-logging
 *       abuse, short enough to keep memory negligible (a few hundred KB per player).</li>
 *   <li>The stored block type is compared on break; if it changed (e.g. sapling
 *       grew into a log), XP is still awarded.</li>
 *   <li>Stale entries are cleaned by a lazy sweep that triggers when the map grows
 *       beyond a threshold, so there's no background timer thread.</li>
 * </ul>
 */
public final class PlayerBlockTracker {

    /** Maximum age of a tracked placement in milliseconds (30 minutes). */
    private static final long MAX_AGE_MS = 30 * 60 * 1000L;

    /** Cleanup triggers when the map exceeds this many entries. */
    private static final int CLEANUP_THRESHOLD = 5000;

    /**
     * Key: packed long (world hash ^ blockPos), Value: placement record.
     * ConcurrentHashMap lets us avoid synchronizing on every lookup.
     */
    private static final Map<Long, PlacementRecord> placements = new ConcurrentHashMap<>();

    private PlayerBlockTracker() {}

    // ------------------------------------------------------------------ //
    // Public API                                                          //
    // ------------------------------------------------------------------ //

    /**
     * Call when a player places a block. Saplings and other non-XP blocks are
     * silently skipped so naturally grown trees still give XP.
     */
    public static void onBlockPlaced(World world, BlockPos pos, UUID player, BlockState state) {
        if (player == null || state == null) return;
        // Don't track saplings — they grow into trees that SHOULD give XP.
        if (state.getBlock() instanceof SaplingBlock) return;
        long key = pack(world, pos);
        placements.put(key, new PlacementRecord(player, state, System.currentTimeMillis()));
        lazyCleanup();
    }

    /**
     * Returns {@code true} if the player placed this exact block type at this
     * location recently. Used to decide whether to award zero XP.
     */
    public static boolean isPlayerPlaced(World world, BlockPos pos, UUID player, BlockState currentState) {
        if (player == null) return false;
        long key = pack(world, pos);
        PlacementRecord rec = placements.get(key);
        if (rec == null) return false;
        // Different player? Allow XP.
        if (!rec.player.equals(player)) return false;
        // Different block type? The original placement was for something else
        // (e.g. a sapling that grew into a log), so allow XP.
        if (!rec.blockState.getBlock().equals(currentState.getBlock())) {
            // Remove the stale record so repeated checks on the grown block are fast.
            placements.remove(key);
            return false;
        }
        return true;
    }

    /**
     * Removes the tracking entry when the block is broken so a player cannot
     * place, break (0 XP), and then have someone else break the "natural" block
     * for 0 XP. Called after the break check.
     */
    public static void onBlockBroken(World world, BlockPos pos) {
        placements.remove(pack(world, pos));
    }

    /** Mostly for debugging — returns the current entry count. */
    public static int size() {
        return placements.size();
    }

    /** Clears all tracked placements. Call on server shutdown if desired. */
    public static void clear() {
        placements.clear();
    }

    // ------------------------------------------------------------------ //
    // Internals                                                           //
    // ------------------------------------------------------------------ //

    private static void lazyCleanup() {
        if (placements.size() < CLEANUP_THRESHOLD) return;
        long cutoff = System.currentTimeMillis() - MAX_AGE_MS;
        for (Iterator<Map.Entry<Long, PlacementRecord>> it = placements.entrySet().iterator(); it.hasNext();) {
            if (it.next().getValue().timestamp < cutoff) it.remove();
        }
    }

    private static long pack(World world, BlockPos pos) {
        // XOR the dimension hash with the serialised position to avoid collisions
        // between identical coordinates in different dimensions.
        long dim = (long) world.getRegistryKey().hashCode() << 32;
        return dim ^ pos.asLong();
    }

    // ------------------------------------------------------------------ //
    // Record                                                              //
    // ------------------------------------------------------------------ //

    private record PlacementRecord(UUID player, BlockState blockState, long timestamp) {}
}
