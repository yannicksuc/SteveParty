package fr.lordfinn.steveparty.payloads;

import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.util.Identifier;

public class ModPayloads {
    public static final Identifier ARROW_PARTICLES_PAYLOAD = Identifier.of(Steveparty.MOD_ID, "arrow-particles");

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(ArrowParticlesPayload.ID, ArrowParticlesPayload.CODEC);
    }
}
