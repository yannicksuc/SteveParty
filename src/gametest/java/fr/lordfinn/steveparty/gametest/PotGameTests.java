package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.TestCleanup;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.MagpieNestBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.board.BrushLinkable;
import fr.lordfinn.steveparty.board.BrushLinks;
import fr.lordfinn.steveparty.board.CartridgeLinks;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.magpie.MagpieEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.BoardRuleCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.PotCartridgeItem;
import fr.lordfinn.steveparty.service.CommonPots;
import fr.lordfinn.steveparty.service.TokenMovementService;
import fr.lordfinn.steveparty.service.TurnMoves;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
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
import net.minecraft.util.math.GlobalPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The Common pot Cartridge, its nest and its Pie: the pot is the coins in the nest its cartridge links (no link, no
 * pot; a nest nearby is not linked by itself), stakes of the passing tokens (what the player has, the cap), the token
 * stopping on it winning the nest's content (in a party, outside a party, paying first if asked), the coins going back
 * to a storage at the party's end, the Pie coming and going with its cartridge. Each test has its own batch (a mock
 * player and their coins).
 */
public class PotGameTests implements SteveGameTest {
    /** Beyond the template: the path goes on to x = 11 (else the token may freeze at a chunk edge). */
    @Override
    public int landAround() {
        return 4;
    }

