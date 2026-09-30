package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lordfinn.steveparty.blocks.custom.signs.AbstractStencilSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.SignShapes;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.WoodType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Wooden easel sign of a fixed wood (the 10 original signs, kept for the worlds that have them). New signs are
 * {@link fr.lordfinn.steveparty.blocks.custom.signs.MaterialEaselSignBlock}, made of any planks.
 * Stencils, dyes, glow ink and sponges: see {@link fr.lordfinn.steveparty.blocks.custom.signs.StencilInteractions}.
 */
public class EaselSignBlock extends AbstractStencilSignBlock {
    public static final MapCodec<EaselSignBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance
            .group(WoodType.CODEC.fieldOf("wood_type").forGetter(block -> block.type), createSettingsCodec())
            .apply(instance, EaselSignBlock::new));
    /**
     * Outline of the A-frame of easel_sign.json, turned like the model: the board (32 pixels wide, tilted by 22.5°
     * around x at (8, 0, 3)) and the two legs behind it (tilted by -45° around x at (1, 12)).
     */
    private static final VoxelShape[] OUTLINES = SignShapes.rotations(tiltedX(new SignShapes.Box(-8, -0.25, 1, 24, 15.75, 5), 22.5, 0, 3,
            new SignShapes.Box(16, -1, 12, 19, 11, 15), new SignShapes.Box(-3, -1, 12, 0, 11, 15)));
    /** Bumped into within its own block only: the ends of the board sticking out never trap a player next to it. */
    private static final VoxelShape[] COLLISIONS = new VoxelShape[OUTLINES.length];

    static {
        for (int i = 0; i < OUTLINES.length; i++) {
            COLLISIONS[i] = VoxelShapes.combineAndSimplify(OUTLINES[i], VoxelShapes.fullCube(), BooleanBiFunction.AND);
        }
    }

    protected final WoodType type;

    public EaselSignBlock(WoodType woodType, Settings settings) {
        super(settings);
        this.type = woodType;
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new EaselSignBlockEntity(pos, state);
    }

    public WoodType getWoodType() {
        return this.type;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return OUTLINES[state.get(ROTATION)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return COLLISIONS[state.get(ROTATION)];
    }

    /**
     * @param board the board, tilted by {@code boardDegrees} around x at ({@code originY}, {@code originZ})
     * @param legs  the legs, tilted by -45° around x at (1, 12)
     * @return model boxes (pixels, before the sign turns) around the tilted pieces, cut in 4 pixel high slices so
     * that the slanted parts get a snug outline; nothing below the ground
     */
    private static SignShapes.Box[] tiltedX(SignShapes.Box board, double boardDegrees, double originY, double originZ, SignShapes.Box... legs) {
        List<SignShapes.Box> boxes = new ArrayList<>();
        slices(boxes, board, boardDegrees, originY, originZ);
        for (SignShapes.Box leg : legs) slices(boxes, leg, -45, 1, 12);
        return boxes.toArray(SignShapes.Box[]::new);
    }

    private static void slices(List<SignShapes.Box> out, SignShapes.Box box, double degrees, double originY, double originZ) {
        double angle = Math.toRadians(degrees), cos = Math.cos(angle), sin = Math.sin(angle);
        int count = Math.max(1, (int) Math.ceil((box.y2() - box.y1()) / 4));
        for (int i = 0; i < count; i++) {
            double y1 = box.y1() + (box.y2() - box.y1()) * i / count, y2 = box.y1() + (box.y2() - box.y1()) * (i + 1) / count;
            double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
            for (double y : new double[]{y1, y2}) {
                for (double z : new double[]{box.z1(), box.z2()}) {
                    // Same turn as the model element (Quaternionf.rotationX)
                    double ry = y - originY, rz = z - originZ;
                    double ty = ry * cos - rz * sin + originY, tz = ry * sin + rz * cos + originZ;
                    minY = Math.min(minY, ty);
                    maxY = Math.max(maxY, ty);
                    minZ = Math.min(minZ, tz);
                    maxZ = Math.max(maxZ, tz);
                }
            }
            if (maxY <= 0) continue;
            out.add(new SignShapes.Box(box.x1(), Math.max(0, minY), minZ, box.x2(), maxY, maxZ));
        }
    }

    // Drops are handled by the loot tables (data/steveparty/loot_table/blocks/*easel_sign.json)
}
