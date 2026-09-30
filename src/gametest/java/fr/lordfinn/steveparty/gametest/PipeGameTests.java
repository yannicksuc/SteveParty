package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlock;
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
    private static final Block GLASS = ModBlocks.PIPES[PipeKind.GLASS.ordinal()][3];

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

    /** A capped end (a pipe going into the ground) warps to the nearest mouth of another pipe network. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void cappedEndWarpsToTheNearestOtherMouth(TestContext context) {
        // The warp pipe: one block on the ground, a mouth on top, capped into the ground
        context.setBlockState(new BlockPos(1, 1, 1), Blocks.STONE);
        context.setBlockState(new BlockPos(1, 2, 1), pipe(RED, PipeSolid.DOWN));
        // The nearest other mouth, and a farther one
        context.setBlockState(new BlockPos(4, 1, 1), Blocks.STONE);
        context.setBlockState(new BlockPos(4, 2, 1), pipe(GLASS, PipeSolid.DOWN));
        context.setBlockState(new BlockPos(6, 1, 6), Blocks.STONE);
        context.setBlockState(new BlockPos(6, 2, 6), pipe(RED, PipeSolid.DOWN));
        PigEntity pig = context.spawnEntity(EntityType.PIG, new BlockPos(1, 3, 1));
        pig.setAiDisabled(true);
        context.assertTrue(PipeTravel.enter(context.getWorld(), context.getAbsolutePos(new BlockPos(1, 2, 1)), Direction.UP, pig, 0), "in");
        when(context, () -> pig.getVehicle() instanceof PipeCarrierEntity, 5, "the pig never went in", () ->
                when(context, () -> !pig.hasVehicle() && relative(context, pig).x > 3, 60, "the pig never came out of the nearest mouth", () -> {
                    Vec3d at = relative(context, pig);
                    context.assertTrue(Math.abs(at.x - 4.5) < 0.6 && Math.abs(at.z - 1.5) < 0.6 && at.y >= 3, "on top of the nearest mouth: " + at);
                    context.assertTrue(pig.getHealth() == pig.getMaxHealth(), "unhurt");
                    context.complete();
                }));
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
        context.setBlockState(new BlockPos(1, 2, 1), pipe(RED, PipeSolid.DOWN));
        context.setBlockState(new BlockPos(3, 1, 4), Blocks.STONE);
        context.setBlockState(new BlockPos(3, 2, 4), pipe(RED, PipeSolid.DOWN));
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

    /** Nowhere to warp to: back out of the mouth it went in. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void warpWithNowhereToGoComesBack(TestContext context) {
        // Every mouth near: blocked by a block in front of it except the one gone into
        context.setBlockState(new BlockPos(1, 1, 1), Blocks.STONE);
        context.setBlockState(new BlockPos(1, 2, 1), pipe(RED, PipeSolid.DOWN));
        context.setBlockState(new BlockPos(3, 1, 1), Blocks.STONE);
        context.setBlockState(new BlockPos(3, 2, 1), pipe(RED, PipeSolid.DOWN));
        context.setBlockState(new BlockPos(3, 3, 1), Blocks.STONE);
        ItemEntity item = new ItemEntity(context.getWorld(), 0, 0, 0, new ItemStack(Items.APPLE));
        Vec3d at = context.getAbsolute(new Vec3d(1.5, 2.9, 1.5));
        item.setPosition(at);
        context.getWorld().spawnEntity(item);
        context.assertTrue(PipeTravel.enter(context.getWorld(), context.getAbsolutePos(new BlockPos(1, 2, 1)), Direction.UP, item, 0), "in");
        when(context, () -> item.age > 3 && !item.hasVehicle(), 60, "never came out", () -> {
            Vec3d out = relative(context, item);
            context.assertTrue(Math.abs(out.x - 1.5) < 0.5 && Math.abs(out.z - 1.5) < 0.5 && out.y >= 2.9, "back out of the mouth it went in: " + out);
            context.complete();
        });
    }

    // ------------------------------------------------------------------ items

    private static ItemStack craft(TestContext context, ItemStack... grid) {
        CraftingRecipeInput input = CraftingRecipeInput.create(3, 3, List.of(grid));
        Optional<RecipeEntry<CraftingRecipe>> recipe = context.getWorld().getServer().getRecipeManager()
                .getFirstMatch(RecipeType.CRAFTING, input, context.getWorld());
        return recipe.map(entry -> entry.value().craft(input, context.getWorld().getRegistryManager())).orElse(ItemStack.EMPTY);
    }

    /** Six pipes of a plastic colour from six plastic blocks (with glass for the glass and windowed ones). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pipesAreCraftedFromPlastic(TestContext context) {
        ItemStack p = new ItemStack(ModBlocks.PLASTIC_BLOCKS[14]), g = new ItemStack(Items.GLASS), e = ItemStack.EMPTY;
        ItemStack opaque = craft(context, p, e, p, p, e, p, p, e, p);
        ItemStack windowed = craft(context, p, e, p, g.copy(), e, g.copy(), p, e, p);
        ItemStack glass = craft(context, g.copy(), e, g.copy(), p, e, p, g.copy(), e, g.copy());
        context.assertTrue(opaque.isOf(RED.asItem()) && opaque.getCount() == 6, "6 red pipes: " + opaque);
        context.assertTrue(windowed.isOf(ModBlocks.PIPES[PipeKind.WINDOWED.ordinal()][14].asItem()) && windowed.getCount() == 6, "6 red windowed pipes: " + windowed);
        context.assertTrue(glass.isOf(ModBlocks.PIPES[PipeKind.GLASS.ordinal()][14].asItem()) && glass.getCount() == 6, "6 red glass pipes: " + glass);
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
