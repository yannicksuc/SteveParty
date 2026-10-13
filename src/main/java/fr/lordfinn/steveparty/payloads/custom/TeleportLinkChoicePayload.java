package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.payloads.ServerboundPayload;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * The Tile Linker Brush painted from a Teleport tile to a board space it has no link to: S2C, the player is asked
 * « TP » or « link » ({@code teleport} unused); C2S, the answer ({@code teleport}: « TP »), see
 * {@link TileLinkerBrush#answerTeleportOrLink}.
 */
public record TeleportLinkChoicePayload(BlockPos from, BlockPos to, boolean teleport) implements ServerboundPayload {
    public static final CustomPayload.Id<TeleportLinkChoicePayload> ID = new CustomPayload.Id<>(Steveparty.id("teleport_link_choice"));
    public static final PacketCodec<RegistryByteBuf, TeleportLinkChoicePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, TeleportLinkChoicePayload::from,
            BlockPos.PACKET_CODEC, TeleportLinkChoicePayload::to,
            PacketCodecs.BOOL, TeleportLinkChoicePayload::teleport,
            TeleportLinkChoicePayload::new);

    @Override
    public void handle(ServerPlayerEntity player) {
        TileLinkerBrush.answerTeleportOrLink(player, from, to, teleport);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
