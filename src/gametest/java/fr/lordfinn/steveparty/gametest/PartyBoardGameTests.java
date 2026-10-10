package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyBoard;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The board of a Party Controller: the start tiles near it, then wherever the paths from them go. A tile no path
 * joins is not part of it; the controller remembers its board, and the spaces of its board know their party.
 */
public class PartyBoardGameTests implements SteveGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(1, 1, 1);
    private static final BlockPos START = new BlockPos(3, 1, 1), MIDDLE = new BlockPos(5, 1, 1), END = new BlockPos(7, 1, 1);
    private static final BlockPos LONE = new BlockPos(3, 1, 6);

    private static BoardSpaceBlockEntity space(TestContext context, BlockPos pos, ItemStack cartridge, BlockPos to) {
        context.setBlockState(pos, ModBlocks.TILE);
        BoardSpaceBlockEntity space = context.getBlockEntity(pos);
        if (to != null) cartridge.set(ModComponents.DESTINATIONS_COMPONENT,
                new DestinationsComponent(new ArrayList<>(List.of(context.getAbsolutePos(to))), ""));
        space.setStack(0, cartridge);
        return space;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "party_board")
    public void theBoardIsWhereThePathsFromTheStartsGo(TestContext context) {
        TestBoards.floor(context, 9);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        ItemStack start = new ItemStack(ModItems.TILE_BEHAVIOR_START);
        UUID token = UUID.randomUUID();
        start.set(ModComponents.TB_START_BOUND_ENTITY, token.toString());
        space(context, START, start, MIDDLE);
        space(context, MIDDLE, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), END);
        space(context, END, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), START);
        space(context, LONE, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), null);

        PartyBoard.Snapshot board = controller.board(context.getWorld(), true);
        for (BlockPos pos : List.of(START, MIDDLE, END))
            context.assertTrue(board.contains(context.getAbsolutePos(pos)), pos + " on the board");
        context.assertTrue(!board.contains(context.getAbsolutePos(LONE)), "a tile no path joins is not");
        context.assertEquals(board.startTokens(), Map.of(token, context.getAbsolutePos(START)), "the token of its start tile");
        context.assertEquals(board.graph().nodes().size(), 3, "the board check sees its 3 spaces");

        // A party runs on it: its spaces know it, the lone tile does not
        PigEntity pig = TestBoards.token(context, START.up(), null);
        PartyControllerEntity party = TestBoards.party(context, CONTROLLER, 1, pig.getUuid());
        party.board(context.getWorld(), true);
        context.assertTrue(PartyControllerEntity.getPartyOfBoardSpace(context.getWorld(), context.getAbsolutePos(END), 1, false)
                .orElse(null) == party, "a space of its board: its party");
        context.assertTrue(PartyControllerEntity.getPartyOfBoardSpace(context.getWorld(), context.getAbsolutePos(LONE), 1, false)
                .isEmpty(), "the lone tile: no party (fallback within 1 block only)");
        context.setBlockState(CONTROLLER, Blocks.AIR);
        context.complete();
    }
}
