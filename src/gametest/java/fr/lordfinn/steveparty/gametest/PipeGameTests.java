package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlock;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeGeometry;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeNetworks;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeShape;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeSolid;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeTravel;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.entities.custom.PipeCarrierEntity;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Warp pipes: shapes and connections, the Wrench, going in, travelling, warps. */
public class PipeGameTests implements FabricGameTest {

    private static final Block RED = ModBlocks.PIPES[PipeKind.OPAQUE.ordinal()][14];
    private static final Block GLASS = ModBlocks.GLASS_PIPE;

    /** A pipe of a kind and colour (index in ModBlocks.COLORS). */
    private static Block pipe(PipeKind kind, int color) {
        return ModBlocks.PIPES[kind.ordinal()][kind.colored ? color : 0];
    }

    // ------------------------------------------------------------------ helpers

    /** Places {@code block}'s item by clicking the face {@code side} of the block at {@code clicked}, like a player. */
    private static void place(TestContext context, PlayerEntity player, Block block, BlockPos clicked, Direction side) {
        BlockPos abs = context.getAbsolutePos(clicked);
        ItemStack stack = new ItemStack(block);
        ItemPlacementContext placement = new ItemPlacementContext(player, Hand.MAIN_HAND, stack,
                new BlockHitResult(Vec3d.ofCenter(abs).add(Vec3d.of(side.getVector()).multiply(0.5)), side, abs, false));
        ((BlockItem) stack.getItem()).place(placement);
    }

    /** A pipe state joined on {@code joined}, fixed to {@code solid}. */
    private static BlockState pipe(Block block, PipeSolid solid, Direction... joined) {
        BlockState state = block.getDefaultState().with(PipeBlock.SOLID, solid);
        for (Direction dir : joined) state = state.with(PipeShape.connection(dir), true);
        return state;
    }

    private static boolean mouth(BlockState state, Direction dir) {
        return PipeShape.mouth(state, dir) != null;
    }

    /** Runs {@code then} as soon as {@code condition} holds, failing with {@code what} after {@code ticks} ticks. */
    private static void when(TestContext context, BooleanSupplier condition, int ticks, String what, Runnable then) {
        if (condition.getAsBoolean()) {
            then.run();
        } else if (ticks <= 0) {
            context.throwGameTestException(what);
        } else {
            context.waitAndRun(1, () -> when(context, condition, ticks - 1, what, then));
        }
    }

    private static Vec3d relative(TestContext context, Entity entity) {
        return context.getRelative(entity.getPos());
    }

    /** A run west to east at height 2: a mouth on the west at {@code x0}, one on the east at {@code x1}. */
    private static void straightRun(TestContext context, int x0, int x1, int y, int z) {
        for (int x = x0; x <= x1; x++) {
            List<Direction> joined = new ArrayList<>();
            if (x > x0) joined.add(Direction.WEST);
            if (x < x1) joined.add(Direction.EAST);
            context.setBlockState(new BlockPos(x, y, z), pipe(RED, PipeSolid.NONE, joined.toArray(Direction[]::new)));
        }
    }

