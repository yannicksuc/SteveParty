package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.pipe.MiniGamePipeBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlock;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeNetworks;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeShape;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeSolid;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeTravel;
import fr.lordfinn.steveparty.gametest.kit.TestAsserts;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.UUID;

import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * A mini-game pipe sends to its mini-game whatever the order its run was built in (playtest of 2026-10-06, #71): its
 * end into the wall capped (the pipe placed against the wall first), a mouth facing the wall (the run built first, the
 * pipe placed against it), or the pipe in the middle of the run. Nobody is brought out of a mouth with a block in front.
 */
public class MiniGamePipeBuildOrderGameTests implements SteveGameTest {
    private static final int GREEN = 13;

    private static Block green() {
        return ModBlocks.PIPES[PipeKind.OPAQUE.ordinal()][GREEN];
    }

    /** Places {@code block}'s item by clicking the face {@code side} of the block at {@code clicked}, like a player. */
    private static void place(TestContext context, PlayerEntity player, Block block, BlockPos clicked, Direction side) {
        BlockPos abs = context.getAbsolutePos(clicked);
        ItemStack stack = new ItemStack(block);
        ItemPlacementContext placement = new ItemPlacementContext(player, Hand.MAIN_HAND, stack,
                new BlockHitResult(Vec3d.ofCenter(abs).add(Vec3d.of(side.getVector()).multiply(0.5)), side, abs, false));
        ((BlockItem) stack.getItem()).place(placement);
    }

    private static ServerPlayerEntity player(TestContext context, double x, double y, double z) {
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        player.getInventory().clear();
        TestPlayers.place(context, player, x, y, z);
        return player;
    }

    /** How a run {wall, x=1, x=2, x=3 (its mouth east)} along row {@code z} is built. */
    private enum Order {
        /** The mini-game pipe against the wall first, then the run toward the mouth: its end into the wall is capped. */
        WALL_FIRST,
        /** The run from the mouth first, the mini-game pipe last against it: its end is a mouth facing the wall. */
        RUN_FIRST,
        /** The mini-game pipe in the middle of the run (x=2). */
        IN_THE_MIDDLE
    }

    /** @return the mini-game pipe's position */
    private static BlockPos build(TestContext context, PlayerEntity builder, Order order, int z) {
        context.setBlockState(new BlockPos(0, 2, z), Blocks.STONE);
        for (int x = 1; x <= 3; x++) context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
        Block mini = ModBlocks.COPPER_MINIGAME_PIPE;
        BlockPos a = new BlockPos(1, 2, z), b = new BlockPos(2, 2, z), c = new BlockPos(3, 2, z);
        switch (order) {
            case WALL_FIRST -> {
                place(context, builder, mini, a.west(), Direction.EAST);
                place(context, builder, green(), a, Direction.EAST);
                place(context, builder, green(), b, Direction.EAST);
                return a;
            }
            case RUN_FIRST -> {
                place(context, builder, green(), c.down(), Direction.UP);
                place(context, builder, green(), c, Direction.WEST);
                place(context, builder, mini, b, Direction.WEST);
                return a;
            }
            default -> {
                place(context, builder, green(), c.down(), Direction.UP);
                place(context, builder, mini, c, Direction.WEST);
                place(context, builder, green(), b, Direction.WEST);
                return b;
            }
        }
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void miniGamePipeSendsWhateverTheBuildOrder(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity builder = player(context, 6.5, 2, 1.5);
        builder.changeGameMode(GameMode.CREATIVE);
        // The mini-game: one players pipe, standing on stone (its mouth on top)
        BlockPos arrival = new BlockPos(6, 2, 6);
        context.setBlockState(arrival.down(), Blocks.STONE);
        context.setBlockState(arrival, green().getDefaultState().with(PipeBlock.SOLID, PipeSolid.DOWN));
        ItemStack page = new ItemStack(ModItems.MINI_GAME_PAGE);
        UUID id = MiniGamePages.ensureId(page);
        MiniGamePages.toggleLink(world.getServer(), id, GlobalPos.create(world.getRegistryKey(), context.getAbsolutePos(arrival)), Direction.UP,
                MiniGamePipeRole.PLAYERS);

        List<Order> orders = List.of(Order.WALL_FIRST, Order.RUN_FIRST, Order.IN_THE_MIDDLE);
        BlockPos[] pipes = new BlockPos[orders.size()];
        for (int i = 0; i < orders.size(); i++) {
            pipes[i] = build(context, builder, orders.get(i), 1 + 2 * i);
            ((MiniGamePipeBlockEntity) context.getBlockEntity(pipes[i])).setPage(page.copyWithCount(1));
        }
        TestPlayers.leaveMiniGames(context, builder);
        // What each order makes of the mini-game pipe
        context.assertTrue(PipeShape.ends(context.getBlockState(pipes[0])).stream().anyMatch(end -> end.capped() && end.dir() == Direction.WEST),
                "against the wall first: capped into the wall");
        context.assertTrue(PipeShape.mouth(context.getBlockState(pipes[1]), Direction.WEST) != null, "the run first: a mouth facing the wall");
        context.assertTrue(PipeShape.ends(context.getBlockState(pipes[2])).isEmpty(), "in the middle: no end");
        for (int i = 0; i < orders.size(); i++) {
            BlockPos mouth = new BlockPos(3, 2, 1 + 2 * i);
            context.assertTrue(PipeShape.mouth(context.getBlockState(mouth), Direction.EAST) != null, orders.get(i) + ": the way in is a mouth");
        }
        // The network found from either end: the same way
        PipeNetworks.of(world).network(context.getAbsolutePos(pipes[0]));
        PipeNetworks.of(world).network(context.getAbsolutePos(new BlockPos(3, 2, 3)));
        go(context, orders, 0, arrival);
    }

    /** A player into the east mouth of run {@code i}: out of the mini-game's players pipe; then the next run. */
    private static void go(TestContext context, List<Order> orders, int i, BlockPos arrival) {
        if (i >= orders.size()) {
            context.complete();
            return;
        }
        ServerWorld world = context.getWorld();
        BlockPos mouth = new BlockPos(3, 2, 1 + 2 * i);
        ServerPlayerEntity player = player(context, 4.2, 2, mouth.getZ() + 0.5);
        if (!PipeTravel.enter(world, context.getAbsolutePos(mouth), Direction.EAST, player, 0)) {
            TestPlayers.leaveMiniGames(context, player);
            context.throwGameTestException(orders.get(i) + ": could not go in");
            return;
        }
        when(context, () -> TestAsserts.cameOutAt(context, player, arrival), 80, orders.get(i) + ": never came out of the mini-game's pipe", () -> {
            boolean alive = player.isAlive() && !player.isInsideWall();
            TestPlayers.leaveMiniGames(context, player);
            context.assertTrue(alive, orders.get(i) + ": came out hurt or in a wall");
            context.waitAndRun(1, () -> go(context, orders, i + 1, arrival));
        });
    }
}
