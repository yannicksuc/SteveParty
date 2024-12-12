package fr.lordfinn.steveparty.payloads;


import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.Vec3d;

import static fr.lordfinn.steveparty.payloads.ModPayloads.ENCHANTED_CIRCULAR_PAYLOAD;

public record EnchantedCircularParticlePayload(Vec3d position, Integer distance, Integer count) implements CustomPayload {
    public static final CustomPayload.Id<EnchantedCircularParticlePayload> ID = new CustomPayload.Id<>(ENCHANTED_CIRCULAR_PAYLOAD);
    public static final PacketCodec<RegistryByteBuf, EnchantedCircularParticlePayload> CODEC =
            PacketCodec.tuple(
                    Vec3d.PACKET_CODEC, EnchantedCircularParticlePayload::position,
                    PacketCodecs.INTEGER, EnchantedCircularParticlePayload::distance,
                    PacketCodecs.INTEGER, EnchantedCircularParticlePayload::count,
                    EnchantedCircularParticlePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
