package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.entities.custom.HidingTraderEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
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
import java.util.Arrays;
import java.util.List;

/**
 * Draws the cubes of the Hiding Trader's box bones ({@code cube*}) with the faces of a block: each face of a cube takes
 * the sprites (and tints) of the same face of the block model. Shared by the merchant and the Box Costume. Render thread only.
 * <p>
 * A block without a usable look (air, an invisible block, a model whose sprites are missing) is drawn as the
 * merchant's default gold block: the purple and black missing texture only shows when asked for (no block at all: the
 * merchant whose box lost its look).
 */
public final class BlockTexturedBones {
    public static final String CUBE_BONE_PREFIX = "cube";
    private static final BlockState FALLBACK = Blocks.GOLD_BLOCK.getDefaultState();
    private static final Direction[] DIRECTIONS = Direction.values();

    /** One textured layer of a face: a sprite of the block's face and its colour (tinted quads: grass, leaves...). */
    private record FaceLayer(VertexConsumer consumer, int color) {
    }

    @SuppressWarnings("unchecked")
    private final List<FaceLayer>[] faceLayers = new List[DIRECTIONS.length + 1];
    private final Vector4f scratchPosition = new Vector4f();
    private final Vector3f scratchNormal = new Vector3f();
    private final Random random = Random.create();

    public static boolean isBoxBone(GeoBone bone) {
        return bone.getName().startsWith(CUBE_BONE_PREFIX);
    }

    /**
     * Draws the cubes of {@code bone} (the pose stack already transformed for it by GeckoLib) with {@code blockState}'s
     * faces, or with the missing texture on every face if {@code blockState} is null.
     * <p>
     * Every quad of the block's face is drawn, in the model's order, each with its own tint: a grass block's side is
     * its dirt side plus the tinted grass overlay, its top the tinted grass, like the real block. Tints are taken at
     * {@code pos} (biome colours), or are the block's default ones without it (item icon).
     */
    public void renderBone(MatrixStack poseStack, GeoBone bone, VertexConsumerProvider bufferSource, int packedLight, int renderColor,
                           @Nullable BlockState blockState, @Nullable BlockPos pos) {
        if (bone.isHidden() || !isBoxBone(bone)) return;
        boolean glitched = blockState == null;
        BlockState state = HidingTraderEntity.isValidBoxBlock(blockState) ? blockState : FALLBACK;
        BakedModel model = glitched ? null : MinecraftClient.getInstance().getBlockRenderManager().getModel(state);
        // Layers are resolved lazily, once per face direction, for this call only
        Arrays.fill(faceLayers, null);
        for (GeoCube cube : bone.getCubes()) {
            poseStack.push();
            RenderUtil.translateToPivotPoint(poseStack, cube);
            RenderUtil.rotateMatrixAroundCube(poseStack, cube);
            RenderUtil.translateAwayFromPivotPoint(poseStack, cube);
            Matrix3f normalMatrix = poseStack.peek().getNormalMatrix();
            Matrix4f positionMatrix = poseStack.peek().getPositionMatrix();
            for (GeoQuad quad : cube.quads()) {
                if (quad == null) continue;
                Vector3f normal = normalMatrix.transform(scratchNormal.set(quad.normal()));
                RenderUtil.fixInvertedFlatCube(cube, normal);
                for (FaceLayer layer : getFaceLayers(quad.direction(), bufferSource, state, model, renderColor, pos)) {
                    for (GeoVertex vertex : quad.vertices()) {
                        Vector3f position = vertex.position();
                        Vector4f transformed = positionMatrix.transform(scratchPosition.set(position.x(), position.y(), position.z(), 1.0F));
                        layer.consumer().vertex(transformed.x(), transformed.y(), transformed.z(), layer.color(), vertex.texU(), vertex.texV(),
                                OverlayTexture.DEFAULT_UV, packedLight, normal.x(), normal.y(), normal.z());
                    }
                }
            }
            poseStack.pop();
        }
    }

    private List<FaceLayer> getFaceLayers(Direction direction, VertexConsumerProvider bufferSource, BlockState state, @Nullable BakedModel model,
                                          int renderColor, @Nullable BlockPos pos) {
        int index = direction == null ? DIRECTIONS.length : direction.ordinal();
        List<FaceLayer> layers = faceLayers[index];
        if (layers == null) {
            layers = new ArrayList<>(2);
            if (model == null) {
                layers.add(layer(missingSprite(), renderColor, bufferSource));
            } else {
                addLayers(layers, direction, model, state, renderColor, pos, bufferSource);
                if (layers.isEmpty()) {
                    BakedModel fallback = MinecraftClient.getInstance().getBlockRenderManager().getModel(FALLBACK);
                    addLayers(layers, direction, fallback, FALLBACK, renderColor, pos, bufferSource);
                }
            }
            faceLayers[index] = layers;
        }
        return layers;
    }

    /**
     * The layers of the block's face: all its quads; if it has none (culled face of a non-cube model...), the block's
     * first unculled quad, else its particle sprite. Missing sprites are left out.
     */
    private void addLayers(List<FaceLayer> layers, Direction direction, BakedModel model, BlockState state, int renderColor,
                           @Nullable BlockPos pos, VertexConsumerProvider bufferSource) {
        List<BakedQuad> quads = model.getQuads(state, direction, random);
        if (quads.isEmpty()) {
            Sprite particle = model.getParticleSprite();
            List<BakedQuad> general = model.getQuads(state, null, random);
            if (isMissing(particle) && !general.isEmpty()) quads = List.of(general.getFirst());
            else if (!isMissing(particle)) layers.add(layer(particle, renderColor, bufferSource));
        }
        for (BakedQuad quad : quads) {
            if (isMissing(quad.getSprite())) continue;
            int color = quad.hasColor() ? tinted(renderColor, state, pos, quad.getColorIndex()) : renderColor;
            layers.add(layer(quad.getSprite(), color, bufferSource));
        }
    }

    private static FaceLayer layer(Sprite sprite, int color, VertexConsumerProvider bufferSource) {
        return new FaceLayer(sprite.getTextureSpecificVertexConsumer(bufferSource.getBuffer(RenderLayer.getEntityCutout(sprite.getAtlasId()))), color);
    }

    /** {@code renderColor} multiplied by the block's colour for that tint index (grass, foliage... at {@code pos}). */
    private static int tinted(int renderColor, BlockState state, @Nullable BlockPos pos, int tintIndex) {
        MinecraftClient client = MinecraftClient.getInstance();
        int tint = client.getBlockColors().getColor(state, pos == null ? null : client.world, pos, tintIndex);
        if (tint == -1) return renderColor;
        return ColorHelper.getArgb(ColorHelper.getAlpha(renderColor),
                ColorHelper.getRed(renderColor) * ColorHelper.getRed(tint) / 255,
                ColorHelper.getGreen(renderColor) * ColorHelper.getGreen(tint) / 255,
                ColorHelper.getBlue(renderColor) * ColorHelper.getBlue(tint) / 255);
    }

    /** The vanilla purple and black checker, from the block atlas. */
    private static Sprite missingSprite() {
        return MinecraftClient.getInstance().getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(MissingSprite.getMissingSpriteId());
    }

    private static boolean isMissing(Sprite sprite) {
        return sprite == null || MissingSprite.getMissingSpriteId().equals(sprite.getContents().getId());
    }
}
