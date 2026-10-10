package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyResources;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.BoardRuleCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.PotCartridgeItem;
import fr.lordfinn.steveparty.powerups.PowerUps;
import fr.lordfinn.steveparty.service.CommonPots;
import fr.lordfinn.steveparty.service.DiceRollEffects;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

/**
 * « Nothing is made from nothing »: every coin a player gets in a party comes from the party's bank (or another
 * player, a pot), every coin taken goes somewhere (the bank, a pot). The coin and debt faces, Double Coins, a Common
 * pot's base, and the sum of everything across a sequence of them.
 */
public class EconomyGameTests implements SteveGameTest {
    private static final BlockPos POT = new BlockPos(5, 1, 3);

    private record Party(PartyControllerEntity controller, ServerPlayerEntity a, ServerPlayerEntity b, MobEntity tokenA,
                         MobEntity tokenB, ItemStack coin) {
        int bank() {
            return InventoryUtils.count(controller.getBankItems(), coin);
        }

        int coins(ServerPlayerEntity player) {
            return InventoryUtils.count(player.getInventory(), coin);
        }
    }

    /** A party of two players (a token each, the first one's turn), its bank holding {@code bank} coins. */
    private static Party party(TestContext context, int bank) {
        ServerPlayerEntity a = DiceTestKit.player(context), b = DiceTestKit.player(context);
        a.getInventory().clear();
        b.getInventory().clear();
        MobEntity tokenA = DiceTestKit.token(context, new BlockPos(1, 1, 1), a.getUuid());
        MobEntity tokenB = DiceTestKit.token(context, new BlockPos(3, 1, 1), b.getUuid());
        PartyControllerEntity controller = DiceTestKit.party(context, a.getUuid(), tokenA, tokenB);
        ItemStack coin = controller.getCurrency(PartyCurrency.COIN);
        controller.getBankItems().clear();
        if (bank > 0) controller.getBankItems().setStack(0, coin.copyWithCount(bank));
        return new Party(controller, a, b, tokenA, tokenB, coin);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "economy_coin_face")
    public void aCoinFacePaysFromTheBankAndStopsWhenItIsEmpty(TestContext context) {
        Party party = party(context, 3);
        context.assertEquals(DiceRollEffects.applyCoins(context.getWorld(), party.tokenA(), party.a().getUuid(), 5), 3,
                "+5 with 3 in the bank: 3");
        context.assertEquals(party.coins(party.a()), 3, "3 received");
        context.assertEquals(party.bank(), 0, "the bank is empty");
        context.assertEquals(DiceRollEffects.applyCoins(context.getWorld(), party.tokenA(), party.a().getUuid(), 4), 0,
                "an empty bank: nothing");
        context.assertEquals(party.coins(party.a()), 3, "nothing made");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "economy_debt_face")
    public void aDebtFaceGoesIntoTheBank(TestContext context) {
        Party party = party(context, 0);
        InventoryUtils.giveOrDrop(party.a(), party.coin(), 4);
        context.assertEquals(DiceRollEffects.applyCoins(context.getWorld(), party.tokenA(), party.a().getUuid(), -3), -3, "-3");
        context.assertEquals(party.coins(party.a()), 1, "3 taken");
        context.assertEquals(party.bank(), 3, "into the bank, not destroyed");
        context.assertEquals(DiceRollEffects.applyCoins(context.getWorld(), party.tokenA(), party.a().getUuid(), -5), -1,
                "never more than held");
        context.assertEquals(party.bank(), 4, "that one too");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "economy_double_coins")
    public void doubleCoinsTakesTheExtraFromTheBank(TestContext context) {
        Party party = party(context, 7);
        party.a().changeGameMode(GameMode.SURVIVAL);
        party.a().setStackInHand(Hand.MAIN_HAND, new ItemStack(PowerUps.DOUBLE_COINS.item()));
        context.assertTrue(party.a().getMainHandStack().use(context.getWorld(), party.a(), Hand.MAIN_HAND).getResult().isAccepted(),
                "Double Coins is used");
        context.assertEquals(DiceRollEffects.applyCoins(context.getWorld(), party.tokenA(), party.a().getUuid(), 5), 7,
                "+5 doubled is 10, the bank has 7: 7");
        context.assertEquals(party.coins(party.a()), 7, "7 received");
        context.assertEquals(party.bank(), 0, "all from the bank");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "economy_pot_base")
    public void thePotsBaseComesFromTheBank(TestContext context) {
        Party party = party(context, 7);
        ItemStack cartridge = new ItemStack(ModItems.POT_CARTRIDGE);
        BoardRuleCartridgeItem.putSetting(cartridge, PotCartridgeItem.START, 5);
        BoardSpaceBlockEntity space = DiceTestKit.tile(context, POT, cartridge);
        ItemStack pot = space.getActiveCartridgeItemStack();
        context.assertEquals(PotCartridgeItem.coins(pot), 0, "a new pot holds nothing of its own");
        context.assertTrue(PotCartridgeItem.baseDue(pot), "its base is due");
        context.assertEquals(CommonPots.fillBase(pot, party.tokenA()), 5, "its base, from the bank");
        context.assertTrue(PotCartridgeItem.coins(pot) == 5 && party.bank() == 2, "5 in the pot, 2 left in the bank");
        context.assertEquals(CommonPots.fillBase(pot, party.tokenA()), 0, "only once");
        // Won: it starts again, from what the bank has left
        CommonPots.win(context.getWorld(), space, party.tokenA());
        context.assertEquals(party.coins(party.a()), 5, "the winner takes the 5");
        context.assertTrue(PotCartridgeItem.coins(pot) == 2 && party.bank() == 0, "the bank had 2: the pot starts again at 2");
        context.complete();
    }

