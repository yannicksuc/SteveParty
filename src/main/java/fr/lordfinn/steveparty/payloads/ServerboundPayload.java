package fr.lordfinn.steveparty.payloads;

import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/** A client → server payload that knows what to do once received (registered with {@link Payloads#c2s}). */
public interface ServerboundPayload extends CustomPayload {
    /** Handles the payload on the server thread, in packet order. Every check on what the client asks is done here. */
    void handle(ServerPlayerEntity player);
}
