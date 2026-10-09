package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.jumpshoes.JumpShoesServer;
import fr.lordfinn.steveparty.payloads.ServerboundPayload;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The Triple Jump Shoes' moves: the client reports each one it makes ({@link Move}: a jump of the chain, a double
 * jump, a wall slide starting or stopping, a wall kick), the server checks it and relays it ({@link Seen}) to the
 * players around, who show its effects. One small packet per move, nothing while nothing happens.
 */
public final class JumpShoesPayloads {
    private JumpShoesPayloads() {
    }

    /** {@code action}: a JumpShoes.Action; {@code side}: the wall's Direction id, or -1. */
    public record Move(byte action, byte side) implements ServerboundPayload {
        public static final CustomPayload.Id<Move> ID = new CustomPayload.Id<>(Steveparty.id("jump_shoes_move"));
        public static final PacketCodec<RegistryByteBuf, Move> CODEC = PacketCodec.tuple(
                PacketCodecs.BYTE, Move::action, PacketCodecs.BYTE, Move::side, Move::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        @Override
        public void handle(ServerPlayerEntity player) {
            JumpShoesServer.onMove(player, action, side);
        }
    }

    /** The move {@code action} of the player {@code entityId}, to show. */
    public record Seen(int entityId, byte action, byte side) implements CustomPayload {
        public static final CustomPayload.Id<Seen> ID = new CustomPayload.Id<>(Steveparty.id("jump_shoes_seen"));
        public static final PacketCodec<RegistryByteBuf, Seen> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, Seen::entityId, PacketCodecs.BYTE, Seen::action, PacketCodecs.BYTE, Seen::side, Seen::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
