package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileTeleport;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceDestination;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ABoardSpaceBehavior;
import fr.lordfinn.steveparty.dice.DiceOutcome;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.events.DiceRollEvent;
import fr.lordfinn.steveparty.events.TileReachedEvent;
import fr.lordfinn.steveparty.events.TileUpdatedEvent;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static fr.lordfinn.steveparty.Steveparty.SCHEDULER;

public class TokenMovementService {

    private static final double MOVE_SPEED = 0.5;

    public TokenMovementService() {
        TileReachedEvent.EVENT.register(TokenMovementService::onTileReached);
        TileUpdatedEvent.EVENT.register(TokenMovementService::tryToMoveEntityOnBoard);

        DiceRollEvent.EVENT.register(this::handleDiceRoll);
    }

    private ActionResult handleDiceRoll(DiceEntity dice, UUID ownerUUID, int rollValue) {
        ServerWorld world = (ServerWorld) dice.getWorld();
        if (world == null) return ActionResult.PASS;

        MobEntity chosenToken = getTargetedToken(world, dice, ownerUUID);
        if (chosenToken == null) return ActionResult.PASS;

        PartyControllerEntity.onTokenDiceRolled(world, chosenToken, rollValue);
        // The roll the board spaces of this move read (Threshold obstacle, Key gate)
        BoardSpaceBlockEntity rolledFrom = BoardSpaces.boardSpaceOf(chosenToken);
        TurnMoves.record(chosenToken, rollValue, numberFaces(dice), rolledFrom == null ? null : rolledFrom.getPos());
        AdvanceBackMoves.cancel(chosenToken); // a new move: nothing left of an extra move
        TileTeleport.cancelPush(chosenToken);
        // The modules of the die that change the move itself (Skeleton Key, Homing)
        DiceRollEffects.setMoveModules(chosenToken, DiceModules.of(dice.lead().getDieStack()));
        DiceOutcome outcome = dice.getOutcome();
        if (outcome.steps() != rollValue) outcome = DiceOutcome.ofSteps(rollValue); // a roll announced by hand (tests, add-ons)
        if (outcome.steps() > 0 && !outcome.isSpecial()) {
            // Add small delay so players can appreciate the dice roll value
            SCHEDULER.schedule(chosenToken.getUuid(), DiceRollEffects.APPRECIATE_TICKS, () -> moveEntityOnBoard(chosenToken, rollValue));
        } else {
            // Coins, swap, a face 0, a roll going backward: see DiceRollEffects
            DiceRollEffects.resolve(world, chosenToken, ownerUUID, outcome);
        }
        return ActionResult.SUCCESS;
    }

    /** The numbers shown by the dice of the throw (the faces that walk steps), one per die. */
    private static List<Integer> numberFaces(DiceEntity dice) {
        List<Integer> faces = new ArrayList<>();
        for (DiceFacesComponent.DiceFace face : dice.getRolledFaces()) {
            if (face.steps() > 0) faces.add(face.steps());
        }
        return faces;
    }

    private MobEntity getTargetedToken(ServerWorld world, DiceEntity dice, UUID ownerUUID) {
        List<MobEntity> chosenTokens = getEligibleTokens(world, dice, ownerUUID);
        if (chosenTokens.isEmpty()) return null;

        sortTokens(chosenTokens, dice);

        return chosenTokens.getFirst();
    }

    private List<MobEntity> getEligibleTokens(ServerWorld world, DiceEntity dice, UUID ownerUUID) {
        List<MobEntity> eligibleTokens = new ArrayList<>();
        for (MobEntity token : world.getEntitiesByClass(MobEntity.class,
                Box.of(dice.getPos(), 50, 50, 50),
                entity -> ((TokenizedEntityInterface) entity).steveparty$isTokenized())) {

            TokenizedEntityInterface tokenInterface = (TokenizedEntityInterface) token;
            // Tokens without owner are eligible too: anyone may move them (see isTokenEligible)
            if (tokenInterface.steveparty$getNbSteps() == 0
                    && !DiceRollEffects.isResolving(token.getUuid()) // its last roll is still being resolved
                    && isTokenEligible(tokenInterface, ownerUUID)) {
                eligibleTokens.add(token);
            }
        }
        return eligibleTokens;
    }

