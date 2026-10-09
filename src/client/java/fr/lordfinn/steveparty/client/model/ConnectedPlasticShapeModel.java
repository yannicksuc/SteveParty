package fr.lordfinn.steveparty.client.model;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Connected texture for a plastic slab, stairs or wall part, with the 47 sprites of the plastic block of its colour
 * (see {@link ConnectedPlasticModel}): the faces of these shapes join the faces of their neighbours of the same
 * colour (shapes, blocks, double slabs) where they are flush along a whole edge (rules in {@link PlasticConnections}),
 * and pieces are cut the same way as the blocks' ({@link PieceLayout}).
 * <p>
 * The vanilla shape models texture each quad with the part of the block texture under it: each quad keeps its UVs
 * and only swaps the sprite for the one of its connections. Only the edges of a quad that lie on the edge of the
 * block can join a neighbour; the others are inside the texture, where no border is drawn anyway. Which way the
 * texture of a quad looks is read from its UVs, so rotated (uvlock) variants work as they are.
 */
public class ConnectedPlasticShapeModel implements BakedModel {
    private static final float ON_EDGE = 1e-4f;
    private static final Direction[] CULL_FACES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST, null};

    /** Material of the quads (the renderer's default), set once when baked; null without a renderer (nothing draws then). */
    @Nullable
    private final RenderMaterial material;
    private final BakedModel base;
    /** The 47 sprites of the colour's plastic block, by mask (see {@link ConnectedPlasticModel#isValidMask}). */
    private final Sprite[] sprites;

    public ConnectedPlasticShapeModel(BakedModel base, Sprite[] sprites) {
        this.base = base;
        this.sprites = sprites;
        Renderer renderer = RendererAccess.INSTANCE.getRenderer();
        this.material = renderer == null ? null : renderer.materialFinder().find();
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockRenderView world, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context) {
        if (material == null) return;
        QuadEmitter emitter = context.getEmitter();
        boolean connect = PlasticConnections.colour(world.getBlockState(pos)) != 0;
        PieceLayout layout = connect ? PieceLayout.begin(world, pos, PlasticConnections.LOOKUP, PlasticConnections.JOINER) : null;
        try {
            for (Direction cull : CULL_FACES) {
                if (cull != null && context.isFaceCulled(cull)) continue;
                for (BakedQuad quad : base.getQuads(state, cull, randomSupplier.get())) {
                    emitter.fromVanilla(quad, material, cull);
                    Sprite from = quad.getSprite();
                    Sprite to = layout == null ? null : sprites[mask(world, layout, pos, emitter, quad.getFace())];
                    if (to != null && to != from) {
                        for (int i = 0; i < 4; i++) {
                            emitter.uv(i, MathHelper.map(emitter.u(i), from.getMinU(), from.getMaxU(), to.getMinU(), to.getMaxU()),
                                    MathHelper.map(emitter.v(i), from.getMinV(), from.getMaxV(), to.getMinV(), to.getMaxV()));
                        }
                    }
                    emitter.emit();
                }
            }
        } finally {
            if (layout != null) layout.end();
        }
    }

    /** Sprite mask of the quad in {@code emitter}, facing {@code face}. */
    private static int mask(BlockRenderView world, PieceLayout layout, BlockPos pos, QuadEmitter emitter, Direction face) {
        int faceAxis = face.getAxis().ordinal();
        int axisA = faceAxis == 0 ? 1 : 0;
        int axisB = faceAxis == 2 ? 1 : 2;
        // Where the quad lies, and which way its texture runs (UVs against positions on the two axes of its plane)
        float minA = 1, maxA = 0, minB = 1, maxB = 0, meanA = 0, meanB = 0, meanU = 0, meanV = 0;
        for (int i = 0; i < 4; i++) {
            float a = emitter.posByIndex(i, axisA), b = emitter.posByIndex(i, axisB);
            minA = Math.min(minA, a);
            maxA = Math.max(maxA, a);
            minB = Math.min(minB, b);
            maxB = Math.max(maxB, b);
            meanA += a / 4;
            meanB += b / 4;
            meanU += emitter.u(i) / 4;
            meanV += emitter.v(i) / 4;
        }
        float uA = 0, uB = 0, vA = 0, vB = 0;
        for (int i = 0; i < 4; i++) {
            float a = emitter.posByIndex(i, axisA) - meanA, b = emitter.posByIndex(i, axisB) - meanB;
            float u = emitter.u(i) - meanU, v = emitter.v(i) - meanV;
            uA += a * u;
            uB += b * u;
            vA += a * v;
            vB += b * v;
        }
        // The texture's top lies where v is smallest, its left where u is smallest
        boolean vOnA = Math.abs(vA) >= Math.abs(vB);
        int upAxis = vOnA ? axisA : axisB;
        int leftAxis = vOnA ? axisB : axisA;
        float vSlope = vOnA ? vA : vB;
        float uSlope = vOnA ? uB : uA;
        if (vSlope == 0 || uSlope == 0) return 0;
        Direction up = direction(upAxis, vSlope < 0);
        Direction left = direction(leftAxis, uSlope < 0);

        float upMin = upAxis == axisA ? minA : minB, upMax = upAxis == axisA ? maxA : maxB;
        float leftMin = leftAxis == axisA ? minA : minB, leftMax = leftAxis == axisA ? maxA : maxB;
        boolean upPositive = up.getDirection() == Direction.AxisDirection.POSITIVE;
        boolean leftPositive = left.getDirection() == Direction.AxisDirection.POSITIVE;
        boolean atUp = upPositive ? upMax >= 1 - ON_EDGE : upMin <= ON_EDGE;
        boolean atDown = upPositive ? upMin <= ON_EDGE : upMax >= 1 - ON_EDGE;
        boolean atLeft = leftPositive ? leftMax >= 1 - ON_EDGE : leftMin <= ON_EDGE;
        boolean atRight = leftPositive ? leftMin <= ON_EDGE : leftMax >= 1 - ON_EDGE;
        if (!(atUp || atDown || atLeft || atRight)) return 0;
        float plane = emitter.posByIndex(0, faceAxis);
        return ConnectedPlasticModel.mask(world, layout, pos, face, plane, up, left,
                span(upMin, upMax), span(leftMin, leftMax), atUp, atRight, atDown, atLeft);
    }

    private static Direction direction(int axis, boolean positive) {
        return Direction.from(Direction.Axis.values()[axis],
                positive ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
    }

    /** The 16ths (bits, see {@link PlasticConnections#joined}) from {@code min} to {@code max}. */
    private static int span(float min, float max) {
        int bits = 0;
        for (int k = 0; k < 16; k++) {
            float t = (k + 0.5f) / 16;
            if (t >= min && t <= max) bits |= 1 << k;
        }
        return bits;
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
