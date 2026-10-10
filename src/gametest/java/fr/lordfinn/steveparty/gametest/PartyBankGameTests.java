package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestAsserts;
import fr.lordfinn.steveparty.gametest.kit.TestBank;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.MiniGameGains;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyBank;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGameResults;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The bank of a Party Controller: the chest its Inventory Cartridge remembers, the only source of the mini-games' gains. Paid in
 * the order of the places (the 1st first, the participants last), partially when the chest runs short, never
 * created; the results card says what was really paid; the dashboard says what the chest holds.
 */
public class PartyBankGameTests implements SteveGameTest {
    private static final AtomicInteger SERIAL = new AtomicInteger();
    private static final BlockPos CONTROLLER = new BlockPos(1, 1, 6);
    private static final BlockPos CHEST = new BlockPos(3, 1, 6);

    private static ServerPlayerEntity player(TestContext context, String name, double x) {
        return TestPlayers.joined(context, "b", name, GameMode.CREATIVE, x, 1, 1.5);
    }

    /** The players go, and the controller and chests too (other tests look for the nearest party controller). */
    private static void remove(TestContext context, ServerPlayerEntity... players) {
        TestPlayers.remove(context, players);
        for (BlockPos pos : List.of(CONTROLLER, CHEST, CHEST.east())) {
            if (!context.getBlockState(pos).isAir()) context.setBlockState(pos, Blocks.AIR);
        }
    }

