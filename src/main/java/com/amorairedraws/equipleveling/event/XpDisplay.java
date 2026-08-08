package com.amorairedraws.equipleveling.event;

import com.amorairedraws.equipleveling.config.EquipLevelingConfig;
import com.amorairedraws.equipleveling.network.XpGainPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.function.BiConsumer;

/** Side-neutral bridge for server-authoritative floating XP labels. */
public final class XpDisplay {
    private static BiConsumer<Vec3d, Integer> sink = (position, amount) -> {};
    private XpDisplay() {}

    public static void install(BiConsumer<Vec3d, Integer> clientSink) {
        sink = clientSink;
    }

    /** Local/client-only presentation path, retained for client-side callbacks. */
    public static void show(Vec3d position, int amount) {
        if (amount >= EquipLevelingConfig.getXpDisplayThreshold()) {
            sink.accept(position, amount);
        }
    }

    /**
     * Sends a reward notification only after the server has awarded the XP.
     * This avoids showing a kill label for an attack that did not kill its target.
     */
    public static void showForPlayer(PlayerEntity player, Vec3d position, int amount) {
        if (amount < EquipLevelingConfig.getXpDisplayThreshold()) return;
        if (player instanceof ServerPlayerEntity serverPlayer) {
            ServerPlayNetworking.send(serverPlayer, new XpGainPayload(position, amount));
        } else {
            show(position, amount);
        }
    }
}
