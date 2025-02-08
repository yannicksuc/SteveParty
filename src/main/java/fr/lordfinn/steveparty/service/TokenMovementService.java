package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceDestination;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.events.DiceRollEvent;
import fr.lordfinn.steveparty.events.TileReachedEvent;
import fr.lordfinn.steveparty.events.TileUpdatedEvent;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static fr.lordfinn.steveparty.Steveparty.SCHEDULER;

public class TokenMovementService {

    public TokenMovementService() {
        TileReachedEvent.EVENT.register(TokenMovementService::tryToMoveEntityOnBoard);
        TileUpdatedEvent.EVENT.register(TokenMovementService::tryToMoveEntityOnBoard);

        DiceRollEvent.EVENT.register(this::handleDiceRoll);
    }

    private ActionResult handleDiceRoll(DiceEntity dice, UUID ownerUUID, int rollValue) {
        ServerWorld world = (ServerWorld) dice.getWorld();
        if (world == null) return ActionResult.PASS;

        MobEntity chosenToken = getTargetedToken(world, dice, ownerUUID);
        if (chosenToken == null) return ActionResult.PASS;

        SCHEDULER.schedule(chosenToken.getUuid(), 30, () -> moveEntityOnBoard(chosenToken, rollValue));
        return ActionResult.SUCCESS;
    }

    private MobEntity getTargetedToken(ServerWorld world, DiceEntity dice, UUID ownerUUID) {
        List<MobEntity> chosenTokens = getEligibleTokens(world, dice, ownerUUID);
        if (chosenTokens.isEmpty()) return null;

        sortTokens(chosenTokens, dice);

        // Add small delay so players can appreciate the dice roll value
        return chosenTokens.getFirst();
    }

    private List<MobEntity> getEligibleTokens(ServerWorld world, DiceEntity dice, UUID ownerUUID) {
        List<MobEntity> eligibleTokens = new ArrayList<>();
        for (MobEntity token : world.getEntitiesByClass(MobEntity.class,
                Box.of(dice.getPos(), 50, 50, 50),
                entity -> ((TokenizedEntityInterface) entity).steveparty$isTokenized())) {

            TokenizedEntityInterface tokenInterface = (TokenizedEntityInterface) token;
            UUID tokenOwnerUUID = tokenInterface.steveparty$getTokenOwner();
            if (tokenOwnerUUID != null
                    && tokenInterface.steveparty$getNbSteps() == 0
                    && isTokenEligible(tokenInterface, ownerUUID)) {
                eligibleTokens.add(token);
            }
        }
        return eligibleTokens;
    }

    private boolean isTokenEligible(TokenizedEntityInterface token, UUID ownerUUID) {
        int status = token.steveparty$getStatus();
        boolean isOwnedByOwner = token.steveparty$getTokenOwner().equals(ownerUUID);

        return (isOwnedByOwner && TokenStatus.canMoveInGame(status)) || !TokenStatus.isInGame(status);
    }

    private void sortTokens(List<MobEntity> tokens, DiceEntity dice) {
        // Sort tokens by distance to dice and then by status (IN_GAME_CAN_MOVE are first and OUT_OF_GAME_xxx are last)
        tokens.sort(
                Comparator
                        .<MobEntity>comparingInt(token -> TokenStatus.canMoveInGame(((TokenizedEntityInterface) token).steveparty$getStatus())  ? 1 : -1)
                        .thenComparingDouble(token -> token.getPos().distanceTo(dice.getPos()))
        );
    }

    private static @NotNull ActionResult tryToMoveEntityOnBoard(MobEntity entity, BoardSpaceBlockEntity tile) {
        if (entity.getWorld().isClient) return ActionResult.PASS;
        int nbSteps = ((TokenizedEntityInterface) entity).steveparty$getNbSteps();
        if (nbSteps == 0) return ActionResult.PASS;

        ABoardSpaceBehavior behavior = tile.getBoardSpaceBehavior();
        if (behavior == null || !behavior.needToStop(entity.getWorld(), tile.getPos())) {
            moveEntityOnBoard(entity, nbSteps);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    public static void moveEntityOnBoard(MobEntity mob, int rollNumber) {
        ((TokenizedEntityInterface) mob).steveparty$setNbSteps(rollNumber);
        if (rollNumber == 0) {
            MessageUtils.sendToNearby(mob.getServer(), mob.getPos(), 100,
                    Text.translatable("message.steveparty.arrived_at_destination", mob.getCustomName() != null ? mob.getCustomName() : mob.getName()),
                    MessageUtils.MessageType.ACTION_BAR);
            return;
        }
        BoardSpaceBlockEntity tileEntity = ABoardSpaceBlock.getBoardSpaceEntity(mob.getWorld(), mob.getBlockPos());
        if (tileEntity == null) return;
        //SendMessageService.sendTokenMovementMessage(mob, rollNumber);

        MessageUtils.sendToNearby( mob.getServer(), mob.getPos(), 100,
                Text.translatable("message.steveparty.steps_remaining_for", rollNumber, mob.getCustomName() != null ? mob.getCustomName() : mob.getName())
                , MessageUtils.MessageType.ACTION_BAR);

        List<BoardSpaceDestination> destinations = tileEntity.getStockedDestinations().stream().filter(BoardSpaceDestination::isTile).toList();
        if (destinations.size() > 1) {
            tileEntity.displayDestinations((ServerPlayerEntity) mob.getWorld().getPlayers().getFirst(), destinations);
        } else if (destinations.size() == 1) {
            BoardSpaceDestination destination = destinations.getFirst();
            if (destination == null) return;
            moveEntity(mob, destination.position());
        }
    }

    public static void moveEntityOnTileToDestination(ServerWorld world, BlockPos tileOrigin, BoardSpaceDestination tileDestination) {
        if (tileDestination == null || tileOrigin == null) return;
        BoardSpaceBlockEntity tileEntity = ABoardSpaceBlock.getBoardSpaceEntity(world, tileOrigin);
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
        VoxelShape shape = blockState.getCollisionShape(mob.getWorld(), targetPos);

        double blockHeight = shape.isEmpty() ? 0 : shape.getMax(Direction.Axis.Y);
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
