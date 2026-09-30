package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.Set;

/**
 * Teleports of a mini-game: the players are sent onto the arrival pads the « Here we go » books would pick
 * (see {@code HereWeGoBookItem#getMiniGameDestination}), and brought back where they stood at the end.
 */
public final class MiniGameTeleports {
    private MiniGameTeleports() {}

    /** Position standing on top of the block at {@code pos} (a pad is lower than a full block). */
    public static Vec3d standingPos(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(world, pos);
        double height = shape.isEmpty() ? 0 : shape.getMax(Direction.Axis.Y);
        return new Vec3d(pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5);
    }

    public static void teleport(ServerPlayerEntity player, ServerWorld world, Vec3d target, float yaw, float pitch) {
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_TELEPORT, SoundCategory.PLAYERS, 0.6f, 1.0f);
        player.teleport(world, target.x, target.y, target.z, Set.of(), yaw, pitch, true);
        player.fallDistance = 0;
        world.playSound(null, BlockPos.ofFloored(target), SoundEvents.ENTITY_PLAYER_TELEPORT, SoundCategory.PLAYERS, 0.6f, 1.2f);
    }
}
