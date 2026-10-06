package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.util.RenderUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the cubes of the Boxed Trader's box bones ({@code cube*}) with the look of a block, shared by the merchant and
 * the Box Costume. Render thread only.
 * <p>
 * Each face of a cube shows what the block shows on that side: every quad of the block's model facing that way is
 * projected on the face, where it is in the block, with its own texture coordinates, sprite and tint. So a face made
 * of several pieces (two-coloured tiles), a rotated texture (logs, pistons), an overlay (grass) or a block seen
 * through its glass (beacon) look like the real block. Pieces deeper in the block are drawn first, the outer ones
 * over them. Where a face is not fully covered by an outer piece (dragon egg, non-cubic blocks), the block's particle
 * texture is drawn underneath so the box has no hole. Translucent blocks (ice, stained glass) stay translucent.
 * <p>
 * A block without a usable look (air, an invisible block, a model whose sprites are missing) is drawn as the
 * merchant's default gold block: the purple and black missing texture only shows when asked for (no block at all: the
 * merchant whose box lost its look).
 */
public final class BlockTexturedBones {
    public static final String CUBE_BONE_PREFIX = "cube";
    private static final String SHADOW_BONE = "shadow";
    private static final BlockState FALLBACK = Blocks.GOLD_BLOCK.getDefaultState();
    private static final Direction[] DIRECTIONS = Direction.values();
    /** Vertex data of a baked quad: 8 ints per vertex (position, colour, uv, light, normal). */
    private static final int STRIDE = 8, U = 4, V = 5;
    /** Each piece drawn over another is lifted this much off the face (no z-fighting between coplanar pieces). */
    private static final float LAYER_LIFT = 4.0E-4F;
    private static final float EDGE = 1.0E-3F;

    /** One piece of a face: where it is on the face (s, t in 0..1, per vertex), its atlas uvs, sprite, tint index. */
    private record Piece(float[] s, float[] t, float[] u, float[] v, Identifier atlas, int tintIndex, float depth) {
    }

    /** The pieces of each face of a block, deepest first; cached per block state until its model changes. */
    private record Faces(BakedModel model, List<Piece>[] pieces) {
    }

    private static final Map<BlockState, Faces> CACHE = new IdentityHashMap<>();

    private final Vector4f scratchPosition = new Vector4f();
    private final Vector3f scratchNormal = new Vector3f();
    private final float[][] corners = new float[4][3];
    /** The face's part of the block face: min u, max u, min v, max v. */
    private final float[] rect = new float[4];
    /** A piece clipped to {@link #rect}: s, t, u, v per vertex (a quad clipped by a rectangle keeps at most 8). */
    private final float[][] poly = new float[4][8], clipped = new float[4][8];
    private final float[] px = new float[8], py = new float[8], pz = new float[8], turnS = new float[4], turnT = new float[4];

    public static boolean isBoxBone(GeoBone bone) {
        return bone.getName().startsWith(CUBE_BONE_PREFIX);
    }

    /** The dark plane inside the box: not drawn in a box one sees through (see {@link #isSeeThrough}). */
    public static boolean isHiddenInside(GeoBone bone, @Nullable BlockState state) {
        return SHADOW_BONE.equals(bone.getName()) && isSeeThrough(state);
    }

    /**
     * Whether one sees through the block (glass, leaves, ice...): the inside of a box of it must then show nothing
     * dark (the box's inner shadow is not drawn).
     */
    public static boolean isSeeThrough(@Nullable BlockState state) {
        return BoxedTraderEntity.isValidBoxBlock(state) && RenderLayers.getBlockLayer(state) != RenderLayer.getSolid();
    }

