package fr.lordfinn.steveparty.particles;

import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModParticles {
    public static final SimpleParticleType HERE_PARTICLE = FabricParticleTypes.simple();
    public static final SimpleParticleType ARROW_PARTICLE = FabricParticleTypes.simple();

    public static void initialize() {
        Registry.register(Registries.PARTICLE_TYPE, Identifier.of(Steveparty.MOD_ID, "here"),
                HERE_PARTICLE);
        Registry.register(Registries.PARTICLE_TYPE, Identifier.of(Steveparty.MOD_ID, "arrow"),
                ARROW_PARTICLE);
    }
}