    private static final BlockPos CONTROLLER = new BlockPos(0, 1, 7);
    private static final List<BlockPos> PATH = List.of(new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1),
            new BlockPos(7, 1, 1), new BlockPos(9, 1, 1), new BlockPos(11, 1, 1));
    private static final int POT = 2;
    /** The nest of the pot space, three blocks beside it. */
    private static final BlockPos NEST = PATH.get(POT).add(0, 0, 3);

    /** The path, the pot at {@link #POT} (its nest at {@link #NEST}); a mock player owning a cow token on the first space. */
    private record Board(ServerPlayerEntity player, CowEntity token, BoardSpaceBlockEntity pot, MagpieNestBlockEntity nest) {
        ItemStack cartridge() {
            return pot.getActiveCartridgeItemStack();
        }

        int coins() {
            return InventoryUtils.count(player.getInventory(), new ItemStack(ModItems.COIN));
        }

        /** The mod's coins in the nest: the pot outside a party. */
        int potCoins() {
            return nest.count(new ItemStack(ModItems.COIN));
        }
    }

    private static Board board(TestContext context, ItemStack pot) {
        return board(context, pot, true);
    }

    /** The board; {@code linked}: the cartridge links the nest. */
    private static Board board(TestContext context, ItemStack pot, boolean linked) {
        context.setBlockState(NEST.down(), Blocks.STONE);
        context.setBlockState(NEST, ModBlocks.MAGPIE_NEST);
        if (linked) PotCartridgeItem.setNest(pot, context.getWorld(), context.getAbsolutePos(NEST));
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
        TestCleanup.atEnd(context, () -> {
            token.steveparty$setTokenized(false);
            TurnMoves.forget(cow.getUuid());
            context.getWorld().getServer().getPlayerManager().remove(player);
        });
        return new Board(player, cow, context.getBlockEntity(PATH.get(POT)), context.getBlockEntity(NEST));
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

    /** A party running on the board, the board's token playing; its bank emptied. */
    private static PartyControllerEntity party(TestContext context, Board board) {
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
        controller.getBankItems().clear();
        TestCleanup.atEnd(context, () -> context.removeBlock(CONTROLLER));
        return controller;
    }

    // ---------------------------------------------------------------- the party's coin

    /**
     * The party's coin is a coin for the nest of its pot space (the party found through the space, never the nearest
     * one around); a nest no pot links knows the mod's coin only.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "pot_party_coin")
    public void theNestKnowsTheCoinOfItsPotSpacesParty(TestContext context) {
        Board board = board(context, pot(2));
        PartyControllerEntity controller = party(context, board);
        context.assertTrue(controller.setCurrency(PartyCurrency.COIN, new ItemStack(Items.GOLD_NUGGET)), "coins: gold nuggets");
        BlockPos alone = NEST.add(2, 0, 0);
        context.setBlockState(alone.down(), Blocks.STONE);
        context.setBlockState(alone, ModBlocks.MAGPIE_NEST);
        MagpieNestBlockEntity lone = context.getBlockEntity(alone);
        // As its pot space does when it first acts on it
        board.nest().link(board.pot().getPos());
        context.waitAndRun(21, () -> {
            context.assertTrue(board.nest().isCoin(new ItemStack(Items.GOLD_NUGGET)), "the pot's nest: the party's coin");
            context.assertTrue(board.nest().isCoin(new ItemStack(ModItems.COIN)), "and the mod's coin");
            context.assertTrue(!lone.isCoin(new ItemStack(Items.GOLD_NUGGET)), "a nest on its own: not a coin");
            context.complete();
        });
    }

    // ---------------------------------------------------------------- stakes

    /** Passing over the pot: the stake goes into the nest (stake 2: 5 coins → 3, the nest 0 → 2). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "pot_stake")
    public void aPassingTokenPutsItsStakeIn(TestContext context) {
        Board board = board(context, pot(2));
        give(board, 5);
        context.waitAndRun(2, () -> {
            roll(board.token(), 4);
            when(context, () -> isOn(context, board.token(), PATH.get(4)), 200, "the move ends", () -> {
                context.assertEquals(board.coins(), 3, "the stake was taken");
                context.assertEquals(board.potCoins(), 2, "the nest holds the stake");
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
                context.assertEquals(board.potCoins(), 2, "the nest got 2");
                context.complete();
            });
        });
    }

    /** A capped pot takes no more than its cap. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "pot_cap")
    public void theCapLimitsThePot(TestContext context) {
        ItemStack pot = pot(5);
        BoardRuleCartridgeItem.putSetting(pot, PotCartridgeItem.CAP, 3);
        Board board = board(context, pot);
        board.nest().addCoins(new ItemStack(ModItems.COIN), 2);
        give(board, 10);
        context.waitAndRun(2, () -> {
            roll(board.token(), 3);
            when(context, () -> isOn(context, board.token(), PATH.get(3)), 200, "the move ends", () -> {
                context.assertEquals(board.potCoins(), 3, "capped at 3");
                context.assertEquals(board.coins(), 9, "only 1 coin taken");
                context.complete();
            });
        });
    }

    /** No nest linked: passing costs nothing; a nest standing beside the space is not its pot by itself. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "pot_no_nest")
    public void withoutALinkedNestThePotDoesNothing(TestContext context) {
        Board board = board(context, pot(2), false);
        give(board, 5);
        context.waitAndRun(2, () -> {
            CommonPots.care(context.getWorld(), board.pot(), board.cartridge());
            context.assertTrue(board.nest().getPot() == null && !board.nest().isLinked(), "a nest nearby is not linked by itself");
            roll(board.token(), 4);
            when(context, () -> isOn(context, board.token(), PATH.get(4)), 200, "the move ends", () -> {
                context.assertEquals(board.coins(), 5, "no stake without a nest");
                context.assertEquals(board.nest().getPileCount(), 0, "the nest untouched");
                context.complete();
            });
        });
    }

    /** A new pot cartridge and a new nest, linked: the pot is empty, and the cartridge holds no coins of its own. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "pot_new")
    public void aNewPotWithANewNestIsEmpty(TestContext context) {
        Board board = board(context, new ItemStack(ModItems.POT_CARTRIDGE));
        CommonPots.care(context.getWorld(), board.pot(), board.cartridge());
        context.assertEquals(board.nest().getPot(), board.pot().getPos(), "the nest knows its pot");
        context.assertEquals(board.nest().getPileCount(), 0, "nothing in the nest");
        context.assertTrue(board.nest().getContents().isEmpty(), "its card shows nothing");
        context.assertEquals(BoardRuleCartridgeItem.state(board.cartridge(), "coins", -1), -1, "no coins kept in the cartridge");
        context.complete();
    }

    // ---------------------------------------------------------------- wins

    /** Outside a party: stopping exactly on the pot wins the nest; no storage: its base is not refilled (nothing made). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "pot_win")
    public void stoppingOnThePotWinsIt(TestContext context) {
        ItemStack pot = pot(1);
        BoardRuleCartridgeItem.putSetting(pot, PotCartridgeItem.START, 1);
        Board board = board(context, pot);
        board.nest().addCoins(new ItemStack(ModItems.COIN), 7);
        context.waitAndRun(2, () -> {
            roll(board.token(), 2);
            when(context, () -> isOn(context, board.token(), PATH.get(POT)), 200, "it stops on the pot", () -> {
                context.waitAndRun(2, () -> {
                    context.assertEquals(board.coins(), 7, "the whole pot");
                    context.assertEquals(board.potCoins(), 0, "no storage: the nest starts again empty");
                    context.assertTrue(PotCartridgeItem.baseDue(board.cartridge()), "its base still due");
                    context.complete();
                });
            });
        });
    }

    /** In a party the landing wins the whole pot, paying no stake. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "pot_win_party")
    public void inAPartyTheLandingWinsThePot(TestContext context) {
        Board board = board(context, pot(1));
        PartyControllerEntity controller = party(context, board);
        ItemStack coin = controller.getCurrency(PartyCurrency.COIN);
        board.nest().addCoins(coin, 4);
        InventoryUtils.giveOrDrop(board.player(), coin, 3);
        context.waitAndRun(2, () -> {
            roll(board.token(), 2);
            when(context, () -> controller.getPartyData().getStepIndex() >= 2, 200, "the turn ends", () -> {
                context.assertEquals(InventoryUtils.count(board.player().getInventory(), coin), 3 + 4, "paid nothing, won 4");
                context.assertEquals(board.nest().count(coin), 0, "the nest is empty again");
                context.complete();
            });
        });
    }

    // ---------------------------------------------------------------- the party's end

    /** The party over: the pot's coins go back to the party's bank, the shiny things stay in the nest. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "pot_end_bank")
    public void atThePartysEndTheCoinsGoBackToTheBank(TestContext context) {
        Board board = board(context, pot(1));
        PartyControllerEntity controller = party(context, board);
        ItemStack coin = controller.getCurrency(PartyCurrency.COIN);
        board.nest().addCoins(coin, 6);
        board.nest().putTreasure(new ItemStack(Items.EMERALD));
        context.assertEquals(CommonPots.giveBack(context.getWorld(), board.pot(), controller), 6, "6 coins back");
        context.assertEquals(InventoryUtils.count(controller.getBankItems(), coin), 6, "in the bank");
        context.assertEquals(board.nest().count(coin), 0, "none left in the nest");
        context.assertEquals(board.nest().count(new ItemStack(Items.EMERALD)), 1, "the emerald stays");
        context.assertTrue(PotCartridgeItem.baseDue(board.cartridge()), "its base due again");
        context.complete();
    }

    /** The space's own storage first (an Inventory Cartridge's chest, same tile); what it cannot take stays in the nest. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "pot_end_storage")
    public void atThePartysEndTheCoinsGoToTheSpacesStorage(TestContext context) {
        Board board = board(context, pot(1));
        PartyControllerEntity controller = party(context, board);
        ItemStack coin = controller.getCurrency(PartyCurrency.COIN);
        // An Advanced Tile: the pot (slot 0, active) and an Inventory Cartridge (slot 1) linked to a chest
        BlockPos tilePos = new BlockPos(3, 1, 9), chestPos = new BlockPos(5, 1, 9);
        context.setBlockState(tilePos.down(), Blocks.STONE);
        context.setBlockState(tilePos, ModBlocks.ADVANCED_TILE);
        context.setBlockState(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = context.getBlockEntity(chestPos);
        // The chest full but for one slot: it takes a stack, the rest stays in the nest
        for (int slot = 1; slot < chest.size(); slot++) chest.setStack(slot, new ItemStack(Items.DIRT, 64));
        BoardSpaceBlockEntity tile = context.getBlockEntity(tilePos);
        ItemStack pot = pot(1);
        PotCartridgeItem.setNest(pot, context.getWorld(), context.getAbsolutePos(NEST));
        tile.setStack(0, pot);
        ItemStack inventory = new ItemStack(ModItems.INVENTORY_CARTRIDGE);
        CartridgeContainers.set(inventory, List.of(GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(chestPos))));
        tile.setStack(1, inventory);
        board.nest().addCoins(coin, 70);
        int stack = coin.getMaxCount();
        context.assertTrue(CommonPots.potOf(tile) != null, "the pot is the active cartridge");
        context.assertEquals(CommonPots.giveBack(context.getWorld(), tile, controller), stack, "a stack fits in the chest");
        context.assertEquals(InventoryUtils.count(chest, coin), stack, "in the chest, not the bank");
        context.assertEquals(InventoryUtils.count(controller.getBankItems(), coin), 0, "the bank untouched");
        context.assertEquals(board.nest().count(coin), 70 - stack, "the rest kept in the nest");
        context.complete();
    }

    // ---------------------------------------------------------------- saving

    /** The cartridge keeps its nest and its settings with its tile. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "pot_save")
    public void thePotIsSavedWithItsTile(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.TILE);
        BoardSpaceBlockEntity tile = context.getBlockEntity(pos);
        ItemStack pot = pot(3);
        BlockPos nest = context.getAbsolutePos(new BlockPos(1, 1, 4));
        PotCartridgeItem.setNest(pot, context.getWorld(), nest);
        tile.setStack(0, pot);
        NbtCompound nbt = tile.createNbtWithIdentifyingData(context.getWorld().getRegistryManager());
        context.setBlockState(pos, Blocks.AIR);
        context.setBlockState(pos, ModBlocks.TILE);
        BoardSpaceBlockEntity loaded = context.getBlockEntity(pos);
        loaded.read(nbt, context.getWorld().getRegistryManager());
        ItemStack back = loaded.getStack(0);
        context.assertEquals(PotCartridgeItem.linkedNest(back, context.getWorld()), nest, "its nest");
        context.assertEquals(PotCartridgeItem.stake(back), 3, "its settings");
        context.complete();
    }

    // ---------------------------------------------------------------- the nest and the Pie

    /** A linked nest: the Pie comes to live on it, the nest shows the coins; the cartridge gone, the Pie leaves. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "pot_nest")
    public void theNestGetsItsPieWhichLeavesWithTheCartridge(TestContext context) {
        BlockPos pos = new BlockPos(2, 1, 2), nestPos = new BlockPos(2, 1, 5);
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.TILE);
        context.setBlockState(nestPos.down(), Blocks.STONE);
        context.setBlockState(nestPos, ModBlocks.MAGPIE_NEST);
        MagpieNestBlockEntity nest = context.getBlockEntity(nestPos);
        nest.addCoins(new ItemStack(ModItems.COIN), 9);
        BoardSpaceBlockEntity tile = context.getBlockEntity(pos);
        ItemStack pot = pot(1);
        PotCartridgeItem.setNest(pot, context.getWorld(), context.getAbsolutePos(nestPos));
        tile.setStack(0, pot);
        Box around = new Box(context.getAbsolutePos(nestPos)).expand(4);
        when(context, () -> !context.getWorld().getEntitiesByType(ModEntities.MAGPIE, around, MagpieEntity::isAlive).isEmpty(),
                120, "the Pie comes", () -> {
                    MagpieEntity magpie = context.getWorld().getEntitiesByType(ModEntities.MAGPIE, around, MagpieEntity::isAlive).getFirst();
                    context.assertEquals(magpie.getHome(), context.getAbsolutePos(pos), "its home is the pot space");
                    context.assertEquals(nest.getPileCount(), 9, "9 coins: a pile of 9 in the nest");
                    context.assertTrue(!magpie.damage(context.getWorld().getDamageSources().generic(), 100), "never hurt");
                    tile.setStack(0, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));
                    when(context, magpie::isRemoved, 120, "the Pie leaves with the cartridge", context::complete);
                });
    }

    /**
     * The nest is the pot: the stakes land in it, coins and shiny things put in it by hand are the pot's too (each one
     * more coin on the pile); the winner takes it all (the shiny things too) and empties it.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = "pot_nest_content")
    public void theNestIsThePot(TestContext context) {
        Board board = board(context, pot(2));
        give(board, 5);
        context.waitAndRun(2, () -> {
            MagpieNestBlockEntity nest = board.nest();
            CommonPots.care(context.getWorld(), board.pot(), board.cartridge());
            context.assertEquals(nest.getPot(), board.pot().getPos(), "linked to the pot space");
            CommonPots.pass(context.getWorld(), board.pot(), board.token());
            context.assertEquals(board.potCoins(), 2, "the stake in the nest");
            context.assertTrue(nest.insert(new ItemStack(ModItems.COIN, 3)).isEmpty(), "coins by hand");
            context.assertTrue(nest.insert(new ItemStack(Items.EMERALD, 2)).isEmpty(), "a shiny thing by hand");
            context.assertTrue(!nest.insert(new ItemStack(Items.DIRT)).isEmpty(), "dirt is no treasure");
            context.assertEquals(board.potCoins(), 5, "the coins in the pot");
            context.assertEquals(nest.getPileCount(), 7, "one coin of the pile a thing: 5 coins, 2 emeralds");
            List<ItemStack> shown = nest.getContents();
            context.assertTrue(shown.size() == 2 && shown.get(0).isOf(ModItems.COIN) && shown.get(0).getCount() == 5
                    && shown.get(1).isOf(Items.EMERALD), "its card shows the pot: 5 coins, then the emeralds");
            context.assertTrue(nest.isEmpty() && nest.getStack(0).isEmpty(), "a hopper sees nothing to take");
            context.assertTrue(nest.takeTreasure().isEmpty() && nest.takeCoins(5) == 0, "nobody takes from a pot's nest");
            int before = board.coins();
            CommonPots.win(context.getWorld(), board.pot(), board.token());
            context.assertEquals(board.coins() - before, 5, "the winner takes the coins");
            context.assertEquals(InventoryUtils.count(board.player().getInventory(), new ItemStack(Items.EMERALD)), 2, "and the emeralds");
            context.assertEquals(nest.getPileCount(), 0, "the nest emptied");
            context.complete();
        });
    }

    /** The brush links the pot's nest (undone like any link); a Spawn Bubble is refused: the pot's Pie lives on a nest. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "pot_nest_brush")
    public void theBrushLinksTheNestAndRefusesABubble(TestContext context) {
        Board board = board(context, pot(1), false);
        BlockPos nest = context.getAbsolutePos(NEST), bubble = context.getAbsolutePos(new BlockPos(9, 1, 5));
        context.setBlockState(new BlockPos(9, 1, 4), Blocks.STONE);
        context.setBlockState(new BlockPos(9, 1, 5), ModBlocks.SPAWN_MARKER);
        List<BrushLinkable> kinds = BrushLinks.of(context.getWorld(), board.pot().getPos(), TileLinkerBrush.POWERED);
        context.assertTrue(BrushLinks.kindFor(kinds, context.getWorld(), nest) instanceof CartridgeLinks.PotNest, "a nest is the pot's nest");
        context.assertEquals(BrushLinks.refusal(context.getWorld(), board.pot().getPos(), TileLinkerBrush.POWERED, bubble),
                "message.steveparty.tile_linker_brush.pot_needs_nest", "a Spawn Bubble: refused, a nest is needed");
        context.complete();
    }

    /** Unlinked (the cartridge links no nest any more): the nest is on its own again, its content still in it. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = "pot_nest_unlink")
    public void anUnlinkedNestKeepsItsContent(TestContext context) {
        Board board = board(context, pot(1));
        MagpieNestBlockEntity nest = board.nest();
        CommonPots.care(context.getWorld(), board.pot(), board.cartridge());
        nest.addCoins(new ItemStack(ModItems.COIN), 4);
        context.assertTrue(nest.isLinked(), "a pot's nest");
        PotCartridgeItem.setNest(board.cartridge(), context.getWorld(), null);
        context.assertTrue(!nest.isLinked(), "no pot any more");
        context.assertEquals(nest.getPileCount(), 4, "its coins stay in it");
        context.assertEquals(nest.takeCoins(4), 4, "and can be taken back");
        context.complete();
    }
}
