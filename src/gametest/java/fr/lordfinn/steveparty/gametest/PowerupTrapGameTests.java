package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.powerups.effects.TrapEffect;
import fr.lordfinn.steveparty.powerups.effects.TrapState;
import fr.lordfinn.steveparty.service.TokenMovementService;
import fr.lordfinn.steveparty.utils.InventoryUtils;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Trap power-up ({@link TrapEffect}): set on the space of the user's token; the next token of another player that stops
 * there gives its owner 10 coins (at most what that player holds) and the trap is gone; passing over it, or its owner
 * stopping there, does nothing; one trap per space; saved with the party, never sent to the clients.
 * <p>
 * Players: {@code a} sets the traps, {@code b} is the victim. Each test runs in a batch of its own (mock players, the
 * coins they hold).
 */
public class PowerupTrapGameTests implements FabricGameTest {
    private static final BlockPos CONTROLLER = new BlockPos(1, 1, 5);
    /** The board: T0 → T1 → T2 → T3. */
    private static final BlockPos T0 = new BlockPos(1, 1, 1), T1 = new BlockPos(3, 1, 1), T2 = new BlockPos(5, 1, 1),
            T3 = new BlockPos(7, 1, 1);

    private record Game(PartyControllerEntity controller, ServerPlayerEntity a, ServerPlayerEntity b,
                        CowEntity tokenA, CowEntity tokenB, ItemStack coin) {
        BoardSpaceBlockEntity space(TestContext context, BlockPos pos) {
            return context.getBlockEntity(pos);
        }

        int coins(ServerPlayerEntity player) {
            return InventoryUtils.count(player.getInventory(), coin);
        }

        void give(ServerPlayerEntity player, int count) {
            InventoryUtils.giveOrDrop(player, coin, count);
        }

        TrapState traps() {
            return controller.getPartyData().getTraps();
        }
    }

    // ---------------------------------------------------------------- fixture

    /**
     * The board, two players with a token each ({@code a}'s on T0, {@code b}'s on {@code bAt}) and their party started
     * up to the first turn of {@code first} ("a" or "b"). Mock players are removed if the test fails.
     */
    private static void game(TestContext context, BlockPos bAt, String first, BiConsumer<Game, Runnable> test) {
        ServerPlayerEntity a = TestPlayers.mock(context);
        ServerPlayerEntity b = TestPlayers.mock(context);
        Runnable cleanUp = () -> {
            TestPlayers.remove(context, a);
            TestPlayers.remove(context, b);
        };
        try {
            for (int x = 0; x < 9; x++) {
                for (int z = 0; z < 9; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
            }
            for (BlockPos pos : List.of(T0, T1, T2, T3)) context.setBlockState(pos, ModBlocks.TILE);
            link(context, T0, T1);
            link(context, T1, T2);
            link(context, T2, T3);
            CowEntity tokenA = token(context, T0, a);
            CowEntity tokenB = token(context, bAt, b);
            PartyControllerEntity controller = "a".equals(first)
                    ? TestBoards.party(context, CONTROLLER, 2, tokenA.getUuid(), tokenB.getUuid())
                    : TestBoards.party(context, CONTROLLER, 2, tokenB.getUuid(), tokenA.getUuid());
            Game game = new Game(controller, a, b, tokenA, tokenB, controller.getCurrency(PartyCurrency.COIN));
            test.accept(game, () -> {
                ((TokenizedEntityInterface) tokenA).steveparty$setTokenized(false);
                ((TokenizedEntityInterface) tokenB).steveparty$setTokenized(false);
                context.removeBlock(CONTROLLER);
                cleanUp.run();
                context.complete();
            });
        } catch (RuntimeException e) {
            cleanUp.run();
            throw e;
        }
    }

    private static void link(TestContext context, BlockPos from, BlockPos to) {
        BoardSpaceBlockEntity space = context.getBlockEntity(from);
        ItemStack cartridge = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR);
        cartridge.set(ModComponents.DESTINATIONS_COMPONENT,
                new DestinationsComponent(new ArrayList<>(List.of(context.getAbsolutePos(to))), ""));
        space.setStack(0, cartridge);
    }

    private static CowEntity token(TestContext context, BlockPos at, ServerPlayerEntity owner) {
        CowEntity cow = context.spawnEntity(EntityType.COW, at);
        TokenizedEntityInterface token = (TokenizedEntityInterface) cow;
        token.steveparty$setTokenized(true);
        token.steveparty$setTokenOwner(owner.getUuid());
        token.steveparty$setStatus(TokenStatus.IN_GAME);
        return cow;
    }

