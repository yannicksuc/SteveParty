package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopStock;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenus;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeRef;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.StallOffersModule;
import fr.lordfinn.steveparty.payloads.custom.CartridgeSettingPayload;
import fr.lordfinn.steveparty.screen_handlers.custom.BoardSpaceScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.custom.CartridgeScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.custom.GhostSlot;
import fr.lordfinn.steveparty.screen_handlers.custom.ShopStallScreenHandler;
import fr.lordfinn.steveparty.service.BoardShop;
import net.minecraft.block.Blocks;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * The Shop Cartridge's offers on a Trading Stall: 9 offers (a stall's columns) kept, read by its merchant and counted in
 * its storage; a cartridge of the 3 offers' time keeps them; its menu's small stall opens the stall, whose ghost slots
 * write the cartridge, and its back button returns to the menu it came from.
 */
public class ShopCartridgeStallGameTests implements SteveGameTest {
    private static final BlockPos TILE = new BlockPos(2, 1, 2), CHEST = new BlockPos(4, 1, 2);
    /** One item sold per column, all different. */
    private static final Item[] SOLD = {Items.DIAMOND, Items.APPLE, Items.BREAD, Items.IRON_INGOT, Items.GOLD_INGOT,
            Items.STICK, Items.COAL, Items.ARROW, Items.BONE};

    private static List<List<ItemStack>> nineOffers() {
        List<List<ItemStack>> offers = new ArrayList<>();
        for (int column = 0; column < ShopCartridgeItem.OFFERS; column++) {
            List<ItemStack> offer = new ArrayList<>(List.of(new ItemStack(SOLD[column], column + 1), new ItemStack(Items.EMERALD, column + 1)));
            if (column == 8) offer.add(new ItemStack(Items.GOLD_NUGGET, 2));
            offers.add(offer);
        }
        return offers;
    }

    /** An old cartridge's 3 × 3 (row by row: sold, price, 2nd price): a diamond for 5 emeralds and a gold ingot, an apple for a coin. */
    private static ItemStack oldCartridge() {
        List<ItemStack> old = new ArrayList<>();
        for (int i = 0; i < 9; i++) old.add(ItemStack.EMPTY);
        old.set(0, new ItemStack(Items.DIAMOND));
        old.set(1, new ItemStack(Items.APPLE, 2));
        old.set(3, new ItemStack(Items.EMERALD, 5));
        old.set(4, new ItemStack(ModItems.COIN));
        old.set(6, new ItemStack(Items.GOLD_INGOT));
        ItemStack cartridge = new ItemStack(ModItems.SHOP_CARTRIDGE);
        cartridge.set(ModComponents.INVENTORY_COMPONENT, new InventoryComponent(old));
        return cartridge;
    }

    private static BoardSpaceBlockEntity tile(TestContext context, ItemStack cartridge) {
        context.setBlockState(TILE.down(), Blocks.STONE);
        context.setBlockState(TILE, ModBlocks.TILE);
        BoardSpaceBlockEntity tile = context.getBlockEntity(TILE);
        tile.setStack(0, cartridge);
        return tile;
    }

