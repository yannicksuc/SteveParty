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
    public static final SimpleParticleType FLOATING_TEXT_PARTICLE = FabricParticleTypes.simple();

    public static void initialize() {
        Registry.register(Registries.PARTICLE_TYPE, Steveparty.id("here"),
                HERE_PARTICLE);
        Registry.register(Registries.PARTICLE_TYPE, Steveparty.id("arrow"),
                ARROW_PARTICLE);
        Registry.register(Registries.PARTICLE_TYPE, Steveparty.id("enchanted_circular"),
                ENCHANTED_CIRCULAR_PARTICLE);
        Registry.register(Registries.PARTICLE_TYPE, Steveparty.id("floating_text"),
                FLOATING_TEXT_PARTICLE);
    }
}