    /** Runs {@code step} in {@code ticks}, logging a failure (the gametest log does not show the assertion messages). */
    private static void later(TestContext context, long ticks, Runnable step) {
        context.waitAndRun(ticks, () -> {
            try {
                step.run();
            } catch (RuntimeException e) {
                fr.lordfinn.steveparty.Steveparty.LOGGER.error("Trap test failed: {}", e.getMessage());
                throw e;
            }
        });
    }

    // ---------------------------------------------------------------- tests

    /** Using the Trap sets it on the space of the user's token, owned by its player; off the board, nothing is set. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "powerup_trap_set")
    public void usingTheTrapSetsItOnTheTokensSpace(TestContext context) {
        game(context, T2, "a", (game, done) -> {
            BlockPos t0 = context.getAbsolutePos(T0);
            context.assertEquals(BoardSpaces.boardSpaceOf(game.tokenA()).getPos(), t0, "a's token stands on T0");
            context.assertEquals(TrapEffect.use(game.controller(), game.tokenA()), TrapEffect.Placed.SET, "trap set");
            TrapState.Trap trap = TrapEffect.trapAt(game.controller(), t0);
            context.assertTrue(trap != null && trap.placer().equals(game.a().getUuid()), "a's trap on T0");
            context.assertTrue(game.tokenA().getUuid().equals(trap.placerToken()), "a's token is remembered");
            context.assertEquals(game.traps().all().size(), 1, "one trap");
            context.assertFalse(game.controller().toInitialChunkDataNbt(context.getWorld().getRegistryManager()).contains(TrapState.NBT_KEY),
                    "the traps are not sent to the clients");

            game.tokenA().setPosition(context.getAbsolute(new net.minecraft.util.math.Vec3d(4.5, 1, 7.5)));
            context.assertEquals(TrapEffect.use(game.controller(), game.tokenA()), TrapEffect.Placed.NO_SPACE, "off the board");
            context.assertEquals(game.traps().all().size(), 1, "no other trap");
            done.run();
        });
    }

    /** Another player's token stopping on the trap gives its owner 10 coins; the trap is gone (no second time). */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "powerup_trap_sprung")
    public void anotherPlayerStoppingSpringsTheTrap(TestContext context) {
        game(context, T2, "b", (game, done) -> {
            BlockPos t2 = context.getAbsolutePos(T2);
            TrapEffect.place(game.controller(), t2, game.a().getUuid(), game.tokenA().getUuid());
            game.give(game.b(), 15);
            // The landing of b's token (as at the end of its move): the trap springs before the tile's role
            game.space(context, T2).onDestinationReached(game.tokenB(), game.controller());
            context.assertEquals(game.coins(game.b()), 5, "b gave 10 coins");
            context.assertEquals(game.coins(game.a()), 10, "a got them");
            context.assertTrue(TrapEffect.trapAt(game.controller(), t2) == null, "the trap is gone");

            TrapEffect.Result again = TrapEffect.onTokenStopped(game.controller(), game.space(context, T2), game.tokenB());
            context.assertEquals(again.outcome(), TrapEffect.Outcome.NONE, "no trap the second time");
            context.assertEquals(game.coins(game.b()), 5, "b keeps the rest");
            done.run();
        });
    }

    /** Passing over a trap does nothing; the trap on the space where the token stops springs. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200, batchId = "powerup_trap_passing")
    public void passingOverTheTrapDoesNothing(TestContext context) {
        game(context, T0, "b", (game, done) -> {
            BlockPos t1 = context.getAbsolutePos(T1), t2 = context.getAbsolutePos(T2);
            TrapEffect.place(game.controller(), t1, game.a().getUuid(), game.tokenA().getUuid());
            TrapEffect.place(game.controller(), t2, game.a().getUuid(), game.tokenA().getUuid());
            game.give(game.b(), 30);
            later(context, 2, () -> {
                TokenMovementService.moveEntityOnBoard(game.tokenB(), 2);
                later(context, 60, () -> {
                    BoardSpaceBlockEntity at = BoardSpaces.boardSpaceOf(game.tokenB());
                    context.assertTrue(at != null && at.getPos().equals(t2), "b's token walked 2 steps to T2");
                    context.assertTrue(TrapEffect.trapAt(game.controller(), t1) != null, "the trap passed over is still there");
                    context.assertTrue(TrapEffect.trapAt(game.controller(), t2) == null, "the trap where it stopped sprang");
                    context.assertEquals(game.coins(game.b()), 20, "b gave 10 coins, once");
                    context.assertEquals(game.coins(game.a()), 10, "a got them");
                    done.run();
                });
            });
        });
    }

    /** The owner's token stopping on their own trap does nothing: the trap stays for the others. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "powerup_trap_own")
    public void theOwnerDoesNotSpringTheirTrap(TestContext context) {
        game(context, T2, "a", (game, done) -> {
            BlockPos t0 = context.getAbsolutePos(T0);
            TrapEffect.use(game.controller(), game.tokenA());
            game.give(game.a(), 15);
            TrapEffect.Result result = TrapEffect.onTokenStopped(game.controller(), game.space(context, T0), game.tokenA());
            context.assertEquals(result.outcome(), TrapEffect.Outcome.OWN, "its own trap");
            game.space(context, T0).onDestinationReached(game.tokenA(), game.controller());
            context.assertEquals(game.coins(game.a()), 15, "a keeps the coins");
            context.assertTrue(TrapEffect.trapAt(game.controller(), t0) != null, "the trap stays");
            done.run();
        });
    }

    /** The victim gives at most what they hold; with nothing, the trap still springs and is gone. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "powerup_trap_cap")
    public void theVictimGivesAtMostWhatTheyHold(TestContext context) {
        game(context, T2, "b", (game, done) -> {
            BlockPos t2 = context.getAbsolutePos(T2);
            TrapEffect.place(game.controller(), t2, game.a().getUuid(), game.tokenA().getUuid());
            game.give(game.b(), 4);
            TrapEffect.Result result = TrapEffect.onTokenStopped(game.controller(), game.space(context, T2), game.tokenB());
            context.assertEquals(result, new TrapEffect.Result(TrapEffect.Outcome.SPRUNG, 4), "4 coins only");
            context.assertEquals(game.coins(game.b()), 0, "b has nothing left");
            context.assertEquals(game.coins(game.a()), 4, "a got 4");

            TrapEffect.place(game.controller(), t2, game.a().getUuid(), game.tokenA().getUuid());
            result = TrapEffect.onTokenStopped(game.controller(), game.space(context, T2), game.tokenB());
            context.assertEquals(result, new TrapEffect.Result(TrapEffect.Outcome.SPRUNG, 0), "nothing to give");
            context.assertTrue(TrapEffect.trapAt(game.controller(), t2) == null, "the trap is gone all the same");
            context.assertEquals(game.coins(game.a()), 4, "a got nothing more");
            done.run();
        });
    }

    /** One trap per space: a new one replaces the old one, whoever set it. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "powerup_trap_replace")
    public void aNewTrapReplacesTheOldOne(TestContext context) {
        game(context, T0, "a", (game, done) -> {
            BlockPos t0 = context.getAbsolutePos(T0);
            context.assertEquals(TrapEffect.use(game.controller(), game.tokenA()), TrapEffect.Placed.SET, "a's trap");
            context.assertEquals(TrapEffect.use(game.controller(), game.tokenB()), TrapEffect.Placed.REPLACED, "b's trap replaces it");
            context.assertEquals(game.traps().all().size(), 1, "still one trap");
            context.assertTrue(TrapEffect.trapAt(game.controller(), t0).placer().equals(game.b().getUuid()), "b owns it");

            // Now a's token is the victim
            game.give(game.a(), 12);
            TrapEffect.Result result = TrapEffect.onTokenStopped(game.controller(), game.space(context, T0), game.tokenA());
            context.assertEquals(result, new TrapEffect.Result(TrapEffect.Outcome.SPRUNG, 10), "b's trap springs on a");
            context.assertEquals(game.coins(game.b()), 10, "b got 10");
            done.run();
        });
    }

    /** The traps are saved with the Party Controller and come back when it is loaded; a new party has none. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "powerup_trap_saved")
    public void theTrapsAreSavedWithTheParty(TestContext context) {
        game(context, T2, "b", (game, done) -> {
            RegistryWrapper.WrapperLookup registries = context.getWorld().getRegistryManager();
            BlockPos t1 = context.getAbsolutePos(T1), t2 = context.getAbsolutePos(T2);
            TrapEffect.place(game.controller(), t1, game.a().getUuid(), null);
            TrapEffect.place(game.controller(), t2, game.a().getUuid(), game.tokenA().getUuid());
            NbtCompound saved = game.controller().createNbt(registries);
            context.assertTrue(saved.contains(TrapState.NBT_KEY), "the traps are in the save");

            game.traps().clear();
            game.controller().read(saved, registries);
            TrapState loaded = game.controller().getPartyData().getTraps();
            context.assertEquals(loaded.all().size(), 2, "both traps are back");
            context.assertTrue(loaded.get(t1).placer().equals(game.a().getUuid()) && loaded.get(t1).placerToken() == null, "T1");
            context.assertTrue(game.tokenA().getUuid().equals(loaded.get(t2).placerToken()), "T2");

            // Still working after the load
            game.give(game.b(), 10);
            game.space(context, T2).onDestinationReached(game.tokenB(), game.controller());
            context.assertEquals(game.coins(game.a()), 10, "the loaded trap springs");

            game.controller().getPartyData().reset();
            context.assertTrue(game.controller().getPartyData().getTraps().isEmpty(), "a new party starts without traps");
            done.run();
        });
    }
}
