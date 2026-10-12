package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlock;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.StarSettingsComponent;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestBank;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeSpawnMarker;
import fr.lordfinn.steveparty.service.BoardActors;
import fr.lordfinn.steveparty.service.PartyStars;
import fr.lordfinn.steveparty.service.StarHosts;
import fr.lordfinn.steveparty.service.TokenMovementService;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static fr.lordfinn.steveparty.gametest.kit.TestAsserts.count;
import java.util.function.BooleanSupplier;

/**
 * The star space's host (StarHosts): a Mula comes with the star when a token reaches it, a hologram board actor
 * (invulnerable, never saved) holding the party's star item, on the space's Spawn Bubble when its cartridge is linked
 * to one, else floating beside the space; it presents the star while the player chooses, dances and gives it when it
 * is bought, is disappointed when it is not (or the player is short of coins), and is removed at the end of its show.
 * The purchase itself is unchanged (StarCartridgeGameTests).
 * <p>
 * Each test runs in a batch of its own and takes its star spaces away when it ends (the star spaces of a party are
 * those of its board).
 */
public class StarHostGameTests implements SteveGameTest {
    private static final BlockPos START = new BlockPos(1, 1, 1), STAR = new BlockPos(3, 1, 1), END = new BlockPos(5, 1, 1);
    private static final BlockPos OTHER = new BlockPos(1, 1, 5);
    private static final BlockPos CONTROLLER = new BlockPos(8, 1, 8), BANK = new BlockPos(8, 1, 3);
    private static final BlockPos MARKER = new BlockPos(3, 1, 4);
    private static final int PRICE = StarSettingsComponent.DEFAULT_PRICE;
    /** Its whole show at most, once the choice is made (dance, gift and exit; or disappointment and exit). */
    private static final int GOODBYE = 160;

    private record Board(CowEntity token, PartyControllerEntity party, BoardSpaceBlockEntity star) {
    }

    private static BoardSpaceBlockEntity space(TestContext context, BlockPos pos, ItemStack cartridge, BlockPos to) {
        context.setBlockState(pos, ModBlocks.TILE);
        BoardSpaceBlockEntity space = context.getBlockEntity(pos);
        if (to != null) {
            cartridge.set(ModComponents.DESTINATIONS_COMPONENT,
                    new DestinationsComponent(new ArrayList<>(List.of(context.getAbsolutePos(to))), ""));
        }
        space.setStack(0, cartridge);
        return space;
    }

