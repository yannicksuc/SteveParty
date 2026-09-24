package fr.lordfinn.steveparty.client.model;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Connected texture for a part (post or side bars) of a plastic fence, like {@link ConnectedPlasticModel}: fences
 * of the same colour make one plastic piece at most 2 blocks high (stacked posts) and 4 blocks long (a fence line),
 * so that the plastic border only runs round each piece instead of crossing the bars at every block.
 * <p>
 * The fence models texture their faces with the block grid (uvlock): each quad keeps its UVs and only takes the
 * sprite of its face's connections.
 */
public class ConnectedPlasticFenceModel implements BakedModel {
    private static final int SPAN_HORIZONTAL = 4;
    private static final int SPAN_VERTICAL = 2;
    private static final int MAX_SCAN = 12;

    private static RenderMaterial material;
    private final BakedModel base;
    /** Sprite of each mask of {@link ConnectedPlasticModel#UP} .. {@link ConnectedPlasticModel#LEFT} (0 to 15). */
    private final Sprite[] sprites;

    public ConnectedPlasticFenceModel(BakedModel base, Sprite[] sprites) {
        this.base = base;
        this.sprites = sprites;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockRenderView world, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
        if (material == null) {
            Renderer renderer = RendererAccess.INSTANCE.getRenderer();
            if (renderer == null) return;
            material = renderer.materialFinder().find();
        }
        QuadEmitter emitter = context.getEmitter();
        Map<BlockPos, int[]> phases = new HashMap<>();
        int[] masks = new int[6];
        boolean fence = world.getBlockState(pos).isOf(state.getBlock());
        for (Direction face : Direction.values()) masks[face.ordinal()] = fence ? mask(world, pos, face, phases) : 0;
        for (Direction cull : CULL_FACES) {
            if (cull != null && context.isFaceCulled(cull)) continue;
            for (BakedQuad quad : base.getQuads(state, cull, randomSupplier.get())) {
                emitter.fromVanilla(quad, material, cull);
                Sprite from = quad.getSprite();
                Sprite to = sprites[masks[quad.getFace().ordinal()]];
                if (to != null && to != from) {
                    for (int i = 0; i < 4; i++) {
                        emitter.uv(i, remap(emitter.u(i), from.getMinU(), from.getMaxU(), to.getMinU(), to.getMaxU()),
                                remap(emitter.v(i), from.getMinV(), from.getMaxV(), to.getMinV(), to.getMaxV()));
                    }
                }
                emitter.emit();
            }
        }
    }

    private static final Direction[] CULL_FACES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST, null};

    /** Which edges of this face's texture go on into the same piece (see {@link ConnectedPlasticModel#UP}). */
    private static int mask(BlockRenderView world, BlockPos pos, Direction face, Map<BlockPos, int[]> phases) {
        Direction up = ConnectedPlasticModel.textureUp(face);
        Direction left = ConnectedPlasticModel.textureLeft(face);
        int mask = 0;
        if (connected(world, pos, up, phases)) mask |= ConnectedPlasticModel.UP;
        if (connected(world, pos, left.getOpposite(), phases)) mask |= ConnectedPlasticModel.RIGHT;
        if (connected(world, pos, up.getOpposite(), phases)) mask |= ConnectedPlasticModel.DOWN;
        if (connected(world, pos, left, phases)) mask |= ConnectedPlasticModel.LEFT;
        return mask;
    }

    /** @return whether the fence at {@code pos} and its neighbour on {@code dir} are one piece. */
    private static boolean connected(BlockRenderView world, BlockPos pos, Direction dir, Map<BlockPos, int[]> phases) {
        BlockState here = world.getBlockState(pos);
        BlockState there = world.getBlockState(pos.offset(dir));
        if (!joined(here, there, dir)) return false;
        int axis = dir.getAxis().ordinal();
        int[] phase = phases.computeIfAbsent(pos, p -> phases(world, p));
        // A piece boundary between the two
        if (phase[axis] == (dir.getDirection() == Direction.AxisDirection.POSITIVE ? span(axis) - 1 : 0)) return false;
        // Aligned on the other axes, so that a piece stays within its 4x2x4 box
        int[] otherPhase = phases.computeIfAbsent(pos.offset(dir), p -> phases(world, p));
        for (int a = 0; a < 3; a++) {
            if (a != axis && otherPhase[a] != phase[a]) return false;
        }
        return true;
    }

    /** Same fence, stacked, or linked by its bars. */
    private static boolean joined(BlockState here, BlockState there, Direction dir) {
        if (!there.isOf(here.getBlock())) return false;
        if (dir.getAxis().isVertical()) return true;
        BooleanProperty side = side(dir);
        return here.contains(side) && here.get(side);
    }

    private static BooleanProperty side(Direction dir) {
        return switch (dir) {
            case NORTH -> FenceBlock.NORTH;
            case SOUTH -> FenceBlock.SOUTH;
            case WEST -> FenceBlock.WEST;
            default -> FenceBlock.EAST;
        };
    }

    /** Position inside the piece on each axis (X, Y, Z), counted from the end of the fence. */
    private static int[] phases(BlockRenderView world, BlockPos pos) {
        int[] phase = new int[3];
        for (Direction.Axis axis : Direction.Axis.values()) {
            Direction back = Direction.from(axis, Direction.AxisDirection.NEGATIVE);
            int run = 0;
            BlockPos.Mutable cursor = pos.mutableCopy();
            BlockState current = world.getBlockState(cursor);
            while (run < MAX_SCAN) {
                BlockState previous = world.getBlockState(cursor.move(back));
                if (!joined(current, previous, back)) break;
                current = previous;
                run++;
            }
            int span = span(axis.ordinal());
            phase[axis.ordinal()] = run < MAX_SCAN ? run % span : Math.floorMod(axis.choose(pos.getX(), pos.getY(), pos.getZ()), span);
        }
        return phase;
    }

    private static int span(int axisOrdinal) {
        return axisOrdinal == Direction.Axis.Y.ordinal() ? SPAN_VERTICAL : SPAN_HORIZONTAL;
    }

    private static float remap(float value, float fromMin, float fromMax, float toMin, float toMax) {
        return toMin + (value - fromMin) / (fromMax - fromMin) * (toMax - toMin);
    }

    // Everything else (items, particles, fallback) uses the standalone texture

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, Random random) {
        return base.getQuads(state, face, random);
    }

    @Override public boolean useAmbientOcclusion() { return base.useAmbientOcclusion(); }
    @Override public boolean hasDepth() { return base.hasDepth(); }
    @Override public boolean isSideLit() { return base.isSideLit(); }
    @Override public boolean isBuiltin() { return false; }
    @Override public Sprite getParticleSprite() { return base.getParticleSprite(); }
    @Override public ModelTransformation getTransformation() { return base.getTransformation(); }
    @Override public ModelOverrideList getOverrides() { return base.getOverrides(); }
}