    /** A controller whose mini-game (a page without podium) is being played by the players, in that turn order. */
    private static MiniGamePartyStep playing(TestContext context, PartyControllerEntity controller, ServerPlayerEntity... players) {
        NbtCompound stepNbt = new NbtCompound();
        stepNbt.putString("Type", PartyStepType.MINI_GAME.name());
        stepNbt.putString("Status", PartyStep.Status.IN_PROGRESS.name());
        stepNbt.putString("Phase", MiniGamePartyStep.Phase.PLAYING.name());
        stepNbt.putBoolean("MiniGameChosen", true);
        NbtList list = new NbtList();
        List<UUID> uuids = new ArrayList<>();
        for (ServerPlayerEntity player : players) {
            list.add(NbtString.of(player.getUuid().toString()));
            uuids.add(player.getUuid());
        }
        stepNbt.put("Participants", list);
        MiniGamePartyStep step = new MiniGamePartyStep(stepNbt);
        UUID token = UUID.randomUUID();
        PartyData data = new PartyData();
        data.addToken(token);
        data.addStep(new PartyStep());
        data.addStep(step);
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(token))));
        data.setStepIndex(1);
        controller.setPartyData(data);
        ItemStack page = new ItemStack(ModItems.MINI_GAME_PAGE);
        UUID id = MiniGamePages.ensureId(page);
        MiniGamePages.update(context.getWorld().getServer(),
                MiniGamePages.get(context.getWorld().getServer(), id).withTexts("Bank test " + SERIAL.incrementAndGet(), ""));
        ItemStack catalogue = new ItemStack(ModItems.MINI_GAMES_CATALOGUE);
        MiniGamesCatalogueItem.setCurrentMiniGamePage(catalogue, page);
        MiniGamesCatalogueItem.setCurrentMiniGameTeamDisposition(catalogue, TeamDisposition.freeForAll(uuids));
        controller.catalogue = catalogue;
        return step;
    }

    private static PartyControllerEntity controller(TestContext context) {
        context.setBlockState(CONTROLLER.down(), Blocks.STONE);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        // 10, 5, 3, 1 coins; 2 for the participants; a star for the winner
        controller.setGains(MiniGameGains.DEFAULT.with(PartyCurrency.COIN, MiniGameGains.PARTICIPANTS, 2).with(PartyCurrency.STAR, 0, 1));
        return controller;
    }

    private static int coins(PartyControllerEntity controller, ServerPlayerEntity player) {
        return InventoryUtils.count(player.getInventory(), controller.getCurrency(PartyCurrency.COIN));
    }

    private static int stars(PartyControllerEntity controller, ServerPlayerEntity player) {
        return InventoryUtils.count(player.getInventory(), controller.getCurrency(PartyCurrency.STAR));
    }

    /** The coins shown on the results card for a player. */
    private static int shown(MiniGameResults results, ServerPlayerEntity player) {
        for (MiniGameResults.Row row : results.rows()) if (row.names().contains(player.getGameProfile().getName())) return row.coins();
        return -1;
    }

    private static PartyBank.Status status(TestContext context, PartyControllerEntity controller, int players) {
        return PartyBank.status(controller, context.getWorld().getServer(), players);
    }

    /** The containers a bank may be, and the cartridge: set on a chest (which does not open), cleared, put in the controller's slot. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void inventoryCartridgeAndBankSlot(TestContext context) {
        ServerWorld world = context.getWorld();
        for (var block : List.of(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.SHULKER_BOX)) {
            context.setBlockState(CHEST, block);
            context.assertTrue(PartyBank.isBank(world.getBlockEntity(context.getAbsolutePos(CHEST))), block + " can be a bank");
        }
        for (var block : List.of(Blocks.HOPPER, Blocks.DROPPER, Blocks.DISPENSER, Blocks.FURNACE)) {
            context.setBlockState(CHEST, block);
            context.assertTrue(!PartyBank.isBank(world.getBlockEntity(context.getAbsolutePos(CHEST))), block + " is no bank");
        }
        context.setBlockState(CHEST, Blocks.CHEST);
        ServerPlayerEntity player = player(context, "c", 1.5);
        try {
            ItemStack cartridge = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
            context.assertTrue(PartyBank.target(cartridge) == null, "a new cartridge remembers nothing");
            player.setStackInHand(Hand.MAIN_HAND, cartridge);
            BlockPos abs = context.getAbsolutePos(CHEST);
            var hit = new BlockHitResult(Vec3d.ofCenter(abs), Direction.UP, abs, false);
            player.interactionManager.interactBlock(player, world, cartridge, Hand.MAIN_HAND, hit);
            context.assertTrue(PartyBank.target(cartridge) != null && PartyBank.target(cartridge).pos().equals(abs)
                    && PartyBank.target(cartridge).dimension().equals(world.getRegistryKey()), "main hand, a click on the chest: it remembers it");
            context.assertTrue(!(player.currentScreenHandler instanceof GenericContainerScreenHandler), "the chest did not open");
            player.interactionManager.interactBlock(player, world, cartridge, Hand.MAIN_HAND, hit);
            context.assertTrue(PartyBank.target(cartridge) == null, "a click on the same chest: forgotten");
            // In the off hand (sneaking, as the main hand would open the chest): the same
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            player.setStackInHand(Hand.OFF_HAND, cartridge);
            player.setSneaking(true);
            player.interactionManager.interactBlock(player, world, cartridge, Hand.OFF_HAND, hit);
            context.assertTrue(PartyBank.target(cartridge) != null && PartyBank.target(cartridge).pos().equals(abs), "off hand: it remembers it too");
            player.setSneaking(false);
            player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);

            // The controller's bank slot: an Inventory Cartridge only, for who may edit the controller
            PartyControllerEntity controller = controller(context);
            PartyControllerScreenHandler handler = new PartyControllerScreenHandler(1, player.getInventory(), controller);
            var slot = handler.getSlot(PartyControllerScreenHandler.SLOT_BANK);
            context.assertTrue(!slot.canInsert(new ItemStack(Items.CHEST)), "not a chest");
            ItemStack set = TestBank.cartridge(context, CHEST);
            context.assertTrue(slot.canInsert(set), "an Inventory Cartridge");
            slot.setStack(set);
            context.assertTrue(ItemStack.areEqual(controller.getBank(), set), "it is the controller's bank");
            context.assertEquals(status(context, controller, 4).state(), PartyBank.State.SHORT, "an empty chest: too little");
            // Saved with the controller
            NbtCompound nbt = controller.createNbtWithIdentifyingData(world.getRegistryManager());
            controller.setBank(ItemStack.EMPTY);
            controller.read(nbt, world.getRegistryManager());
            context.assertTrue(ItemStack.areEqual(controller.getBank(), set), "saved and read back");
            player.changeGameMode(GameMode.ADVENTURE);
            context.assertTrue(!slot.canTakeItems(player), "adventure: can't take it");
        } finally {
            remove(context, player);
        }
        context.complete();
    }

    /** A chest with enough: every place paid in full, taken out of it; the results card shows it. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bank_full")
    public void fullPayoutFromTheChest(TestContext context) {
        ServerPlayerEntity p1 = player(context, "a", 0.5), p2 = player(context, "b", 1.5), p3 = player(context, "c", 2.5);
        try {
            PartyControllerEntity controller = controller(context);
            ChestBlockEntity chest = TestBank.stock(context, controller, CHEST, 20, 3);
            context.assertEquals(status(context, controller, 3).state(), PartyBank.State.OK, "enough for 3 players (10 + 2 + 2 coins, a star)");
            MiniGamePartyStep step = playing(context, controller, p1, p2, p3);
            context.assertTrue(step.finish(controller, List.of(p2.getUuid())), "p2 wins");
            context.assertEquals(coins(controller, p2), 10, "1st: 10 coins");
            context.assertEquals(stars(controller, p2), 1, "and a star");
            context.assertTrue(coins(controller, p1) == 2 && coins(controller, p3) == 2, "participants: 2 coins");
            context.assertEquals(TestAsserts.count(chest, controller.getCurrency(PartyCurrency.COIN)), 6, "taken out of the chest: 20 - 14");
            context.assertEquals(TestAsserts.count(chest, controller.getCurrency(PartyCurrency.STAR)), 2, "a star taken");
            MiniGameResults results = step.getLastResults();
            context.assertTrue(shown(results, p2) == 10 && shown(results, p1) == 2, "the results card: what was paid");
        } finally {
            remove(context, p1, p2, p3);
        }
        context.complete();
    }

    /**
     * A chest running short: the 1st place first, then the participants in turn order; a player gets what is left,
     * the next ones nothing. Items of the same kind but other components (renamed) are not the currency: left alone.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bank_partial")
    public void partialPayoutInTheOrderOfThePlaces(TestContext context) {
        ServerPlayerEntity p1 = player(context, "a", 0.5), p2 = player(context, "b", 1.5), p3 = player(context, "c", 2.5);
        try {
            PartyControllerEntity controller = controller(context);
            ChestBlockEntity chest = TestBank.stock(context, controller, CHEST, 11, 0);
            ItemStack renamed = controller.getCurrency(PartyCurrency.COIN).copyWithCount(30);
            renamed.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Fake"));
            chest.setStack(chest.size() - 1, renamed);
            context.assertEquals(status(context, controller, 3).state(), PartyBank.State.SHORT, "11 coins: too little for 14");
            context.assertEquals(status(context, controller, 3).coins(), 11, "the renamed ones don't count");
            MiniGamePartyStep step = playing(context, controller, p1, p2, p3);
            step.finish(controller, List.of(p2.getUuid()));
            context.assertEquals(coins(controller, p2), 10, "the 1st place is paid first: 10");
            context.assertEquals(stars(controller, p2), 0, "no star in the chest: none");
            context.assertEquals(coins(controller, p1), 1, "then the first participant in turn order: what is left");
            context.assertEquals(coins(controller, p3), 0, "the next one: nothing");
            context.assertEquals(TestAsserts.count(chest, controller.getCurrency(PartyCurrency.COIN)), 0, "the chest is empty");
            context.assertEquals(chest.getStack(chest.size() - 1).getCount(), 30, "the renamed coins are left");
            MiniGameResults results = step.getLastResults();
            context.assertTrue(shown(results, p2) == 10 && shown(results, p1) == 1 && shown(results, p3) == 0, "the card shows the amounts really paid");
        } finally {
            remove(context, p1, p2, p3);
        }
        context.complete();
    }

    /** No cartridge, or a cartridge whose chest was removed: nothing is paid, nothing is created. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bank_none")
    public void noChestNoGains(TestContext context) {
        ServerPlayerEntity p1 = player(context, "a", 0.5), p2 = player(context, "b", 1.5);
        try {
            PartyControllerEntity controller = controller(context);
            context.assertEquals(status(context, controller, 2), PartyBank.Status.NONE, "no cartridge: no bank");
            MiniGamePartyStep step = playing(context, controller, p1, p2);
            step.finish(controller, List.of(p1.getUuid()));
            context.assertTrue(coins(controller, p1) == 0 && stars(controller, p1) == 0, "no bank: nothing paid");
            context.assertEquals(shown(step.getLastResults(), p1), 0, "the card says so");

            TestBank.stock(context, controller, CHEST, 50, 5);
            context.setBlockState(CHEST, Blocks.AIR);
            context.assertEquals(status(context, controller, 2).state(), PartyBank.State.MISSING, "the chest was removed: missing");
            step = playing(context, controller, p1, p2);
            step.finish(controller, List.of(p1.getUuid()));
            context.assertEquals(coins(controller, p1), 0, "a removed chest pays nothing");
            context.setBlockState(CHEST, Blocks.HOPPER);
            context.assertEquals(status(context, controller, 2).state(), PartyBank.State.MISSING, "a hopper in its place is no bank");
        } finally {
            remove(context, p1, p2);
        }
        context.complete();
    }

    /** A double chest counts whole: the cartridge points to one half, the coins in the other are paid too. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bank_double")
    public void doubleChestCountsWhole(TestContext context) {
        ServerPlayerEntity p1 = player(context, "a", 0.5);
        try {
            PartyControllerEntity controller = controller(context);
            BlockPos other = CHEST.east();
            context.setBlockState(CHEST, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH).with(ChestBlock.CHEST_TYPE, ChestType.LEFT));
            context.setBlockState(other, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH).with(ChestBlock.CHEST_TYPE, ChestType.RIGHT));
            Inventory whole = PartyBank.inventory(context.getWorld(), context.getAbsolutePos(CHEST));
            context.assertEquals(whole == null ? 0 : whole.size(), 54, "one double chest");
            ChestBlockEntity otherHalf = context.getBlockEntity(other);
            otherHalf.setStack(5, controller.getCurrency(PartyCurrency.COIN).copyWithCount(12));
            controller.setBank(TestBank.cartridge(context, CHEST));
            context.assertEquals(status(context, controller, 1).coins(), 12, "the coins of the other half count");
            MiniGamePartyStep step = playing(context, controller, p1);
            step.finish(controller, List.of(p1.getUuid()));
            context.assertEquals(coins(controller, p1), 10, "paid from the other half");
            context.assertEquals(otherHalf.getStack(5).getCount(), 2, "taken from it");
        } finally {
            remove(context, p1);
        }
        context.complete();
    }

    /** A full inventory: the gain falls at the player's feet, as before. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bank_drop")
    public void fullInventoryDropsTheGain(TestContext context) {
        ServerPlayerEntity p1 = player(context, "a", 0.5);
        try {
            PartyControllerEntity controller = controller(context);
            TestBank.stock(context, controller, CHEST, 30, 0);
            for (int i = 0; i < p1.getInventory().main.size(); i++) p1.getInventory().main.set(i, new ItemStack(Items.DIRT, 64));
            MiniGamePartyStep step = playing(context, controller, p1);
            step.finish(controller, List.of(p1.getUuid()));
            int dropped = 0;
            for (ItemEntity item : context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(p1.getBlockPos()).expand(3),
                    item -> ItemStack.areItemsAndComponentsEqual(item.getStack(), controller.getCurrency(PartyCurrency.COIN))))
                dropped += item.getStack().getCount();
            context.assertEquals(dropped, 10, "the 10 coins fell at his feet");
        } finally {
            remove(context, p1);
        }
        context.complete();
    }

    /** The dashboard: what the chest holds, whether it can pay a whole mini-game, sent to the client as it is. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bank_dashboard")
    public void dashboardShowsTheBank(TestContext context) {
        ServerPlayerEntity player = player(context, "d", 1.5);
        try {
            PartyControllerEntity controller = controller(context);
            PartyDashboardData.Board board = new PartyDashboardData.Board(0, 0, List.of(), List.of());
            context.assertEquals(PartyDashboardData.capture(controller, context.getWorld(), player, board).bank(), PartyBank.Status.NONE, "no bank");
            TestBank.stock(context, controller, CHEST, 34, 2);
            // No player known: a mini-game of 4 (10 + 5 + 3 + 1 coins, a star)
            PartyDashboardData data = PartyDashboardData.capture(controller, context.getWorld(), player, board);
            context.assertEquals(data.bank(), new PartyBank.Status(PartyBank.State.OK, 34, 2), "34 coins, 2 stars: enough");
            RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
            PartyDashboardData.PACKET_CODEC.encode(buf, data);
            context.assertEquals(PartyDashboardData.PACKET_CODEC.decode(buf).bank(), data.bank(), "sent and read back");
            buf.release();
            controller.setGains(controller.getGains().with(PartyCurrency.COIN, 0, 40));
            context.assertEquals(PartyDashboardData.capture(controller, context.getWorld(), player, board).bank().state(), PartyBank.State.SHORT,
                    "40 coins for the winner: not enough for a whole mini-game");
            context.assertEquals(PartyBank.need(controller.getGains(), PartyCurrency.COIN, 6), 40 + 5 + 3 + 1 + 2 + 2, "6 players: the 4 places and 2 participants");
        } finally {
            remove(context, player);
        }
        context.complete();
    }

    // ---------------------------------------------------------------- its own bank (27 slots), then the linked chests

    /** The gains are taken from its own bank first, then from the linked chests in their order. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bank_own_take")
    public void takenFromItsOwnBankFirst(TestContext context) {
        ServerPlayerEntity p1 = player(context, "a", 0.5);
        try {
            PartyControllerEntity controller = controller(context);
            ChestBlockEntity chest = TestBank.stock(context, controller, CHEST, 30, 0);
            controller.getBankItems().setStack(4, controller.getCurrency(PartyCurrency.COIN).copyWithCount(6));
            context.assertEquals(status(context, controller, 1).coins(), 36, "its own 6 and the chest's 30");
            MiniGamePartyStep step = playing(context, controller, p1);
            step.finish(controller, List.of(p1.getUuid()));
            context.assertEquals(coins(controller, p1), 10, "the winner's 10 coins");
            context.assertTrue(controller.getBankItems().isEmpty(), "its own 6 first");
            context.assertEquals(InventoryUtils.count(chest, controller.getCurrency(PartyCurrency.COIN)), 26, "then 4 from the chest");
        } finally {
            remove(context, p1);
        }
        context.complete();
    }

    /** What the party takes goes in the first place with room: its own bank, then the linked chests in order. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bank_own_deposit")
    public void depositedInOrder(TestContext context) {
        try {
            PartyControllerEntity controller = controller(context);
            ChestBlockEntity chest = TestBank.stock(context, controller, CHEST, 0, 0);
            ItemStack coin = controller.getCurrency(PartyCurrency.COIN);
            for (int i = 1; i < PartyControllerEntity.BANK_SIZE; i++) controller.getBankItems().setStack(i, new ItemStack(Items.DIRT, 64));
            controller.getBankItems().setStack(0, coin.copyWithCount(60));
            ItemStack paid = coin.copyWithCount(10);
            context.assertEquals(PartyBank.deposit(controller, paid), 10, "all of it went in");
            context.assertEquals(controller.getBankItems().getStack(0).getCount(), 64, "its own bank filled first");
            context.assertEquals(InventoryUtils.count(chest, coin), 6, "the rest in the chest");
        } finally {
            remove(context);
        }
        context.complete();
    }

    /** A hopper fills its own bank; broken, the controller drops what it held. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bank_own_hopper", tickLimit = 120)
    public void hopperFillsItAndBreakingDropsIt(TestContext context) {
        PartyControllerEntity controller = controller(context);
        ItemStack coin = controller.getCurrency(PartyCurrency.COIN);
        context.setBlockState(CONTROLLER.up(), Blocks.HOPPER);
        context.<net.minecraft.block.entity.HopperBlockEntity>getBlockEntity(CONTROLLER.up()).setStack(0, coin.copyWithCount(3));
        context.waitAndRun(40, () -> {
            context.assertEquals(InventoryUtils.count(controller.getBankItems(), coin), 3, "the hopper filled its bank");
            context.setBlockState(CONTROLLER.up(), Blocks.AIR);
            controller.getBankItems().setStack(10, new ItemStack(Items.DIAMOND, 5));
            context.setBlockState(CONTROLLER, Blocks.AIR);
            int coins = 0, diamonds = 0;
            for (ItemEntity item : context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(context.getAbsolutePos(CONTROLLER)).expand(2), e -> true)) {
                if (ItemStack.areItemsAndComponentsEqual(item.getStack(), coin)) coins += item.getStack().getCount();
                if (item.getStack().isOf(Items.DIAMOND)) diamonds += item.getStack().getCount();
                item.discard();
            }
            context.assertTrue(coins == 3 && diamonds == 5, "broken, it dropped its bank (" + coins + " coins, " + diamonds + " diamonds)");
            remove(context);
            context.complete();
        });
    }

    /** A controller saved before it had its own bank: an empty one, its linked chest still pays; its bank is saved. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bank_own_old")
    public void anOldControllerKeepsItsChests(TestContext context) {
        ServerPlayerEntity p1 = player(context, "a", 0.5);
        try {
            PartyControllerEntity controller = controller(context);
            TestBank.stock(context, controller, CHEST, 20, 0);
            NbtCompound saved = controller.createNbtWithIdentifyingData(context.getWorld().getRegistryManager());
            context.assertTrue(!saved.contains("BankItems"), "nothing of its own: nothing saved");
            controller.getBankItems().setStack(0, new ItemStack(Items.DIAMOND));
            controller.read(saved, context.getWorld().getRegistryManager());
            context.assertTrue(controller.getBankItems().isEmpty(), "read from before: an empty bank");
            context.assertEquals(status(context, controller, 1).coins(), 20, "its chest still counts");
            MiniGamePartyStep step = playing(context, controller, p1);
            step.finish(controller, List.of(p1.getUuid()));
            context.assertEquals(coins(controller, p1), 10, "and pays");
            controller.getBankItems().setStack(2, new ItemStack(Items.EMERALD, 3));
            NbtCompound again = controller.createNbtWithIdentifyingData(context.getWorld().getRegistryManager());
            controller.getBankItems().clear();
            controller.read(again, context.getWorld().getRegistryManager());
            context.assertEquals(controller.getBankItems().getStack(2).getCount(), 3, "its own bank is saved");
            context.assertTrue(!controller.toInitialChunkDataNbt(context.getWorld().getRegistryManager()).contains("BankItems"), "never sent to clients");
        } finally {
            remove(context, p1);
        }
        context.complete();
    }
}
