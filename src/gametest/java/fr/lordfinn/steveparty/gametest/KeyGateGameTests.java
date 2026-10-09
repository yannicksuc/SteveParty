package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceDestination;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.dice.DicePrompts;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.BoardRuleCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.KeyGateCartridgeItem;
import fr.lordfinn.steveparty.service.KeyGates;
import fr.lordfinn.steveparty.service.TokenMovementService;
import fr.lordfinn.steveparty.service.TurnMoves;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The Key gate Cartridge: the only way on locked (wait, use or keep the key, replay the move once, back to the start),
 * a fork with a locked branch (refused, another way taken), a gate kept open for everyone, and its settings. Each test
 * has its own batch (a mock player).
 * <p>
 * Board: S (a start tile) → T0 → G (the gate) → T2 → T3 along x, and G → F (south of G, the free branch of the fork
 * tests) → F2.
 */
public class KeyGateGameTests implements SteveGameTest {
    private static final BlockPos S = new BlockPos(1, 1, 1), T0 = new BlockPos(3, 1, 1), G = new BlockPos(5, 1, 1),
            T2 = new BlockPos(7, 1, 1), T3 = new BlockPos(9, 1, 1), F = new BlockPos(5, 1, 3), F2 = new BlockPos(5, 1, 5);

    private record Board(ServerPlayerEntity player, CowEntity token, BoardSpaceBlockEntity gate) {
    }

