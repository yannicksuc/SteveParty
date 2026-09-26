package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.components.BlockOriginComponent;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.ShopLinkComponent;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.HidingTraderEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.persistent_state.VendorLinkPersistentState;
import fr.lordfinn.steveparty.screen_handlers.custom.ShopStopScreenHandler;
import fr.lordfinn.steveparty.service.ShopStops;
import fr.lordfinn.steveparty.service.TokenMovementService;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
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
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Shop stops (Shop Cartridge): a check point stops the tokens passing through, a tile opens the shop on landing only;
 * the nearest merchant is the shop (or the one chosen with the Wrench); the purchases allowed are enforced; « Buy
 * nothing », closing, the time running out or the owner leaving end the stop and the token goes on.
 */
public class ShopStopGameTests implements FabricGameTest {
    /** The board: tile → shop space (a check point or a tile) → tile → tile. */
    private static final BlockPos START = new BlockPos(1, 1, 1), SHOP = new BlockPos(3, 1, 1),
            MIDDLE = new BlockPos(5, 1, 1), END = new BlockPos(7, 1, 1);
    private static final BlockPos STALL = new BlockPos(1, 1, 5), CHEST = new BlockPos(3, 1, 5), TRADER = new BlockPos(6, 1, 5);

    private record Board(CowEntity token, HidingTraderEntity trader, BoardSpaceBlockEntity shop) {
        TokenizedEntityInterface tokenized() {
            return (TokenizedEntityInterface) token;
        }

        ItemStack cartridge() {
            return shop.getStack(0);
        }
    }

