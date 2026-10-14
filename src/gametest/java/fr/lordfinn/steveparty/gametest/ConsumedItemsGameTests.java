package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.DefaultDiceItem;
import fr.lordfinn.steveparty.powerups.PowerUps;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.Map;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;

/**
 * What a party uses up (a power-up, a Power-up die) goes back to its Party Controller's storage; a full storage drops
 * it on the controller; the infinite storage just takes it. Every die has a base price on a stall.
 */
public class ConsumedItemsGameTests implements SteveGameTest {
    private static final String BATCH = "consumed_items";

    /** A survival player and their party (its turn, before the roll), its storage empty and not infinite. */
    private static PartyControllerEntity party(TestContext context, ServerPlayerEntity player) {
        path(context, 3, -1, null);
        PigEntity pig = token(context, PATH.get(0), player.getUuid());
        PartyControllerEntity controller = DiceTestKit.party(context, player.getUuid(), pig);
        controller.setInfiniteBank(false);
        return controller;
    }

    private static ServerPlayerEntity survivalPlayer(TestContext context) {
        ServerPlayerEntity player = player(context);
        player.changeGameMode(GameMode.SURVIVAL);
        return player;
    }

    private static boolean useDoubleCoins(TestContext context, ServerPlayerEntity player, int count) {
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(PowerUps.DOUBLE_COINS.item(), count));
        return player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND).getResult().isAccepted();
    }

    private static int stored(PartyControllerEntity controller, Item item) {
        return InventoryUtils.count(controller.getBankItems(), new ItemStack(item));
    }

    /** The items of {@code item} lying around the controller (removed: nothing left for the next tests). */
    private static int dropped(TestContext context, Item item) {
        Box around = new Box(context.getAbsolutePos(CONTROLLER)).expand(2);
        List<ItemEntity> items = context.getWorld().getEntitiesByClass(ItemEntity.class, around, entity -> entity.getStack().isOf(item));
        int count = items.stream().mapToInt(entity -> entity.getStack().getCount()).sum();
        items.forEach(ItemEntity::discard);
        return count;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void aUsedPowerUpGoesBackToTheStorage(TestContext context) {
        ServerPlayerEntity player = survivalPlayer(context);
        PartyControllerEntity controller = party(context, player);
        context.waitAndRun(2, () -> {
            context.assertTrue(useDoubleCoins(context, player, 2), "used during its turn");
            context.assertEquals(player.getMainHandStack().getCount(), 1, "one used up");
            context.assertEquals(stored(controller, PowerUps.DOUBLE_COINS.item()), 1, "back in the storage");
            context.assertEquals(dropped(context, PowerUps.DOUBLE_COINS.item()), 0, "nothing dropped");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void aFullStorageDropsItOnTheController(TestContext context) {
        ServerPlayerEntity player = survivalPlayer(context);
        PartyControllerEntity controller = party(context, player);
        for (int slot = 0; slot < PartyControllerEntity.BANK_SIZE; slot++)
            controller.getBankItems().setStack(slot, new ItemStack(Items.STONE, 64));
        context.waitAndRun(2, () -> {
            context.assertTrue(useDoubleCoins(context, player, 1), "used during its turn");
            context.assertTrue(player.getMainHandStack().isEmpty(), "used up");
            context.assertEquals(stored(controller, PowerUps.DOUBLE_COINS.item()), 0, "no room in the storage");
            context.assertEquals(dropped(context, PowerUps.DOUBLE_COINS.item()), 1, "dropped on the controller");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void theInfiniteStorageJustTakesIt(TestContext context) {
        ServerPlayerEntity player = survivalPlayer(context);
        PartyControllerEntity controller = party(context, player);
        controller.setInfiniteBank(true);
        context.waitAndRun(2, () -> {
            context.assertTrue(useDoubleCoins(context, player, 1), "used during its turn");
            context.assertTrue(player.getMainHandStack().isEmpty(), "used up");
            context.assertEquals(stored(controller, PowerUps.DOUBLE_COINS.item()), 0, "its own inventory untouched");
            context.assertEquals(dropped(context, PowerUps.DOUBLE_COINS.item()), 0, "nothing dropped");
            context.complete();
        });
    }

    /** A spent Power-up die goes to the storage; a plain die goes back to its roller, not to the storage. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = BATCH)
    public void aSpentPowerUpDieGoesBackToTheStorage(TestContext context) {
        ServerPlayerEntity player = survivalPlayer(context);
        PartyControllerEntity controller = party(context, player);
        ItemStack powerUpDie = DiceModules.set(new ItemStack(ModItems.DEFAULT_DICE), Map.of(DiceModules.POWER_UP, 1));
        context.waitAndRun(2, () -> {
            DiceEntity spent = context.spawnEntity(ModEntities.DICE_ENTITY, PATH.get(1).up(2));
            spent.setOwner(player.getUuid());
            spent.setItemReference(powerUpDie.copy());
            spent.kill();
            ItemStack back = controller.getBankItems().getStack(0);
            context.assertTrue(ItemStack.areItemsAndComponentsEqual(back, powerUpDie) && back.getCount() == 1,
                    "the Power-up die is in the storage, got " + back);

            controller.getBankItems().clear();
            DiceEntity plain = context.spawnEntity(ModEntities.DICE_ENTITY, PATH.get(1).up(2));
            plain.setOwner(player.getUuid());
            plain.setItemReference(new ItemStack(ModItems.DEFAULT_DICE));
            plain.kill();
            context.assertEquals(stored(controller, ModItems.DEFAULT_DICE), 0, "a plain die is not the party's");
            context.complete();
        });
    }

    /** Every die sells without a price set: one die 5 coins, double 10, triple 20, a forged one as a die. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void everyDieHasABasePrice(TestContext context) {
        for (Item item : Registries.ITEM) {
            if (!(item instanceof DefaultDiceItem)) continue;
            ItemStack price = TradingStallBlockEntity.basePrice(new ItemStack(item));
            context.assertTrue(price.isOf(ModItems.COIN) && price.getCount() > 0, Registries.ITEM.getId(item) + " has a base price");
        }
        context.assertEquals(TradingStallBlockEntity.basePrice(new ItemStack(ModItems.DEFAULT_DICE)).getCount(), 5, "a die: 5");
        context.assertEquals(TradingStallBlockEntity.basePrice(new ItemStack(ModItems.DOUBLE_DICE)).getCount(), 10, "a double die: 10");
        context.assertEquals(TradingStallBlockEntity.basePrice(new ItemStack(ModItems.TRIPLE_DICE)).getCount(), 20, "a triple die: 20");
        context.assertEquals(TradingStallBlockEntity.basePrice(die("dice_face_1", "dice_face_6")).getCount(), 5, "a forged die: 5");
        context.complete();
    }
}