    /** START → STAR → END and another star space (OTHER), a token of {@code owner} on START, its party started, the star on STAR. */
    private static Board board(TestContext context, ServerPlayerEntity owner) {
        TestBoards.floor(context, 10);
        space(context, START, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), STAR);
        BoardSpaceBlockEntity star = space(context, STAR, new ItemStack(ModItems.STAR_CARTRIDGE), END);
        space(context, END, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), null);
        space(context, OTHER, new ItemStack(ModItems.STAR_CARTRIDGE), START);
        CowEntity cow = context.spawnEntity(EntityType.COW, START);
        TokenizedEntityInterface token = (TokenizedEntityInterface) cow;
        token.steveparty$setTokenized(true);
        token.steveparty$setTokenOwner(owner.getUuid());
        owner.setPosition(context.getAbsolute(new Vec3d(4, 1, -10)));
        context.setBlockState(CONTROLLER, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        PartyData data = new PartyData();
        data.addToken(cow.getUuid());
        data.addStep(new PartyStep());
        data.addStep(new TokenTurnPartyStep(cow.getUuid(), owner.getUuid()));
        data.addStep(new TokenTurnPartyStep(cow.getUuid(), owner.getUuid()));
        data.addStep(new EndPartyStep(new ArrayList<>(List.of(cow.getUuid()))));
        controller.setPartyData(data);
        controller.nextStep();
        controller.nextStep();
        controller.setStarSpace(context.getAbsolutePos(STAR));
        TestBank.stock(context, controller, BANK, 0, 2);
        return new Board(cow, controller, star);
    }

    private static void withPlayer(TestContext context, Consumer<ServerPlayerEntity> test) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            test.accept(player);
        } catch (RuntimeException e) {
            TestPlayers.remove(context, player);
            Steveparty.LOGGER.error("Star host test failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Runs {@code then} as soon as {@code condition} holds (checked every tick, failing after {@code ticks}), logging a
     * failure (the gametest log does not show the assertion messages).
     */
    private static void when(TestContext context, BooleanSupplier condition, int ticks, String what, Runnable then) {
        try {
            if (condition.getAsBoolean()) {
                then.run();
                return;
            }
            context.assertTrue(ticks > 0, "timed out: " + what);
        } catch (RuntimeException e) {
            Steveparty.LOGGER.error("Star host test failed: {}", e.getMessage());
            throw e;
        }
        context.waitAndRun(1, () -> when(context, condition, ticks - 1, what, then));
    }

    /** Runs {@code step} next tick, logging a failure. */
    private static void later(TestContext context, Runnable step) {
        context.waitAndRun(1, () -> when(context, () -> true, 0, "", step));
    }

    /** Ends the test: no star space left for the next batches, no party, no player, the token a cow again. */
    private static void finish(TestContext context, Board board, ServerPlayerEntity player) {
        ((TokenizedEntityInterface) board.token()).steveparty$setTokenized(false);
        for (BlockPos pos : List.of(STAR, OTHER)) context.setBlockState(pos, Blocks.AIR);
        context.setBlockState(CONTROLLER, Blocks.AIR);
        context.setBlockState(BANK, Blocks.AIR);
        TestPlayers.remove(context, player);
        context.complete();
    }

    /** The host of the token's visit, checked: a hologram board actor, never saved, holding the party's star, hosting the space. */
    private static MulaEntity host(TestContext context, Board board) {
        MulaEntity host = StarHosts.hostOf(board.token().getUuid());
        context.assertTrue(host != null && !host.isRemoved(), "a Mula hosts the star");
        context.assertTrue(host.isBoardActor() && BoardActors.isBoardActor(host) && BoardActors.isHologram(host),
                "a hologram board actor");
        context.assertTrue(host.isInvulnerable() && host.isAiDisabled(), "invulnerable, no will of its own");
        context.assertFalse(host.shouldSave(), "never saved");
        context.assertTrue(ItemStack.areItemsAndComponentsEqual(host.getHeldItem(), board.party().getCurrency(PartyCurrency.STAR)),
                "it holds the party's star");
        context.assertEquals(host.hostSpace(), context.getAbsolutePos(STAR), "it hosts the star space");
        return host;
    }

    /** The token walks onto the star with {@code coins}; {@code then} once it reached it. */
    private static void reach(TestContext context, Board board, ServerPlayerEntity owner, int coins, boolean offered, Runnable then) {
        InventoryUtils.giveOrDrop(owner, board.party().getCurrency(PartyCurrency.COIN), coins);
        context.waitAndRun(2, () -> {
            TokenMovementService.moveEntityOnBoard(board.token(), 2);
            when(context, () -> offered ? PartyStars.isDeciding(board.token().getUuid())
                    : StarHosts.hostOf(board.token().getUuid()) != null, 80, "the token reaches the star", then);
        });
    }

    // ---------------------------------------------------------------- bought

    /**
     * Beside the space (no Spawn Bubble), facing the token, it presents the star; bought: it dances, gives it, and is
     * removed; the purchase is as without it.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "star_host_bought")
    public void boughtTheHostDancesAndGoes(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner);
            ItemStack coin = board.party().getCurrency(PartyCurrency.COIN);
            ItemStack starItem = board.party().getCurrency(PartyCurrency.STAR);
            reach(context, board, owner, PRICE + 5, true, () -> {
                MulaEntity host = host(context, board);
                Vec3d stand = BoardSpaces.standPos(context.getWorld(), context.getAbsolutePos(STAR));
                double side = Math.hypot(host.getX() - stand.x, host.getZ() - stand.z);
                context.assertTrue(Math.abs(side - StarHosts.SIDE) < 0.1 && Math.abs(host.getY() - (stand.y + StarHosts.LIFT)) < 0.1,
                        "beside the space, floating: " + side + " / " + (host.getY() - stand.y));
                when(context, () -> StarHosts.phase(board.token().getUuid()) == StarHosts.Phase.PRESENT, 60, "it presents the star", () -> {
                    context.assertTrue(PartyStars.decide(owner, true), "bought");
                    context.assertEquals(count(owner, coin), 5, "the price paid");
                    context.assertEquals(count(owner, starItem), 1, "the star given (by the bank, not by the Mula)");
                    later(context, () -> {
                        context.assertEquals(StarHosts.phase(board.token().getUuid()), StarHosts.Phase.DANCE, "it dances");
                        when(context, () -> host.isRemoved() && StarHosts.hostOf(board.token().getUuid()) == null, GOODBYE,
                                "it is gone after its show", () -> {
                                    context.assertEquals(count(owner, starItem), 1, "nothing more given");
                                    finish(context, board, owner);
                                });
                    });
                });
            });
        });
    }

    // ---------------------------------------------------------------- not bought

    /** Refused: it is disappointed, leaves empty-handed (the star stays over its space), and is removed. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "star_host_refused")
    public void refusedTheHostIsDisappointedAndGoes(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner);
            ItemStack coin = board.party().getCurrency(PartyCurrency.COIN);
            reach(context, board, owner, PRICE, true, () -> {
                MulaEntity host = host(context, board);
                context.assertTrue(PartyStars.decide(owner, false), "no thanks");
                context.assertEquals(count(owner, coin), PRICE, "the coins kept");
                when(context, () -> StarHosts.phase(board.token().getUuid()) == StarHosts.Phase.SAD, 30, "disappointed (once it has popped in)", () -> {
                    when(context, () -> StarHosts.phase(board.token().getUuid()) == StarHosts.Phase.LEAVE, 80, "it leaves", () -> {
                        context.assertTrue(host.getHeldItem().isEmpty() && host.hostSpace() == null, "empty-handed: the star is back over its space");
                        when(context, () -> host.isRemoved() && StarHosts.hostOf(board.token().getUuid()) == null, GOODBYE,
                                "it is gone", () -> {
                                    context.assertEquals(context.getAbsolutePos(STAR), board.party().getStarSpace(), "the star stays");
                                    finish(context, board, owner);
                                });
                    });
                });
            });
        });
    }

    /** Too few coins: no choice, the token goes on; its host comes all the same, disappointed, and goes. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "star_host_too_poor")
    public void tooFewCoinsTheHostIsDisappointed(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner);
            reach(context, board, owner, PRICE - 1, false, () -> {
                MulaEntity host = host(context, board);
                context.assertFalse(PartyStars.isDeciding(board.token().getUuid()), "no choice");
                when(context, () -> StarHosts.phase(board.token().getUuid()) == StarHosts.Phase.SAD, 40, "disappointed", () ->
                        when(context, () -> host.isRemoved() && StarHosts.hostOf(board.token().getUuid()) == null, GOODBYE,
                                "it is gone", () -> {
                                    BoardSpaceBlockEntity on = BoardSpaces.boardSpaceOf(board.token());
                                    context.assertTrue(on != null && on.getPos().equals(context.getAbsolutePos(END)), "the token went on");
                                    finish(context, board, owner);
                                }));
            });
        });
    }

    /** The party stopped while its player chooses: the host goes at once. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "star_host_cancelled")
    public void aStoppedVisitRemovesTheHostAtOnce(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner);
            reach(context, board, owner, PRICE, true, () -> {
                MulaEntity host = host(context, board);
                PartyStars.cancelOffer(board.token().getUuid());
                context.assertTrue(host.isRemoved() && StarHosts.hostOf(board.token().getUuid()) == null, "gone at once");
                finish(context, board, owner);
            });
        });
    }

    // ---------------------------------------------------------------- its Spawn Bubble

    /** A Star Cartridge is a mob space's cartridge (a Mula): linked to a Spawn Bubble, its host appears there. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "star_host_bubble")
    public void theHostAppearsOnItsSpawnBubble(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner);
            ItemStack cartridge = board.star().getStack(0);
            context.assertTrue(CartridgeSpawnMarker.spawnsMobs(cartridge) && CartridgeSpawnMarker.mobOf(cartridge) == ModEntities.MULA_ENTITY,
                    "the Star Cartridge summons a Mula: the brush links it to a Spawn Bubble");
            context.setBlockState(MARKER.down(), Blocks.STONE);
            context.setBlockState(MARKER, ModBlocks.SPAWN_MARKER.getDefaultState()
                    .with(SpawnMarkerBlock.ROTATION, SpawnMarkerBlock.rotation(Direction.WEST)));
            BlockPos marker = context.getAbsolutePos(MARKER);
            CartridgeSpawnMarker.set(cartridge, context.getWorld(), marker);
            CartridgeSpawnMarker.own(context.getWorld(), marker, board.star().getPos());
            reach(context, board, owner, PRICE, true, () -> {
                MulaEntity host = host(context, board);
                Vec3d on = SpawnMarkerBlock.standPos(marker);
                context.assertTrue(Math.abs(host.getX() - on.x) < 0.05 && Math.abs(host.getZ() - on.z) < 0.05, "on its Spawn Bubble");
                context.assertTrue(PartyStars.decide(owner, false), "no thanks");
                when(context, () -> host.isRemoved(), GOODBYE, "it is gone", () -> {
                    context.setBlockState(MARKER, Blocks.AIR);
                    finish(context, board, owner);
                });
            });
        });
    }
}
