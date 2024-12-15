package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.TokenizedEntityInterface;
import fr.lordfinn.steveparty.blocks.tiles.Tile;
import fr.lordfinn.steveparty.blocks.tiles.TileDestination;
import fr.lordfinn.steveparty.blocks.tiles.TileEntity;
import fr.lordfinn.steveparty.events.TileReachedEvent;
import fr.lordfinn.steveparty.particles.ParticleUtils;
import fr.lordfinn.steveparty.payloads.ArrowParticlesPayload;
import fr.lordfinn.steveparty.utils.TaskScheduler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.awt.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

public class TokenMovementService {

    public TokenMovementService() {
        TileReachedEvent.EVENT.register((entity, tile) -> {
            if (entity.getWorld().isClient) return ActionResult.PASS;
            int nbSteps = ((TokenizedEntityInterface)entity).steveparty$getNbSteps();
            if (nbSteps == 0) return ActionResult.PASS;
            moveEntityOnBoard(entity, nbSteps);
            return ActionResult.SUCCESS;
        });
    }

    public static void moveEntityOnBoard(MobEntity mob, int rollNumber) {
        if (rollNumber == 0) return;
        TileEntity tileEntity = Tile.getTileEntity(mob.getWorld(), mob.getBlockPos());
        if (tileEntity == null) return;

        List<TileDestination> destinations = TileEntity.getCurrentDestinations(tileEntity).stream().filter(TileDestination::isTile).toList();
        if (destinations.size() > 1) {
            Steveparty.LOGGER.info("Multiple destinations");
            displayDestinations(destinations, mob.getBlockPos(), null,
                    mob.getWorld(), () -> mob.getWorld().getPlayers().stream().anyMatch(player -> isWithinDistance(mob.getBlockPos(), player)),
                    null);
        } else if (destinations.size() == 1) {
            TileDestination destination = destinations.getFirst();
            Steveparty.LOGGER.info("Destination: {}", destination);
            if (destination == null) return;
            Steveparty.LOGGER.info("Destination position: {}", destination.position());
            ((TokenizedEntityInterface) mob).steveparty$setNbSteps(rollNumber - 1);
            moveEntity(mob, destination.position()); //TODO Detecter quand la prochaine tuile est atteinte
        }
    }

    public static void moveEntity(MobEntity mob, BlockPos target) {
        Vector3d preciseTargetPos = calculateTargetPosition(mob, target);
        double distance = mob.squaredDistanceTo(preciseTargetPos.x(), preciseTargetPos.y(), preciseTargetPos.z());

        if (isTooFar(distance)) {
            teleportEntity(mob, target, preciseTargetPos);
        } else {
            moveEntityToTarget(mob, preciseTargetPos);
            playSound(mob, target, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP);
        }
    }

    private static Vector3d calculateTargetPosition(MobEntity mob, BlockPos targetPos) {
        BlockState blockState = mob.getWorld().getBlockState(targetPos);
        double blockHeight = blockState.getCollisionShape(mob.getWorld(), targetPos).getMax(Direction.Axis.Y);
        return new Vector3d(targetPos.getX() + 0.5, targetPos.getY() + blockHeight, targetPos.getZ() + 0.5);
    }

    private static boolean isTooFar(double distance) {
        return distance > 2500;
    }

    private static void teleportEntity(MobEntity mob, BlockPos targetPos, Vector3d preciseTargetPos) {
        mob.setPosition(preciseTargetPos.x(), preciseTargetPos.y(), preciseTargetPos.z());
        playSound(mob, targetPos, SoundEvents.ENTITY_ENDERMAN_TELEPORT);
    }

    private static void moveEntityToTarget(MobEntity mob, Vector3d target) {
        // Set the target position (if applicable to your custom interface)
        if (mob instanceof TokenizedEntityInterface tokenizedEntity) {
            Steveparty.LOGGER.info("New target: {}", target);
            tokenizedEntity.steveparty$setTargetPosition(target, 0.5);
        }

        // Calculate rotation
        double deltaX = target.x() - mob.getX();
        double deltaZ = target.z() - mob.getZ();
        float yaw = (float) (Math.atan2(deltaZ, deltaX) * (180 / Math.PI)) - 90; // Convert radians to degrees
        mob.setYaw(yaw);

        // Optionally update pitch for vertical rotation
        double deltaY = target.y() - mob.getY();
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float pitch = (float) -(Math.atan2(deltaY, horizontalDistance) * (180 / Math.PI)); // Convert radians to degrees
        mob.setPitch(pitch);
    }

    private static void playSound(MobEntity mob, BlockPos targetPos, SoundEvent soundEvent) {
        mob.getWorld().playSound(
                null, // Null plays sound to all nearby players
                targetPos,
                soundEvent,
                SoundCategory.PLAYERS,
                100,
                1.0F
        );
    }

    public static void displayDestinations(List<TileDestination> destinations, BlockPos origin, List<ServerPlayerEntity> players, World world, Callable<Boolean> condition, Runnable lastCallback) {
        if (destinations.isEmpty()) return;
        if (world.isClient) return;
        for (TileDestination destination : destinations) {
            // Summon the particle at each position for the player
            BlockPos offset = destination.position().subtract(origin);
            Vector3f normalizedOffset = new Vector3f(offset.getX(), offset.getY(), offset.getZ()).normalize().mul(1.5f);

            Color color = destination.isTile() ? Color.WHITE : Color.RED;

            Vec3d encodedVelocity = ParticleUtils.encodeVelocity(
                    color,
                    offset.getX() - (normalizedOffset.x() * 2),
                    offset.getY() - (normalizedOffset.y() * 2),
                    offset.getZ() - (normalizedOffset.z() * 2));
            if (players == null || players.isEmpty()) {
                scheduleDestinationRendering(
                        () -> world.getPlayers().stream().filter(player -> isWithinDistance(origin, player))
                                .forEach(player -> renderDirection(origin, (ServerPlayerEntity) player, normalizedOffset, encodedVelocity)),
                        condition, lastCallback);
            }
            else
                scheduleDestinationRendering(() -> players.forEach(player -> renderDirection(origin, player, normalizedOffset, encodedVelocity)), condition, lastCallback);
        }
    }

    private static void scheduleDestinationRendering(Runnable render, Callable<Boolean> condition, Runnable lastCallback) {
        Steveparty.SCHEDULER.repeat(UUID.randomUUID(), 15, render, condition, lastCallback);
    }

    private static boolean isWithinDistance(BlockPos origin, PlayerEntity player) {
        return player.getBlockPos().isWithinDistance(origin.toCenterPos(), 15);
    }

    private static void renderDirection(BlockPos origin, ServerPlayerEntity player, Vector3f normalizedOffset, Vec3d encodedVelocity) {
        ServerPlayNetworking.send(player, new ArrowParticlesPayload(new Vec3d(origin.getX() + 0.5 + normalizedOffset.x(),
                origin.getY() + 0.6 + normalizedOffset.y(),
                origin.getZ() + 0.5 + normalizedOffset.z()), encodedVelocity));
    }
}