    /**
     * Draws the cubes of {@code bone} (the pose stack already transformed for it by GeckoLib) with {@code blockState}'s
     * faces, or with the missing texture on every face if {@code blockState} is null. Tints are taken at {@code pos}
     * (biome colours), or are the block's default ones without it (item icon).
     */
    public void renderBone(MatrixStack poseStack, GeoBone bone, VertexConsumerProvider bufferSource, int packedLight, int renderColor,
                           @Nullable BlockState blockState, @Nullable BlockPos pos) {
        if (bone.isHidden() || !isBoxBone(bone)) return;
        boolean glitched = blockState == null;
        BlockState state = BoxedTraderEntity.isValidBoxBlock(blockState) ? blockState : FALLBACK;
        Faces faces = glitched ? null : faces(state);
        boolean translucent = !glitched && RenderLayers.getBlockLayer(state) == RenderLayer.getTranslucent();
        for (GeoCube cube : bone.getCubes()) {
            poseStack.push();
            RenderUtil.translateToPivotPoint(poseStack, cube);
            RenderUtil.rotateMatrixAroundCube(poseStack, cube);
            RenderUtil.translateAwayFromPivotPoint(poseStack, cube);
            Matrix3f normalMatrix = poseStack.peek().getNormalMatrix();
            Matrix4f positionMatrix = poseStack.peek().getPositionMatrix();
            for (GeoQuad quad : cube.quads()) {
                if (quad == null || quad.direction() == null) continue;
                Vector3f normal = normalMatrix.transform(scratchNormal.set(quad.normal()));
                RenderUtil.fixInvertedFlatCube(cube, normal);
                if (!cornersOf(quad, positionMatrix)) continue;
                if (faces == null) {
                    Sprite missing = missingSprite();
                    drawPiece(fullFace(missing, -1), missing.getAtlasId(), renderColor, 0, normal, packedLight, bufferSource, false, quad);
                    continue;
                }
                List<Piece> pieces = faces.pieces()[quad.direction().ordinal()];
                for (int i = 0; i < pieces.size(); i++) {
                    Piece piece = pieces.get(i);
                    int color = piece.tintIndex() >= 0 ? tinted(renderColor, state, pos, piece.tintIndex()) : renderColor;
                    drawPiece(piece, piece.atlas(), color, i * LAYER_LIFT, normal, packedLight, bufferSource, translucent, quad);
                }
            }
            poseStack.pop();
        }
    }

    /**
     * Fills {@link #corners} with the face's corners, transformed, and {@link #rect} with the part of the block face it
     * shows: its uvs are in block face space (the box's texture is 64 px for a whole block face). Corners: [0] at
     * (min u, min v), [1] (max u, min v), [2] (max u, max v), [3] (min u, max v).
     *
     * @return false for a face without a usable texture rectangle
     */
    private boolean cornersOf(GeoQuad quad, Matrix4f positionMatrix) {
        GeoVertex[] vertices = quad.vertices();
        if (vertices.length != 4) return false;
        float minU = Float.MAX_VALUE, maxU = -Float.MAX_VALUE, minV = Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
        for (GeoVertex vertex : vertices) {
            minU = Math.min(minU, vertex.texU());
            maxU = Math.max(maxU, vertex.texU());
            minV = Math.min(minV, vertex.texV());
            maxV = Math.max(maxV, vertex.texV());
        }
        if (maxU - minU < 1.0E-6F || maxV - minV < 1.0E-6F) return false;
        int found = 0;
        for (GeoVertex vertex : vertices) {
            boolean right = vertex.texU() - minU > (maxU - minU) / 2, bottom = vertex.texV() - minV > (maxV - minV) / 2;
            int corner = bottom ? (right ? 2 : 3) : (right ? 1 : 0);
            Vector3f position = vertex.position();
            Vector4f transformed = positionMatrix.transform(scratchPosition.set(position.x(), position.y(), position.z(), 1.0F));
            corners[corner][0] = transformed.x();
            corners[corner][1] = transformed.y();
            corners[corner][2] = transformed.z();
            found |= 1 << corner;
        }
        rect[0] = minU;
        rect[1] = maxU;
        rect[2] = minV;
        rect[3] = maxV;
        return found == 0b1111;
    }

    /**
     * Draws the part of a piece inside the face's rectangle ({@link #rect}) on the face whose corners are in
     * {@link #corners}, lifted off it along its normal.
     */
    private void drawPiece(Piece piece, Identifier atlas, int color, float lift, Vector3f normal, int packedLight,
                           VertexConsumerProvider bufferSource, boolean translucent, GeoQuad quad) {
        int count = clip(piece);
        if (count < 3) return;
        float spanU = rect[1] - rect[0], spanV = rect[3] - rect[2];
        for (int i = 0; i < count; i++) {
            float s = (poly[0][i] - rect[0]) / spanU, t = (poly[1][i] - rect[2]) / spanV;
            float a = (1 - s) * (1 - t), b = s * (1 - t), c = s * t, d = (1 - s) * t;
            px[i] = a * corners[0][0] + b * corners[1][0] + c * corners[2][0] + d * corners[3][0] + normal.x() * lift;
            py[i] = a * corners[0][1] + b * corners[1][1] + c * corners[2][1] + d * corners[3][1] + normal.y() * lift;
            pz[i] = a * corners[0][2] + b * corners[1][2] + c * corners[2][2] + d * corners[3][2] + normal.z() * lift;
        }
        // Same turning direction on the face as the face's own vertices: never drawn back to front (culled)
        boolean reversed = signedArea(poly[0], poly[1], count) * faceTurn(quad) < 0;
        VertexConsumer consumer = bufferSource.getBuffer(translucent ? RenderLayer.getEntityTranslucent(atlas) : RenderLayer.getEntityCutout(atlas));
        // A fan of quads (the last one of each repeated: a triangle)
        for (int k = 1; k + 1 < count; k++) {
            int[] fan = {0, k, k + 1, k + 1};
            for (int f = 0; f < 4; f++) {
                int i = fan[reversed ? 3 - f : f];
                consumer.vertex(px[i], py[i], pz[i], color, poly[2][i], poly[3][i], OverlayTexture.DEFAULT_UV, packedLight,
                        normal.x(), normal.y(), normal.z());
            }
        }
    }

