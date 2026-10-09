package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.persistent_state.VendorLinkPersistentState;
import fr.lordfinn.steveparty.powerups.PowerUpLimit;
import fr.lordfinn.steveparty.powerups.PowerUpService;
import fr.lordfinn.steveparty.powerups.PowerUps;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.GameMode;

import java.util.List;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;

/**
 * The party's « Max power-ups » setting ({@link PowerUpLimit}): 3 by default, saved; power-ups and dice carrying the
 * Power-up module count, any other die does not; a purchase past it is refused with nothing paid; 0 is no limit. A
 * Power-up die thrown is the power-up of the turn.
 */
public class PowerUpLimitGameTests implements SteveGameTest {
    private static final String BATCH = "powerup_limit";

    /** A party of {@code player}'s single token, running, at their turn. */
    private static PartyControllerEntity partyOf(TestContext context, ServerPlayerEntity player) {
        path(context, 1, -1, null);
        PigEntity pig = token(context, PATH.get(0), player.getUuid());
        return party(context, player.getUuid(), pig);
    }

    private static void fill(ServerPlayerEntity player, ItemStack... stacks) {
        player.getInventory().clear();
        for (ItemStack stack : stacks) player.getInventory().insertStack(stack);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void threeByDefaultAndSaved(TestContext context) {
        ServerPlayerEntity player = player(context);
        PartyControllerEntity controller = partyOf(context, player);
        context.assertEquals(controller.getMaxPowerUps(), 3, "3 by default");
        context.assertEquals(PowerUpLimit.limitOf(player), 3, "the player's limit in the party");
        controller.setMaxPowerUps(5);
        NbtCompound nbt = controller.createNbt(context.getWorld().getRegistryManager());
        context.assertEquals(nbt.getInt("MaxPowerUps"), 5, "saved with the party");
        controller.setMaxPowerUps(99);
        context.assertEquals(controller.getMaxPowerUps(), PowerUpLimit.MAX, "never above its maximum");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void aPowerUpDieCounts(TestContext context) {
        ServerPlayerEntity player = player(context);
        partyOf(context, player);
        ItemStack powerUp = with(die("dice_face_1", "dice_face_2"), DiceModules.POWER_UP, 1);
        context.assertTrue(PowerUpLimit.counts(powerUp), "a Power-up die counts");
        context.assertTrue(PowerUpLimit.counts(new ItemStack(PowerUps.MUSHROOM.item())), "a power-up counts");
        fill(player, powerUp.copy(), powerUp.copy(), new ItemStack(PowerUps.MUSHROOM.item()));
        context.assertEquals(PowerUpLimit.carried(player), 3, "piles counted by their items");
        context.assertEquals(PowerUpLimit.room(player), 0, "the limit reached");
        context.assertEquals(PowerUpLimit.allowed(player, new ItemStack(PowerUps.TRAP.item())), 0, "nothing more");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void aPlainDieDoesNotCount(TestContext context) {
        ServerPlayerEntity player = player(context);
        partyOf(context, player);
        ItemStack plain = with(die("dice_face_1", "dice_face_2"), DiceModules.LUCKY, 1);
        context.assertTrue(!PowerUpLimit.counts(plain), "a die without the Power-up module does not count");
        context.assertTrue(!PowerUpLimit.counts(new ItemStack(ModItems.DOUBLE_DICE)), "nor a Double Dice");
        fill(player, plain.copy(), plain.copy(), plain.copy(), plain.copy(), new ItemStack(ModItems.TRIPLE_DICE),
                new ItemStack(Items.DIAMOND, 10));
        context.assertEquals(PowerUpLimit.carried(player), 0, "nothing carried");
        context.assertEquals(PowerUpLimit.room(player), 3, "room for 3");
        context.complete();
    }

    /** {@code player} right-clicks the air with {@code stack} in their main hand. */
    private static boolean use(TestContext context, ServerPlayerEntity player, ItemStack stack) {
        player.setStackInHand(Hand.MAIN_HAND, stack);
        return stack.use(context.getWorld(), player, Hand.MAIN_HAND).getResult().isAccepted();
    }

    /** Removes the dice thrown by the test (they would roll on, then come back). */
    private static void discardDice(TestContext context, ServerPlayerEntity player) {
        context.getWorld().getEntitiesByType(ModEntities.DICE_ENTITY, player.getBoundingBox().expand(16), dice -> true)
                .forEach(DiceEntity::discard);
    }

    /**
     * A Power-up die thrown during the turn is its power-up: consumed, no other power-up after it. A plain die thrown
     * is not one: a power-up may still come.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void aPowerUpDieIsThePowerUpOfTheTurn(TestContext context) {
        ServerPlayerEntity player = player(context);
        player.changeGameMode(GameMode.SURVIVAL);
        PartyControllerEntity controller = partyOf(context, player);
        TokenTurnPartyStep turn = (TokenTurnPartyStep) controller.getPartyData().getCurrentStep();
        context.waitAndRun(2, () -> {
            ItemStack plain = die("dice_face_1", "dice_face_2");
            context.assertTrue(use(context, player, plain.copy()), "a plain die is thrown");
            discardDice(context, player);
            context.assertTrue(!turn.getPowerUps().hasUsed(), "a plain die is not the power-up of the turn");

            context.assertTrue(use(context, player, with(plain.copy(), DiceModules.POWER_UP, 1)), "a Power-up die is thrown");
            discardDice(context, player);
            context.assertTrue(player.getMainHandStack().isEmpty(), "consumed");
            context.assertTrue(turn.getPowerUps().hasUsed(), "the power-up of the turn");
            context.assertTrue(!use(context, player, new ItemStack(PowerUps.MUSHROOM.item())), "no other power-up after it");
            context.assertEquals(player.getMainHandStack().getCount(), 1, "the Mushroom is kept");
            context.complete();
        });
    }

    /** After another power-up this turn, a Power-up die is refused (kept); a plain die may still be thrown. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void aPowerUpDieIsRefusedAfterAnotherPowerUp(TestContext context) {
        ServerPlayerEntity player = player(context);
        player.changeGameMode(GameMode.SURVIVAL);
        partyOf(context, player);
        context.waitAndRun(2, () -> {
            context.assertTrue(use(context, player, new ItemStack(PowerUps.MUSHROOM.item())), "a Mushroom is used");
            ItemStack powerUp = with(die("dice_face_1", "dice_face_2"), DiceModules.POWER_UP, 1);
            context.assertTrue(!use(context, player, powerUp.copy()), "the Power-up die is refused");
            context.assertTrue(ItemStack.areItemsAndComponentsEqual(player.getMainHandStack(), powerUp), "and kept");
            context.assertTrue(PowerUpService.rollRefusal(player, die("dice_face_1")) == null, "a plain die may be thrown");
            discardDice(context, player);
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void zeroIsNoLimitAndNoPartyNoLimit(TestContext context) {
        ServerPlayerEntity loner = player(context);
        fill(loner, new ItemStack(PowerUps.MUSHROOM.item(), 10));
        context.assertEquals(PowerUpLimit.room(loner), Integer.MAX_VALUE, "outside a party: no limit");
        ServerPlayerEntity player = player(context);
        PartyControllerEntity controller = partyOf(context, player);
        fill(player, new ItemStack(PowerUps.MUSHROOM.item(), 10));
        context.assertEquals(PowerUpLimit.room(player), 0, "10 over 3: no room");
        controller.setMaxPowerUps(0);
        context.assertEquals(PowerUpLimit.room(player), Integer.MAX_VALUE, "0: no limit");
        context.assertEquals(PowerUpLimit.allowed(player, new ItemStack(PowerUps.TRAP.item(), 5)), 5, "all of it");
        context.complete();
    }

    /** At the limit, a Boxed Trader refuses to sell a power-up: the price stays, nothing comes. Below it, it sells. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void aPurchaseAtTheLimitIsRefused(TestContext context) {
        ServerPlayerEntity player = player(context);
        PartyControllerEntity controller = partyOf(context, player);
        BlockPos stallPos = new BlockPos(4, 1, 1), chestPos = new BlockPos(6, 1, 1);
        context.setBlockState(stallPos, ModBlocks.TRADING_STALL);
        context.setBlockState(chestPos, Blocks.CHEST);
        TradingStallBlockEntity stall = context.getBlockEntity(stallPos);
        stall.setStack(0, new ItemStack(Items.EMERALD));
        stall.setStack(18, new ItemStack(PowerUps.MUSHROOM.item()));
        Inventory chest = context.getBlockEntity(chestPos);
        chest.setStack(0, new ItemStack(PowerUps.MUSHROOM.item(), 10));
        BoxedTraderEntity trader = context.spawnEntity(ModEntities.BOXED_TRADER_ENTITY, new BlockPos(5, 1, 3));
        VendorLinkPersistentState links = VendorLinkPersistentState.get(context.getWorld().getServer());
        for (BlockPos pos : List.of(stallPos, chestPos))
            links.linkBlock(trader.getUuid(), GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(pos)));
        Item mushroom = PowerUps.MUSHROOM.item();
        fill(player, new ItemStack(mushroom, 3));
        player.setPosition(trader.getPos().add(0, 0, 1));
        trader.interact(player, Hand.MAIN_HAND);
        context.assertTrue(player.currentScreenHandler instanceof MerchantScreenHandler, "merchant screen opened");
        MerchantScreenHandler handler = (MerchantScreenHandler) player.currentScreenHandler;
        handler.getSlot(0).setStack(new ItemStack(Items.EMERALD));
        context.assertTrue(!handler.getSlot(2).getStack().isEmpty(), "the offer is there");
        handler.onSlotClick(2, 0, SlotActionType.PICKUP, player);
        context.assertTrue(handler.getCursorStack().isEmpty(), "refused at 3/3");
        context.assertTrue(handler.getSlot(0).getStack().isOf(Items.EMERALD), "nothing paid");
        context.assertEquals(chest.count(mushroom), 10, "nothing taken from the stock");
        handler.quickMove(player, 2);
        context.assertEquals(player.getInventory().count(mushroom), 3, "a shift-click is refused too");
        controller.setMaxPowerUps(4);
        handler.onSlotClick(2, 0, SlotActionType.PICKUP, player);
        context.assertTrue(handler.getCursorStack().isOf(mushroom), "sold below the limit");
        handler.setCursorStack(ItemStack.EMPTY);
        player.closeHandledScreen();
        context.complete();
    }
}
