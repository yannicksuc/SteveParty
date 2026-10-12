package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.CartridgeTransfers;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlock;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EventPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PiggyBankBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PiggyBankBlockEntity.Target;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestBank;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.screen_handlers.custom.PiggyBankScreenHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Piggy Bank on the party flow: who each target pays (player of the turn, at a turn end or start too; last
 * winners; everyone; nearest), from its linked chest or, none linked, the party's bank; its menu sets its target.
 * Each test in a batch of its own: the bank pays the closest running party within 64 blocks.
 */
public class PiggyBankGameTests implements SteveGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(1, 1, 1), BANK = new BlockPos(4, 1, 4), CHEST = new BlockPos(4, 1, 7);

    private static PartyControllerEntity placeController(TestContext context) {
        context.setBlockState(CONTROLLER.down(), Blocks.STONE);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        return context.getBlockEntity(CONTROLLER);
    }

    /** A Piggy Bank whose cartridge gives one gold nugget from a chest holding {@code nuggets}. */
    private static PiggyBankBlockEntity placeBank(TestContext context, Target target, int nuggets) {
        context.setBlockState(CHEST, Blocks.CHEST);
        ChestBlockEntity chest = context.getBlockEntity(CHEST);
        if (nuggets > 0) chest.setStack(0, new ItemStack(Items.GOLD_NUGGET, nuggets));
        context.setBlockState(BANK, ModBlocks.PIGGY_BANK);
        PiggyBankBlockEntity bank = context.getBlockEntity(BANK);
        ItemStack cartridge = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
        cartridge.set(ModComponents.INVENTORY_COMPONENT, new InventoryComponent(List.of(new ItemStack(Items.GOLD_NUGGET, 1))));
        cartridge.set(ModComponents.INVENTORY_POS, context.getAbsolutePos(CHEST));
        cartridge.set(ModComponents.SELECTION_STATE, InventoryCartridgeItem.ALL);
        bank.setStack(0, cartridge);
        bank.setTarget(target);
        return bank;
    }

    private static ServerPlayerEntity player(TestContext context, String name, double x, double z) {
        ServerPlayerEntity player = TestPlayers.joined(context, "pig", name, GameMode.SURVIVAL, x, 1, z);
        TestPlayers.removeAtEnd(context, player);
        return player;
    }

    private static int nuggets(ServerPlayerEntity player) {
        return CartridgeTransfers.countMatching(new ItemStack(Items.GOLD_NUGGET), player.getInventory());
    }

    /** Two turns of unloaded tokens owned by {@code a} and {@code b} (nothing moves on by itself). */
    private static PartyData twoTurns(UUID tokenA, UUID ownerA, UUID tokenB, UUID ownerB) {
        PartyData data = new PartyData();
        data.addToken(tokenA);
        data.addToken(tokenB);
        data.addStep(new PartyStep());
        data.addStep(new TokenTurnPartyStep(tokenA, ownerA));
        data.addStep(new TokenTurnPartyStep(tokenB, ownerB));
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(tokenA, tokenB))));
        return data;
    }

    private static void end(TestContext context) {
        context.removeBlock(CONTROLLER);
        context.complete();
    }

    /** During a turn, its player is paid; the next turn, the next one. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "piggy_turn")
    public void turnPlayerIsTheOwnerOfTheTurnPlaying(TestContext context) {
        TestPlayers.alone(context, new Vec3d(4, 1, 4), 16);
        ServerPlayerEntity a = player(context, "A", 2.5, 4.5), b = player(context, "B", 6.5, 4.5);
        PartyControllerEntity controller = placeController(context);
        PiggyBankBlockEntity bank = placeBank(context, Target.TURN_PLAYER, 10);
        controller.setPartyData(twoTurns(UUID.randomUUID(), a.getUuid(), UUID.randomUUID(), b.getUuid()));
        controller.nextStep();
        controller.nextStep();
        context.assertTrue(bank.activate(), "paid during the turn of A");
        context.assertEquals(nuggets(a), 1, "A paid");
        context.assertEquals(nuggets(b), 0, "B not paid");
        controller.nextStep();
        context.assertTrue(bank.activate(), "paid during the turn of B");
        context.assertEquals(nuggets(b), 1, "B paid");
        context.assertEquals(nuggets(a), 1, "A paid once");
        ChestBlockEntity chest = context.getBlockEntity(CHEST);
        context.assertEquals(chest.getStack(0).getCount(), 8, "taken from the linked chest");
        end(context);
    }

    /** A turn end bell next to the bank: the turn that just ended is paid, not the one starting at the same tick. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "piggy_turn_end")
    public void turnEndBellPaysThePlayerWhoJustPlayed(TestContext context) {
        TestPlayers.alone(context, new Vec3d(4, 1, 4), 16);
        ServerPlayerEntity a = player(context, "A", 2.5, 4.5), b = player(context, "B", 6.5, 4.5);
        PartyControllerEntity controller = placeController(context);
        placeBank(context, Target.TURN_PLAYER, 10);
        context.setBlockState(BANK.east().down(), Blocks.STONE);
        context.setBlockState(BANK.east(), ModBlocks.PARTY_BELL.getDefaultState().with(PartyBellBlock.MOMENT, PartyMoment.TURN_END));
        controller.setPartyData(twoTurns(UUID.randomUUID(), a.getUuid(), UUID.randomUUID(), b.getUuid()));
        controller.nextStep();
        controller.nextStep();
        context.assertEquals(nuggets(a) + nuggets(b), 0, "nothing before the turn end");
        controller.nextStep(); // turn of A over, turn of B starts at the same tick
        context.assertEquals(nuggets(a), 1, "A, whose turn ended, paid");
        context.assertEquals(nuggets(b), 0, "B, starting, not paid");
        end(context);
    }

    /** A turn start bell next to the bank: the turn starting is paid. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "piggy_turn_start")
    public void turnStartBellPaysThePlayerStarting(TestContext context) {
        TestPlayers.alone(context, new Vec3d(4, 1, 4), 16);
        ServerPlayerEntity a = player(context, "A", 2.5, 4.5), b = player(context, "B", 6.5, 4.5);
        PartyControllerEntity controller = placeController(context);
        placeBank(context, Target.TURN_PLAYER, 10);
        context.setBlockState(BANK.east().down(), Blocks.STONE);
        context.setBlockState(BANK.east(), ModBlocks.PARTY_BELL.getDefaultState().with(PartyBellBlock.MOMENT, PartyMoment.TURN_START));
        controller.setPartyData(twoTurns(UUID.randomUUID(), a.getUuid(), UUID.randomUUID(), b.getUuid()));
        controller.nextStep();
        controller.nextStep(); // turn of A starts
        context.assertEquals(nuggets(a), 1, "A paid at its turn start");
        context.waitAndRun(PartyBellBlock.PULSE_TICKS + 2, () -> {
            controller.nextStep(); // turn of B starts
            context.assertEquals(nuggets(b), 1, "B paid at its turn start");
            context.assertEquals(nuggets(a), 1, "A paid once");
            end(context);
        });
    }

    /** A waiting turn end bell: while the party waits on it, the turn that ended is the one paid. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "piggy_turn_wait")
    public void waitingTurnEndPaysThePlayerWhoJustPlayed(TestContext context) {
        TestPlayers.alone(context, new Vec3d(4, 1, 4), 16);
        ServerPlayerEntity a = player(context, "A", 2.5, 4.5), b = player(context, "B", 6.5, 4.5);
        PartyControllerEntity controller = placeController(context);
        PiggyBankBlockEntity bank = placeBank(context, Target.TURN_PLAYER, 10);
        BlockPos bell = new BlockPos(1, 1, 7);
        context.setBlockState(bell.down(), Blocks.STONE);
        context.setBlockState(bell, ModBlocks.PARTY_BELL.getDefaultState().with(PartyBellBlock.MOMENT, PartyMoment.TURN_END)
                .with(PartyBellBlock.WAITING, true));
        PartyData data = twoTurns(UUID.randomUUID(), a.getUuid(), UUID.randomUUID(), b.getUuid());
        controller.setPartyData(data);
        controller.nextStep();
        controller.nextStep();
        controller.nextStep();
        context.assertTrue(data.getCurrentStep() instanceof EventPartyStep event && event.isWaitingForBell(), "waiting on the bell");
        context.waitAndRun(PartyBellBlock.PULSE_TICKS + 2, () -> {
            context.assertTrue(bank.activate(), "paid while waiting");
            context.assertEquals(nuggets(a), 1, "A, whose turn ended, paid");
            context.assertEquals(nuggets(b), 0, "B not paid");
            end(context);
        });
    }

    /** The winners of the last mini-game only. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "piggy_winners")
    public void winnersArePaid(TestContext context) {
        TestPlayers.alone(context, new Vec3d(4, 1, 4), 16);
        ServerPlayerEntity a = player(context, "A", 2.5, 4.5), b = player(context, "B", 6.5, 4.5);
        PartyControllerEntity controller = placeController(context);
        PiggyBankBlockEntity bank = placeBank(context, Target.WINNERS, 10);
        controller.setPartyData(twoTurns(UUID.randomUUID(), a.getUuid(), UUID.randomUUID(), b.getUuid()));
        controller.nextStep();
        controller.nextStep();
        context.assertTrue(!bank.activate(), "no mini-game yet: nobody");
        controller.setLastWinners(List.of(b.getUuid()));
        context.assertTrue(bank.activate(), "the winner paid");
        context.assertEquals(nuggets(b), 1, "B, the winner, paid");
        context.assertEquals(nuggets(a), 0, "A not paid");
        context.assertEquals(comparator(context), 15, "comparator on: the last pulse paid");
        end(context);
    }

    /** Every player of the party, and none of a player around who does not play. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "piggy_everyone")
    public void everyoneOfThePartyIsPaid(TestContext context) {
        TestPlayers.alone(context, new Vec3d(4, 1, 4), 16);
        ServerPlayerEntity a = player(context, "A", 2.5, 4.5), b = player(context, "B", 6.5, 4.5),
                watcher = player(context, "W", 4.5, 3.5);
        PartyControllerEntity controller = placeController(context);
        PiggyBankBlockEntity bank = placeBank(context, Target.EVERYONE, 10);
        controller.setPartyData(twoTurns(UUID.randomUUID(), a.getUuid(), UUID.randomUUID(), b.getUuid()));
        controller.nextStep();
        context.assertTrue(bank.activate(), "paid");
        context.assertEquals(nuggets(a), 1, "A paid");
        context.assertEquals(nuggets(b), 1, "B paid");
        context.assertEquals(nuggets(watcher), 0, "the watcher not paid");
        end(context);
    }

    /** The nearest player, a spectator nearer not counted; out of a party, the player of the turn is the nearest. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "piggy_nearest")
    public void nearestPlayerSkipsSpectators(TestContext context) {
        TestPlayers.alone(context, new Vec3d(4, 1, 4), 16);
        ServerPlayerEntity spectator = TestPlayers.joined(context, "pig", "S", GameMode.SPECTATOR, 4.5, 1, 3.5);
        TestPlayers.removeAtEnd(context, spectator);
        ServerPlayerEntity near = player(context, "N", 6.5, 4.5), far = player(context, "F", 9.5, 4.5);
        PiggyBankBlockEntity bank = placeBank(context, Target.NEAREST, 10);
        context.assertTrue(bank.activate(), "paid");
        context.assertEquals(nuggets(near), 1, "the nearest player paid");
        context.assertEquals(nuggets(far), 0, "the farther one not");
        context.assertEquals(nuggets(spectator), 0, "the spectator not");
        bank.setTarget(Target.TURN_PLAYER);
        context.assertTrue(bank.activate(), "free play: the player of the turn is the nearest");
        context.assertEquals(nuggets(near), 2, "the nearest paid again");
        context.complete();
    }

    /** No chest linked: the party's bank pays, as for a board space; outside a party nothing is made from nothing. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "piggy_party_bank")
    public void unlinkedCartridgeUsesThePartyBank(TestContext context) {
        TestPlayers.alone(context, new Vec3d(4, 1, 4), 16);
        ServerPlayerEntity a = player(context, "A", 2.5, 4.5), b = player(context, "B", 6.5, 4.5);
        context.setBlockState(BANK, ModBlocks.PIGGY_BANK);
        PiggyBankBlockEntity bank = context.getBlockEntity(BANK);
        bank.setTarget(Target.EVERYONE);
        PartyControllerEntity controller = placeController(context);
        controller.setInfiniteBank(false);
        ChestBlockEntity bankChest = TestBank.stock(context, controller, new BlockPos(1, 1, 7), 10, 0);
        ItemStack coin = controller.getCurrency(PartyCurrency.COIN);
        ItemStack cartridge = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
        cartridge.set(ModComponents.INVENTORY_COMPONENT, new InventoryComponent(List.of(coin.copyWithCount(3))));
        cartridge.set(ModComponents.SELECTION_STATE, InventoryCartridgeItem.ALL);
        bank.setStack(0, cartridge);
        context.assertTrue(!bank.activate(), "no party running: nothing");
        controller.setPartyData(twoTurns(UUID.randomUUID(), a.getUuid(), UUID.randomUUID(), b.getUuid()));
        controller.nextStep();
        context.assertTrue(bank.activate(), "paid from the party's bank");
        context.assertEquals(CartridgeTransfers.countMatching(coin, a.getInventory()), 3, "A got 3 coins");
        context.assertEquals(CartridgeTransfers.countMatching(coin, b.getInventory()), 3, "B got 3 coins");
        context.assertEquals(CartridgeTransfers.countMatching(coin, bankChest), 4, "6 coins out of the bank");
        end(context);
    }

    /** Its menu's buttons set its target. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void menuButtonsSetTheTarget(TestContext context) {
        context.setBlockState(BANK, ModBlocks.PIGGY_BANK);
        PiggyBankBlockEntity bank = context.getBlockEntity(BANK);
        ServerPlayerEntity player = TestPlayers.mock(context);
        PiggyBankScreenHandler menu = (PiggyBankScreenHandler) bank.createMenu(0, player.getInventory(), player);
        context.assertTrue(menu != null && menu.onButtonClick(player, Target.WINNERS.ordinal()), "button accepted");
        context.assertEquals(bank.getTarget(), Target.WINNERS, "winners chosen");
        context.assertEquals(menu.getTarget(), Target.WINNERS, "the menu shows it");
        context.assertTrue(!menu.onButtonClick(player, 9), "no such button");
        context.complete();
    }

    private static int comparator(TestContext context) {
        BlockState state = context.getBlockState(BANK);
        return state.getComparatorOutput(context.getWorld(), context.getAbsolutePos(BANK));
    }
}
