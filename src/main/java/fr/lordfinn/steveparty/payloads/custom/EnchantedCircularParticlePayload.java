package fr.lordfinn.steveparty.payloads.custom;


import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.payloads.ModPacketCodecs;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.Vec3d;

public record EnchantedCircularParticlePayload(Vec3d position, Integer distance, Integer count) implements CustomPayload {
    public static final CustomPayload.Id<EnchantedCircularParticlePayload> ID = new CustomPayload.Id<>(Steveparty.id("enchanted_circular_particles"));
    public static final PacketCodec<RegistryByteBuf, EnchantedCircularParticlePayload> CODEC =
            PacketCodec.tuple(
                    ModPacketCodecs.VEC3D, EnchantedCircularParticlePayload::position,
                    PacketCodecs.INTEGER, EnchantedCircularParticlePayload::distance,
                    PacketCodecs.INTEGER, EnchantedCircularParticlePayload::count,
                    EnchantedCircularParticlePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
