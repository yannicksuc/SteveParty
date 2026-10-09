package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.board.BrushLinks;
import fr.lordfinn.steveparty.board.LinkHistory;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.board.WrenchActions;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.gametest.BoardLinkingGameTests.brush;
import static fr.lordfinn.steveparty.gametest.BoardLinkingGameTests.paint;
import static fr.lordfinn.steveparty.gametest.BoardLinkingGameTests.withPlayer;

/**
 * The Tile Linker Brush links every holder of a cartridge as a click of that cartridge on the target would: routers,
 * teleport spaces, Hop Switches, inventory tiles, Piggy Banks, Looting Boxes, the Party Controller's bank (see
 * BrushLinks). Painting over a target again erases it; the cartridges' limits and the right to build hold.
 */
public class BrushCartridgeLinksGameTests implements FabricGameTest {

    /** {@code block} on a stone floor at {@code relative}; its absolute position. */
    static BlockPos place(TestContext context, Block block, BlockPos relative) {
        context.setBlockState(relative.down(), Blocks.STONE);
        context.setBlockState(relative, block);
        return context.getAbsolutePos(relative);
    }

    /** A new cartridge of {@code item} in the first slot of the holder at {@code absolute}; the stack in the slot. */
    static ItemStack insert(TestContext context, BlockPos absolute, Item item) {
        CartridgeContainerBlockEntity container = (CartridgeContainerBlockEntity) context.getWorld().getBlockEntity(absolute);
        container.setStack(0, new ItemStack(item));
        return container.getStack(0);
    }

    static ItemStack cartridge(TestContext context, BlockPos absolute) {
        return ((CartridgeContainerBlockEntity) context.getWorld().getBlockEntity(absolute)).getStack(0);
    }

    static List<BlockPos> chests(TestContext context, ItemStack cartridge) {
        return CartridgeContainers.in(cartridge, context.getWorld());
    }

