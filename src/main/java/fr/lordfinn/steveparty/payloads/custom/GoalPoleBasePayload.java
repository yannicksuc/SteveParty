package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity;
import fr.lordfinn.steveparty.payloads.ServerboundPayload;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import fr.lordfinn.steveparty.screen_handlers.custom.GoalPoleBaseScreenHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * The goal pole base's settings: sent to the screen when it opens, and back to the server when the player validates
 * (see {@code GoalPoleBaseBlockEntity#writeSettings} / {@code applySettings}, which checks every value).
 */
public record GoalPoleBasePayload(BlockPos pos, NbtCompound settings) implements ServerboundPayload {
    public static final CustomPayload.Id<GoalPoleBasePayload> ID = new CustomPayload.Id<>(Steveparty.id("goal_pole_base"));
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

    /**
     * The settings of a goal pole base sent back by its screen. The screen opens for anyone holding a Wrench (to read
     * them), but only a player who may build there changes them: they reset the points of a party going on and pick
     * the players it counts.
     */
    @Override
    public void handle(ServerPlayerEntity player) {
        if (settings == null) return;
        // The goal pole base screen for this block must be open and the block in reach
        if (!(player.currentScreenHandler instanceof GoalPoleBaseScreenHandler handler)
                || !pos.equals(handler.getPos())
                || !ScreenHandlerChecks.isInReach(player, pos)
                || !ScreenHandlerChecks.canBuildAt(player, pos)) return;
        // Each setting is checked by the base (known values, string lengths)
        if (player.getWorld().getBlockEntity(pos) instanceof GoalPoleBaseBlockEntity blockEntity) {
            blockEntity.applySettings(settings);
        }
    }

}
