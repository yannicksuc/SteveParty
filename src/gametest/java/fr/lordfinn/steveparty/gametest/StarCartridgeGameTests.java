package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.StarSettingsComponent;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.StarCartridgeItem;
import fr.lordfinn.steveparty.service.DiceRollEffects;
import fr.lordfinn.steveparty.service.PartyStars;
import fr.lordfinn.steveparty.service.TokenMovementService;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The Star Cartridge: the party's star stands on an active star space (drawn when the party starts); a token reaching
 * it may buy it, and it then goes to another star space; a star space switched off sends it elsewhere (or keeps it, if
 * its cartridge says so); no active star space: it hides until one is switched on; the Skeleton Key does not walk past
 * it.
 * <p>
 * Each test runs in a batch of its own: the star spaces of a party are looked for within
 * {@link PartyControllerEntity#START_TILES_SEARCH_RADIUS} blocks of its controller, and the tests of a batch stand side
 * by side, so another test's star spaces would be found. Each test also takes its star spaces away when it ends, for
 * the batches after it.
 */
public class StarCartridgeGameTests implements FabricGameTest {
    /** The board: tile → star tile → tile, and two more star tiles off the path. */
    private static final BlockPos START = new BlockPos(1, 1, 1), STAR = new BlockPos(3, 1, 1), END = new BlockPos(5, 1, 1);
    private static final BlockPos OTHER = new BlockPos(1, 1, 5), ANOTHER = new BlockPos(5, 1, 5);
    private static final BlockPos CONTROLLER = new BlockPos(8, 1, 8);
    /** The party's bank: a chest holding the stars the star spaces sell, receiving the coins paid. */
    private static final BlockPos BANK = new BlockPos(8, 1, 3);
    /** The stars in the bank at the start. */
    private static final int BANK_STARS = 2;
    private static final int PRICE = StarSettingsComponent.DEFAULT_PRICE;

    private record Board(CowEntity token, PartyControllerEntity party, List<BlockPos> starSpaces,
                         net.minecraft.block.entity.ChestBlockEntity bank) {
        TokenizedEntityInterface tokenized() {
            return (TokenizedEntityInterface) token;
        }
    }

    private static void floor(TestContext context) {
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    /** A tile at {@code pos} holding {@code cartridge} in its first slot, linked to {@code to} if not null. */
    private static BoardSpaceBlockEntity space(TestContext context, BlockPos pos, Block block, ItemStack cartridge, BlockPos to) {
        context.setBlockState(pos, block);
        BoardSpaceBlockEntity space = context.getBlockEntity(pos);
        if (to != null) {
            cartridge.set(ModComponents.DESTINATIONS_COMPONENT,
                    new DestinationsComponent(new ArrayList<>(List.of(context.getAbsolutePos(to))), ""));
        }
        space.setStack(0, cartridge);
        return space;
    }

    /** A party of one token, started up to its first turn (the controller off the board). */
    private static PartyControllerEntity party(TestContext context, CowEntity token, ServerPlayerEntity owner) {
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        PartyData data = new PartyData();
        data.addToken(token.getUuid());
        data.addStep(new PartyStep());
        // Two turns: the party still runs once the first move is over
        data.addStep(new TokenTurnPartyStep(token.getUuid(), owner.getUuid()));
        data.addStep(new TokenTurnPartyStep(token.getUuid(), owner.getUuid()));
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(token.getUuid()))));
        controller.setPartyData(data);
        controller.nextStep();
        controller.nextStep();
        return controller;
    }

    /**
     * The board (START → STAR → END, STAR and the two others holding a Star Cartridge), a token of {@code owner} on
     * START, its party started, and the star on STAR.
     */
    private static Board board(TestContext context, ServerPlayerEntity owner, Block starBlock) {
        floor(context);
        space(context, START, ModBlocks.TILE, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), STAR);
        space(context, STAR, starBlock, new ItemStack(ModItems.STAR_CARTRIDGE), END);
        space(context, END, ModBlocks.TILE, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), null);
        space(context, OTHER, ModBlocks.TILE, new ItemStack(ModItems.STAR_CARTRIDGE), null);
        space(context, ANOTHER, ModBlocks.TILE, new ItemStack(ModItems.STAR_CARTRIDGE), null);
        CowEntity cow = context.spawnEntity(EntityType.COW, START);
        TokenizedEntityInterface token = (TokenizedEntityInterface) cow;
        token.steveparty$setTokenized(true);
        token.steveparty$setTokenOwner(owner.getUuid());
        owner.setPosition(context.getAbsolute(new Vec3d(4, 1, -10)));
        PartyControllerEntity controller = party(context, cow, owner);
        controller.setStarSpace(context.getAbsolutePos(STAR));
        net.minecraft.block.entity.ChestBlockEntity bank = BankFixtures.stock(context, controller, BANK, 0, BANK_STARS);
        return new Board(cow, controller, List.of(STAR, OTHER, ANOTHER), bank);
    }

    private static int count(ServerPlayerEntity player, ItemStack template) {
        return InventoryUtils.count(player.getInventory(), template);
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
                fr.lordfinn.steveparty.Steveparty.LOGGER.error("Star cartridge test failed: {}", e.getMessage());
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

    /** Ends the test: no star space left for the next batches, no party, no player. */
    private static void finish(TestContext context, List<BlockPos> starSpaces, ServerPlayerEntity player) {
        for (BlockPos pos : starSpaces) context.setBlockState(pos, Blocks.AIR);
        context.setBlockState(CONTROLLER, Blocks.AIR);
        context.setBlockState(BANK, Blocks.AIR);
        if (player != null) disconnect(context, player);
        context.complete();
    }

    // ---------------------------------------------------------------- the cartridge

    /** Yellow tile and check point (yellow is no longer the shop's), its landing, its comparator level, its menu defaults. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "star_cartridge_makesAYellowStarSpace")
    public void makesAYellowStarSpace(TestContext context) {
        floor(context);
        BoardSpaceBlockEntity tile = space(context, STAR, ModBlocks.TILE, new ItemStack(ModItems.STAR_CARTRIDGE), null);
        BoardSpaceBlockEntity checkPoint = space(context, END, ModBlocks.CHECK_POINT, new ItemStack(ModItems.STAR_CARTRIDGE), null);
        context.assertEquals(tile.getCachedState().get(ABoardSpaceBlock.TILE_TYPE), BoardSpaceType.TILE_STAR, "a star tile");
        context.assertEquals(checkPoint.getCachedState().get(ABoardSpaceBlock.TILE_TYPE), BoardSpaceType.TILE_STAR, "a star check point");
        context.assertEquals(TileFeedback.tileColor(tile), StarCartridgeItem.COLOR, "a yellow face");
        context.assertFalse(ShopCartridgeItem.COLOR == StarCartridgeItem.COLOR, "the shop is no longer yellow");
        context.assertEquals(TileFeedback.landingOf(tile), TileFeedback.Landing.STAR, "its landing");
        context.assertEquals(tile.getBoardSpaceBehavior().comparatorLevel(tile, tile.getStack(0)),
                fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity.LEVEL_STAR, "its comparator level");
        StarSettingsComponent settings = StarCartridgeItem.settings(tile.getStack(0));
        context.assertEquals(settings.price(), 20, "20 coins by default");
        context.assertTrue(settings.onPass() && settings.relocate(), "sold in passing, leaves a switched-off space");
        finish(context, List.of(STAR, END), null);
    }

    // ---------------------------------------------------------------- where the star stands

    /** When the party starts, the star stands on an active star space, never on a switched-off one. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "star_cartridge_startsOnAnActiveStarSpace")
    public void startsOnAnActiveStarSpace(TestContext context) {
        withPlayer(context, owner -> {
            floor(context);
            space(context, STAR, ModBlocks.TILE, new ItemStack(ModItems.STAR_CARTRIDGE), null);
            // Switched off: its Star Cartridge is in a slot that is not the active one
            context.setBlockState(OTHER, ModBlocks.ADVANCED_TILE);
            BoardSpaceBlockEntity off = context.getBlockEntity(OTHER);
            off.setStack(1, new ItemStack(ModItems.STAR_CARTRIDGE));
            context.assertEquals(off.getCachedState().get(ABoardSpaceBlock.TILE_TYPE), BoardSpaceType.DEFAULT, "a switched-off star space");
            CowEntity cow = context.spawnEntity(EntityType.COW, START);
            PartyControllerEntity controller = party(context, cow, owner);
            for (int i = 0; i < 12; i++) {
                PartyStars.onPartyStarted(controller, context.getWorld());
                context.assertEquals(context.getAbsolutePos(STAR), controller.getStarSpace(), "on the active star space");
            }
            finish(context, List.of(STAR, OTHER), owner);
        });
    }

    /** A star space switched off (redstone) while it holds the star sends it to another one; with the option off, it stays. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "star_cartridge_switchingOffMovesTheStar")
    public void switchingOffMovesTheStar(TestContext context) {
        withPlayer(context, owner -> {
            floor(context);
            context.setBlockState(STAR, ModBlocks.ADVANCED_TILE);
            BoardSpaceBlockEntity star = context.getBlockEntity(STAR);
            star.setStack(0, new ItemStack(ModItems.STAR_CARTRIDGE));
            space(context, OTHER, ModBlocks.TILE, new ItemStack(ModItems.STAR_CARTRIDGE), null);
            // Waits on its space when switched off
            context.setBlockState(ANOTHER, ModBlocks.ADVANCED_TILE);
            BoardSpaceBlockEntity stays = context.getBlockEntity(ANOTHER);
            ItemStack waiting = new ItemStack(ModItems.STAR_CARTRIDGE);
            waiting.set(ModComponents.STAR_SETTINGS, StarSettingsComponent.DEFAULT.withRelocate(false));
            stays.setStack(0, waiting);
            CowEntity cow = context.spawnEntity(EntityType.COW, START);
            PartyControllerEntity controller = party(context, cow, owner);
            controller.setStarSpace(context.getAbsolutePos(STAR));

            context.setBlockState(STAR.west(), Blocks.REDSTONE_BLOCK); // power 15: slot 15, empty
            later(context, 2, () -> {
                context.assertEquals(star.getCachedState().get(ABoardSpaceBlock.TILE_TYPE), BoardSpaceType.DEFAULT, "switched off");
                BlockPos moved = controller.getStarSpace();
                context.assertTrue(context.getAbsolutePos(OTHER).equals(moved) || context.getAbsolutePos(ANOTHER).equals(moved),
                        "the star went to another star space, got " + moved);
                controller.setStarSpace(context.getAbsolutePos(ANOTHER));
                context.setBlockState(ANOTHER.east(), Blocks.REDSTONE_BLOCK);
                later(context, 30, () -> {
                    context.assertEquals(stays.getCachedState().get(ABoardSpaceBlock.TILE_TYPE), BoardSpaceType.DEFAULT, "switched off too");
                    context.assertEquals(context.getAbsolutePos(ANOTHER), controller.getStarSpace(), "its cartridge keeps the star there");
                    finish(context, List.of(STAR, OTHER, ANOTHER), owner);
                });
            });
        });
    }

    /** No active star space: the star hides; a star space switched on again gets it. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "star_cartridge_noActiveStarSpaceHidesTheStar")
    public void noActiveStarSpaceHidesTheStar(TestContext context) {
        withPlayer(context, owner -> {
            floor(context);
            context.setBlockState(STAR, ModBlocks.ADVANCED_TILE);
            BoardSpaceBlockEntity star = context.getBlockEntity(STAR);
            star.setStack(0, new ItemStack(ModItems.STAR_CARTRIDGE));
            CowEntity cow = context.spawnEntity(EntityType.COW, START);
            PartyControllerEntity controller = party(context, cow, owner);
            controller.setStarSpace(context.getAbsolutePos(STAR));

            context.setBlockState(STAR.west(), Blocks.REDSTONE_BLOCK);
            later(context, 25, () -> {
                context.assertTrue(controller.getStarSpace() == null, "hidden: no star space is on, got " + controller.getStarSpace());
                context.setBlockState(STAR.west(), Blocks.AIR);
                later(context, 2, () -> {
                    context.assertEquals(star.getCachedState().get(ABoardSpaceBlock.TILE_TYPE), BoardSpaceType.TILE_STAR, "switched on again");
                    context.assertEquals(context.getAbsolutePos(STAR), controller.getStarSpace(), "the star came back");
                    finish(context, List.of(STAR), owner);
                });
            });
        });
    }

    // ---------------------------------------------------------------- buying it

    /** A token passing over the star stops; its owner buys it: coins paid, a star given, the star moves elsewhere, the token goes on. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "star_cartridge_buyingMovesTheStarElsewhere")
    public void buyingMovesTheStarElsewhere(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.TILE);
            ItemStack coin = board.party().getCurrency(fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency.COIN);
            ItemStack starItem = board.party().getCurrency(fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency.STAR);
            InventoryUtils.giveOrDrop(owner, coin, PRICE + 5);
            later(context, 2, () -> {
                TokenMovementService.moveEntityOnBoard(board.token(), 2);
                later(context, 40, () -> {
                    context.assertTrue(PartyStars.isDeciding(board.token().getUuid()), "the token waits on the star");
                    context.assertEquals(spaceOf(board.token()), context.getAbsolutePos(STAR), "stopped on the star");
                    context.assertEquals(board.tokenized().steveparty$getNbSteps(), 1, "its step left kept");
                    context.assertTrue(PartyStars.decide(owner, true), "bought");
                    context.assertEquals(count(owner, coin), 5, "the price paid");
                    context.assertEquals(count(owner, starItem), 1, "a star given");
                    context.assertEquals(BankFixtures.count(board.bank(), starItem), BANK_STARS - 1, "the star taken from the bank");
                    context.assertEquals(BankFixtures.count(board.bank(), coin), PRICE, "the coins paid into the bank");
                    BlockPos moved = board.party().getStarSpace();
                    context.assertTrue(context.getAbsolutePos(OTHER).equals(moved) || context.getAbsolutePos(ANOTHER).equals(moved),
                            "the star went to another star space, got " + moved);
                    context.assertFalse(PartyStars.isDeciding(board.token().getUuid()), "the choice is over");
                    later(context, 50, () -> {
                        context.assertEquals(spaceOf(board.token()), context.getAbsolutePos(END), "the token walked its step left");
                        board.tokenized().steveparty$setTokenized(false);
                        finish(context, board.starSpaces(), owner);
                    });
                });
            });
        });
    }

    /** No star left in the bank: nothing to sell, the token goes on, nothing paid. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "star_cartridge_emptyBankSellsNothing")
    public void emptyBankSellsNothing(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.TILE);
            board.bank().clear();
            ItemStack coin = board.party().getCurrency(fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency.COIN);
            ItemStack starItem = board.party().getCurrency(fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency.STAR);
            InventoryUtils.giveOrDrop(owner, coin, PRICE + 5);
            later(context, 2, () -> {
                context.assertFalse(PartyStars.buy(board.party(), context.getWorld(), owner, context.getAbsolutePos(STAR), PRICE),
                        "no star to buy");
                TokenMovementService.moveEntityOnBoard(board.token(), 2);
                later(context, 50, () -> {
                    context.assertFalse(PartyStars.isDeciding(board.token().getUuid()), "no choice");
                    context.assertEquals(spaceOf(board.token()), context.getAbsolutePos(END), "the token went on");
                    context.assertEquals(count(owner, coin), PRICE + 5, "nothing paid");
                    context.assertEquals(count(owner, starItem), 0, "no star created");
                    context.assertEquals(context.getAbsolutePos(STAR), board.party().getStarSpace(), "the star stays");
                    board.tokenized().steveparty$setTokenized(false);
                    finish(context, board.starSpaces(), owner);
                });
            });
        });
    }

    /** Too few coins: the token does not stop, the star stays. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "star_cartridge_tooFewCoinsGoesOn")
    public void tooFewCoinsGoesOn(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.TILE);
            ItemStack coin = board.party().getCurrency(fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency.COIN);
            InventoryUtils.giveOrDrop(owner, coin, PRICE - 1);
            later(context, 2, () -> {
                TokenMovementService.moveEntityOnBoard(board.token(), 2);
                later(context, 50, () -> {
                    context.assertFalse(PartyStars.isDeciding(board.token().getUuid()), "no choice");
                    context.assertEquals(spaceOf(board.token()), context.getAbsolutePos(END), "the token went on");
                    context.assertEquals(count(owner, coin), PRICE - 1, "nothing paid");
                    context.assertEquals(context.getAbsolutePos(STAR), board.party().getStarSpace(), "the star stays");
                    board.tokenized().steveparty$setTokenized(false);
                    finish(context, board.starSpaces(), owner);
                });
            });
        });
    }

    /** The Skeleton Key walks past Stop spaces and shop check points, not past the star; « No thanks » keeps the coins. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "star_cartridge_skeletonKeyDoesNotSkipTheStar")
    public void skeletonKeyDoesNotSkipTheStar(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner, ModBlocks.CHECK_POINT);
            ItemStack coin = board.party().getCurrency(fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency.COIN);
            InventoryUtils.giveOrDrop(owner, coin, PRICE);
            later(context, 2, () -> {
                DiceRollEffects.setMoveModules(board.token(), Map.of(DiceModules.SKELETON_KEY, 1));
                context.assertTrue(DiceRollEffects.ignoresStops(board.token()), "the Skeleton Key is on");
                TokenMovementService.moveEntityOnBoard(board.token(), 2);
                later(context, 40, () -> {
                    context.assertTrue(PartyStars.isDeciding(board.token().getUuid()), "stopped on the star check point anyway");
                    context.assertEquals(spaceOf(board.token()), context.getAbsolutePos(STAR), "on the star");
                    context.assertTrue(PartyStars.decide(owner, false), "no thanks");
                    context.assertEquals(count(owner, coin), PRICE, "the coins kept");
                    context.assertEquals(context.getAbsolutePos(STAR), board.party().getStarSpace(), "the star stays");
                    later(context, 60, () -> {
                        context.assertEquals(spaceOf(board.token()), context.getAbsolutePos(END), "the token went on");
                        board.tokenized().steveparty$setTokenized(false);
                        DiceRollEffects.clearMoveModules(board.token().getUuid());
                        finish(context, board.starSpaces(), owner);
                    });
                });
            });
        });
    }
}
