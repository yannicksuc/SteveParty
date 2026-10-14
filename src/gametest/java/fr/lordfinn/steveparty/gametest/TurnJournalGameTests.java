package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.custom.CartridgeTransfers;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyResources;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyRewind;
import fr.lordfinn.steveparty.blocks.custom.PartyController.StepPulse;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.service.ShopStops;
import fr.lordfinn.steveparty.gametest.kit.TestWait;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.GlobalPos;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.gametest.kit.TestCleanup;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.service.DiceRollEffects;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.List;

/**
 * The turn journal: a Step Controller's « replay » undoes the turn being played (the pawn back, what the game gave and
 * took back where it came from) and plays it again; « previous » goes back a turn. Never duplicates anything: what can't
 * be taken back is not given back, a player who does not give back all he gained gets nothing back, and the chat says
 * what could not be undone.
 */
public class TurnJournalGameTests implements SteveGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(1, 1, 1), PAWN_A = new BlockPos(3, 1, 3), PAWN_B = new BlockPos(5, 1, 3),
            CHEST = new BlockPos(1, 1, 5), OTHER_CHEST = new BlockPos(2, 1, 5), SHOP = new BlockPos(4, 0, 6);

    /** Two players, their pawns, a party of two rounds at a's first turn, coins in its bank. */
    private record Scene(PartyControllerEntity party, ServerPlayerEntity a, ServerPlayerEntity b, PigEntity pawnA, PigEntity pawnB,
                         ItemStack coin) {
        int bankCoins() {
            return InventoryUtils.count(party.getBankItems(), coin);
        }

        int coinsOf(ServerPlayerEntity player) {
            return InventoryUtils.count(player.getInventory(), coin);
        }
    }

    private static Scene scene(TestContext context, int bankCoins) {
        TestBoards.floor(context, 8);
        ServerPlayerEntity a = TestPlayers.joined(context, "tj", "A", GameMode.SURVIVAL, 6.5, 1, 6.5);
        ServerPlayerEntity b = TestPlayers.joined(context, "tj", "B", GameMode.SURVIVAL, 6.5, 1, 7.5);
        TestCleanup.atEnd(context, () -> TestPlayers.removeIfOnline(context, a, b));
        PigEntity pawnA = TestBoards.token(context, PAWN_A, a.getUuid());
        PigEntity pawnB = TestBoards.token(context, PAWN_B, b.getUuid());
        pawnA.setAiDisabled(true);
        pawnB.setAiDisabled(true);
        PartyControllerEntity party = TestBoards.party(context, CONTROLLER, 2, pawnA.getUuid(), pawnB.getUuid());
        TestCleanup.atEnd(context, () -> {
            party.getBankItems().clear();
            TestCleanup.removeIf(context, CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        });
        ItemStack coin = party.getCurrency(PartyCurrency.COIN);
        party.getBankItems().setStack(0, coin.copyWithCount(bankCoins));
        context.assertTrue(party.getPartyData().getCurrentStep() instanceof TokenTurnPartyStep turn && pawnA.getUuid().equals(turn.getTokenUUID()),
                "a's turn");
        return new Scene(party, a, b, pawnA, pawnB, coin);
    }

    private static Inventory chest(TestContext context, BlockPos pos) {
        context.setBlockState(pos, Blocks.CHEST);
        return context.getBlockEntity(pos);
    }

    private static int count(Inventory inventory, ItemStack kind) {
        return InventoryUtils.count(inventory, kind);
    }

    private static int onGround(TestContext context, ItemStack kind) {
        int count = 0;
        Box box = new Box(context.getAbsolutePos(BlockPos.ORIGIN)).expand(12);
        for (ItemEntity item : context.getWorld().getEntitiesByClass(ItemEntity.class, box, e -> ItemStack.areItemsAndComponentsEqual(e.getStack(), kind)))
            count += item.getStack().getCount();
        return count;
    }

    private static boolean says(List<Text> lines, String key) {
        for (Text line : lines) if (line.getContent() instanceof TranslatableTextContent content && content.getKey().equals(key)) return true;
        return false;
    }

    private static void moveTo(TestContext context, PigEntity pawn, BlockPos relative) {
        Vec3d at = context.getAbsolute(Vec3d.ofBottomCenter(relative));
        pawn.refreshPositionAndAngles(at.x, at.y, at.z, 90, 0);
    }

    private static boolean standsAt(TestContext context, PigEntity pawn, BlockPos relative) {
        return pawn.getPos().squaredDistanceTo(context.getAbsolute(Vec3d.ofBottomCenter(relative))) < 0.01;
    }

    /**
     * The pawn walked and its player won coins from the bank (a Coin face): « replay » puts the pawn back and the coins
     * back in the bank, and the same turn starts again. Pulsed twice, nothing is given twice.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "turn_journal_replay")
    public void replayUndoesTheMoveAndTheCoins(TestContext context) {
        Scene scene = scene(context, 10);
        int index = scene.party().getPartyData().getStepIndex();
        moveTo(context, scene.pawnA(), new BlockPos(6, 1, 3));
        DiceRollEffects.applyCoins(context.getWorld(), scene.pawnA(), scene.a().getUuid(), 3);
        context.assertEquals(scene.coinsOf(scene.a()), 3, "a won 3 coins");
        context.assertEquals(scene.bankCoins(), 7, "out of the bank");

        context.assertTrue(StepPulse.apply(scene.party(), StepPulse.Mode.REPLAY), "replayed");
        context.assertTrue(standsAt(context, scene.pawnA(), PAWN_A), "the pawn is back where the turn started");
        context.assertEquals(scene.coinsOf(scene.a()), 0, "the coins were taken back");
        context.assertEquals(scene.bankCoins(), 10, "into the bank");
        context.assertTrue(scene.party().getPartyData().getStepIndex() == index
                && scene.party().getPartyData().getCurrentStep() instanceof TokenTurnPartyStep turn
                && scene.pawnA().getUuid().equals(turn.getTokenUUID()) && turn.getStatus() == PartyStep.Status.IN_PROGRESS, "a's turn again");

        context.assertTrue(StepPulse.apply(scene.party(), StepPulse.Mode.REPLAY), "replayed again");
        context.assertTrue(scene.coinsOf(scene.a()) == 0 && scene.bankCoins() == 10, "a second pulse gives nothing twice");
        context.complete();
    }

    /**
     * An Inventory space gave items from its chest and took others into it: « replay » takes them back from the player
     * and puts them back in the chest, and gives the player what was taken.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "turn_journal_cartridge")
    public void inventoryCartridgeTransfersAreUndone(TestContext context) {
        Scene scene = scene(context, 0);
        Inventory chest = chest(context, CHEST);
        ItemStack diamond = new ItemStack(Items.DIAMOND), emerald = new ItemStack(Items.EMERALD);
        chest.setStack(0, diamond.copyWithCount(5));
        scene.a().getInventory().insertStack(emerald.copyWithCount(4));
        PartyResources linked = PartyResources.of(List.of(chest));
        context.assertEquals(CartridgeTransfers.transfer(diamond.copyWithCount(2), linked, scene.a()), 2, "2 diamonds given");
        ItemStack taking = emerald.copyWithCount(3);
        taking.set(ModComponents.IS_NEGATIVE, true);
        context.assertEquals(CartridgeTransfers.transfer(taking, linked, scene.a()), 3, "3 emeralds taken");

        List<Text> lines = PartyRewind.replay(scene.party());
        context.assertTrue(lines != null && lines.isEmpty(), "replayed, everything undone");
        context.assertTrue(count(chest, diamond) == 5 && count(chest, emerald) == 0, "the chest as before");
        context.assertTrue(count(scene.a().getInventory(), diamond) == 0 && count(scene.a().getInventory(), emerald) == 4, "the player as before");
        context.complete();
    }

    /**
     * A gain thrown on the ground or put away in a chest can't be taken back: nothing goes back to where it came from
     * for it (no item made), the chat says so; the count of items stays the same.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "turn_journal_thrown")
    public void aGainThrownAwayIsNotGivenBackTwice(TestContext context) {
        Scene scene = scene(context, 0);
        Inventory chest = chest(context, CHEST), other = chest(context, OTHER_CHEST);
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        chest.setStack(0, diamond.copyWithCount(5));
        CartridgeTransfers.transfer(diamond.copyWithCount(3), PartyResources.of(List.of(chest)), scene.a());
        // The player throws two and puts one in another chest
        InventoryUtils.take(scene.a().getInventory(), diamond, 3);
        scene.a().dropItem(diamond.copyWithCount(2), false);
        other.setStack(0, diamond.copyWithCount(1));

        List<Text> lines = PartyRewind.replay(scene.party());
        context.assertTrue(lines != null, "replayed");
        context.assertEquals(count(chest, diamond), 2, "nothing given back for what was not taken back");
        context.assertTrue(says(lines, "journal.steveparty.not_undone.not_there"), "the chat says so");
        context.assertEquals(count(chest, diamond) + count(other, diamond) + onGround(context, diamond) + count(scene.a().getInventory(), diamond), 5,
                "no diamond made");
        context.complete();
    }

    /** The chest a gain came from is full now: the player keeps it, the chat says the chest is full. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "turn_journal_full")
    public void aFullSourceKeepsItAndSaysSo(TestContext context) {
        Scene scene = scene(context, 0);
        Inventory chest = chest(context, CHEST);
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        chest.setStack(0, diamond.copyWithCount(3));
        CartridgeTransfers.transfer(diamond.copyWithCount(3), PartyResources.of(List.of(chest)), scene.a());
        for (int slot = 0; slot < chest.size(); slot++) chest.setStack(slot, new ItemStack(Items.DIRT, 64));

        List<Text> lines = PartyRewind.replay(scene.party());
        context.assertTrue(lines != null && says(lines, "journal.steveparty.not_undone.full"), "replayed, the chest full is said");
        context.assertEquals(count(scene.a().getInventory(), diamond), 3, "the player keeps what can't go back");
        context.assertEquals(count(chest, diamond), 0, "nothing forced in");
        context.complete();
    }

    /**
     * A star bought (coins for a star) and the star given to another player: the buyer gets nothing back, the price stays
     * in the bank, the other keeps the star.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "turn_journal_given_away")
    public void aPurchaseGivenAwayKeepsItsPrice(TestContext context) {
        Scene scene = scene(context, 0);
        PartyControllerEntity party = scene.party();
        ItemStack star = party.getCurrency(PartyCurrency.STAR);
        party.getBankItems().setStack(1, star.copyWithCount(1));
        scene.a().getInventory().insertStack(scene.coin().copyWithCount(20));
        BlockPos space = context.getAbsolutePos(new BlockPos(4, 1, 1));
        party.setStarSpace(space);
        context.assertTrue(fr.lordfinn.steveparty.service.PartyStars.buy(party, context.getWorld(), scene.a(), space, 20), "bought");
        context.assertTrue(count(scene.a().getInventory(), star) == 1 && scene.bankCoins() == 20, "the star for 20 coins");
        // Given to b
        InventoryUtils.take(scene.a().getInventory(), star, 1);
        scene.b().getInventory().insertStack(star.copyWithCount(1));

        List<Text> lines = PartyRewind.replay(scene.party());
        context.assertTrue(lines != null && says(lines, "journal.steveparty.not_undone.kept"), "replayed, the price kept is said");
        context.assertEquals(scene.coinsOf(scene.a()), 0, "the buyer gets no coin back");
        context.assertEquals(scene.bankCoins(), 20, "the price stays in the bank");
        context.assertEquals(count(scene.b().getInventory(), star), 1, "b keeps the star");
        context.complete();
    }

    /** The star bought and kept: « replay » takes the star back to the bank and gives the coins back. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "turn_journal_purchase")
    public void aPurchaseIsUndone(TestContext context) {
        Scene scene = scene(context, 0);
        PartyControllerEntity party = scene.party();
        ItemStack star = party.getCurrency(PartyCurrency.STAR);
        party.getBankItems().setStack(1, star.copyWithCount(1));
        scene.a().getInventory().insertStack(scene.coin().copyWithCount(20));
        BlockPos space = context.getAbsolutePos(new BlockPos(4, 1, 1));
        party.setStarSpace(space);
        context.assertTrue(fr.lordfinn.steveparty.service.PartyStars.buy(party, context.getWorld(), scene.a(), space, 20), "bought");

        List<Text> lines = PartyRewind.replay(scene.party());
        context.assertTrue(lines != null && lines.isEmpty(), "replayed, everything undone");
        context.assertTrue(scene.coinsOf(scene.a()) == 20 && count(scene.a().getInventory(), star) == 0, "the buyer as before");
        context.assertTrue(scene.bankCoins() == 0 && count(party.getBankItems(), star) == 1, "the bank as before");
        context.assertTrue(party.getStarSpace() == null, "the star as when the turn started (none)");
        context.complete();
    }

    /**
     * A shop space selling a diamond (from {@link #CHEST}, 10 of them) for an emerald; a's pawn lands on it and a buys
     * one, keeping it in his inventory. The party's bank is the till.
     */
    private static void buyAtTheShop(TestContext context, Scene scene, Inventory chest, Runnable then) {
        ItemStack diamond = new ItemStack(Items.DIAMOND), emerald = new ItemStack(Items.EMERALD);
        chest.setStack(0, diamond.copyWithCount(10));
        context.setBlockState(SHOP, ModBlocks.TILE);
        BoardSpaceBlockEntity space = context.getBlockEntity(SHOP);
        ItemStack cartridge = new ItemStack(ModItems.SHOP_CARTRIDGE);
        ShopCartridgeItem.setOffers(cartridge, List.of(List.of(diamond.copy(), emerald.copy())));
        CartridgeContainers.set(cartridge, List.of(GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(CHEST))));
        space.setStack(0, cartridge);
        scene.a().getInventory().insertStack(emerald.copyWithCount(1));
        moveTo(context, scene.pawnA(), SHOP.up());
        context.assertTrue(ShopStops.onLanding(scene.pawnA(), space, null), "a shops");
        context.assertTrue(ShopStops.open(scene.a(), scene.pawnA().getUuid()) && scene.a().currentScreenHandler instanceof MerchantScreenHandler,
                "the shop screen");
        MerchantScreenHandler handler = (MerchantScreenHandler) scene.a().currentScreenHandler;
        InventoryUtils.take(scene.a().getInventory(), emerald, 1);
        handler.getSlot(0).setStack(emerald.copyWithCount(1));
        handler.onSlotClick(2, 0, SlotActionType.PICKUP, scene.a());
        context.assertTrue(handler.getCursorStack().isOf(Items.DIAMOND), "bought a diamond");
        scene.a().getInventory().insertStack(handler.getCursorStack().copy());
        handler.setCursorStack(ItemStack.EMPTY);
        scene.a().closeHandledScreen();
        context.assertTrue(count(chest, diamond) == 9 && count(scene.party().getBankItems(), emerald) == 1, "from the stock, paid into the bank");
        TestWait.when(context, () -> !ShopStops.isShopping(scene.pawnA().getUuid()), 100, "the stop is over", then);
    }

    /** A purchase at a shop space: « replay » takes the item back to the stock and gives the price back. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "turn_journal_shop")
    public void aShopPurchaseIsUndone(TestContext context) {
        Scene scene = scene(context, 0);
        Inventory chest = chest(context, CHEST);
        ItemStack diamond = new ItemStack(Items.DIAMOND), emerald = new ItemStack(Items.EMERALD);
        buyAtTheShop(context, scene, chest, () -> {
            List<Text> lines = PartyRewind.replay(scene.party());
            context.assertTrue(lines != null && lines.isEmpty(), "replayed, everything undone");
            context.assertTrue(count(scene.a().getInventory(), diamond) == 0 && count(scene.a().getInventory(), emerald) == 1, "the buyer as before");
            context.assertTrue(count(chest, diamond) == 10 && count(scene.party().getBankItems(), emerald) == 0, "the stock and the till as before");
            context.complete();
        });
    }

    /** The item bought given to another player: « replay » gives the buyer nothing back, the price stays in the till. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "turn_journal_shop_given")
    public void aShopPurchaseGivenAwayKeepsItsPrice(TestContext context) {
        Scene scene = scene(context, 0);
        Inventory chest = chest(context, CHEST);
        ItemStack diamond = new ItemStack(Items.DIAMOND), emerald = new ItemStack(Items.EMERALD);
        buyAtTheShop(context, scene, chest, () -> {
            InventoryUtils.take(scene.a().getInventory(), diamond, 1);
            scene.b().getInventory().insertStack(diamond.copyWithCount(1));
            List<Text> lines = PartyRewind.replay(scene.party());
            context.assertTrue(lines != null && says(lines, "journal.steveparty.not_undone.kept"), "replayed, the price kept is said");
            context.assertEquals(count(scene.a().getInventory(), emerald), 0, "the buyer gets no emerald back");
            context.assertEquals(count(scene.party().getBankItems(), emerald), 1, "the price stays in the till");
            context.assertTrue(count(scene.b().getInventory(), diamond) == 1 && count(chest, diamond) == 9, "b keeps the diamond, none made");
            context.complete();
        });
    }

    /**
     * « previous » from b's turn: b's turn and a's are undone (their coins back in the bank, as far as they still hold
     * them: a gave one to b meanwhile), back to a's turn. Nothing is made: the coins add up.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "turn_journal_previous")
    public void previousGoesBackOverTwoTurns(TestContext context) {
        Scene scene = scene(context, 10);
        PartyControllerEntity party = scene.party();
        int aTurn = party.getPartyData().getStepIndex();
        moveTo(context, scene.pawnA(), new BlockPos(6, 1, 1));
        DiceRollEffects.applyCoins(context.getWorld(), scene.pawnA(), scene.a().getUuid(), 3);
        party.nextStep();
        context.assertTrue(party.getPartyData().getCurrentStep() instanceof TokenTurnPartyStep turn && scene.pawnB().getUuid().equals(turn.getTokenUUID()),
                "b's turn");
        moveTo(context, scene.pawnB(), new BlockPos(6, 1, 5));
        DiceRollEffects.applyCoins(context.getWorld(), scene.pawnB(), scene.b().getUuid(), 2);
        // a gives one of his coins to b
        InventoryUtils.take(scene.a().getInventory(), scene.coin(), 1);
        scene.b().getInventory().insertStack(scene.coin().copyWithCount(1));

        List<Text> lines = PartyRewind.previous(party);
        context.assertTrue(lines != null, "went back");
        context.assertTrue(party.getPartyData().getStepIndex() == aTurn
                && party.getPartyData().getCurrentStep() instanceof TokenTurnPartyStep turn && scene.pawnA().getUuid().equals(turn.getTokenUUID()),
                "a's turn again");
        context.assertTrue(standsAt(context, scene.pawnA(), PAWN_A) && standsAt(context, scene.pawnB(), PAWN_B), "both pawns back");
        context.assertEquals(scene.coinsOf(scene.a()), 0, "a's coins taken back");
        context.assertEquals(scene.coinsOf(scene.b()), 1, "b keeps the coin a gave him");
        context.assertTrue(says(lines, "journal.steveparty.not_undone.not_there"), "the coin a gave away is said");
        context.assertEquals(scene.bankCoins() + scene.coinsOf(scene.a()) + scene.coinsOf(scene.b()), 10, "no coin made");
        context.assertTrue(PartyRewind.previous(party) == null, "nothing before a's first turn");
        context.complete();
    }
}
