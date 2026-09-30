package fr.lordfinn.steveparty.payloads.custom;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import static fr.lordfinn.steveparty.payloads.ModPayloads.GOAL_POLE_BASE_PAYLOAD;

/**
 * The goal pole base's settings: sent to the screen when it opens, and back to the server when the player validates
 * (see {@code GoalPoleBaseBlockEntity#writeSettings} / {@code applySettings}, which checks every value).
 */
public record GoalPoleBasePayload(BlockPos pos, NbtCompound settings) implements CustomPayload {
    public static final CustomPayload.Id<GoalPoleBasePayload> ID = new CustomPayload.Id<>(GOAL_POLE_BASE_PAYLOAD);
    /** A few short strings and enum names: 16 KiB is plenty, and bounds what a client can send. */
    private static final long MAX_SETTINGS_BYTES = 16 * 1024;

    public static final PacketCodec<RegistryByteBuf, GoalPoleBasePayload> CODEC =
            PacketCodec.tuple(
                    BlockPos.PACKET_CODEC, GoalPoleBasePayload::pos,
                    PacketCodecs.nbtCompound(() -> new NbtSizeTracker(MAX_SETTINGS_BYTES, 16)), GoalPoleBasePayload::settings,
                    GoalPoleBasePayload::new
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