    private boolean isTokenEligible(TokenizedEntityInterface token, UUID ownerUUID) {
        int status = token.steveparty$getStatus();
        UUID tokenOwner = token.steveparty$getTokenOwner();
        if (TokenStatus.isInGame(status)) {
            // In game: only the owner (anyone for an ownerless token) can move it, and only when it is its turn
            return (tokenOwner == null || tokenOwner.equals(ownerUUID)) && TokenStatus.canMoveInGame(status);
        }
        // Free play: a dice only moves the tokens of the player who rolled it (ownerless tokens: anyone)
        return tokenOwner == null || tokenOwner.equals(ownerUUID);
    }

    private void sortTokens(List<MobEntity> tokens, DiceEntity dice) {
        // Sort tokens by status (IN_GAME_CAN_MOVE are first and OUT_OF_GAME_xxx are last) and then by distance to dice
        tokens.sort(
                Comparator
                        .<MobEntity>comparingInt(token -> TokenStatus.canMoveInGame(((TokenizedEntityInterface) token).steveparty$getStatus()) ? 0 : 1)
                        .thenComparingDouble(token -> token.getPos().squaredDistanceTo(dice.getPos()))
        );
    }

    /**
     * A token reached a board space: the star may keep it while its owner chooses to buy it (PartyStars), a shop stop
     * while its owner shops (ShopStops), else it goes on.
     */
    private static @NotNull ActionResult onTileReached(MobEntity entity, BoardSpaceBlockEntity tile) {
        if (!entity.getWorld().isClient && (PartyStars.onTileReached(entity, tile) || ShopStops.onTileReached(entity, tile)))
            return ActionResult.SUCCESS;
        return tryToMoveEntityOnBoard(entity, tile);
    }

