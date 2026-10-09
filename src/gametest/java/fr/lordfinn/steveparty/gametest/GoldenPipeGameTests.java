package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileTeleport;
import fr.lordfinn.steveparty.board.BoardGraph;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.powerups.effects.GoldenPipeEffect;
import fr.lordfinn.steveparty.powerups.effects.StarLocator;
import fr.lordfinn.steveparty.service.AdvanceBackMoves;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The Golden Pipe power-up: the pawn is warped to the board space just before the Star (closest to a start tile,
 * through check points), on the Star itself when no space leads there; no Star: nothing happens and it is not used up.
 * The Star is given by a test {@link StarLocator}.
 */
public class GoldenPipeGameTests implements SteveGameTest {
    private static final int WAIT = TileTeleport.TOTAL_TICKS + 6;
    private static final BlockPos CONTROLLER = new BlockPos(1, 1, 7);
    /** Start, then three tiles, then the Star's tile: 2 blocks apart along x on z = 1, then on to z = 3. */
    private static final List<BlockPos> PATH = List.of(
            new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1), new BlockPos(7, 1, 1), new BlockPos(7, 1, 3));
    private static final BlockPos STAR = PATH.get(4);
    /** A branch no start leads to: a tile, then a check point, then the Star. */
    private static final BlockPos BRANCH = new BlockPos(3, 1, 5), CHECK_POINT = new BlockPos(5, 1, 5);

    // ---------------------------------------------------------------- board

    private static BoardSpaceBlockEntity space(TestContext context, Block block, BlockPos pos, ItemStack cartridge, BlockPos... to) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, block);
        List<BlockPos> destinations = new ArrayList<>();
        for (BlockPos next : to) destinations.add(context.getAbsolutePos(next));
        if (!destinations.isEmpty()) cartridge.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(destinations, ""));
        BoardSpaceBlockEntity space = context.getBlockEntity(pos);
        space.setStack(0, cartridge);
        return space;
    }

    private static ItemStack plain() {
        return new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR);
    }

    /** The start tile and the tiles of {@link #PATH}, each linked to the next, the Star's last. */
    private static void path(TestContext context) {
        space(context, ModBlocks.TILE, PATH.get(0), new ItemStack(ModItems.TILE_BEHAVIOR_START), PATH.get(1));
        for (int i = 1; i < PATH.size(); i++) {
            BlockPos[] next = i + 1 < PATH.size() ? new BlockPos[]{PATH.get(i + 1)} : new BlockPos[0];
            space(context, ModBlocks.TILE, PATH.get(i), plain(), next);
        }
    }

    private static PigEntity token(TestContext context, BlockPos tile) {
        PigEntity pig = context.spawnMob(EntityType.PIG, tile);
        TokenizedEntityInterface token = (TokenizedEntityInterface) pig;
        token.steveparty$setTokenized(true);
        token.steveparty$setStatus(TokenStatus.setStatuses(0, TokenStatus.IN_GAME, TokenStatus.CAN_MOVE));
        Vec3d stand = BoardSpaces.standPos(context.getWorld(), context.getAbsolutePos(tile));
        pig.refreshPositionAndAngles(stand.x, stand.y, stand.z, 0, 0);
        return pig;
    }

    private static PartyControllerEntity party(TestContext context) {
        context.setBlockState(CONTROLLER.down(), Blocks.STONE);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        return context.getBlockEntity(CONTROLLER);
    }

    /** A Star on {@code relative}, whatever the party. */
    private static StarLocator starOn(TestContext context, BlockPos relative) {
        BlockPos absolute = context.getAbsolutePos(relative);
        return party -> Optional.of(absolute);
    }

    private static List<TileTeleport.Teleported> teleports(MobEntity token, List<Runnable> cleanups) {
        List<TileTeleport.Teleported> list = new ArrayList<>();
        Consumer<TileTeleport.Teleported> listener = teleported -> {
            if (teleported.token().equals(token.getUuid())) list.add(teleported);
        };
        TileTeleport.LISTENERS.add(listener);
        cleanups.add(() -> TileTeleport.LISTENERS.remove(listener));
        return list;
    }

    private static void assertStandsOn(TestContext context, MobEntity token, BlockPos relative, String what) {
        BlockPos absolute = context.getAbsolutePos(relative);
        Vec3d stand = BoardSpaces.standPos(context.getWorld(), absolute);
        context.assertTrue(token.getPos().distanceTo(stand) < 0.05, what + ": stands on " + stand + ", is at " + token.getPos());
        BoardSpaceBlockEntity on = BoardSpaces.boardSpaceOf(token);
        context.assertTrue(on != null && on.getPos().equals(absolute), what + ": on " + absolute + ", found " + (on == null ? null : on.getPos()));
    }

    private static void untokenize(MobEntity token) {
        ((TokenizedEntityInterface) token).steveparty$setTokenized(false);
        AdvanceBackMoves.forgetTrail(token.getUuid());
    }

    // ---------------------------------------------------------------- tests

    /**
     * A board of five tiles and a branch: the spaces before the Star are the tile linked to it and, through a check
     * point, the branch's tile; the one a start leads to is picked, every time.
     */
    @GameTest(batchId = "golden_pipe", templateName = EMPTY_STRUCTURE)
    public void theSpaceBeforeTheStarIsFoundOnTheBoard(TestContext context) {
        path(context);
        BlockPos star = context.getAbsolutePos(STAR), before = context.getAbsolutePos(PATH.get(3));
        BoardGraph graph = BoardGraph.collect(context.getWorld(), star, GoldenPipeEffect.BOARD_RANGE);
        context.assertEquals(GoldenPipeEffect.spacesBefore(graph, star), List.of(before), "the tile linked to the Star");
        context.assertEquals(GoldenPipeEffect.spaceBefore(graph, star, Random.create(1)), Optional.of(before), "picked");

        space(context, ModBlocks.TILE, BRANCH, plain(), CHECK_POINT);
        space(context, ModBlocks.CHECK_POINT, CHECK_POINT, plain(), STAR);
        graph = BoardGraph.collect(context.getWorld(), star, GoldenPipeEffect.BOARD_RANGE);
        context.assertEquals(Set.copyOf(GoldenPipeEffect.spacesBefore(graph, star)), Set.of(before, context.getAbsolutePos(BRANCH)),
                "the branch's tile too, through the check point (not the check point)");
        Random random = Random.create(7);
        for (int i = 0; i < 20; i++) {
            context.assertEquals(GoldenPipeEffect.spaceBefore(graph, star, random), Optional.of(before),
                    "the one a start leads to");
        }
        context.assertEquals(GoldenPipeEffect.destination(context.getWorld(), star, random), before, "the destination");
        context.complete();
    }

    /** Using it: « X takes the Golden Pipe », the gold warp, and the pawn stands on the space before the Star. */
    @GameTest(batchId = "golden_pipe", templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void thePawnArrivesOnTheSpaceBeforeTheStar(TestContext context) {
        path(context);
        PigEntity pig = token(context, PATH.get(0));
        List<Runnable> cleanups = new ArrayList<>();
        List<TileTeleport.Teleported> teleports = teleports(pig, cleanups);
        GoldenPipeEffect effect = new GoldenPipeEffect(starOn(context, STAR));
        PartyControllerEntity party = party(context);
        boolean[] arrived = {false};

        GoldenPipeEffect.Result result = effect.use(party, pig, null, () -> arrived[0] = true);
        BlockPos before = context.getAbsolutePos(PATH.get(3));
        context.assertEquals(result.outcome(), GoldenPipeEffect.Outcome.TELEPORTED, "warped");
        context.assertTrue(result.consumed(), "used up");
        context.assertEquals(result.destination(), before, "to the space before the Star");
        context.assertTrue(TileTeleport.isTeleporting(pig), "warping");
        context.assertTrue(!TokenStatus.hasStatus(((TokenizedEntityInterface) pig).steveparty$getStatus(), TokenStatus.CAN_MOVE),
                "no move during the warp");
        context.assertEquals(effect.use(party, pig, null, () -> {}).outcome(), GoldenPipeEffect.Outcome.BUSY, "not twice at once");

        context.waitAndRun(WAIT, () -> {
            assertStandsOn(context, pig, PATH.get(3), "on the space before the Star");
            context.assertTrue(arrived[0], "the turn goes on");
            context.assertTrue(TokenStatus.hasStatus(((TokenizedEntityInterface) pig).steveparty$getStatus(), TokenStatus.CAN_MOVE),
                    "may move again (its roll)");
            context.assertEquals(teleports.size(), 1, "one warp");
            context.assertEquals(teleports.getFirst().from(), context.getAbsolutePos(PATH.get(0)), "from its space");
            context.assertEquals(AdvanceBackMoves.trail(pig), List.of(before), "its path starts again there");
            GoldenPipeEffect.Result again = effect.use(party, pig, null, () -> {});
            context.assertEquals(again.outcome(), GoldenPipeEffect.Outcome.ALREADY_THERE, "already there");
            context.assertTrue(!again.consumed(), "not used up");
            cleanups.forEach(Runnable::run);
            untokenize(pig);
            context.complete();
        });
    }

    /** No space leads to the Star: the pawn is put on the Star's space itself. */
    @GameTest(batchId = "golden_pipe", templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void withNoSpaceBeforeThePawnGoesOnTheStar(TestContext context) {
        space(context, ModBlocks.TILE, PATH.get(0), plain());
        space(context, ModBlocks.TILE, STAR, plain());
        BlockPos star = context.getAbsolutePos(STAR);
        BoardGraph graph = BoardGraph.collect(context.getWorld(), star, GoldenPipeEffect.BOARD_RANGE);
        context.assertTrue(GoldenPipeEffect.spacesBefore(graph, star).isEmpty(), "no space before");
        context.assertEquals(GoldenPipeEffect.spaceBefore(graph, star, Random.create(3)), Optional.empty(), "none picked");
        PigEntity pig = token(context, PATH.get(0));
        GoldenPipeEffect effect = new GoldenPipeEffect(starOn(context, STAR));

        GoldenPipeEffect.Result result = effect.use(party(context), pig, null, () -> {});
        context.assertEquals(result.outcome(), GoldenPipeEffect.Outcome.TELEPORTED, "warped");
        context.assertEquals(result.destination(), star, "onto the Star");
        context.waitAndRun(WAIT, () -> {
            assertStandsOn(context, pig, STAR, "on the Star");
            untokenize(pig);
            context.complete();
        });
    }

    /** No Star on the board: nothing happens, the power-up is not used up. */
    @GameTest(batchId = "golden_pipe", templateName = EMPTY_STRUCTURE)
    public void withNoStarNothingHappens(TestContext context) {
        path(context);
        PigEntity pig = token(context, PATH.get(1));
        PartyControllerEntity party = party(context);
        boolean[] arrived = {false};

        GoldenPipeEffect.Result result = new GoldenPipeEffect(StarLocator.NONE).use(party, pig, null, () -> arrived[0] = true);
        context.assertEquals(result.outcome(), GoldenPipeEffect.Outcome.NO_STAR, "no Star");
        context.assertTrue(!result.consumed(), "not used up");
        context.assertTrue(!TileTeleport.isTeleporting(pig), "not warping");
        // A Star on a space that is not a board space (any more): the same
        GoldenPipeEffect.Result broken = new GoldenPipeEffect(starOn(context, new BlockPos(5, 1, 7))).use(party, pig, null, () -> arrived[0] = true);
        context.assertEquals(broken.outcome(), GoldenPipeEffect.Outcome.NO_STAR, "no Star on a block that is no board space");
        context.waitAndRun(WAIT, () -> {
            assertStandsOn(context, pig, PATH.get(1), "still on its space");
            context.assertTrue(!arrived[0], "nothing to go on with");
            context.assertTrue(TokenStatus.hasStatus(((TokenizedEntityInterface) pig).steveparty$getStatus(), TokenStatus.CAN_MOVE),
                    "may still roll");
            untokenize(pig);
            context.complete();
        });
    }
}