    static void click(TestContext context, ServerPlayerEntity player, BlockPos absolute) {
        UseBlockCallback.EVENT.invoker().interact(player, context.getWorld(), Hand.MAIN_HAND,
                new BlockHitResult(absolute.toCenterPos(), Direction.UP, absolute, false));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aRouterLinksBoardSpacesAndErasesThem(TestContext context) {
        BlockPos router = place(context, ModBlocks.BOARD_SPACE_REDSTONE_ROUTER, new BlockPos(1, 1, 1));
        BlockPos tile = place(context, ModBlocks.TILE, new BlockPos(3, 1, 1));
        BlockPos other = place(context, ModBlocks.BOARD_SPACE_REDSTONE_ROUTER, new BlockPos(5, 1, 1));
        withPlayer(context, true, player -> {
            ItemStack brush = brush(player);
            paint(player, brush, context, router, tile);
            context.assertEquals(BoardLinks.links(cartridge(context, router)), List.of(tile), "the router routes the tile (a cartridge supplied)");
            paint(player, brush, context, router, other);
            context.assertEquals(BoardLinks.links(cartridge(context, router)), List.of(tile), "a router is no board space: not linked");
            paint(player, brush, context, router, tile);
            context.assertEquals(BoardLinks.links(cartridge(context, router)), List.of(), "painted again: erased");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aTeleportSpaceKeepsItsCartridgeAndIsLinked(TestContext context) {
        BlockPos teleport = place(context, ModBlocks.TILE, new BlockPos(1, 1, 1));
        BlockPos next = place(context, ModBlocks.TILE, new BlockPos(3, 1, 1));
        insert(context, teleport, ModItems.TELEPORT_CARTRIDGE);
        withPlayer(context, true, player -> {
            ItemStack brush = brush(player);
            paint(player, brush, context, teleport, next);
            context.assertTrue(cartridge(context, teleport).isOf(ModItems.TELEPORT_CARTRIDGE), "the Teleport Cartridge is kept");
            context.assertEquals(BoardLinks.links(cartridge(context, teleport)), List.of(next), "linked to the next tile");
            paint(player, brush, context, teleport, next);
            context.assertEquals(BoardLinks.links(cartridge(context, teleport)), List.of(), "erased");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aHopSwitchLinksTheBlocksItSwitchesOnly(TestContext context) {
        BlockPos hop = place(context, ModBlocks.HOP_SWITCH, new BlockPos(1, 1, 1));
        BlockPos empty = place(context, ModBlocks.HOP_SWITCH, new BlockPos(1, 1, 4));
        BlockPos plastic = place(context, ModBlocks.PLASTIC_BLOCKS[0], new BlockPos(3, 1, 1));
        BlockPos stone = place(context, Blocks.STONE, new BlockPos(4, 1, 1));
        insert(context, hop, ModItems.BOARD_SPACE_BEHAVIOR);
        withPlayer(context, true, player -> {
            ItemStack brush = brush(player);
            // Over the plastic twice in one stroke: linked once
            paint(player, brush, context, hop, plastic, plastic, stone);
            context.assertEquals(BoardLinks.links(cartridge(context, hop)), List.of(plastic), "the switchable block, not the stone");
            context.assertTrue(BrushLinks.aims(context.getWorld(), hop, TileLinkerBrush.POWERED, plastic), "the brush aims at it from the Hop Switch");
            context.assertTrue(!BrushLinks.aims(context.getWorld(), hop, TileLinkerBrush.POWERED, stone), "not at the stone");

            LinkHistory.undo(player, true, brush);
            context.assertEquals(BoardLinks.links(cartridge(context, hop)), List.of(), "undone");
            LinkHistory.undo(player, false, brush);
            context.assertEquals(BoardLinks.links(cartridge(context, hop)), List.of(plastic), "redone");

            paint(player, brush, context, hop, plastic);
            context.assertEquals(BoardLinks.links(cartridge(context, hop)), List.of(), "painted again: erased");

            paint(player, brush, context, empty, plastic);
            context.assertTrue(cartridge(context, empty).isEmpty(), "a Hop Switch without cartridge links nothing (none supplied)");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anInventoryTileTakesAtMostEightChests(TestContext context) {
        BlockPos tile = place(context, ModBlocks.TILE, new BlockPos(1, 1, 1));
        ItemStack cartridge = insert(context, tile, ModItems.INVENTORY_CARTRIDGE);
        List<BlockPos> stroke = new ArrayList<>(List.of(tile));
        List<BlockPos> chests = new ArrayList<>();
        for (int x = 0; x < 9; x++) {
            BlockPos chest = place(context, Blocks.CHEST, new BlockPos((x % 4) * 2, 1, 3 + (x / 4) * 2));
            chests.add(chest);
            stroke.add(chest);
        }
        withPlayer(context, true, player -> {
            ItemStack brush = brush(player);
            paint(player, brush, context, stroke.toArray(BlockPos[]::new));
            context.assertEquals(chests(context, cartridge(context, tile)), chests.subList(0, CartridgeContainers.MAX), "the first 8: a cartridge holds no more");
            paint(player, brush, context, tile, chests.getFirst());
            context.assertEquals(chests(context, cartridge(context, tile)), chests.subList(1, CartridgeContainers.MAX), "painted again: removed");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void piggyBanksAndLootingBoxesLinkTheirChestsNotHopSwitches(TestContext context) {
        BlockPos piggy = place(context, ModBlocks.PIGGY_BANK, new BlockPos(1, 1, 1));
        BlockPos box = place(context, ModBlocks.LOOTING_BOX, new BlockPos(1, 1, 4));
        BlockPos hop = place(context, ModBlocks.HOP_SWITCH, new BlockPos(1, 1, 7));
        BlockPos chest = place(context, Blocks.CHEST, new BlockPos(4, 1, 4));
        for (BlockPos holder : List.of(piggy, box, hop)) insert(context, holder, ModItems.INVENTORY_CARTRIDGE);
        withPlayer(context, true, player -> {
            ItemStack brush = brush(player);
            for (BlockPos holder : List.of(piggy, box)) {
                paint(player, brush, context, holder, chest);
                context.assertEquals(chests(context, cartridge(context, holder)), List.of(chest), "the chest of " + holder);
                paint(player, brush, context, holder, chest);
                context.assertEquals(chests(context, cartridge(context, holder)), List.of(), "erased from " + holder);
            }
            paint(player, brush, context, hop, chest);
            context.assertEquals(chests(context, cartridge(context, hop)), List.of(), "a Hop Switch's cartridge takes no chest");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void thePartyControllersBankLinksItsChest(TestContext context) {
        BlockPos controller = place(context, ModBlocks.PARTY_CONTROLLER, new BlockPos(1, 1, 1));
        BlockPos chest = place(context, Blocks.CHEST, new BlockPos(4, 1, 1));
        PartyControllerEntity entity = (PartyControllerEntity) context.getWorld().getBlockEntity(controller);
        entity.setBank(new ItemStack(ModItems.INVENTORY_CARTRIDGE));
        withPlayer(context, true, player -> {
            ItemStack brush = brush(player);
            paint(player, brush, context, controller, chest);
            context.assertEquals(chests(context, entity.getBank()), List.of(chest), "the bank pays from the chest");
            paint(player, brush, context, controller, chest);
            context.assertEquals(chests(context, entity.getBank()), List.of(), "erased");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aPlayerWhoCannotBuildLinksNothing(TestContext context) {
        BlockPos hop = place(context, ModBlocks.HOP_SWITCH, new BlockPos(1, 1, 1));
        BlockPos plastic = place(context, ModBlocks.PLASTIC_BLOCKS[0], new BlockPos(3, 1, 1));
        insert(context, hop, ModItems.BOARD_SPACE_BEHAVIOR);
        withPlayer(context, false, player -> {
            player.getAbilities().allowModifyWorld = false;
            ItemStack brush = brush(player);
            paint(player, brush, context, hop, plastic);
            context.assertEquals(BoardLinks.links(cartridge(context, hop)), List.of(), "adventure: not linked");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aClickOrABlockPlacedWithTheBrushInTheOffHandLinksFromTheAnchor(TestContext context) {
        BlockPos hop = place(context, ModBlocks.HOP_SWITCH, new BlockPos(1, 1, 1));
        BlockPos clicked = place(context, ModBlocks.PLASTIC_BLOCKS[1], new BlockPos(3, 1, 1));
        insert(context, hop, ModItems.BOARD_SPACE_BEHAVIOR);
        withPlayer(context, true, player -> {
            ItemStack brush = brush(player);
            paint(player, brush, context, hop);
            context.assertEquals(TileLinkerBrush.anchor(brush, context.getWorld()), hop, "the Hop Switch is the anchor");
            click(context, player, clicked);
            context.assertEquals(BoardLinks.links(cartridge(context, hop)), List.of(clicked), "the clicked block is switched by the anchor");
            ActionResult onStone = UseBlockCallback.EVENT.invoker().interact(player, context.getWorld(), Hand.MAIN_HAND,
                    new BlockHitResult(hop.down().toCenterPos(), Direction.UP, hop.down(), false));
            context.assertEquals(onStone, ActionResult.PASS, "a block the anchor does not link: the click goes on");

            player.setStackInHand(Hand.OFF_HAND, brush.copy());
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            BlockPos placed = place(context, ModBlocks.PLASTIC_BLOCKS[2], new BlockPos(3, 1, 3));
            WrenchActions.onBlockPlaced(context.getWorld(), placed, player);
            context.assertEquals(BoardLinks.links(cartridge(context, hop)), List.of(clicked, placed), "the placed block too");
        });
    }
}
