package com.amorairedraws.equipleveling.network;

import com.amorairedraws.equipleveling.EquipLevelingMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server-to-client packet that sends the full config JSON string.
 * Clients use this to sync their local view of the server's configuration.
 */
public record ConfigSyncPacket(String json) implements CustomPayload {
    public static final CustomPayload.Id<ConfigSyncPacket> ID =
            new CustomPayload.Id<>(Identifier.of(EquipLevelingMod.MOD_ID, "config_sync"));
    public static final PacketCodec<RegistryByteBuf, ConfigSyncPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, ConfigSyncPacket::json,
            ConfigSyncPacket::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
