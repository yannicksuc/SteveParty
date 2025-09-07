package fr.lordfinn.steveparty.payloads.custom;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import static fr.lordfinn.steveparty.payloads.ModPayloads.GOAL_POLE_BASE_PAYLOAD;

public record GoalPoleBasePayload(BlockPos pos, String selector, String goal) implements CustomPayload {
    public static final CustomPayload.Id<GoalPoleBasePayload> ID = new CustomPayload.Id<>(GOAL_POLE_BASE_PAYLOAD);

    public static final PacketCodec<RegistryByteBuf, GoalPoleBasePayload> CODEC =
            PacketCodec.tuple(
                    BlockPos.PACKET_CODEC, GoalPoleBasePayload::pos,
                    PacketCodecs.STRING, GoalPoleBasePayload::selector,
                    PacketCodecs.STRING, GoalPoleBasePayload::goal,
                    GoalPoleBasePayload::new
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
