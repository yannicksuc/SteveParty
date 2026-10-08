package fr.lordfinn.steveparty.particles;

import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModParticles {
    public static final SimpleParticleType HERE_PARTICLE = FabricParticleTypes.simple();
    public static final SimpleParticleType ARROW_PARTICLE = FabricParticleTypes.simple();
    public static final SimpleParticleType ENCHANTED_CIRCULAR_PARTICLE = FabricParticleTypes.simple();
    /** End rod sparkle keeping its speed: the dice forge laser. */
    public static final SimpleParticleType FORGE_BEAM = FabricParticleTypes.simple();
    /** The Mula's tinted twinkles, star bits and sleepy z's (client-side animation effects). */
    public static final ParticleType<MulaSparkleEffect> MULA_SPARKLE =
            FabricParticleTypes.complex(MulaSparkleEffect.CODEC, MulaSparkleEffect.PACKET_CODEC);
    /** The Tokenizer Wand's magic: coloured circles, triangles and squares. */
    public static final ParticleType<MagicShapeEffect> MAGIC_SHAPE =
            FabricParticleTypes.complex(MagicShapeEffect.CODEC, MagicShapeEffect.PACKET_CODEC);
    /** Star fragments blocks' solar eruptions: a flame blob arcing around the block (client display only). */
    public static final ParticleType<StarFlareEffect> STAR_FLARE =
            FabricParticleTypes.complex(StarFlareEffect.CODEC, StarFlareEffect.PACKET_CODEC);

    public static void initialize() {
        Registry.register(Registries.PARTICLE_TYPE, Steveparty.id("here"),
                HERE_PARTICLE);
        Registry.register(Registries.PARTICLE_TYPE, Steveparty.id("arrow"),
                ARROW_PARTICLE);
        Registry.register(Registries.PARTICLE_TYPE, Steveparty.id("enchanted_circular"),
                ENCHANTED_CIRCULAR_PARTICLE);
        Registry.register(Registries.PARTICLE_TYPE, Steveparty.id("forge_beam"), FORGE_BEAM);
        Registry.register(Registries.PARTICLE_TYPE, Steveparty.id("mula_sparkle"), MULA_SPARKLE);
        Registry.register(Registries.PARTICLE_TYPE, Steveparty.id("magic_shape"), MAGIC_SHAPE);
        Registry.register(Registries.PARTICLE_TYPE, Steveparty.id("star_flare"), STAR_FLARE);
    }
}
