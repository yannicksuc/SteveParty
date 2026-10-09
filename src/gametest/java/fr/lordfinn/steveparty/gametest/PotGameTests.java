package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.MagpieNestBlock;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.magpie.MagpieEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.BoardRuleCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.PotCartridgeItem;
import fr.lordfinn.steveparty.service.CommonPots;
import fr.lordfinn.steveparty.service.TokenMovementService;
import fr.lordfinn.steveparty.service.TurnMoves;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The Common pot Cartridge and its Pie: stakes of the passing tokens (what the player has, the cap), the token stopping
 * on it winning the pot (in a party, outside a party, paying first if asked), the thieving Pie, the pot saved with its
 * tile, the nest showing it and the Pie coming and going with its cartridge. Each test has its own batch (a mock
 * player and their coins).
 */
public class PotGameTests implements SteveGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(0, 1, 7);
    private static final List<BlockPos> PATH = List.of(new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1),
            new BlockPos(7, 1, 1), new BlockPos(9, 1, 1), new BlockPos(11, 1, 1));
    private static final int POT = 2;

    /** The path, the pot at {@link #POT}; a mock player owning a cow token on the first space. */
    private record Board(ServerPlayerEntity player, CowEntity token, BoardSpaceBlockEntity pot) {
        ItemStack cartridge() {
            return pot.getActiveCartridgeItemStack();
        }

        int coins() {
            return InventoryUtils.count(player.getInventory(), new ItemStack(ModItems.COIN));
        }
    }

    private static Board board(TestContext context, ItemStack pot) {
        for (int i = 0; i < PATH.size(); i++) {
            BlockPos pos = PATH.get(i);
            context.setBlockState(pos.down(), Blocks.STONE);
            context.setBlockState(pos, ModBlocks.TILE);
            ItemStack cartridge = i == POT ? pot : new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR);
            List<BlockPos> next = i + 1 < PATH.size() ? List.of(context.getAbsolutePos(PATH.get(i + 1))) : List.of();
            cartridge.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(new ArrayList<>(next), ""));
            BoardSpaceBlockEntity space = context.getBlockEntity(pos);
            space.setStack(0, cartridge);
        }
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.getInventory().clear();
        CowEntity cow = context.spawnEntity(EntityType.COW, PATH.getFirst());
        TokenizedEntityInterface token = (TokenizedEntityInterface) cow;
        token.steveparty$setTokenized(true);
        token.steveparty$setTokenOwner(player.getUuid());
        token.steveparty$setStatus(TokenStatus.IN_GAME);
        ThresholdGameTests.atEnd(context, () -> {
            token.steveparty$setTokenized(false);
            TurnMoves.forget(cow.getUuid());
            context.getWorld().getServer().getPlayerManager().remove(player);
        });
        return new Board(player, cow, context.getBlockEntity(PATH.get(POT)));
    }

    private static ItemStack pot(int stake) {
        ItemStack pot = new ItemStack(ModItems.POT_CARTRIDGE);
        BoardRuleCartridgeItem.putSetting(pot, PotCartridgeItem.STAKE, stake);
        return pot;
    }

    private static void roll(MobEntity token, int steps) {
        BoardSpaceBlockEntity from = BoardSpaces.boardSpaceOf(token);
        TurnMoves.record(token, steps, List.of(steps), from == null ? null : from.getPos());
        TokenMovementService.moveEntityOnBoard(token, steps);
    }

    private static boolean isOn(TestContext context, MobEntity token, BlockPos at) {
        BoardSpaceBlockEntity on = BoardSpaces.boardSpaceOf(token);
        return on != null && on.getPos().equals(context.getAbsolutePos(at)) && ((TokenizedEntityInterface) token).steveparty$getNbSteps() == 0;
    }

    private static void when(TestContext context, BooleanSupplier condition, int ticks, String what, Runnable then) {
        if (condition.getAsBoolean()) {
            then.run();
            return;
        }
        context.assertTrue(ticks > 0, "timed out: " + what);
        context.waitAndRun(1, () -> when(context, condition, ticks - 1, what, then));
    }

    private static void give(Board board, int coins) {
        InventoryUtils.giveOrDrop(board.player(), new ItemStack(ModItems.COIN), coins);
    }

    // ---------------------------------------------------------------- stakes

    /** Passing over the pot: the stake goes in (stake 2: 5 coins → 3, the pot 0 → 2). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "pot_stake")
    public void aPassingTokenPutsItsStakeIn(TestContext context) {
        Board board = board(context, pot(2));
        give(board, 5);
        context.waitAndRun(2, () -> {
            roll(board.token(), 4);
            when(context, () -> isOn(context, board.token(), PATH.get(4)), 200, "the move ends", () -> {
                context.assertEquals(board.coins(), 3, "the stake was taken");
                context.assertEquals(PotCartridgeItem.coins(board.cartridge()), 2, "the pot holds the stake");
                context.complete();
            });
        });
    }

    /** Not enough coins: all it has goes in, no debt. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "pot_poor")
    public void notEnoughCoinsGivesWhatItHas(TestContext context) {
        Board board = board(context, pot(5));
        give(board, 2);
        context.waitAndRun(2, () -> {
            roll(board.token(), 3);
            when(context, () -> isOn(context, board.token(), PATH.get(3)), 200, "the move ends", () -> {
                context.assertEquals(board.coins(), 0, "all its coins");
                context.assertEquals(PotCartridgeItem.coins(board.cartridge()), 2, "the pot got 2");
                context.complete();
            });
        });
    }

    /** A capped pot takes no more than its cap. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "pot_cap")
    public void theCapLimitsThePot(TestContext context) {
        ItemStack pot = pot(5);
        BoardRuleCartridgeItem.putSetting(pot, PotCartridgeItem.CAP, 3);
        PotCartridgeItem.setCoins(pot, 2);
        Board board = board(context, pot);
        give(board, 10);
        context.waitAndRun(2, () -> {
            roll(board.token(), 3);
            when(context, () -> isOn(context, board.token(), PATH.get(3)), 200, "the move ends", () -> {
                context.assertEquals(PotCartridgeItem.coins(board.cartridge()), 3, "capped at 3");
                context.assertEquals(board.coins(), 9, "only 1 coin taken");
                context.complete();
            });
        });
    }

    // ---------------------------------------------------------------- wins

    /** Outside a party: stopping exactly on the pot wins it, the pot starts again from its start. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "pot_win")
    public void stoppingOnThePotWinsIt(TestContext context) {
        ItemStack pot = pot(1);
        BoardRuleCartridgeItem.putSetting(pot, PotCartridgeItem.START, 1);
        PotCartridgeItem.setCoins(pot, 7);
        Board board = board(context, pot);
        context.waitAndRun(2, () -> {
            roll(board.token(), 2);
            when(context, () -> isOn(context, board.token(), PATH.get(POT)), 200, "it stops on the pot", () -> {
                context.waitAndRun(2, () -> {
                    context.assertEquals(board.coins(), 7, "the whole pot");
                    context.assertEquals(PotCartridgeItem.coins(board.cartridge()), 1, "the pot starts again from its start");
                    context.complete();
                });
            });
        });
    }

    /** In a party the landing wins it; with « the winner stakes too », the stake goes in first. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "pot_win_party")
    public void inAPartyTheLandingWinsAndMayPayFirst(TestContext context) {
        ItemStack pot = pot(1);
        BoardRuleCartridgeItem.putSetting(pot, PotCartridgeItem.LANDER_PAYS, 1);
        PotCartridgeItem.setCoins(pot, 4);
        Board board = board(context, pot);
        context.setBlockState(CONTROLLER.down(), Blocks.STONE);
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        PartyData data = new PartyData();
        data.addToken(board.token().getUuid());
        data.addStep(new PartyStep());
        data.addStep(new TokenTurnPartyStep(board.token().getUuid(), null));
        data.addStep(new TokenTurnPartyStep(board.token().getUuid(), null));
        controller.setPartyData(data);
        controller.nextStep();
        controller.nextStep();
        ThresholdGameTests.atEnd(context, () -> context.removeBlock(CONTROLLER));
        ItemStack coin = controller.getCurrency(PartyCurrency.COIN);
        InventoryUtils.giveOrDrop(board.player(), coin, 3);
        context.waitAndRun(2, () -> {
            roll(board.token(), 2);
            when(context, () -> controller.getPartyData().getStepIndex() >= 2, 200, "the turn ends", () -> {
                context.assertEquals(InventoryUtils.count(board.player().getInventory(), coin), 3 - 1 + 5, "paid 1, won 5");
                context.assertEquals(PotCartridgeItem.coins(board.cartridge()), 0, "the pot is empty again");
                context.complete();
            });
        });
    }

    // ---------------------------------------------------------------- the thieving Pie, saving

    /** The thieving Pie (100 %) steals one item (never the coins) into the pot; the winner gets it. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "pot_thief")
    public void theThievingPieStealsAnItemForThePot(TestContext context) {
        ItemStack pot = pot(1);
        BoardRuleCartridgeItem.putSetting(pot, PotCartridgeItem.THIEF, 100);
        Board board = board(context, pot);
        give(board, 3);
        board.player().getInventory().insertStack(new ItemStack(Items.DIAMOND));
        context.waitAndRun(2, () -> {
            roll(board.token(), 3);
            when(context, () -> isOn(context, board.token(), PATH.get(3)), 200, "the move ends", () -> {
                List<ItemStack> items = PotCartridgeItem.items(board.cartridge());
                context.assertTrue(items.size() == 1 && items.getFirst().isOf(Items.DIAMOND), "the diamond is in the pot");
                context.assertEquals(board.player().getInventory().count(Items.DIAMOND), 0, "taken from the player");
                context.assertEquals(board.coins(), 2, "the coins only for the stake");
                CommonPots.Win win = CommonPots.win(context.getWorld(), board.pot(), board.token());
                context.assertTrue(win.items().size() == 1, "the winner gets the diamond");
                context.assertEquals(board.player().getInventory().count(Items.DIAMOND), 1, "back in an inventory");
                context.assertTrue(PotCartridgeItem.items(board.cartridge()).isEmpty(), "the pot is emptied");
                context.complete();
            });
        });
    }

    /** The pot (coins and items) is saved with its tile. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "pot_save")
    public void thePotIsSavedWithItsTile(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.TILE);
        BoardSpaceBlockEntity tile = context.getBlockEntity(pos);
        ItemStack pot = pot(3);
        PotCartridgeItem.setCoins(pot, 12);
        PotCartridgeItem.setItems(pot, List.of(new ItemStack(Items.EMERALD, 2)));
        tile.setStack(0, pot);
        NbtCompound nbt = tile.createNbtWithIdentifyingData(context.getWorld().getRegistryManager());
        context.setBlockState(pos, Blocks.AIR);
        context.setBlockState(pos, ModBlocks.TILE);
        BoardSpaceBlockEntity loaded = context.getBlockEntity(pos);
        loaded.read(nbt, context.getWorld().getRegistryManager());
        ItemStack back = loaded.getStack(0);
        context.assertEquals(PotCartridgeItem.coins(back), 12, "the coins");
        context.assertEquals(PotCartridgeItem.stake(back), 3, "its settings");
        List<ItemStack> items = PotCartridgeItem.items(back);
        context.assertTrue(items.size() == 1 && items.getFirst().isOf(Items.EMERALD) && items.getFirst().getCount() == 2, "the stolen items");
        context.complete();
    }

    // ---------------------------------------------------------------- the nest and the Pie

    /** A nest near the pot: the Pie comes to live on it, the nest shows the coins; the cartridge gone, the Pie leaves. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "pot_nest")
    public void theNestGetsItsPieWhichLeavesWithTheCartridge(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2), nestPos = new BlockPos(2, 1, 5);
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.TILE);
        context.setBlockState(nestPos.down(), Blocks.STONE);
        context.setBlockState(nestPos, ModBlocks.MAGPIE_NEST);
        BoardSpaceBlockEntity tile = context.getBlockEntity(pos);
        ItemStack pot = pot(1);
        PotCartridgeItem.setCoins(pot, 9);
        tile.setStack(0, pot);
        Box around = new Box(context.getAbsolutePos(nestPos)).expand(4);
        when(context, () -> !context.getWorld().getEntitiesByType(ModEntities.MAGPIE, around, MagpieEntity::isAlive).isEmpty(),
                120, "the Pie comes", () -> {
                    MagpieEntity magpie = context.getWorld().getEntitiesByType(ModEntities.MAGPIE, around, MagpieEntity::isAlive).getFirst();
                    context.assertEquals(magpie.getHome(), context.getAbsolutePos(pos), "its home is the pot space");
                    context.assertEquals(context.getBlockState(nestPos).get(MagpieNestBlock.COINS), 2, "9 coins: a pile in the nest");
                    context.assertTrue(!magpie.damage(context.getWorld().getDamageSources().generic(), 100), "never hurt");
                    tile.setStack(0, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));
                    when(context, magpie::isRemoved, 120, "the Pie leaves with the cartridge", context::complete);
                });
    }
}
