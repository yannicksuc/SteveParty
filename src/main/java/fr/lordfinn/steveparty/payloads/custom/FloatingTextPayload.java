package fr.lordfinn.steveparty.payloads.custom;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import static fr.lordfinn.steveparty.payloads.ModPayloads.FLOATING_TEXT_PAYLOAD;

public record FloatingTextPayload(
        Vector3f pos,
        Vector3f velocity,
        float duration,      // en ticks
        float scale,
        int color,           // 0xRRGGBB
        float fadeStart,     // 0 à 1
        String text
) implements CustomPayload {

    public static final CustomPayload.Id<FloatingTextPayload> ID = new CustomPayload.Id<>(FLOATING_TEXT_PAYLOAD);

    public static final PacketCodec<RegistryByteBuf, FloatingTextPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VECTOR_3F,FloatingTextPayload::pos,
                    PacketCodecs.VECTOR_3F,FloatingTextPayload::velocity,
                    PacketCodecs.FLOAT, FloatingTextPayload::duration,
                    PacketCodecs.FLOAT, FloatingTextPayload::scale,
                    PacketCodecs.INTEGER, FloatingTextPayload::color,
                    PacketCodecs.FLOAT, FloatingTextPayload::fadeStart,
                    PacketCodecs.STRING, FloatingTextPayload::text,
                    FloatingTextPayload::new
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    // Helpers pour le codec
    private double posX() { return pos.x; }
    private double posY() { return pos.y; }
    private double posZ() { return pos.z; }
    private double velX() { return velocity.x; }
    private double velY() { return velocity.y; }
    private double velZ() { return velocity.z; }
}