    private static @NotNull ActionResult tryToMoveEntityOnBoard(MobEntity entity, BoardSpaceBlockEntity tile) {
        if (entity.getWorld().isClient) return ActionResult.PASS;
        if (ShopStops.isShopping(entity.getUuid())) return ActionResult.PASS; // its owner is shopping
        if (PartyStars.isDeciding(entity.getUuid())) return ActionResult.PASS; // its owner may buy the star
        int nbSteps = ((TokenizedEntityInterface) entity).steveparty$getNbSteps();
        if (nbSteps == 0) return ActionResult.PASS;
        // The extra move of a Move Forward / Back tile starts on its own, once its landing is heard
        if (AdvanceBackMoves.isWaiting(entity)) return ActionResult.PASS;

        ABoardSpaceBehavior behavior = tile.getBoardSpaceBehavior();
        // A token still standing on a Stop space has no steps left (forced arrival: see onTokenArrived)
        if (behavior == null || !behavior.needToStop(entity.getWorld(), tile.getPos()) || DiceRollEffects.ignoresStops(entity)) {
            moveEntityOnBoard(entity, nbSteps);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    /**
     * Called when a token reached the position it was moving (or teleported) to.
     * Consumes one step when the board space counts as a step, then fires {@link TileReachedEvent}.
     */
    public static void onTokenArrived(MobEntity mob) {
        if (mob.getWorld().isClient) return;
        BoardSpaceBlockEntity boardSpace = BoardSpaces.boardSpaceOf(mob);
        if (boardSpace == null) return;
        TokenizedEntityInterface token = (TokenizedEntityInterface) mob;
        if (token.steveparty$getNbSteps() > 0 //TODO Manage negative Steps (Not urgent)
                && ABoardSpaceBlock.countsAsStep(boardSpace.getCachedState().getBlock())) {
            token.steveparty$setNbSteps(token.steveparty$getNbSteps() - 1);
        }
        // The space's role may end the move here (a Threshold obstacle the roll does not get over)
        ABoardSpaceBehavior reached = boardSpace.getBoardSpaceBehavior();
        if (reached != null && mob.getWorld() instanceof ServerWorld reachedWorld
                && reached.onTokenReached(reachedWorld, boardSpace, mob, token.steveparty$getNbSteps())
                && token.steveparty$getNbSteps() > 0) {
            halt(mob, boardSpace.getPos());
        }
        endMoveIfForcedStop(mob, boardSpace);
        AdvanceBackMoves.onArrived(mob, boardSpace.getPos());
        if (token.steveparty$getNbSteps() == 0 && mob.getWorld() instanceof ServerWorld serverWorld)
            PartyControllerEntity.onFreeTokenArrived(serverWorld, mob);
        TileReachedEvent.EVENT.invoker().onTileReached(mob, boardSpace);
        AdvanceBackMoves.afterArrival(mob);
        // The move is over: the modules of its die no longer apply, its player no longer sees where it is heading
        if (token.steveparty$getNbSteps() == 0) {
            DiceRollEffects.clearMoveModules(mob.getUuid());
            if (mob.getWorld() instanceof ServerWorld world) TileInfos.arrived(world, mob);
        }
    }

    /** True if a token reaching this board space must end its move there (a Stop space), steps left or not. */
    public static boolean isForcedStop(World world, BoardSpaceBlockEntity boardSpace) {
        ABoardSpaceBehavior behavior = boardSpace.getBoardSpaceBehavior();
        return behavior != null && behavior.needToStop(world, boardSpace.getPos());
    }

    /**
     * Same for this token: also true where its move was ended early ({@link #halt}: a Threshold obstacle, waiting at a
     * Key gate), a check point included.
     */
    public static boolean isForcedStop(World world, BoardSpaceBlockEntity boardSpace, @Nullable MobEntity token) {
        return isForcedStop(world, boardSpace) || token != null && TurnMoves.isHaltedOn(token, boardSpace.getPos());
    }

    /**
     * Ends the move of {@code mob} on the board space it just reached at {@code space}: the steps left are lost and it
     * lands there (a check point too).
     */
    public static void halt(MobEntity mob, BlockPos space) {
        ((TokenizedEntityInterface) mob).steveparty$setNbSteps(0);
        SCHEDULER.cancel(mob.getUuid()); // nothing of the roll may move it on
        TurnMoves.halt(mob, space);
    }

    /**
     * A token reaching a Stop space ends its move there (forced arrival): the steps left of its roll are lost.
     * Not if the die that moves it carries the Skeleton Key module: it walks through.
     *
     * @return true if the move was ended here
     */
    public static boolean endMoveIfForcedStop(MobEntity mob, BoardSpaceBlockEntity boardSpace) {
        TokenizedEntityInterface token = (TokenizedEntityInterface) mob;
        if (token.steveparty$getNbSteps() <= 0 || !isForcedStop(mob.getWorld(), boardSpace)) return false;
        if (DiceRollEffects.ignoresStops(mob)) return false;
        token.steveparty$setNbSteps(0);
        SCHEDULER.cancel(mob.getUuid()); // nothing of the roll may move it on
        return true;
    }

    public static void moveEntityOnBoard(MobEntity mob, int rollNumber) {
        ((TokenizedEntityInterface) mob).steveparty$setNbSteps(rollNumber);
        if (rollNumber != 0) TurnMoves.release(mob); // it moves on: no halt holds it
        if (rollNumber == 0) {
            if (mob.getWorld() instanceof ServerWorld world) TileInfos.arrived(world, mob);
            MessageUtils.sendToNearby((ServerWorld) mob.getWorld(), mob.getPos(), 100,
                    Text.translatable("message.steveparty.arrived_at_destination", mob.getCustomName() != null ? mob.getCustomName() : mob.getName()),
                    MessageUtils.MessageType.ACTION_BAR);
            return;
        }
        BoardSpaceBlockEntity tileEntity = BoardSpaces.boardSpaceOf(mob);
        if (tileEntity == null) {
            // Not on the board: it can't move, and must not keep pending steps (it would never be eligible again)
            ((TokenizedEntityInterface) mob).steveparty$setNbSteps(0);
            return;
        }
        //SendMessageService.sendTokenMovementMessage(mob, rollNumber);
        AdvanceBackMoves.noteAt(mob, tileEntity.getPos()); // where it comes from (to go back that way)

        MessageUtils.sendToNearby((ServerWorld) mob.getWorld(), mob.getPos(), 100,
                Text.translatable("message.steveparty.steps_remaining_for", rollNumber, mob.getCustomName() != null ? mob.getCustomName() : mob.getName())
                , MessageUtils.MessageType.ACTION_BAR);

        if (AdvanceBackMoves.isRouted(mob)) {
            TileInfos.arrived((ServerWorld) mob.getWorld(), mob);
            // Going back (Move Forward / Back tile): the way it came, not the destinations
            BlockPos previous = AdvanceBackMoves.nextRouted(mob);
            if (previous != null) moveEntity(mob, previous);
            else stopOnCurrentBoardSpace(mob, tileEntity.getPos());
            return;
        }

        List<BoardSpaceDestination> destinations = tileEntity.getStockedDestinations()
                .stream()
                .filter(BoardSpaceDestination::isTile)
                .toList();

        // Its player sees where each way ends (the info of the space it will stop on)
        TileInfos.heading((ServerWorld) mob.getWorld(), mob, destinations, rollNumber);
        // A closed Key gate: an exit it locks is refused without a key (see KeyGates)
        if (KeyGates.holdsAtExit(mob, tileEntity, destinations)) return;
        List<BoardSpaceDestination> forks = KeyGates.homingChoices(mob, tileEntity, destinations);
        BoardSpaceDestination homing = destinations.size() > 1 ? DiceRollEffects.chooseFork(mob, forks) : null;
        if (homing != null) {
            // Homing module: the token takes a branch by itself, nobody is asked
            moveEntity(mob, homing.position());
        } else if (destinations.size() > 1) {
            // Remove the arrows of a previous display (re-triggered movement) to avoid duplicates
            tileEntity.hideDestinations();
            tileEntity.displayDestinations(getDestinationChooser(mob), destinations, mob.getUuid());
        } else if (destinations.size() == 1) {
            BoardSpaceDestination destination = destinations.getFirst();
            moveEntity(mob, destination.position());
        } else {
            // Dead end: the movement ends here
            stopOnCurrentBoardSpace(mob, tileEntity.getPos());
        }
    }

    /** Ends the movement on the given board space: the token "arrives" there on the next tick. */
    public static void stopOnCurrentBoardSpace(MobEntity mob, BlockPos boardSpacePos) {
        moveEntityOnBoard(mob, 0);
        ((TokenizedEntityInterface) mob).steveparty$setTargetPosition(calculateTargetPosition(mob, boardSpacePos), MOVE_SPEED);
    }

    /**
     * The player who chooses the direction: the token owner if online in this world, otherwise the nearest player.
     */
    private static @Nullable ServerPlayerEntity getDestinationChooser(MobEntity mob) {
        if (!(mob.getWorld() instanceof ServerWorld world)) return null;
        UUID ownerUuid = ((TokenizedEntityInterface) mob).steveparty$getTokenOwner();
        if (ownerUuid != null) {
            ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerUuid);
            if (owner != null && owner.getWorld() == world) return owner;
        }
        PlayerEntity closest = world.getClosestPlayer(mob, -1);
        return closest instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null;
    }

    public static void moveEntityOnTileToDestination(ServerWorld world, BlockPos tileOrigin, BoardSpaceDestination tileDestination) {
        moveEntityOnTileToDestination(world, tileOrigin, tileDestination, null);
    }

    /**
     * Moves the token waiting on {@code tileOrigin} toward {@code tileDestination}.
     * @param preferredToken the token the direction was displayed for, if known
     */
    public static void moveEntityOnTileToDestination(ServerWorld world, BlockPos tileOrigin, BoardSpaceDestination tileDestination, @Nullable UUID preferredToken) {
        if (tileDestination == null || tileOrigin == null) return;
        BoardSpaceBlockEntity tileEntity = ABoardSpaceBlock.getBoardSpaceEntity(world, tileOrigin);
        if (tileEntity == null) return;
        List<MobEntity> tokens = tileEntity.getTokensOnMe();
        MobEntity mob = null;
        for (MobEntity token : tokens) {
            if (((TokenizedEntityInterface)token).steveparty$getNbSteps() != 0) {
                if (preferredToken == null || preferredToken.equals(token.getUuid())) {
                    mob = token;
                    break;
                }
                if (mob == null) mob = token; // fallback: first waiting token
            }
        }
        if (mob == null) {
            tileEntity.hideDestinations();
            return;
        }
        // A branch locked by a closed Key gate: the arrows stay while its player answers (see KeyGates)
        if (KeyGates.holdsAtChoice(mob, tileEntity, tileDestination)) return;
        tileEntity.hideDestinations();
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

    /** Tokens stand on the real surface of the board space (lowered or sloped tiles included, see BoardSpaces). */
    private static Vector3d calculateTargetPosition(MobEntity mob, BlockPos targetPos) {
        Vec3d stand = BoardSpaces.standPos(mob.getWorld(), targetPos);
        return new Vector3d(stand.x, stand.y, stand.z);
    }

    private static boolean isTooFar(double distance) {
        return distance > 2500;
    }

    private static void teleportEntity(MobEntity mob, BlockPos targetPos, Vector3d preciseTargetPos) {
        mob.setPosition(preciseTargetPos.x(), preciseTargetPos.y(), preciseTargetPos.z());
        playSound(mob, targetPos, SoundEvents.ENTITY_ENDERMAN_TELEPORT);
        // Already there: the arrival (step count + TileReachedEvent) is handled on the next tick, like a normal move
        if (mob instanceof TokenizedEntityInterface tokenizedEntity) {
            tokenizedEntity.steveparty$setTargetPosition(preciseTargetPos, MOVE_SPEED);
        }
    }

    private static void moveEntityToTarget(MobEntity mob, Vector3d target) {
        // Set the target position (if applicable to your custom interface)
        if (mob instanceof TokenizedEntityInterface tokenizedEntity) {
            tokenizedEntity.steveparty$setTargetPosition(target, MOVE_SPEED);
        }

        // A pawn faces where it goes: body and head together, level (a straight up / down move keeps its facing)
        double deltaX = target.x() - mob.getX();
        double deltaZ = target.z() - mob.getZ();
        double deltaY = target.y() - mob.getY();
        if (deltaX * deltaX + deltaZ * deltaZ > 1.0E-6) {
            float yaw = (float) (Math.atan2(deltaZ, deltaX) * (180 / Math.PI)) - 90; // Convert radians to degrees
            faceYaw(mob, yaw);
        }
        mob.setPitch(0);
        mob.setVelocity(deltaX, deltaY, deltaZ);
    }

    /** Turns a token (body and head) to {@code yaw}, degrees. */
    public static void faceYaw(MobEntity mob, float yaw) {
        mob.setYaw(yaw);
        mob.setBodyYaw(yaw);
        mob.setHeadYaw(yaw);
    }

    /** At the tile, heard by the players around it (16 blocks, fading with the distance), not across the map. */
    private static void playSound(MobEntity mob, BlockPos targetPos, SoundEvent soundEvent) {
        Vec3d at = BoardSpaces.standPos(mob.getWorld(), targetPos); // where the tile is seen
        mob.getWorld().playSound(null, at.x, at.y, at.z, soundEvent, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }
}
