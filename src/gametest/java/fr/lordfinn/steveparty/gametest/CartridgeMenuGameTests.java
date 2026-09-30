package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.ReplayBoardSpaceBehavior;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.StopBoardSpaceBehavior;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TeleportNetwork;
import fr.lordfinn.steveparty.components.TeleportSettingsComponent;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.AdvanceBackCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.TeleportCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeLayout;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenus;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeRef;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ColorModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.GhostSlotsModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.NumberModule;
import fr.lordfinn.steveparty.payloads.custom.CartridgeSettingPayload;
import fr.lordfinn.steveparty.screen_handlers.custom.BoardSpaceScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.custom.CartridgeScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.custom.GhostSlot;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The cartridge menus: every cartridge declares modules that fit the shell, a change is checked (who, where, which
 * module, which value) then written, each cartridge's settings go and come back through its modules, and the Inventory
 * Cartridge's ghost slots work in hand and in a tile.
 */
public class CartridgeMenuGameTests implements FabricGameTest {
    private static final BlockPos TILE = new BlockPos(2, 1, 2);

    private static final List<Item> CARTRIDGES = List.of(ModItems.BOARD_SPACE_BEHAVIOR, ModItems.TILE_BEHAVIOR_START,
            ModItems.BOARD_SPACE_BEHAVIOR_STOP, ModItems.INVENTORY_CARTRIDGE, ModItems.SHOP_CARTRIDGE,
            ModItems.ADVANCE_BACK_CARTRIDGE, ModItems.REPLAY_CARTRIDGE, ModItems.TELEPORT_CARTRIDGE);

    private static BoardSpaceBlockEntity tile(TestContext context, net.minecraft.block.Block block, ItemStack cartridge) {
        context.setBlockState(TILE.down(), Blocks.STONE);
        context.setBlockState(TILE, block);
        BoardSpaceBlockEntity tile = context.getBlockEntity(TILE);
        tile.setStack(0, cartridge);
        return tile;
    }

