package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceDestination;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback.Kind;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback.Landing;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback.Played;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.DirectionDisplayEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.AdvanceBackCartridgeItem;
import fr.lordfinn.steveparty.service.AdvanceBackMoves;
import fr.lordfinn.steveparty.service.TokenMovementService;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static fr.lordfinn.steveparty.gametest.kit.TestAsserts.assertOn;
import static fr.lordfinn.steveparty.gametest.kit.TestCleanup.atEnd;
import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * The Move Forward / Back tile: forward N, back N the way it came, a fork asking the owner, no chain, a start tile
 * stopping a token going back, and the cartridge's setting.
 */
public class AdvanceBackGameTests implements FabricGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(0, 1, 7);
    /** A path of tiles 2 blocks apart: along x on z = 1, back along x on z = 3, then along x on z = 5. */
    private static final List<BlockPos> PATH = List.of(
            new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1), new BlockPos(7, 1, 1),
            new BlockPos(7, 1, 3), new BlockPos(5, 1, 3), new BlockPos(3, 1, 3), new BlockPos(1, 1, 3));

    // ---------------------------------------------------------------- board

    private static BoardSpaceBlockEntity tile(TestContext context, BlockPos pos, ItemStack cartridge, BlockPos... to) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.TILE);
        List<BlockPos> destinations = new ArrayList<>();
        for (BlockPos next : to) destinations.add(context.getAbsolutePos(next));
        cartridge.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(destinations, ""));
        BoardSpaceBlockEntity tile = context.getBlockEntity(pos);
        tile.setStack(0, cartridge);
        return tile;
    }

    private static ItemStack plain() {
        return new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR);
    }

    /** The first {@code count} tiles of {@link #PATH}, each linked to the next; {@code special} at index {@code at}. */
    private static void path(TestContext context, int count, int at, ItemStack special) {
        for (int i = 0; i < count; i++) {
            BlockPos[] next = i + 1 < count ? new BlockPos[]{PATH.get(i + 1)} : new BlockPos[0];
            tile(context, PATH.get(i), i == at ? special : plain(), next);
        }
    }

    private static PigEntity token(TestContext context, BlockPos on) {
        PigEntity pig = context.spawnMob(EntityType.PIG, on);
        TokenizedEntityInterface token = (TokenizedEntityInterface) pig;
        token.steveparty$setTokenized(true);
        token.steveparty$setStatus(TokenStatus.IN_GAME);
        return pig;
    }

    /** A party of this one token, at its turn (step 1); the next turn is step 2. */
    private static PartyControllerEntity party(TestContext context, MobEntity token) {
        context.setBlockState(CONTROLLER.down(), Blocks.STONE);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        PartyData data = new PartyData();
        data.addToken(token.getUuid());
        data.addStep(new PartyStep());
        data.addStep(new TokenTurnPartyStep(token.getUuid(), null));
        data.addStep(new TokenTurnPartyStep(token.getUuid(), null));
        controller.setPartyData(data);
        controller.nextStep();
        controller.nextStep();
        context.assertEquals(data.getStepIndex(), 1, "the token's turn");
        atEnd(context, () -> {
            ((TokenizedEntityInterface) token).steveparty$setTokenized(false);
            context.removeBlock(CONTROLLER);
        });
        return controller;
    }

    private static List<Played> record(TestContext context) {
        List<Played> played = new ArrayList<>();
        Consumer<Played> listener = played::add;
        TileFeedback.LISTENERS.add(listener);
        atEnd(context, () -> TileFeedback.LISTENERS.remove(listener));
        return played;
    }

    private static boolean landed(List<Played> played, TestContext context, BlockPos at, Landing landing) {
        BlockPos absolute = context.getAbsolutePos(at);
        return played.stream().anyMatch(event -> event.kind() == Kind.LAND && event.tile().equals(absolute) && event.landing() == landing);
    }

    private static boolean passed(List<Played> played, TestContext context, BlockPos at) {
        BlockPos absolute = context.getAbsolutePos(at);
        return played.stream().anyMatch(event -> event.kind() == Kind.PASS && event.tile().equals(absolute));
    }

    private static BooleanSupplier turnEnded(PartyControllerEntity controller) {
        return () -> controller.getPartyData().getStepIndex() >= 2;
    }

    // ---------------------------------------------------------------- tests

    /** Landing on a Move Forward 2 tile: it goes on 2 more spaces (the one passed pops), its turn ends there. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void forwardMovesOnAndEndsTheTurnWhereItLands(TestContext context) {
        path(context, 6, 2, AdvanceBackCartridgeItem.withSteps(2));
        List<Played> played = record(context);
        PigEntity pig = token(context, PATH.get(0));
        PartyControllerEntity controller = party(context, pig);
        context.waitAndRun(2, () -> {
            TokenMovementService.moveEntityOnBoard(pig, 2);
            when(context, () -> landed(played, context, PATH.get(2), Landing.ADVANCE), 100, "lands on the forward tile", () -> {
                context.assertEquals(controller.getPartyData().getStepIndex(), 1, "the turn goes on");
                context.assertEquals(((TokenizedEntityInterface) pig).steveparty$getNbSteps(), 2, "2 more steps to go");
                when(context, turnEnded(controller), 150, "turn ends", () -> {
                    assertOn(context, pig, PATH.get(4), "2 spaces further");
                    context.assertTrue(passed(played, context, PATH.get(3)), "the space passed pops");
                    context.assertTrue(landed(played, context, PATH.get(4), Landing.DEFAULT), "the space reached lands");
                    context.assertTrue(!AdvanceBackMoves.isExtraMove(pig), "the extra move is over");
                    context.complete();
                });
            });
        });
    }

    /** Landing on a Move Back 2 tile: it goes back the way it came. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void backRetracesThePathItCameBy(TestContext context) {
        path(context, 5, 3, AdvanceBackCartridgeItem.withSteps(-2));
        List<Played> played = record(context);
        PigEntity pig = token(context, PATH.get(0));
        PartyControllerEntity controller = party(context, pig);
        context.waitAndRun(2, () -> {
            TokenMovementService.moveEntityOnBoard(pig, 3);
            when(context, turnEnded(controller), 250, "turn ends", () -> {
                context.assertTrue(landed(played, context, PATH.get(3), Landing.BACK), "the back tile lands");
                assertOn(context, pig, PATH.get(1), "2 spaces back");
                context.assertTrue(passed(played, context, PATH.get(2)), "the space passed going back pops");
                List<BlockPos> trail = AdvanceBackMoves.trail(pig);
                context.assertEquals(trail.getLast(), context.getAbsolutePos(PATH.get(1)), "its path ends where it is");
                context.assertTrue(!trail.contains(context.getAbsolutePos(PATH.get(3))), "the spaces gone back are forgotten");
                context.complete();
            });
        });
    }

    /** Beyond what it remembers it walks the links backward, and stops on a start tile. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void backWalksTheLinksAndStopsOnTheStartTile(TestContext context) {
        tile(context, PATH.get(0), new ItemStack(ModItems.TILE_BEHAVIOR_START), PATH.get(1));
        tile(context, PATH.get(1), plain(), PATH.get(2));
        tile(context, PATH.get(2), AdvanceBackCartridgeItem.withSteps(-5), PATH.get(3));
        tile(context, PATH.get(3), plain());
        context.expectBlockProperty(PATH.get(0), ABoardSpaceBlock.TILE_TYPE, BoardSpaceType.TILE_START);
        PigEntity pig = token(context, PATH.get(1));
        AdvanceBackMoves.forgetTrail(pig.getUuid());
        // Nothing remembered: the links, backward, down to the start tile (2 steps of the 5)
        AdvanceBackMoves.Route route = AdvanceBackMoves.planBack(context.getWorld(), pig, context.getAbsolutePos(PATH.get(2)), 5);
        context.assertEquals(route.spaces(), List.of(context.getAbsolutePos(PATH.get(1)), context.getAbsolutePos(PATH.get(0))), "route");
        context.assertEquals(route.steps(), 2, "stops on the start tile");

        PartyControllerEntity controller = party(context, pig);
        context.waitAndRun(2, () -> {
            TokenMovementService.moveEntityOnBoard(pig, 1);
            when(context, turnEnded(controller), 250, "turn ends", () -> {
                assertOn(context, pig, PATH.get(0), "back on the start tile");
                context.complete();
            });
        });
    }

    /** Going forward from the tile, a fork asks the owner, like any move; the turn ends after the chosen way. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void forwardAtAForkAsksTheOwner(TestContext context) {
        BlockPos start = PATH.get(0), fork = PATH.get(1), a = PATH.get(2), a2 = PATH.get(3);
        BlockPos b = new BlockPos(3, 1, 3), b2 = new BlockPos(3, 1, 5);
        tile(context, start, plain(), fork);
        tile(context, fork, AdvanceBackCartridgeItem.withSteps(2), a, b);
        tile(context, a, plain(), a2);
        tile(context, a2, plain());
        tile(context, b, plain(), b2);
        tile(context, b2, plain());
        PigEntity pig = token(context, start);
        PartyControllerEntity controller = party(context, pig);
        Box around = new Box(context.getAbsolutePos(fork)).expand(4);
        context.waitAndRun(2, () -> {
            TokenMovementService.moveEntityOnBoard(pig, 1);
            when(context, () -> !context.getWorld().getEntitiesByClass(DirectionDisplayEntity.class, around, e -> true).isEmpty(),
                    100, "the ways are shown", () -> {
                        assertOn(context, pig, fork, "waits at the fork");
                        context.assertEquals(controller.getPartyData().getStepIndex(), 1, "still its turn");
                        TokenMovementService.moveEntityOnTileToDestination(context.getWorld(), context.getAbsolutePos(fork),
                                new BoardSpaceDestination(context.getAbsolutePos(b), true), pig.getUuid());
                        when(context, turnEnded(controller), 150, "turn ends", () -> {
                            assertOn(context, pig, b2, "2 spaces along the chosen way");
                            context.complete();
                        });
                    });
        });
    }

    /** Reached by an extra move, a Move Forward / Back tile doesn't send the token on again: no loop. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void noChainBetweenMoveTiles(TestContext context) {
        tile(context, PATH.get(0), plain(), PATH.get(1));
        tile(context, PATH.get(1), AdvanceBackCartridgeItem.withSteps(1), PATH.get(2));
        tile(context, PATH.get(2), AdvanceBackCartridgeItem.withSteps(-1), PATH.get(3));
        tile(context, PATH.get(3), plain());
        List<Played> played = record(context);
        PigEntity pig = token(context, PATH.get(0));
        PartyControllerEntity controller = party(context, pig);
        context.waitAndRun(2, () -> {
            TokenMovementService.moveEntityOnBoard(pig, 1);
            when(context, turnEnded(controller), 200, "turn ends", () -> {
                assertOn(context, pig, PATH.get(2), "stays on the second move tile");
                context.assertTrue(landed(played, context, PATH.get(2), Landing.DEFAULT), "a plain landing there");
                context.assertTrue(!landed(played, context, PATH.get(2), Landing.BACK), "not sent back");
                context.waitAndRun(40, () -> {
                    assertOn(context, pig, PATH.get(2), "still there");
                    context.assertEquals(((TokenizedEntityInterface) pig).steveparty$getNbSteps(), 0, "no step left");
                    context.complete();
                });
            });
        });
    }

    /** The cartridge: 1..6 forward or back (never 0), its colour and its landing kind. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cartridgeSetting(TestContext context) {
        ItemStack cartridge = new ItemStack(ModItems.ADVANCE_BACK_CARTRIDGE);
        context.assertEquals(AdvanceBackCartridgeItem.steps(cartridge), AdvanceBackCartridgeItem.DEFAULT_STEPS, "default");
        context.assertEquals(AdvanceBackCartridgeItem.scrolled(1, -1), -1, "0 skipped going back");
        context.assertEquals(AdvanceBackCartridgeItem.scrolled(-1, 1), 1, "0 skipped going forward");
        context.assertEquals(AdvanceBackCartridgeItem.scrolled(6, 1), 6, "at most 6 forward");
        context.assertEquals(AdvanceBackCartridgeItem.scrolled(-6, -1), -6, "at most 6 back");
        AdvanceBackCartridgeItem.scroll(cartridge, -1);
        context.assertEquals(AdvanceBackCartridgeItem.steps(cartridge), 2, "scrolled down once");

        BoardSpaceBlockEntity tile = tile(context, new BlockPos(2, 1, 2), AdvanceBackCartridgeItem.withSteps(4));
        context.expectBlockProperty(new BlockPos(2, 1, 2), ABoardSpaceBlock.TILE_TYPE, BoardSpaceType.TILE_ADVANCE_BACK);
        context.assertEquals(TileFeedback.landingOf(tile), Landing.ADVANCE, "forward landing");
        context.assertEquals(TileFeedback.tileColor(tile), AdvanceBackCartridgeItem.FORWARD_COLOR, "green");
        tile.setStack(0, AdvanceBackCartridgeItem.withSteps(-3));
        context.assertEquals(TileFeedback.landingOf(tile), Landing.BACK, "back landing");
        context.assertEquals(TileFeedback.tileColor(tile), AdvanceBackCartridgeItem.BACK_COLOR, "pink-magenta");
        context.complete();
    }
}
