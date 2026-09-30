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
 * Tokenizer Wand magic, in the style of Kamek's spells (Yoshi's Island): small outlined circles, triangles and
 * squares in red, green, blue or yellow, and his four-point sparkles, spinning / twinkling and fading (see the
 * client's KamekShapeParticle).
 *
 * @param scale    size factor (1 = a tenth of a block)
 * @param friction speed kept each tick (1 = flies straight on, 0.85 = a puff that slows down, 0 = stays put)
 * @param life     lifetime in ticks (0 = a random short one)
 * @param style    {@link #SHAPE} (random circle / triangle / square) or {@link #SPARKLE}
 * @param color    RGB tint, or {@link #RANDOM_COLOR} for one of the four spell colours
 */
public record KamekShapeEffect(float scale, float friction, int life, int style, int color) implements ParticleEffect {
    public static final int SHAPE = 0, SPARKLE = 1;
    public static final int RANDOM_COLOR = -1;
    public static final int RED = 0xE8413C, GREEN = 0x4CC34A, BLUE = 0x3D7BE0, YELLOW = 0xF7D038;
    public static final int[] COLORS = {RED, GREEN, BLUE, YELLOW};

    public static final MapCodec<KamekShapeEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.fieldOf("scale").forGetter(KamekShapeEffect::scale),
            Codec.FLOAT.fieldOf("friction").forGetter(KamekShapeEffect::friction),
            Codec.INT.fieldOf("life").forGetter(KamekShapeEffect::life),
            Codec.INT.fieldOf("style").forGetter(KamekShapeEffect::style),
            Codec.INT.fieldOf("color").forGetter(KamekShapeEffect::color)
    ).apply(instance, KamekShapeEffect::new));

    public static final PacketCodec<ByteBuf, KamekShapeEffect> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.FLOAT, KamekShapeEffect::scale,
            PacketCodecs.FLOAT, KamekShapeEffect::friction,
            PacketCodecs.VAR_INT, KamekShapeEffect::life,
            PacketCodecs.VAR_INT, KamekShapeEffect::style,
            PacketCodecs.INTEGER, KamekShapeEffect::color,
            KamekShapeEffect::new);

    /** A random shape of a random spell colour. */
    public static KamekShapeEffect shape(float scale, float friction, int life) {
        return new KamekShapeEffect(scale, friction, life, SHAPE, RANDOM_COLOR);
    }

    /** A sparkle of the given colour ({@link #RANDOM_COLOR} for a spell colour). */
    public static KamekShapeEffect sparkle(float scale, float friction, int life, int color) {
        return new KamekShapeEffect(scale, friction, life, SPARKLE, color);
    }

    @Override
    public ParticleType<?> getType() {
        return ModParticles.KAMEK_SHAPE;
    }
}