    /**
     * Clips the piece (s, t, u, v per vertex) to {@link #rect}, into {@link #poly}.
     *
     * @return the number of vertices left
     */
    private int clip(Piece piece) {
        int count = 4;
        for (int i = 0; i < 4; i++) {
            poly[0][i] = piece.s()[i];
            poly[1][i] = piece.t()[i];
            poly[2][i] = piece.u()[i];
            poly[3][i] = piece.v()[i];
        }
        for (int edge = 0; edge < 4 && count > 0; edge++) {
            int axis = edge / 2;
            boolean keepAbove = edge % 2 == 0;
            float limit = rect[edge];
            int out = 0;
            for (int i = 0; i < count; i++) {
                int j = (i + 1) % count;
                float di = keepAbove ? poly[axis][i] - limit : limit - poly[axis][i];
                float dj = keepAbove ? poly[axis][j] - limit : limit - poly[axis][j];
                if (di >= 0) copy(poly, i, clipped, out++);
                if ((di >= 0) != (dj >= 0)) {
                    float k = di / (di - dj);
                    for (int c = 0; c < 4; c++) clipped[c][out] = poly[c][i] + (poly[c][j] - poly[c][i]) * k;
                    out++;
                }
            }
            for (int c = 0; c < 4; c++) System.arraycopy(clipped[c], 0, poly[c], 0, out);
            count = out;
        }
        return count;
    }

    private static void copy(float[][] from, int i, float[][] to, int j) {
        for (int c = 0; c < 4; c++) to[c][j] = from[c][i];
    }

    /** Turning direction of the face's vertices in its texture space (u right, v down). */
    private float faceTurn(GeoQuad quad) {
        GeoVertex[] vertices = quad.vertices();
        for (int i = 0; i < 4; i++) {
            turnS[i] = vertices[i].texU();
            turnT[i] = vertices[i].texV();
        }
        return signedArea(turnS, turnT, 4);
    }

