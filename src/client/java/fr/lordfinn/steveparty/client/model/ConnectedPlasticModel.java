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
 * Connected texture for the plastic blocks: touching blocks of the same colour merge into one plastic
 * piece, at most 4 blocks wide on X/Z and 2 blocks high. Pieces start from the edge of the build, and two blocks
 * only merge when they are aligned on the other two axes too, so a piece never outgrows its 4x2x4 box (it can
 * still wrap around another block, hence the inner corner sprites). Where pieces start is worked out by
 * {@link PieceLayout}. A double plastic slab (drawn with this model) is the full block of its colour and merges with
 * those blocks. The slabs, stairs and walls of the colour ({@link ConnectedPlasticShapeModel}) join the piece where
 * their faces are flush with the block's along a whole edge (see {@link PlasticConnections}).
 * <p>
 * Each face picks one of 47 sprites from which of its 4 edges are connected (bit 1 up, 2 right, 4 down, 8 left,
 * in the texture orientation of vanilla block faces) and which inner corners it must close (bits 16 to 128).
 */
public class ConnectedPlasticModel implements BakedModel {
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

    /** @return the side the top of {@code face}'s texture points to (vanilla block face UVs). */
    public static Direction textureUp(Direction face) {
        return TEXTURE_UP[face.ordinal()];
    }

    /** @return the side the left of {@code face}'s texture points to (vanilla block face UVs). */
    public static Direction textureLeft(Direction face) {
        return TEXTURE_LEFT[face.ordinal()];
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
        PieceLayout layout = PieceLayout.begin(world, pos, PlasticConnections.LOOKUP, PlasticConnections.JOINER);
        try {
            for (Direction face : Direction.values()) {
                if (context.isFaceCulled(face)) continue;
                float plane = face.getDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
                int mask = mask(world, layout, pos, face, plane, TEXTURE_UP[face.ordinal()], TEXTURE_LEFT[face.ordinal()],
                        PlasticConnections.FULL_SPAN, PlasticConnections.FULL_SPAN, true, true, true, true);
                emitter.square(face, 0, 0, 1, 1, 0);
                emitter.cullFace(face);
                emitter.spriteBake(sprites[mask], MutableQuadView.BAKE_LOCK_UV);
                emitter.color(-1, -1, -1, -1);
                emitter.emit();
            }
        } finally {
            layout.end();
        }
    }

    /**
     * Sprite mask of a face (or part of one) of the block at {@code pos}, whose texture top and left look to
     * {@code up} and {@code left}; {@code verticalSpan} / {@code horizontalSpan} are the bits (see
     * {@link PlasticConnections#joined}) the face covers along its left / right edges and along its top / bottom
     * edges, and each {@code at...} flag says whether that edge of the face lies on the edge of the block.
     */
    static int mask(BlockRenderView world, PieceLayout layout, BlockPos pos, Direction face, float plane,
                    Direction up, Direction left, int verticalSpan, int horizontalSpan,
                    boolean atUp, boolean atRight, boolean atDown, boolean atLeft) {
        Direction right = left.getOpposite();
        Direction down = up.getOpposite();
        boolean u = atUp && PlasticConnections.joined(world, layout, pos, face, plane, up, horizontalSpan);
        boolean r = atRight && PlasticConnections.joined(world, layout, pos, face, plane, right, verticalSpan);
        boolean d = atDown && PlasticConnections.joined(world, layout, pos, face, plane, down, horizontalSpan);
        boolean l = atLeft && PlasticConnections.joined(world, layout, pos, face, plane, left, verticalSpan);
        int mask = (u ? UP : 0) | (r ? RIGHT : 0) | (d ? DOWN : 0) | (l ? LEFT : 0);
        // Inner corners: both sides belong to the piece but the diagonal block does not
        if (u && l && !cornerJoined(world, layout, pos.offset(up), face, plane, left, down)) mask |= INNER_UP_LEFT;
        if (u && r && !cornerJoined(world, layout, pos.offset(up), face, plane, right, down)) mask |= INNER_UP_RIGHT;
        if (d && r && !cornerJoined(world, layout, pos.offset(down), face, plane, right, up)) mask |= INNER_DOWN_RIGHT;
        if (d && l && !cornerJoined(world, layout, pos.offset(down), face, plane, left, up)) mask |= INNER_DOWN_LEFT;
        return mask;
    }

    /** @return whether the face of {@code pos} goes on across {@code edge} at its end next to side {@code near}. */
    private static boolean cornerJoined(BlockRenderView world, PieceLayout layout, BlockPos pos, Direction face, float plane,
                                        Direction edge, Direction near) {
        int bit = near.getDirection() == Direction.AxisDirection.POSITIVE ? 1 << 15 : 1;
        return PlasticConnections.joined(world, layout, pos, face, plane, edge, bit);
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
