package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.entities.custom.CustomizableMerchant;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

import java.util.List;

public class CustomizableMerchantRenderLayer extends GeoRenderLayer<CustomizableMerchant> {
    public static final String CUBE_BONE_ID = "cube";
    Identifier textureId;
    Random random = Random.create();

    public CustomizableMerchantRenderLayer(CustomizableMerchantRenderer customizableMerchantRenderer) {
        super(customizableMerchantRenderer);
        textureId = MinecraftClient.getInstance().getBlockRenderManager().getModel(Blocks.GRASS_BLOCK.getDefaultState()).getParticleSprite().getAtlasId();
    }


    @Override
    public void renderForBone(MatrixStack poseStack, CustomizableMerchant animatable, GeoBone bone, RenderLayer renderType, VertexConsumerProvider bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay, int renderColor) {
        RenderLayer type = getRenderer().getRenderType(animatable, textureId, bufferSource, partialTick);
        if (type == null || !bone.getName().equals(CUBE_BONE_ID)) return;
        renderRecursively(poseStack, bone, bufferSource, packedLight, renderer.getRenderColor(animatable, partialTick, packedLight).getColor(), animatable.getBlockState());
    }

    private void renderRecursively(MatrixStack poseStack, GeoBone bone, VertexConsumerProvider buffer, int packedLight, int renderColor, BlockState blockState) {
        poseStack.push();
        RenderUtil.prepMatrixForBone(poseStack, bone);
        this.renderCubesOfBone(poseStack, bone, buffer, packedLight, renderColor, blockState);
        poseStack.pop();
    }

    private void renderCubesOfBone(MatrixStack poseStack, GeoBone bone, VertexConsumerProvider buffer, int packedLight, int renderColor, BlockState blockState) {
        if (!bone.isHidden()) {
            for (GeoCube cube : bone.getCubes()) {
                poseStack.push();
                this.renderCube(poseStack, cube, buffer, packedLight, renderColor, blockState);
                poseStack.pop();
            }

        }
    }

    private void renderCube(MatrixStack poseStack, GeoCube cube, VertexConsumerProvider buffer, int packedLight, int renderColor, BlockState blockState) {
        RenderUtil.translateToPivotPoint(poseStack, cube);
        RenderUtil.rotateMatrixAroundCube(poseStack, cube);
        RenderUtil.translateAwayFromPivotPoint(poseStack, cube);
        Matrix3f normalisedPoseState = poseStack.peek().getNormalMatrix();
        Matrix4f poseState = new Matrix4f(poseStack.peek().getPositionMatrix());
        GeoQuad[] var9 = cube.quads();

        for (GeoQuad quad : var9) {
            if (quad != null) {
                Vector3f normal = normalisedPoseState.transform(new Vector3f(quad.normal()));
                RenderUtil.fixInvertedFlatCube(cube, normal);
                this.createVerticesOfQuad(quad, poseState, normal, buffer, packedLight, renderColor, blockState);
            }
        }

    }

    private void createVerticesOfQuad(GeoQuad quad, Matrix4f poseState, Vector3f normal, VertexConsumerProvider bufferSource, int packedLight, int renderColor, BlockState blockState) {
        GeoVertex[] var8 = quad.vertices();
        Direction direction = quad.direction();

        Sprite quadSprite = getQuadSpriteForDirection(direction, MinecraftClient.getInstance().getBlockRenderManager().getModel(blockState), blockState);
        VertexConsumer vertexConsumer = quadSprite.getTextureSpecificVertexConsumer(bufferSource.getBuffer(RenderLayer.getEntityCutout(quadSprite.getAtlasId())));

        for (GeoVertex vertex : var8) {
            Vector3f position = vertex.position();
            Vector4f vector4f = poseState.transform(new Vector4f(position.x(), position.y(), position.z(), 1.0F));
            vertexConsumer.vertex(vector4f.x(), vector4f.y(), vector4f.z(), renderColor, vertex.texU(), vertex.texV(), OverlayTexture.DEFAULT_UV, packedLight, normal.x(), normal.y(), normal.z());
        }
    }

    // Helper method to get the correct sprite for each face direction
    private Sprite getQuadSpriteForDirection(Direction direction, BakedModel bakedModel, BlockState blockState) {
        // Get quads for the specified direction (face)
        List<BakedQuad> quads = bakedModel.getQuads(blockState, direction, random);

        // If there are no quads for this face, return a default texture
        if (quads.isEmpty()) {
            return MinecraftClient.getInstance().getBlockRenderManager()
                    .getModel(blockState).getParticleSprite();
        }

        // Get the first quad's sprite (assuming all quads for a face share the same sprite)
        BakedQuad quad = quads.getFirst();
        return quad.getSprite();
    }

}