    private static float signedArea(float[] s, float[] t, int count) {
        float area = 0;
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            area += s[i] * t[j] - s[j] * t[i];
        }
        return area;
    }

    // ---- The pieces of a block's faces ----

    private static Faces faces(BlockState state) {
        BakedModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel(state);
        Faces faces = CACHE.get(state);
        if (faces == null || faces.model() != model) {
            faces = new Faces(model, buildPieces(state, model));
            CACHE.put(state, faces);
        }
        return faces;
    }

    @SuppressWarnings("unchecked")
    private static List<Piece>[] buildPieces(BlockState state, BakedModel model) {
        List<Piece>[] pieces = new List[DIRECTIONS.length];
        for (int i = 0; i < pieces.length; i++) pieces[i] = new ArrayList<>();
        Random random = Random.create(42L);
        List<BakedQuad> quads = new ArrayList<>(model.getQuads(state, null, random));
        for (Direction direction : DIRECTIONS) quads.addAll(model.getQuads(state, direction, random));
        for (BakedQuad quad : quads) {
            if (isMissing(quad.getSprite())) continue;
            Piece piece = piece(quad);
            if (piece != null) pieces[quad.getFace().ordinal()].add(piece);
        }
        Sprite particle = model.getParticleSprite();
        for (Direction direction : DIRECTIONS) {
            List<Piece> face = pieces[direction.ordinal()];
            // Deepest first (stable: the model's order among pieces at the same depth, e.g. grass over dirt)
            face.sort(Comparator.comparingDouble(piece -> -piece.depth()));
            if (!covers(face)) {
                face.addFirst(fullFace(!isMissing(particle) ? particle : fallbackParticle(), -1));
            }
        }
        return pieces;
    }

    /** A quad of the block model as a piece of its face (null if it has no area there). */
    @Nullable
    private static Piece piece(BakedQuad quad) {
        int[] data = quad.getVertexData();
        if (data.length < 4 * STRIDE) return null;
        Direction face = quad.getFace();
        float[] s = new float[4], t = new float[4], u = new float[4], v = new float[4];
        float depth = 0;
        for (int i = 0; i < 4; i++) {
            float x = Float.intBitsToFloat(data[i * STRIDE]), y = Float.intBitsToFloat(data[i * STRIDE + 1]),
                    z = Float.intBitsToFloat(data[i * STRIDE + 2]);
            u[i] = Float.intBitsToFloat(data[i * STRIDE + U]);
            v[i] = Float.intBitsToFloat(data[i * STRIDE + V]);
            switch (face) {
                case DOWN -> { s[i] = x; t[i] = 1 - z; depth += y; }
                case UP -> { s[i] = x; t[i] = z; depth += 1 - y; }
                case NORTH -> { s[i] = 1 - x; t[i] = 1 - y; depth += z; }
                case SOUTH -> { s[i] = x; t[i] = 1 - y; depth += 1 - z; }
                case WEST -> { s[i] = z; t[i] = 1 - y; depth += x; }
                case EAST -> { s[i] = 1 - z; t[i] = 1 - y; depth += 1 - x; }
            }
        }
        if (Math.abs(signedArea(s, t, 4)) < 1.0E-6F) return null;
        return new Piece(s, t, u, v, quad.getSprite().getAtlasId(), quad.hasColor() ? quad.getColorIndex() : -1, Math.max(0, depth / 4));
    }

    /** Whether an outer piece covers the whole face (nothing to draw underneath). */
    private static boolean covers(List<Piece> face) {
        for (Piece piece : face) {
            if (piece.depth() > EDGE) continue;
            float minS = 1, maxS = 0, minT = 1, maxT = 0;
            for (int i = 0; i < 4; i++) {
                minS = Math.min(minS, piece.s()[i]);
                maxS = Math.max(maxS, piece.s()[i]);
                minT = Math.min(minT, piece.t()[i]);
                maxT = Math.max(maxT, piece.t()[i]);
            }
            if (minS <= EDGE && minT <= EDGE && maxS >= 1 - EDGE && maxT >= 1 - EDGE) return true;
        }
        return false;
    }

    /** A whole sprite over the whole face, upright, untinted. Corners in the order top left, top right, bottom right, bottom left. */
    private static Piece fullFace(Sprite sprite, int tintIndex) {
        return new Piece(new float[]{0, 1, 1, 0}, new float[]{0, 0, 1, 1},
                new float[]{sprite.getMinU(), sprite.getMaxU(), sprite.getMaxU(), sprite.getMinU()},
                new float[]{sprite.getMinV(), sprite.getMinV(), sprite.getMaxV(), sprite.getMaxV()},
                sprite.getAtlasId(), tintIndex, 1.0F);
    }

    private static Sprite fallbackParticle() {
        return MinecraftClient.getInstance().getBlockRenderManager().getModel(FALLBACK).getParticleSprite();
    }

    /** {@code renderColor} multiplied by the block's colour for that tint index (grass, foliage... at {@code pos}). */
    private static int tinted(int renderColor, BlockState state, @Nullable BlockPos pos, int tintIndex) {
        MinecraftClient client = MinecraftClient.getInstance();
        int tint = client.getBlockColors().getColor(state, pos == null ? null : client.world, pos, tintIndex);
        if (tint == -1) return renderColor;
        return ColorHelper.Argb.getArgb(ColorHelper.Argb.getAlpha(renderColor),
                ColorHelper.Argb.getRed(renderColor) * ColorHelper.Argb.getRed(tint) / 255,
                ColorHelper.Argb.getGreen(renderColor) * ColorHelper.Argb.getGreen(tint) / 255,
                ColorHelper.Argb.getBlue(renderColor) * ColorHelper.Argb.getBlue(tint) / 255);
    }

    /** The vanilla purple and black checker, from the block atlas. */
    private static Sprite missingSprite() {
        return MinecraftClient.getInstance().getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(MissingSprite.getMissingSpriteId());
    }

    private static boolean isMissing(@Nullable Sprite sprite) {
        return sprite == null || MissingSprite.getMissingSpriteId().equals(sprite.getContents().getId());
    }
}
