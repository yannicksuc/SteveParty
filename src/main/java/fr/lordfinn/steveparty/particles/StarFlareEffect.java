package fr.lordfinn.steveparty.particles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;

/**
 * A star fragments block's swoosh slash (see the client's StarFlareParticle): a crescent ribbon that sweeps an arc of
 * circle centred on the block (the particle's spawn position), starting from an exposed face, then fades.
 *
 * @param colour  star colour index: 0 blue, 1 green, 2 purple, 3 red, 4 yellow, 5 black (sprite set)
 * @param face    Direction id of the face it bursts out of (the arc starts on that axis)
 * @param roll    angle (radians) of the arc's plane around the face normal: the slash's orientation
 * @param radius  arc radius in blocks, from the block's centre
 * @param sweep   arc angle in radians
 * @param life    lifetime in ticks
 */
public record StarFlareEffect(int colour, int face, float roll, float radius, float sweep, int life)
        implements ParticleEffect {

    public static final MapCodec<StarFlareEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("colour").forGetter(StarFlareEffect::colour),
            Codec.INT.fieldOf("face").forGetter(StarFlareEffect::face),
            Codec.FLOAT.fieldOf("roll").forGetter(StarFlareEffect::roll),
            Codec.FLOAT.fieldOf("radius").forGetter(StarFlareEffect::radius),
            Codec.FLOAT.fieldOf("sweep").forGetter(StarFlareEffect::sweep),
            Codec.INT.fieldOf("life").forGetter(StarFlareEffect::life)
    ).apply(instance, StarFlareEffect::new));

    public static final PacketCodec<ByteBuf, StarFlareEffect> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, StarFlareEffect::colour,
            PacketCodecs.VAR_INT, StarFlareEffect::face,
            PacketCodecs.FLOAT, StarFlareEffect::roll,
            PacketCodecs.FLOAT, StarFlareEffect::radius,
            PacketCodecs.FLOAT, StarFlareEffect::sweep,
            PacketCodecs.VAR_INT, StarFlareEffect::life,
            StarFlareEffect::new);

    @Override
    public ParticleType<?> getType() {
        return ModParticles.STAR_FLARE;
    }
}