    private static ServerPlayerEntity playerNear(TestContext context, BlockPos absolute) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        Vec3d near = absolute.toCenterPos().add(1.5, 0.5, 0);
        player.refreshPositionAndAngles(near.x, near.y, near.z, 0, 0);
        return player;
    }

    private static void remove(TestContext context, ServerPlayerEntity player) {
        context.getWorld().getServer().getPlayerManager().remove(player);
    }

    /** Every cartridge: modules with unique ids, laid out in one column beside a tile and within 240 × 320 in hand. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyCartridgeDeclaresModulesThatFit(TestContext context) {
        for (Item item : CARTRIDGES) {
            List<CartridgeModule> modules = ((CartridgeItem) item).modules();
            String name = item.toString();
            context.assertTrue(!modules.isEmpty(), name + ": has modules");
            Set<String> ids = new HashSet<>();
            for (CartridgeModule module : modules) context.assertTrue(ids.add(module.id()), name + ": unique id " + module.id());
            CartridgeLayout beside = CartridgeLayout.of(modules, CartridgeLayout.MAX_CONTENT_BESIDE_TILE);
            context.assertEquals(beside.columns(), 1, name + ": one column beside the tile");
            context.assertTrue(beside.height() <= 183, name + ": not taller than the tile");
            boolean ghosts = CartridgeLayout.indexOf(modules, GhostSlotsModule.class) >= 0;
            CartridgeLayout inHand = CartridgeLayout.of(modules, ghosts ? CartridgeLayout.MAX_CONTENT_WITH_INVENTORY : CartridgeLayout.MAX_CONTENT_ALONE);
            int height = inHand.height() + (ghosts ? CartridgeLayout.INVENTORY_GAP + CartridgeLayout.INVENTORY_H : 0);
            context.assertTrue(height <= 240 && inHand.width() <= 320, name + ": fits the smallest GUI in hand (" + inHand.width() + "x" + height + ")");
        }
        // The ghost slots are the Inventory Cartridge's first module: the tile's interface places them there
        context.assertTrue(((CartridgeItem) ModItems.INVENTORY_CARTRIDGE).modules().getFirst() instanceof GhostSlotsModule, "ghost slots first");
        context.complete();
    }

    /** A change is written only for a player allowed there, a known module that can change now, and a valid value. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void changesAreCheckedThenWritten(TestContext context) {
        BoardSpaceBlockEntity tile = tile(context, ModBlocks.TILE, new ItemStack(ModItems.ADVANCE_BACK_CARTRIDGE));
        BlockPos pos = tile.getPos();
        CartridgeRef ref = CartridgeRef.slot(pos, 0);
        ServerPlayerEntity player = playerNear(context, pos);
        try {
            context.assertTrue(CartridgeMenus.apply(player, ref, "steps", 5), "allowed");
            context.assertEquals(AdvanceBackCartridgeItem.steps(tile.getStack(0)), 5, "written in the tile's cartridge");
            context.assertTrue(!CartridgeMenus.apply(player, ref, "steps", 7), "out of range: refused");
            context.assertTrue(!CartridgeMenus.apply(player, ref, "steps", 0), "under the range: refused");
            context.assertTrue(!CartridgeMenus.apply(player, ref, "nope", 1), "unknown module: refused");
            context.assertTrue(!CartridgeMenus.apply(player, ref, "hint", 0), "a read-only module: refused");
            context.assertTrue(!CartridgeMenus.apply(player, CartridgeRef.slot(pos, 3), "steps", 2), "no such slot: refused");
            player.changeGameMode(GameMode.ADVENTURE);
            context.assertTrue(!CartridgeMenus.apply(player, ref, "steps", 2), "adventure: refused");
            player.changeGameMode(GameMode.SPECTATOR);
            context.assertTrue(!CartridgeMenus.apply(player, ref, "steps", 2), "spectator: refused");
            player.changeGameMode(GameMode.SURVIVAL);
            Vec3d far = pos.toCenterPos().add(20, 0.5, 0);
            player.refreshPositionAndAngles(far.x, far.y, far.z, 0, 0);
            context.assertTrue(!CartridgeMenus.apply(player, ref, "steps", 2), "out of reach: refused");
            context.assertEquals(AdvanceBackCartridgeItem.steps(tile.getStack(0)), 5, "unchanged");

            // A module that can't change now: the next space's effect, when the token stays on the Teleport tile
            ItemStack teleport = new ItemStack(ModItems.TELEPORT_CARTRIDGE);
            player.setStackInHand(Hand.MAIN_HAND, teleport);
            CartridgeRef hand = CartridgeRef.hand(Hand.MAIN_HAND);
            context.assertTrue(!CartridgeMenus.apply(player, hand, "triggers", 1), "no current to « triggers » while staying");
            context.assertTrue(CartridgeMenus.apply(player, hand, "arrival", 1), "moving on");
            context.assertTrue(CartridgeMenus.apply(player, hand, "triggers", 1), "now it can");
            context.assertTrue(!TeleportCartridgeItem.settings(teleport).pushTriggers(), "the next space does nothing");
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
            context.assertTrue(!CartridgeMenus.apply(player, hand, "arrival", 0), "not a cartridge: refused");
        } finally {
            remove(context, player);
        }
        context.complete();
    }

    /** The payload: sent back and forth intact, applied only to the menu open in that screen. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void payloadGoesToTheOpenMenuOnly(TestContext context) {
        CartridgeSettingPayload payload = new CartridgeSettingPayload(7, "purchases", 4);
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
        CartridgeSettingPayload.CODEC.encode(buf, payload);
        context.assertEquals(CartridgeSettingPayload.CODEC.decode(buf), payload, "sent");

        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        try {
            ItemStack shop = new ItemStack(ModItems.SHOP_CARTRIDGE);
            player.setStackInHand(Hand.MAIN_HAND, shop);
            new CartridgeSettingPayload(player.currentScreenHandler.syncId, "purchases", 4).handle(player);
            context.assertEquals(ShopCartridgeItem.purchases(shop), ShopCartridgeItem.DEFAULT_PURCHASES, "no menu open: ignored");

            CartridgeScreenHandler menu = new CartridgeScreenHandler(42, player.getInventory(), CartridgeRef.hand(Hand.MAIN_HAND));
            player.currentScreenHandler = menu;
            new CartridgeSettingPayload(41, "purchases", 4).handle(player);
            context.assertEquals(ShopCartridgeItem.purchases(shop), ShopCartridgeItem.DEFAULT_PURCHASES, "another screen: ignored");
            new CartridgeSettingPayload(42, "purchases", 4).handle(player);
            context.assertEquals(ShopCartridgeItem.purchases(shop), 4, "its menu: applied");
            new CartridgeSettingPayload(42, "purchases", 99).handle(player);
            context.assertEquals(ShopCartridgeItem.purchases(shop), 4, "invalid value: ignored");
            context.assertTrue(menu.canUse(player), "the menu stays open while the cartridge is in hand");
            context.assertTrue(!menu.withInventory() && menu.slots.isEmpty(), "no inventory without ghost slots");
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            context.assertTrue(!menu.canUse(player), "the cartridge gone: the menu closes");
            player.currentScreenHandler = player.playerScreenHandler;
        } finally {
            remove(context, player);
        }
        context.complete();
    }

    /** Each cartridge's settings through its modules: every value written reads back, in hand and in a tile. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyModuleRoundTrips(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        try {
            CartridgeRef hand = CartridgeRef.hand(Hand.MAIN_HAND);
            for (Item item : CARTRIDGES) {
                ItemStack stack = new ItemStack(item);
                player.setStackInHand(Hand.MAIN_HAND, stack);
                for (CartridgeModule module : ((CartridgeItem) item).modules()) {
                    if (!module.editable()) continue;
                    int[] values = switch (module) {
                        case ChoiceModule choice -> java.util.stream.IntStream.range(0, choice.options().size()).toArray();
                        case NumberModule number -> java.util.stream.IntStream.rangeClosed(number.min(), number.max()).toArray();
                        case ColorModule color -> java.util.stream.IntStream.rangeClosed(0, ColorModule.DEFAULT).toArray();
                        default -> new int[0];
                    };
                    // Walk every value, then back to the first (a module that can't change now is skipped)
                    for (int value : values) {
                        if (!module.enabled(stack)) break;
                        context.assertTrue(CartridgeMenus.apply(player, hand, module.id(), value), item + " " + module.id() + " = " + value);
                        context.assertEquals(module.get(stack), value, item + " " + module.id() + " reads back");
                    }
                }
            }

            // What the modules write: the same components as the other ways of setting them
            ItemStack advance = new ItemStack(ModItems.ADVANCE_BACK_CARTRIDGE);
            player.setStackInHand(Hand.MAIN_HAND, advance);
            CartridgeMenus.apply(player, hand, "steps", 4);
            CartridgeMenus.apply(player, hand, "direction", 0);
            context.assertEquals(AdvanceBackCartridgeItem.steps(advance), -4, "back 4");
            CartridgeMenus.apply(player, hand, "steps", 2);
            context.assertEquals(AdvanceBackCartridgeItem.steps(advance), -2, "still back");
            AdvanceBackCartridgeItem.scroll(advance, 1);
            context.assertEquals(AdvanceBackCartridgeItem.steps(advance), -1, "sneak + wheel still works");

            ItemStack shop = new ItemStack(ModItems.SHOP_CARTRIDGE);
            player.setStackInHand(Hand.MAIN_HAND, shop);
            CartridgeMenus.apply(player, hand, "purchases", 3);
            context.assertEquals(shop.get(ModComponents.SHOP_PURCHASES), 3, "3 purchases");
            CartridgeMenus.apply(player, hand, "purchases", ShopCartridgeItem.DEFAULT_PURCHASES);
            context.assertTrue(!shop.contains(ModComponents.SHOP_PURCHASES), "the default is not stored");
            ShopCartridgeItem.scroll(player, shop, 1);
            context.assertEquals(ShopCartridgeItem.purchases(shop), 2, "sneak + wheel still works");

            ItemStack inventory = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
            player.setStackInHand(Hand.MAIN_HAND, inventory);
            CartridgeMenus.apply(player, hand, "mode", InventoryCartridgeItem.CYCLE);
            context.assertEquals(InventoryCartridgeItem.getSelectionState(inventory), InventoryCartridgeItem.CYCLE, "cycle");

            ItemStack stop = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP);
            player.setStackInHand(Hand.MAIN_HAND, stop);
            CartridgeMenus.apply(player, hand, "color", DyeColor.LIME.getId());
            context.assertEquals(stop.get(ModComponents.COLOR), DyeColor.LIME.getEntityColor(), "lime stop");
            CartridgeMenus.apply(player, hand, "color", ColorModule.DEFAULT);
            context.assertEquals(stop.get(ModComponents.COLOR), StopBoardSpaceBehavior.COLOR & 0xFFFFFF, "its own colour back");
            context.assertEquals(new ColorModule("c", "c", ReplayBoardSpaceBehavior.COLOR).get(new ItemStack(ModItems.REPLAY_CARTRIDGE)),
                    ColorModule.DEFAULT, "a new Replay Cartridge: its own colour");

            // On a tile: the tile takes the changes (its colour follows the network)
            BoardSpaceBlockEntity tile = tile(context, ModBlocks.TILE, new ItemStack(ModItems.TELEPORT_CARTRIDGE));
            Vec3d near = tile.getPos().toCenterPos().add(1.5, 0.5, 0);
            player.refreshPositionAndAngles(near.x, near.y, near.z, 0, 0);
            CartridgeRef onTile = CartridgeRef.slot(tile.getPos(), 0);
            context.assertTrue(CartridgeMenus.apply(player, onTile, "network", TeleportNetwork.BLUE.ordinal()), "blue network");
            context.assertEquals(TeleportCartridgeItem.settings(tile.getStack(0)),
                    TeleportSettingsComponent.DEFAULT.withNetwork(TeleportNetwork.BLUE), "written in the tile");
            context.assertEquals(tile.getStack(0).get(ModComponents.COLOR), TeleportNetwork.BLUE.color(), "blue tile");
        } finally {
            remove(context, player);
        }
        context.complete();
    }

    /**
     * The ghost slots: a copy of an item (the real one stays), a quantity with the wheel, written into the cartridge;
     * in a tile's interface only while an Inventory Cartridge is selected, never filled by a shift-click.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ghostSlotsInHandAndInTiles(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        try {
            ItemStack cartridge = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
            player.setStackInHand(Hand.MAIN_HAND, cartridge);
            CartridgeScreenHandler menu = new CartridgeScreenHandler(3, player.getInventory(), CartridgeRef.hand(Hand.MAIN_HAND));
            player.currentScreenHandler = menu;
            context.assertTrue(menu.withInventory(), "the inventory shown with the ghost slots");
            Slot ghost = menu.slots.getFirst();
            context.assertTrue(ghost instanceof GhostSlot, "ghost slots first");
            menu.setCursorStack(new ItemStack(Items.DIAMOND, 5));
            menu.onSlotClick(0, 0, SlotActionType.PICKUP, player);
            context.assertEquals(menu.getCursorStack().getCount(), 5, "the real diamonds stay on the cursor");
            menu.setCursorStack(ItemStack.EMPTY);
            menu.handleGhostScroll(player, 0, 1);
            menu.handleGhostScroll(player, 0, 1);
            ItemStack stored = cartridge.get(ModComponents.INVENTORY_COMPONENT).getStack(0);
            context.assertTrue(stored.isOf(Items.DIAMOND) && stored.getCount() == 3, "3 diamonds given, written in the cartridge: " + stored);
            player.currentScreenHandler = player.playerScreenHandler;

            // In an Advanced Tile: slot 2 holds the Inventory Cartridge, slot 0 a Stop
            BoardSpaceBlockEntity tile = tile(context, ModBlocks.ADVANCED_TILE, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP));
            ItemStack inTile = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
            tile.setStack(2, inTile);
            Vec3d near = tile.getPos().toCenterPos().add(1.5, 0.5, 0);
            player.refreshPositionAndAngles(near.x, near.y, near.z, 0, 0);
            BoardSpaceScreenHandler handler = new BoardSpaceScreenHandler(4, player.getInventory(), tile);
            player.currentScreenHandler = handler;
            int firstGhost = -1;
            for (Slot slot : handler.slots) if (slot instanceof GhostSlot && firstGhost < 0) firstGhost = slot.id;
            context.assertTrue(firstGhost > 16 + 35, "ghost slots after the player's");
            context.assertTrue(!handler.slots.get(firstGhost).isEnabled(), "a Stop selected: no ghost slots");
            context.assertEquals(handler.editedCartridge(player), CartridgeRef.slot(tile.getPos(), 0), "the active slot selected");
            context.assertTrue(handler.onButtonClick(player, 2), "select slot 2");
            context.assertTrue(!handler.onButtonClick(player, 16), "no slot 16");
            context.assertTrue(handler.slots.get(firstGhost).isEnabled(), "the Inventory Cartridge selected: its ghost slots");
            handler.setCursorStack(new ItemStack(Items.EMERALD));
            handler.onSlotClick(firstGhost + 4, 0, SlotActionType.PICKUP, player);
            handler.setCursorStack(ItemStack.EMPTY);
            context.assertTrue(inTile.get(ModComponents.INVENTORY_COMPONENT).getStack(4).isOf(Items.EMERALD), "written in the tile's cartridge");
            // A shift-click on a cartridge of the tile goes to the player's inventory, never to a ghost slot
            player.getInventory().clear();
            handler.quickMove(player, 0);
            context.assertTrue(handler.slots.get(firstGhost).getStack().isEmpty(), "no stop in the ghost slots");
            context.assertTrue(player.getInventory().contains(new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP)), "the stop in the inventory");
            // A player not allowed there: the ghost slots don't change
            player.changeGameMode(GameMode.ADVENTURE);
            handler.setCursorStack(new ItemStack(Items.APPLE));
            handler.onSlotClick(firstGhost + 5, 0, SlotActionType.PICKUP, player);
            handler.setCursorStack(ItemStack.EMPTY);
            context.assertTrue(inTile.get(ModComponents.INVENTORY_COMPONENT).getStack(5).isEmpty(), "adventure: no change");
            player.currentScreenHandler = player.playerScreenHandler;
        } finally {
            remove(context, player);
        }
        context.complete();
    }
}
