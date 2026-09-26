package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.board.BoardGraph;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.board.BoardValidator;
import fr.lordfinn.steveparty.board.WrenchActions;
import fr.lordfinn.steveparty.board.WrenchMode;
import fr.lordfinn.steveparty.board.WrenchState;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.payloads.custom.WrenchActionPayload;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock.TILE_TYPE;

/** Linking board spaces with the Wrench: chains, loops, forks, joins, modes, cartridges supplied and links kept. */
public class BoardLinkingGameTests implements FabricGameTest {

    /** Tiles on a stone floor at y = 1, returned in absolute positions. */
    static List<BlockPos> tiles(TestContext context, Block block, BlockPos... relative) {
        List<BlockPos> absolute = new ArrayList<>();
        for (BlockPos pos : relative) {
            context.setBlockState(pos.down(), Blocks.STONE);
            context.setBlockState(pos, block);
            absolute.add(context.getAbsolutePos(pos));
        }
        return absolute;
    }

    static BoardSpaceBlockEntity boardSpace(TestContext context, BlockPos absolute) {
        return (BoardSpaceBlockEntity) context.getWorld().getBlockEntity(absolute);
    }

    static List<BlockPos> links(TestContext context, BlockPos absolute) {
        BoardSpaceBlockEntity boardSpace = boardSpace(context, absolute);
        return BoardLinks.links(boardSpace, boardSpace.getActiveSlot());
    }

    static ItemStack wrench(ServerPlayerEntity player) {
        ItemStack wrench = new ItemStack(ModItems.WRENCH);
        player.setStackInHand(Hand.MAIN_HAND, wrench);
        return player.getMainHandStack();
    }

    static void click(ServerPlayerEntity player, ItemStack wrench, TestContext context, BlockPos absolute) {
        WrenchActions.useOnBlock(player, wrench, context.getWorld(), absolute);
    }

