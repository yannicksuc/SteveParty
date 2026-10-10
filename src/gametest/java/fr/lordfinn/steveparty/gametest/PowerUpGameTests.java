package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyLiveData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.dice.DiceOutcome;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.PowerUpItem;
import fr.lordfinn.steveparty.powerups.PowerUp;
import fr.lordfinn.steveparty.powerups.PowerUpService;
import fr.lordfinn.steveparty.powerups.PowerUpTurn;
import fr.lordfinn.steveparty.powerups.PowerUps;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.world.GameMode;

import java.util.List;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * The power-ups: used at the start of their player's turn (before the roll, one per turn, consumed), the Mushroom
 * (+3 to the roll) and Double Coins (gains doubled, not losses), their default price on a stall.
 */
public class PowerUpGameTests implements SteveGameTest {
    private static final String BATCH = "powerups";

    private static ServerPlayerEntity survivalPlayer(TestContext context) {
        ServerPlayerEntity player = player(context);
        player.changeGameMode(GameMode.SURVIVAL);
        return player;
    }

    /** {@code player} right-clicks the air with {@code count} of the power-up's item in their main hand. */
    private static boolean use(TestContext context, ServerPlayerEntity player, PowerUp powerUp, int count) {
        if (count > 0) player.setStackInHand(Hand.MAIN_HAND, new ItemStack(powerUp.item(), count));
        return player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND).getResult().isAccepted();
    }

    private static int held(ServerPlayerEntity player) {
        return player.getMainHandStack().getCount();
    }

    private static TokenTurnPartyStep turn(PartyControllerEntity controller) {
        return (TokenTurnPartyStep) controller.getPartyData().getCurrentStep();
    }

    private static int coins(ServerPlayerEntity player) {
        return InventoryUtils.count(player.getInventory(), new ItemStack(ModItems.COIN));
    }

    // ---------------------------------------------------------------- the items

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyPowerUpHasItsItem(TestContext context) {
        context.assertEquals(ModItems.POWER_UPS.size(), PowerUps.all().size(), "one item per power-up");
        for (PowerUp powerUp : PowerUps.all()) {
            context.assertTrue(powerUp.item() instanceof PowerUpItem item && item.powerUp() == powerUp, powerUp + " has its item");
            context.assertEquals(Registries.ITEM.getId(powerUp.item()), powerUp.itemId(), "its item id");
            context.assertEquals(powerUp.itemId().getPath(), "powerup_" + powerUp.identifier().getPath(), "powerup_<path>");
            context.assertTrue(PartyLiveData.isPowerUp(new ItemStack(powerUp.item())), "the HUD counts it as a power-up");
            context.assertTrue(powerUp.defaultPrice() > 0, "it has a price");
        }
        context.assertEquals(PowerUps.byId("mushroom"), PowerUps.MUSHROOM, "found by its id");
        context.complete();
    }

    /** On a stall, a power-up without a price sells at its default price, in coins; a price set by hand wins. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void unpricedPowerUpSellsAtItsDefaultPrice(TestContext context) {
        context.setBlockState(new BlockPos(1, 1, 1), ModBlocks.TRADING_STALL);
        TradingStallBlockEntity stall = context.getBlockEntity(new BlockPos(1, 1, 1));
        stall.setStack(18, new ItemStack(PowerUps.MUSHROOM.item()));
        stall.setStack(1, new ItemStack(Items.EMERALD, 2));
        stall.setStack(19, new ItemStack(PowerUps.DOUBLE_COINS.item()));
        stall.setStack(20, new ItemStack(Items.DIAMOND));
        List<TradeOffer> offers = stall.getTradeOffers();
        context.assertEquals(offers.size(), 2, "the two power-ups are sold, not the unpriced diamond");
        ItemStack price = ((TradingStallBlockEntity.ExactTradeOffer) offers.get(0)).getFirstPrice();
        context.assertTrue(price.isOf(ModItems.COIN) && price.getCount() == PowerUps.MUSHROOM.defaultPrice(), "the Mushroom: its default price in coins");
        ItemStack set = ((TradingStallBlockEntity.ExactTradeOffer) offers.get(1)).getFirstPrice();
        context.assertTrue(set.isOf(Items.EMERALD) && set.getCount() == 2, "a price set by hand is kept");
        context.complete();
    }

    // ---------------------------------------------------------------- when it can be used

    /** Outside a party, or during another player's turn: refused, nothing consumed. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void refusedOutsideItsTurn(TestContext context) {
        path(context, 3, -1, null);
        ServerPlayerEntity player = survivalPlayer(context);
        ServerPlayerEntity other = survivalPlayer(context);
        context.assertTrue(!use(context, other, PowerUps.MUSHROOM, 2), "no party: refused");
        PigEntity pig = token(context, PATH.get(0), player.getUuid());
        PartyControllerEntity controller = party(context, player.getUuid(), pig);
        context.waitAndRun(2, () -> {
            context.assertTrue(!use(context, other, PowerUps.MUSHROOM, 0), "another player's turn: refused");
            context.assertEquals(held(other), 2, "nothing consumed");
            context.assertTrue(!turn(controller).getPowerUps().hasUsed(), "the turn has no power-up");
            context.complete();
        });
    }

    /** During its turn, before the roll: used, consumed (one item), remembered by the turn, saved with it. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void usedAtTheStartOfItsTurnAndConsumed(TestContext context) {
        path(context, 3, -1, null);
        ServerPlayerEntity player = survivalPlayer(context);
        PigEntity pig = token(context, PATH.get(0), player.getUuid());
        PartyControllerEntity controller = party(context, player.getUuid(), pig);
        context.waitAndRun(2, () -> {
            context.assertTrue(use(context, player, PowerUps.DOUBLE_COINS, 3), "used during its turn");
            context.assertEquals(held(player), 2, "one is consumed");
            PowerUpTurn state = turn(controller).getPowerUps();
            context.assertEquals(state.used(), PowerUps.DOUBLE_COINS, "the turn remembers it");
            NbtCompound saved = turn(controller).toNbt();
            TokenTurnPartyStep loaded = new TokenTurnPartyStep(saved);
            context.assertEquals(loaded.getPowerUps().used(), PowerUps.DOUBLE_COINS, "saved with the turn");
            context.complete();
        });
    }

    /** One power-up per turn: a second one is refused and not consumed. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void onlyOnePowerUpPerTurn(TestContext context) {
        path(context, 3, -1, null);
        ServerPlayerEntity player = survivalPlayer(context);
        PigEntity pig = token(context, PATH.get(0), player.getUuid());
        PartyControllerEntity controller = party(context, player.getUuid(), pig);
        context.waitAndRun(2, () -> {
            context.assertTrue(use(context, player, PowerUps.MUSHROOM, 2), "the first one");
            context.assertTrue(!use(context, player, PowerUps.MUSHROOM, 0), "the same again: refused");
            context.assertEquals(held(player), 1, "only the first one is consumed");
            context.assertTrue(!use(context, player, PowerUps.DOUBLE_COINS, 1), "another one: refused");
            context.assertEquals(held(player), 1, "not consumed");
            context.assertEquals(turn(controller).getPowerUps().used(), PowerUps.MUSHROOM, "the first one stays");
            context.complete();
        });
    }

    /** Once the roll counts, it is too late. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void refusedAfterTheRoll(TestContext context) {
        path(context, 3, -1, null);
        ServerPlayerEntity player = survivalPlayer(context);
        PigEntity pig = token(context, PATH.get(0), player.getUuid());
        PartyControllerEntity controller = party(context, player.getUuid(), pig);
        context.waitAndRun(2, () -> {
            DiceEntity dice = thrown(context, player, die("dice_face_1"), PATH.get(0));
            hit(context, dice, player);
            context.assertTrue(turn(controller).hasRolled(), "rolled");
            context.assertTrue(!use(context, player, PowerUps.MUSHROOM, 1), "after the roll: refused");
            context.assertEquals(held(player), 1, "not consumed");
            context.complete();
        });
    }

    // ---------------------------------------------------------------- Mushroom

    /** +3 on top of the die, shown in the announcement; the token walks it all. Only the next roll gets it. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = BATCH)
    public void mushroomAddsThreeToTheRoll(TestContext context) {
        path(context, 8, -1, null);
        ServerPlayerEntity player = survivalPlayer(context);
        PigEntity pig = token(context, PATH.get(0), player.getUuid());
        PartyControllerEntity controller = party(context, player.getUuid(), pig);
        context.waitAndRun(2, () -> {
            context.assertTrue(use(context, player, PowerUps.MUSHROOM, 1), "the Mushroom is used");
            // A blank roll is rolled again: it keeps the bonus for the next one
            PowerUpService.Roll blank = PowerUpService.onRollFinished(player.getUuid(), DiceOutcome.NONE);
            context.assertEquals(blank.outcome(), DiceOutcome.NONE, "a blank roll gets nothing");
            DiceEntity dice = thrown(context, player, die("dice_face_4"), PATH.get(0));
            hit(context, dice, player);
            context.assertEquals(dice.getOutcome().steps(), 7, "4 + 3");
            context.assertEquals(turn(controller).getRoll(), 7, "the turn counts 7");
            PowerUpService.Roll again = PowerUpService.onRollFinished(player.getUuid(), DiceOutcome.ofSteps(2));
            context.assertTrue(again.outcome().steps() == 2 && again.note() == null, "the bonus was spent on that roll");
            when(context, () -> isOn(context, pig, PATH.get(7)), 200, "the token walks 7 spaces", context::complete);
        });
    }

    /** The bonus and its note, whatever the die rolled: a double die's total, a coin roll that then walks 3. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void mushroomStacksWithAnyDie(TestContext context) {
        path(context, 3, -1, null);
        ServerPlayerEntity player = survivalPlayer(context);
        PigEntity pig = token(context, PATH.get(0), player.getUuid());
        party(context, player.getUuid(), pig);
        context.waitAndRun(2, () -> {
            context.assertTrue(use(context, player, PowerUps.MUSHROOM, 1), "the Mushroom is used");
            PowerUpService.Roll doubled = PowerUpService.onRollFinished(player.getUuid(), DiceOutcome.ofSteps(13));
            context.assertTrue(doubled.outcome().steps() == 16 && doubled.note() != null, "13 on a double die: 16, told");
            context.assertTrue(doubled.note().getString().contains("13") && doubled.note().getString().contains("+3"),
                    "the note shows the die and the bonus: " + doubled.note().getString());
            context.complete();
        });
    }

    // ---------------------------------------------------------------- Double Coins

    /** A coin face gives twice its coins, the extra from the bank too; a bank short of them gives what it has. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void doubleCoinsDoublesTheGains(TestContext context) {
        path(context, 3, -1, null);
        ServerPlayerEntity player = survivalPlayer(context);
        PigEntity pig = token(context, PATH.get(1), player.getUuid());
        PartyControllerEntity controller = party(context, player.getUuid(), pig);
        controller.getBankItems().setStack(0, new ItemStack(ModItems.COIN, 12));
        context.waitAndRun(2, () -> {
            context.assertTrue(use(context, player, PowerUps.DOUBLE_COINS, 1), "Double Coins is used");
            context.assertEquals(PowerUpService.itemsGained(player, new ItemStack(ModItems.COIN), 3), 6, "an item space's 3 coins: 6");
            context.assertEquals(PowerUpService.itemsGained(player, new ItemStack(Items.DIAMOND), 3), 3, "other items: as they are");
            DiceEntity dice = thrown(context, player, die("coin_dice_face_5"), PATH.get(1));
            hit(context, dice, player);
            when(context, turnEnded(controller), 150, "the turn ends", () -> {
                context.assertEquals(coins(player), 10, "+5 doubled: 10 coins");
                context.assertEquals(InventoryUtils.count(controller.getBankItems(), new ItemStack(ModItems.COIN)), 2, "all 10 from the bank");
                context.complete();
            });
        });
    }

    /** A debt face takes what it says: losses are not doubled. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = BATCH)
    public void doubleCoinsDoesNotDoubleTheLosses(TestContext context) {
        path(context, 3, -1, null);
        ServerPlayerEntity player = survivalPlayer(context);
        PigEntity pig = token(context, PATH.get(1), player.getUuid());
        PartyControllerEntity controller = party(context, player.getUuid(), pig);
        player.getInventory().setStack(5, new ItemStack(ModItems.COIN, 10));
        context.waitAndRun(2, () -> {
            context.assertTrue(use(context, player, PowerUps.DOUBLE_COINS, 1), "Double Coins is used");
            context.assertEquals(PowerUpService.coinsGained(player.getUuid(), -4), -4, "a loss is left as it is");
            DiceEntity dice = thrown(context, player, die("debt_dice_face_3"), PATH.get(1));
            hit(context, dice, player);
            when(context, turnEnded(controller), 150, "the turn ends", () -> {
                context.assertEquals(coins(player), 7, "−3, not −6");
                context.assertEquals(InventoryUtils.count(controller.getBankItems(), new ItemStack(ModItems.COIN)), 3, "the 3 into the bank");
                context.complete();
            });
        });
    }

    /** Without a power-up, gains are as they are. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void noPowerUpNoChange(TestContext context) {
        path(context, 3, -1, null);
        ServerPlayerEntity player = survivalPlayer(context);
        PigEntity pig = token(context, PATH.get(0), player.getUuid());
        party(context, player.getUuid(), pig);
        context.waitAndRun(2, () -> {
            context.assertEquals(PowerUpService.coinsGained(player.getUuid(), 5), 5, "coins as they are");
            PowerUpService.Roll roll = PowerUpService.onRollFinished(player.getUuid(), DiceOutcome.ofSteps(4));
            context.assertTrue(roll.outcome().steps() == 4 && roll.note() == null, "the roll as it is");
            context.complete();
        });
    }
}
