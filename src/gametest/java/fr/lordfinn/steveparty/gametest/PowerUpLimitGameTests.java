package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import fr.lordfinn.steveparty.persistent_state.VendorLinkPersistentState;
import fr.lordfinn.steveparty.powerups.PowerUpLimit;
import fr.lordfinn.steveparty.powerups.PowerUps;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
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

import java.util.List;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;

/**
 * The party's « Max power-ups » setting ({@link PowerUpLimit}): 3 by default, saved; power-ups and dice without
 * Infinity count, a die with Infinity does not; a purchase past it is refused with nothing paid; 0 is no limit.
 */
public class PowerUpLimitGameTests implements FabricGameTest {
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
    public void aDieWithoutInfinityCounts(TestContext context) {
        ServerPlayerEntity player = player(context);
        partyOf(context, player);
        ItemStack plain = die("dice_face_1", "dice_face_2");
        context.assertTrue(PowerUpLimit.counts(plain), "a die without Infinity counts");
        context.assertTrue(PowerUpLimit.counts(new ItemStack(PowerUps.MUSHROOM.item())), "a power-up counts");
        fill(player, plain.copy(), plain.copy(), new ItemStack(PowerUps.MUSHROOM.item()));
        context.assertEquals(PowerUpLimit.carried(player), 3, "piles counted by their items");
        context.assertEquals(PowerUpLimit.room(player), 0, "the limit reached");
        context.assertEquals(PowerUpLimit.allowed(player, new ItemStack(PowerUps.TRAP.item())), 0, "nothing more");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void anInfinityDieDoesNotCount(TestContext context) {
        ServerPlayerEntity player = player(context);
        partyOf(context, player);
        ItemStack infinity = with(die("dice_face_1", "dice_face_2"), DiceModules.INFINITY, 1);
        context.assertTrue(!PowerUpLimit.counts(infinity), "a die with Infinity does not count");
        fill(player, infinity.copy(), infinity.copy(), infinity.copy(), infinity.copy(), new ItemStack(Items.DIAMOND, 10));
        context.assertEquals(PowerUpLimit.carried(player), 0, "nothing carried");
        context.assertEquals(PowerUpLimit.room(player), 3, "room for 3");
        context.complete();
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
