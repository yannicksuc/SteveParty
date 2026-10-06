package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyChunkHolds;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyLiveData;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

/**
 * Stopping a party: the dashboard's button / the command ({@link PartyControllerEntity#stopParty}) and a broken
 * controller. The pawns go back to their start tiles, out of the game, and nothing of the party is left.
 */
public class PartyStopGameTests implements FabricGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(3, 1, 6);
    private static final BlockPos START_A = new BlockPos(1, 1, 1), START_B = new BlockPos(1, 1, 3);
    private static final BlockPos SPACE_A = new BlockPos(5, 1, 1), SPACE_B = new BlockPos(5, 1, 3);

    private static BoardSpaceBlockEntity tile(TestContext context, BlockPos pos, ItemStack cartridge) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.TILE);
        BoardSpaceBlockEntity tile = context.getBlockEntity(pos);
        tile.setStack(0, cartridge);
        return tile;
    }

    /** A start tile bound to {@code token}: the controller takes it into its party. */
    private static void startTile(TestContext context, BlockPos pos, MobEntity token) {
        ItemStack start = new ItemStack(ModItems.TILE_BEHAVIOR_START);
        start.set(ModComponents.TB_START_BOUND_ENTITY, token.getUuid().toString());
        tile(context, pos, start);
        context.expectBlockProperty(pos, ABoardSpaceBlock.TILE_TYPE, BoardSpaceType.TILE_START);
    }

    private static PigEntity token(TestContext context, BlockPos on) {
        PigEntity pig = context.spawnMob(EntityType.PIG, on);
        ((TokenizedEntityInterface) pig).steveparty$setTokenized(true);
        return pig;
    }

    private record Board(PartyControllerEntity controller, PigEntity a, PigEntity b) {
    }

    /** Two pawns on their start tiles, two plain spaces, a controller whose party has started (its start rolls). */
    private static Board startedParty(TestContext context) {
        PigEntity a = token(context, START_A), b = token(context, START_B);
        startTile(context, START_A, a);
        startTile(context, START_B, b);
        tile(context, SPACE_A, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));
        tile(context, SPACE_B, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));
        context.setBlockState(CONTROLLER.down(), Blocks.STONE);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        controller.boot();
        context.assertTrue(controller.getPartyData().isStarted(), "the party started");
        context.assertTrue(controller.getPartyData().getTokens().containsAll(java.util.List.of(a.getUuid(), b.getUuid())), "both pawns play");
        context.assertTrue(inGame(a) && inGame(b), "the pawns are in game");
        context.assertEquals(controller.getStartTile(a.getUuid()), context.getAbsolutePos(START_A), "start tile of a remembered");
        return new Board(controller, a, b);
    }

    private static boolean inGame(MobEntity token) {
        int status = ((TokenizedEntityInterface) token).steveparty$getStatus();
        return TokenStatus.hasStatus(status, TokenStatus.IN_GAME) || TokenStatus.hasStatus(status, TokenStatus.CAN_MOVE);
    }

    /** The pawns away from their start: a stands on another space, b is walking toward one with steps left. */
    private static void movePawns(TestContext context, Board board) {
        Vec3d away = BoardSpaces.standPos(context.getWorld(), context.getAbsolutePos(SPACE_A));
        board.a().requestTeleport(away.x, away.y, away.z);
        assertOn(context, board.a(), SPACE_A, "a moved");
        TokenizedEntityInterface b = (TokenizedEntityInterface) board.b();
        b.steveparty$setNbSteps(2);
        b.steveparty$setTargetPosition(new Vector3d(away.x, away.y, away.z), 0.05);
    }

    private static void assertOn(TestContext context, MobEntity token, BlockPos at, String what) {
        BoardSpaceBlockEntity on = BoardSpaces.boardSpaceOf(token);
        context.assertTrue(on != null && on.getPos().equals(context.getAbsolutePos(at)),
                what + ": on " + (on == null ? "nothing" : on.getPos()) + ", expected " + context.getAbsolutePos(at));
    }

    /** The pawns are home, out of the game, still, and no party holds them. */
    private static void assertSentHome(TestContext context, Board board) {
        assertOn(context, board.a(), START_A, "a back on its start tile");
        assertOn(context, board.b(), START_B, "b back on its start tile");
        context.assertTrue(!inGame(board.a()) && !inGame(board.b()), "the pawns are out of the game");
        context.assertEquals(((TokenizedEntityInterface) board.b()).steveparty$getNbSteps(), 0, "b has no steps left");
        context.assertTrue(!PartyControllerEntity.isTokenInRunningParty(board.a().getUuid())
                && !PartyControllerEntity.isTokenInRunningParty(board.b().getUuid()), "no party holds the pawns");
    }

    private static void cleanUp(TestContext context, Board board) {
        ((TokenizedEntityInterface) board.a()).steveparty$setTokenized(false);
        ((TokenizedEntityInterface) board.b()).steveparty$setTokenized(false);
        if (context.getBlockState(CONTROLLER).isOf(ModBlocks.PARTY_CONTROLLER)) context.removeBlock(CONTROLLER);
    }

    /** Stopped: no winner, the pawns home and out of the game, the controller as before, and a new party can start. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stopSendsThePawnsHomeAndANewPartyCanStart(TestContext context) {
        Board board = startedParty(context);
        PartyControllerEntity controller = board.controller();
        movePawns(context, board);
        context.assertTrue(controller.stopParty(Text.literal("Tester")), "stopped");
        context.assertTrue(!controller.getPartyData().isStarted() && !controller.getPartyData().isAtEnd(), "no party, not even ended");
        context.assertTrue(controller.getPartyData().getSteps().isEmpty() && controller.getPartyData().getTokens().isEmpty(), "steps and tokens cleared");
        context.assertEquals(controller.getPhase(), PartyControllerEntity.PHASE_IDLE, "controller idle");
        context.assertTrue(controller.getInterestedPlayers().isEmpty(), "nobody follows it any more (HUD cleared)");
        context.assertTrue(controller.getLastLiveData() == PartyLiveData.EMPTY, "no live state");
        context.assertTrue(controller.getStartTile(board.a().getUuid()) == null, "start tiles forgotten");
        context.assertTrue(!controller.stopParty(Text.literal("Tester")), "nothing more to stop");
        assertSentHome(context, board);
        context.waitAndRun(10, () -> {
            // b's walk was dropped: it stays home
            assertOn(context, board.b(), START_B, "b stays on its start tile");
            controller.boot();
            context.assertTrue(controller.getPartyData().isStarted(), "a new party starts");
            context.assertTrue(inGame(board.a()) && inGame(board.b()), "the pawns are in game again");
            controller.stopParty(Text.literal("Tester"));
            cleanUp(context, board);
            context.complete();
        });
    }

    /** Breaking the controller mid-party stops it the same way, and nothing of it is left. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void breakingTheControllerStopsTheParty(TestContext context) {
        Board board = startedParty(context);
        PartyControllerEntity controller = board.controller();
        movePawns(context, board);
        context.removeBlock(CONTROLLER);
        context.assertTrue(controller.isRemoved(), "the controller is gone");
        context.assertTrue(!controller.getPartyData().isStarted(), "its party stopped");
        context.assertTrue(controller.getInterestedPlayers().isEmpty(), "nobody follows it any more (HUD cleared)");
        context.assertTrue(PartyControllerEntity.getPartyControllerEntity(context.getWorld(), context.getAbsolutePos(CONTROLLER)) == null,
                "not in the active controllers");
        context.assertTrue(!PartyControllerEntity.getActivePartyControllers().contains(controller), "not listed");
        context.assertTrue(!PartyChunkHolds.isHeld(context.getWorld(), context.getAbsolutePos(CONTROLLER)), "its chunk is let go");
        assertSentHome(context, board);
        cleanUp(context, board);
        context.complete();
    }

    /** The dashboard's button follows the settings' rule: during a party, a player who is no operator nor Game Master can't. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stopButtonNeedsTheRightToEdit(TestContext context) {
        Board board = startedParty(context);
        net.minecraft.server.network.ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        var handler = new fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler(1, player.getInventory(), board.controller());
        context.assertTrue(!handler.onButtonClick(player, fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.BUTTON_STOP),
                "refused");
        context.assertTrue(board.controller().getPartyData().isStarted(), "the party goes on");
        board.controller().stopParty(Text.literal("Tester"));
        cleanUp(context, board);
        context.complete();
    }

    /** {@code /steveparty party stop <pos>} stops the party of the controller there. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void commandStopsTheParty(TestContext context) {
        Board board = startedParty(context);
        movePawns(context, board);
        BlockPos at = context.getAbsolutePos(CONTROLLER);
        ServerCommandSource source = context.getWorld().getServer().getCommandSource()
                .withWorld(context.getWorld()).withPosition(Vec3d.ofCenter(at)).withSilent();
        context.getWorld().getServer().getCommandManager().executeWithPrefix(source,
                "steveparty party stop " + at.getX() + " " + at.getY() + " " + at.getZ());
        context.assertTrue(!board.controller().getPartyData().isStarted(), "the command stopped it");
        assertSentHome(context, board);
        cleanUp(context, board);
        context.complete();
    }
}