    /** Runs {@code test} with a mock player, always removed afterwards. */
    static void withPlayer(TestContext context, boolean creative, Consumer<ServerPlayerEntity> test) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.getAbilities().creativeMode = creative;
        try {
            test.accept(player);
        } finally {
            context.getWorld().getServer().getPlayerManager().remove(player);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void traceLinksAChainAndClosesTheLoop(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.SIMPLE_TILE,
                new BlockPos(1, 1, 1), new BlockPos(4, 1, 1), new BlockPos(4, 1, 4), new BlockPos(1, 1, 4));
        withPlayer(context, true, player -> {
            ItemStack wrench = wrench(player);
            for (BlockPos pos : t) click(player, wrench, context, pos);
            context.assertEquals(WrenchActions.origin(wrench, context.getWorld()), t.get(3), "the last tile is the origin");
            context.assertEquals(WrenchState.of(wrench).chainLength(), 4, "chain of 4");
            click(player, wrench, context, t.getFirst()); // close the loop
            for (int i = 0; i < 4; i++) {
                context.assertEquals(links(context, t.get(i)), List.of(t.get((i + 1) % 4)), "tile " + i + " links to the next one");
            }
            context.assertTrue(WrenchActions.origin(wrench, context.getWorld()) == null, "closing the loop ends the chain");
            // Each tile faces its next one: east, south, west, north
            int[] rotations = {2, 4, 6, 0};
            for (int i = 0; i < 4; i++) {
                context.assertEquals(context.getWorld().getBlockState(t.get(i)).get(ATileBlock.ROTATION_8), rotations[i], "rotation of tile " + i);
            }
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void holdingTheButtonOverTheOriginDoesNotEndTheChain(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.SIMPLE_TILE, new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1));
        withPlayer(context, true, player -> {
            ItemStack wrench = wrench(player);
            click(player, wrench, context, t.get(0));
            click(player, wrench, context, t.get(1));
            click(player, wrench, context, t.get(1)); // the vanilla repeat while the button is held, same tick
            context.assertEquals(WrenchActions.origin(wrench, context.getWorld()), t.get(1), "still tracing from the second tile");
            click(player, wrench, context, t.get(2));
            context.assertEquals(links(context, t.get(1)), List.of(t.get(2)), "the sweep goes on");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void startingOnALinkedTileForksAndEndingOnOneJoins(TestContext context) {
        // a → b → c → d, then a branch a → e → c
        List<BlockPos> t = tiles(context, ModBlocks.SIMPLE_TILE, new BlockPos(1, 1, 1), new BlockPos(3, 1, 1),
                new BlockPos(5, 1, 1), new BlockPos(7, 1, 1), new BlockPos(3, 1, 4));
        BlockPos a = t.get(0), b = t.get(1), c = t.get(2), d = t.get(3), e = t.get(4);
        withPlayer(context, true, player -> {
            ItemStack wrench = wrench(player);
            for (BlockPos pos : List.of(a, b, c, d)) click(player, wrench, context, pos);
            player.setSneaking(true);
            WrenchActions.use(player, wrench, context.getWorld()); // sneak + right click in the air: end
            player.setSneaking(false);
            context.assertTrue(WrenchActions.origin(wrench, context.getWorld()) == null, "chain ended");
            int facing = context.getWorld().getBlockState(a).get(ATileBlock.ROTATION_8);

            click(player, wrench, context, a); // fork
            click(player, wrench, context, e);
            click(player, wrench, context, c); // join
            context.assertEquals(links(context, a), List.of(b, e), "a forks toward b and e");
            context.assertEquals(links(context, e), List.of(c), "the branch joins c");
            context.assertEquals(links(context, c), List.of(d), "c unchanged");
            context.assertTrue(WrenchActions.origin(wrench, context.getWorld()) == null, "joining ends the chain");
            context.assertEquals(context.getWorld().getBlockState(a).get(ATileBlock.ROTATION_8), facing, "a fork keeps the first direction");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void editTogglesAndCutClears(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.SIMPLE_TILE, new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(3, 1, 3));
        withPlayer(context, true, player -> {
            ItemStack wrench = wrench(player);
            WrenchActions.control(player, wrench, WrenchActionPayload.Action.MODE, 1);
            context.assertEquals(WrenchState.of(wrench).mode(), WrenchMode.EDIT, "Trace → Edit");
            click(player, wrench, context, t.get(0));
            click(player, wrench, context, t.get(1));
            click(player, wrench, context, t.get(2));
            context.assertEquals(links(context, t.get(0)), List.of(t.get(1), t.get(2)), "Edit adds from a fixed origin");
            click(player, wrench, context, t.get(1));
            context.assertEquals(links(context, t.get(0)), List.of(t.get(2)), "a second click removes");
            context.assertEquals(WrenchActions.origin(wrench, context.getWorld()), t.get(0), "the origin stays");

            WrenchActions.control(player, wrench, WrenchActionPayload.Action.MODE, 1);
            context.assertEquals(WrenchState.of(wrench).mode(), WrenchMode.CUT, "Edit → Cut");
            click(player, wrench, context, t.get(0));
            context.assertTrue(links(context, t.get(0)).isEmpty(), "Cut removes the outgoing links");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void survivalTakesCartridgesFromTheInventoryWithoutTheirLinks(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.SIMPLE_TILE, new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1));
        withPlayer(context, false, player -> {
            ItemStack wrench = wrench(player);
            ItemStack cartridges = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR, 1);
            // A stack already used as a selector: its links must not end up in the tiles
            cartridges.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(new ArrayList<>(List.of(new BlockPos(0, 0, 0))), ""));
            player.getInventory().setStack(20, cartridges);
            click(player, wrench, context, t.get(0));
            click(player, wrench, context, t.get(1));
            context.assertEquals(links(context, t.get(0)), List.of(t.get(1)), "first link, with the inventory's cartridge");
            context.assertTrue(player.getInventory().getStack(20).isEmpty(), "the cartridge was taken");
            click(player, wrench, context, t.get(2));
            context.assertTrue(links(context, t.get(1)).isEmpty(), "no cartridge left: no link");
            context.assertTrue(boardSpace(context, t.get(1)).getStack(0).isEmpty(), "and no cartridge inserted");
            context.assertTrue(WrenchActions.origin(wrench, context.getWorld()) == null, "the chain stops");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void replacingTheCartridgeKeepsTheLinks(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.SIMPLE_TILE, new BlockPos(1, 1, 1), new BlockPos(3, 1, 1));
        withPlayer(context, true, player -> {
            ItemStack wrench = wrench(player);
            click(player, wrench, context, t.get(0));
            click(player, wrench, context, t.get(1));
            player.setSneaking(true);
            WrenchActions.use(player, wrench, context.getWorld());
            player.setSneaking(false);

            // From the interface (or a hopper): take the cartridge out, put another type in
            BoardSpaceBlockEntity first = boardSpace(context, t.get(0));
            first.removeStack(0);
            first.setStack(0, new ItemStack(ModItems.TILE_BEHAVIOR_START));
            context.assertEquals(first.getCachedState().get(TILE_TYPE), BoardSpaceType.TILE_START, "now a start tile");
            context.assertEquals(links(context, t.get(0)), List.of(t.get(1)), "links kept through the interface");

            // With the Wrench: another cartridge in the off hand, one click
            player.setStackInHand(Hand.OFF_HAND, new ItemStack(ModItems.INVENTORY_CARTRIDGE));
            click(player, wrench, context, t.get(0));
            context.assertEquals(context.getWorld().getBlockState(t.get(0)).get(TILE_TYPE), BoardSpaceType.TILE_INVENTORY_INTERACTOR, "swapped");
            context.assertEquals(links(context, t.get(0)), List.of(t.get(1)), "links kept through the Wrench");
            context.assertTrue(WrenchActions.origin(wrench, context.getWorld()) == null, "a swap does not start a chain");
        });
    }

    /** Builds s → a → b ⑂ (c, d), d → a, and a lonely e; s is a start tile. */
    static List<BlockPos> smallBoard(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.SIMPLE_TILE, new BlockPos(1, 1, 1), new BlockPos(3, 1, 1),
                new BlockPos(5, 1, 1), new BlockPos(7, 1, 1), new BlockPos(5, 1, 3), new BlockPos(1, 1, 5));
        link(context, t.get(0), t.get(1));
        link(context, t.get(1), t.get(2));
        link(context, t.get(2), t.get(3), t.get(4));
        link(context, t.get(4), t.get(1));
        ItemStack start = new ItemStack(ModItems.TILE_BEHAVIOR_START);
        start.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(new ArrayList<>(List.of(t.get(1))), ""));
        boardSpace(context, t.get(0)).setStack(0, start);
        return t;
    }

    /** This test's area only (tests run side by side). */
    static net.minecraft.util.math.BlockBox box(TestContext context) {
        return net.minecraft.util.math.BlockBox.create(context.getAbsolutePos(new BlockPos(0, 0, 0)), context.getAbsolutePos(new BlockPos(8, 3, 8)));
    }

    static void link(TestContext context, BlockPos from, BlockPos... to) {
        ItemStack cartridge = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR);
        cartridge.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(new ArrayList<>(List.of(to)), ""));
        boardSpace(context, from).setStack(0, cartridge);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void boardGraphNumbersTheSpacesAndFindsTheProblems(TestContext context) {
        List<BlockPos> t = smallBoard(context);
        BoardGraph graph = BoardGraph.collect(context.getWorld(), box(context));
        context.assertEquals(graph.nodes().size(), 6, "6 board spaces");
        context.assertEquals(graph.distance(t.get(0)), 0, "start");
        context.assertEquals(graph.distance(t.get(2)), 2, "b is 2 steps away");
        context.assertEquals(graph.distance(t.get(3)), 3, "c is 3 steps away");
        context.assertTrue(graph.isFork(graph.node(t.get(2))), "b is a fork");
        context.assertTrue(graph.isDeadEnd(graph.node(t.get(3))), "c is a dead end");
        context.assertTrue(graph.isUnreachable(graph.node(t.get(5))), "e is unreachable");
        context.assertTrue(!graph.isUnreachable(graph.node(t.get(4))), "d is reachable");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void boardCheckReportsEachProblem(TestContext context) {
        List<BlockPos> t = smallBoard(context);
        // e links to a block that is no board space
        context.setBlockState(new BlockPos(1, 1, 7), Blocks.STONE);
        link(context, t.get(5), context.getAbsolutePos(new BlockPos(1, 1, 7)));
        BoardValidator.Report report = BoardValidator.check(BoardGraph.collect(context.getWorld(), box(context)));
        java.util.Map<String, List<BlockPos>> issues = new java.util.HashMap<>();
        for (BoardValidator.Issue issue : report.issues()) issues.put(issue.key(), issue.positions());
        context.assertEquals(report.starts(), 1, "one start");
        context.assertEquals(java.util.Set.copyOf(issues.get("dead_ends")), java.util.Set.of(t.get(3), t.get(5)), "c and e are dead ends");
        context.assertEquals(issues.get("unreachable"), List.of(t.get(5)), "e is unreachable");
        context.assertEquals(issues.get("broken_links"), List.of(t.get(5)), "e's link is broken");
        context.assertEquals(issues.get("no_token"), List.of(t.get(0)), "the start has no token");
        context.assertTrue(!issues.containsKey("no_start"), "a start exists");
        context.assertTrue(!report.ok(), "not ok");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void diagonalsAreOriented(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.SIMPLE_TILE, new BlockPos(1, 1, 1), new BlockPos(4, 1, 4));
        withPlayer(context, true, player -> {
            ItemStack wrench = wrench(player);
            click(player, wrench, context, t.get(0));
            click(player, wrench, context, t.get(1));
            context.assertEquals(context.getWorld().getBlockState(t.get(0)).get(ATileBlock.ROTATION_8), 3, "south-east");
            ServerWorld world = context.getWorld();
            context.assertEquals(BoardLinks.rotationToward(world, t.get(1), t.get(0)), 7, "north-west");
        });
    }
}
