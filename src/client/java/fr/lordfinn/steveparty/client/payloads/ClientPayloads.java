package fr.lordfinn.steveparty.client.payloads;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.packet.CustomPayload;

import java.util.function.BiConsumer;

/** One-line registration of the client's receivers for the server → client payloads (see Payloads#s2c). */
public final class ClientPayloads {
    private ClientPayloads() {
    }

    /** Handles {@code id} on the client thread, through {@code client.execute}. */
    public static <T extends CustomPayload> void receive(CustomPayload.Id<T> id, BiConsumer<T, ClientPlayNetworking.Context> handler) {
        ClientPlayNetworking.registerGlobalReceiver(id, (payload, context) -> context.client().execute(() -> handler.accept(payload, context)));
    }
}