    private static ServerPlayerEntity player(TestContext context, Vec3d relativeFeet, float yaw) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        Vec3d abs = context.getAbsolute(relativeFeet);
        player.refreshPositionAndAngles(abs.x, abs.y, abs.z, yaw, 0);
        return player;
    }

    // ------------------------------------------------------------------ shapes and connections

    /** Built by clicking the end of the run: every run end is a mouth, the pipes between are plain lengths. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void runEndsBecomeMouths(TestContext context) {
        PlayerEntity player = context.createMockPlayer(GameMode.CREATIVE);
        for (int x = 1; x <= 3; x++) context.setBlockState(new BlockPos(x, 0, 1), Blocks.STONE);
        place(context, player, RED, new BlockPos(1, 0, 1), Direction.UP);
        BlockState alone = context.getBlockState(new BlockPos(1, 1, 1));
        context.assertTrue(alone.get(PipeBlock.SOLID) == PipeSolid.DOWN, "fixed to the ground it was placed on: " + alone);
        context.assertTrue(mouth(alone, Direction.UP) && !mouth(alone, Direction.DOWN), "alone: a mouth on top, capped in the ground");
        place(context, player, RED, new BlockPos(1, 1, 1), Direction.EAST);
        place(context, player, GLASS, new BlockPos(2, 1, 1), Direction.EAST);
        BlockState first = context.getBlockState(new BlockPos(1, 1, 1));
        BlockState middle = context.getBlockState(new BlockPos(2, 1, 1));
        BlockState last = context.getBlockState(new BlockPos(3, 1, 1));
        context.assertTrue(first.get(PipeShape.connection(Direction.EAST)) && mouth(first, Direction.WEST), "first: joined east, mouth west: " + first);
        context.assertTrue(first.get(PipeBlock.SOLID) == PipeSolid.DOWN, "first still held to the ground (a clamp)");
        context.assertTrue(middle.get(PipeShape.connection(Direction.WEST)) && middle.get(PipeShape.connection(Direction.EAST))
                && PipeShape.ends(middle).isEmpty(), "middle: a length, no mouth: " + middle);
        context.assertTrue(middle.get(PipeBlock.SOLID) == PipeSolid.NONE, "placed against a pipe: fixed to no block");
        context.assertTrue(mouth(last, Direction.EAST) && last.get(PipeShape.connection(Direction.WEST)), "last: mouth east: " + last);
        // Breaking the middle: both sides become run ends again
        context.setBlockState(new BlockPos(2, 1, 1), Blocks.AIR);
        context.assertTrue(mouth(context.getBlockState(new BlockPos(1, 1, 1)), Direction.UP), "first alone again: mouth up");
        context.assertTrue(mouth(context.getBlockState(new BlockPos(3, 1, 1)), Direction.UP), "last alone, fixed to nothing: vertical");
        context.complete();
    }

    /**
     * A pipe sticks to the block it was placed against (one solid block only); a block placed next to it later changes
     * nothing; a pipe placed where its mouth opens joins it.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sticksToTheClickedWallAndOnlyPipesJoin(TestContext context) {
        PlayerEntity player = context.createMockPlayer(GameMode.CREATIVE);
        context.setBlockState(new BlockPos(1, 1, 1), Blocks.STONE);
        context.setBlockState(new BlockPos(2, 0, 1), Blocks.STONE);
        context.setBlockState(new BlockPos(3, 0, 1), Blocks.STONE);
        place(context, player, RED, new BlockPos(1, 1, 1), Direction.EAST);
        BlockPos first = new BlockPos(2, 1, 1);
        BlockState state = context.getBlockState(first);
        context.assertTrue(state.get(PipeBlock.SOLID) == PipeSolid.WEST, "fixed to the clicked wall only (not the ground): " + state);
        context.assertTrue(mouth(state, Direction.EAST), "its mouth faces away from the wall");
        context.setBlockState(new BlockPos(2, 1, 2), Blocks.STONE);
        context.setBlockState(new BlockPos(2, 2, 1), Blocks.STONE);
        context.assertTrue(context.getBlockState(first).equals(state), "a block placed next to it changes nothing");
        // Placed on the ground in front of its mouth: joins it
        place(context, player, RED, new BlockPos(3, 0, 1), Direction.UP);
        BlockState joined = context.getBlockState(first);
        BlockState second = context.getBlockState(new BlockPos(3, 1, 1));
        context.assertTrue(joined.get(PipeShape.connection(Direction.EAST)) && second.get(PipeShape.connection(Direction.WEST)), "the new pipe joined the mouth");
        context.assertTrue(joined.get(PipeBlock.SOLID) == PipeSolid.WEST && !PipeShape.hasMouth(joined), "the first one now goes into the wall (capped)");
        context.assertTrue(second.get(PipeBlock.SOLID) == PipeSolid.DOWN, "the second one is fixed to the ground it was placed on");
        // Its wall broken: the first pipe is fixed to nothing
        context.setBlockState(new BlockPos(1, 1, 1), Blocks.AIR);
        context.assertTrue(context.getBlockState(first).get(PipeBlock.SOLID) == PipeSolid.NONE, "wall gone: fixed to nothing");
        context.complete();
    }

    /** Pipes set one by one by a command (shape updated like /setblock does) keep the connections they are given. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void commandPlacedPipesKeepTheirConnections(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos west = context.getAbsolutePos(new BlockPos(1, 2, 1)), east = west.east();
        world.setBlockState(west, Block.postProcessState(pipe(RED, PipeSolid.NONE, Direction.EAST), world, west), Block.NOTIFY_ALL);
        world.setBlockState(east, Block.postProcessState(pipe(RED, PipeSolid.NONE, Direction.WEST), world, east), Block.NOTIFY_ALL);
        context.assertTrue(world.getBlockState(west).get(PipeShape.connection(Direction.EAST))
                && world.getBlockState(east).get(PipeShape.connection(Direction.WEST)), "joined as given");
        world.setBlockState(east, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        context.assertTrue(!world.getBlockState(west).get(PipeShape.connection(Direction.EAST)), "let go when its neighbour went");
        context.complete();
    }

    /** The Wrench: fixed to the next solid neighbour at each click, then to nothing, then round again. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void wrenchCyclesTheSolidBlock(TestContext context) {
        ServerPlayerEntity player = player(context, new Vec3d(4.5, 1, 4.5), 0);
        BlockPos pos = new BlockPos(2, 1, 2);
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos.west(), Blocks.STONE);
        context.setBlockState(pos.north(), pipe(RED, PipeSolid.NONE, Direction.SOUTH));
        context.setBlockState(pos, pipe(RED, PipeSolid.DOWN, Direction.NORTH));
        ItemStack wrench = new ItemStack(ModItems.WRENCH);
        player.setStackInHand(Hand.MAIN_HAND, wrench);
        BlockPos abs = context.getAbsolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(abs), Direction.UP, abs, false);
        List<PipeSolid> seen = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            context.getBlockState(pos).onUseWithItem(wrench, context.getWorld(), player, Hand.MAIN_HAND, hit);
            seen.add(context.getBlockState(pos).get(PipeBlock.SOLID));
        }
        context.assertTrue(seen.equals(List.of(PipeSolid.WEST, PipeSolid.NONE, PipeSolid.DOWN, PipeSolid.WEST)),
                "down -> west -> none -> down (never the pipe on the north): " + seen);
        context.assertTrue(context.getBlockState(pos).get(PipeShape.connection(Direction.NORTH)), "still joined to its pipe");
        context.complete();
    }

    // ------------------------------------------------------------------ going in

    /** Right click on a mouth: the player goes in, rides through (small) and comes out of the other mouth. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void rightClickTakesThePlayerThrough(TestContext context) {
        straightRun(context, 1, 4, 2, 2);
        ServerPlayerEntity player = player(context, new Vec3d(0.5, 1.5, 2.5), -90);
        BlockPos mouth = context.getAbsolutePos(new BlockPos(1, 2, 2));
        context.getBlockState(new BlockPos(1, 2, 2)).onUse(context.getWorld(), player,
                new BlockHitResult(Vec3d.ofCenter(mouth).add(-0.5, 0, 0), Direction.WEST, mouth, false));
        context.waitAndRun(2, () -> {
            context.assertTrue(player.getVehicle() instanceof PipeCarrierEntity, "riding through the pipe");
            context.assertTrue(player.getHeight() <= PipeTravel.ROOM + 0.01, "shrunk to fit: " + player.getHeight());
            when(context, () -> !player.hasVehicle(), 40, "never came out", () -> {
                Vec3d at = relative(context, player);
                context.assertTrue(at.x > 5 && at.x < 6 && Math.abs(at.z - 2.5) < 0.1, "out of the east mouth: " + at);
                context.assertTrue(player.getAttributeValue(EntityAttributes.SCALE) == 1.0, "back to its size");
                context.assertTrue(player.getVelocity().x > 0, "thrown out eastward");
                context.complete();
            });
        });
    }

    /** Sneaking in front of a mouth, looking at it, close: in. Not looking at it: nothing. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void sneakingInFrontOfAMouthGoesIn(TestContext context) {
        straightRun(context, 1, 4, 2, 2);
        // Eyes level with the pipe, looking east at the west mouth
        ServerPlayerEntity player = player(context, new Vec3d(0.4, 2.5 - 1.27, 2.5), -90);
        ServerPlayerEntity away = player(context, new Vec3d(0.4, 2.5 - 1.27, 5.5), 90);
        player.setSneaking(true);
        away.setSneaking(true);
        context.waitAndRun(3, () -> {
            context.assertTrue(player.getVehicle() instanceof PipeCarrierEntity, "the sneaking player facing the mouth went in");
            context.assertTrue(!away.hasVehicle(), "the one looking away did not");
            context.complete();
        });
    }

    /** Landing fast in an upward mouth: in, without fall damage. Flying fast into a side mouth: in, no damage. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void fastEntryWithoutDamage(TestContext context) {
        // A pipe standing on the ground joined to a length going east: mouth on top, mouth east
        context.setBlockState(new BlockPos(1, 1, 1), Blocks.STONE);
        context.setBlockState(new BlockPos(1, 2, 1), pipe(RED, PipeSolid.DOWN, Direction.EAST));
        context.setBlockState(new BlockPos(2, 2, 1), pipe(RED, PipeSolid.NONE, Direction.WEST, Direction.UP));
        context.setBlockState(new BlockPos(2, 3, 1), pipe(RED, PipeSolid.NONE, Direction.DOWN));
        BlockPos top = new BlockPos(2, 3, 1);
        context.assertTrue(mouth(context.getBlockState(top), Direction.UP), "a mouth on top");
        ServerPlayerEntity faller = player(context, new Vec3d(2.5, 3.8, 1.5), 0);
        float health = faller.getHealth();
        faller.fallDistance = 12;
        BlockPos topAbs = context.getAbsolutePos(top);
        context.getBlockState(top).getBlock().onLandedUpon(context.getWorld(), context.getBlockState(top), topAbs, faller, 12);
        context.assertTrue(faller.getHealth() == health, "no fall damage");
        context.assertTrue(PipeTravel.isTravelling(faller), "went in");
        // Elytra: coming at the west mouth at 1 block per tick
        ServerPlayerEntity flyer = player(context, new Vec3d(1.1, 2.3, 1.5), -90);
        flyer.prevX = flyer.getX() - 1.0;
        flyer.prevY = flyer.getY();
        flyer.prevZ = flyer.getZ();
        BlockPos west = context.getAbsolutePos(new BlockPos(1, 2, 1));
        context.getBlockState(new BlockPos(1, 2, 1)).onEntityCollision(context.getWorld(), west, flyer);
        context.assertTrue(PipeTravel.isTravelling(flyer), "the fast flyer went in");
        float flyerHealth = flyer.getHealth();
        flyer.damage(context.getWorld(), context.getWorld().getDamageSources().flyIntoWall(), 6);
        context.assertTrue(flyer.getHealth() == flyerHealth, "no damage flying into the mouth");
        // Walking slowly into it does nothing (players sneak or click)
        ServerPlayerEntity walker = player(context, new Vec3d(1.1, 2.3, 1.5), -90);
        walker.prevX = walker.getX() - 0.1;
        walker.prevY = walker.getY();
        walker.prevZ = walker.getZ();
        context.getBlockState(new BlockPos(1, 2, 1)).onEntityCollision(context.getWorld(), west, walker);
        context.assertTrue(!PipeTravel.isTravelling(walker), "a walking player does not fall in");
        context.waitAndRun(2, () -> {
            context.assertTrue(faller.getVehicle() instanceof PipeCarrierEntity, "the faller rides through");
            context.assertTrue(((PipeCarrierEntity) faller.getVehicle()).speed() > PipeTravel.BASE_SPEED, "keeps its speed inside");
            context.complete();
        });
    }

    // ------------------------------------------------------------------ travelling

    /** An item dropped in an upward mouth comes out of the other mouth. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void itemsTravel(TestContext context) {
        context.setBlockState(new BlockPos(1, 2, 1), pipe(RED, PipeSolid.NONE, Direction.DOWN));
        context.setBlockState(new BlockPos(1, 1, 1), pipe(GLASS, PipeSolid.NONE, Direction.UP, Direction.EAST));
        context.setBlockState(new BlockPos(2, 1, 1), pipe(GLASS, PipeSolid.NONE, Direction.WEST, Direction.EAST));
        context.setBlockState(new BlockPos(3, 1, 1), pipe(RED, PipeSolid.NONE, Direction.WEST));
        for (int x = 1; x <= 5; x++) context.setBlockState(new BlockPos(x, 0, 1), Blocks.STONE);
        Vec3d drop = context.getAbsolute(new Vec3d(1.5, 3.3, 1.5));
        ItemEntity item = new ItemEntity(context.getWorld(), drop.x, drop.y, drop.z, new ItemStack(Items.DIAMOND, 3));
        item.setVelocity(Vec3d.ZERO);
        context.getWorld().spawnEntity(item);
        when(context, () -> item.getVehicle() instanceof PipeCarrierEntity, 30, "the item never went in", () ->
                when(context, () -> !item.hasVehicle(), 40, "the item never came out", () -> {
                    Vec3d at = relative(context, item);
                    context.assertTrue(at.x > 3.9 && Math.abs(at.z - 1.5) < 0.5, "out of the east mouth: " + at);
                    context.assertTrue(item.isAlive() && item.getStack().isOf(Items.DIAMOND) && item.getStack().getCount() == 3, "same items");
                    context.complete();
                }));
    }

    /**
     * A capped end (a pipe going into the ground) warps to the nearest mouth of the same colour in another network
     * (an opaque and a windowed pipe of one plastic colour are the same colour); a nearer mouth of another colour is not
     * a warp.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void cappedEndWarpsToTheNearestMouthOfItsColour(TestContext context) {
        Block lime = pipe(PipeKind.OPAQUE, 5);
        // The warp pipe: one block on the ground, a mouth on top, capped into the ground
        context.setBlockState(new BlockPos(1, 1, 1), Blocks.STONE);
        context.setBlockState(new BlockPos(1, 2, 1), pipe(lime, PipeSolid.DOWN));
        // Nearer, but cyan
        context.setBlockState(new BlockPos(3, 1, 1), Blocks.STONE);
        context.setBlockState(new BlockPos(3, 2, 1), pipe(pipe(PipeKind.OPAQUE, 9), PipeSolid.DOWN));
        // The nearest lime mouth (a windowed pipe), and a farther lime one
        context.setBlockState(new BlockPos(5, 1, 1), Blocks.STONE);
        context.setBlockState(new BlockPos(5, 2, 1), pipe(pipe(PipeKind.WINDOWED, 5), PipeSolid.DOWN));
        context.setBlockState(new BlockPos(6, 1, 6), Blocks.STONE);
        context.setBlockState(new BlockPos(6, 2, 6), pipe(lime, PipeSolid.DOWN));
        PigEntity pig = context.spawnEntity(EntityType.PIG, new BlockPos(1, 3, 1));
        pig.setAiDisabled(true);
        context.assertTrue(PipeTravel.enter(context.getWorld(), context.getAbsolutePos(new BlockPos(1, 2, 1)), Direction.UP, pig, 0), "in");
        when(context, () -> pig.getVehicle() instanceof PipeCarrierEntity, 5, "the pig never went in", () ->
                when(context, () -> !pig.hasVehicle() && relative(context, pig).x > 4, 60, "the pig never came out of the nearest lime mouth", () -> {
                    Vec3d at = relative(context, pig);
                    context.assertTrue(Math.abs(at.x - 5.5) < 0.6 && Math.abs(at.z - 1.5) < 0.6 && at.y >= 3, "on top of the nearest lime mouth: " + at);
                    context.assertTrue(pig.getHealth() == pig.getMaxHealth(), "unhurt");
                    context.complete();
                }));
    }

    /** Warps reach {@link PipeNetworks#WARP_RADIUS} (100) blocks: 101 blocks away is too far, 99 is fine. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void warpsReachAHundredBlocks(TestContext context) {
        Block orange = pipe(PipeKind.OPAQUE, 1);
        context.assertTrue(PipeNetworks.WARP_RADIUS == 100, "100 blocks");
        context.setBlockState(new BlockPos(1, 1, 1), Blocks.STONE);
        context.setBlockState(new BlockPos(1, 2, 1), pipe(orange, PipeSolid.DOWN));
        // The far pipes' chunk stays loaded (a player would be around)
        BlockPos far = context.getAbsolutePos(new BlockPos(1, 2, 100));
        ServerWorld world = context.getWorld();
        world.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
        world.setChunkForced(far.getX() >> 4, (far.getZ() + 2) >> 4, true);
        context.setBlockState(new BlockPos(1, 1, 102), Blocks.STONE);
        context.setBlockState(new BlockPos(1, 2, 102), pipe(orange, PipeSolid.DOWN));
        ItemEntity item = new ItemEntity(context.getWorld(), 0, 0, 0, new ItemStack(Items.CARROT));
        item.setPosition(context.getAbsolute(new Vec3d(1.5, 2.9, 1.5)));
        context.getWorld().spawnEntity(item);
        BlockPos warp = context.getAbsolutePos(new BlockPos(1, 2, 1));
        context.assertTrue(PipeTravel.enter(context.getWorld(), warp, Direction.UP, item, 0), "in");
        when(context, () -> item.age > 3 && !item.hasVehicle(), 60, "never came out", () -> {
            context.assertTrue(relative(context, item).z < 3, "101 blocks: too far, back out where it went in: " + relative(context, item));
            context.setBlockState(new BlockPos(1, 1, 100), Blocks.STONE);
            context.setBlockState(new BlockPos(1, 2, 100), pipe(orange, PipeSolid.DOWN));
            context.waitAndRun(PipeTravel.COOLDOWN + 1, () -> {
                item.setVelocity(Vec3d.ZERO);
                context.assertTrue(PipeTravel.enter(context.getWorld(), warp, Direction.UP, item, 0), "in again");
                int age = item.age;
                when(context, () -> item.age > age + 3 && !item.hasVehicle(), 60, "never came out again", () -> {
                    Vec3d at = relative(context, item);
                    world.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false);
                    world.setChunkForced(far.getX() >> 4, (far.getZ() + 2) >> 4, false);
                    context.assertTrue(Math.abs(at.z - 100.5) < 0.6 && Math.abs(at.x - 1.5) < 0.6, "99 blocks: out of that mouth: " + at);
                    context.complete();
                });
            });
        });
    }

    /** Without a capped end, a traveller comes out of one of the other mouths of the network (never where it went in). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void openNetworkSendsToAnotherMouth(TestContext context) {
        BlockPos center = new BlockPos(3, 2, 3);
        context.setBlockState(center, pipe(RED, PipeSolid.NONE, Direction.WEST, Direction.EAST, Direction.SOUTH));
        context.setBlockState(center.west(), pipe(RED, PipeSolid.NONE, Direction.EAST));
        context.setBlockState(center.east(), pipe(RED, PipeSolid.NONE, Direction.WEST));
        context.setBlockState(center.south(), pipe(RED, PipeSolid.NONE, Direction.NORTH));
        List<ItemEntity> items = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Vec3d at = context.getAbsolute(new Vec3d(1.8, 2.5, 3.5));
            ItemEntity item = new ItemEntity(context.getWorld(), at.x, at.y, at.z, new ItemStack(Items.EMERALD));
            item.setNoGravity(true);
            context.getWorld().spawnEntity(item);
            context.assertTrue(PipeTravel.enter(context.getWorld(), context.getAbsolutePos(center.west()), Direction.WEST, item, 0), "in");
            items.add(item);
        }
        when(context, () -> items.stream().allMatch(item -> item.age > 3 && !item.hasVehicle()), 60, "not all out", () -> {
            for (ItemEntity item : items) {
                Vec3d at = relative(context, item);
                boolean east = at.x > 4.9, south = at.z > 4.9;
                context.assertTrue(east || south, "out of the east or south mouth, not the west one: " + at);
            }
            context.complete();
        });
    }

    /** A token (tokenized mob) goes through and warps as it is: same entity, still a token, same owner, its size back. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void tokensSurviveTheTrip(TestContext context) {
        context.setBlockState(new BlockPos(1, 1, 1), Blocks.STONE);
        context.setBlockState(new BlockPos(1, 2, 1), pipe(pipe(PipeKind.OPAQUE, 6), PipeSolid.DOWN));
        context.setBlockState(new BlockPos(3, 1, 4), Blocks.STONE);
        context.setBlockState(new BlockPos(3, 2, 4), pipe(pipe(PipeKind.OPAQUE, 6), PipeSolid.DOWN));
        PigEntity pig = context.spawnEntity(EntityType.PIG, new BlockPos(1, 3, 1));
        TokenizedEntityInterface token = (TokenizedEntityInterface) pig;
        token.steveparty$setTokenized(true);
        UUID owner = UUID.randomUUID();
        token.steveparty$setTokenOwner(owner);
        pig.setCustomName(net.minecraft.text.Text.literal("Pawn"));
        UUID id = pig.getUuid();
        double scale = pig.getAttributeValue(EntityAttributes.SCALE);
        context.assertTrue(PipeTravel.enter(context.getWorld(), context.getAbsolutePos(new BlockPos(1, 2, 1)), Direction.UP, pig, 0), "in");
        when(context, () -> pig.getVehicle() instanceof PipeCarrierEntity, 5, "the token never went in", () ->
                when(context, () -> !pig.hasVehicle() && relative(context, pig).z > 3, 60, "the token never came out", () -> {
                    ServerWorld world = context.getWorld();
                    context.assertTrue(pig.isAlive() && world.getEntity(id) == pig, "the same entity");
                    context.assertTrue(token.steveparty$isTokenized(), "still a token");
                    context.assertTrue(owner.equals(token.steveparty$getTokenOwner()), "same owner");
                    context.assertTrue(pig.hasCustomName() && pig.getCustomName().getString().equals("Pawn"), "same name");
                    context.assertTrue(pig.getAttributeValue(EntityAttributes.SCALE) == scale, "its size back");
                    Vec3d at = relative(context, pig);
                    context.assertTrue(Math.abs(at.x - 3.5) < 0.6 && Math.abs(at.z - 4.5) < 0.6, "out of the other pipe: " + at);
                    context.complete();
                }));
    }

    /**
     * Nowhere to warp to (no mouth of its colour near, only one of another colour and one of its colour with a block in
     * front of it): the traveller travels back up through the pipe (still riding) and comes out of the mouth it went in.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void warpWithNowhereToGoTravelsBack(TestContext context) {
        Block purple = pipe(PipeKind.STAINED_GLASS, 10);
        context.setBlockState(new BlockPos(1, 0, 1), Blocks.STONE);
        context.setBlockState(new BlockPos(1, 1, 1), pipe(purple, PipeSolid.DOWN, Direction.UP));
        context.setBlockState(new BlockPos(1, 2, 1), pipe(purple, PipeSolid.NONE, Direction.DOWN, Direction.UP));
        context.setBlockState(new BlockPos(1, 3, 1), pipe(purple, PipeSolid.NONE, Direction.DOWN));
        context.setBlockState(new BlockPos(4, 0, 1), Blocks.STONE);
        context.setBlockState(new BlockPos(4, 1, 1), pipe(GLASS, PipeSolid.DOWN));
        context.setBlockState(new BlockPos(4, 0, 4), Blocks.STONE);
        context.setBlockState(new BlockPos(4, 1, 4), pipe(purple, PipeSolid.DOWN));
        context.setBlockState(new BlockPos(4, 2, 4), Blocks.STONE);
        ItemEntity item = new ItemEntity(context.getWorld(), 0, 0, 0, new ItemStack(Items.APPLE));
        item.setPosition(context.getAbsolute(new Vec3d(1.5, 3.9, 1.5)));
        context.getWorld().spawnEntity(item);
        context.assertTrue(PipeTravel.enter(context.getWorld(), context.getAbsolutePos(new BlockPos(1, 3, 1)), Direction.UP, item, 0), "in");
        boolean[] cameBack = {false};
        when(context, () -> {
            if (item.getVehicle() instanceof PipeCarrierEntity carrier && carrier.hasReturned()) cameBack[0] = true;
            return item.age > 3 && !item.hasVehicle();
        }, 60, "never came out", () -> {
            Vec3d out = relative(context, item);
            context.assertTrue(cameBack[0], "travelled back through the pipe");
            context.assertTrue(Math.abs(out.x - 1.5) < 0.5 && Math.abs(out.z - 1.5) < 0.5 && out.y >= 3.9, "back out of the mouth it went in: " + out);
            context.complete();
        });
    }

    /**
     * Any pipe joins any other (kinds and colours mixed) when placed against it or in front of its mouth; placed beside a
     * pipe, it does not join it.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pipesOfAnyKindJoinWhenPlacedTowardEachOther(TestContext context) {
        PlayerEntity player = context.createMockPlayer(GameMode.CREATIVE);
        for (int x = 1; x <= 4; x++) for (int z = 1; z <= 2; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
        Block windowed = pipe(PipeKind.WINDOWED, 3), stained = pipe(PipeKind.STAINED_GLASS, 13);
        place(context, player, RED, new BlockPos(1, 0, 1), Direction.UP);
        place(context, player, windowed, new BlockPos(1, 1, 1), Direction.EAST);
        context.assertTrue(context.getBlockState(new BlockPos(1, 1, 1)).get(PipeShape.connection(Direction.EAST))
                && context.getBlockState(new BlockPos(2, 1, 1)).get(PipeShape.connection(Direction.WEST)), "a windowed pipe placed against an opaque one joins it");
        // Beside the windowed pipe (on the ground next to it): alone
        place(context, player, GLASS, new BlockPos(2, 0, 2), Direction.UP);
        BlockState beside = context.getBlockState(new BlockPos(2, 1, 2));
        context.assertTrue(PipeShape.mask(beside) == 0 && !context.getBlockState(new BlockPos(2, 1, 1)).get(PipeShape.connection(Direction.SOUTH)),
                "placed beside a pipe: not joined: " + beside);
        // Against the glass pipe: joined; in front of the windowed pipe's mouth: joined, and not to the glass beside
        place(context, player, stained, new BlockPos(2, 1, 2), Direction.EAST);
        context.assertTrue(context.getBlockState(new BlockPos(3, 1, 2)).get(PipeShape.connection(Direction.WEST)), "stained glass against plain glass: joined");
        place(context, player, stained, new BlockPos(3, 0, 1), Direction.UP);
        BlockState front = context.getBlockState(new BlockPos(3, 1, 1));
        context.assertTrue(front.get(PipeShape.connection(Direction.WEST)) && !front.get(PipeShape.connection(Direction.SOUTH)),
                "in front of the windowed pipe's mouth: joined to it only: " + front);
        context.complete();
    }

    /**
     * Every shape of pipe of every kind (straight, bends, junctions up to 6 ways, clamps, mouths, capped ends) is drawn
     * cleanly: inside the pipe's outline (nothing sticks out where branches meet), no two quads on the same plane over
     * each other (no z-fighting), texture coordinates inside the texture, the block's faces (and only them) culled
     * against the block beside. A straight length has no rim nor clamp part.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void modelsStayInsideTheTube(TestContext context) {
        for (PipeKind kind : PipeKind.values()) {
            for (int key = 0; key < PipeShape.KEYS; key++) {
                int mask = PipeShape.maskOf(key);
                Direction solid = PipeShape.solidOf(key);
                if (solid != null && (mask & 1 << solid.ordinal()) != 0) continue;
                PipeShape.Face[] faces = PipeShape.faces(mask, solid);
                List<float[]> outline = outline(faces);
                String shape = kind + " shape " + key;
                boolean straight = solid == null && Integer.bitCount(mask) == 2 && ((mask & 3) == 3 || (mask & 12) == 12 || (mask & 48) == 48);
                // facing, plane, then the rectangle on the two other axes
                List<float[]> rects = new ArrayList<>();
                PipeGeometry.build(key, kind, (facing, corners, uvs, sprite, cull) -> {
                    float[] center = new float[3], lo = {16, 16, 16}, hi = {0, 0, 0};
                    for (float[] corner : corners) {
                        for (int i = 0; i < 3; i++) {
                            center[i] += corner[i] / 4;
                            lo[i] = Math.min(lo[i], corner[i]);
                            hi[i] = Math.max(hi[i], corner[i]);
                        }
                    }
                    context.assertTrue(inside(outline, center), shape + ": a quad out of the tube at " + java.util.Arrays.toString(center));
                    for (float[] corner : corners) {
                        float[] in = new float[3];
                        for (int i = 0; i < 3; i++) in[i] = corner[i] + (center[i] - corner[i]) * 0.02f;
                        context.assertTrue(inside(outline, in), shape + ": a quad out of the tube at " + java.util.Arrays.toString(in));
                    }
                    for (float[] uv : uvs) {
                        context.assertTrue(uv[0] >= 0 && uv[0] <= 16 && uv[1] >= 0 && uv[1] <= 16, shape + ": texture coordinates out of the texture");
                    }
                    int a = facing.getAxis().ordinal();
                    boolean onFace = lo[a] == (facing.getDirection() == Direction.AxisDirection.POSITIVE ? 16 : 0);
                    context.assertTrue(onFace == (cull == facing) && (cull == null || cull == facing), shape + ": cull face " + cull + " of a quad facing " + facing);
                    if (straight) context.assertTrue(sprite != PipeGeometry.RIM, shape + ": a straight length is tube walls only");
                    int b = (a + 1) % 3, c = (a + 2) % 3;
                    rects.add(new float[]{facing.ordinal(), lo[a], lo[b], hi[b], lo[c], hi[c]});
                });
                for (int i = 0; i < rects.size(); i++) {
                    for (int j = i + 1; j < rects.size(); j++) {
                        float[] r = rects.get(i), q = rects.get(j);
                        if (r[0] != q[0] || r[1] != q[1]) continue;
                        boolean overlap = Math.min(r[3], q[3]) - Math.max(r[2], q[2]) > 1e-4 && Math.min(r[5], q[5]) - Math.max(r[4], q[4]) > 1e-4;
                        context.assertFalse(overlap, shape + ": two quads over each other on the same plane (z-fighting)");
                    }
                }
            }
        }
        context.complete();
    }

    /** The tube's outline (pixels): its body, its arms, rims, flanges and clamps. */
    private static List<float[]> outline(PipeShape.Face[] faces) {
        List<float[]> boxes = new ArrayList<>();
        boxes.add(new float[]{1, 1, 1, 15, 15, 15});
        for (Direction dir : Direction.values()) {
            float across0, across1, length;
            switch (faces[dir.ordinal()]) {
                case CONNECTED -> { across0 = 1; across1 = 15; length = 1; }
                case MOUTH -> { across0 = 0; across1 = 16; length = PipeShape.RIM; }
                case CAPPED -> { across0 = 0; across1 = 16; length = PipeShape.FLANGE; }
                case CLAMP -> { across0 = 5; across1 = 11; length = 1; }
                default -> { continue; }
            }
            int axis = dir.getAxis().ordinal();
            boolean positive = dir.getDirection() == Direction.AxisDirection.POSITIVE;
            float[] box = {across0, across0, across0, across1, across1, across1};
            box[axis] = positive ? 16 - length : 0;
            box[axis + 3] = positive ? 16 : length;
            boxes.add(box);
        }
        return boxes;
    }

    private static boolean inside(List<float[]> boxes, float[] point) {
        for (float[] box : boxes) {
            if (point[0] >= box[0] - 1e-4 && point[0] <= box[3] + 1e-4 && point[1] >= box[1] - 1e-4 && point[1] <= box[4] + 1e-4
                    && point[2] >= box[2] - 1e-4 && point[2] <= box[5] + 1e-4) return true;
        }
        return false;
    }

    /**
     * The wall sheets (a tile per set of open sides of a face): the pixels along an open side are those of the straight
     * length going that way, so that the tube's lines go on from block to block whatever each block's shape (bend,
     * junction...); a windowed pipe's window goes on to the block's end.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pipeTexturesGoOnAcrossTheJoints(TestContext context) {
        int[] across = {PipeGeometry.LEFT | PipeGeometry.RIGHT, PipeGeometry.TOP | PipeGeometry.BOTTOM};
        for (PipeKind kind : new PipeKind[]{PipeKind.OPAQUE, PipeKind.WINDOWED}) {
            for (String part : new String[]{"outer", "inner"}) {
                String path = "/assets/steveparty/textures/block/pipe/" + kind.folder + "/red_" + part + ".png";
                try (java.io.InputStream in = PipeGameTests.class.getResourceAsStream(path)) {
                    context.assertTrue(in != null, path + " found");
                    java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(in);
                    context.assertTrue(image.getWidth() == 16 * PipeGeometry.TILES && image.getHeight() == 16 * PipeGeometry.TILES, path + ": 4 x 4 tiles");
                    for (int open = 0; open < 16; open++) {
                        for (int side : new int[]{PipeGeometry.LEFT, PipeGeometry.RIGHT, PipeGeometry.TOP, PipeGeometry.BOTTOM}) {
                            if ((open & side) == 0) continue;
                            boolean vertical = side == PipeGeometry.LEFT || side == PipeGeometry.RIGHT;
                            int straight = vertical ? across[0] : across[1];
                            int edge = side == PipeGeometry.LEFT || side == PipeGeometry.TOP ? 0 : 15;
                            for (int i = 0; i < 16; i++) {
                                int s = vertical ? edge : i, t = vertical ? i : edge;
                                int mine = image.getRGB(open % 4 * 16 + s, open / 4 * 16 + t);
                                int theirs = image.getRGB(straight % 4 * 16 + s, straight / 4 * 16 + t);
                                context.assertTrue(mine == theirs, path + ": tile " + open + " does not go on like a straight length at " + s + "," + t);
                            }
                        }
                    }
                    if (kind == PipeKind.WINDOWED && part.equals("outer")) {
                        int straight = across[1];
                        for (int t : new int[]{0, 15}) {
                            context.assertTrue((image.getRGB(straight % 4 * 16 + 8, straight / 4 * 16 + t) >>> 24) == 0, "the window goes on to the block's end");
                        }
                    }
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        context.complete();
    }

    /**
     * Two rims side by side hide each other's faces between them (the same for the same glass); a glass rim hides
     * nothing (seen through), nor does a pipe's side that does not cover the face.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rimsSideBySideHideTheirFacesBetweenThem(TestContext context) {
        BlockState warp = pipe(RED, PipeSolid.DOWN), glass = pipe(GLASS, PipeSolid.DOWN);
        BlockState stained = pipe(pipe(PipeKind.STAINED_GLASS, 14), PipeSolid.DOWN), windowed = pipe(pipe(PipeKind.WINDOWED, 3), PipeSolid.DOWN);
        context.assertTrue(warp.isSideInvisible(warp, Direction.EAST), "opaque rim beside an opaque rim: hidden");
        context.assertTrue(warp.isSideInvisible(windowed, Direction.EAST), "opaque rim beside a windowed rim: hidden");
        context.assertTrue(glass.isSideInvisible(glass, Direction.EAST), "glass rim beside a glass rim: hidden");
        context.assertFalse(warp.isSideInvisible(glass, Direction.EAST), "opaque rim beside a glass rim: seen through the glass");
        context.assertFalse(glass.isSideInvisible(stained, Direction.EAST), "glass beside another glass: seen through");
        context.assertFalse(warp.isSideInvisible(pipe(RED, PipeSolid.NONE, Direction.NORTH, Direction.SOUTH), Direction.EAST),
                "beside a pipe's side (not covering it): drawn");
        context.complete();
    }

    // ------------------------------------------------------------------ coming out, falling in, pipes changed

    /**
     * Out of an upward mouth, a player pops off to the side it looks; landing back on that mouth (falling, sneaking)
     * does not take it in again before it has left the space above it; once it has, a fall takes it in again.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void outOfAMouthNotBackInBeforeLeavingIt(TestContext context) {
        for (int x = 1; x <= 3; x++) context.setBlockState(new BlockPos(x, 0, 1), Blocks.STONE);
        context.setBlockState(new BlockPos(1, 2, 1), pipe(RED, PipeSolid.NONE, Direction.DOWN));
        context.setBlockState(new BlockPos(1, 1, 1), pipe(RED, PipeSolid.NONE, Direction.UP, Direction.EAST));
        context.setBlockState(new BlockPos(2, 1, 1), pipe(RED, PipeSolid.NONE, Direction.WEST, Direction.EAST));
        context.setBlockState(new BlockPos(3, 1, 1), pipe(RED, PipeSolid.NONE, Direction.WEST, Direction.UP));
        BlockPos exit = new BlockPos(3, 2, 1);
        context.setBlockState(exit, pipe(RED, PipeSolid.NONE, Direction.DOWN));
        BlockPos exitAbs = context.getAbsolutePos(exit);
        ServerPlayerEntity player = player(context, new Vec3d(1.5, 3, 1.5), 0);
        context.assertTrue(PipeTravel.enter(context.getWorld(), context.getAbsolutePos(new BlockPos(1, 2, 1)), Direction.UP, player, 0), "in");
        when(context, () -> player.getVehicle() instanceof PipeCarrierEntity, 5, "never went in", () ->
                when(context, () -> !player.hasVehicle(), 60, "never came out", () -> {
                    Vec3d at = relative(context, player);
                    context.assertTrue(Math.abs(at.x - 3.5) < 0.1 && at.y >= 3 - 1e-3, "on top of the other mouth: " + at);
                    Vec3d velocity = player.getVelocity();
                    context.assertTrue(velocity.y > 0.4 && velocity.horizontalLength() > 0.15 && velocity.z > 0,
                            "pops up, and off to the side it looks (south): " + velocity);
                    context.assertTrue(PipeTravel.barred(player, exitAbs, Direction.UP), "that mouth is barred to it");
                    // Falling back on it from high above, still in the space above it: not in
                    context.waitAndRun(PipeTravel.COOLDOWN + 1, () -> {
                        Vec3d top = context.getAbsolute(new Vec3d(3.5, 3, 1.5));
                        player.requestTeleport(top.x, top.y, top.z);
                        player.fallDistance = 6;
                        context.assertFalse(PipeTravel.fallOnto(context.getWorld(), player), "falling back in the mouth it came out of: not in");
                        context.assertFalse(PipeTravel.land(context.getWorld(), exitAbs, context.getBlockState(exit), player, 6), "nor by landing on it");
                        // Gone off (beside the pipe), then falling on it again: in
                        Vec3d away = context.getAbsolute(new Vec3d(5.5, 1, 1.5));
                        player.requestTeleport(away.x, away.y, away.z);
                        context.waitAndRun(2, () -> {
                            context.assertFalse(PipeTravel.barred(player, exitAbs, Direction.UP), "no more barred once it has gone off");
                            player.requestTeleport(top.x, top.y, top.z);
                            player.fallDistance = 6;
                            context.assertTrue(PipeTravel.fallOnto(context.getWorld(), player), "a fall on it takes it in again");
                            context.complete();
                        });
                    });
                }));
    }

    /**
     * A fall of 3 blocks or more onto an upward mouth always takes the traveller in, unhurt, even landing on its rim
     * with its middle over the block beside it; a small fall there does not.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void fallingThreeBlocksOnAMouthAlwaysGoesIn(TestContext context) {
        for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++) context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
        context.setBlockState(new BlockPos(2, 1, 2), pipe(RED, PipeSolid.DOWN));
        context.setBlockState(new BlockPos(3, 1, 2), Blocks.STONE);
        context.setBlockState(new BlockPos(2, 1, 4), pipe(RED, PipeSolid.DOWN));
        context.setBlockState(new BlockPos(3, 1, 4), Blocks.STONE);
        // Its middle over the stone beside the pipe, its side over the pipe's rim
        PigEntity high = context.spawnEntity(EntityType.PIG, new Vec3d(3.1, 7, 2.5));
        PigEntity low = context.spawnEntity(EntityType.PIG, new Vec3d(3.1, 3.5, 4.5));
        for (PigEntity pig : List.of(high, low)) pig.setVelocity(Vec3d.ZERO);
        when(context, () -> PipeTravel.isTravelling(high), 40, "the pig that fell 5 blocks on the rim did not go in", () -> {
            context.assertTrue(high.getHealth() == high.getMaxHealth(), "unhurt: " + high.getHealth());
            when(context, () -> low.isOnGround(), 30, "the other pig never landed", () -> {
                context.assertFalse(PipeTravel.isTravelling(low), "a fall of 1.5 blocks on the rim beside: not in");
                context.complete();
            });
        });
    }

    /** A pipe lengthened past the mouth a traveller is heading for, on the way: it comes out of the new end. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void aMouthLengthenedOnTheWayIsWhereItComesOut(TestContext context) {
        straightRun(context, 1, 5, 2, 2);
        ItemEntity item = new ItemEntity(context.getWorld(), 0, 0, 0, new ItemStack(Items.GOLD_INGOT));
        item.setPosition(context.getAbsolute(new Vec3d(0.8, 2.5, 2.5)));
        item.setNoGravity(true);
        context.getWorld().spawnEntity(item);
        context.assertTrue(PipeTravel.enter(context.getWorld(), context.getAbsolutePos(new BlockPos(1, 2, 2)), Direction.WEST, item, 0), "in");
        when(context, () -> item.getVehicle() instanceof PipeCarrierEntity, 5, "never went in", () -> {
            context.setBlockState(new BlockPos(5, 2, 2), pipe(RED, PipeSolid.NONE, Direction.WEST, Direction.EAST));
            context.setBlockState(new BlockPos(6, 2, 2), pipe(RED, PipeSolid.NONE, Direction.WEST));
            when(context, () -> !item.hasVehicle(), 60, "never came out", () -> {
                Vec3d at = relative(context, item);
                context.assertTrue(at.x > 6.9 && Math.abs(at.z - 2.5) < 0.2, "out of the new east mouth (not inside the new pipe): " + at);
                context.complete();
            });
        });
    }

    /** Pipes added between two trips: the second goes the new way (the network is worked out again). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void aNetworkChangedBetweenTripsIsUsedAsItIs(TestContext context) {
        straightRun(context, 1, 4, 2, 2);
        BlockPos west = context.getAbsolutePos(new BlockPos(1, 2, 2));
        ItemEntity first = new ItemEntity(context.getWorld(), 0, 0, 0, new ItemStack(Items.IRON_INGOT));
        first.setPosition(context.getAbsolute(new Vec3d(0.8, 2.5, 2.5)));
        first.setNoGravity(true);
        context.getWorld().spawnEntity(first);
        context.assertTrue(PipeTravel.enter(context.getWorld(), west, Direction.WEST, first, 0), "in");
        when(context, () -> first.age > 3 && !first.hasVehicle(), 40, "the first never came out", () -> {
            context.assertTrue(relative(context, first).x > 4.9, "out of the east mouth: " + relative(context, first));
            first.discard();
            context.setBlockState(new BlockPos(4, 2, 2), pipe(RED, PipeSolid.NONE, Direction.WEST, Direction.EAST));
            context.setBlockState(new BlockPos(5, 2, 2), pipe(RED, PipeSolid.NONE, Direction.WEST, Direction.EAST));
            context.setBlockState(new BlockPos(6, 2, 2), pipe(RED, PipeSolid.NONE, Direction.WEST));
            ItemEntity second = new ItemEntity(context.getWorld(), 0, 0, 0, new ItemStack(Items.IRON_INGOT));
            second.setPosition(context.getAbsolute(new Vec3d(0.8, 2.5, 2.5)));
            second.setNoGravity(true);
            context.getWorld().spawnEntity(second);
            context.assertTrue(PipeTravel.enter(context.getWorld(), west, Direction.WEST, second, 0), "the second in");
            when(context, () -> second.age > 3 && !second.hasVehicle(), 40, "the second never came out", () -> {
                Vec3d at = relative(context, second);
                context.assertTrue(at.x > 6.9, "out of the new east mouth: " + at);
                context.complete();
            });
        });
    }

    /** A floating item (no gravity), at {@code relative}. */
    private static ItemEntity floating(TestContext context, Vec3d relative) {
        Vec3d at = context.getAbsolute(relative);
        ItemEntity item = new ItemEntity(context.getWorld(), at.x, at.y, at.z, new ItemStack(Items.SLIME_BALL));
        item.setVelocity(Vec3d.ZERO);
        item.setNoGravity(true);
        context.getWorld().spawnEntity(item);
        return item;
    }

    /** The mouth each traveller last came out of. */
    private static final java.util.Map<Entity, BlockPos> ARRIVALS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    static {
        PipeTravel.ARRIVED.register((world, entity, mouth, opening) -> ARRIVALS.put(entity, mouth.toImmutable()));
    }

    /** Sends a new item through the warp at {@code warp}: it must come out of the mouth at {@code mouth} (relative). */
    private static void warpThrough(TestContext context, BlockPos warp, BlockPos mouth, String what, Runnable then) {
        ItemEntity item = floating(context, context.getRelative(Vec3d.ofCenter(warp).add(0, 0.4, 0)));
        context.assertTrue(PipeTravel.enter(context.getWorld(), warp, Direction.UP, item, 0), "in");
        when(context, () -> item.age > 3 && !item.hasVehicle(), 60, "never came out", () -> {
            BlockPos out = ARRIVALS.get(item);
            Vec3d at = relative(context, item);
            context.assertTrue(context.getAbsolutePos(mouth).equals(out) && Math.abs(at.x - (mouth.getX() + 0.5)) < 0.6 && at.y >= mouth.getY() + 1 - 1e-3,
                    what + ": came out of " + (out == null ? null : context.getRelativePos(out)) + " at " + at);
            item.discard();
            then.run();
        });
    }

    /** A warp mouth lengthened upward, then shortened back: each warp comes out of its top as it is then. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void warpsComeOutOfTheMouthAsItIsNow(TestContext context) {
        Block lime = pipe(PipeKind.OPAQUE, 5);
        context.setBlockState(new BlockPos(1, 1, 1), Blocks.STONE);
        context.setBlockState(new BlockPos(1, 2, 1), pipe(lime, PipeSolid.DOWN));
        context.setBlockState(new BlockPos(5, 1, 1), Blocks.STONE);
        BlockPos destination = new BlockPos(5, 2, 1);
        context.setBlockState(destination, pipe(lime, PipeSolid.DOWN));
        BlockPos warp = context.getAbsolutePos(new BlockPos(1, 2, 1));
        warpThrough(context, warp, destination, "out on top of the destination", () -> {
            // Lengthened by a pipe on top: its old mouth goes into the ground now (a warp), the new top is the mouth
            context.setBlockState(destination, pipe(lime, PipeSolid.DOWN, Direction.UP));
            context.setBlockState(destination.up(), pipe(lime, PipeSolid.NONE, Direction.DOWN));
            warpThrough(context, warp, destination.up(), "out on top of the lengthened pipe", () -> {
                // Shortened back
                context.setBlockState(destination.up(), Blocks.AIR);
                context.assertTrue(mouth(context.getBlockState(destination), Direction.UP), "a mouth on top again");
                warpThrough(context, warp, destination, "out on top of the shortened pipe", context::complete);
            });
        });
    }

    /**
     * Out of a downward mouth with the ground one block under it: a player (taller than the gap) stands on the ground,
     * not in it; with room, it hangs just under the mouth.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void downwardMouthExitNeverInTheGround(TestContext context) {
        context.setBlockState(new BlockPos(1, 4, 1), pipe(RED, PipeSolid.NONE, Direction.EAST));
        context.setBlockState(new BlockPos(2, 4, 1), pipe(RED, PipeSolid.NONE, Direction.WEST, Direction.DOWN));
        BlockPos down = new BlockPos(2, 3, 1);
        context.setBlockState(down, pipe(RED, PipeSolid.NONE, Direction.UP));
        context.setBlockState(new BlockPos(2, 1, 1), Blocks.STONE);
        context.assertTrue(mouth(context.getBlockState(down), Direction.DOWN), "a mouth facing down");
        ServerPlayerEntity player = player(context, new Vec3d(0.4, 3.6, 1.5), -90);
        PipeNetworks.End end = new PipeNetworks.End(context.getAbsolutePos(down), Direction.DOWN, false);
        Vec3d roomy = context.getRelative(PipeTravel.standPos(context.getWorld(), context.spawnEntity(EntityType.CHICKEN, new BlockPos(5, 1, 5)), end));
        context.assertTrue(Math.abs(roomy.y - (3 - EntityType.CHICKEN.getHeight() - 0.01)) < 1e-3, "a small one hangs just under the mouth: " + roomy);
        context.assertTrue(PipeTravel.enter(context.getWorld(), context.getAbsolutePos(new BlockPos(1, 4, 1)), Direction.WEST, player, 0), "in");
        when(context, () -> player.getVehicle() instanceof PipeCarrierEntity, 5, "never went in", () ->
                when(context, () -> !player.hasVehicle(), 60, "never came out", () -> {
                    Vec3d at = relative(context, player);
                    context.assertTrue(Math.abs(at.x - 2.5) < 0.1 && Math.abs(at.z - 1.5) < 0.1, "under the mouth: " + at);
                    context.assertTrue(at.y >= 2 - 1e-3 && at.y < 2.1, "standing on the ground under it, not in it: " + at);
                    context.complete();
                }));
    }

    // ------------------------------------------------------------------ items

    private static ItemStack craft(TestContext context, ItemStack... grid) {
        CraftingRecipeInput input = CraftingRecipeInput.create(3, 3, List.of(grid));
        Optional<RecipeEntry<CraftingRecipe>> recipe = context.getWorld().getServer().getRecipeManager()
                .getFirstMatch(RecipeType.CRAFTING, input, context.getWorld());
        return recipe.map(entry -> entry.value().craft(input, context.getWorld().getRegistryManager())).orElse(ItemStack.EMPTY);
    }

    /** Six pipes from six plastic blocks (with glass for the windowed ones), six glass or stained glass blocks. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pipesAreCrafted(TestContext context) {
        ItemStack p = new ItemStack(ModBlocks.PLASTIC_BLOCKS[14]), g = new ItemStack(Items.GLASS), s = new ItemStack(Items.RED_STAINED_GLASS), e = ItemStack.EMPTY;
        ItemStack opaque = craft(context, p, e, p, p, e, p, p, e, p);
        ItemStack windowed = craft(context, p, e, p, g.copy(), e, g.copy(), p, e, p);
        ItemStack glass = craft(context, g.copy(), e, g.copy(), g.copy(), e, g.copy(), g.copy(), e, g.copy());
        ItemStack stained = craft(context, s.copy(), e, s.copy(), s.copy(), e, s.copy(), s.copy(), e, s.copy());
        context.assertTrue(opaque.isOf(RED.asItem()) && opaque.getCount() == 6, "6 red pipes: " + opaque);
        context.assertTrue(windowed.isOf(pipe(PipeKind.WINDOWED, 14).asItem()) && windowed.getCount() == 6, "6 red windowed pipes: " + windowed);
        context.assertTrue(glass.isOf(GLASS.asItem()) && glass.getCount() == 6, "6 glass pipes: " + glass);
        context.assertTrue(stained.isOf(pipe(PipeKind.STAINED_GLASS, 14).asItem()) && stained.getCount() == 6, "6 red stained glass pipes: " + stained);
        context.assertTrue(ModBlocks.PIPES[PipeKind.GLASS.ordinal()].length == 1 && ModBlocks.PIPES[PipeKind.STAINED_GLASS.ordinal()].length == 16,
                "one glass pipe, 16 stained glass pipes");
        context.complete();
    }

    /** A broken pipe drops itself. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pipeDropsItself(TestContext context) {
        BlockPos pos = new BlockPos(2, 2, 2);
        context.setBlockState(pos, pipe(GLASS, PipeSolid.NONE));
        context.getWorld().breakBlock(context.getAbsolutePos(pos), true);
        context.waitAndRun(1, () -> {
            List<ItemEntity> drops = context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(context.getAbsolutePos(pos)).expand(1), item -> true);
            context.assertTrue(drops.size() == 1 && drops.getFirst().getStack().isOf(GLASS.asItem()), "dropped itself: " + drops);
            context.complete();
        });
    }
}
