package fr.lordfinn.steveparty.client.payloads;

import fr.lordfinn.steveparty.client.PartyService;
import fr.lordfinn.steveparty.payloads.ArrowParticlesPayload;
import fr.lordfinn.steveparty.payloads.TokenPayload;
import fr.lordfinn.steveparty.service.TokenData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.Map;
import java.util.UUID;

import static fr.lordfinn.steveparty.particles.ModParticles.ARROW_PARTICLE;

public class PayloadReceivers {
    public static void initialize() {

        // Used to spawn arrow particles from the server
        ClientPlayNetworking.registerGlobalReceiver(ArrowParticlesPayload.ID,
                (payload, context) -> context.client().execute(() ->
                context.player().getWorld().addImportantParticle(ARROW_PARTICLE,
                payload.position().x, payload.position().y, payload.position().z,
                payload.velocity().x, payload.velocity().y, payload.velocity().z)));

        ClientPlayNetworking.registerGlobalReceiver(TokenPayload.ID, (payload, context)  -> context.client().execute(() ->
        {
            Map<UUID, TokenData> tokens = payload.tokens();
            PartyService.tokens.putAll(tokens);
        }));
    }
}
