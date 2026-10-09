package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.service.TileInfos;
import fr.lordfinn.steveparty.payloads.ServerboundPayload;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** The board spaces' game info (see {@link TileInfos}): asked by the client for what it shows, sent when it changed. */
public final class TileInfoPayloads {
    private TileInfoPayloads() {
    }

    /**
     * Client → server: the info of these spaces, please (the ones the client shows now; at most
     * {@link TileInfos#MAX_ASKED}). {@code fresh}: the client has nothing yet (it joined, changed world), send all.
     */
    public record Request(boolean fresh, List<BlockPos> spaces) implements ServerboundPayload {
        public static final Id<Request> ID = new Id<>(Steveparty.id("tile_info_request"));
        public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.tuple(
                PacketCodecs.BOOL, Request::fresh,
                BlockPos.PACKET_CODEC.collect(PacketCodecs.toList(TileInfos.MAX_ASKED)), Request::spaces, Request::new);

        @Override
        public void handle(ServerPlayerEntity player) {
            if (fresh) TileInfos.forget(player);
            TileInfos.onRequest(player, spaces);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Server → client: the info of a space (empty: it has nothing to tell). */
    public record Info(BlockPos space, TileInfo info) implements CustomPayload {
        public static final Id<Info> ID = new Id<>(Steveparty.id("tile_info"));
        public static final PacketCodec<RegistryByteBuf, Info> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC, Info::space, TileInfo.PACKET_CODEC, Info::info, Info::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Server → client: where the player's own token is heading (the space it will stop on, or at a fork where each
     * branch leads), its info shown without looking at it. Empty: no move.
     */
    public record Focus(List<BlockPos> spaces) implements CustomPayload {
        public static final Id<Focus> ID = new Id<>(Steveparty.id("tile_info_focus"));
        public static final PacketCodec<RegistryByteBuf, Focus> CODEC = PacketCodec.tuple(
                BlockPos.PACKET_CODEC.collect(PacketCodecs.toList(TileInfos.MAX_ASKED)), Focus::spaces, Focus::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
