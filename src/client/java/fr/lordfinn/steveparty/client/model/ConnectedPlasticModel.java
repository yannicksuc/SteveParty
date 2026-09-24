package fr.lordfinn.steveparty.client.model;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Connected texture for the plastic switcher blocks: touching blocks of the same colour merge into one plastic
 * piece, at most 4 blocks wide on X/Z and 2 blocks high. Pieces start from the edge of the build, and two blocks
 * only merge when they are aligned on the other two axes too, so every piece stays a clean box.
 * <p>
 * Each face picks one of 16 sprites from which of its 4 edges are connected (bit 1 up, 2 right, 4 down, 8 left,
 * in the texture orientation of vanilla block faces).
 */
public class ConnectedPlasticModel implements BakedModel {
    private static final int SPAN_HORIZONTAL = 4;
    private static final int SPAN_VERTICAL = 2;
    // Longest run measured from the edge of the build; longer runs fall back to the world grid
    private static final int MAX_SCAN = 12;

    // Texture up / left of each face, following the vanilla block face UVs
    private static final Direction[] TEXTURE_UP = new Direction[6];
    private static final Direction[] TEXTURE_LEFT = new Direction[6];

    static {
        set(Direction.UP, Direction.NORTH, Direction.WEST);
        set(Direction.DOWN, Direction.SOUTH, Direction.WEST);
        set(Direction.NORTH, Direction.UP, Direction.EAST);
        set(Direction.SOUTH, Direction.UP, Direction.WEST);
        set(Direction.WEST, Direction.UP, Direction.NORTH);
        set(Direction.EAST, Direction.UP, Direction.SOUTH);
    }

    private static void set(Direction face, Direction up, Direction left) {
        TEXTURE_UP[face.ordinal()] = up;
        TEXTURE_LEFT[face.ordinal()] = left;
    }

    private final BakedModel base;
    private final Sprite[] sprites;

    public ConnectedPlasticModel(BakedModel base, Sprite[] sprites) {
        this.base = base;
        this.sprites = sprites;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockRenderView world, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
        QuadEmitter emitter = context.getEmitter();
        int[] phase = phases(world, state, pos);
        for (Direction face : Direction.values()) {
            if (context.isFaceCulled(face)) continue;
            Direction up = TEXTURE_UP[face.ordinal()];
            Direction left = TEXTURE_LEFT[face.ordinal()];
            int mask = 0;
            if (connected(world, state, pos, phase, up)) mask |= 1;
            if (connected(world, state, pos, phase, left.getOpposite())) mask |= 2;
            if (connected(world, state, pos, phase, up.getOpposite())) mask |= 4;
            if (connected(world, state, pos, phase, left)) mask |= 8;
            emitter.square(face, 0, 0, 1, 1, 0);
            emitter.cullFace(face);
            emitter.spriteBake(sprites[mask], MutableQuadView.BAKE_LOCK_UV);
            emitter.color(-1, -1, -1, -1);
            emitter.emit();
        }
    }

    private boolean connected(BlockRenderView world, BlockState state, BlockPos pos, int[] phase, Direction dir) {
        BlockPos other = pos.offset(dir);
        if (world.getBlockState(other) != state) return false;
        int axis = dir.getAxis().ordinal();
        int span = span(axis);
        // A piece boundary on the axis between the two blocks
        if (phase[axis] == (dir.getDirection() == Direction.AxisDirection.POSITIVE ? span - 1 : 0)) return false;
        // Aligned on the two other axes, so that pieces stay boxes
        int[] otherPhase = phases(world, state, other);
        for (int a = 0; a < 3; a++) {
            if (a != axis && otherPhase[a] != phase[a]) return false;
        }
        return true;
    }

    /** Position inside the piece on each axis (X, Y, Z), counted from the edge of the build. */
    private static int[] phases(BlockRenderView world, BlockState state, BlockPos pos) {
        int[] phase = new int[3];
        for (Direction.Axis axis : Direction.Axis.values()) {
            Direction back = Direction.from(axis, Direction.AxisDirection.NEGATIVE);
            int span = span(axis.ordinal());
            int run = 0;
            BlockPos.Mutable cursor = pos.mutableCopy();
            while (run < MAX_SCAN && world.getBlockState(cursor.move(back)) == state) run++;
            phase[axis.ordinal()] = run < MAX_SCAN
                    ? run % span
                    : Math.floorMod(axis.choose(pos.getX(), pos.getY(), pos.getZ()), span);
        }
        return phase;
    }

    private static int span(int axisOrdinal) {
        return axisOrdinal == Direction.Axis.Y.ordinal() ? SPAN_VERTICAL : SPAN_HORIZONTAL;
    }

    // Everything else (items, particles, fallback) uses the standalone tile

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
