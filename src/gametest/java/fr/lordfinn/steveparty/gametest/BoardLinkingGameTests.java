package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.board.BoardGraph;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.board.BoardValidator;
import fr.lordfinn.steveparty.board.WrenchActions;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.payloads.custom.ToolWheelPayload;
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

/**
 * Linking board spaces: painted with the Tile Linker Brush (chains, loops, forks, joins, levels, cartridges supplied,
 * its wheel's picks), the Wrench opening the spaces and swapping their cartridge, links kept.
 */
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

    static ItemStack brush(ServerPlayerEntity player) {
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.TILE_LINKER_BRUSH));
        return player.getMainHandStack();
    }

    /** A Wrench right click on a block (the Wrench's own action). */
    static void click(ServerPlayerEntity player, ItemStack wrench, TestContext context, BlockPos absolute) {
        WrenchActions.useOnBlock(player, wrench, context.getWorld(), absolute);
    }

    /** One stroke of the brush over {@code stroke}, in order, then the button released. */
    static void paint(ServerPlayerEntity player, ItemStack brush, TestContext context, BlockPos... stroke) {
        for (BlockPos pos : stroke) TileLinkerBrush.paint(player, brush, context.getWorld(), pos);
        TileLinkerBrush.endStroke(player);
    }

    /** A sector picked on the wheel of the tool in hand, as the client sends it. */
    static boolean pick(ServerPlayerEntity player, ToolWheelPayload.Action action, int value) {
        return new ToolWheelPayload(action, value).handle(player);
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
    public void theBrushLinksAChainAndClosesTheLoop(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.TILE,
                new BlockPos(1, 1, 1), new BlockPos(4, 1, 1), new BlockPos(4, 1, 4), new BlockPos(1, 1, 4));
        withPlayer(context, true, player -> {
            ItemStack brush = brush(player);
            paint(player, brush, context, t.get(0), t.get(1), t.get(2), t.get(3), t.get(0));
            for (int i = 0; i < 4; i++) {
                context.assertEquals(links(context, t.get(i)), List.of(t.get((i + 1) % 4)), "tile " + i + " links to the next one");
            }
            context.assertEquals(TileLinkerBrush.anchor(brush, context.getWorld()), t.get(0), "the anchor: the last tile painted");
            // Each tile faces its next one: east, south, west, north
            int[] rotations = {2, 4, 6, 0};
            for (int i = 0; i < 4; i++) {
                context.assertEquals(context.getWorld().getBlockState(t.get(i)).get(ATileBlock.ROTATION_8), rotations[i], "rotation of tile " + i);
            }
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void holdingTheBrushOverATileDoesNotRepaintIt(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.TILE, new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1));
        withPlayer(context, true, player -> {
            ItemStack brush = brush(player);
            paint(player, brush, context, t.get(0), t.get(1), t.get(1), t.get(2)); // the same tile on two ticks
            context.assertEquals(links(context, t.get(0)), List.of(t.get(1)), "first link");
            context.assertEquals(links(context, t.get(1)), List.of(t.get(2)), "the sweep goes on");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void startingOnALinkedTileForksAndEndingOnOneJoins(TestContext context) {
        // a → b → c → d, then a branch a → e → c
        List<BlockPos> t = tiles(context, ModBlocks.TILE, new BlockPos(1, 1, 1), new BlockPos(3, 1, 1),
                new BlockPos(5, 1, 1), new BlockPos(7, 1, 1), new BlockPos(3, 1, 4));
        BlockPos a = t.get(0), b = t.get(1), c = t.get(2), d = t.get(3), e = t.get(4);
        withPlayer(context, true, player -> {
            ItemStack brush = brush(player);
            paint(player, brush, context, a, b, c, d);
            int facing = context.getWorld().getBlockState(a).get(ATileBlock.ROTATION_8);
            paint(player, brush, context, a, e, c);
            context.assertEquals(links(context, a), List.of(b, e), "a forks toward b and e");
            context.assertEquals(links(context, e), List.of(c), "the branch joins c");
            context.assertEquals(links(context, c), List.of(d), "c unchanged");
            context.assertEquals(context.getWorld().getBlockState(a).get(ATileBlock.ROTATION_8), facing, "a fork keeps the first direction");
        });
    }

    /** #65: the Wrench opens a board space with a plain right click, no sneaking. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theWrenchOpensABoardSpaceWithoutSneaking(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.TILE, new BlockPos(1, 1, 1));
        withPlayer(context, true, player -> {
            ItemStack wrench = wrench(player);
            player.setSneaking(false);
            click(player, wrench, context, t.getFirst());
            context.assertTrue(player.currentScreenHandler != player.playerScreenHandler, "its interface is open");
            context.assertTrue(links(context, t.getFirst()).isEmpty(), "and nothing linked");
            player.closeHandledScreen();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void survivalTakesCartridgesFromTheInventoryWithoutTheirLinks(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.TILE, new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1));
        withPlayer(context, false, player -> {
            ItemStack brush = brush(player);
            ItemStack cartridges = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR, 1);
            // A stack already used as a selector: its links must not end up in the tiles
            cartridges.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(new ArrayList<>(List.of(new BlockPos(0, 0, 0))), ""));
            player.getInventory().setStack(20, cartridges);
            paint(player, brush, context, t.get(0), t.get(1), t.get(2));
            context.assertEquals(links(context, t.get(0)), List.of(t.get(1)), "first link, with the inventory's cartridge");
            context.assertTrue(player.getInventory().getStack(20).isEmpty(), "the cartridge was taken");
            context.assertTrue(links(context, t.get(1)).isEmpty(), "no cartridge left: no link");
            context.assertTrue(boardSpace(context, t.get(1)).getStack(0).isEmpty(), "and no cartridge inserted");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void replacingTheCartridgeKeepsTheLinks(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.TILE, new BlockPos(1, 1, 1), new BlockPos(3, 1, 1));
        withPlayer(context, true, player -> {
            paint(player, brush(player), context, t.get(0), t.get(1));

            // From the interface (or a hopper): take the cartridge out, put another type in
            BoardSpaceBlockEntity first = boardSpace(context, t.get(0));
            first.removeStack(0);
            first.setStack(0, new ItemStack(ModItems.TILE_BEHAVIOR_START));
            context.assertEquals(first.getCachedState().get(TILE_TYPE), BoardSpaceType.TILE_START, "now a start tile");
            context.assertEquals(links(context, t.get(0)), List.of(t.get(1)), "links kept through the interface");

            // With the Wrench: another cartridge in the off hand, one click
            ItemStack wrench = wrench(player);
            player.setStackInHand(Hand.OFF_HAND, new ItemStack(ModItems.INVENTORY_CARTRIDGE));
            click(player, wrench, context, t.get(0));
            context.assertEquals(context.getWorld().getBlockState(t.get(0)).get(TILE_TYPE), BoardSpaceType.TILE_INVENTORY_INTERACTOR, "swapped");
            context.assertEquals(links(context, t.get(0)), List.of(t.get(1)), "links kept through the Wrench");
            context.assertTrue(player.currentScreenHandler == player.playerScreenHandler, "a swap opens nothing");
        });
    }

    /** Builds s → a → b ⑂ (c, d), d → a, and a lonely e; s is a start tile. */
    static List<BlockPos> smallBoard(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.TILE, new BlockPos(1, 1, 1), new BlockPos(3, 1, 1),
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
    public void undoAndRedoFollowTheAnchor(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.TILE, new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1));
        withPlayer(context, true, player -> {
            ItemStack brush = brush(player);
            paint(player, brush, context, t.get(0), t.get(1), t.get(2));
            context.assertTrue(pick(player, ToolWheelPayload.Action.BRUSH_UNDO, 0), "the wheel's undo");
            context.assertTrue(links(context, t.get(1)).isEmpty(), "the last link is undone");
            context.assertEquals(TileLinkerBrush.anchor(brush, context.getWorld()), t.get(1), "the anchor is back on the second tile");
            context.assertTrue(pick(player, ToolWheelPayload.Action.BRUSH_REDO, 0), "the wheel's redo");
            context.assertEquals(links(context, t.get(1)), List.of(t.get(2)), "redone");
            context.assertEquals(TileLinkerBrush.anchor(brush, context.getWorld()), t.get(2), "the anchor is on the third tile again");

            // Edited since (another player, the interface...): skipped
            pick(player, ToolWheelPayload.Action.BRUSH_UNDO, 0);
            BoardSpaceBlockEntity first = boardSpace(context, t.get(0));
            BoardLinks.setLinks(context.getWorld(), first, 0, List.of(t.get(2)));
            pick(player, ToolWheelPayload.Action.BRUSH_UNDO, 0);
            context.assertEquals(links(context, t.get(0)), List.of(t.get(2)), "a change made since is kept");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placingTilesWithTheBrushInTheOffHandLinksThem(TestContext context) {
        for (int x = 0; x < 9; x++) context.setBlockState(new BlockPos(x, 0, 1), Blocks.STONE);
        withPlayer(context, true, player -> {
            player.setStackInHand(Hand.OFF_HAND, new ItemStack(ModItems.TILE_LINKER_BRUSH));
            List<BlockPos> placed = new ArrayList<>();
            for (int x = 1; x <= 7; x += 3) {
                ItemStack tile = new ItemStack(ModBlocks.TILE);
                player.setStackInHand(Hand.MAIN_HAND, tile);
                BlockPos ground = context.getAbsolutePos(new BlockPos(x, 0, 1));
                tile.useOnBlock(new net.minecraft.item.ItemUsageContext(player, Hand.MAIN_HAND,
                        new net.minecraft.util.hit.BlockHitResult(ground.toCenterPos().add(0, 0.5, 0), net.minecraft.util.math.Direction.UP, ground, false)));
                placed.add(ground.up());
            }
            context.assertEquals(links(context, placed.get(0)), List.of(placed.get(1)), "first → second");
            context.assertEquals(links(context, placed.get(1)), List.of(placed.get(2)), "second → third");
            context.assertEquals(TileLinkerBrush.anchor(player.getOffHandStack(), context.getWorld()), placed.get(2), "the last placed tile is the anchor");
        });
    }

    /** Moved from the Wrench's origin to the brush's anchor: the chests of an inventory tile. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void inventoryTilesTakeTheNearestChestOrTheClickedOne(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.TILE, new BlockPos(1, 1, 1), new BlockPos(4, 1, 1));
        context.setBlockState(new BlockPos(1, 1, 3), Blocks.CHEST);
        context.setBlockState(new BlockPos(6, 1, 3), Blocks.CHEST);
        BlockPos near = context.getAbsolutePos(new BlockPos(1, 1, 3)), far = context.getAbsolutePos(new BlockPos(6, 1, 3));
        withPlayer(context, true, player -> {
            ItemStack brush = brush(player);
            player.setStackInHand(Hand.OFF_HAND, new ItemStack(ModItems.INVENTORY_CARTRIDGE));
            paint(player, brush, context, t.get(0), t.get(1)); // the inventory cartridge goes in the first tile
            ItemStack cartridge = boardSpace(context, t.get(0)).getStack(0);
            context.assertEquals(fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers.in(cartridge, context.getWorld()), List.of(near), "the nearest chest");

            // The anchor back on the first tile (a stroke on it alone), click the far chest
            player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
            paint(player, brush, context, t.get(0));
            net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(player, context.getWorld(), Hand.MAIN_HAND,
                    new net.minecraft.util.hit.BlockHitResult(far.toCenterPos(), net.minecraft.util.math.Direction.UP, far, false));
            context.assertEquals(fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers.in(boardSpace(context, t.get(0)).getStack(0), context.getWorld()),
                    List.of(near, far), "the clicked chest, after the first (a cartridge holds a list)");
            // The same chest again: out of the list
            net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(player, context.getWorld(), Hand.MAIN_HAND,
                    new net.minecraft.util.hit.BlockHitResult(near.toCenterPos(), net.minecraft.util.math.Direction.UP, near, false));
            context.assertEquals(fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers.in(boardSpace(context, t.get(0)).getStack(0), context.getWorld()),
                    List.of(far), "the near one clicked again: removed");
            // The Wrench no longer links chests: the chest's own use goes on
            ItemStack wrench = wrench(player);
            net.minecraft.util.ActionResult withWrench = net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(player, context.getWorld(), Hand.MAIN_HAND,
                    new net.minecraft.util.hit.BlockHitResult(near.toCenterPos(), net.minecraft.util.math.Direction.UP, near, false));
            context.assertEquals(withWrench, net.minecraft.util.ActionResult.PASS, "the Wrench leaves the chest alone " + wrench);
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theWheelPicksTheSlotOfAnAdvancedTile(TestContext context) {
        List<BlockPos> advanced = tiles(context, ModBlocks.ADVANCED_TILE, new BlockPos(1, 1, 1));
        List<BlockPos> next = tiles(context, ModBlocks.TILE, new BlockPos(4, 1, 1));
        withPlayer(context, true, player -> {
            ItemStack brush = brush(player);
            context.assertTrue(pick(player, ToolWheelPayload.Action.BRUSH_LEVEL, 3), "level 3 picked");
            paint(player, brush, context, advanced.getFirst(), next.getFirst());
            BoardSpaceBlockEntity tile = boardSpace(context, advanced.getFirst());
            context.assertEquals(BoardLinks.links(tile, 3), List.of(next.getFirst()), "linked in slot 3");
            context.assertTrue(tile.getStack(0).isEmpty(), "the active slot (0, no redstone) untouched");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pastingTurnsTheLinksWithTheCopyAndUndoRemovesIt(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.TILE, new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(3, 1, 3));
        link(context, t.get(0), t.get(1));
        link(context, t.get(1), t.get(2), context.getAbsolutePos(new BlockPos(8, 1, 8)));
        withPlayer(context, true, player -> {
            ServerWorld world = context.getWorld();
            fr.lordfinn.steveparty.board.BoardBlueprint.Clip clip = fr.lordfinn.steveparty.board.BoardBlueprint.copy(world,
                    net.minecraft.util.math.BlockBox.create(t.get(0), t.get(2)), context.getAbsolutePos(new BlockPos(2, 1, 2)));
            BlockPos anchor = context.getAbsolutePos(new BlockPos(6, 1, 6));
            WrenchActions.recorded(player, world, null, () -> fr.lordfinn.steveparty.board.BoardBlueprint.paste(world, clip, anchor,
                    net.minecraft.util.BlockRotation.CLOCKWISE_90, false, player));
            // Relative to the anchor, (x, z) turns into (-z, x)
            BlockPos a = anchor.add(1, 0, -1), b = anchor.add(1, 0, 1), c = anchor.add(-1, 0, 1);
            context.assertEquals(links(context, a), List.of(b), "a' → b'");
            context.assertEquals(links(context, b), List.of(c), "b' → c', the link leaving the copy is cut");
            context.assertEquals(links(context, t.get(0)), List.of(t.get(1)), "the original is untouched");
            fr.lordfinn.steveparty.board.LinkHistory.undo(player, true, null);
            context.assertTrue(world.getBlockState(a).isAir() && world.getBlockState(c).isAir(), "undo removes the paste");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aPastedLargeTileTurnsItsSide(TestContext context) {
        for (int x = 1; x <= 7; x++) for (int z = 1; z <= 7; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
        context.setBlockState(new BlockPos(2, 1, 2), ModBlocks.TILE.getDefaultState()
                .with(ATileBlock.SIZE, fr.lordfinn.steveparty.blocks.custom.boardspaces.TileLayout.LARGE_SOUTH_EAST));
        ServerWorld world = context.getWorld();
        fr.lordfinn.steveparty.board.BoardBlueprint.Clip clip = fr.lordfinn.steveparty.board.BoardBlueprint.copy(world,
                net.minecraft.util.math.BlockBox.create(context.getAbsolutePos(new BlockPos(2, 1, 2)), context.getAbsolutePos(new BlockPos(3, 1, 3))),
                context.getAbsolutePos(new BlockPos(2, 1, 2)));
        context.assertEquals(clip.entries().size(), 1, "the parts are not copied");
        BlockPos anchor = context.getAbsolutePos(new BlockPos(5, 1, 5));
        fr.lordfinn.steveparty.board.BoardBlueprint.paste(world, clip, anchor, net.minecraft.util.BlockRotation.CLOCKWISE_90, false, null);
        context.assertEquals(world.getBlockState(anchor).get(ATileBlock.SIZE),
                fr.lordfinn.steveparty.blocks.custom.boardspaces.TileLayout.LARGE_SOUTH_WEST, "south-east turned 90 degrees: south-west");
        context.assertTrue(world.getBlockState(anchor.west()).getBlock() instanceof fr.lordfinn.steveparty.blocks.custom.boardspaces.TilePartBlock,
                "its parts follow");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void translateFollowsAMoveMadeWithAnotherTool(TestContext context) {
        // a → b moved 3 blocks south by hand: the copies still point at the old b
        List<BlockPos> moved = tiles(context, ModBlocks.TILE, new BlockPos(1, 1, 4), new BlockPos(3, 1, 4));
        BlockPos oldB = context.getAbsolutePos(new BlockPos(3, 1, 1)), outside = context.getAbsolutePos(new BlockPos(7, 1, 7));
        link(context, moved.get(0), oldB, outside);
        int count = fr.lordfinn.steveparty.board.BoardBlueprint.translate(context.getWorld(),
                net.minecraft.util.math.BlockBox.create(moved.get(0), moved.get(1)), new net.minecraft.util.math.Vec3i(0, 0, 3), null);
        context.assertEquals(count, 1, "one link moved");
        context.assertEquals(links(context, moved.get(0)), List.of(moved.get(1), outside), "the inner link follows, the outer one stays");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aLoopTemplateIsClosedAndStarts(TestContext context) {
        List<BlockPos> cells = fr.lordfinn.steveparty.board.BoardBlueprint.template(context.getWorld(),
                fr.lordfinn.steveparty.board.BoardBlueprint.Template.LOOP, context.getAbsolutePos(new BlockPos(1, 1, 7)),
                net.minecraft.util.math.Direction.NORTH, 6, 2, null);
        context.assertEquals(cells.size(), 6, "6 tiles");
        for (int i = 0; i < 6; i++) context.assertEquals(links(context, cells.get(i)), List.of(cells.get((i + 1) % 6)), "tile " + i);
        context.assertEquals(context.getWorld().getBlockState(cells.getFirst()).get(TILE_TYPE), BoardSpaceType.TILE_START, "the first one starts");
        BoardValidator.Report report = BoardValidator.check(BoardGraph.collect(context.getWorld(), box(context)));
        context.assertTrue(report.issues().stream().allMatch(i -> i.key().equals("no_token")), "a valid board: " + report.issues());
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aPickedCopyComesWithoutLinks(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.TILE, new BlockPos(1, 1, 1), new BlockPos(3, 1, 1));
        link(context, t.get(0), t.get(1));
        BoardSpaceBlockEntity source = boardSpace(context, t.get(0));
        // As the creative pick block with Ctrl makes it: block entity data and components
        ItemStack item = fr.lordfinn.steveparty.blocks.custom.boardspaces.TileContents.copyOf(new ItemStack(ModBlocks.TILE), source, context.getWorld());
        context.setBlockState(new BlockPos(5, 0, 5), Blocks.STONE);
        withPlayer(context, true, player -> {
            player.setStackInHand(Hand.MAIN_HAND, item);
            BlockPos ground = context.getAbsolutePos(new BlockPos(5, 0, 5));
            item.useOnBlock(new net.minecraft.item.ItemUsageContext(player, Hand.MAIN_HAND,
                    new net.minecraft.util.hit.BlockHitResult(ground.toCenterPos().add(0, 0.5, 0), net.minecraft.util.math.Direction.UP, ground, false)));
            BoardSpaceBlockEntity copy = boardSpace(context, ground.up());
            context.assertTrue(copy.getStack(0).isOf(ModItems.BOARD_SPACE_BEHAVIOR), "the copy has its cartridge");
            context.assertTrue(BoardLinks.links(copy, 0).isEmpty(), "but no links to the original's neighbours");
        });
    }

    // ---------------------------------------------------------------- 16-slot board spaces and the redstone power

    /**
     * An Advanced Tile at (3, 1, 1) receiving a power of 5 (a comparator reading a third-full chest), and a Tile at
     * (6, 1, 1). The comparator needs a few ticks: {@code then} runs once the power is there.
     */
    static void poweredAdvancedTile(TestContext context, java.util.function.BiConsumer<BlockPos, BlockPos> then) {
        BlockPos advanced = tiles(context, ModBlocks.ADVANCED_TILE, new BlockPos(3, 1, 1)).getFirst();
        BlockPos next = tiles(context, ModBlocks.TILE, new BlockPos(6, 1, 1)).getFirst();
        context.setBlockState(new BlockPos(1, 0, 1), Blocks.STONE);
        context.setBlockState(new BlockPos(2, 0, 1), Blocks.STONE);
        // The comparator first: it only reads its chest when a neighbour changes (setBlockState is no player placement)
        context.setBlockState(new BlockPos(2, 1, 1), Blocks.COMPARATOR.getDefaultState()
                .with(net.minecraft.block.ComparatorBlock.FACING, net.minecraft.util.math.Direction.WEST));
        context.setBlockState(new BlockPos(1, 1, 1), Blocks.CHEST);
        net.minecraft.inventory.Inventory chest = (net.minecraft.inventory.Inventory) context.getBlockEntity(new BlockPos(1, 1, 1));
        for (int i = 0; i < 9; i++) chest.setStack(i, new ItemStack(net.minecraft.item.Items.STONE, 64)); // 1/3 full: 5
        chest.markDirty();
        context.waitAndRun(6, () -> {
            context.assertEquals(boardSpace(context, advanced).getActiveSlot(), 5, "powered at 5");
            then.accept(advanced, next);
        });
    }

    /** Like {@link #withPlayer}, for tests that go on over several ticks: {@code test} completes the test itself. */
    static void withLatePlayer(TestContext context, Consumer<ServerPlayerEntity> test) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        try {
            test.accept(player);
        } finally {
            context.getWorld().getServer().getPlayerManager().remove(player);
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void aPoweredAdvancedTileIsLinkedInItsPoweredSlot(TestContext context) {
        poweredAdvancedTile(context, (advanced, next) -> withLatePlayer(context, player -> {
            paint(player, brush(player), context, advanced, next);
            BoardSpaceBlockEntity tile = boardSpace(context, advanced);
            context.assertEquals(BoardLinks.links(tile, 5), List.of(next), "linked in slot 5");
            context.assertTrue(tile.getStack(5).isOf(ModItems.BOARD_SPACE_BEHAVIOR), "the cartridge was supplied in slot 5");
            for (int slot = 0; slot < 16; slot++) {
                if (slot != 5) context.assertTrue(tile.getStack(slot).isEmpty(), "slot " + slot + " untouched");
            }
            context.complete();
        }));
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void aCartridgeIsReplacedInThePoweredSlotOnly(TestContext context) {
        poweredAdvancedTile(context, (advanced, next) -> withLatePlayer(context, player -> {
            BoardSpaceBlockEntity tile = boardSpace(context, advanced);
            BlockPos elsewhere = context.getAbsolutePos(new BlockPos(3, 1, 5));
            ItemStack slot0 = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR);
            slot0.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(new ArrayList<>(List.of(elsewhere)), ""));
            ItemStack slot5 = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR);
            slot5.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(new ArrayList<>(List.of(next)), ""));
            tile.setStack(0, slot0);
            tile.setStack(5, slot5);
            ItemStack wrench = wrench(player);
            player.setStackInHand(Hand.OFF_HAND, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP));
            click(player, wrench, context, advanced);
            context.assertTrue(tile.getStack(5).isOf(ModItems.BOARD_SPACE_BEHAVIOR_STOP), "slot 5 now holds the stop cartridge");
            context.assertEquals(BoardLinks.links(tile, 5), List.of(next), "with slot 5's links");
            context.assertTrue(tile.getStack(0).isOf(ModItems.BOARD_SPACE_BEHAVIOR), "slot 0 untouched");
            context.assertEquals(BoardLinks.links(tile, 0), List.of(elsewhere), "slot 0 keeps its own links");
            context.expectBlockProperty(new BlockPos(3, 1, 1), TILE_TYPE, BoardSpaceType.BOARD_SPACE_STOP);
            context.complete();
        }));
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void aChosenSlotOverridesThePowerAndThePowerIsFollowedOtherwise(TestContext context) {
        poweredAdvancedTile(context, (advanced, next) -> withLatePlayer(context, player -> {
            tiles(context, ModBlocks.TILE, new BlockPos(6, 1, 4));
            ItemStack brush = brush(player);
            pick(player, ToolWheelPayload.Action.BRUSH_LEVEL, 7);
            paint(player, brush, context, advanced, next);
            BoardSpaceBlockEntity tile = boardSpace(context, advanced);
            context.assertEquals(BoardLinks.links(tile, 7), List.of(next), "the chosen slot 7, not the powered 5");
            context.assertTrue(tile.getStack(5).isEmpty(), "slot 5 untouched");
            context.removeBlock(new BlockPos(2, 1, 1)); // no more comparator: power 0
        }));
        context.waitAndRun(14, () -> withLatePlayer(context, player -> {
            // Back to the powered slot: the link follows the power, now 0
            BlockPos advanced = context.getAbsolutePos(new BlockPos(3, 1, 1));
            BlockPos third = context.getAbsolutePos(new BlockPos(6, 1, 4));
            context.assertEquals(boardSpace(context, advanced).getActiveSlot(), 0, "no power: slot 0");
            ItemStack brush = brush(player);
            pick(player, ToolWheelPayload.Action.BRUSH_LEVEL, TileLinkerBrush.POWERED);
            paint(player, brush, context, advanced, third);
            context.assertEquals(BoardLinks.links(boardSpace(context, advanced), 0), List.of(third), "the link follows the new power: slot 0");
            context.complete();
        }));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void diagonalsAreOriented(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.TILE, new BlockPos(1, 1, 1), new BlockPos(4, 1, 4));
        withPlayer(context, true, player -> {
            paint(player, brush(player), context, t.get(0), t.get(1));
            context.assertEquals(context.getWorld().getBlockState(t.get(0)).get(ATileBlock.ROTATION_8), 3, "south-east");
            ServerWorld world = context.getWorld();
            context.assertEquals(BoardLinks.rotationToward(world, t.get(1), t.get(0)), 7, "north-west");
        });
    }

    /**
     * Undoing or redoing a board edit takes the right to build: a player put in adventure mode since (a party started)
     * changes nothing; back in survival, the undo works.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void undoTakesTheRightToBuild(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.TILE, new BlockPos(1, 1, 1), new BlockPos(4, 1, 1));
        withPlayer(context, true, player -> {
            ItemStack wrench = brush(player);
            paint(player, wrench, context, t.toArray(BlockPos[]::new));
            context.assertEquals(links(context, t.get(0)), List.of(t.get(1)), "linked");
            player.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
            context.assertTrue(!fr.lordfinn.steveparty.board.LinkHistory.undo(player, true, wrench), "adventure: nothing undone");
            context.assertEquals(links(context, t.get(0)), List.of(t.get(1)), "adventure: still linked");
            player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            context.assertTrue(fr.lordfinn.steveparty.board.LinkHistory.undo(player, true, wrench), "survival: undone");
            context.assertEquals(links(context, t.get(0)), List.of(), "survival: the link is gone");
        });
    }

    /**
     * A cartridge linked then unlinked (in a tile with the Wrench, or in the hand) is the same as a new one again: no
     * empty links left on it, so it stacks with new ones.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anUnlinkedCartridgeStacksWithNewOnes(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.TILE, new BlockPos(1, 1, 1), new BlockPos(4, 1, 1));
        withPlayer(context, true, player -> {
            ItemStack brush = brush(player);
            paint(player, brush, context, t.get(0), t.get(1));
            context.assertEquals(links(context, t.get(0)), List.of(t.get(1)), "linked");
            paint(player, brush, context, t.get(0), t.get(1)); // erased
            BoardSpaceBlockEntity boardSpace = boardSpace(context, t.get(0));
            ItemStack cut = boardSpace.getStack(boardSpace.getActiveSlot());
            ItemStack fresh = new ItemStack(cut.getItem());
            context.assertTrue(ItemStack.areItemsAndComponentsEqual(cut, fresh), "erased in its tile: like a new one " + cut.getComponentChanges());

            ItemStack held = new ItemStack(ModItems.TILE_BEHAVIOR_START);
            var item = (fr.lordfinn.steveparty.items.custom.AbstractDestinationsSelectorItem) held.getItem();
            item.addOrRemoveDestination(DestinationsComponent.DEFAULT, t.get(1), player, held, context.getWorld());
            context.assertTrue(held.contains(ModComponents.DESTINATIONS_COMPONENT), "linked in the hand");
            item.addOrRemoveDestination(held.get(ModComponents.DESTINATIONS_COMPONENT), t.get(1), player, held, context.getWorld());
            context.assertTrue(ItemStack.areItemsAndComponentsEqual(held, new ItemStack(ModItems.TILE_BEHAVIOR_START)),
                    "unlinked in the hand: like a new one " + held.getComponentChanges());
        });
    }

    /**
     * A cartridge in the hand clicked on the ground opens its menu and links nothing; clicked where its linked tile was
     * (the tile broken since), it unlinks it.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aCartridgeOnTheGroundOpensItsMenuOrUnlinksAMissingTile(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.TILE, new BlockPos(1, 1, 1));
        context.setBlockState(new BlockPos(3, 0, 3), Blocks.STONE);
        withPlayer(context, true, player -> {
            ItemStack held = new ItemStack(ModItems.TILE_BEHAVIOR_START);
            player.setStackInHand(Hand.MAIN_HAND, held);
            BlockPos ground = context.getAbsolutePos(new BlockPos(3, 0, 3));
            ItemStack inHand = player.getMainHandStack();
            inHand.useOnBlock(new net.minecraft.item.ItemUsageContext(player, Hand.MAIN_HAND,
                    new net.minecraft.util.hit.BlockHitResult(ground.toCenterPos().add(0, 0.5, 0), net.minecraft.util.math.Direction.UP, ground, false)));
            context.assertTrue(!inHand.contains(ModComponents.DESTINATIONS_COMPONENT), "the ground is not linked");
            context.assertTrue(player.currentScreenHandler != player.playerScreenHandler, "its menu is open");
            player.closeHandledScreen();

            BlockPos tile = t.getFirst();
            inHand.useOnBlock(new net.minecraft.item.ItemUsageContext(player, Hand.MAIN_HAND,
                    new net.minecraft.util.hit.BlockHitResult(tile.toCenterPos(), net.minecraft.util.math.Direction.UP, tile, false)));
            context.assertEquals(inHand.get(ModComponents.DESTINATIONS_COMPONENT).destinations(), List.of(tile), "the tile is linked");
            context.setBlockState(new BlockPos(1, 1, 1), Blocks.AIR);
            BlockPos under = tile.down();
            inHand.useOnBlock(new net.minecraft.item.ItemUsageContext(player, Hand.MAIN_HAND,
                    new net.minecraft.util.hit.BlockHitResult(under.toCenterPos().add(0, 0.5, 0), net.minecraft.util.math.Direction.UP, under, false)));
            context.assertTrue(!inHand.contains(ModComponents.DESTINATIONS_COMPONENT), "the missing tile is unlinked by clicking under it");
        });
    }

    // ---------------------------------------------------------------- the Tile Linker Brush

    /** A stroke links each painted tile to the next; going over a link again, either way, erases it. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theBrushPaintsLinksAndErasesThemWhenRepainted(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.TILE,
                new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1), new BlockPos(7, 1, 1));
        withPlayer(context, true, player -> {
            ItemStack brush = brush(player);
            paint(player, brush, context, t.get(0), t.get(1), t.get(2), t.get(3));
            for (int i = 0; i < 3; i++) context.assertEquals(links(context, t.get(i)), List.of(t.get(i + 1)), "tile " + i + " linked to the next");
            context.assertEquals(links(context, t.get(3)), List.of(), "the last one leads nowhere yet");
            paint(player, brush, context, t.get(0), t.get(1));
            context.assertEquals(links(context, t.get(0)), List.of(), "repainted the same way: erased");
            paint(player, brush, context, t.get(3), t.get(2));
            context.assertEquals(links(context, t.get(2)), List.of(), "repainted the other way: erased");
            context.assertEquals(links(context, t.get(3)), List.of(), "and not linked back");
            context.assertEquals(links(context, t.get(1)), List.of(t.get(2)), "the link in between kept");
            context.assertTrue(fr.lordfinn.steveparty.board.LinkHistory.undo(player, true, null), "undone");
            context.assertEquals(links(context, t.get(2)), List.of(t.get(3)), "the erased link is back");
        });
    }

    /** The level picked on the wheel: kept on the brush, invalid values and other tools ignored. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theWheelSetsTheBrushLevel(TestContext context) {
        withPlayer(context, true, player -> {
            ItemStack brush = brush(player);
            context.assertTrue(pick(player, ToolWheelPayload.Action.BRUSH_LEVEL, 12), "level 12");
            context.assertEquals(TileLinkerBrush.level(brush), 12, "kept on the brush");
            context.assertTrue(!pick(player, ToolWheelPayload.Action.BRUSH_LEVEL, 16), "no level 16");
            context.assertTrue(!pick(player, ToolWheelPayload.Action.BRUSH_LEVEL, -2), "no level -2");
            context.assertEquals(TileLinkerBrush.level(brush), 12, "still 12");
            context.assertTrue(pick(player, ToolWheelPayload.Action.BRUSH_LEVEL, TileLinkerBrush.POWERED), "the powered slot");
            context.assertTrue(!brush.contains(ModComponents.LINK_LEVEL), "the default: no component left");
            context.assertTrue(!new ToolWheelPayload(99, 0).handle(player), "an unknown action");
            // Not the brush in hand: nothing
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.WRENCH));
            context.assertTrue(!pick(player, ToolWheelPayload.Action.BRUSH_LEVEL, 3), "the Wrench has no level");
            context.assertTrue(!pick(player, ToolWheelPayload.Action.HAMMER_OPEN, 0), "nor a hammer inventory");
        });
    }

    /** The kind of Cartridge picked on the wheel goes in the empty tiles the brush links (from the inventory in survival). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theWheelPicksTheCartridgeOfNewTiles(TestContext context) {
        List<BlockPos> t = tiles(context, ModBlocks.TILE, new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1));
        withPlayer(context, false, player -> {
            ItemStack brush = brush(player);
            player.getInventory().setStack(10, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP, 2));
            player.getInventory().setStack(11, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR, 5));
            context.assertTrue(!pick(player, ToolWheelPayload.Action.BRUSH_CARTRIDGE,
                    net.minecraft.registry.Registries.ITEM.getRawId(net.minecraft.item.Items.STONE)), "stone is no cartridge");
            context.assertTrue(!pick(player, ToolWheelPayload.Action.BRUSH_CARTRIDGE, -2), "no item -2");
            context.assertTrue(pick(player, ToolWheelPayload.Action.BRUSH_CARTRIDGE,
                    net.minecraft.registry.Registries.ITEM.getRawId(ModItems.BOARD_SPACE_BEHAVIOR_STOP)), "the stop cartridge");
            context.assertTrue(TileLinkerBrush.cartridge(brush) == ModItems.BOARD_SPACE_BEHAVIOR_STOP, "kept on the brush");
            paint(player, brush, context, t.get(0), t.get(1), t.get(2));
            context.assertTrue(boardSpace(context, t.get(0)).getStack(0).isOf(ModItems.BOARD_SPACE_BEHAVIOR_STOP), "a stop cartridge went in");
            context.assertTrue(boardSpace(context, t.get(1)).getStack(0).isOf(ModItems.BOARD_SPACE_BEHAVIOR_STOP), "and another");
            context.assertTrue(player.getInventory().getStack(10).isEmpty(), "both taken from the inventory");
            context.assertEquals(player.getInventory().getStack(11).getCount(), 5, "the plain ones untouched");
            // The plain Cartridge is a kind like the others; keeping the tiles' cartridges is the default: nothing stored
            context.assertTrue(pick(player, ToolWheelPayload.Action.BRUSH_CARTRIDGE,
                    net.minecraft.registry.Registries.ITEM.getRawId(ModItems.BOARD_SPACE_BEHAVIOR)), "the plain cartridge");
            context.assertTrue(TileLinkerBrush.cartridge(brush) == ModItems.BOARD_SPACE_BEHAVIOR, "stored");
            context.assertTrue(pick(player, ToolWheelPayload.Action.BRUSH_CARTRIDGE, ToolWheelPayload.KEEP_CARTRIDGES), "keep");
            context.assertTrue(!brush.contains(ModComponents.LINK_CARTRIDGE), "the default: no component left");
        });
    }

    /** The anchor of the brush picks the shop of a shop space, as the Wrench's origin did (see ShopStopGameTests). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theWrenchNoLongerKeepsAnOrigin(TestContext context) {
        withPlayer(context, true, player -> {
            ItemStack wrench = wrench(player);
            wrench.set(ModComponents.BLOCK_ORIGIN_COMPONENT, new fr.lordfinn.steveparty.components.BlockOriginComponent(
                    context.getAbsolutePos(new BlockPos(1, 1, 1)), ""));
            wrench.set(ModComponents.WRENCH_STATE, new net.minecraft.nbt.NbtCompound());
            wrench.inventoryTick(context.getWorld(), player, 0, true);
            context.assertTrue(!wrench.contains(ModComponents.BLOCK_ORIGIN_COMPONENT) && !wrench.contains(ModComponents.WRENCH_STATE),
                    "an old wrench's origin and mode are dropped");
        });
    }

    /**
     * The brush finds tiles the way they are seen: a ray grazing just above a flat tile, or hitting the ground right
     * next to it, is on it; a ray hitting a wall before the tile is not.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theBrushAimsAtTilesAsTheyAreSeen(TestContext context) {
        for (int x = 0; x < 8; x++) context.setBlockState(new BlockPos(x, 0, 2), Blocks.STONE);
        BlockPos tile = tiles(context, ModBlocks.TILE, new BlockPos(5, 1, 2)).getFirst();
        withPlayer(context, true, player -> {
            ServerWorld world = context.getWorld();
            // Level with the ground plus 0.2: over the thin tile's shape (0.125 high), under what is drawn of it
            net.minecraft.util.math.Vec3d eye = context.getAbsolute(new net.minecraft.util.math.Vec3d(1.5, 1.2, 2.5));
            net.minecraft.util.math.Vec3d east = new net.minecraft.util.math.Vec3d(1, 0, 0);
            context.assertEquals(fr.lordfinn.steveparty.board.BrushAim.along(world, player, eye, east), tile, "grazing over it");
            // Down onto the ground just before it
            net.minecraft.util.math.Vec3d high = context.getAbsolute(new net.minecraft.util.math.Vec3d(1.5, 4, 2.5));
            net.minecraft.util.math.Vec3d target = context.getAbsolute(new net.minecraft.util.math.Vec3d(4.95, 1.0, 2.5));
            context.assertEquals(fr.lordfinn.steveparty.board.BrushAim.along(world, player, high, target.subtract(high)), tile, "the ground at its edge");
            // A wall in between
            context.setBlockState(new BlockPos(3, 1, 2), Blocks.STONE);
            context.setBlockState(new BlockPos(3, 2, 2), Blocks.STONE);
            context.assertTrue(fr.lordfinn.steveparty.board.BrushAim.along(world, player, eye, east) == null, "behind a wall: nothing");
        });
    }

    /** The Stencil Hammer's wheel: loaded stencils and dyes only, Engrave, its inventory. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theHammerWheelPicksStencilAndColour(TestContext context) {
        withPlayer(context, true, player -> {
            ItemStack hammer = new ItemStack(ModItems.STENCIL_GUN);
            List<ItemStack> contents = new ArrayList<>(fr.lordfinn.steveparty.items.custom.StencilGunItem.contents(hammer));
            ItemStack stencil = new ItemStack(ModItems.STENCIL);
            fr.lordfinn.steveparty.items.custom.StencilItem.setShape(fr.lordfinn.steveparty.stencil.StencilShape.full(), stencil);
            contents.set(0, stencil.copy());
            contents.set(4, stencil.copy());
            contents.set(fr.lordfinn.steveparty.items.custom.StencilGunItem.STENCIL_SLOTS + 2, new ItemStack(net.minecraft.item.Items.RED_DYE, 8));
            fr.lordfinn.steveparty.items.custom.StencilGunItem.setContents(hammer, contents);
            player.setStackInHand(Hand.MAIN_HAND, hammer);
            ItemStack held = player.getMainHandStack();

            context.assertTrue(pick(player, ToolWheelPayload.Action.HAMMER_STENCIL, 4), "the stencil of slot 4");
            context.assertTrue(!pick(player, ToolWheelPayload.Action.HAMMER_STENCIL, 1), "slot 1 is empty");
            context.assertTrue(!pick(player, ToolWheelPayload.Action.HAMMER_STENCIL, 9), "no slot 9");
            context.assertEquals(fr.lordfinn.steveparty.items.custom.StencilGunItem.selection(held).stencil(), 4, "slot 4 stamped");
            context.assertTrue(pick(player, ToolWheelPayload.Action.HAMMER_DYE, 2), "the red dye");
            context.assertTrue(fr.lordfinn.steveparty.items.custom.StencilGunItem.selectedLoad(held).color() == net.minecraft.util.DyeColor.RED, "paints red");
            context.assertTrue(!pick(player, ToolWheelPayload.Action.HAMMER_DYE, 3), "no dye in slot 3");
            context.assertTrue(pick(player, ToolWheelPayload.Action.HAMMER_DYE, fr.lordfinn.steveparty.components.StencilGunSelection.ENGRAVE), "Engrave");
            context.assertTrue(fr.lordfinn.steveparty.items.custom.StencilGunItem.selectedLoad(held).color() == null, "no paint");
            context.assertEquals(fr.lordfinn.steveparty.items.custom.StencilGunItem.selection(held).stencil(), 4, "the stencil kept");
            context.assertTrue(pick(player, ToolWheelPayload.Action.HAMMER_OPEN, 0), "its inventory");
            context.assertTrue(player.currentScreenHandler instanceof fr.lordfinn.steveparty.screen_handlers.custom.StencilGunScreenHandler, "open");
            player.closeHandledScreen();
            // The brush's picks do nothing to a hammer
            context.assertTrue(!pick(player, ToolWheelPayload.Action.BRUSH_LEVEL, 3), "no level on a hammer");
        });
    }
}
