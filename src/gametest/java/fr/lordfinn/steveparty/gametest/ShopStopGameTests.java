package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyLiveData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlock;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.service.TileInfos;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeSpawnMarker;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.persistent_state.VendorLinkPersistentState;
import fr.lordfinn.steveparty.registry.ModGameRules;
import fr.lordfinn.steveparty.screen_handlers.custom.ShopStopScreenHandler;
import fr.lordfinn.steveparty.service.BoardActors;
import fr.lordfinn.steveparty.service.BoardShop;
import fr.lordfinn.steveparty.service.ShopStops;
import fr.lordfinn.steveparty.service.TokenMovementService;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Shop stops (Shop Cartridge): a check point stops the tokens passing through, a tile opens the shop on landing only;
 * the space summons its own merchant (a hologram, on its Spawn Marker or beside the space, gone with the stop), selling
 * the cartridge's offers from its stock (its chest, or the party's bank) into its till; the purchases allowed are
 * enforced; « Buy nothing », closing, the time running out or the owner leaving end the stop and the token goes on.
 * The merchants of the world are a shop of their own, without any limit.
 * <p>
 * Each test runs in a batch of its own: {@code timeRunsOut} changes a game rule of the whole world, and a party's bank is
 * looked for around the board.
 */
public class ShopStopGameTests implements SteveGameTest {
    /** The board: tile → shop space (a check point or a tile) → tile → tile. */
    private static final BlockPos START = new BlockPos(1, 1, 1), SHOP = new BlockPos(3, 1, 1),
            MIDDLE = new BlockPos(5, 1, 1), END = new BlockPos(7, 1, 1);
    /** The shop's chest, a Spawn Marker facing the board (north), and a merchant of the world with his stall and stock. */
    private static final BlockPos CHEST = new BlockPos(7, 1, 7), MARKER = new BlockPos(3, 1, 4),
            STALL = new BlockPos(1, 1, 7), STOCK = new BlockPos(2, 1, 8), TRADER = new BlockPos(1, 1, 8);
    private static final String KEY = "hud.steveparty.tile_info.";

    private record Board(CowEntity token, BoardSpaceBlockEntity shop, Inventory chest) {
        TokenizedEntityInterface tokenized() {
            return (TokenizedEntityInterface) token;
        }

        ItemStack cartridge() {
            return shop.getStack(0);
        }

        /** The merchant of the token's stop, null if none. */
        BoxedTraderEntity trader() {
            return ShopStops.merchantOf(token.getUuid());
        }
    }

