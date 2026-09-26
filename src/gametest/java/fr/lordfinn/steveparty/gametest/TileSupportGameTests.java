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
        context.setBlockState(TILE, ModBlocks.SIMPLE_TILE);
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
        context.assertEquals(supportOn(context, stairs(Direction.NORTH, BlockHalf.BOTTOM, StairShape.OUTER_LEFT)),
                TileSupport.FLAT, "outer corner: level on its high quarter");
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
        context.assertTrue(slope.shape().getMax(Direction.Axis.Y) > 0.6 && slope.shape().getMin(Direction.Axis.Y) < -0.3,
                "outline along the slope: " + slope.shape());
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
        ItemStack item = new ItemStack(ModBlocks.TILE);
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
        for (BlockPos pos : List.of(a, b, c)) context.setBlockState(pos, ModBlocks.SIMPLE_TILE);
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
