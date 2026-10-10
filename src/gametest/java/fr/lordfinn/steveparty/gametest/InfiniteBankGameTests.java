package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.CartridgeTransfers;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyBank;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyResources;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.TrichaudronCartridgeItem;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler;
import fr.lordfinn.steveparty.service.BoardShop;
import fr.lordfinn.steveparty.service.PartyStars;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.List;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.CONTROLLER;

/**
 * The Party Controller's « Infinite bank »: switched by a player in creative mode or an operator only (checked by the
 * server), saved; on, every consumer of the party's one source ({@link PartyResources}) is served without any stock
 * and what goes in is absorbed; off, the real bank (nothing made from nothing).
 */
public class InfiniteBankGameTests implements SteveGameTest {
    private static final ItemStack DIAMOND = new ItemStack(Items.DIAMOND);

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "infinite_bank_switch")
    public void onlyCreativeOrOperatorSwitchesIt(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        try {
            context.setBlockState(CONTROLLER.down(), Blocks.STONE);
            context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
            PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
            PartyControllerScreenHandler handler = new PartyControllerScreenHandler(1, player.getInventory(), controller);
            boolean operator = player.hasPermissionLevel(2);
            context.assertEquals(handler.onButtonClick(player, PartyControllerScreenHandler.BUTTON_INFINITE_BANK), operator,
                    "survival: refused unless operator");
            context.assertEquals(controller.isInfiniteBank(), operator, "survival, not operator: unchanged");
            controller.setInfiniteBank(false);
            player.changeGameMode(GameMode.CREATIVE);
            context.assertTrue(handler.onButtonClick(player, PartyControllerScreenHandler.BUTTON_INFINITE_BANK), "creative: switched");
            context.assertTrue(controller.isInfiniteBank(), "on");
            context.assertEquals(PartyBank.status(controller, context.getWorld().getServer(), 4).state(), PartyBank.State.INFINITE,
                    "the dashboard says so");
            RegistryWrapper.WrapperLookup registries = context.getWorld().getRegistryManager();
            NbtCompound saved = controller.createNbt(registries);
            controller.setInfiniteBank(false);
            controller.read(saved, registries);
            context.assertTrue(controller.isInfiniteBank(), "saved and loaded");
            saved.remove("InfiniteBank");
            controller.read(saved, registries);
            context.assertTrue(!controller.isInfiniteBank(), "a controller saved without it: off");
            context.setBlockState(CONTROLLER, Blocks.AIR);
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "infinite_bank_serves")
    public void everyConsumerIsServedWithoutStock(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            player.getInventory().clear();
            MobEntity token = DiceTestKit.token(context, new BlockPos(1, 1, 1), player.getUuid());
            PartyControllerEntity controller = DiceTestKit.party(context, player.getUuid(), token);
            controller.getBankItems().clear();
            ItemStack coin = controller.getCurrency(PartyCurrency.COIN), star = controller.getCurrency(PartyCurrency.STAR);
            BoardSpaceBlockEntity shop = DiceTestKit.tile(context, new BlockPos(3, 1, 3), new ItemStack(ModItems.SHOP_CARTRIDGE));
            ItemStack trichaudronCartridge = new ItemStack(ModItems.TRICHAUDRON_CARTRIDGE);
            TrichaudronCartridgeItem.setFilters(trichaudronCartridge, List.of(DIAMOND.copyWithCount(2)));
            BoardSpaceBlockEntity trichaudron = DiceTestKit.tile(context, new BlockPos(5, 1, 3), trichaudronCartridge);
            BoardSpaceBlockEntity inventory = DiceTestKit.tile(context, new BlockPos(7, 1, 3), new ItemStack(ModItems.INVENTORY_CARTRIDGE));

            // Off: the real bank, empty: nothing for anyone
            context.assertTrue(!controller.payGains(player, 1, PartyResources.of(controller)).full(), "off: an empty bank pays nothing");
            context.assertEquals(PartyStars.bankStars(controller, context.getWorld()), 0, "off: no star to sell");
            context.assertTrue(!new BoardShop(context.getWorld(), shop.getPos()).inStock(DIAMOND), "off: sold out");
            context.assertTrue(TrichaudronCartridgeItem.available(trichaudron.getStack(0), context.getWorld(), trichaudron.getPos()).isEmpty(),
                    "off: the Tricauldron has nothing");
            context.assertEquals(CartridgeTransfers.source(context.getWorld(), inventory.getStack(0), inventory.getPos()).available(DIAMOND), 0,
                    "off: the Inventory space has nothing");

            // On: everything asked is there
            controller.setInfiniteBank(true);
            PartyControllerEntity.Paid paid = controller.payGains(player, 1, PartyResources.of(controller));
            context.assertTrue(paid.full() && paid.coins() == 10, "the mini-game gains paid in full");
            context.assertEquals(InventoryUtils.count(player.getInventory(), coin), 10, "10 coins received");
            context.assertTrue(PartyStars.bankStars(controller, context.getWorld()) > 0, "a star to sell");
            context.assertEquals(PartyResources.of(controller).take(star, 1), 1, "the star given");
            BoardShop boardShop = new BoardShop(context.getWorld(), shop.getPos());
            context.assertTrue(boardShop.inStock(DIAMOND.copyWithCount(64)), "the shop is in stock");
            List<ItemStack> prizes = TrichaudronCartridgeItem.available(trichaudron.getStack(0), context.getWorld(), trichaudron.getPos());
            context.assertTrue(prizes.size() == 1 && prizes.getFirst().isOf(Items.DIAMOND) && prizes.getFirst().getCount() == 2,
                    "the Tricauldron's prize is there");
            PartyResources space = CartridgeTransfers.source(context.getWorld(), inventory.getStack(0), inventory.getPos());
            context.assertEquals(CartridgeTransfers.transfer(DIAMOND.copyWithCount(3), space, player), 3, "the Inventory space gives");
            context.assertEquals(InventoryUtils.count(player.getInventory(), DIAMOND), 3, "3 diamonds received");
            // What goes in is absorbed
            ItemStack deposit = coin.copyWithCount(7);
            PartyResources.deposit(controller, deposit);
            context.assertTrue(deposit.isEmpty() && controller.getBankItems().isEmpty(), "a deposit is absorbed");
            context.assertTrue(controller.getBankItems().isEmpty(), "its inventory untouched");

            // Off again: the real bank
            controller.setInfiniteBank(false);
            context.assertEquals(PartyResources.of(controller).take(coin, 1), 0, "off again: nothing made");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }
}