    /**
     * Builds the board, the shop space holding a Shop Cartridge ({@code shopBlock}: a check point or a tile) selling a
     * diamond for an emerald from its chest (10 diamonds).
     */
    private static Board board(TestContext context, ServerPlayerEntity owner, Block shopBlock) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
        }
        context.setBlockState(START, ModBlocks.TILE);
        context.setBlockState(SHOP, shopBlock);
        context.setBlockState(MIDDLE, ModBlocks.TILE);
        context.setBlockState(END, ModBlocks.TILE);
        context.setBlockState(CHEST, Blocks.CHEST);
        Inventory chest = context.getBlockEntity(CHEST);
        chest.setStack(0, new ItemStack(Items.DIAMOND, 10));
        ItemStack cartridge = new ItemStack(ModItems.SHOP_CARTRIDGE);
        ShopCartridgeItem.setOffers(cartridge, List.of(List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.EMERALD))));
        CartridgeContainers.set(cartridge, List.of(GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(CHEST))));
        link(context, START, SHOP, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));
        link(context, SHOP, MIDDLE, cartridge);
        link(context, MIDDLE, END, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));

        CowEntity cow = context.spawnEntity(EntityType.COW, START);
        TokenizedEntityInterface token = (TokenizedEntityInterface) cow;
        token.steveparty$setTokenized(true);
        token.steveparty$setTokenOwner(owner.getUuid());
        // Far from the board: the shop opens wherever its player is
        owner.setPosition(context.getAbsolute(new Vec3d(4, 1, -20)));
        return new Board(cow, context.getBlockEntity(SHOP), chest);
    }

    private static void link(TestContext context, BlockPos from, BlockPos to, ItemStack cartridge) {
        BoardSpaceBlockEntity space = context.getBlockEntity(from);
        cartridge.set(ModComponents.DESTINATIONS_COMPONENT,
                new DestinationsComponent(new ArrayList<>(List.of(context.getAbsolutePos(to))), ""));
        space.setStack(0, cartridge);
    }

    /** A Spawn Marker at {@link #MARKER} facing north (the board), linked to the shop space; its absolute position. */
    private static BlockPos marker(TestContext context, Board board) {
        context.setBlockState(MARKER, ModBlocks.SPAWN_MARKER.getDefaultState().with(SpawnMarkerBlock.ROTATION, SpawnMarkerBlock.rotation(Direction.NORTH)));
        BlockPos marker = context.getAbsolutePos(MARKER);
        CartridgeSpawnMarker.set(board.cartridge(), context.getWorld(), marker);
        CartridgeSpawnMarker.own(context.getWorld(), marker, board.shop().getPos());
        return marker;
    }

    /** A Boxed Trader of the world, with a trading stall (two offers) and a stock chest linked by a Shopkeeper Key. */
    private static BoxedTraderEntity worldMerchant(TestContext context) {
        context.setBlockState(STALL, ModBlocks.TRADING_STALL);
        context.setBlockState(STOCK, Blocks.CHEST);
        TradingStallBlockEntity stall = context.getBlockEntity(STALL);
        stall.setStack(0, new ItemStack(Items.EMERALD));
        stall.setStack(18, new ItemStack(Items.DIAMOND));
        stall.setStack(1, new ItemStack(Items.IRON_INGOT, 3));
        stall.setStack(19, new ItemStack(Items.GOLD_INGOT));
        Inventory chest = context.getBlockEntity(STOCK);
        chest.setStack(0, new ItemStack(Items.DIAMOND, 10));
        chest.setStack(1, new ItemStack(Items.GOLD_INGOT, 10));
        BoxedTraderEntity trader = context.spawnEntity(ModEntities.BOXED_TRADER_ENTITY, TRADER);
        VendorLinkPersistentState links = VendorLinkPersistentState.get(context.getWorld().getServer());
        for (BlockPos pos : List.of(STALL, STOCK)) {
            links.linkBlock(trader.getUuid(), GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(pos)));
        }
        return trader;
    }

    /** Runs {@code test} with a mock player (removed on failure; the tests remove it when they end). */
    private static void withPlayer(TestContext context, Consumer<ServerPlayerEntity> test) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            test.accept(player);
        } catch (RuntimeException e) {
            TestPlayers.remove(context, player);
            throw e;
        }
    }

    /** Runs {@code step} in {@code ticks}, logging a failure (the gametest log does not show the assertion messages). */
    private static void later(TestContext context, long ticks, Runnable step) {
        context.waitAndRun(ticks, () -> {
            try {
                step.run();
            } catch (RuntimeException e) {
                Steveparty.LOGGER.error("Shop stop test failed: {}", e.getMessage());
                throw e;
            }
        });
    }

    private static BlockPos spaceOf(CowEntity cow) {
        BoardSpaceBlockEntity space = BoardSpaces.boardSpaceOf(cow);
        return space == null ? null : space.getPos();
    }

    private static boolean near(Vec3d a, Vec3d b) {
        return a.squaredDistanceTo(b) < 0.01;
    }

    /**
     * The token waits on the shop space with {@code steps} steps left, the owner has the shop screen, the space's merchant
     * is there (a hologram) and glows.
     */
    private static ShopStopScreenHandler assertShopping(TestContext context, Board board, ServerPlayerEntity owner, int steps) {
        context.assertTrue(ShopStops.isShopping(board.token().getUuid()), "the token waits at the shop");
        context.assertEquals(spaceOf(board.token()), context.getAbsolutePos(SHOP), "stopped on the shop space");
        context.assertEquals(board.tokenized().steveparty$getNbSteps(), steps, "steps left");
        context.assertTrue(owner.currentScreenHandler instanceof ShopStopScreenHandler, "the owner has the shop screen");
        BoxedTraderEntity trader = board.trader();
        context.assertTrue(trader != null && !trader.isRemoved(), "the space summoned its merchant");
        context.assertTrue(BoardActors.isBoardActor(trader) && trader.isBoardActor() && trader.isAiDisabled(), "a hologram");
        context.assertTrue(trader.isInvulnerable() && !trader.shouldSave(), "invulnerable, never saved");
        context.assertTrue(trader.hasStatusEffect(StatusEffects.GLOWING), "the merchant is highlighted");
        ShopStopScreenHandler handler = (ShopStopScreenHandler) owner.currentScreenHandler;
        context.assertTrue(handler.getSecondsLeft() > 0, "a countdown runs");
        return handler;
    }

    /** The stop is over: screen closed, merchant gone; then the token is on {@code end} with no step left. */
    private static void assertWentOn(TestContext context, Board board, ServerPlayerEntity owner, BoxedTraderEntity trader,
                                     BlockPos end, Runnable then) {
        context.assertFalse(ShopStops.isShopping(board.token().getUuid()), "the stop is over");
        context.assertFalse(owner.currentScreenHandler instanceof ShopStopScreenHandler, "the shop screen is closed");
        context.assertTrue(trader.isRemoved(), "the merchant is gone");
        later(context, 50, () -> {
            context.assertEquals(spaceOf(board.token()), context.getAbsolutePos(end), "the token walked its remaining steps");
            context.assertEquals(board.tokenized().steveparty$getNbSteps(), 0, "no step left");
            then.run();
        });
    }

    private static void finish(TestContext context, Board board, ServerPlayerEntity owner) {
        board.tokenized().steveparty$setTokenized(false);
        TestPlayers.remove(context, owner);
        context.complete();
    }

    /** Puts {@code price} in the merchant input and takes the result; false if nothing could be taken. */
    private static boolean buyOnce(MerchantScreenHandler handler, ServerPlayerEntity player, ItemStack price) {
        handler.getSlot(0).setStack(price.copy());
        handler.onSlotClick(2, 0, SlotActionType.PICKUP, player);
        boolean bought = !handler.getCursorStack().isEmpty();
        handler.setCursorStack(ItemStack.EMPTY);
        handler.getSlot(0).setStack(ItemStack.EMPTY);
        return bought;
    }

    private static boolean buyOnce(MerchantScreenHandler handler, ServerPlayerEntity player) {
        return buyOnce(handler, player, new ItemStack(Items.EMERALD));
    }

    private static int count(Inventory inventory, net.minecraft.item.Item item) {
        int count = 0;
        for (int i = 0; i < inventory.size(); i++) if (inventory.getStack(i).isOf(item)) count += inventory.getStack(i).getCount();
        return count;
    }

    /** Starts the token's move of {@code steps} from START, then {@code then} once it stopped (40 ticks later). */
    private static void walk(TestContext context, Board board, int steps, Runnable then) {
        later(context, 2, () -> {
            TokenMovementService.moveEntityOnBoard(board.token(), steps);
            later(context, 40, then);
        });
    }

    // ---------------------------------------------------------------- check point: stops the passing tokens

    /**
     * A shop check point stops a passing token: its merchant appears beside the space, facing it, with his own stall
     * (no real one in front); « Buy nothing »: he goes and the token resumes its steps.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "shop_stop_checkPointSummonsTheMerchantBesideTheSpace")
    public void checkPointSummonsTheMerchantBesideTheSpace(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.CHECK_POINT);
            context.expectBlockProperty(SHOP, ABoardSpaceBlock.TILE_TYPE, BoardSpaceType.BOARD_SPACE_SHOP);
            walk(context, board, 2, () -> {
                ShopStopScreenHandler handler = assertShopping(context, board, owner, 2);
                BoxedTraderEntity trader = board.trader();
                context.assertEquals(handler.getRecipes().size(), 1, "the cartridge's offer is sold");
                Vec3d stand = BoardSpaces.standPos(context.getWorld(), context.getAbsolutePos(SHOP));
                double side = Math.hypot(trader.getX() - stand.x, trader.getZ() - stand.z);
                context.assertTrue(Math.abs(side - ShopStops.SIDE) < 0.05, "beside the space, " + side);
                Vec3d toSpace = stand.subtract(trader.getPos()).normalize();
                Vec3d facing = Vec3d.fromPolar(0, trader.getYaw());
                context.assertTrue(facing.x * toSpace.x + facing.z * toSpace.z > 0.95, "facing the space");
                context.assertTrue(trader.getShopStall() == null && trader.bringsOwnStall(), "his own stall: none in front");
                context.assertEquals(trader.getShopItems().size(), 1, "his stall shows what he sells");
                later(context, 20, () -> {
                    context.assertTrue(ShopStops.isShopping(board.token().getUuid()), "still waiting: nothing chosen");
                    handler.onButtonClick(owner, ShopStopScreenHandler.BUY_NOTHING_BUTTON_ID);
                    assertWentOn(context, board, owner, trader, END, () -> finish(context, board, owner));
                });
            });
        });
    }

    /** On the space's Spawn Marker: there, facing its way; a real trading stall in front shows his offers, untouched. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "shop_stop_merchantOnTheMarkerUsesTheStallInFront")
    public void merchantOnTheMarkerUsesTheStallInFront(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.CHECK_POINT);
            BlockPos marker = marker(context, board);
            BlockPos stallPos = MARKER.north();
            context.setBlockState(stallPos, ModBlocks.TRADING_STALL);
            TradingStallBlockEntity stall = context.getBlockEntity(stallPos);
            walk(context, board, 2, () -> {
                assertShopping(context, board, owner, 2);
                BoxedTraderEntity trader = board.trader();
                context.assertTrue(near(trader.getPos(), SpawnMarkerBlock.standPos(marker)), "on the marker");
                context.assertTrue(Math.abs(trader.getYaw() - Direction.NORTH.asRotation()) < 1, "facing its way");
                context.assertEquals(trader.getShopStall(), context.getAbsolutePos(stallPos), "the real stall in front shows his offers");
                context.assertFalse(trader.bringsOwnStall(), "no stall of his own then");
                context.assertTrue(stall.isEmpty(), "the stall's contents untouched");
                owner.closeHandledScreen();
                assertWentOn(context, board, owner, trader, END, () -> {
                    context.assertTrue(stall.isEmpty(), "still untouched");
                    finish(context, board, owner);
                });
            });
        });
    }

    /**
     * The limit (1 by default) is enforced: one purchase, taken from the cartridge's chest, paid into it (no party),
     * no second one, and the stop ends by itself.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "shop_stop_purchaseLimitIsEnforcedAndEndsTheStop")
    public void purchaseLimitIsEnforcedAndEndsTheStop(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.CHECK_POINT);
            walk(context, board, 2, () -> {
                ShopStopScreenHandler handler = assertShopping(context, board, owner, 2);
                BoxedTraderEntity trader = board.trader();
                context.assertEquals(handler.getLimit(), 1, "one purchase by default");
                context.assertTrue(buyOnce(handler, owner), "bought from afar");
                context.assertFalse(buyOnce(handler, owner), "no second purchase");
                context.assertEquals(handler.getPurchases(), 1, "one purchase counted");
                context.assertEquals(count(board.chest(), Items.DIAMOND), 9, "taken from its chest");
                // Its till: the chest outside a party (a party of another test around: its bank)
                PartyControllerEntity party = new BoardShop(context.getWorld(), board.shop().getPos()).party();
                context.assertEquals(count(party != null ? party.getBankItems() : board.chest(), Items.EMERALD), 1, "paid into its till");
                later(context, 2, () -> assertWentOn(context, board, owner, trader, END, () -> finish(context, board, owner)));
            });
        });
    }

    /** The cartridge sets the limit: with 2, two purchases, then the stop ends. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "shop_stop_cartridgeSetsThePurchasesAllowed")
    public void cartridgeSetsThePurchasesAllowed(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.CHECK_POINT);
            ShopCartridgeItem.scroll(owner, board.cartridge(), 1);
            context.assertEquals(ShopCartridgeItem.purchases(board.cartridge()), 2, "sneak + wheel: one more");
            walk(context, board, 2, () -> {
                ShopStopScreenHandler handler = assertShopping(context, board, owner, 2);
                BoxedTraderEntity trader = board.trader();
                context.assertEquals(handler.getLimit(), 2, "two purchases allowed");
                context.assertTrue(buyOnce(handler, owner), "first purchase");
                later(context, 2, () -> {
                    context.assertTrue(ShopStops.isShopping(board.token().getUuid()), "one more purchase allowed: still shopping");
                    context.assertTrue(buyOnce(handler, owner), "second purchase");
                    context.assertFalse(buyOnce(handler, owner), "no third one");
                    later(context, 2, () -> assertWentOn(context, board, owner, trader, END, () -> finish(context, board, owner)));
                });
            });
        });
    }

    /** What its stock lacks is sold out: shown so, not sold; the tile's panel says it too. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "shop_stop_missingStockIsSoldOut")
    public void missingStockIsSoldOut(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.CHECK_POINT);
            board.chest().clear();
            TileInfo info = TileInfos.of(board.shop());
            context.assertTrue(info.lines().stream().anyMatch(line -> line.text().getContent() instanceof TranslatableTextContent content
                    && content.getKey().equals(KEY + "shop.sold_out") && line.icon().isOf(Items.EMERALD)), "the panel: sold out, priced in emeralds");
            walk(context, board, 2, () -> {
                ShopStopScreenHandler handler = assertShopping(context, board, owner, 2);
                BoxedTraderEntity trader = board.trader();
                context.assertTrue(handler.getRecipes().getFirst().isDisabled(), "the offer is sold out");
                context.assertFalse(buyOnce(handler, owner), "nothing to buy");
                context.assertEquals(count(board.chest(), Items.EMERALD), 0, "nothing paid");
                handler.onButtonClick(owner, ShopStopScreenHandler.BUY_NOTHING_BUTTON_ID);
                assertWentOn(context, board, owner, trader, END, () -> finish(context, board, owner));
            });
        });
    }

    /**
     * During a party, without a chest of its own: sold from the party's bank (the Party Controller's), paid into it;
     * the party HUD's live state shows the turn waiting at the shop.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "shop_stop_partySellsFromItsBank")
    public void partySellsFromItsBank(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.CHECK_POINT);
            CartridgeContainers.set(board.cartridge(), List.of());
            BlockPos controllerPos = new BlockPos(8, 1, 8);
            context.setBlockState(controllerPos, ModBlocks.PARTY_CONTROLLER);
            PartyControllerEntity controller = context.getBlockEntity(controllerPos);
            controller.getBankItems().setStack(0, new ItemStack(Items.DIAMOND, 5));
            PartyData data = new PartyData();
            data.addToken(board.token().getUuid());
            data.addStep(new PartyStep());
            data.addStep(new TokenTurnPartyStep(board.token().getUuid(), owner.getUuid()));
            data.addStep(new EndPartyStep(new ArrayList<>(List.of(board.token().getUuid()))));
            controller.setPartyData(data);
            controller.nextStep();
            controller.nextStep();
            walk(context, board, 2, () -> {
                ShopStopScreenHandler handler = assertShopping(context, board, owner, 2);
                var live = PartyLiveData.capture(controller, context.getWorld());
                context.assertTrue(live.shopping(), "the HUD shows the shopping");
                context.assertEquals(live.stepsLeft(), 2, "and the steps left");
                context.assertTrue(buyOnce(handler, owner), "bought");
                context.assertEquals(count(controller.getBankItems(), Items.DIAMOND), 4, "taken from the bank");
                context.assertEquals(count(controller.getBankItems(), Items.EMERALD), 1, "paid into the bank");
                context.assertEquals(count(board.chest(), Items.DIAMOND), 10, "the chest it no longer links is untouched");
                later(context, 2, () -> {
                    context.assertFalse(PartyLiveData.capture(controller, context.getWorld()).shopping(), "the stop is over");
                    controller.getBankItems().clear();
                    context.setBlockState(controllerPos, Blocks.AIR);
                    later(context, 50, () -> finish(context, board, owner));
                });
            });
        });
    }

    /** Closing the screen is « Buy nothing ». */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "shop_stop_closingTheScreenGoesOn")
    public void closingTheScreenGoesOn(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.CHECK_POINT);
            walk(context, board, 2, () -> {
                assertShopping(context, board, owner, 2);
                BoxedTraderEntity trader = board.trader();
                owner.closeHandledScreen();
                assertWentOn(context, board, owner, trader, END, () -> finish(context, board, owner));
            });
        });
    }

    /**
     * Only the owner shops: another player gets no screen, and the hologram takes no click. A merchant of the world
     * nearby is not the shop.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "shop_stop_onlyTheOwnerShopsAtTheSpacesOwnMerchant")
    public void onlyTheOwnerShopsAtTheSpacesOwnMerchant(TestContext context) {
        withPlayer(context, owner -> {
            ServerPlayerEntity stranger = TestPlayers.mock(context);
            Board board = board(context, owner, ModBlocks.CHECK_POINT);
            BoxedTraderEntity world = worldMerchant(context);
            walk(context, board, 2, () -> {
                ShopStopScreenHandler handler = assertShopping(context, board, owner, 2);
                BoxedTraderEntity trader = board.trader();
                context.assertTrue(trader != world, "the space's own merchant, not the one of the world");
                stranger.setPosition(trader.getPos().add(0, 0, 1));
                context.assertFalse(stranger.currentScreenHandler instanceof MerchantScreenHandler, "no shop for the others");
                stranger.interact(trader, Hand.MAIN_HAND);
                context.assertFalse(stranger.currentScreenHandler instanceof MerchantScreenHandler, "a hologram: no click");
                context.assertTrue(owner.currentScreenHandler == handler, "the shopper keeps the screen");
                TestPlayers.remove(context, stranger);
                handler.onButtonClick(owner, ShopStopScreenHandler.BUY_NOTHING_BUTTON_ID);
                assertWentOn(context, board, owner, trader, END, () -> {
                    world.discard();
                    finish(context, board, owner);
                });
            });
        });
    }

    /** Nobody chooses: the time runs out, the merchant goes and the token goes on by itself. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "shop_stop_timeRunsOut")
    public void timeRunsOut(TestContext context) {
        var rule = context.getWorld().getGameRules().get(ModGameRules.SHOP_STOP_SECONDS);
        int before = rule.get();
        rule.set(5, context.getWorld().getServer());
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.CHECK_POINT);
            walk(context, board, 2, () -> {
                rule.set(before, context.getWorld().getServer());
                assertShopping(context, board, owner, 2);
                BoxedTraderEntity trader = board.trader();
                later(context, 5 * 20, () -> assertWentOn(context, board, owner, trader, END, () -> finish(context, board, owner)));
            });
        });
    }

    /** The shopper disconnects: the stop ends, the merchant goes, the token goes on (the game never waits for them). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "shop_stop_ownerLeavingEndsTheStop")
    public void ownerLeavingEndsTheStop(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.CHECK_POINT);
            walk(context, board, 2, () -> {
                assertShopping(context, board, owner, 2);
                BoxedTraderEntity trader = board.trader();
                TestPlayers.remove(context, owner);
                later(context, 3, () -> {
                    context.assertFalse(ShopStops.isShopping(board.token().getUuid()), "the stop is over");
                    context.assertTrue(trader.isRemoved(), "the merchant is gone");
                    later(context, 50, () -> {
                        context.assertEquals(spaceOf(board.token()), context.getAbsolutePos(END), "the token walked its remaining steps");
                        board.tokenized().steveparty$setTokenized(false);
                        context.complete();
                    });
                });
            });
        });
    }

    /** No offer set: no merchant, the token passes through (the panel says « no offer »). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "shop_stop_withoutOfferTheTokenPassesThrough")
    public void withoutOfferTheTokenPassesThrough(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.CHECK_POINT);
            ShopCartridgeItem.setOffers(board.cartridge(), List.of());
            context.assertFalse(ShopCartridgeItem.hasOffers(board.cartridge()), "no offer");
            context.assertTrue(TileInfos.of(board.shop()).lines().stream().anyMatch(line -> line.text().getContent()
                    instanceof TranslatableTextContent content && content.getKey().equals(KEY + "shop.no_offer")), "the panel says so");
            later(context, 2, () -> {
                TokenMovementService.moveEntityOnBoard(board.token(), 2);
                later(context, 60, () -> {
                    context.assertFalse(ShopStops.isShopping(board.token().getUuid()), "no offer: no stop");
                    context.assertTrue(context.getWorld().getEntitiesByClass(BoxedTraderEntity.class,
                            new Box(context.getAbsolutePos(SHOP)).expand(8), e -> true).isEmpty(), "no merchant");
                    context.assertEquals(spaceOf(board.token()), context.getAbsolutePos(END), "passed through");
                    finish(context, board, owner);
                });
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "shop_stop_ownerlessTokenPassesThrough")
    public void ownerlessTokenPassesThrough(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.CHECK_POINT);
            board.tokenized().steveparty$setTokenOwner((UUID) null);
            later(context, 2, () -> {
                TokenMovementService.moveEntityOnBoard(board.token(), 2);
                later(context, 60, () -> {
                    context.assertFalse(ShopStops.isShopping(board.token().getUuid()), "nobody to shop: no stop");
                    context.assertEquals(spaceOf(board.token()), context.getAbsolutePos(END), "passed through");
                    finish(context, board, owner);
                });
            });
        });
    }

    // ---------------------------------------------------------------- tile: landing only

    /** A shop tile does not stop a token passing over it. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "shop_stop_tilePassedOverDoesNotStop")
    public void tilePassedOverDoesNotStop(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.TILE);
            later(context, 2, () -> {
                TokenMovementService.moveEntityOnBoard(board.token(), 3);
                later(context, 70, () -> {
                    context.assertFalse(ShopStops.isShopping(board.token().getUuid()), "passing a shop tile: no stop");
                    context.assertFalse(owner.currentScreenHandler instanceof MerchantScreenHandler, "no shop screen");
                    context.assertEquals(spaceOf(board.token()), context.getAbsolutePos(END), "3 steps walked, the shop tile counted");
                    finish(context, board, owner);
                });
            });
        });
    }

    /** Ending the move on a shop tile opens the shop; the token stays there when the stop ends. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "shop_stop_landingOnATileOpensTheShop")
    public void landingOnATileOpensTheShop(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.TILE);
            walk(context, board, 1, () -> {
                ShopStopScreenHandler handler = assertShopping(context, board, owner, 0);
                BoxedTraderEntity trader = board.trader();
                context.assertTrue(board.shop().getBoardSpaceBehavior().keepsTurn(board.token()), "a party's turn waits for the stop");
                handler.onButtonClick(owner, ShopStopScreenHandler.BUY_NOTHING_BUTTON_ID);
                context.assertFalse(ShopStops.isShopping(board.token().getUuid()), "the stop is over");
                context.assertTrue(trader.isRemoved(), "the merchant is gone");
                context.assertFalse(board.shop().getBoardSpaceBehavior().keepsTurn(board.token()), "the turn can go on");
                later(context, 20, () -> {
                    context.assertEquals(spaceOf(board.token()), context.getAbsolutePos(SHOP), "the token stays on the tile");
                    finish(context, board, owner);
                });
            });
        });
    }

    // ---------------------------------------------------------------- the merchants of the world

    /**
     * A merchant of the world (Shopkeeper Key, stall, stock) is a free shop between players: no purchase limit, every
     * offer of his stall, as many times as the stock allows.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = "shop_stop_worldMerchantsHaveNoLimit")
    public void worldMerchantsHaveNoLimit(TestContext context) {
        withPlayer(context, player -> {
            for (int x = 0; x < 9; x++) {
                for (int z = 0; z < 9; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
            }
            BoxedTraderEntity trader = worldMerchant(context);
            player.setPosition(trader.getPos().add(1, 0, 0));
            trader.interact(player, Hand.MAIN_HAND);
            context.assertTrue(player.currentScreenHandler instanceof MerchantScreenHandler
                    && !(player.currentScreenHandler instanceof ShopStopScreenHandler), "his own shop screen");
            MerchantScreenHandler handler = (MerchantScreenHandler) player.currentScreenHandler;
            context.assertEquals(handler.getRecipes().size(), 2, "both offers of his stall");
            for (int i = 0; i < 3; i++) context.assertTrue(buyOnce(handler, player), "purchase " + (i + 1));
            context.assertTrue(buyOnce(handler, player, new ItemStack(Items.IRON_INGOT, 3)), "and the other offer");
            Inventory stock = context.getBlockEntity(STOCK);
            context.assertEquals(count(stock, Items.DIAMOND), 7, "three diamonds sold");
            context.assertEquals(count(stock, Items.GOLD_INGOT), 9, "one gold ingot sold");
            player.closeHandledScreen();
            trader.discard();
            TestPlayers.remove(context, player);
            context.complete();
        });
    }
}