    /** Coin and debt faces, a pot's base, stakes, a win, the gains, Double Coins: the coins only move. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "economy_conservation")
    public void theCoinsOnlyMove(TestContext context) {
        Party party = party(context, 20);
        InventoryUtils.giveOrDrop(party.a(), party.coin(), 5);
        InventoryUtils.giveOrDrop(party.b(), party.coin(), 5);
        ItemStack cartridge = new ItemStack(ModItems.POT_CARTRIDGE);
        BoardRuleCartridgeItem.putSetting(cartridge, PotCartridgeItem.START, 3);
        BoardRuleCartridgeItem.putSetting(cartridge, PotCartridgeItem.STAKE, 2);
        BoardSpaceBlockEntity space = DiceTestKit.tile(context, POT, cartridge);
        ItemStack pot = space.getActiveCartridgeItemStack();
        int total = 20 + 5 + 5;
        Runnable conserved = () -> context.assertEquals(party.bank() + party.coins(party.a()) + party.coins(party.b())
                + PotCartridgeItem.coins(pot), total, "bank + players + pot: always " + total);
        DiceRollEffects.applyCoins(context.getWorld(), party.tokenA(), party.a().getUuid(), 4);
        conserved.run();
        DiceRollEffects.applyCoins(context.getWorld(), party.tokenB(), party.b().getUuid(), -3);
        conserved.run();
        CommonPots.fillBase(pot, party.tokenA());
        conserved.run();
        CommonPots.pass(context.getWorld(), space, party.tokenB());
        conserved.run();
        CommonPots.win(context.getWorld(), space, party.tokenA());
        conserved.run();
        party.controller().payGains(party.a(), 1, PartyResources.of(party.controller()));
        conserved.run();
        ItemStack paid = party.coin().copyWithCount(InventoryUtils.take(party.a().getInventory(), party.coin(), 2));
        PartyResources.deposit(party.controller(), paid);
        conserved.run();
        DiceRollEffects.applyCoins(context.getWorld(), party.tokenA(), party.a().getUuid(), 100);
        conserved.run();
        context.assertEquals(party.bank(), 0, "the bank emptied, no more");
        context.complete();
    }
}
