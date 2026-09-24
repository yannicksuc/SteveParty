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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Connected texture for the plastic blocks: touching blocks of the same colour merge into one plastic
 * piece, at most 4 blocks wide on X/Z and 2 blocks high. Pieces start from the edge of the build, and two blocks
 * only merge when they are aligned on the other two axes too, so a piece never outgrows its 4x2x4 box (it can
 * still wrap around another block, hence the inner corner sprites).
 * <p>
 * Each face picks one of 47 sprites from which of its 4 edges are connected (bit 1 up, 2 right, 4 down, 8 left,
 * in the texture orientation of vanilla block faces) and which inner corners it must close (bits 16 to 128).
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
        Map<BlockPos, int[]> phases = new HashMap<>();
        for (Direction face : Direction.values()) {
            if (context.isFaceCulled(face)) continue;
            Direction up = TEXTURE_UP[face.ordinal()];
            Direction left = TEXTURE_LEFT[face.ordinal()];
            Direction right = left.getOpposite();
            Direction down = up.getOpposite();
            boolean u = connected(world, state, pos, up, phases);
            boolean r = connected(world, state, pos, right, phases);
            boolean d = connected(world, state, pos, down, phases);
            boolean l = connected(world, state, pos, left, phases);
            int mask = (u ? UP : 0) | (r ? RIGHT : 0) | (d ? DOWN : 0) | (l ? LEFT : 0);
            // Inner corners: both sides belong to the piece but the diagonal block does not
            if (u && l && !connected(world, state, pos.offset(up), left, phases)) mask |= INNER_UP_LEFT;
            if (u && r && !connected(world, state, pos.offset(up), right, phases)) mask |= INNER_UP_RIGHT;
            if (d && r && !connected(world, state, pos.offset(down), right, phases)) mask |= INNER_DOWN_RIGHT;
            if (d && l && !connected(world, state, pos.offset(down), left, phases)) mask |= INNER_DOWN_LEFT;
            emitter.square(face, 0, 0, 1, 1, 0);
            emitter.cullFace(face);
            emitter.spriteBake(sprites[mask], MutableQuadView.BAKE_LOCK_UV);
            emitter.color(-1, -1, -1, -1);
            emitter.emit();
        }
    }

    private static boolean connected(BlockRenderView world, BlockState state, BlockPos pos, Direction dir, Map<BlockPos, int[]> phases) {
        BlockPos other = pos.offset(dir);
        if (world.getBlockState(pos) != state || world.getBlockState(other) != state) return false;
        int[] phase = phases.computeIfAbsent(pos, p -> phases(world, state, p));
        int axis = dir.getAxis().ordinal();
        int span = span(axis);
        // A piece boundary on the axis between the two blocks
        if (phase[axis] == (dir.getDirection() == Direction.AxisDirection.POSITIVE ? span - 1 : 0)) return false;
        // Aligned on the two other axes, so that a piece never grows past 4x2x4
        int[] otherPhase = phases.computeIfAbsent(other, p -> phases(world, state, p));
        for (int a = 0; a < 3; a++) {
            if (a != axis && otherPhase[a] != phase[a]) return false;
        }
        return true;
    }

    /** Sprite index of a face: which edges are connected, plus the inner corners (a valid mask has 47 values). */
    public static final int UP = 1, RIGHT = 2, DOWN = 4, LEFT = 8;
    public static final int INNER_UP_LEFT = 16, INNER_UP_RIGHT = 32, INNER_DOWN_RIGHT = 64, INNER_DOWN_LEFT = 128;

    public static boolean isValidMask(int mask) {
        return ((mask & INNER_UP_LEFT) == 0 || (mask & (UP | LEFT)) == (UP | LEFT))
                && ((mask & INNER_UP_RIGHT) == 0 || (mask & (UP | RIGHT)) == (UP | RIGHT))
                && ((mask & INNER_DOWN_RIGHT) == 0 || (mask & (DOWN | RIGHT)) == (DOWN | RIGHT))
                && ((mask & INNER_DOWN_LEFT) == 0 || (mask & (DOWN | LEFT)) == (DOWN | LEFT));
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
