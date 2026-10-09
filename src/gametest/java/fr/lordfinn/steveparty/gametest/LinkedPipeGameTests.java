package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.pipe.MiniGamePipeBlock;
import fr.lordfinn.steveparty.blocks.custom.pipe.MiniGamePipeBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlock;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeShape;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeSolid;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeTravel;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeIndex;
import fr.lordfinn.steveparty.minigame.MiniGamePipeLink;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.minigame.MiniGamePipes;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.UUID;
import java.util.function.BooleanSupplier;

import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * The pipes linked to a mini-game page: closed during a round, out of a round the way out by a mini-game pipe of the
 * page (the last one taken in range, else the nearest in range, else a pipe like any other), out of its mouth of the
 * colour of the linked pipe taken; and the index of the mini-game pipes it relies on.
 */
public class LinkedPipeGameTests implements FabricGameTest {
    private static final int GREEN = 13, BLUE = 11;

    private static Block pipe(PipeKind kind, int color) {
        return ModBlocks.PIPES[kind.ordinal()][kind.colored ? color : 0];
    }

    private static GlobalPos global(TestContext context, BlockPos relative) {
        return GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(relative));
    }

    private static ServerPlayerEntity player(TestContext context, double x, double y, double z) {
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        player.getInventory().clear();
        Vec3d abs = context.getAbsolute(new Vec3d(x, y, z));
        player.refreshPositionAndAngles(abs.x, abs.y, abs.z, 0, 0);
        return player;
    }

    /** True once the player stands out of the mouth at {@code mouth} opening on {@code side}. */
    private static boolean outOf(TestContext context, ServerPlayerEntity player, BlockPos mouth, Direction side) {
        Vec3d at = context.getRelative(player.getPos());
        Vec3d expected = Vec3d.ofCenter(mouth).add(Vec3d.of(side.getVector()));
        return !player.hasVehicle() && Math.abs(at.x - expected.x) < 1.6 && Math.abs(at.z - expected.z) < 1.6 && Math.abs(at.y - mouth.getY()) < 2;
    }

    /**
     * A lobby around a copper mini-game pipe at (2,2,3) programmed with the page: a cyan junction above it, a green mouth
     * on the east (3,3,3), a blue one on the west (1,3,3), with room in front of each (nobody comes out of a mouth facing a block).
     */
    private static BlockPos lobby(TestContext context, ItemStack page) {
        BlockPos programmed = new BlockPos(2, 2, 3), junction = programmed.up();
        context.setBlockState(programmed.down(), Blocks.STONE);
        context.setBlockState(programmed, ModBlocks.COPPER_MINIGAME_PIPE.getDefaultState().with(PipeBlock.SOLID, PipeSolid.DOWN).with(PipeShape.connection(Direction.UP), true));
        context.setBlockState(junction, pipe(PipeKind.OPAQUE, 9).getDefaultState().with(PipeShape.connection(Direction.DOWN), true)
                .with(PipeShape.connection(Direction.EAST), true).with(PipeShape.connection(Direction.WEST), true));
        context.setBlockState(junction.east(), pipe(PipeKind.OPAQUE, GREEN).getDefaultState().with(PipeShape.connection(Direction.WEST), true));
        context.setBlockState(junction.west(), pipe(PipeKind.STAINED_GLASS, BLUE).getDefaultState().with(PipeShape.connection(Direction.EAST), true));
        ((MiniGamePipeBlockEntity) context.getBlockEntity(programmed)).setPage(page.copyWithCount(1));
        return programmed;
    }

    /** A pipe of that colour standing on stone at (x, 2, z), its mouth up, linked to the page. */
    private static BlockPos linked(TestContext context, UUID page, Block block, int x, int z) {
        context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
        BlockPos pos = new BlockPos(x, 2, z);
        context.setBlockState(pos, block.getDefaultState().with(PipeBlock.SOLID, PipeSolid.DOWN));
        MiniGamePages.toggleLink(context.getWorld().getServer(), page, global(context, pos), Direction.UP, MiniGamePipeRole.ofPipe(context.getBlockState(pos)));
        return pos;
    }

    /**
     * Out of a round, a green linked pipe takes the player out by the mini-game pipe he came in by, out of its green
     * mouth (a blue one, out of its blue mouth); the mouth does not take him back at once.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void outOfARoundALinkedPipeLeadsOutByTheMiniGamePipe(TestContext context) {
        ServerWorld world = context.getWorld();
        MinecraftServer server = world.getServer();
        ItemStack stack = new ItemStack(ModItems.MINI_GAME_PAGE);
        UUID page = MiniGamePages.ensureId(stack);
        MiniGamePages.update(server, MiniGamePageData.empty(page).withTexts("Linked " + UUID.randomUUID().toString().substring(0, 4), ""));
        BlockPos programmed = lobby(context, stack);
        BlockPos green = linked(context, page, pipe(PipeKind.OPAQUE, GREEN), 6, 6), blue = linked(context, page, pipe(PipeKind.OPAQUE, BLUE), 3, 6);
        BlockPos greenMouth = programmed.up().east(), blueMouth = programmed.up().west();
        ServerPlayerEntity player = player(context, 6.5, 3, 6.5);
        MiniGamePipes.cameBy(player.getUuid(), page, global(context, programmed), global(context, blueMouth), Direction.WEST);
        context.assertTrue(PipeTravel.enter(world, context.getAbsolutePos(green), Direction.UP, player, 0), "into the green linked pipe");
        when(context, () -> outOf(context, player, greenMouth, Direction.EAST), 60, "never came out of the green mouth of the mini-game pipe", () -> {
            context.assertTrue(PipeTravel.barred(player, context.getAbsolutePos(greenMouth), Direction.EAST), "that mouth does not take him back at once");
            context.waitAndRun(PipeTravel.COOLDOWN + 1, () -> {
                Vec3d at = context.getAbsolute(new Vec3d(3.5, 3, 6.5));
                player.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
                context.assertTrue(PipeTravel.enter(world, context.getAbsolutePos(blue), Direction.UP, player, 0), "into the blue linked pipe");
                when(context, () -> outOf(context, player, blueMouth, Direction.WEST), 60, "never came out of the blue mouth", () -> {
                    TestPlayers.leaveMiniGames(context, player);
                    context.complete();
                });
            });
        });
    }

    /**
     * The last mini-game pipe taken, out of its range (a copper pipe far away): the nearest one in range instead; none
     * in range (the pipe broken): the linked pipe is a pipe like any other. The tiers' ranges.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theLastPipeInRangeElseTheNearestElseAPipe(TestContext context) {
        ServerWorld world = context.getWorld();
        MinecraftServer server = world.getServer();
        ItemStack stack = new ItemStack(ModItems.MINI_GAME_PAGE);
        UUID page = MiniGamePages.ensureId(stack);
        BlockPos programmed = lobby(context, stack);
        GlobalPos here = global(context, new BlockPos(6, 2, 6)), lobbyPipe = global(context, programmed);
        GlobalPos farCopper = GlobalPos.create(world.getRegistryKey(), context.getAbsolutePos(new BlockPos(6, 2, 6)).east(300));
        ServerPlayerEntity player = player(context, 6.5, 3, 6.5);
        try {
            MiniGamePipeIndex.set(server, farCopper, page, MiniGamePipeBlock.Reach.NEAR);
            MiniGamePipes.cameBy(player.getUuid(), page, farCopper, null, null);
            context.assertEquals(MiniGamePipes.wayOutPipe(server, player.getUuid(), page, here), lobbyPipe, "the last one out of range: the nearest in range");
            MiniGamePipeIndex.set(server, farCopper, page, MiniGamePipeBlock.Reach.DIMENSION);
            context.assertEquals(MiniGamePipes.wayOutPipe(server, player.getUuid(), page, here), farCopper, "an iron one, in its dimension: the last one taken");
            MiniGamePipes.cameBy(player.getUuid(), UUID.randomUUID(), farCopper, null, null);
            context.assertEquals(MiniGamePipes.wayOutPipe(server, player.getUuid(), page, here), lobbyPipe, "the last one taken was another page's: the nearest");
            MiniGamePipeIndex.remove(server, farCopper);

            context.assertTrue(MiniGamePipeIndex.inRange(MiniGamePipeBlock.Reach.NEAR, here, GlobalPos.create(here.dimension(), here.pos().east(100)))
                    && !MiniGamePipeIndex.inRange(MiniGamePipeBlock.Reach.NEAR, here, GlobalPos.create(here.dimension(), here.pos().east(101))), "copper: 100 blocks");
            context.assertTrue(MiniGamePipeIndex.inRange(MiniGamePipeBlock.Reach.DIMENSION, here, GlobalPos.create(here.dimension(), here.pos().east(5000)))
                    && !MiniGamePipeIndex.inRange(MiniGamePipeBlock.Reach.DIMENSION, here, GlobalPos.create(World.NETHER, here.pos())), "iron: its dimension");
            context.assertTrue(MiniGamePipeIndex.inRange(MiniGamePipeBlock.Reach.EVERYWHERE, here, GlobalPos.create(World.NETHER, here.pos())), "golden: anywhere");

            // The index: saved and read back (a restart), and a broken pipe forgotten
            MiniGamePipeIndex read = MiniGamePipeIndex.fromNbt(MiniGamePipeIndex.get(server).writeNbt(new NbtCompound(), world.getRegistryManager()), world.getRegistryManager());
            NbtCompound again = read.writeNbt(new NbtCompound(), world.getRegistryManager());
            context.assertTrue(again.getList("Pipes", 10).stream().anyMatch(e -> ((NbtCompound) e).getLong("Pos") == lobbyPipe.pos().asLong()
                    && ((NbtCompound) e).getString("Reach").equals("NEAR")), "saved with its page and its tier");
            context.setBlockState(programmed, Blocks.AIR);
            context.assertTrue(MiniGamePipeIndex.at(server, lobbyPipe) == null, "the broken pipe is forgotten");
            context.assertTrue(MiniGamePipes.wayOutPipe(server, player.getUuid(), page, here) == null, "none in range: the linked pipe is a pipe like any other");
        } finally {
            MiniGamePipeIndex.remove(server, farCopper);
            TestPlayers.leaveMiniGames(context, player);
        }
        context.complete();
    }

    /** During a round of the page, its linked pipes are closed to its players; another page's linked pipes are pipes. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void duringARoundTheLinkedPipesAreClosed(TestContext context) {
        ServerWorld world = context.getWorld();
        ItemStack stack = new ItemStack(ModItems.MINI_GAME_PAGE);
        UUID page = MiniGamePages.ensureId(stack);
        lobby(context, stack);
        BlockPos green = linked(context, page, pipe(PipeKind.OPAQUE, GREEN), 6, 6);
        ServerPlayerEntity player = player(context, 6.5, 3, 6.5);
        MiniGamePipes.enterParty(player.getUuid(), page, () -> true);
        Vec3d before = player.getPos();
        context.assertTrue(PipeTravel.enter(world, context.getAbsolutePos(green), Direction.UP, player, 0), "into the linked pipe");
        context.waitAndRun(5, () -> {
            context.assertTrue(!PipeTravel.isTravelling(player) && player.getPos().distanceTo(before) < 0.01, "nothing happens");
            context.assertTrue(MiniGamePipes.isInParty(player.getUuid()), "still in the round");
            TestPlayers.leaveMiniGames(context, player);
            context.complete();
        });
    }
}
