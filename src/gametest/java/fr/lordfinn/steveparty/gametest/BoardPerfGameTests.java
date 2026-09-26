package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.board.BoardGraph;
import fr.lordfinn.steveparty.board.BoardPerf;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * A benchmark of the board's server side work on a generated 200-tile board (a path through 4 layers of 8x8 Advanced
 * Tiles, a Router driving the first 100): the counters of {@link BoardPerf} and the time of each operation are logged
 * ({@code [board perf]} lines) to compare a change against the previous code. The asserts only check what must stay
 * true (no work without a change).
 */
public class BoardPerfGameTests implements FabricGameTest {
    private static final int TILES = 200, ROUTED = 100, SIDE = 8;
    private static final BlockPos ROUTER = new BlockPos(7, 7, 7);
    private static final BlockPos SWITCH = ROUTER.north();

    /** The 200 tile positions (relative), in path order: 8x8 layers at y = 1, 3, 5, 7 (stone under each). */
    private static List<BlockPos> tilePositions() {
        List<BlockPos> positions = new ArrayList<>();
        for (int y = 1; y < SIDE && positions.size() < TILES; y += 2) {
            for (int x = 0; x < SIDE && positions.size() < TILES; x++) {
                for (int i = 0; i < SIDE && positions.size() < TILES; i++) {
                    int z = x % 2 == 0 ? i : SIDE - 1 - i; // a snake: each tile next to the previous one
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return positions;
    }

    private static void log(String what, long nanos, int repeats) {
        Steveparty.LOGGER.info("[board perf] {}: {} us/op over {} ops | {}", what, nanos / 1000 / Math.max(1, repeats), repeats, BoardPerf.summary());
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "board_perf")
    public void benchmark200TileBoard(TestContext context) {
        ServerWorld world = context.getWorld();
        List<BlockPos> tiles = tilePositions();
        List<BlockPos> absolute = tiles.stream().map(context::getAbsolutePos).toList();
        for (int y = 0; y < SIDE; y += 2) {
            for (int x = 0; x < SIDE; x++) for (int z = 0; z < SIDE; z++) context.setBlockState(new BlockPos(x, y, z), Blocks.STONE);
        }
        long start = System.nanoTime();
        for (int i = 0; i < TILES; i++) {
            context.setBlockState(tiles.get(i), ModBlocks.ADVANCED_TILE);
            BoardSpaceBlockEntity tile = context.getBlockEntity(tiles.get(i));
            ItemStack cartridge = new ItemStack(i == 0 ? ModItems.TILE_BEHAVIOR_START : ModItems.BOARD_SPACE_BEHAVIOR);
            if (i + 1 < TILES) cartridge.set(ModComponents.DESTINATIONS_COMPONENT,
                    new DestinationsComponent(new ArrayList<>(List.of(absolute.get(i + 1))), ""));
            tile.setStack(0, cartridge);
            tile.setStack(15, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP));
        }
        log("build 200 tiles", System.nanoTime() - start, TILES);

        context.setBlockState(ROUTER, ModBlocks.BOARD_SPACE_REDSTONE_ROUTER);
        BoardSpaceRedstoneRouterBlockEntity router = context.getBlockEntity(ROUTER);
        ItemStack routerCartridge = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR);
        routerCartridge.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(new ArrayList<>(absolute.subList(0, ROUTED)), ""));
        BoardPerf.reset();
        start = System.nanoTime();
        router.setStack(0, routerCartridge);
        log("route 100 tiles", System.nanoTime() - start, 1);

        // The board graph (the Wrench's board view and the board check build it)
        BlockBox box = BlockBox.create(context.getAbsolutePos(BlockPos.ORIGIN), context.getAbsolutePos(new BlockPos(SIDE - 1, SIDE - 1, SIDE - 1)));
        BoardGraph graph = BoardGraph.collect(world, box);
        context.assertEquals(graph.nodes().size(), TILES, "the whole board");
        context.assertEquals(graph.distance(absolute.getLast()), TILES - 1, "a single path");
        BoardPerf.reset();
        start = System.nanoTime();
        for (int i = 0; i < 50; i++) BoardGraph.collect(world, box);
        log("board graph", System.nanoTime() - start, 50);
        // Where the board view draws a link's ends (read per link and label, see BoardView on the client)
        start = System.nanoTime();
        double sink = 0;
        for (int i = 0; i < 20; i++) for (BlockPos pos : absolute) sink += fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces.standPos(world, pos).y;
        Steveparty.LOGGER.info("[board perf] tile anchor (stand position) read: {} ns/op over {} ops",
                (System.nanoTime() - start) / (20 * TILES), 20 * TILES);
        context.assertTrue(!Double.isNaN(sink), "anchors read");

        // The router's power switched on and off: 100 tiles change their role each time
        BoardPerf.reset();
        start = System.nanoTime();
        for (int i = 0; i < 10; i++) {
            context.setBlockState(SWITCH, Blocks.REDSTONE_BLOCK);
            context.removeBlock(SWITCH);
        }
        log("router power toggles (100 tiles each)", System.nanoTime() - start, 20);
        long togglesPushes = BoardPerf.routerPushLookups;
        context.assertTrue(BoardPerf.boardSpaceSyncs <= 20L * ROUTED, "each tile changing its role is sent once: " + BoardPerf.boardSpaceSyncs);
        BoardSpaceBlockEntity routed = context.getBlockEntity(tiles.get(ROUTED - 1)), free = context.getBlockEntity(tiles.get(ROUTED));
        context.assertEquals(routed.getActiveSlot(), 0, "routed tiles follow the (unpowered) router");
        context.assertEquals(free.getActiveSlot(), 0, "the others their own power");

        // Neighbour updates that change nothing (redstone dust around, blocks placed next to the router or the tiles)
        BoardPerf.reset();
        start = System.nanoTime();
        BlockPos routerAbs = context.getAbsolutePos(ROUTER);
        BlockState routerState = world.getBlockState(routerAbs);
        for (int i = 0; i < 200; i++) routerState.neighborUpdate(world, routerAbs, Blocks.STONE, null, false);
        log("router neighbour updates without power change", System.nanoTime() - start, 200);
        long idlePushes = BoardPerf.routerPushLookups;

        BoardPerf.reset();
        start = System.nanoTime();
        for (BlockPos pos : absolute) world.getBlockState(pos).neighborUpdate(world, pos, Blocks.STONE, null, false);
        log("tile neighbour updates without power change", System.nanoTime() - start, TILES);
        long idleSyncs = BoardPerf.boardSpaceSyncs;

        Steveparty.LOGGER.info("[board perf] pushes while toggling: {}, pushes without change: {}, syncs without change: {}",
                togglesPushes, idlePushes, idleSyncs);
        context.assertEquals(idleSyncs, 0L, "nothing sent to the clients when nothing changed");
        context.assertEquals(idlePushes, 0L, "a router pushes only a change of its power");
        // Still routed after all that: the router's power drives its tiles
        context.setBlockState(SWITCH, Blocks.REDSTONE_BLOCK);
        context.assertEquals(routed.getActiveSlot(), 15, "the powered router's slot");
        context.assertEquals(free.getActiveSlot(), 0, "not routed: unchanged");
        context.complete();
    }
}
