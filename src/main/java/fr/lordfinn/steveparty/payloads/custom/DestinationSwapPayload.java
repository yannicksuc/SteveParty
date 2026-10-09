package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.board.DestinationSwap;
import fr.lordfinn.steveparty.payloads.ServerboundPayload;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Client → server: what the player chose for a cartridge clicked on another one (see {@link DestinationSwap}), sent
 * on joining and whenever it changes.
 */
public record DestinationSwapPayload(int preference) implements ServerboundPayload {
    public static final Id<DestinationSwapPayload> ID = new Id<>(Steveparty.id("destination_swap"));
    public static final PacketCodec<RegistryByteBuf, DestinationSwapPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, DestinationSwapPayload::preference,
            DestinationSwapPayload::new);

    public DestinationSwapPayload(DestinationSwap.Preference preference) {
        this(preference.ordinal());
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    @Override
    public void handle(ServerPlayerEntity player) {
        DestinationSwap.setPreference(player, DestinationSwap.Preference.of(preference));
    }
}
