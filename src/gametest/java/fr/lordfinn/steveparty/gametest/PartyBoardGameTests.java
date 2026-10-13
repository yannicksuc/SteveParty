package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.CartridgeTransfers;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyBoard;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.service.BoardShop;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;

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
        PigEntity pig = TestBoards.token(context, START.up(), null);
        UUID token = pig.getUuid();
        start.set(ModComponents.TB_START_BOUND_ENTITY, token.toString());
        space(context, START, start, MIDDLE);
        space(context, MIDDLE, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), END);
        space(context, END, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), START);
        space(context, LONE, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), null);
        // Its start tile linked (with the brush): the start tiles of the tests running around are not its own
        controller.setStartLinks(List.of(context.getAbsolutePos(START)));

        PartyBoard.Snapshot board = controller.board(context.getWorld(), true);
        for (BlockPos pos : List.of(START, MIDDLE, END))
            context.assertTrue(board.contains(context.getAbsolutePos(pos)), pos + " on the board");
        context.assertTrue(!board.contains(context.getAbsolutePos(LONE)), "a tile no path joins is not");
        context.assertEquals(board.startTokens(), Map.of(token, context.getAbsolutePos(START)), "the token of its start tile");
        context.assertEquals(board.graph().nodes().size(), 3, "the board check sees its 3 spaces");

        // A party runs on it: its spaces know it, the lone tile does not
        PartyControllerEntity party = TestBoards.party(context, CONTROLLER, 1, pig.getUuid());
        party.board(context.getWorld(), true);
        context.assertTrue(PartyControllerEntity.getPartyOfBoardSpace(context.getWorld(), context.getAbsolutePos(END), 1, false)
                .orElse(null) == party, "a space of its board: its party");
        context.assertTrue(PartyControllerEntity.getPartyOfBoardSpace(context.getWorld(), context.getAbsolutePos(LONE), 1, false)
                .isEmpty(), "the lone tile: no party (fallback within 1 block only)");
        context.setBlockState(CONTROLLER, Blocks.AIR);
        context.complete();
    }

    /**
     * The storage a container cartridge's menu tells: its linked containers; none linked, the Party Controller of its
     * board, its party running or not; else none (a lone space, a cartridge in hand, the controller gone).
     */
    // Its own batch: the start tiles of the board above, running beside it, would join its controller's board
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "party_board_storage")
    public void aCartridgeStorageIsItsContainersElseItsBoardController(TestContext context) {
        TestBoards.floor(context, 9);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        ItemStack start = new ItemStack(ModItems.TILE_BEHAVIOR_START);
        start.set(ModComponents.TB_START_BOUND_ENTITY, UUID.randomUUID().toString());
        space(context, START, start, MIDDLE);
        space(context, MIDDLE, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), END);
        space(context, END, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), START);
        space(context, LONE, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), null);
        controller.board(context.getWorld(), true);

        var world = context.getWorld();
        ItemStack cartridge = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
        BlockPos end = context.getAbsolutePos(END), lone = context.getAbsolutePos(LONE);
        context.assertEquals(CartridgeContainers.storageOf(cartridge, world, end, 1), CartridgeContainers.Storage.PARTY_CONTROLLER,
                "none linked, on the board of a controller (no party running): its bank");
        context.assertEquals(CartridgeContainers.storageOf(cartridge, world, lone, 1), CartridgeContainers.Storage.NONE,
                "a lone space, no controller within 1 block: none");
        context.assertEquals(CartridgeContainers.storageOf(cartridge, world, null, 1), CartridgeContainers.Storage.NONE,
                "a cartridge in hand: none");

        BlockPos chest = new BlockPos(5, 1, 6);
        context.setBlockState(chest, Blocks.CHEST);
        CartridgeContainers.set(cartridge, List.of(GlobalPos.create(world.getRegistryKey(), context.getAbsolutePos(chest))));
        context.assertEquals(CartridgeContainers.storageOf(cartridge, world, lone, 1), CartridgeContainers.Storage.LINKED,
                "a linked container: its own, even off any board");
        context.assertEquals(CartridgeContainers.storageOf(cartridge, world, end, 1), CartridgeContainers.Storage.LINKED,
                "a linked container: before the controller's bank");

        context.setBlockState(CONTROLLER, Blocks.AIR);
        context.assertEquals(CartridgeContainers.storageOf(new ItemStack(ModItems.INVENTORY_CARTRIDGE), world, end, 1),
                CartridgeContainers.Storage.NONE, "the controller gone: none");
        context.complete();
    }

    /**
     * A start tile still bound to a pawn that is gone, or that stands elsewhere, has no pawn: no player without a pawn
     * joins the party. A pawn put back on it plays again.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "party_board_phantom")
    public void aStartBoundToAGoneOrAwayPawnHasNone(TestContext context) {
        TestBoards.floor(context, 9);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        ItemStack gone = new ItemStack(ModItems.TILE_BEHAVIOR_START);
        gone.set(ModComponents.TB_START_BOUND_ENTITY, UUID.randomUUID().toString());
        space(context, START, gone, MIDDLE);
        PigEntity away = TestBoards.token(context, new BlockPos(7, 1, 7), null);
        ItemStack bound = new ItemStack(ModItems.TILE_BEHAVIOR_START);
        bound.set(ModComponents.TB_START_BOUND_ENTITY, away.getUuidAsString());
        space(context, MIDDLE, bound, START);

        context.assertTrue(controller.findStartTokens(context.getWorld()).isEmpty(), "no pawn on its start: nobody plays");
        context.assertEquals(controller.board(context.getWorld(), true).starts().size(), 2, "both starts still count");

        away.refreshPositionAndAngles(context.getAbsolutePos(MIDDLE.up()).toBottomCenterPos(), 0, 0);
        context.assertEquals(controller.findStartTokens(context.getWorld()), List.of(away.getUuid()), "back on its start: it plays");
        away.discard();
        context.setBlockState(CONTROLLER, Blocks.AIR);
        context.complete();
    }

    /**
     * A Party Controller's infinite storage, its party not started yet: the cartridges of its board without a chest of
     * their own (an Inventory space, a Shop space) all show it (they use it once the party runs); off, its real bank. A
     * die sells at its base price, a power-up at its own.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "party_board_infinite")
    public void theInfiniteStorageServesEveryCartridgeOfItsBoard(TestContext context) {
        TestBoards.floor(context, 9);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        ItemStack start = new ItemStack(ModItems.TILE_BEHAVIOR_START);
        start.set(ModComponents.TB_START_BOUND_ENTITY, UUID.randomUUID().toString());
        space(context, START, start, MIDDLE);
        space(context, MIDDLE, new ItemStack(ModItems.INVENTORY_CARTRIDGE), END);
        space(context, END, new ItemStack(ModItems.SHOP_CARTRIDGE), START);
        controller.setInfiniteBank(true);

        var world = context.getWorld();
        BlockPos middle = context.getAbsolutePos(MIDDLE), end = context.getAbsolutePos(END);
        ItemStack inventory = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
        context.assertTrue(CartridgeContainers.shownSourceFor(inventory, world, middle).isUnlimited(),
                "an Inventory space of its board, no party yet: shows the infinite storage");
        context.assertTrue(CartridgeTransfers.source(world, inventory, middle).isNone(), "but gives nothing outside a party");
        BoardShop shop = new BoardShop(world, end);
        context.assertTrue(shop.shownInStock(new ItemStack(ModItems.DOUBLE_DICE)), "its shop: shown never sold out");
        context.assertTrue(!shop.inStock(new ItemStack(ModItems.DOUBLE_DICE)), "but sells nothing outside a party");
        controller.setInfiniteBank(false);
        context.assertTrue(!shop.shownInStock(new ItemStack(ModItems.DOUBLE_DICE)), "off: its real bank, empty");

        // Its party running: they use it
        controller.setInfiniteBank(true);
        PigEntity pig = TestBoards.token(context, START.up(), null);
        PartyControllerEntity party = TestBoards.party(context, CONTROLLER, 1, pig.getUuid());
        context.assertTrue(party == controller, "its party");
        context.assertTrue(CartridgeTransfers.source(world, inventory, middle).isUnlimited(), "in game: the infinite storage");
        context.assertTrue(shop.inStock(new ItemStack(ModItems.DOUBLE_DICE)) && shop.party() == controller, "in game: its shop sells, its till");

        context.assertEquals(TradingStallBlockEntity.basePrice(new ItemStack(ModItems.DOUBLE_DICE)).getCount(), 10, "a double die: 10 coins");
        context.assertTrue(TradingStallBlockEntity.offerOf(ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(ModItems.TRIPLE_DICE)) != null,
                "a die without a price is sold");
        context.assertTrue(TradingStallBlockEntity.basePrice(new ItemStack(Blocks.DIRT)).isEmpty(), "dirt: no base price");
        // Nothing left for the boards of the tests around (a start tile would join their controller's board)
        for (BlockPos pos : List.of(START, MIDDLE, END, CONTROLLER)) context.setBlockState(pos, Blocks.AIR);
        pig.discard();
        context.complete();
    }
}
