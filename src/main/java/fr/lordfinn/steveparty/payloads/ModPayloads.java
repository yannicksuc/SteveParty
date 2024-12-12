package fr.lordfinn.steveparty.payloads;

import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.util.Identifier;

public class ModPayloads {
    public static final Identifier ARROW_PARTICLES_PAYLOAD = Identifier.of(Steveparty.MOD_ID, "arrow-particles");
    public static final Identifier TOKENS_PAYLOAD = Identifier.of(Steveparty.MOD_ID, "tokens-payload");
    public static final Identifier ENCHANTED_CIRCULAR_PAYLOAD = Identifier.of(Steveparty.MOD_ID, "enchanted-circular-particles-payload");

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(ArrowParticlesPayload.ID, ArrowParticlesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TokenPayload.ID, TokenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EnchantedCircularParticlePayload.ID, EnchantedCircularParticlePayload.CODEC);
    }
}
