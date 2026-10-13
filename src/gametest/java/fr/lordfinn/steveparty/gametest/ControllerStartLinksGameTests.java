package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.board.LinkHistory;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.List;

import static fr.lordfinn.steveparty.gametest.BoardLinkingGameTests.brush;
import static fr.lordfinn.steveparty.gametest.BoardLinkingGameTests.links;
import static fr.lordfinn.steveparty.gametest.BoardLinkingGameTests.paint;
import static fr.lordfinn.steveparty.gametest.BoardLinkingGameTests.withPlayer;

/**
 * The start tiles of a Party Controller linked with the Tile Linker Brush: when it has any, its only start tiles;
 * painted again (either way), unlinked; none linked, the ones around it; undo takes a link back.
 */
public class ControllerStartLinksGameTests implements SteveGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(1, 1, 1);
    private static final BlockPos A = new BlockPos(3, 1, 1), B = new BlockPos(5, 1, 1), C = new BlockPos(7, 1, 1);

    private static void start(TestContext context, BlockPos pos) {
        context.setBlockState(pos, ModBlocks.TILE);
        BoardSpaceBlockEntity space = context.getBlockEntity(pos);
        space.setStack(0, new ItemStack(ModItems.TILE_BEHAVIOR_START));
    }

    private static List<BlockPos> starts(TestContext context, PartyControllerEntity controller) {
        return controller.board(context.getWorld(), true).starts();
    }

    // Its own batch: the start tiles of another test beside it would be found around its controller
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "controller_start_links")
    public void linkedStartsAreTheOnlyStarts(TestContext context) {
        TestBoards.floor(context, 9);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        for (BlockPos pos : List.of(A, B, C)) start(context, pos);
        BlockPos controllerPos = context.getAbsolutePos(CONTROLLER);
        BlockPos a = context.getAbsolutePos(A), b = context.getAbsolutePos(B), c = context.getAbsolutePos(C);
        withPlayer(context, true, player -> {
            context.assertEquals(starts(context, controller), List.of(a, b, c), "none linked: the starts around it");

            ItemStack brush = brush(player);
            paint(player, brush, context, controllerPos, a);
            context.assertEquals(controller.getStartLinks(), List.of(a), "the controller painted to A: A linked");
            context.assertEquals(starts(context, controller), List.of(a), "linked: its only start");

            // The other way round, and two in one stroke: no path painted between the starts
            paint(player, brush, context, b, controllerPos, c);
            context.assertEquals(controller.getStartLinks(), List.of(a, b, c), "B (to the controller) and C linked");
            context.assertEquals(links(context, b), List.of(), "no path from B");
            context.assertEquals(links(context, a), List.of(), "no path from A");
            context.assertEquals(starts(context, controller), List.of(a, b, c), "the three linked");

            // Undo, redo
            context.assertTrue(LinkHistory.undo(player, true, brush), "undone");
            context.assertEquals(controller.getStartLinks(), List.of(a, b), "C's link undone");
            context.assertTrue(LinkHistory.undo(player, false, brush), "redone");
            context.assertEquals(controller.getStartLinks(), List.of(a, b, c), "C's link redone");

            // Painted again: unlinked, either way
            paint(player, brush, context, controllerPos, a, b);
            context.assertEquals(controller.getStartLinks(), List.of(c), "A and B unlinked");
            context.assertEquals(starts(context, controller), List.of(c), "only C");
            paint(player, brush, context, c, controllerPos);
            context.assertEquals(controller.getStartLinks(), List.of(), "C unlinked from the start tile");
            context.assertEquals(starts(context, controller), List.of(a, b, c), "none linked: found around it again");

            // A tile that is no start: not linked
            context.setBlockState(B, Blocks.AIR);
            context.setBlockState(B, ModBlocks.TILE);
            BoardSpaceBlockEntity plain = context.getBlockEntity(B);
            plain.setStack(0, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));
            paint(player, brush, context, controllerPos, b);
            context.assertEquals(controller.getStartLinks(), List.of(), "a plain tile is no start");
            // Crossed on the way to a start tile: skipped, no path from it
            paint(player, brush, context, controllerPos, b, a);
            context.assertEquals(controller.getStartLinks(), List.of(a), "A linked across B");
            context.assertEquals(links(context, b), List.of(), "no path from the tile crossed");

            // Saved with the controller
            long[] saved = controller.createNbt(context.getWorld().getRegistryManager()).getLongArray("StartLinks");
            context.assertTrue(saved.length == 1 && saved[0] == a.asLong(), "saved");
            context.setBlockState(CONTROLLER, Blocks.AIR);
        });
    }
}
