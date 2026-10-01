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

import java.util.Arrays;
import java.util.List;

/**
 * Draws the cubes of the Hiding Trader's box bones ({@code cube*}) with the faces of a block: each face of a cube takes
 * the sprite of the same face of the block model. Shared by the merchant and the Box Costume. Render thread only.
 * <p>
 * A block without a usable look (air, an invisible block, a model whose sprites are missing) is drawn as the
 * merchant's default gold block: the purple and black missing texture only shows when asked for (no block at all: the
 * merchant whose box lost its look).
 */
public final class BlockTexturedBones {
    public static final String CUBE_BONE_PREFIX = "cube";
    private static final BlockState FALLBACK = Blocks.GOLD_BLOCK.getDefaultState();
    private static final Direction[] DIRECTIONS = Direction.values();

    private final VertexConsumer[] faceConsumers = new VertexConsumer[DIRECTIONS.length + 1];
    private final Vector4f scratchPosition = new Vector4f();
    private final Vector3f scratchNormal = new Vector3f();
    private final Random random = Random.create();

    public static boolean isBoxBone(GeoBone bone) {
        return bone.getName().startsWith(CUBE_BONE_PREFIX);
    }

    /**
     * Draws the cubes of {@code bone} (the pose stack already transformed for it by GeckoLib) with {@code blockState}'s
     * faces, or with the missing texture on every face if {@code blockState} is null.
     */
    public void renderBone(MatrixStack poseStack, GeoBone bone, VertexConsumerProvider bufferSource, int packedLight, int renderColor,
                           @Nullable BlockState blockState) {
        if (bone.isHidden() || !isBoxBone(bone)) return;
        boolean glitched = blockState == null;
        BlockState state = HidingTraderEntity.isValidBoxBlock(blockState) ? blockState : FALLBACK;
        BakedModel model = glitched ? null : MinecraftClient.getInstance().getBlockRenderManager().getModel(state);
        // Buffers are resolved lazily, once per face direction, for this call only
        Arrays.fill(faceConsumers, null);
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
                VertexConsumer consumer = getFaceConsumer(quad.direction(), bufferSource, state, model);
                for (GeoVertex vertex : quad.vertices()) {
                    Vector3f position = vertex.position();
                    Vector4f transformed = positionMatrix.transform(scratchPosition.set(position.x(), position.y(), position.z(), 1.0F));
                    consumer.vertex(transformed.x(), transformed.y(), transformed.z(), renderColor, vertex.texU(), vertex.texV(),
                            OverlayTexture.DEFAULT_UV, packedLight, normal.x(), normal.y(), normal.z());
                }
            }
            poseStack.pop();
        }
    }

    private VertexConsumer getFaceConsumer(Direction direction, VertexConsumerProvider bufferSource, BlockState state, @Nullable BakedModel model) {
        int index = direction == null ? DIRECTIONS.length : direction.ordinal();
        VertexConsumer consumer = faceConsumers[index];
        if (consumer == null) {
            Sprite sprite = model == null ? missingSprite() : getSprite(direction, model, state);
            consumer = sprite.getTextureSpecificVertexConsumer(bufferSource.getBuffer(RenderLayer.getEntityCutout(sprite.getAtlasId())));
            faceConsumers[index] = consumer;
        }
        return consumer;
    }

    /** The sprite of the block's face (its particle sprite if that face has no quad), never the missing sprite. */
    private Sprite getSprite(Direction direction, BakedModel model, BlockState state) {
        Sprite sprite = spriteOf(direction, model, state);
        if (!isMissing(sprite)) return sprite;
        BakedModel fallback = MinecraftClient.getInstance().getBlockRenderManager().getModel(FALLBACK);
        return spriteOf(direction, fallback, FALLBACK);
    }

    private Sprite spriteOf(Direction direction, BakedModel model, BlockState state) {
        List<BakedQuad> quads = model.getQuads(state, direction, random);
        if (!quads.isEmpty()) return quads.getFirst().getSprite();
        // Nothing on that face (culled face of a non-cube model...): its first unculled quad, else its particle
        List<BakedQuad> general = model.getQuads(state, null, random);
        Sprite particle = model.getParticleSprite();
        return !isMissing(particle) || general.isEmpty() ? particle : general.getFirst().getSprite();
    }

    /** The vanilla purple and black checker, from the block atlas. */
    private static Sprite missingSprite() {
        return MinecraftClient.getInstance().getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).apply(MissingSprite.getMissingSpriteId());
    }

    private static boolean isMissing(Sprite sprite) {
        return sprite == null || MissingSprite.getMissingSpriteId().equals(sprite.getContents().getId());
    }
}
