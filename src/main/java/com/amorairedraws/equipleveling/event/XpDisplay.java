package com.amorairedraws.equipleveling.event;

import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

/**
 * Lightweight XP feedback. A vanilla action-bar message is reliable on every
 * renderer and avoids the fragile custom world-text render pass that previously
 * made the floating number invisible.
 */
public final class XpDisplay {
    private XpDisplay() { }

    /** Retained for callers that only have a local presentation path. */
    public static void show(Vec3d position, int amount) {
        // Progression is server-authoritative; there is no client prediction.
    }

    public static void showForPlayer(PlayerEntity player, Vec3d position, int amount) {
        if (amount < EquipLevelingConfig.getXpDisplayThreshold()) return;
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(Text.literal("+" + amount + " equipment XP")
                    .formatted(Formatting.AQUA), true);
        }
    }
}
