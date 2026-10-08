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
 * The Mula's little lights: a tinted sprite ({@link #TWINKLE} four-point twinkle, {@link #STAR_BIT} star bit,
 * {@link #Z} sleepy z), each with its own motion (see the client's MulaSparkleParticle). Only spawned on the client
 * (animation effects), never sent by the server, but a particle type needs its codecs anyway.
 *
 * @param color RGB tint
 * @param scale size factor (1 = a small sparkle)
 * @param style {@link #TWINKLE}, {@link #STAR_BIT} or {@link #Z}
 */
public record MulaSparkleEffect(int color, float scale, int style) implements ParticleEffect {
    public static final int TWINKLE = 0, STAR_BIT = 1, Z = 2;

    public static final MapCodec<MulaSparkleEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("color").forGetter(MulaSparkleEffect::color),
            Codec.FLOAT.fieldOf("scale").forGetter(MulaSparkleEffect::scale),
            Codec.INT.fieldOf("style").forGetter(MulaSparkleEffect::style)
    ).apply(instance, MulaSparkleEffect::new));

    public static final PacketCodec<ByteBuf, MulaSparkleEffect> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, MulaSparkleEffect::color,
            PacketCodecs.FLOAT, MulaSparkleEffect::scale,
            PacketCodecs.INTEGER, MulaSparkleEffect::style,
            MulaSparkleEffect::new);

    @Override
    public ParticleType<?> getType() {
        return ModParticles.MULA_SPARKLE;
    }
}
