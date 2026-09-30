package fr.lordfinn.steveparty.client.model;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PlasticBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.EmptyBlockView;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When two faces of plastic (blocks, double slabs, slabs, stairs and walls of one colour) join into one connected
 * texture, for {@link ConnectedPlasticModel} and {@link ConnectedPlasticShapeModel}.
 * <p>
 * Two blocks side by side join a face across the edge between them when both have a face on the same plane there
 * (coplanar, facing the same way) and those faces meet along exactly the same stretch of that edge: slab next to slab
 * of the same half, stairs in a row, the back of stairs against a block, the top of a top slab next to the top of a
 * block, a wall post on a wall post, wall sides in a line. Where the two faces don't match (the side of a half slab
 * against a full block, a stairs top next to a block top), both keep their border: a face can only drop a whole
 * border, so a one-sided seam would show. Faces are read from the blocks' outline shapes, which are the shapes their
 * models draw; the shapes of each state are cached, so nothing is allocated while drawing.
 */
final class PlasticConnections {
    /** Colour index + 1 of every plastic block, slab, stairs and wall (0: not plastic of that kind). */
    private static final Map<Block, Integer> COLOUR = new IdentityHashMap<>();
    private static final Map<BlockState, float[]> BOXES = new ConcurrentHashMap<>();
    /** Offset of the samples off the face plane and off the edge, in blocks (well under a model pixel). */
    private static final float EPS = 1 / 64f;
    static final int FULL_SPAN = 0xFFFF;

    static {
        for (int i = 0; i < ModBlocks.COLORS.length; i++) {
            COLOUR.put(ModBlocks.PLASTIC_BLOCKS[i], i + 1);
            COLOUR.put(ModBlocks.PLASTIC_SLABS[i], i + 1);
            COLOUR.put(ModBlocks.PLASTIC_STAIRS[i], i + 1);
            COLOUR.put(ModBlocks.PLASTIC_WALLS[i], i + 1);
        }
    }

    /** Blocks of one colour count together when working out where the 4x2x4 pieces start. */
    static final PieceLayout.Lookup LOOKUP = BlockRenderView::getBlockState;
    static final PieceLayout.Joiner JOINER = (here, there, dir) -> {
        int colour = colour(here);
        return colour != 0 && colour(there) == colour;
    };

    private PlasticConnections() {
    }

    /** @return 1 + the colour index of a plastic block, slab, stairs or wall, 0 for anything else. */
    static int colour(BlockState state) {
        Integer colour = COLOUR.get(state.getBlock());
        return colour == null ? 0 : colour;
    }

    /** @return whether the state fills its whole block: a plastic block or a double plastic slab. */
    static boolean isFull(BlockState state) {
        Block block = state.getBlock();
        return block instanceof PlasticBlock
                || block instanceof SlabBlock && COLOUR.containsKey(block) && state.get(SlabBlock.TYPE) == SlabType.DOUBLE;
    }

    /**
     * @param face  the side the face looks to
     * @param plane where the face lies along its axis, in the block (0 to 1)
     * @param edge  the side of the edge crossed, towards the neighbour (perpendicular to {@code face})
     * @param span  bits (see {@link #profile}) the face of {@code pos} covers along that edge; all must be joined
     * @return whether the face of {@code pos} goes on into the neighbour across {@code edge}, as one piece
     */
    static boolean joined(BlockRenderView world, PieceLayout layout, BlockPos pos, Direction face, float plane,
                          Direction edge, int span) {
        BlockState here = world.getBlockState(pos);
        int colour = colour(here);
        if (colour == 0) return false;
        BlockPos otherPos = pos.offset(edge);
        BlockState there = world.getBlockState(otherPos);
        if (colour(there) != colour) return false;
        if (!(isFull(here) && isFull(there) && (plane == 0 || plane == 1))) {
            int mine = profile(here, face, plane, edge, false);
            if (mine == 0 || (mine & span) != span) return false;
            if (profile(there, face, plane, edge, true) != mine) return false;
        }
        return layout.samePiece(pos, edge);
    }

    /**
     * Where a block has a face looking to {@code face} on {@code plane}, along the edge of the block on side
     * {@code edge} (or, with {@code far}, the edge on the opposite side, where it meets a block beyond {@code edge}):
     * bit k is set when there is face at 16ths (k + 0.5) along the third axis.
     */
    private static int profile(BlockState state, Direction face, float plane, Direction edge, boolean far) {
        float[] boxes = BOXES.computeIfAbsent(state, PlasticConnections::boxes);
        int faceAxis = face.getAxis().ordinal();
        int edgeAxis = edge.getAxis().ordinal();
        int along = 3 - faceAxis - edgeAxis;
        boolean positive = edge.getDirection() == Direction.AxisDirection.POSITIVE;
        float edgeCoord = positive != far ? 1 - EPS : EPS;
        float out = face.getDirection() == Direction.AxisDirection.POSITIVE ? EPS : -EPS;
        int bits = 0;
        for (int k = 0; k < 16; k++) {
            float t = (k + 0.5f) / 16;
            if (inside(boxes, faceAxis, plane - out, edgeAxis, edgeCoord, along, t)
                    && !inside(boxes, faceAxis, plane + out, edgeAxis, edgeCoord, along, t)) bits |= 1 << k;
        }
        return bits;
    }

    private static boolean inside(float[] boxes, int axisA, float a, int axisB, float b, int axisC, float c) {
        for (int i = 0; i < boxes.length; i += 6) {
            if (boxes[i + axisA] <= a && a < boxes[i + 3 + axisA]
                    && boxes[i + axisB] <= b && b < boxes[i + 3 + axisB]
                    && boxes[i + axisC] <= c && c < boxes[i + 3 + axisC]) return true;
        }
        return false;
    }

    /** The boxes of a state's outline, as min x, y, z, max x, y, z each. */
    private static float[] boxes(BlockState state) {
        List<Box> list = state.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).getBoundingBoxes();
        float[] boxes = new float[list.size() * 6];
        for (int i = 0; i < list.size(); i++) {
            Box box = list.get(i);
            boxes[i * 6] = (float) box.minX;
            boxes[i * 6 + 1] = (float) box.minY;
            boxes[i * 6 + 2] = (float) box.minZ;
            boxes[i * 6 + 3] = (float) box.maxX;
            boxes[i * 6 + 4] = (float) box.maxY;
            boxes[i * 6 + 5] = (float) box.maxZ;
        }
        return boxes;
    }
}
