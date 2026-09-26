package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSupport;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.service.TokenMovementService;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.StairShape;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock.ROTATION_8;
import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock.SUPPORT;

/** Tiles lie on the real surface of the block under them: lowered on slabs, snow and carpets, sloped on stairs. */
public class TileSupportGameTests implements FabricGameTest {
    private static final BlockPos TILE = new BlockPos(2, 2, 2);

    private static TileSupport supportOn(TestContext context, BlockState support) {
        context.setBlockState(TILE.down(2), Blocks.STONE); // snow and carpets need a floor
        context.setBlockState(TILE.down(), support);
        context.setBlockState(TILE, ModBlocks.TILE);
        return context.getBlockState(TILE).get(SUPPORT);
    }

    private static BlockState stairs(Direction facing, BlockHalf half, StairShape shape) {
        return Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, facing).with(StairsBlock.HALF, half)
                .with(StairsBlock.SHAPE, shape);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void levelSupportsLowerTheTile(TestContext context) {
        context.assertEquals(supportOn(context, Blocks.STONE.getDefaultState()), TileSupport.FLAT, "on a full block");
        context.assertEquals(supportOn(context, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.BOTTOM)),
                TileSupport.DROP_8, "on a bottom slab");
        context.assertEquals(supportOn(context, Blocks.OAK_SLAB.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP)),
                TileSupport.FLAT, "on a top slab");
        context.assertEquals(supportOn(context, Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, 3)),
                TileSupport.DROP_10, "on 3 snow layers");
        context.assertEquals(supportOn(context, Blocks.RED_CARPET.getDefaultState()), TileSupport.DROP_15, "on a carpet");
        context.assertEquals(supportOn(context, Blocks.AIR.getDefaultState()), TileSupport.FLAT, "in the air");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stairsSlopeTheTile(TestContext context) {
        context.assertEquals(supportOn(context, stairs(Direction.NORTH, BlockHalf.BOTTOM, StairShape.STRAIGHT)),
                TileSupport.SLOPE_NORTH, "stairs facing north");
        context.assertEquals(supportOn(context, stairs(Direction.EAST, BlockHalf.BOTTOM, StairShape.STRAIGHT)),
                TileSupport.SLOPE_EAST, "stairs facing east");
        context.assertEquals(supportOn(context, stairs(Direction.SOUTH, BlockHalf.BOTTOM, StairShape.STRAIGHT)),
                TileSupport.SLOPE_SOUTH, "stairs facing south");
        context.assertEquals(supportOn(context, stairs(Direction.WEST, BlockHalf.BOTTOM, StairShape.STRAIGHT)),
                TileSupport.SLOPE_WEST, "stairs facing west");
        context.assertEquals(supportOn(context, stairs(Direction.NORTH, BlockHalf.TOP, StairShape.STRAIGHT)),
                TileSupport.FLAT, "upside-down stairs: a flat top");
        // Outer left facing north: only its north-west quarter is high
        context.assertEquals(supportOn(context, stairs(Direction.NORTH, BlockHalf.BOTTOM, StairShape.OUTER_LEFT)),
                TileSupport.OUTER_NORTH_WEST, "outer corner: along the diagonal toward its high quarter");
        context.assertEquals(supportOn(context, stairs(Direction.SOUTH, BlockHalf.BOTTOM, StairShape.OUTER_RIGHT)),
                TileSupport.OUTER_SOUTH_WEST, "outer right facing south");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void slopeTouchesTheStepsAndRisesTowardTheStairs(TestContext context) {
        TileSupport slope = TileSupport.SLOPE_NORTH;
        // Rises toward the north (the high step), 45 degrees, through the step nose, down to the lower step
        context.assertTrue(Math.abs(slope.surfaceY(0.5, 0.5)) < 1.0E-6, "through the nose of the step");
        context.assertTrue(Math.abs(slope.surfaceY(0.5, 1) + 0.5) < 1.0E-6, "on the lower step at the south edge");
        context.assertTrue(Math.abs(slope.surfaceY(0.5, 0) - 0.5) < 1.0E-6, "half a block up at the north edge");
        context.assertTrue(slope.supportTop(0) == 0 && slope.supportTop(1) == 0 && slope.supportTop(2) == -0.5
                && slope.supportTop(3) == -0.5, "high step north, low step south");
        // The outline follows the slope: aimed at from above, it is high at the north edge, low at the south edge
        context.assertTrue(slope.outline().getMax(Direction.Axis.Y) > 0.6 && slope.outline().getMin(Direction.Axis.Y) < -0.3,
                "outline along the slope: " + slope.outline());
        // Walked like bare stairs: its lowest step is the lower stair step (half a block above the stairs below),
        // and it rises by steps of a quarter of a block, each longer than a walking stride per tick
        java.util.List<net.minecraft.util.math.Box> boxes = slope.shape().getBoundingBoxes();
        double lowest = boxes.stream().mapToDouble(box -> box.maxY).min().orElse(9);
        context.assertTrue(Math.abs(lowest + 0.5) < 1.0E-6, "lowest step on the lower stair step: " + lowest);
        for (double z = 0; z < 1; z += 1.0 / 16) {
            final double at = z + 1.0 / 32, next = z + 1.0 / 32 + 1.0 / 16;
            double here = boxes.stream().filter(box -> box.minZ <= at && at <= box.maxZ).mapToDouble(box -> box.maxY).max().orElse(-9);
            double then = boxes.stream().filter(box -> box.minZ <= next && next <= box.maxZ).mapToDouble(box -> box.maxY).max().orElse(here);
            context.assertTrue(Math.abs(here - then) <= 0.25 + 1.0E-6, "smooth steps at z " + z + ": " + here + " -> " + then);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void innerCornersSlopeAlongTheDiagonal(TestContext context) {
        // Inner left facing north: high north half and south-west quarter, low south-east quarter
        context.assertEquals(supportOn(context, stairs(Direction.NORTH, BlockHalf.BOTTOM, StairShape.INNER_LEFT)),
                TileSupport.SLOPE_NORTH_WEST, "inner left, facing north");
        context.assertEquals(supportOn(context, stairs(Direction.NORTH, BlockHalf.BOTTOM, StairShape.INNER_RIGHT)),
                TileSupport.SLOPE_NORTH_EAST, "inner right, facing north");
        context.assertEquals(supportOn(context, stairs(Direction.SOUTH, BlockHalf.BOTTOM, StairShape.INNER_LEFT)),
                TileSupport.SLOPE_SOUTH_EAST, "inner left, facing south");
        context.assertEquals(supportOn(context, stairs(Direction.SOUTH, BlockHalf.BOTTOM, StairShape.INNER_RIGHT)),
                TileSupport.SLOPE_SOUTH_WEST, "inner right, facing south");

        TileSupport corner = TileSupport.SLOPE_NORTH_WEST;
        // 45 degrees along the diagonal, resting on the inner edges of the L (where the high part meets the low step)
        context.assertTrue(Math.abs(corner.angle() - Math.PI / 4) < 1.0E-6, "45 degrees");
        context.assertTrue(Math.abs(corner.surfaceY(1, 0.5)) < 1.0E-6 && Math.abs(corner.surfaceY(0.5, 1)) < 1.0E-6, "on the L's edges");
        // ... and never under the stairs: above the high part everywhere
        for (double x = 0; x <= 1; x += 0.125) for (double z = 0; z <= 1; z += 0.125) {
            boolean lowQuarter = x > 0.5 && z > 0.5;
            context.assertTrue(corner.surfaceY(x, z) >= (lowQuarter ? -0.5 : 0) - 1.0E-6, "above the stairs at " + x + ", " + z);
        }
        context.assertTrue(corner.supportTop(3) == -0.5 && corner.supportTop(0) == 0 && corner.supportTop(1) == 0
                && corner.supportTop(2) == 0, "three high quarters, the south-east one low");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aTiltedTileKeepsItsSquareFace(TestContext context) {
        // Turned, not stretched: a level square keeps its sides' lengths once on the slope
        for (TileSupport support : List.of(TileSupport.SLOPE_NORTH, TileSupport.SLOPE_EAST, TileSupport.SLOPE_NORTH_WEST, TileSupport.OUTER_SOUTH_EAST)) {
            org.joml.Matrix4f matrix = support.transform();
            org.joml.Vector3f a = matrix.transformPosition(new org.joml.Vector3f(0, 0, 0));
            org.joml.Vector3f b = matrix.transformPosition(new org.joml.Vector3f(1, 0, 0));
            org.joml.Vector3f c = matrix.transformPosition(new org.joml.Vector3f(0, 0, 1));
            context.assertTrue(Math.abs(a.distance(b) - 1) < 1.0E-5 && Math.abs(a.distance(c) - 1) < 1.0E-5,
                    support + ": sides " + a.distance(b) + " / " + a.distance(c));
            // ... and lies on the slope: its middle on the support's surface
            org.joml.Vector3f middle = matrix.transformPosition(new org.joml.Vector3f(0.5f, 0, 0.5f));
            context.assertTrue(Math.abs(middle.y - support.surfaceY(0.5, 0.5)) < 1.0E-5, support + ": middle on the surface");
            // ... rising 45 degrees toward its high side
            org.joml.Vector3f up = matrix.transformPosition(new org.joml.Vector3f(
                    (float) (0.5 + 0.1 * Math.signum(support.gradientX())), 0, (float) (0.5 + 0.1 * Math.signum(support.gradientZ()))));
            context.assertTrue(up.y > middle.y, support + ": rises toward its high side");
        }
        context.complete();
    }

    /** Aimed at from the side, low: the vanilla ray reaches the slab's cell first, the tile must still be the target. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aLoweredTileIsAimedAtFromEveryAngle(TestContext context) {
        supportOn(context, Blocks.OAK_SLAB.getDefaultState());
        context.setBlockState(TILE.down().west(), Blocks.OAK_SLAB.getDefaultState()); // a bare slab next to it
        BlockPos abs = context.getAbsolutePos(TILE);
        var world = context.getWorld();
        // From the west, just above the slabs (y + 0.55 in the slab's cell), toward the tile's layer
        Vec3d start = new Vec3d(abs.getX() - 1.5, abs.getY() - 0.45, abs.getZ() + 0.5);
        Vec3d end = new Vec3d(abs.getX() + 0.5, abs.getY() - 0.42, abs.getZ() + 0.5);
        var vanilla = world.raycast(new net.minecraft.world.RaycastContext(start, end, net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, net.minecraft.block.ShapeContext.absent()));
        var hit = BoardSpaces.preferTile(world, start, end, vanilla);
        context.assertTrue(hit instanceof net.minecraft.util.hit.BlockHitResult block && block.getBlockPos().equals(abs),
                "the lowered tile is aimed at from the side: " + hit.getPos() + " vanilla " + vanilla.getType());
        // Straight down on the bare slab: still the slab
        Vec3d above = new Vec3d(abs.getX() - 0.5, abs.getY() + 1, abs.getZ() + 0.5);
        Vec3d below = new Vec3d(abs.getX() - 0.5, abs.getY() - 1.5, abs.getZ() + 0.5);
        var onSlab = world.raycast(new net.minecraft.world.RaycastContext(above, below, net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, net.minecraft.block.ShapeContext.absent()));
        context.assertTrue(BoardSpaces.preferTile(world, above, below, onSlab) == onSlab, "the bare slab stays the target");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void supportFollowsTheBlockUnder(TestContext context) {
        supportOn(context, Blocks.STONE.getDefaultState());
        context.setBlockState(TILE.down(), Blocks.STONE_SLAB.getDefaultState());
        context.expectBlockProperty(TILE, SUPPORT, TileSupport.DROP_8);
        context.setBlockState(TILE.down(), stairs(Direction.WEST, BlockHalf.BOTTOM, StairShape.STRAIGHT));
        context.expectBlockProperty(TILE, SUPPORT, TileSupport.SLOPE_WEST);
        context.removeBlock(TILE.down());
        context.expectBlockProperty(TILE, SUPPORT, TileSupport.FLAT);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placedOnStairsFacesWhereThePlayerLooks(TestContext context) {
        context.setBlockState(TILE.down(), stairs(Direction.NORTH, BlockHalf.BOTTOM, StairShape.STRAIGHT));
        PlayerEntity player = context.createMockPlayer(GameMode.CREATIVE);
        player.setYaw(-90); // looking east: the facing is the player's, the slope the stairs'
        ItemStack item = new ItemStack(ModBlocks.ADVANCED_TILE);
        player.setStackInHand(Hand.MAIN_HAND, item);
        context.useStackOnBlock(player, item, TILE.down(), Direction.UP);
        context.expectBlockProperty(TILE, SUPPORT, TileSupport.SLOPE_NORTH);
        context.expectBlockProperty(TILE, ROTATION_8, ATileBlock.rotation8FromYaw(-90));
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tokensStandOnTheSurface(TestContext context) {
        supportOn(context, Blocks.OAK_SLAB.getDefaultState());
        BlockPos abs = context.getAbsolutePos(TILE);
        Vec3d stand = BoardSpaces.standPos(context.getWorld(), abs);
        context.assertTrue(Math.abs(stand.y - (abs.getY() - 0.5 + 2 / 16.0)) < 1.0E-6, "on the lowered tile: " + stand);
        // A token standing there has its feet in the slab's cell: it is still on the tile
        context.assertEquals(BoardSpaces.boardSpacePosAt(context.getWorld(), BlockPos.ofFloored(stand)), abs, "found from the cell below");
        context.complete();
    }

    /** A token walks from a level tile onto a lowered one, then onto a sloped one: it stands on each surface. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
    public void tokenMovesBetweenLoweredAndSlopedTiles(TestContext context) {
        BlockPos a = new BlockPos(1, 2, 1), b = new BlockPos(4, 2, 1), c = new BlockPos(7, 2, 1);
        context.setBlockState(a.down(), Blocks.STONE);
        context.setBlockState(b.down(), Blocks.OAK_SLAB.getDefaultState());
        context.setBlockState(c.down(), stairs(Direction.EAST, BlockHalf.BOTTOM, StairShape.STRAIGHT));
        for (BlockPos pos : List.of(a, b, c)) context.setBlockState(pos, ModBlocks.TILE);
        link(context, a, b);
        link(context, b, c);

        CowEntity cow = context.spawnEntity(EntityType.COW, a);
        TokenizedEntityInterface token = (TokenizedEntityInterface) cow;
        token.steveparty$setTokenized(true);
        context.waitAndRun(2, () -> {
            TokenMovementService.moveEntityOnBoard(cow, 2);
            context.waitAndRun(60, () -> {
                Vec3d expected = BoardSpaces.standPos(context.getWorld(), context.getAbsolutePos(c));
                context.assertTrue(cow.getPos().distanceTo(expected) < 0.05, "on the sloped tile: " + cow.getPos() + " expected " + expected);
                context.assertEquals(token.steveparty$getNbSteps(), 0, "both steps walked (the lowered tile counted)");
                token.steveparty$setTokenized(false);
                context.complete();
            });
        });
    }

    private static void link(TestContext context, BlockPos from, BlockPos to) {
        BoardSpaceBlockEntity tile = context.getBlockEntity(from);
        ItemStack cartridge = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR);
        cartridge.set(ModComponents.DESTINATIONS_COMPONENT,
                new DestinationsComponent(new ArrayList<>(List.of(context.getAbsolutePos(to))), ""));
        tile.setStack(0, cartridge);
    }
}