    private static void space(TestContext context, BlockPos pos, ItemStack cartridge, BlockPos... to) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.TILE);
        List<BlockPos> next = new ArrayList<>();
        for (BlockPos p : to) next.add(context.getAbsolutePos(p));
        cartridge.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(next, ""));
        BoardSpaceBlockEntity space = context.getBlockEntity(pos);
        space.setStack(0, cartridge);
    }

    /** The board; {@code fork}: G also leads south to F. The token on T0, its player in survival (keys used up). */
    private static Board board(TestContext context, ItemStack gate, boolean fork) {
        space(context, S, new ItemStack(ModItems.TILE_BEHAVIOR_START), T0);
        space(context, T0, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), G);
        if (fork) space(context, G, gate, T2, F);
        else space(context, G, gate, T2);
        space(context, T2, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), T3);
        space(context, T3, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));
        if (fork) {
            space(context, F, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), F2);
            space(context, F2, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));
        }
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        CowEntity cow = context.spawnEntity(EntityType.COW, T0);
        TokenizedEntityInterface token = (TokenizedEntityInterface) cow;
        token.steveparty$setTokenized(true);
        token.steveparty$setTokenOwner(player.getUuid());
        token.steveparty$setStatus(TokenStatus.IN_GAME);
        ThresholdGameTests.atEnd(context, () -> {
            token.steveparty$setTokenized(false);
            TurnMoves.forget(cow.getUuid());
            context.getWorld().getServer().getPlayerManager().remove(player);
        });
        return new Board(player, cow, context.getBlockEntity(G));
    }

    private static void roll(MobEntity token, int steps) {
        BoardSpaceBlockEntity from = BoardSpaces.boardSpaceOf(token);
        TurnMoves.record(token, steps, List.of(steps), from == null ? null : from.getPos());
        TokenMovementService.moveEntityOnBoard(token, steps);
    }

    private static int steps(MobEntity token) {
        return ((TokenizedEntityInterface) token).steveparty$getNbSteps();
    }

    private static boolean stopsOn(TestContext context, MobEntity token, BlockPos at) {
        BoardSpaceBlockEntity on = BoardSpaces.boardSpaceOf(token);
        return on != null && on.getPos().equals(context.getAbsolutePos(at)) && steps(token) == 0;
    }

    private static void when(TestContext context, BooleanSupplier condition, int ticks, String what, Runnable then) {
        if (condition.getAsBoolean()) {
            then.run();
            return;
        }
        context.assertTrue(ticks > 0, "timed out: " + what);
        context.waitAndRun(1, () -> when(context, condition, ticks - 1, what, then));
    }

    /** The choices of the player's prompt, as KeyGates.Choice. */
    private static List<KeyGates.Choice> choices(ServerPlayerEntity player) {
        DicePrompts.Prompt prompt = DicePrompts.pending(player);
        List<KeyGates.Choice> choices = new ArrayList<>();
        if (prompt == null) return choices;
        for (DicePrompts.Option option : prompt.options()) {
            String key = option.label().getContent() instanceof TranslatableTextContent t ? t.getKey() : "";
            for (KeyGates.Choice choice : KeyGates.Choice.values()) if (choice.key().equals(key)) choices.add(choice);
        }
        return choices;
    }

    private static void answer(TestContext context, ServerPlayerEntity player, KeyGates.Choice choice) {
        DicePrompts.Prompt prompt = DicePrompts.pending(player);
        int index = choices(player).indexOf(choice);
        context.assertTrue(prompt != null && index >= 0, "the player is offered " + choice + ": " + choices(player));
        DicePrompts.answer(player, prompt.id(), index);
    }

    private static BooleanSupplier asked(ServerPlayerEntity player) {
        return () -> DicePrompts.pending(player) != null;
    }

    private static ItemStack gate() {
        return new ItemStack(ModItems.KEY_GATE_CARTRIDGE);
    }

    // ---------------------------------------------------------------- the only way on locked

    /** No key: waiting there loses the steps left, the token stays on the gate's space. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "key_gate_wait")
    public void withoutAKeyItWaitsInFrontOfTheGate(TestContext context) {
        Board board = board(context, gate(), false);
        context.waitAndRun(2, () -> {
            roll(board.token(), 3);
            when(context, asked(board.player()), 100, "its player is asked", () -> {
                List<KeyGates.Choice> offered = choices(board.player());
                context.assertTrue(!offered.contains(KeyGates.Choice.USE_KEY), "no key to use");
                context.assertTrue(offered.containsAll(List.of(KeyGates.Choice.WAIT, KeyGates.Choice.CANCEL, KeyGates.Choice.START)),
                        "wait, cancel, start: " + offered);
                context.assertTrue(steps(board.token()) > 0, "its move is held");
                answer(context, board.player(), KeyGates.Choice.WAIT);
                when(context, () -> stopsOn(context, board.token(), G), 60, "it stays on the gate's space", context::complete);
            });
        });
    }

    /** With a key: used up, the token goes on through the gate with its steps left. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "key_gate_use")
    public void aKeyOpensTheGateAndIsUsedUp(TestContext context) {
        Board board = board(context, gate(), false);
        board.player().getInventory().insertStack(new ItemStack(ModItems.GATE_KEY, 2));
        context.waitAndRun(2, () -> {
            roll(board.token(), 3);
            when(context, asked(board.player()), 100, "its player is asked", () -> {
                context.assertEquals(choices(board.player()), List.of(KeyGates.Choice.USE_KEY, KeyGates.Choice.KEEP_KEY), "use or keep");
                answer(context, board.player(), KeyGates.Choice.USE_KEY);
                when(context, () -> stopsOn(context, board.token(), T3), 120, "it goes on to T3", () -> {
                    context.assertEquals(board.player().getInventory().count(ModItems.GATE_KEY), 1, "one key used up");
                    context.assertTrue(!KeyGates.isOpen(board.gate(), board.token()), "it closes behind (no stay open)");
                    context.complete();
                });
            });
        });
    }

    /** Keeping the key: as without one (the key stays). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "key_gate_keep")
    public void keepingTheKeyIsAsWithoutOne(TestContext context) {
        Board board = board(context, gate(), false);
        board.player().getInventory().insertStack(new ItemStack(ModItems.GATE_KEY));
        context.waitAndRun(2, () -> {
            roll(board.token(), 3);
            when(context, asked(board.player()), 100, "its player is asked", () -> {
                answer(context, board.player(), KeyGates.Choice.KEEP_KEY);
                when(context, () -> choices(board.player()).contains(KeyGates.Choice.WAIT), 20, "then the choices without a key", () -> {
                    answer(context, board.player(), KeyGates.Choice.WAIT);
                    when(context, () -> stopsOn(context, board.token(), G), 60, "it waits", () -> {
                        context.assertEquals(board.player().getInventory().count(ModItems.GATE_KEY), 1, "the key is kept");
                        context.complete();
                    });
                });
            });
        });
    }

    /** Cancel the move: back where it started, the same roll walked again; not offered a second time. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "key_gate_cancel")
    public void cancellingReplaysTheMoveOnce(TestContext context) {
        Board board = board(context, gate(), false);
        context.waitAndRun(2, () -> {
            roll(board.token(), 3);
            when(context, asked(board.player()), 100, "its player is asked", () -> {
                answer(context, board.player(), KeyGates.Choice.CANCEL);
                when(context, asked(board.player()), 200, "blocked again after the replay", () -> {
                    context.assertTrue(TurnMoves.wasReplayed(board.token()), "its move was replayed");
                    context.assertTrue(!choices(board.player()).contains(KeyGates.Choice.CANCEL), "no second replay");
                    answer(context, board.player(), KeyGates.Choice.WAIT);
                    when(context, () -> stopsOn(context, board.token(), G), 60, "it waits", context::complete);
                });
            });
        });
    }

    /** Back to the start: on the start tile, its move over. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "key_gate_start")
    public void backToTheStart(TestContext context) {
        Board board = board(context, gate(), false);
        context.waitAndRun(2, () -> {
            roll(board.token(), 3);
            when(context, asked(board.player()), 100, "its player is asked", () -> {
                answer(context, board.player(), KeyGates.Choice.START);
                when(context, () -> stopsOn(context, board.token(), S), 150, "it is back on the start", context::complete);
            });
        });
    }

    /** The cartridge may forbid replaying and going back to the start: only waiting is left. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "key_gate_options")
    public void theCartridgeChoosesTheOptions(TestContext context) {
        ItemStack gate = gate();
        BoardRuleCartridgeItem.putSetting(gate, KeyGateCartridgeItem.WITHOUT_KEY, 0);
        Board board = board(context, gate, false);
        context.waitAndRun(2, () -> {
            roll(board.token(), 3);
            when(context, asked(board.player()), 100, "its player is asked", () -> {
                context.assertEquals(choices(board.player()), List.of(KeyGates.Choice.WAIT), "only waiting");
                answer(context, board.player(), KeyGates.Choice.WAIT);
                when(context, () -> stopsOn(context, board.token(), G), 60, "it waits", context::complete);
            });
        });
    }

    // ---------------------------------------------------------------- a fork, a gate kept open

    /** At a fork, the locked branch is refused without a key; another way is taken. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300, batchId = "key_gate_fork")
    public void aLockedBranchIsRefusedAtAFork(TestContext context) {
        ItemStack gate = gate();
        KeyGateCartridgeItem.setLocked(gate, Direction.SOUTH, false);
        Board board = board(context, gate, true);
        context.waitAndRun(2, () -> {
            roll(board.token(), 3);
            when(context, () -> stopsOn(context, board.token(), G) || steps(board.token()) == 2 && BoardSpaces.boardSpaceOf(board.token()) != null
                    && BoardSpaces.boardSpaceOf(board.token()).getPos().equals(context.getAbsolutePos(G)), 100, "it waits at the fork", () -> {
                TokenMovementService.moveEntityOnTileToDestination(context.getWorld(), context.getAbsolutePos(G),
                        new BoardSpaceDestination(context.getAbsolutePos(T2), true), board.token().getUuid());
                when(context, asked(board.player()), 20, "the locked branch asks", () -> {
                    context.assertTrue(choices(board.player()).contains(KeyGates.Choice.OTHER_WAY), "another way is offered");
                    answer(context, board.player(), KeyGates.Choice.OTHER_WAY);
                    context.assertEquals(steps(board.token()), 2, "still its steps");
                    TokenMovementService.moveEntityOnTileToDestination(context.getWorld(), context.getAbsolutePos(G),
                            new BoardSpaceDestination(context.getAbsolutePos(F), true), board.token().getUuid());
                    when(context, () -> stopsOn(context, board.token(), F2), 100, "the free branch", context::complete);
                });
            });
        });
    }

    /** « Stays open for good »: once opened, the next move goes through without a key nor a question. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400, batchId = "key_gate_open")
    public void aGateMayStayOpenForEveryone(TestContext context) {
        ItemStack gate = gate();
        BoardRuleCartridgeItem.putSetting(gate, KeyGateCartridgeItem.STAY_OPEN, KeyGateCartridgeItem.FOREVER);
        Board board = board(context, gate, false);
        board.player().getInventory().insertStack(new ItemStack(ModItems.GATE_KEY));
        context.waitAndRun(2, () -> {
            roll(board.token(), 1);
            when(context, () -> stopsOn(context, board.token(), G), 60, "it stops on the gate's space", () -> {
                roll(board.token(), 1);
                when(context, asked(board.player()), 60, "leaving it asks", () -> {
                    answer(context, board.player(), KeyGates.Choice.USE_KEY);
                    when(context, () -> stopsOn(context, board.token(), T2), 60, "through", () -> {
                        context.assertTrue(KeyGates.isOpen(board.gate(), board.token()), "open for good");
                        // Back in front of it, without a key now: it walks through
                        board.token().refreshPositionAndAngles(BoardSpaces.standPos(context.getWorld(), context.getAbsolutePos(G)), 0, 0);
                        roll(board.token(), 2);
                        when(context, () -> stopsOn(context, board.token(), T3), 100, "through again, no key", () -> {
                            context.assertTrue(DicePrompts.pending(board.player()) == null, "nobody asked");
                            context.complete();
                        });
                    });
                });
            });
        });
    }
}
