package fr.lordfinn.steveparty.payloads.custom;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.Vec3d;

import static fr.lordfinn.steveparty.payloads.ModPayloads.FLOATING_TEXT_PAYLOAD;

public record FloatingTextPayload(
        Vec3d pos,
        Vec3d velocity,
        float duration,      // en ticks
        float scale,
        int color,           // 0xRRGGBB
        float fadeStart,     // 0 à 1
        String text
) implements CustomPayload {

    public static final CustomPayload.Id<FloatingTextPayload> ID = new CustomPayload.Id<>(FLOATING_TEXT_PAYLOAD);

    public static final PacketCodec<RegistryByteBuf, FloatingTextPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.DOUBLE, buf -> buf.readDouble(), FloatingTextPayload::posX,
                    PacketCodecs.DOUBLE, buf -> buf.readDouble(), FloatingTextPayload::posY,
                    PacketCodecs.DOUBLE, buf -> buf.readDouble(), FloatingTextPayload::posZ,
                    PacketCodecs.DOUBLE, buf -> buf.readDouble(), FloatingTextPayload::velX,
                    PacketCodecs.DOUBLE, buf -> buf.readDouble(), FloatingTextPayload::velY,
                    PacketCodecs.DOUBLE, buf -> buf.readDouble(), FloatingTextPayload::velZ,
                    PacketCodecs.FLOAT, FloatingTextPayload::duration,
                    PacketCodecs.FLOAT, FloatingTextPayload::scale,
                    PacketCodecs.INT, FloatingTextPayload::color,
                    PacketCodecs.FLOAT, FloatingTextPayload::fadeStart,
                    PacketCodecs.STRING, FloatingTextPayload::text,
                    (x, y, z, vx, vy, vz, duration, scale, color, fadeStart, text) ->
                            new FloatingTextPayload(
                                    new Vec3d(x, y, z),
                                    new Vec3d(vx, vy, vz),
                                    duration,
                                    scale,
                                    color,
                                    fadeStart,
                                    text
                            )
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
