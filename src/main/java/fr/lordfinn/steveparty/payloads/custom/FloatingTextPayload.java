package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import org.joml.Vector3f;

public record FloatingTextPayload(
        Vector3f pos,
        Vector3f velocity,
        float duration,      // en ticks
        float scale,
        int color,           // 0xRRGGBB
        float fadeStart,     // 0 à 1
        String text
) implements CustomPayload {

    public static final CustomPayload.Id<FloatingTextPayload> ID = new CustomPayload.Id<>(Steveparty.id("floating_text"));

    // 7 fields: PacketCodec.tuple stops at 6 in 1.21.1, so written field by field (same order)
    public static final PacketCodec<RegistryByteBuf, FloatingTextPayload> CODEC =
            PacketCodec.of(FloatingTextPayload::write, FloatingTextPayload::read);

    private void write(RegistryByteBuf buf) {
        PacketCodecs.VECTOR3F.encode(buf, pos);
        PacketCodecs.VECTOR3F.encode(buf, velocity);
        PacketCodecs.FLOAT.encode(buf, duration);
        PacketCodecs.FLOAT.encode(buf, scale);
        PacketCodecs.INTEGER.encode(buf, color);
        PacketCodecs.FLOAT.encode(buf, fadeStart);
        PacketCodecs.STRING.encode(buf, text);
    }

    private static FloatingTextPayload read(RegistryByteBuf buf) {
        return new FloatingTextPayload(PacketCodecs.VECTOR3F.decode(buf), PacketCodecs.VECTOR3F.decode(buf),
                PacketCodecs.FLOAT.decode(buf), PacketCodecs.FLOAT.decode(buf), PacketCodecs.INTEGER.decode(buf),
                PacketCodecs.FLOAT.decode(buf), PacketCodecs.STRING.decode(buf));
    }

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
