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
 * A solar eruption of a star fragments block (see the client's StarFlareParticle): a glowing blob bursts out of a face
 * and travels along an arc of circle around the block's centre (the particle's spawn position), stretching and fading.
 *
 * @param colour  star colour index: 0 blue, 1 green, 2 purple, 3 red, 4 yellow, 5 black (sprite set)
 * @param face    Direction id of the face it bursts out of (the arc starts on that axis)
 * @param tangent Direction id the arc turns towards (perpendicular to the face)
 * @param radius  arc radius in blocks, from the block's centre
 * @param sweep   arc angle in radians
 * @param life    lifetime in ticks
 */
public record StarFlareEffect(int colour, int face, int tangent, float radius, float sweep, int life)
        implements ParticleEffect {

    public static final MapCodec<StarFlareEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("colour").forGetter(StarFlareEffect::colour),
            Codec.INT.fieldOf("face").forGetter(StarFlareEffect::face),
            Codec.INT.fieldOf("tangent").forGetter(StarFlareEffect::tangent),
            Codec.FLOAT.fieldOf("radius").forGetter(StarFlareEffect::radius),
            Codec.FLOAT.fieldOf("sweep").forGetter(StarFlareEffect::sweep),
            Codec.INT.fieldOf("life").forGetter(StarFlareEffect::life)
    ).apply(instance, StarFlareEffect::new));

    public static final PacketCodec<ByteBuf, StarFlareEffect> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, StarFlareEffect::colour,
            PacketCodecs.VAR_INT, StarFlareEffect::face,
            PacketCodecs.VAR_INT, StarFlareEffect::tangent,
            PacketCodecs.FLOAT, StarFlareEffect::radius,
            PacketCodecs.FLOAT, StarFlareEffect::sweep,
            PacketCodecs.VAR_INT, StarFlareEffect::life,
            StarFlareEffect::new);

    @Override
    public ParticleType<?> getType() {
        return ModParticles.STAR_FLARE;
    }
}
