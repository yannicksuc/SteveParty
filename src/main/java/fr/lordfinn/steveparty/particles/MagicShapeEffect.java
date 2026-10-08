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
 * Tokenizer Wand magic: small outlined circles, triangles and squares in fuchsia, purple, violet or blue, and
 * four-point sparkles, spinning / twinkling and fading (see the
 * client's MagicShapeParticle).
 *
 * @param scale    size factor (1 = a tenth of a block)
 * @param friction speed kept each tick (1 = flies straight on, 0.85 = a puff that slows down, 0 = stays put)
 * @param life     lifetime in ticks (0 = a random short one)
 * @param style    {@link #SHAPE} (random circle / triangle / square) or {@link #SPARKLE}
 * @param color    RGB tint, or {@link #RANDOM_COLOR} for one of the four spell colours
 */
public record MagicShapeEffect(float scale, float friction, int life, int style, int color) implements ParticleEffect {
    public static final int SHAPE = 0, SPARKLE = 1;
    public static final int RANDOM_COLOR = -1;
    /** The four spell colours, a fuchsia to blue gradient. */
    public static final int FUCHSIA = 0xF03CC8, PURPLE = 0xB848F0, VIOLET = 0x7B55F5, BLUE = 0x3D7BE8;
    public static final int[] COLORS = {FUCHSIA, PURPLE, VIOLET, BLUE};

    public static final MapCodec<MagicShapeEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.fieldOf("scale").forGetter(MagicShapeEffect::scale),
            Codec.FLOAT.fieldOf("friction").forGetter(MagicShapeEffect::friction),
            Codec.INT.fieldOf("life").forGetter(MagicShapeEffect::life),
            Codec.INT.fieldOf("style").forGetter(MagicShapeEffect::style),
            Codec.INT.fieldOf("color").forGetter(MagicShapeEffect::color)
    ).apply(instance, MagicShapeEffect::new));

    public static final PacketCodec<ByteBuf, MagicShapeEffect> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.FLOAT, MagicShapeEffect::scale,
            PacketCodecs.FLOAT, MagicShapeEffect::friction,
            PacketCodecs.VAR_INT, MagicShapeEffect::life,
            PacketCodecs.VAR_INT, MagicShapeEffect::style,
            PacketCodecs.INTEGER, MagicShapeEffect::color,
            MagicShapeEffect::new);

    /** A random shape of a random spell colour. */
    public static MagicShapeEffect shape(float scale, float friction, int life) {
        return new MagicShapeEffect(scale, friction, life, SHAPE, RANDOM_COLOR);
    }

    /** A sparkle of the given colour ({@link #RANDOM_COLOR} for a spell colour). */
    public static MagicShapeEffect sparkle(float scale, float friction, int life, int color) {
        return new MagicShapeEffect(scale, friction, life, SPARKLE, color);
    }

    @Override
    public ParticleType<?> getType() {
        return ModParticles.MAGIC_SHAPE;
    }
}
