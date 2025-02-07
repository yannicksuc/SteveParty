package fr.lordfinn.steveparty.payloads.custom;


import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.Vec3d;

import static fr.lordfinn.steveparty.payloads.ModPayloads.ARROW_PARTICLES_PAYLOAD;

public record ArrowParticlesPayload(Vec3d position, Vec3d velocity) implements CustomPayload {
    public static final CustomPayload.Id<ArrowParticlesPayload> ID = new CustomPayload.Id<>(ARROW_PARTICLES_PAYLOAD);
    public static final PacketCodec<RegistryByteBuf, ArrowParticlesPayload> CODEC =
            PacketCodec.tuple(
                    Vec3d.PACKET_CODEC, ArrowParticlesPayload::position,
                    Vec3d.PACKET_CODEC, ArrowParticlesPayload::velocity,
                    ArrowParticlesPayload::new);
    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
