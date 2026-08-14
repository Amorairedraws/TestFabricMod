package com.amorairedraws.equipleveling.event;

import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Action-bar XP feedback. Progression is server-authoritative and there is no
 * client prediction, so a vanilla action-bar message is the single reliable
 * feedback channel on every renderer (a custom world-text pass was dropped as
 * too fragile).
 */
public final class XpFeedback {
    private XpFeedback() { }

    /** Sends a "+N equipment XP" action-bar message when the gain meets the
     *  configured display threshold. No-op for non-server players. */
    public static void showForPlayer(PlayerEntity player, int amount) {
        if (amount < EquipLevelingConfig.getXpDisplayThreshold()) return;
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(Text.literal("+" + amount + " equipment XP")
                    .formatted(Formatting.AQUA), true);
        }
    }
}
