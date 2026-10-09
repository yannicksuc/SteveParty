package fr.lordfinn.steveparty.payloads.custom;


import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.payloads.ModPacketCodecs;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.Vec3d;

public record ArrowParticlesPayload(Vec3d position, Vec3d velocity) implements CustomPayload {
    public static final CustomPayload.Id<ArrowParticlesPayload> ID = new CustomPayload.Id<>(Steveparty.id("arrow_particles"));
    public static final PacketCodec<RegistryByteBuf, ArrowParticlesPayload> CODEC =
            PacketCodec.tuple(
                    ModPacketCodecs.VEC3D, ArrowParticlesPayload::position,
                    ModPacketCodecs.VEC3D, ArrowParticlesPayload::velocity,
                    ArrowParticlesPayload::new);
    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
