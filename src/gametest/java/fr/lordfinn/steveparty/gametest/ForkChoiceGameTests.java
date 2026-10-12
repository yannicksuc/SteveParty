package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.entities.custom.DirectionDisplayEntity;
import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnEntity;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.gametest.kit.TestCleanup.atEnd;
import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * A fork in a party, played as a player plays it: the roll walks the pawn to the fork, its arrows show, the player
 * looks at one (the crosshair as the client finds it: blocks, then what can be aimed at) and right clicks it, whatever
 * is in hand (the packets the client sends, through the server's checks): the pawn takes that way.
 */
public class ForkChoiceGameTests implements SteveGameTest {
    private static final String BATCH = "fork_choice";
    private static final BlockPos START = new BlockPos(1, 1, 1), FORK = new BlockPos(3, 1, 1);
    private static final BlockPos EAST = new BlockPos(5, 1, 1), SOUTH = new BlockPos(3, 1, 3);

    /** A pig pawn, an empty hand: the player picks the way south. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = BATCH)
    public void aPlayerPicksAWayAtAFork(TestContext context) {
        ServerPlayerEntity player = TestPlayers.joined(context, "f", "Pig", GameMode.SURVIVAL, 0.5, 1, 5.5);
        MobEntity pig = context.spawnMob(EntityType.PIG, START);
        pickAWay(context, player, pig, ItemStack.EMPTY, null);
    }

    /**
     * A player pawn, its die back in the hand after the roll (every die comes back to its roller): the click on the
     * arrow still picks the way, and the die stays in the hand.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = BATCH)
    public void itsDieInHandThePlayerStillPicksAWay(TestContext context) {
        ServerPlayerEntity player = TestPlayers.joined(context, "f", "Die", GameMode.SURVIVAL, 0.5, 1, 5.5);
        pickAWay(context, player, context.spawnEntity(ModEntities.PLAYER_PAWN, START), die("dice_face_3"), null);
    }

    /** Someone else's click on the arrows does nothing: the pawn waits for its player. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = BATCH)
    public void onlyItsPlayerPicksTheWay(TestContext context) {
        ServerPlayerEntity player = TestPlayers.joined(context, "f", "Owner", GameMode.SURVIVAL, 0.5, 1, 5.5);
        ServerPlayerEntity stranger = TestPlayers.joined(context, "f", "Stranger", GameMode.SURVIVAL, 0.5, 1, 6.5);
        PlayerPawnEntity pawn = context.spawnEntity(ModEntities.PLAYER_PAWN, START);
        pickAWay(context, player, pawn, ItemStack.EMPTY, stranger);
    }

    /**
     * The roll walks {@code pawn} (tokenized here, played by {@code player}) to the fork; {@code stranger} (if any)
     * clicks the south arrow first, then {@code player}, holding {@code inHand}, looks at it and clicks it: the pawn
     * goes south.
     */
    private static void pickAWay(TestContext context, ServerPlayerEntity player, MobEntity pawn, ItemStack inHand,
                                 @Nullable ServerPlayerEntity stranger) {
        TokenizedEntityInterface token = (TokenizedEntityInterface) pawn;
        token.steveparty$setTokenized(true);
        token.steveparty$setTokenOwner(player.getUuid());
        token.steveparty$setTokenColor(0x3355FF);
        tile(context, START, plain(), FORK);
        tile(context, FORK, plain(), EAST, SOUTH);
        tile(context, EAST, plain());
        tile(context, SOUTH, plain());
        PartyControllerEntity party = party(context, player, pawn);
        List<ServerPlayerEntity> players = new ArrayList<>(List.of(player));
        if (stranger != null) players.add(stranger);
        atEnd(context, () -> {
            party.stopParty(null);
            context.removeBlock(CONTROLLER);
            token.steveparty$setTokenized(false);
            TestPlayers.removeIfOnline(context, players.toArray(ServerPlayerEntity[]::new));
        });
        Box around = new Box(context.getAbsolutePos(FORK)).expand(4);
        context.waitAndRun(2, () -> {
            roll(context, player, 3);
            when(context, () -> context.getWorld().getEntitiesByClass(DirectionDisplayEntity.class, around, e -> true).size() == 2,
                    200, "the fork's arrows", () -> {
                        assertNear(context, pawn, FORK, "waits at the fork");
                        DirectionDisplayEntity south = context.getWorld().getEntitiesByClass(DirectionDisplayEntity.class, around,
                                arrow -> arrow.getZ() > context.getAbsolutePos(FORK).getZ() + 0.6).getFirst();
                        if (stranger != null) {
                            click(context, stranger, south);
                            context.assertTrue(!south.isRemoved(), "someone else's click: the arrow stays");
                            context.assertTrue(token.steveparty$getNbSteps() > 0, "the pawn still waits");
                        }
                        player.setStackInHand(Hand.MAIN_HAND, inHand.copy());
                        click(context, player, south);
                        context.assertTrue(south.isRemoved(), "its player's click picks the way (holding " + inHand + ")");
                        context.assertTrue(ItemStack.areItemsAndComponentsEqual(player.getMainHandStack(), inHand)
                                        && player.getMainHandStack().getCount() == inHand.getCount(),
                                "what is in hand stays there: " + player.getMainHandStack());
                        when(context, () -> token.steveparty$getNbSteps() == 0, 200, "the move ends", () -> {
                            assertNear(context, pawn, SOUTH, "the way picked");
                            context.complete();
                        });
                    });
        });
    }

    /** A party of {@code pawn} alone, played by {@code player}, at its turn. */
    private static PartyControllerEntity party(TestContext context, ServerPlayerEntity player, MobEntity pawn) {
        context.setBlockState(CONTROLLER.down(), Blocks.STONE);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        PartyData data = new PartyData();
        data.addToken(pawn.getUuid());
        ((TokenizedEntityInterface) pawn).steveparty$setStatus(TokenStatus.IN_GAME);
        data.addStep(new PartyStep());
        data.addStep(new TokenTurnPartyStep(pawn.getUuid(), player.getUuid()));
        data.addStep(new TokenTurnPartyStep(pawn.getUuid(), player.getUuid()));
        controller.setPartyData(data);
        controller.nextStep();
        controller.nextStep();
        return controller;
    }

    /** {@code player} throws a die showing {@code steps} and hits it: the roll of the turn. */
    private static void roll(TestContext context, ServerPlayerEntity player, int steps) {
        DiceEntity dice = context.spawnEntity(ModEntities.DICE_ENTITY, new BlockPos(6, 3, 5));
        dice.age = DiceEntity.THROW_GRACE_TICKS;
        dice.setNoGravity(true);
        dice.setOwner(player.getUuid());
        dice.setItemReference(die("dice_face_" + steps));
        dice.startRoll();
        hit(context, dice, player);
    }

    /**
     * {@code player} stands south west of {@code arrow}, within reach, looks at it (the crosshair must be on it) and
     * right clicks: the packets a client sends, through the server's checks.
     */
    private static void click(TestContext context, ServerPlayerEntity player, DirectionDisplayEntity arrow) {
        Vec3d at = arrow.getBoundingBox().getCenter();
        Vec3d stand = at.add(-1.6, 0, 1.2);
        player.refreshPositionAndAngles(stand.x, context.getAbsolutePos(FORK).getY(), stand.z, 0, 0);
        player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, at);
        Entity aimed = crosshair(player);
        context.assertTrue(aimed == arrow, "the crosshair is on the arrow, not on " + aimed);
        player.networkHandler.onPlayerInteractEntity(PlayerInteractEntityC2SPacket.interactAt(arrow, false, Hand.MAIN_HAND, at.subtract(arrow.getPos())));
        player.networkHandler.onPlayerInteractEntity(PlayerInteractEntityC2SPacket.interact(arrow, false, Hand.MAIN_HAND));
    }

    /** What the client's crosshair is on (GameRenderer#findCrosshairTarget): an entity, or null. */
    private static Entity crosshair(ServerPlayerEntity player) {
        double reach = Math.max(player.getBlockInteractionRange(), player.getEntityInteractionRange());
        HitResult block = player.raycast(reach, 1f, false);
        Vec3d eye = player.getEyePos();
        double blockDistance = block.getType() == HitResult.Type.MISS ? reach * reach : block.getPos().squaredDistanceTo(eye);
        Vec3d look = player.getRotationVec(1f);
        Vec3d end = eye.add(look.multiply(reach));
        EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, player.getBoundingBox().stretch(look.multiply(reach)).expand(1),
                entity -> !entity.isSpectator() && entity.canHit(), blockDistance);
        if (hit == null || hit.getPos().squaredDistanceTo(eye) > player.getEntityInteractionRange() * player.getEntityInteractionRange())
            return null;
        return hit.getEntity();
    }

    private static void assertNear(TestContext context, MobEntity pawn, BlockPos at, String what) {
        context.assertTrue(pawn.getBlockPos().isWithinDistance(context.getAbsolutePos(at), 1.5),
                what + ": at " + pawn.getBlockPos() + ", expected " + context.getAbsolutePos(at));
    }
}
