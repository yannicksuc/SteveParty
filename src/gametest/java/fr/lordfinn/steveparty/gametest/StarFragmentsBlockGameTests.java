package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.StarFragmentsBlock;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.DyeColor;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The star fragments blocks are glowing glass: full-cube collision, see-through, never suffocating. */
public class StarFragmentsBlockGameTests implements FabricGameTest {

    /** The 16 colours, in dye order. */
    private static final List<Block> BLOCKS = ModBlocks.STAR_FRAGMENTS_BLOCKS;

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void starFragmentsBlocksBehaveLikeGlowingGlass(TestContext context) {
        World world = context.getWorld();
        Set<Integer> colours = new HashSet<>();
        for (int i = 0; i < BLOCKS.size(); i++) {
            BlockPos rel = new BlockPos(i % 4, 1, i / 4);
            context.setBlockState(rel, BLOCKS.get(i));
            BlockPos pos = context.getAbsolutePos(rel);
            BlockState state = world.getBlockState(pos);
            String name = BLOCKS.get(i).getTranslationKey();
            context.assertFalse(VoxelShapes.matchesAnywhere(state.getCollisionShape(world, pos), VoxelShapes.fullCube(),
                    BooleanBiFunction.NOT_SAME), name + " has a full-cube collision");
            context.assertTrue(state.isFullCube(world, pos), name + " is a full cube");
            context.assertFalse(state.shouldSuffocate(world, pos), name + " does not suffocate");
            context.assertFalse(state.shouldBlockVision(world, pos), name + " does not block vision");
            context.assertFalse(state.isSolidBlock(world, pos), name + " is not a solid (opaque) block");
            context.assertTrue(state.getOpacity() == 0, name + " lets light through like glass");
            context.assertTrue(state.getLuminance() == 15, name + " keeps its light level 15");
            context.assertTrue(state.isSideInvisible(state, Direction.EAST), name + " hides faces against itself");
            BlockState other = BLOCKS.get((i + 1) % BLOCKS.size()).getDefaultState();
            context.assertFalse(state.isSideInvisible(other, Direction.EAST), name + " shows faces against another colour");
            DyeColor dye = DyeColor.byId(i);
            context.assertTrue(state.getMapColor(world, pos) == dye.getMapColor(), name + " shows in its colour on maps");
            context.assertTrue(((StarFragmentsBlock) BLOCKS.get(i)).getColour() < StarFragmentsBlock.COLOUR_COUNT
                    && colours.add(((StarFragmentsBlock) BLOCKS.get(i)).getColour()), name + " has its own slash colour");
        }
        context.complete();
    }
}
