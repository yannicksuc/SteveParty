package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlock;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlockEntity;
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
import fr.lordfinn.steveparty.service.MarkerResidents;
import fr.lordfinn.steveparty.service.PartyStars;
import fr.lordfinn.steveparty.service.StarHosts;
import fr.lordfinn.steveparty.service.TokenMovementService;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
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
 * The star's host (StarHosts): a Mula waits with the star on the star space that has it the whole party, a hologram
 * board actor (invulnerable, never saved) holding the party's star item, on the space's Spawn Bubble when its cartridge
 * is linked to one, else floating beside the space; it presents the star to a token that reaches it, dances and gives
 * it when it is bought (and a new host waits with the star on its new space), is disappointed when it is not (or the
 * player is short of coins) and waits again. A bubble « always visible » has a Mula asleep on it where the star is not,
 * which wakes up with the star. The purchase itself is unchanged (StarCartridgeGameTests).
 * <p>
 * Each test runs in a batch of its own and takes its star spaces away when it ends (the star spaces of a party are
 * those of its board).
 */
public class StarHostGameTests implements SteveGameTest {
    private static final BlockPos START = new BlockPos(1, 1, 1), STAR = new BlockPos(3, 1, 1), END = new BlockPos(5, 1, 1);
    private static final BlockPos OTHER = new BlockPos(1, 1, 5);
    private static final BlockPos CONTROLLER = new BlockPos(8, 1, 8), BANK = new BlockPos(8, 1, 3);
    private static final BlockPos MARKER = new BlockPos(3, 1, 4), OTHER_MARKER = new BlockPos(3, 1, 7);
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
        for (BlockPos pos : List.of(STAR, OTHER, MARKER, OTHER_MARKER)) context.setBlockState(pos, Blocks.AIR);
        context.setBlockState(CONTROLLER, Blocks.AIR);
        context.setBlockState(BANK, Blocks.AIR);
        TestPlayers.remove(context, player);
        context.complete();
    }

    /** The host of the token's visit, checked: a hologram board actor, never saved, holding the party's star, hosting the space. */
    private static MulaEntity host(TestContext context, Board board) {
        MulaEntity host = StarHosts.hostOf(board.token().getUuid());
        context.assertTrue(host != null && !host.isRemoved(), "a Mula hosts the star");
        checked(context, board, host, STAR);
        return host;
    }

    /** The host waiting with the star on {@code space} (no token there), checked. */
    private static MulaEntity waiting(TestContext context, Board board, BlockPos space) {
        StarHosts.update(context.getWorld());
        MulaEntity host = StarHosts.hostAt(context.getWorld(), context.getAbsolutePos(space));
        context.assertTrue(host != null && !host.isRemoved(), "a Mula waits with the star");
        checked(context, board, host, space);
        return host;
    }

    private static void checked(TestContext context, Board board, MulaEntity host, BlockPos space) {
        context.assertTrue(host.isBoardActor() && BoardActors.isBoardActor(host) && BoardActors.isHologram(host),
                "a hologram board actor");
        context.assertTrue(host.isInvulnerable() && host.isAiDisabled(), "invulnerable, no will of its own");
        context.assertFalse(host.shouldSave(), "never saved");
        context.assertTrue(ItemStack.areItemsAndComponentsEqual(host.getHeldItem(), board.party().getCurrency(PartyCurrency.STAR)),
                "it holds the party's star");
        context.assertEquals(host.hostSpace(), context.getAbsolutePos(space), "it hosts the star space");
        context.assertFalse(host.isHostAsleep(), "awake");
    }

    /** A Spawn Bubble at {@code at} facing west, linked to the space {@code space}, « always visible » or not. */
    private static BlockPos bubble(TestContext context, BoardSpaceBlockEntity space, BlockPos at, boolean resident) {
        context.setBlockState(at.down(), Blocks.STONE);
        context.setBlockState(at, ModBlocks.SPAWN_MARKER.getDefaultState()
                .with(SpawnMarkerBlock.ROTATION, SpawnMarkerBlock.rotation(Direction.WEST)));
        BlockPos marker = context.getAbsolutePos(at);
        CartridgeSpawnMarker.set(space.getStack(0), context.getWorld(), marker);
        CartridgeSpawnMarker.own(context.getWorld(), marker, space.getPos());
        if (resident) ((SpawnMarkerBlockEntity) context.getWorld().getBlockEntity(marker)).setResident(true);
        return marker;
    }

    private static boolean on(MulaEntity mula, BlockPos marker) {
        Vec3d at = SpawnMarkerBlock.standPos(marker);
        return Math.abs(mula.getX() - at.x) < 0.05 && Math.abs(mula.getZ() - at.z) < 0.05;
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

    // ---------------------------------------------------------------- waiting, bought

    /**
     * Before any token, a Mula waits with the star beside its space (no Spawn Bubble), none on the other star space; a
     * token reaches it: the same Mula presents it; bought: it dances, gives it, and goes, and a new Mula waits with the
     * star on its new space. The purchase is as without it.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "star_host_bought")
    public void boughtTheHostDancesAndANewOneWaitsWithTheStar(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner);
            ItemStack coin = board.party().getCurrency(PartyCurrency.COIN);
            ItemStack starItem = board.party().getCurrency(PartyCurrency.STAR);
            MulaEntity waiting = waiting(context, board, STAR);
            context.assertTrue(StarHosts.hostAt(context.getWorld(), context.getAbsolutePos(OTHER)) == null, "none where the star is not");
            Vec3d stand = BoardSpaces.standPos(context.getWorld(), context.getAbsolutePos(STAR));
            double side = Math.hypot(waiting.getX() - stand.x, waiting.getZ() - stand.z);
            context.assertTrue(Math.abs(side - StarHosts.SIDE) < 0.1 && Math.abs(waiting.getY() - (stand.y + StarHosts.LIFT)) < 0.1,
                    "beside the space, floating: " + side + " / " + (waiting.getY() - stand.y));
            context.assertTrue(Math.abs(waiting.getZ() - stand.z) > 1, "off the path (the spaces along x)");
            when(context, () -> StarHosts.phaseAt(context.getWorld(), context.getAbsolutePos(STAR)) == StarHosts.Phase.WAIT, 40,
                    "it waits", () -> reach(context, board, owner, PRICE + 5, true, () -> {
                MulaEntity host = host(context, board);
                context.assertTrue(host == waiting, "the Mula waiting presents it");
                when(context, () -> StarHosts.phase(board.token().getUuid()) == StarHosts.Phase.PRESENT, 60, "it presents the star", () -> {
                    context.assertTrue(PartyStars.decide(owner, true), "bought");
                    context.assertEquals(count(owner, coin), 5, "the price paid");
                    context.assertEquals(count(owner, starItem), 1, "the star given (by the bank, not by the Mula)");
                    context.assertEquals(board.party().getStarSpace(), context.getAbsolutePos(OTHER), "the star moved");
                    MulaEntity next = StarHosts.hostAt(context.getWorld(), context.getAbsolutePos(OTHER));
                    context.assertTrue(next != null && next != host, "a new Mula waits with the star on its new space");
                    checked(context, board, next, OTHER);
                    later(context, () -> {
                        context.assertEquals(StarHosts.phase(board.token().getUuid()), StarHosts.Phase.DANCE, "it dances");
                        when(context, () -> host.isRemoved() && StarHosts.hostOf(board.token().getUuid()) == null, GOODBYE,
                                "it is gone after its show", () -> {
                                    context.assertEquals(count(owner, starItem), 1, "nothing more given");
                                    context.assertTrue(StarHosts.hostAt(context.getWorld(), context.getAbsolutePos(STAR)) == null, "none left there");
                                    context.assertTrue(!next.isRemoved(), "the new one stays");
                                    finish(context, board, owner);
                                });
                    });
                });
            }));
        });
    }

    // ---------------------------------------------------------------- not bought

    /** Refused: it is disappointed, then waits again with the star. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "star_host_refused")
    public void refusedTheHostIsDisappointedAndWaitsAgain(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner);
            ItemStack coin = board.party().getCurrency(PartyCurrency.COIN);
            reach(context, board, owner, PRICE, true, () -> {
                MulaEntity host = host(context, board);
                context.assertTrue(PartyStars.decide(owner, false), "no thanks");
                context.assertEquals(count(owner, coin), PRICE, "the coins kept");
                when(context, () -> StarHosts.phase(board.token().getUuid()) == StarHosts.Phase.SAD, 30, "disappointed (once it has popped in)", () ->
                        when(context, () -> StarHosts.hostOf(board.token().getUuid()) == null, 80, "its visit over", () -> {
                            context.assertEquals(StarHosts.phaseAt(context.getWorld(), context.getAbsolutePos(STAR)), StarHosts.Phase.WAIT, "it waits again");
                            context.assertTrue(waiting(context, board, STAR) == host, "the same Mula, the star in its hands");
                            context.assertEquals(context.getAbsolutePos(STAR), board.party().getStarSpace(), "the star stays");
                            finish(context, board, owner);
                        }));
            });
        });
    }

    /** Too few coins: no choice, the token goes on; its host is disappointed all the same, and waits again. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "star_host_too_poor")
    public void tooFewCoinsTheHostIsDisappointed(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner);
            reach(context, board, owner, PRICE - 1, false, () -> {
                MulaEntity host = host(context, board);
                context.assertFalse(PartyStars.isDeciding(board.token().getUuid()), "no choice");
                when(context, () -> StarHosts.phase(board.token().getUuid()) == StarHosts.Phase.SAD, 40, "disappointed", () ->
                        when(context, () -> StarHosts.hostOf(board.token().getUuid()) == null, GOODBYE, "its visit over", () -> {
                            context.assertTrue(!host.isRemoved() && waiting(context, board, STAR) == host, "it waits again");
                            BoardSpaceBlockEntity on = BoardSpaces.boardSpaceOf(board.token());
                            context.assertTrue(on != null && on.getPos().equals(context.getAbsolutePos(END)), "the token went on");
                            finish(context, board, owner);
                        }));
            });
        });
    }

    /** The party stopped: the host goes at once, and none comes back. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "star_host_cancelled")
    public void aStoppedPartyRemovesTheHost(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner);
            reach(context, board, owner, PRICE, true, () -> {
                MulaEntity host = host(context, board);
                board.party().stopParty(null);
                context.assertTrue(host.isRemoved() && StarHosts.hostOf(board.token().getUuid()) == null, "gone at once");
                StarHosts.update(context.getWorld());
                context.assertTrue(StarHosts.hostAt(context.getWorld(), context.getAbsolutePos(STAR)) == null, "none comes back");
                finish(context, board, owner);
            });
        });
    }

    /** Never saved: its Mula unloaded (a chunk, a restart), a new one waits with the star. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "star_host_reload")
    public void anUnloadedHostIsMadeAgain(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner);
            MulaEntity host = waiting(context, board, STAR);
            context.assertFalse(host.shouldSave(), "never saved");
            host.discard();
            later(context, () -> {
                MulaEntity again = waiting(context, board, STAR);
                context.assertTrue(again != host, "made again");
                finish(context, board, owner);
            });
        });
    }

    // ---------------------------------------------------------------- its Spawn Bubble

    /**
     * A Star Cartridge is a mob space's cartridge (a Mula): linked to a Spawn Bubble (« with the star », the default),
     * its host waits there with the star; nobody on the bubble of the star space without the star.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "star_host_bubble")
    public void theHostWaitsOnItsSpawnBubble(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner);
            ItemStack cartridge = board.star().getStack(0);
            context.assertTrue(CartridgeSpawnMarker.spawnsMobs(cartridge) && CartridgeSpawnMarker.mobOf(cartridge) == ModEntities.MULA_ENTITY,
                    "the Star Cartridge summons a Mula: the brush links it to a Spawn Bubble");
            BlockPos marker = bubble(context, board.star(), MARKER, false);
            BoardSpaceBlockEntity other = context.getBlockEntity(OTHER);
            BlockPos otherMarker = bubble(context, other, OTHER_MARKER, false);
            MulaEntity waiting = waiting(context, board, STAR);
            context.assertTrue(on(waiting, marker), "on its Spawn Bubble");
            MarkerResidents.update(context.getWorld().getServer());
            context.assertTrue(MarkerResidents.resident(context.getWorld(), otherMarker) == null, "nobody where the star is not");
            reach(context, board, owner, PRICE, true, () -> {
                MulaEntity host = host(context, board);
                context.assertTrue(host == waiting && on(host, marker), "the same, on its bubble");
                context.assertTrue(PartyStars.decide(owner, false), "no thanks");
                when(context, () -> StarHosts.hostOf(board.token().getUuid()) == null, GOODBYE, "its visit over", () -> {
                    context.assertTrue(!host.isRemoved() && on(host, marker) && !host.getHeldItem().isEmpty(), "back to waiting on its bubble");
                    finish(context, board, owner);
                });
            });
        });
    }

    /**
     * Bubbles « always visible »: a Mula asleep on the bubble of the star space without the star, the one with the star
     * awake holding it; bought, the star moves: the sleeper wakes up with it, the other falls asleep, empty-handed.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "star_host_resident")
    public void alwaysVisibleBubblesSleepWithoutTheStar(TestContext context) {
        withPlayer(context, owner -> {
            Board board = board(context, owner);
            BlockPos marker = bubble(context, board.star(), MARKER, true);
            BoardSpaceBlockEntity other = context.getBlockEntity(OTHER);
            BlockPos otherMarker = bubble(context, other, OTHER_MARKER, true);
            MarkerResidents.update(context.getWorld().getServer());
            MulaEntity host = waiting(context, board, STAR);
            MarkerResidents.update(context.getWorld().getServer());
            context.assertTrue(on(host, marker) && MarkerResidents.resident(context.getWorld(), marker) == null,
                    "awake with the star on its bubble, alone there");
            MobEntity resident = MarkerResidents.resident(context.getWorld(), otherMarker);
            context.assertTrue(resident instanceof MulaEntity mula && mula.isHostAsleep() && mula.getHeldItem().isEmpty()
                    && on(mula, otherMarker), "asleep where the star is not");
            MulaEntity sleeper = (MulaEntity) resident;
            reach(context, board, owner, PRICE, true, () -> {
                context.assertTrue(PartyStars.decide(owner, true), "bought");
                MulaEntity woken = StarHosts.hostAt(context.getWorld(), context.getAbsolutePos(OTHER));
                context.assertTrue(woken == sleeper, "the sleeper wakes up with the star");
                checked(context, board, woken, OTHER);
                context.assertTrue(MarkerResidents.resident(context.getWorld(), otherMarker) == null, "no second Mula there");
                when(context, () -> StarHosts.hostOf(board.token().getUuid()) == null, GOODBYE, "its show over", () -> {
                    context.assertTrue(!host.isRemoved() && MarkerResidents.resident(context.getWorld(), marker) == host
                            && host.isHostAsleep() && host.getHeldItem().isEmpty() && host.hostSpace() == null,
                            "the other falls asleep, empty-handed");
                    finish(context, board, owner);
                });
            });
        });
    }
}
