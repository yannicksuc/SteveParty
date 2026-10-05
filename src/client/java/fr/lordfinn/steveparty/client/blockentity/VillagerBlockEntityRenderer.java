package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerExpression;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerReaction;
import fr.lordfinn.steveparty.client.villager.VillagerBlockAnimator;
import fr.lordfinn.steveparty.client.villager.VillagerPose;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * Draws the villager block alive: its baked model (the block's own textures) in the pose of its reaction or mode
 * ({@link VillagerBlockAnimator}), turned toward whoever it looks at, with its face's expression drawn over the top
 * texture (textures/entity/villager_block_expressions.png, one 16x16 overlay per {@link VillagerExpression}).
 * <p>
 * At rest (no reaction, nobody around) it is exactly the static block. Nothing is allocated per frame: one reused
 * pose (render thread only), the baked model drawn as is.
 */
public class VillagerBlockEntityRenderer implements BlockEntityRenderer<VillagerBlockEntity> {
    public static final Identifier EXPRESSIONS = Steveparty.id("textures/entity/villager_block_expressions.png");
    /** Just above the top face, against z-fighting. */
    private static final float OVERLAY_Y = 1.0015f;
    private static final VillagerPose POSE = new VillagerPose();

    private final BlockRenderManager blocks;

    public VillagerBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
        this.blocks = context.getRenderManager();
    }

    @Override
    public void render(VillagerBlockEntity villager, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                       int light, int overlay) {
        VillagerReaction reaction = villager.getReaction();
        float age = Math.max(0f, villager.getClientAge() - 1 + tickDelta);
        float modeAge = villager.getModeAge() + tickDelta;
        VillagerPose pose = POSE;
        VillagerBlockAnimator.pose(pose, reaction, age, villager.getMode(), modeAge, villager.isBlinking(),
                villager.getMiningStage());

        matrices.push();
        matrices.translate(0.5f + pose.offsetX, pose.offsetY, 0.5f + pose.offsetZ);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(villager.getYaw(tickDelta) * pose.lookWeight + pose.yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(villager.getTilt(tickDelta) * pose.lookWeight + pose.tilt));
        if (pose.roll != 0) {
            // a positive roll tips the top toward -x: pivot on the -x edge
            matrices.translate(-pose.rollPivot, 0, 0);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(pose.roll));
            matrices.translate(pose.rollPivot, 0, 0);
        }
        matrices.scale(pose.scaleX, pose.scaleY, pose.scaleZ);
        matrices.translate(-0.5f, 0, -0.5f);

        BlockState state = pose.disguise ? Blocks.COBBLESTONE.getDefaultState() : villager.getCachedState();
        blocks.getModelRenderer().render(matrices.peek(), vertexConsumers.getBuffer(RenderLayers.getEntityBlockLayer(state, false)),
                state, blocks.getModel(state), 1f, 1f, 1f, light, OverlayTexture.DEFAULT_UV);
        if (!pose.disguise && pose.expression != VillagerExpression.NONE) {
            drawExpression(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(EXPRESSIONS)),
                    pose.expression, light);
        }
        matrices.pop();
    }

    /** The expression's overlay on the top face (u along x, v along z, like the block's top texture). */
    private static void drawExpression(MatrixStack matrices, VertexConsumer consumer, VillagerExpression expression, int light) {
        int cell = expression.ordinal();
        float u0 = (cell % 4) * 0.25f, v0 = (cell / 4) * 0.25f, u1 = u0 + 0.25f, v1 = v0 + 0.25f;
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f m = entry.getPositionMatrix();
        vertex(consumer, entry, m, 0, 0, u0, v0, light);
        vertex(consumer, entry, m, 0, 1, u0, v1, light);
        vertex(consumer, entry, m, 1, 1, u1, v1, light);
        vertex(consumer, entry, m, 1, 0, u1, v0, light);
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, Matrix4f m, float x, float z, float u, float v, int light) {
        consumer.vertex(m, x, OVERLAY_Y, z).color(255, 255, 255, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(entry, 0, 1, 0);
    }

    @Override
    public boolean rendersOutsideBoundingBox(VillagerBlockEntity villager) {
        // hops high, faints on its side (decided when the chunk is built: always)
        return true;
    }
}
