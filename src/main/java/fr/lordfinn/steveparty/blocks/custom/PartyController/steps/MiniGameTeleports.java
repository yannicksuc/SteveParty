package fr.lordfinn.steveparty.blocks.custom.PartyController.steps;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Set;

/**
 * Teleports of a mini-game: the players, who came out of the page's pipes, are brought back where they stood
 * at the end.
 */
public final class MiniGameTeleports {
    private MiniGameTeleports() {}

    public static void teleport(ServerPlayerEntity player, ServerWorld world, Vec3d target, float yaw, float pitch) {
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_TELEPORT, SoundCategory.PLAYERS, 0.6f, 1.0f);
        player.setCameraEntity(player);
        player.teleport(world, target.x, target.y, target.z, Set.of(), yaw, pitch);
        player.fallDistance = 0;
        world.playSound(null, BlockPos.ofFloored(target), SoundEvents.ENTITY_PLAYER_TELEPORT, SoundCategory.PLAYERS, 0.6f, 1.2f);
    }
}
