package fr.lordfinn.steveparty.payloads;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.BiConsumer;

/** One-line registration of the mod's play payloads: their type, and for the client → server ones, their receiver. */
public final class Payloads {
    private Payloads() {
    }

    /** A server → client payload (its receiver is registered on the client). */
    public static <T extends CustomPayload> void s2c(CustomPayload.Id<T> id, PacketCodec<? super RegistryByteBuf, T> codec) {
        PayloadTypeRegistry.playS2C().register(id, codec);
    }

    /** A client → server payload handled by its own {@link ServerboundPayload#handle}. */
    public static <T extends ServerboundPayload> void c2s(CustomPayload.Id<T> id, PacketCodec<? super RegistryByteBuf, T> codec) {
        c2s(id, codec, (player, payload) -> payload.handle(player));
    }

    /** A client → server payload handled by {@code handler(player, payload)}, in packet order ({@link #runInPacketOrder}). */
    public static <T extends CustomPayload> void c2s(CustomPayload.Id<T> id, PacketCodec<? super RegistryByteBuf, T> codec,
                                                     BiConsumer<ServerPlayerEntity, ? super T> handler) {
        PayloadTypeRegistry.playC2S().register(id, codec);
        ServerPlayNetworking.registerGlobalReceiver(id, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            runInPacketOrder(player, () -> handler.accept(player, payload));
        });
    }

    /**
     * Runs a C2S payload's action right away when Fabric already calls the receiver on the server thread (it does for
     * play payloads), so that it is handled in packet order. Deferring it with {@code server.execute} queued it behind
     * the packets received with it: a screen that sends its settings then closes had its close packet handled first,
     * and the "screen must be open" check then dropped the settings (the goal pole screens often did not save).
     */
    public static void runInPacketOrder(ServerPlayerEntity player, Runnable action) {
        if (player.server.isOnThread()) action.run();
        else player.server.execute(action);
    }
}
