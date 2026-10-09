package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBlockEntity;
import fr.lordfinn.steveparty.payloads.ServerboundPayload;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import fr.lordfinn.steveparty.screen_handlers.custom.GoalPoleScreenHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * The goal of a pole segment, whether each segment has its own goal, whether the flags step down point by point, and
 * whose points the goal is about ({@link GoalPoleBlockEntity.Count}): to and from the pole screen.
 */
public record GoalPolePayload(BlockPos pos, GoalPoleBlockEntity.Comparator comparator, int value, boolean perSegment,
                              boolean flagSteps, GoalPoleBlockEntity.Count count) implements ServerboundPayload {
    public static final CustomPayload.Id<GoalPolePayload> ID = new CustomPayload.Id<>(Steveparty.id("goal_pole"));
    public static final PacketCodec<ByteBuf, GoalPoleBlockEntity.Comparator> COMPARATOR_CODEC =
            new PacketCodec<>() {
                @Override
                public GoalPoleBlockEntity.Comparator decode(ByteBuf buf) {
                    int i = buf.readInt();
                    GoalPoleBlockEntity.Comparator[] values = GoalPoleBlockEntity.Comparator.values();
                    // Never trust the index sent by the client
                    return values[Math.clamp(i, 0, values.length - 1)];
                }

                @Override
                public void encode(ByteBuf buf, GoalPoleBlockEntity.Comparator comp) {
                    buf.writeInt(comp.ordinal());
                }
            };
    public static final PacketCodec<ByteBuf, GoalPoleBlockEntity.Count> COUNT_CODEC = PacketCodecs.BYTE.xmap(
            // Never trust the index sent by the client
            i -> GoalPoleBlockEntity.Count.values()[Math.clamp(i, 0, GoalPoleBlockEntity.Count.values().length - 1)],
            count -> (byte) count.ordinal());
    public static final PacketCodec<RegistryByteBuf, GoalPolePayload> CODEC =
            PacketCodec.tuple(
                    BlockPos.PACKET_CODEC, GoalPolePayload::pos,
                    COMPARATOR_CODEC, GoalPolePayload::comparator,
                    PacketCodecs.INTEGER, GoalPolePayload::value,
                    PacketCodecs.BOOL, GoalPolePayload::perSegment,
                    PacketCodecs.BOOL, GoalPolePayload::flagSteps,
                    COUNT_CODEC, GoalPolePayload::count,
                    GoalPolePayload::new
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** The goal of a goal pole sent back by its screen: like the base, read by anyone, changed by a builder. */
    @Override
    public void handle(ServerPlayerEntity player) {
        if (comparator == null) return;
        // The goal pole screen for this block must be open and the block in reach
        if (!(player.currentScreenHandler instanceof GoalPoleScreenHandler handler)
                || !pos.equals(handler.getPos())
                || !ScreenHandlerChecks.isInReach(player, pos)
                || !ScreenHandlerChecks.canBuildAt(player, pos)) return;
        if (player.getWorld().getBlockEntity(pos) instanceof GoalPoleBlockEntity blockEntity) {
            blockEntity.applyGoal(comparator, value, perSegment);
            blockEntity.applyFlagSteps(flagSteps);
            if (count != null) blockEntity.applyCount(count);
        }
    }

}
