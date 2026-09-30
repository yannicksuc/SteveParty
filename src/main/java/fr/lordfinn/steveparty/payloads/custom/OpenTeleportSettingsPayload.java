package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Server → client: opens the menu of a Teleport Cartridge, the one of the tile at {@code tile}, or (empty) the one in
 * the player's hand. Sent only to a player allowed to edit it (see TeleportCartridgeItem#openMenu).
 */
public record OpenTeleportSettingsPayload(Optional<BlockPos> tile) implements CustomPayload {
    public static final Id<OpenTeleportSettingsPayload> ID = new Id<>(Steveparty.id("open_teleport_settings"));
    public static final PacketCodec<RegistryByteBuf, OpenTeleportSettingsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.optional(BlockPos.PACKET_CODEC), OpenTeleportSettingsPayload::tile,
            OpenTeleportSettingsPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