    /**
     * Builds the board, the shop space holding a Shop Cartridge ({@code shopBlock}: a check point or a tile) and a
     * merchant selling a diamond for an emerald.
     */
    private static Board board(TestContext context, ServerPlayerEntity owner, Block shopBlock) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
        }
        context.setBlockState(START, ModBlocks.TILE);
        context.setBlockState(SHOP, shopBlock);
        context.setBlockState(MIDDLE, ModBlocks.TILE);
        context.setBlockState(END, ModBlocks.TILE);
        link(context, START, SHOP, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));
        link(context, SHOP, MIDDLE, new ItemStack(ModItems.SHOP_CARTRIDGE));
        link(context, MIDDLE, END, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));

        HidingTraderEntity trader = merchant(context, TRADER, STALL, CHEST);
        CowEntity cow = context.spawnEntity(EntityType.COW, START);
        TokenizedEntityInterface token = (TokenizedEntityInterface) cow;
        token.steveparty$setTokenized(true);
        token.steveparty$setTokenOwner(owner.getUuid());
        // Far from the merchant: the shop opens wherever its player is
        owner.setPosition(context.getAbsolute(new Vec3d(4, 1, -20)));
        return new Board(cow, trader, context.getBlockEntity(SHOP));
    }

    /** A Hiding Trader with a trading stall (a diamond for an emerald) and a stock chest. */
    private static HidingTraderEntity merchant(TestContext context, BlockPos at, BlockPos stallPos, BlockPos chestPos) {
        context.setBlockState(stallPos, ModBlocks.TRADING_STALL);
        context.setBlockState(chestPos, Blocks.CHEST);
        TradingStallBlockEntity stall = context.getBlockEntity(stallPos);
        stall.setStack(0, new ItemStack(Items.EMERALD));
        stall.setStack(18, new ItemStack(Items.DIAMOND));
        Inventory chest = context.getBlockEntity(chestPos);
        chest.setStack(0, new ItemStack(Items.DIAMOND, 10));
        HidingTraderEntity trader = context.spawnEntity(ModEntities.HIDING_TRADER_ENTITY, at);
        VendorLinkPersistentState links = VendorLinkPersistentState.get(context.getWorld().getServer());
        for (BlockPos pos : List.of(stallPos, chestPos)) {
            links.linkBlock(trader.getUuid(), GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(pos)));
        }
        return trader;
    }

    private static void link(TestContext context, BlockPos from, BlockPos to, ItemStack cartridge) {
        BoardSpaceBlockEntity space = context.getBlockEntity(from);
        cartridge.set(ModComponents.DESTINATIONS_COMPONENT,
                new DestinationsComponent(new ArrayList<>(List.of(context.getAbsolutePos(to))), ""));
        space.setStack(0, cartridge);
    }

    /** Runs {@code test} with a mock player (removed on failure; the tests remove it when they end). */
    private static void withPlayer(TestContext context, Consumer<ServerPlayerEntity> test) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        try {
            test.accept(player);
        } catch (RuntimeException e) {
            disconnect(context, player);
            throw e;
        }
    }

    /** Runs {@code step} in {@code ticks}, logging a failure (the gametest log does not show the assertion messages). */
    private static void later(TestContext context, long ticks, Runnable step) {
        context.waitAndRun(ticks, () -> {
            try {
                step.run();
            } catch (RuntimeException e) {
                fr.lordfinn.steveparty.Steveparty.LOGGER.error("Shop stop test failed: {}", e.getMessage());
                throw e;
            }
        });
    }

    private static void disconnect(TestContext context, ServerPlayerEntity player) {
        context.getWorld().getServer().getPlayerManager().remove(player);
    }

    private static BlockPos spaceOf(CowEntity cow) {
        BoardSpaceBlockEntity space = BoardSpaces.boardSpaceOf(cow);
        return space == null ? null : space.getPos();
    }

    /** The token waits on the shop space with {@code steps} steps left, the owner has the shop screen, the merchant glows. */
    private static ShopStopScreenHandler assertShopping(TestContext context, Board board, ServerPlayerEntity owner, int steps) {
        context.assertTrue(ShopStops.isShopping(board.token().getUuid()), "the token waits at the shop");
        context.assertEquals(spaceOf(board.token()), context.getAbsolutePos(SHOP), "stopped on the shop space");
        context.assertEquals(board.tokenized().steveparty$getNbSteps(), steps, "steps left");
        context.assertTrue(owner.currentScreenHandler instanceof ShopStopScreenHandler, "the owner has the shop screen");
        context.assertTrue(board.trader().hasStatusEffect(StatusEffects.GLOWING), "the merchant is highlighted");
        ShopStopScreenHandler handler = (ShopStopScreenHandler) owner.currentScreenHandler;
        context.assertTrue(handler.getSecondsLeft() > 0, "a countdown runs");
        return handler;
    }

    /** The stop is over: screen closed, merchant back to normal; then the token is on {@code end} with no step left. */
    private static void assertWentOn(TestContext context, Board board, ServerPlayerEntity owner, BlockPos end, Runnable then) {
        context.assertFalse(ShopStops.isShopping(board.token().getUuid()), "the stop is over");
        context.assertFalse(owner.currentScreenHandler instanceof ShopStopScreenHandler, "the shop screen is closed");
        context.assertFalse(board.trader().hasStatusEffect(StatusEffects.GLOWING), "the merchant no longer glows");
        later(context, 50, () -> {
            context.assertEquals(spaceOf(board.token()), context.getAbsolutePos(end), "the token walked its remaining steps");
            context.assertEquals(board.tokenized().steveparty$getNbSteps(), 0, "no step left");
            then.run();
        });
    }

    private static void finish(TestContext context, Board board, ServerPlayerEntity owner) {
        board.tokenized().steveparty$setTokenized(false);
        disconnect(context, owner);
        context.complete();
    }

    /** Puts one emerald in the merchant input and takes the result; false if nothing could be taken. */
    private static boolean buyOnce(MerchantScreenHandler handler, ServerPlayerEntity player) {
        handler.getSlot(0).setStack(new ItemStack(Items.EMERALD));
        handler.onSlotClick(2, 0, SlotActionType.PICKUP, player);
        boolean bought = handler.getCursorStack().isOf(Items.DIAMOND);
        handler.setCursorStack(ItemStack.EMPTY);
        handler.getSlot(0).setStack(ItemStack.EMPTY);
        return bought;
    }

    // ---------------------------------------------------------------- check point: stops the passing tokens

    /** A shop check point stops a passing token, turns the check point yellow; « Buy nothing » resumes its steps. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void checkPointStopsThePassingTokenAndBuyNothingGoesOn(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.CHECK_POINT);
            context.expectBlockProperty(SHOP, ABoardSpaceBlock.TILE_TYPE, BoardSpaceType.BOARD_SPACE_SHOP);
            later(context, 2, () -> {
                TokenMovementService.moveEntityOnBoard(board.token(), 2);
                later(context, 40, () -> {
                    ShopStopScreenHandler handler = assertShopping(context, board, owner, 2);
                    context.assertEquals(handler.getRecipes().size(), 1, "the merchant's offer is shown");
                    later(context, 20, () -> {
                        context.assertTrue(ShopStops.isShopping(board.token().getUuid()), "still waiting: nothing chosen");
                        handler.onButtonClick(owner, ShopStopScreenHandler.BUY_NOTHING_BUTTON_ID);
                        assertWentOn(context, board, owner, END, () -> finish(context, board, owner));
                    });
                });
            });
        });
    }

    /** The limit (1 by default) is enforced: one purchase, no second one, and the stop ends by itself. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void purchaseLimitIsEnforcedAndEndsTheStop(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.CHECK_POINT);
            later(context, 2, () -> {
                TokenMovementService.moveEntityOnBoard(board.token(), 2);
                later(context, 40, () -> {
                    ShopStopScreenHandler handler = assertShopping(context, board, owner, 2);
                    context.assertEquals(handler.getLimit(), 1, "one purchase by default");
                    context.assertTrue(buyOnce(handler, owner), "bought from afar");
                    context.assertFalse(buyOnce(handler, owner), "no second purchase");
                    context.assertEquals(handler.getPurchases(), 1, "one purchase counted");
                    later(context, 2, () -> assertWentOn(context, board, owner, END, () -> finish(context, board, owner)));
                });
            });
        });
    }

    /** The cartridge sets the limit: with 2, two purchases, then the stop ends. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void cartridgeSetsThePurchasesAllowed(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.CHECK_POINT);
            ShopCartridgeItem.scroll(owner, board.cartridge(), 1);
            context.assertEquals(ShopCartridgeItem.purchases(board.cartridge()), 2, "sneak + wheel: one more");
            later(context, 2, () -> {
                TokenMovementService.moveEntityOnBoard(board.token(), 2);
                later(context, 40, () -> {
                    ShopStopScreenHandler handler = assertShopping(context, board, owner, 2);
                    context.assertEquals(handler.getLimit(), 2, "two purchases allowed");
                    context.assertTrue(buyOnce(handler, owner), "first purchase");
                    later(context, 2, () -> {
                        context.assertTrue(ShopStops.isShopping(board.token().getUuid()), "one more purchase allowed: still shopping");
                        context.assertTrue(buyOnce(handler, owner), "second purchase");
                        context.assertFalse(buyOnce(handler, owner), "no third one");
                        later(context, 2, () -> assertWentOn(context, board, owner, END, () -> finish(context, board, owner)));
                    });
                });
            });
        });
    }

    /** Closing the screen is « Buy nothing ». */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void closingTheScreenGoesOn(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.CHECK_POINT);
            later(context, 2, () -> {
                TokenMovementService.moveEntityOnBoard(board.token(), 2);
                later(context, 40, () -> {
                    assertShopping(context, board, owner, 2);
                    owner.closeHandledScreen();
                    assertWentOn(context, board, owner, END, () -> finish(context, board, owner));
                });
            });
        });
    }

    /** Only the owner shops: another player gets no screen and cannot take the merchant meanwhile. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void onlyTheOwnerShops(TestContext context) {
        withPlayer(context, owner -> {
            ServerPlayerEntity stranger = context.createMockCreativeServerPlayerInWorld();
            Board board = board(context, owner, ModBlocks.CHECK_POINT);
            stranger.setPosition(board.trader().getPos().add(0, 0, 1));
            later(context, 2, () -> {
                TokenMovementService.moveEntityOnBoard(board.token(), 2);
                later(context, 40, () -> {
                    ShopStopScreenHandler handler = assertShopping(context, board, owner, 2);
                    context.assertFalse(stranger.currentScreenHandler instanceof MerchantScreenHandler, "no shop for the others");
                    board.trader().interact(stranger, Hand.MAIN_HAND);
                    context.assertFalse(stranger.currentScreenHandler instanceof MerchantScreenHandler, "the merchant is busy with the shopper");
                    context.assertTrue(owner.currentScreenHandler == handler, "the shopper keeps the screen");
                    disconnect(context, stranger);
                    handler.onButtonClick(owner, ShopStopScreenHandler.BUY_NOTHING_BUTTON_ID);
                    assertWentOn(context, board, owner, END, () -> finish(context, board, owner));
                });
            });
        });
    }

    /** Nobody chooses: the time runs out and the token goes on by itself. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void timeRunsOut(TestContext context) {
        var rule = context.getWorld().getGameRules().get(ShopStops.SHOP_SECONDS);
        int before = rule.get();
        rule.set(5, context.getWorld().getServer());
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.CHECK_POINT);
            later(context, 2, () -> {
                TokenMovementService.moveEntityOnBoard(board.token(), 2);
                later(context, 40, () -> {
                    rule.set(before, context.getWorld().getServer());
                    assertShopping(context, board, owner, 2);
                    later(context, 5 * 20, () -> assertWentOn(context, board, owner, END, () -> finish(context, board, owner)));
                });
            });
        });
    }

    /** The shopper disconnects: the stop ends and the token goes on (the game never waits for them). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void ownerLeavingEndsTheStop(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.CHECK_POINT);
            later(context, 2, () -> {
                TokenMovementService.moveEntityOnBoard(board.token(), 2);
                later(context, 40, () -> {
                    assertShopping(context, board, owner, 2);
                    disconnect(context, owner);
                    later(context, 3, () -> {
                        context.assertFalse(ShopStops.isShopping(board.token().getUuid()), "the stop is over");
                        later(context, 50, () -> {
                            context.assertEquals(spaceOf(board.token()), context.getAbsolutePos(END), "the token walked its remaining steps");
                            board.tokenized().steveparty$setTokenized(false);
                            context.complete();
                        });
                    });
                });
            });
        });
    }

    /** No merchant around, or a token without owner: the token passes through. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void withoutMerchantOrOwnerTheTokenPassesThrough(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.CHECK_POINT);
            board.trader().discard();
            later(context, 2, () -> {
                TokenMovementService.moveEntityOnBoard(board.token(), 2);
                later(context, 60, () -> {
                    context.assertFalse(ShopStops.isShopping(board.token().getUuid()), "no merchant: no stop");
                    context.assertEquals(spaceOf(board.token()), context.getAbsolutePos(END), "passed through");
                    finish(context, board, owner);
                });
            });
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
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
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
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
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void landingOnATileOpensTheShop(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.TILE);
            later(context, 2, () -> {
                TokenMovementService.moveEntityOnBoard(board.token(), 1);
                later(context, 40, () -> {
                    ShopStopScreenHandler handler = assertShopping(context, board, owner, 0);
                    context.assertTrue(board.shop().getBoardSpaceBehavior().keepsTurn(board.token()), "a party's turn waits for the stop");
                    handler.onButtonClick(owner, ShopStopScreenHandler.BUY_NOTHING_BUTTON_ID);
                    context.assertFalse(ShopStops.isShopping(board.token().getUuid()), "the stop is over");
                    context.assertFalse(board.shop().getBoardSpaceBehavior().keepsTurn(board.token()), "the turn can go on");
                    later(context, 20, () -> {
                        context.assertEquals(spaceOf(board.token()), context.getAbsolutePos(SHOP), "the token stays on the tile");
                        finish(context, board, owner);
                    });
                });
            });
        });
    }

    // ---------------------------------------------------------------- which merchant

    /**
     * The nearest merchant is the shop, the distance counted to him or to his nearest stall; a trader without stall
     * sells nothing and is ignored; the Wrench's choice wins.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void nearestMerchantIsTheShop(TestContext context) {
        withPlayer(context, player -> {
            Board board = board(context, player, ModBlocks.CHECK_POINT);
            // The board's merchant: stall at (1,1,5), 4.5 blocks away. A closer one by his stall, a closer one without stall
            HidingTraderEntity byStall = merchant(context, new BlockPos(8, 1, 8), new BlockPos(3, 1, 3), new BlockPos(8, 1, 7));
            HidingTraderEntity noStall = context.spawnEntity(ModEntities.HIDING_TRADER_ENTITY, new BlockPos(3, 1, 2));
            BlockPos shop = context.getAbsolutePos(SHOP);
            HidingTraderEntity found = ShopStops.findShop(context.getWorld(), shop, board.cartridge());
            context.assertTrue(found == byStall, "the merchant whose stall is nearest");
            context.assertTrue(found != noStall, "a trader without stall is no shop");
            board.cartridge().set(ModComponents.SHOP_LINK, new ShopLinkComponent(board.trader().getUuid(), board.trader().getBlockPos()));
            context.assertTrue(ShopStops.findShop(context.getWorld(), shop, board.cartridge()) == board.trader(), "the Wrench's choice wins");
            finish(context, board, player);
        });
    }

    /** The Wrench chooses the shop: origin on the shop space, click a stall (again: back to the nearest) or a trader. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void wrenchChoosesTheShop(TestContext context) {
        withPlayer(context, player -> {
            Board board = board(context, player, ModBlocks.CHECK_POINT);
            ItemStack wrench = new ItemStack(ModItems.WRENCH);
            wrench.set(ModComponents.BLOCK_ORIGIN_COMPONENT, new BlockOriginComponent(context.getAbsolutePos(SHOP),
                    context.getWorld().getRegistryKey().getValue().toString()));
            player.setStackInHand(Hand.MAIN_HAND, wrench);
            BlockPos stall = context.getAbsolutePos(STALL);
            ActionResult result = UseBlockCallback.EVENT.invoker().interact(player, context.getWorld(), Hand.MAIN_HAND,
                    new BlockHitResult(stall.toCenterPos(), Direction.UP, stall, false));
            context.assertEquals(result, ActionResult.SUCCESS, "the click is the Wrench's");
            ShopLinkComponent link = board.cartridge().get(ModComponents.SHOP_LINK);
            context.assertTrue(link != null && link.trader().equals(board.trader().getUuid()), "the stall's merchant is the shop");
            context.assertEquals(link.anchor(), stall, "shown at the stall");
            later(context, 10, () -> {
                UseBlockCallback.EVENT.invoker().interact(player, context.getWorld(), Hand.MAIN_HAND,
                        new BlockHitResult(stall.toCenterPos(), Direction.UP, stall, false));
                context.assertTrue(board.cartridge().get(ModComponents.SHOP_LINK) == null, "the same shop again: back to the nearest");
                ActionResult onTrader = UseEntityCallback.EVENT.invoker().interact(player, context.getWorld(), Hand.MAIN_HAND, board.trader(), null);
                context.assertEquals(onTrader, ActionResult.SUCCESS, "a click on the trader is the Wrench's");
                ShopLinkComponent direct = board.cartridge().get(ModComponents.SHOP_LINK);
                context.assertTrue(direct != null && direct.trader().equals(board.trader().getUuid()), "the clicked trader is the shop");
                finish(context, board, player);
            });
        });
    }
}
