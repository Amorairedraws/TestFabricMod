package com.amorairedraws.equipleveling.network;

import com.amorairedraws.equipleveling.EquipLevelingMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Server-authoritative floating XP notification. */
public record XpGainPayload(Vec3d position, int amount) implements CustomPayload {
    public static final CustomPayload.Id<XpGainPayload> ID =
            new CustomPayload.Id<>(Identifier.of(EquipLevelingMod.MOD_ID, "xp_gain"));
    public static final PacketCodec<RegistryByteBuf, XpGainPayload> CODEC = PacketCodec.tuple(
            Vec3d.PACKET_CODEC, XpGainPayload::position,
            PacketCodecs.VAR_INT, XpGainPayload::amount,
            XpGainPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
