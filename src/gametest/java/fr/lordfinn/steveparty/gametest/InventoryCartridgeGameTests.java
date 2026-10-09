package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.CartridgeTransfers;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyBank;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenus;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeRef;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ContainersModule;
import fr.lordfinn.steveparty.screen_handlers.custom.BoardSpaceScreenHandler;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.List;

/**
 * The Inventory Cartridge's containers: a list in order (at most 8), chosen by a click read by its target, used in
 * order by board spaces and by a Party Controller's bank, and edited from its menu.
 */
public class InventoryCartridgeGameTests implements SteveGameTest {

    private static ServerPlayerEntity player(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        TestPlayers.place(context, player, 3.5, 1, 3.5);
        return player;
    }

    private static void click(TestContext context, ServerPlayerEntity player, Hand hand, BlockPos relative) {
        BlockPos abs = context.getAbsolutePos(relative);
        player.interactionManager.interactBlock(player, context.getWorld(), player.getStackInHand(hand), hand,
                new BlockHitResult(Vec3d.ofCenter(abs), Direction.UP, abs, false));
    }

    private static GlobalPos global(TestContext context, BlockPos relative) {
        return GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(relative));
    }

    /** A cartridge saved with one container reads as a list of it; the next change writes the list. Toggles, cap. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aListOfContainersInOrder(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemStack cartridge = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
        cartridge.set(ModComponents.INVENTORY_POS, context.getAbsolutePos(new BlockPos(1, 1, 1)));
        context.assertEquals(CartridgeContainers.of(cartridge, world.getRegistryKey()), List.of(global(context, new BlockPos(1, 1, 1))),
                "the former single container is a list of one");
        context.assertEquals(CartridgeContainers.toggle(cartridge, world, context.getAbsolutePos(new BlockPos(2, 1, 1))), CartridgeContainers.Toggle.ADDED, "a second");
        context.assertTrue(cartridge.get(ModComponents.INVENTORY_POS) == null && cartridge.get(ModComponents.INVENTORY_CONTAINERS).size() == 2,
                "written as a list, the former field gone");
        for (int x = 3; x < 3 + CartridgeContainers.MAX - 2; x++) CartridgeContainers.toggle(cartridge, world, context.getAbsolutePos(new BlockPos(x, 1, 1)));
        context.assertEquals(CartridgeContainers.of(cartridge, world.getRegistryKey()).size(), CartridgeContainers.MAX, "8 containers");
        context.assertEquals(CartridgeContainers.toggle(cartridge, world, context.getAbsolutePos(new BlockPos(1, 1, 5))), CartridgeContainers.Toggle.FULL, "no ninth");
        context.assertEquals(CartridgeContainers.toggle(cartridge, world, context.getAbsolutePos(new BlockPos(1, 1, 1))), CartridgeContainers.Toggle.REMOVED, "the first again: out");
        context.assertEquals(CartridgeContainers.of(cartridge, world.getRegistryKey()).getFirst(), global(context, new BlockPos(2, 1, 1)), "the order kept");
        context.complete();
    }

    /**
     * The click reads its target, in either hand: a chest is added (it does not open); with the cartridge in the off
     * hand and nothing in the main hand too; sneaking on a board space adds a destination; a plain click on a board
     * space opens its interface (where cartridges go in); a click on stone makes it a destination.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theClickDecidesByItsTarget(TestContext context) {
        for (int x = 0; x < 6; x++) for (int z = 0; z < 6; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
        BlockPos chest = new BlockPos(1, 1, 1), barrel = new BlockPos(2, 1, 1), tile = new BlockPos(4, 1, 1), stone = new BlockPos(1, 1, 4);
        context.setBlockState(chest, Blocks.CHEST);
        context.setBlockState(barrel, Blocks.BARREL);
        context.setBlockState(tile, ModBlocks.TILE);
        context.setBlockState(stone, Blocks.STONE);
        ServerPlayerEntity player = player(context);
        try {
            ItemStack cartridge = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
            player.setStackInHand(Hand.MAIN_HAND, cartridge);
            click(context, player, Hand.MAIN_HAND, chest);
            context.assertEquals(CartridgeContainers.in(cartridge, context.getWorld()), List.of(context.getAbsolutePos(chest)), "main hand, a chest: added");
            context.assertTrue(!(player.currentScreenHandler instanceof GenericContainerScreenHandler), "it did not open");

            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            player.setStackInHand(Hand.OFF_HAND, cartridge);
            click(context, player, Hand.MAIN_HAND, barrel);
            context.assertEquals(CartridgeContainers.in(cartridge, context.getWorld()),
                    List.of(context.getAbsolutePos(chest), context.getAbsolutePos(barrel)), "off hand, nothing in the main hand, a barrel: added after");
            context.assertTrue(!(player.currentScreenHandler instanceof GenericContainerScreenHandler), "the barrel did not open");
            player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);

            player.setStackInHand(Hand.MAIN_HAND, cartridge);
            player.setSneaking(true);
            click(context, player, Hand.MAIN_HAND, tile);
            player.setSneaking(false);
            DestinationsComponent destinations = cartridge.getOrDefault(ModComponents.DESTINATIONS_COMPONENT, DestinationsComponent.DEFAULT);
            context.assertTrue(destinations.destinations().contains(context.getAbsolutePos(tile)), "sneaking on a board space: a destination");
            context.assertEquals(CartridgeContainers.in(cartridge, context.getWorld()).size(), 2, "the board space is no container of it");

            click(context, player, Hand.MAIN_HAND, tile);
            context.assertTrue(player.currentScreenHandler instanceof BoardSpaceScreenHandler,
                    "a plain click on a board space: its interface, where cartridges are put in");
            player.closeHandledScreen();
            context.assertTrue(cartridge.getOrDefault(ModComponents.DESTINATIONS_COMPONENT, DestinationsComponent.DEFAULT).destinations().size() == 1,
                    "and no destination changed");

            click(context, player, Hand.MAIN_HAND, stone);
            context.assertTrue(cartridge.getOrDefault(ModComponents.DESTINATIONS_COMPONENT, DestinationsComponent.DEFAULT).destinations()
                    .equals(List.of(context.getAbsolutePos(tile), context.getAbsolutePos(stone)))
                    && CartridgeContainers.in(cartridge, context.getWorld()).size() == 2, "stone: a destination, no container");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    /** A board space's cartridge takes from the first container that has the item and gives to the first with room. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void transfersUseTheContainersInOrder(TestContext context) {
        BlockPos first = new BlockPos(1, 1, 1), second = new BlockPos(3, 1, 1);
        context.setBlockState(first, Blocks.CHEST);
        context.setBlockState(second, Blocks.CHEST);
        ChestBlockEntity a = context.getBlockEntity(first), b = context.getBlockEntity(second);
        a.setStack(0, new ItemStack(Items.DIAMOND, 1));
        b.setStack(0, new ItemStack(Items.DIAMOND, 5));
        // The first chest is full: what a player pays goes to the second
        for (int slot = 1; slot < a.size(); slot++) a.setStack(slot, new ItemStack(Items.DIRT, 64));
        ItemStack cartridge = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
        ItemStack price = new ItemStack(Items.EMERALD, 3);
        price.set(ModComponents.IS_NEGATIVE, true);
        cartridge.set(ModComponents.INVENTORY_COMPONENT, new InventoryComponent(List.of(price, new ItemStack(Items.DIAMOND, 3))));
        cartridge.set(ModComponents.SELECTION_STATE, InventoryCartridgeItem.ALL);
        CartridgeContainers.set(cartridge, List.of(global(context, first), global(context, second)));
        ServerPlayerEntity player = player(context);
        try {
            player.getInventory().insertStack(new ItemStack(Items.EMERALD, 3));
            int[] cycle = {0};
            context.assertTrue(CartridgeTransfers.apply(context.getWorld(), cartridge, player, () -> cycle[0], i -> cycle[0] = i), "done");
            context.assertTrue(a.count(Items.DIAMOND) == 0 && b.count(Items.DIAMOND) == 3, "1 diamond from the first, then 2 from the second");
            context.assertTrue(b.count(Items.EMERALD) == 3 && a.count(Items.EMERALD) == 0, "the price in the first with room: the second");
            context.assertEquals(player.getInventory().count(Items.DIAMOND), 3, "the player got 3 diamonds");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    /** The bank pays from its chests in order, counts them all, and skips one that is gone (the others pay). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theBankPaysFromItsChestsInOrder(TestContext context) {
        BlockPos first = new BlockPos(1, 1, 1), second = new BlockPos(3, 1, 1), gone = new BlockPos(5, 1, 1);
        context.setBlockState(first, Blocks.CHEST);
        context.setBlockState(second, Blocks.BARREL);
        ItemStack coin = new ItemStack(ModItems.COIN);
        ((Inventory) context.getBlockEntity(first)).setStack(0, coin.copyWithCount(3));
        ((Inventory) context.getBlockEntity(second)).setStack(0, coin.copyWithCount(10));
        ItemStack cartridge = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
        CartridgeContainers.set(cartridge, List.of(global(context, gone), global(context, first), global(context, second)));
        var server = context.getWorld().getServer();
        Inventory bank = PartyBank.inventory(server, cartridge);
        context.assertTrue(bank != null && InventoryUtils.count(bank, coin) == 13, "13 coins over the two chests there");
        context.assertEquals(InventoryUtils.take(bank, coin, 5), 5, "5 paid");
        context.assertTrue(((Inventory) context.getBlockEntity(first)).isEmpty() && InventoryUtils.count(context.getBlockEntity(second), coin) == 8,
                "the first chest first (3), then the second (2)");
        context.complete();
    }

    /** From its menu: an entry removed, moved down; a wrong index refused (checked on the server). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theMenuEditsTheList(TestContext context) {
        ServerPlayerEntity player = player(context);
        try {
            ItemStack cartridge = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
            List<GlobalPos> three = List.of(global(context, new BlockPos(1, 1, 1)), global(context, new BlockPos(2, 1, 1)), global(context, new BlockPos(3, 1, 1)));
            CartridgeContainers.set(cartridge, three);
            player.setStackInHand(Hand.MAIN_HAND, cartridge);
            CartridgeRef hand = CartridgeRef.hand(Hand.MAIN_HAND);
            context.assertTrue(CartridgeMenus.apply(player, hand, "chests", ContainersModule.action(0, ContainersModule.DOWN)), "the first moved down");
            context.assertEquals(CartridgeContainers.of(player.getMainHandStack(), context.getWorld().getRegistryKey()),
                    List.of(three.get(1), three.get(0), three.get(2)), "now second");
            context.assertTrue(!CartridgeMenus.apply(player, hand, "chests", ContainersModule.action(0, ContainersModule.UP)), "the first can't go up");
            context.assertTrue(!CartridgeMenus.apply(player, hand, "chests", ContainersModule.action(7, ContainersModule.REMOVE)), "no eighth to remove");
            context.assertTrue(CartridgeMenus.apply(player, hand, "chests", ContainersModule.action(2, ContainersModule.REMOVE)), "the third removed");
            context.assertEquals(CartridgeContainers.of(player.getMainHandStack(), context.getWorld().getRegistryKey()),
                    List.of(three.get(1), three.get(0)), "two left");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }
}
