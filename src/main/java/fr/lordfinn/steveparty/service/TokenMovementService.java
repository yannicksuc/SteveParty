package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.TokenizedEntityInterface;
import fr.lordfinn.steveparty.blocks.tiles.Tile;
import fr.lordfinn.steveparty.blocks.tiles.TileDestination;
import fr.lordfinn.steveparty.blocks.tiles.TileEntity;
import fr.lordfinn.steveparty.events.DiceRollEvent;
import fr.lordfinn.steveparty.events.TileReachedEvent;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.*;
import org.joml.Vector3d;

import java.util.List;

import static fr.lordfinn.steveparty.Steveparty.SCHEDULER;

public class TokenMovementService {

    public TokenMovementService() {
        TileReachedEvent.EVENT.register((entity, tile) -> {
            if (entity.getWorld().isClient) return ActionResult.PASS;
            int nbSteps = ((TokenizedEntityInterface)entity).steveparty$getNbSteps();
            if (nbSteps == 0) return ActionResult.PASS;
            moveEntityOnBoard(entity, nbSteps - 1);
            return ActionResult.SUCCESS;
        });

        DiceRollEvent.EVENT.register((dice, ownerUUID, rollValue) -> {
            double distance = -1d;
            MobEntity chosenToken = null;
            ServerWorld world = (ServerWorld) dice.getWorld();
            if (world == null) return ActionResult.PASS;
            for (Entity entity :  world.iterateEntities()) {
                if (entity instanceof MobEntity token) {
                    if (((TokenizedEntityInterface)entity).steveparty$getTokenOwner() != null && ((TokenizedEntityInterface)entity).steveparty$getNbSteps() == 0) {
                        if (((TokenizedEntityInterface)entity).steveparty$getTokenOwner().equals(ownerUUID)) { //TODO Check for the current player when party as started
                            double newDistance = dice.getPos().distanceTo(entity.getPos());
                            if (distance == -1d || (newDistance < distance)) {
                                chosenToken = token;
                                distance = newDistance;
                            }
                        }
                    }
                }
            }
            if (chosenToken == null) return ActionResult.PASS;
            //Add small delay so the players can appreciate the value of the dice
            MobEntity finalChosenToken = chosenToken;
            SCHEDULER.schedule(chosenToken.getUuid(), 30, () -> moveEntityOnBoard(finalChosenToken, rollValue));
            return ActionResult.SUCCESS;
        });
    }

    private static Box getNearbyBox(ServerCommandSource source) {
        return Box.of(source.getPosition(), 100, 100, 100);
    }

    public static void moveEntityOnBoard(MobEntity mob, int rollNumber) {
        ((TokenizedEntityInterface) mob).steveparty$setNbSteps(rollNumber);
        if (rollNumber == 0) {
            MessageUtils.sendToNearby(mob.getServer(), mob.getPos(), 100,
                    Text.translatable("message.steveparty.arrived_at_destination", mob.getCustomName()),
                    MessageUtils.MessageType.ACTION_BAR);
            return;
        }
        TileEntity tileEntity = Tile.getTileEntity(mob.getWorld(), mob.getBlockPos());
        if (tileEntity == null) return;
        //SendMessageService.sendTokenMovementMessage(mob, rollNumber);

        MessageUtils.sendToNearby( mob.getServer(), mob.getPos(), 100,
                Text.translatable("message.steveparty.steps_remaining_for", rollNumber, mob.getCustomName())
                , MessageUtils.MessageType.ACTION_BAR);

        List<TileDestination> destinations = TileEntity.getCurrentDestinations(tileEntity).stream().filter(TileDestination::isTile).toList();
        if (destinations.size() > 1) {
            tileEntity.displayDestinations((ServerPlayerEntity) mob.getWorld().getPlayers().getFirst(), destinations);
        } else if (destinations.size() == 1) {
            TileDestination destination = destinations.getFirst();
            if (destination == null) return;
            moveEntity(mob, destination.position());
        }
    }

    private void sendMessageToPlayer(String message, PlayerEntity player) {
        if (player.getWorld().isClient)
            player.sendMessage(Text.of(message), false);
    }

    public static void moveEntityOnTileToDestination(ServerWorld world, BlockPos tileOrigin, TileDestination tileDestination) {
        if (tileDestination == null || tileOrigin == null) return;
        TileEntity tileEntity = Tile.getTileEntity(world, tileOrigin);
        if (tileEntity == null) return;
        tileEntity.hideDestinations();
        List<MobEntity> tokens = tileEntity.getTokensOnMe();
        MobEntity mob = null;
        for (MobEntity token : tokens) {
            if (((TokenizedEntityInterface)token).steveparty$getNbSteps() != 0) {
                mob = token;
                break;
            }
        }
        if (mob == null) return;
        ((TokenizedEntityInterface) mob).steveparty$setNbSteps(((TokenizedEntityInterface) mob).steveparty$getNbSteps());
        moveEntity(mob, tileDestination.position());
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
        mob.setVelocity(deltaX, deltaY, deltaZ);
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
}
