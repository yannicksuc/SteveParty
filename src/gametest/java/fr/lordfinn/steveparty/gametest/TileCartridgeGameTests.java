package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileContents;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.board.DestinationSwap;
import fr.lordfinn.steveparty.board.Pipette;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.payloads.custom.ToolWheelPayload;
import fr.lordfinn.steveparty.recipes.TileCartridgeRecipe;
import fr.lordfinn.steveparty.screen_handlers.custom.BoardSpaceScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tiles crafted with their cartridge, cartridges changed and Advanced Tiles filled at the crafting grid, the brush
 * linking equipped tiles without spare cartridges, a cartridge clicked on another swapping their destinations, and the
 * pipette of the tile screens.
 */
public class TileCartridgeGameTests implements FabricGameTest {
    private static final ItemStack EMPTY = ItemStack.EMPTY;

    private static ItemStack plain() {
        return new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR);
    }

    private static ItemStack tileHolding(ItemStack cartridge) {
        return TileContents.holding(new ItemStack(ModBlocks.TILE), cartridge);
    }

    private static ItemStack linked(ItemStack cartridge, BlockPos... links) {
        ItemStack copy = cartridge.copy();
        copy.set(ModComponents.DESTINATIONS_COMPONENT, new fr.lordfinn.steveparty.components.DestinationsComponent(List.of(links), "overworld"));
        return copy;
    }

    /** The crafting recipe the server's recipe manager finds for {@code input} (the one the grid would use). */
    private static Optional<RecipeEntry<CraftingRecipe>> match(TestContext context, CraftingRecipeInput input) {
        return context.getWorld().getServer().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, context.getWorld());
    }

    private static ItemStack craft(TestContext context, CraftingRecipeInput input) {
        return match(context, input).orElseThrow().value().craft(input, context.getWorld().getRegistryManager());
    }

    private static DefaultedList<ItemStack> remainders(TestContext context, CraftingRecipeInput input) {
        return match(context, input).orElseThrow().value().getRemainder(input);
    }

    private static List<Integer> slots(ItemStack tile) {
        return TileContents.cartridges(tile).stream().map(TileContents.Slot::slot).toList();
    }

    // ---------------------------------------------------------------- recipes

    /** The Tile is crafted holding a plain Cartridge (two at a time, they stack); the Advanced Tile comes empty. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aCraftedTileHoldsAPlainCartridge(TestContext context) {
        var manager = context.getWorld().getServer().getRecipeManager();
        ItemStack tile = manager.get(Steveparty.id("tile")).orElseThrow().value().getResult(context.getWorld().getRegistryManager());
        context.assertTrue(tile.isOf(ModBlocks.TILE.asItem()) && tile.getCount() == 2, "two tiles: " + tile);
        List<TileContents.Slot> held = TileContents.cartridges(tile);
        context.assertTrue(held.size() == 1 && held.getFirst().cartridge().isOf(ModItems.BOARD_SPACE_BEHAVIOR)
                && held.getFirst().cartridge().getComponentChanges().isEmpty(), "each holding a plain Cartridge: " + held);
        // Crafted from the grid
        CraftingRecipeInput grid = CraftingRecipeInput.create(3, 2, List.of(
                new ItemStack(Items.WHITE_CARPET), new ItemStack(Items.WHITE_CARPET), new ItemStack(Items.WHITE_CARPET),
                plain(), new ItemStack(Items.LIGHT_WEIGHTED_PRESSURE_PLATE), plain()));
        ItemStack crafted = craft(context, grid);
        context.assertTrue(ItemStack.areItemsAndComponentsEqual(crafted, tile), "the grid gives them: " + crafted);
        ItemStack advanced = manager.get(Steveparty.id("advanced_tile")).orElseThrow().value().getResult(context.getWorld().getRegistryManager());
        context.assertTrue(advanced.isOf(ModBlocks.ADVANCED_TILE.asItem()) && TileContents.cartridges(advanced).isEmpty(), "an empty Advanced Tile");
        context.complete();
    }

    /** Tile + cartridge: the Tile holds that one, without links; its old one stays in the grid; alone, it is emptied. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aTileAndACartridgeGiveTheOldOneBack(TestContext context) {
        BlockPos somewhere = context.getAbsolutePos(new BlockPos(1, 1, 1));
        ItemStack tile = tileHolding(plain());
        ItemStack shop = linked(new ItemStack(ModItems.SHOP_CARTRIDGE), somewhere);
        CraftingRecipeInput grid = CraftingRecipeInput.create(2, 1, List.of(shop, tile));
        context.assertTrue(match(context, grid).orElseThrow().value() instanceof TileCartridgeRecipe, "the tile cartridge recipe");
        ItemStack result = craft(context, grid);
        List<TileContents.Slot> held = TileContents.cartridges(result);
        context.assertTrue(result.isOf(ModBlocks.TILE.asItem()) && result.getCount() == 1 && held.size() == 1
                && held.getFirst().cartridge().isOf(ModItems.SHOP_CARTRIDGE), "the Tile holds the Shop Cartridge: " + held);
        context.assertTrue(BoardLinks.links(held.getFirst().cartridge()).isEmpty(), "without the links it came with");
        DefaultedList<ItemStack> back = remainders(context, grid);
        context.assertTrue(back.get(0).isEmpty() && back.get(1).isOf(ModItems.BOARD_SPACE_BEHAVIOR) && back.get(1).getCount() == 1,
                "the plain Cartridge back in the Tile's slot: " + back);
        // An empty (old) Tile: nothing back
        CraftingRecipeInput empty = CraftingRecipeInput.create(1, 2, List.of(new ItemStack(ModBlocks.TILE), plain()));
        context.assertTrue(TileContents.cartridges(craft(context, empty)).size() == 1, "an empty Tile gets one");
        context.assertTrue(remainders(context, empty).stream().allMatch(ItemStack::isEmpty), "nothing back");
        // The same cartridge again: nothing to do
        context.assertTrue(match(context, CraftingRecipeInput.create(2, 1, List.of(tileHolding(plain()), plain()))).isEmpty(),
                "the same cartridge: no craft");
        // Alone: emptied, the cartridge back
        CraftingRecipeInput alone = CraftingRecipeInput.create(1, 1, List.of(tileHolding(new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP))));
        ItemStack emptied = craft(context, alone);
        context.assertTrue(emptied.isOf(ModBlocks.TILE.asItem()) && !emptied.contains(DataComponentTypes.CONTAINER), "an empty Tile: " + emptied);
        context.assertTrue(remainders(context, alone).getFirst().isOf(ModItems.BOARD_SPACE_BEHAVIOR_STOP), "its cartridge back");
        context.complete();
    }

    /** Advanced Tile + Tiles / cartridges: free slots filled 0, 15, 14..., taken ones skipped, too many refused. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anAdvancedTileIsFilledInOrder(TestContext context) {
        ItemStack advanced = new ItemStack(ModBlocks.ADVANCED_TILE);
        ItemStack stopTile = tileHolding(new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP));
        CraftingRecipeInput grid = CraftingRecipeInput.create(2, 2, List.of(advanced, tileHolding(plain()), stopTile,
                new ItemStack(ModItems.SHOP_CARTRIDGE)));
        ItemStack filled = craft(context, grid);
        context.assertEquals(slots(filled), List.of(0, 14, 15), "slots 0, 15 and 14 taken");
        context.assertTrue(TileContents.cartridges(filled).get(0).cartridge().isOf(ModItems.BOARD_SPACE_BEHAVIOR), "0: the first tile's");
        context.assertTrue(TileContents.cartridges(filled).get(2).cartridge().isOf(ModItems.BOARD_SPACE_BEHAVIOR_STOP), "15: the second's");
        context.assertTrue(TileContents.cartridges(filled).get(1).cartridge().isOf(ModItems.SHOP_CARTRIDGE), "14: the loose cartridge");
        context.assertTrue(remainders(context, grid).stream().allMatch(ItemStack::isEmpty), "the Tiles are used up");
        // Taken slots are skipped: 0 and 15 taken, the next go in 14 then 13
        ItemStack again = craft(context, CraftingRecipeInput.create(3, 1, List.of(filled.copy(), stopTile, stopTile.copy())));
        context.assertEquals(slots(again), List.of(0, 12, 13, 14, 15), "then 13 and 12");
        // Full but one: two more are refused, one fits
        List<ItemStack> fifteen = new ArrayList<>();
        for (int i = 0; i < 15; i++) fifteen.add(plain());
        ItemStack almost = TileCartridgeRecipe.fill(new ItemStack(ModBlocks.ADVANCED_TILE), fifteen);
        context.assertTrue(almost != null && TileCartridgeRecipe.firstFreeSlot(almost) == 1, "slot 1 is the last free one");
        context.assertTrue(match(context, CraftingRecipeInput.create(3, 1, List.of(almost, stopTile, stopTile.copy()))).isEmpty(),
                "more tiles than free slots: no craft");
        context.assertEquals(slots(craft(context, CraftingRecipeInput.create(2, 1, List.of(almost, stopTile)))).size(), 16, "one fits");
        // An empty Tile adds nothing: no craft
        context.assertTrue(match(context, CraftingRecipeInput.create(2, 1, List.of(advanced, new ItemStack(ModBlocks.TILE)))).isEmpty(),
                "an empty Tile: no craft");
        context.complete();
    }

    // ---------------------------------------------------------------- the brush

    /** Equipped tiles are linked by a survival player with no spare Cartridge; a kind picked on the wheel swaps theirs. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theBrushLinksEquippedTilesWithoutSpareCartridges(TestContext context) {
        List<BlockPos> t = BoardLinkingGameTests.tiles(context, ModBlocks.TILE, new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1));
        for (BlockPos pos : t) BoardLinkingGameTests.boardSpace(context, pos).setStack(0, plain());
        BoardLinkingGameTests.withPlayer(context, false, player -> {
            player.getInventory().clear();
            ItemStack brush = BoardLinkingGameTests.brush(player);
            BoardLinkingGameTests.paint(player, brush, context, t.get(0), t.get(1), t.get(2));
            context.assertEquals(BoardLinkingGameTests.links(context, t.get(0)), List.of(t.get(1)), "linked without any cartridge in the inventory");
            context.assertEquals(BoardLinkingGameTests.links(context, t.get(1)), List.of(t.get(2)), "and the next one");
            // A kind picked: the tiles painted get it, links kept, theirs back
            player.getInventory().setStack(9, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP, 1));
            context.assertTrue(BoardLinkingGameTests.pick(player, ToolWheelPayload.Action.BRUSH_CARTRIDGE,
                    net.minecraft.registry.Registries.ITEM.getRawId(ModItems.BOARD_SPACE_BEHAVIOR_STOP)), "stop picked");
            TileLinkerBrush.paint(player, brush, context.getWorld(), t.get(0));
            TileLinkerBrush.endStroke(player);
            BoardSpaceBlockEntity first = BoardLinkingGameTests.boardSpace(context, t.get(0));
            context.assertTrue(first.getStack(0).isOf(ModItems.BOARD_SPACE_BEHAVIOR_STOP), "the stop cartridge went in");
            context.assertEquals(BoardLinks.links(first, 0), List.of(t.get(1)), "with the tile's links");
            context.assertTrue(player.getInventory().getStack(9).isEmpty(), "taken from the inventory");
            context.assertTrue(player.getInventory().count(ModItems.BOARD_SPACE_BEHAVIOR) == 1, "the plain one back");
        });
    }

    // ---------------------------------------------------------------- a cartridge on a cartridge

    private static PlayerEntity clicker(TestContext context, DestinationSwap.Preference preference) {
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.currentScreenHandler = player.playerScreenHandler;
        DestinationSwap.setPreference(player, preference);
        return player;
    }

    /** Left click with {@code cursor} on the player's inventory slot 9 (screen slot 9) holding {@code inSlot}. */
    private static void click(PlayerEntity player, ItemStack cursor, ItemStack inSlot) {
        ScreenHandler handler = player.currentScreenHandler;
        player.getInventory().setStack(9, inSlot);
        handler.setCursorStack(cursor);
        handler.onSlotClick(9, 0, SlotActionType.PICKUP, player);
    }

    /** On: the items swap and so do their destinations; a stack on either side, or the option off: the usual click. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aCartridgeOnACartridgeSwapsTheirDestinations(TestContext context) {
        BlockPos a = context.getAbsolutePos(new BlockPos(1, 1, 1)), b = context.getAbsolutePos(new BlockPos(2, 1, 1));
        PlayerEntity player = clicker(context, DestinationSwap.Preference.ON);
        click(player, linked(new ItemStack(ModItems.SHOP_CARTRIDGE), b), linked(plain(), a));
        ItemStack slot = player.getInventory().getStack(9), cursor = player.currentScreenHandler.getCursorStack();
        context.assertTrue(slot.isOf(ModItems.SHOP_CARTRIDGE) && BoardLinks.links(slot).equals(List.of(a)), "the shop in place, with the place's links: " + slot);
        context.assertTrue(cursor.isOf(ModItems.BOARD_SPACE_BEHAVIOR) && BoardLinks.links(cursor).equals(List.of(b)), "the plain on the cursor with the shop's: " + cursor);

        // On a Tile item: its cartridge replaced, the Tile keeps its links, the old one comes to the cursor
        click(player, new ItemStack(ModItems.SHOP_CARTRIDGE), tileHolding(linked(plain(), a)));
        ItemStack tile = player.getInventory().getStack(9);
        ItemStack inTile = TileContents.cartridges(tile).getFirst().cartridge();
        context.assertTrue(tile.isOf(ModBlocks.TILE.asItem()) && inTile.isOf(ModItems.SHOP_CARTRIDGE) && BoardLinks.links(inTile).equals(List.of(a)),
                "the Tile holds the shop with its links: " + inTile);
        context.assertTrue(player.currentScreenHandler.getCursorStack().isOf(ModItems.BOARD_SPACE_BEHAVIOR)
                && BoardLinks.links(player.currentScreenHandler.getCursorStack()).isEmpty(), "the plain one back, without links");

        // A stack on the cursor: the usual swap, links untouched
        click(player, linked(new ItemStack(ModItems.SHOP_CARTRIDGE, 2), b), linked(plain(), a));
        context.assertTrue(player.getInventory().getStack(9).getCount() == 2 && BoardLinks.links(player.getInventory().getStack(9)).equals(List.of(b)),
                "the stack went in as it was");
        context.assertTrue(BoardLinks.links(player.currentScreenHandler.getCursorStack()).equals(List.of(a)), "the plain kept its own");

        // The option off: the usual swap
        PlayerEntity off = clicker(context, DestinationSwap.Preference.OFF);
        click(off, linked(new ItemStack(ModItems.SHOP_CARTRIDGE), b), linked(plain(), a));
        context.assertTrue(BoardLinks.links(off.getInventory().getStack(9)).equals(List.of(b)), "off: the shop kept its links");
        // Not asked yet: nothing happens (the client asks)
        PlayerEntity unset = clicker(context, DestinationSwap.Preference.UNSET);
        click(unset, new ItemStack(ModItems.SHOP_CARTRIDGE), plain());
        context.assertTrue(unset.getInventory().getStack(9).isOf(ModItems.BOARD_SPACE_BEHAVIOR)
                && unset.currentScreenHandler.getCursorStack().isOf(ModItems.SHOP_CARTRIDGE), "not asked: nothing moved");
        context.complete();
    }

    // ---------------------------------------------------------------- the pipette

    /**
     * The pipette copies only the destinations, between any kinds (the target's kind, colour and settings stay), on
     * cartridges and Tile items, through the tile screen: nothing is created or used up.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void thePipetteCopiesOnlyTheDestinations(TestContext context) {
        BlockPos a = context.getAbsolutePos(new BlockPos(1, 1, 1)), b = context.getAbsolutePos(new BlockPos(2, 1, 1));
        // The pure logic: a coloured stop gets a shop's destinations, and stays a coloured stop
        ItemStack stop = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP);
        stop.set(DataComponentTypes.DYED_COLOR, new net.minecraft.component.type.DyedColorComponent(0xFF0000, true));
        List<BlockPos> copied = Pipette.copyOf(linked(new ItemStack(ModItems.SHOP_CARTRIDGE), a, b));
        ItemStack pasted = Pipette.pasted(stop, copied, context.getWorld());
        context.assertTrue(pasted != null && pasted.isOf(ModItems.BOARD_SPACE_BEHAVIOR_STOP)
                && pasted.get(DataComponentTypes.DYED_COLOR) != null && BoardLinks.links(pasted).equals(List.of(a, b)), "stop, colour kept, links copied");
        context.assertTrue(Pipette.copyOf(new ItemStack(Items.STONE)) == null && Pipette.pasted(new ItemStack(Items.STONE), copied, context.getWorld()) == null,
                "stone is no cartridge");
        ItemStack tilePasted = Pipette.pasted(tileHolding(plain()), copied, context.getWorld());
        context.assertTrue(tilePasted != null && BoardLinks.links(TileContents.cartridges(tilePasted).getFirst().cartridge()).equals(List.of(a, b)),
                "a Tile item's cartridge too");

        // Through a tile's screen: the tile's cartridge as the source, the player's cartridges as targets
        BlockPos tilePos = BoardLinkingGameTests.tiles(context, ModBlocks.TILE, new BlockPos(4, 1, 4)).getFirst();
        BoardSpaceBlockEntity tile = BoardLinkingGameTests.boardSpace(context, tilePos);
        tile.setStack(0, linked(plain(), a));
        BoardLinkingGameTests.withPlayer(context, false, player -> {
            player.getInventory().clear();
            player.setPosition(tilePos.toCenterPos().add(0, 0, 2)); // within reach of the tile
            BoardSpaceScreenHandler handler = new BoardSpaceScreenHandler(1, player.getInventory(), tile);
            player.currentScreenHandler = handler;
            int target = -1;
            for (int i = 0; i < handler.slots.size(); i++) {
                if (handler.slots.get(i).inventory == player.getInventory() && handler.slots.get(i).getIndex() == 9) target = i;
            }
            player.getInventory().setStack(9, new ItemStack(ModItems.SHOP_CARTRIDGE, 3));
            context.assertTrue(Pipette.apply(player, handler.syncId, 0, target), "pasted");
            ItemStack shops = player.getInventory().getStack(9);
            context.assertTrue(shops.isOf(ModItems.SHOP_CARTRIDGE) && shops.getCount() == 3 && BoardLinks.links(shops).equals(List.of(a)),
                    "the 3 shops, still shops, with the tile's destinations: " + shops);
            context.assertTrue(tile.getStack(0).isOf(ModItems.BOARD_SPACE_BEHAVIOR) && BoardLinks.links(tile, 0).equals(List.of(a)), "the source untouched");
            context.assertTrue(player.getInventory().count(ModItems.SHOP_CARTRIDGE) == 3 && player.getInventory().count(ModItems.BOARD_SPACE_BEHAVIOR) == 0,
                    "nothing created or used up");
            context.assertTrue(!Pipette.apply(player, handler.syncId + 1, 0, target), "another screen: refused");
            context.assertTrue(!Pipette.apply(player, handler.syncId, 0, 0), "on itself: refused");
            player.currentScreenHandler = player.playerScreenHandler;
        });
    }
}