    /** 9 offers set: all kept in their columns, the 9th with its second price, in a stall's slot order. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nineOffersAreKept(TestContext context) {
        ItemStack cartridge = new ItemStack(ModItems.SHOP_CARTRIDGE);
        ShopCartridgeItem.setOffers(cartridge, nineOffers());
        List<TradingStallBlockEntity.ExactTradeOffer> offers = ShopCartridgeItem.offers(cartridge);
        context.assertEquals(offers.size(), 9, "9 offers");
        for (int column = 0; column < 9; column++) {
            TradingStallBlockEntity.ExactTradeOffer offer = offers.get(column);
            context.assertTrue(offer.getSellItem().isOf(SOLD[column]) && offer.getSellItem().getCount() == column + 1, "sold " + column);
            context.assertTrue(offer.getFirstPrice().isOf(Items.EMERALD) && offer.getFirstPrice().getCount() == column + 1, "price " + column);
        }
        context.assertTrue(offers.get(8).getSecondPrice().isOf(Items.GOLD_NUGGET) && offers.get(8).getSecondPrice().getCount() == 2, "the 9th's second price");
        InventoryComponent stored = cartridge.get(ModComponents.INVENTORY_COMPONENT);
        context.assertTrue(stored != null && stored.size() == ShopCartridgeItem.SLOTS && !ShopCartridgeItem.isOldLayout(stored), "a stall's 27 slots");
        context.assertTrue(stored.getStack(0).isOf(Items.EMERALD) && stored.getStack(17).isOf(Items.GOLD_NUGGET) && stored.getStack(26).isOf(Items.BONE),
                "the stall's order: prices, second prices, items sold");
        // Too many: the first 9 only; none: no offer
        List<List<ItemStack>> ten = new ArrayList<>(nineOffers());
        ten.add(List.of(new ItemStack(Items.CAKE), new ItemStack(Items.EMERALD)));
        ShopCartridgeItem.setOffers(cartridge, ten);
        context.assertEquals(ShopCartridgeItem.offers(cartridge).size(), 9, "at most 9");
        ShopCartridgeItem.setOffers(cartridge, List.of());
        context.assertTrue(!ShopCartridgeItem.hasOffers(cartridge) && !cartridge.contains(ModComponents.INVENTORY_COMPONENT), "none: nothing stored");
        context.complete();
    }

    /** A cartridge of the 3 offers' time: its offers read the same, and the stall shows them in its first columns. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void oldThreeOffersAreKept(TestContext context) {
        ItemStack cartridge = oldCartridge();
        List<TradingStallBlockEntity.ExactTradeOffer> offers = ShopCartridgeItem.offers(cartridge);
        context.assertEquals(offers.size(), 2, "its 2 offers");
        TradingStallBlockEntity.ExactTradeOffer diamond = offers.get(0), apple = offers.get(1);
        context.assertTrue(diamond.getSellItem().isOf(Items.DIAMOND) && diamond.getFirstPrice().isOf(Items.EMERALD)
                && diamond.getFirstPrice().getCount() == 5 && diamond.getSecondPrice().isOf(Items.GOLD_INGOT), "a diamond for 5 emeralds and a gold ingot");
        context.assertTrue(apple.getSellItem().isOf(Items.APPLE) && apple.getSellItem().getCount() == 2
                && apple.getFirstPrice().isOf(ModItems.COIN) && apple.getSecondPrice().isEmpty(), "2 apples for a coin");
        List<ItemStack> stall = ShopCartridgeItem.stallSlots(cartridge);
        context.assertTrue(stall.get(ShopCartridgeItem.slot(1, ShopCartridgeItem.ROW_SOLD)).isOf(Items.APPLE)
                && stall.get(ShopCartridgeItem.slot(0, ShopCartridgeItem.ROW_SECOND_PRICE)).isOf(Items.GOLD_INGOT)
                && stall.get(ShopCartridgeItem.slot(3, ShopCartridgeItem.ROW_SOLD)).isEmpty(), "in the stall's first columns");
        context.complete();
    }

    /** The first change on the stall writes the new order: the old offers kept, the new one added. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void oldOffersMigrateOnTheStall(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            ItemStack cartridge = oldCartridge();
            player.setStackInHand(Hand.MAIN_HAND, cartridge);
            ShopStallScreenHandler stall = new ShopStallScreenHandler(77, player.getInventory(), CartridgeRef.hand(Hand.MAIN_HAND));
            context.assertTrue(stall.slots.get(ShopCartridgeItem.slot(0, ShopCartridgeItem.ROW_SOLD)).getStack().isOf(Items.DIAMOND)
                    && stall.slots.get(ShopCartridgeItem.slot(1, ShopCartridgeItem.ROW_PRICE)).getStack().isOf(ModItems.COIN),
                    "the stall shows the old offers in its columns");
            // A 6th column: a cake for 3 emeralds, the cake picked with the cursor (a copy: the cursor keeps it)
            stall.setCursorStack(new ItemStack(Items.CAKE, 4));
            stall.onSlotClick(ShopCartridgeItem.slot(5, ShopCartridgeItem.ROW_SOLD), 0, SlotActionType.PICKUP, player);
            context.assertEquals(stall.getCursorStack().getCount(), 4, "a ghost copy: nothing taken");
            stall.setCursorStack(new ItemStack(Items.EMERALD));
            stall.onSlotClick(ShopCartridgeItem.slot(5, ShopCartridgeItem.ROW_PRICE), 0, SlotActionType.PICKUP, player);
            stall.handleGhostEdit(player, ShopCartridgeItem.slot(5, ShopCartridgeItem.ROW_PRICE), GhostSlot.Edit.STEP, 1);
            stall.handleGhostEdit(player, ShopCartridgeItem.slot(5, ShopCartridgeItem.ROW_PRICE), GhostSlot.Edit.STEP, 1);
            stall.setCursorStack(ItemStack.EMPTY);

            InventoryComponent stored = cartridge.get(ModComponents.INVENTORY_COMPONENT);
            context.assertTrue(stored != null && !ShopCartridgeItem.isOldLayout(stored), "written in the stall's order");
            List<TradingStallBlockEntity.ExactTradeOffer> offers = ShopCartridgeItem.offers(cartridge);
            context.assertEquals(offers.size(), 3, "the 2 old offers and the new one");
            context.assertTrue(offers.get(0).getSellItem().isOf(Items.DIAMOND) && offers.get(0).getSecondPrice().isOf(Items.GOLD_INGOT), "the diamond kept");
            context.assertTrue(offers.get(1).getSellItem().isOf(Items.APPLE) && offers.get(1).getFirstPrice().isOf(ModItems.COIN), "the apples kept");
            context.assertTrue(offers.get(2).getSellItem().isOf(Items.CAKE) && offers.get(2).getSellItem().getCount() == 1
                    && offers.get(2).getFirstPrice().getCount() == 3, "a cake for 3 emeralds (the wheel)");
            context.assertTrue(stall.slots.get(ShopCartridgeItem.slot(5, ShopCartridgeItem.ROW_SOLD)) instanceof GhostSlot, "ghost slots");
            context.assertEquals(player.getInventory().count(Items.CAKE), 0, "no cake given");
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            context.assertTrue(!stall.canUse(player), "the cartridge gone: the stall closes");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    /** The stock per offer: how many sales its chest still holds, nothing without an offer, no storage in hand. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stockIsCountedPerOffer(TestContext context) {
        context.setBlockState(CHEST, Blocks.CHEST);
        Inventory chest = context.getBlockEntity(CHEST);
        chest.setStack(0, new ItemStack(Items.DIAMOND, 10));
        chest.setStack(1, new ItemStack(Items.BREAD, 64));
        ItemStack cartridge = new ItemStack(ModItems.SHOP_CARTRIDGE);
        ShopCartridgeItem.setOffers(cartridge, nineOffers());
        int[] alone = ShopStock.of(cartridge, context.getWorld(), null);
        context.assertEquals(alone[0], ShopStock.NO_STORAGE, "in hand, nothing linked: no storage");
        CartridgeContainers.set(cartridge, List.of(GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(CHEST))));
        int[] stock = ShopStock.of(cartridge, context.getWorld(), null);
        context.assertEquals(stock[0], 10, "10 diamonds, 1 per sale");
        context.assertEquals(stock[2], 21, "64 breads, 3 per sale");
        context.assertEquals(stock[1], 0, "no apple: sold out");
        context.assertEquals(stock.length, ShopCartridgeItem.OFFERS, "one per column");
        ShopCartridgeItem.setOffers(cartridge, List.of(List.of(new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.EMERALD))));
        stock = ShopStock.of(cartridge, context.getWorld(), null);
        context.assertTrue(stock[0] == 3 && stock[1] == ShopStock.NO_OFFER, "3 sales of 3 diamonds; no offer in column 2");
        context.assertEquals(ShopStock.label(ShopStock.INFINITE), "∞", "an infinite bank");
        context.assertEquals(ShopStock.label(1500), "1k", "a thousand and more");
        context.assertTrue(ShopStock.label(ShopStock.NO_STORAGE) == null && ShopStock.label(ShopStock.NO_OFFER) == null, "nothing shown");
        context.complete();
    }

    /** The merchant summoned by its space sells all 9 offers and shows them on his stall. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void merchantShowsNineOffers(TestContext context) {
        ItemStack cartridge = new ItemStack(ModItems.SHOP_CARTRIDGE);
        ShopCartridgeItem.setOffers(cartridge, nineOffers());
        BoardSpaceBlockEntity tile = tile(context, cartridge);
        BoardShop shop = new BoardShop(context.getWorld(), tile.getPos());
        context.assertEquals(shop.offers().size(), 9, "the shop sells 9 offers");
        BoxedTraderEntity trader = ModEntities.BOXED_TRADER_ENTITY.create(context.getWorld());
        context.assertTrue(trader != null, "a merchant");
        trader.setBoardShop(shop, null);
        context.assertEquals(trader.getShopItems().size(), 9, "his stall shows the 9 items sold");
        context.assertTrue(trader.getShopItems().get(8).isOf(Items.BONE), "the 9th");
        trader.discard();
        context.complete();
    }

    /** The menu has no ghost slots any more: its small stall opens the stall, whose back button returns to it. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void smallStallOpensTheStallAndBack(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            ItemStack cartridge = new ItemStack(ModItems.SHOP_CARTRIDGE);
            player.setStackInHand(Hand.MAIN_HAND, cartridge);
            CartridgeRef hand = CartridgeRef.hand(Hand.MAIN_HAND);
            context.assertTrue(((ShopCartridgeItem) ModItems.SHOP_CARTRIDGE).modules().getFirst() instanceof StallOffersModule, "its offers: a small stall");
            context.assertTrue(CartridgeMenus.open(player, hand), "its menu opens");
            context.assertTrue(player.currentScreenHandler instanceof CartridgeScreenHandler menu && menu.withInventory(), "its menu, with the inventory (to pick its disguise)");
            new CartridgeSettingPayload(player.currentScreenHandler.syncId, "offers", StallOffersModule.OPEN).handle(player);
            context.assertTrue(player.currentScreenHandler instanceof ShopStallScreenHandler, "the stall opens");
            context.assertTrue(player.currentScreenHandler.onButtonClick(player, ShopStallScreenHandler.BACK), "back");
            context.assertTrue(player.currentScreenHandler instanceof CartridgeScreenHandler, "back to its menu");
            player.closeHandledScreen();

            // From a tile's interface: back to it, the cartridge's slot selected
            ItemStack inTile = new ItemStack(ModItems.SHOP_CARTRIDGE);
            BoardSpaceBlockEntity tile = tile(context, inTile);
            Vec3d near = tile.getPos().toCenterPos().add(1.5, 0.5, 0);
            player.refreshPositionAndAngles(near.x, near.y, near.z, 0, 0);
            player.openHandledScreen(tile);
            context.assertTrue(player.currentScreenHandler instanceof BoardSpaceScreenHandler, "the tile's interface");
            new CartridgeSettingPayload(player.currentScreenHandler.syncId, "offers", StallOffersModule.OPEN).handle(player);
            context.assertTrue(player.currentScreenHandler instanceof ShopStallScreenHandler stall
                    && stall.ref().equals(CartridgeRef.slot(tile.getPos(), 0)), "the tile's cartridge on the stall");
            player.currentScreenHandler.onButtonClick(player, ShopStallScreenHandler.BACK);
            context.assertTrue(player.currentScreenHandler instanceof BoardSpaceScreenHandler back && back.getSelectedSlot() == 0, "back to the tile");
            player.closeHandledScreen();
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }
}
