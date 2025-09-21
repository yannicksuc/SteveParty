package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.RotationAxis;

import java.util.List;

public class DiceForgeConvergenceRenderer {
    private static float CUBE_SIZE = 0.753f;
    private static float[][] FACE_POSITIONS = {
            { CUBE_SIZE / 2f, 0f, 0f}, {-CUBE_SIZE / 2f, 0f, 0f},
            {0f,  CUBE_SIZE / 2f, 0f}, {0f, -CUBE_SIZE / 2f, 0f},
            {0f, 0f,  CUBE_SIZE / 2f}, {0f, 0f, -CUBE_SIZE / 2f}
    };
    // Final cube rotations
    private static final float[][] FACE_ROTATIONS = {
            {0f, 90f, 0f}, {0f, -90f, 0f}, // X±
            {90f, 0f, 0f}, {-90f, 0f, 0f}, // Y±
            {0f, 0f, 0f}, {0f, 180f, 0f}   // Z±
    };

    public void render(DiceForgeBlockEntity blockEntity, float partialTick,
                       MatrixStack poseStack, VertexConsumerProvider bufferSource,
                       int packedLight, int packedOverlay,
                       List<DiceForgeOrbitRenderer.OrbitFace> orbitFaces) {

        ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();
        // Ensure 6 faces (duplicate if needed)
        int faceCount = Math.min(orbitFaces.size(), 6);
        if (faceCount < 6)
            return;
        // Inside render method
        for (int i = 0; i < 6; i++) {
            DiceForgeOrbitRenderer.OrbitFace face = i < orbitFaces.size() ? orbitFaces.get(i) : orbitFaces.get(i % orbitFaces.size());
            if (face.stack.isEmpty()) continue;

            // Convergence progress [0 -> 1] remapped
            float rawProgress = blockEntity.getCraftProgress(partialTick);
            float progress = mapProgress(rawProgress);

            // Recompute orbit position with partialTick (frame-smooth)
            double ticks = blockEntity.getRotationTicks() + partialTick;
            double radius = Math.sqrt(face.xOrbit * face.xOrbit + face.zOrbit * face.zOrbit);
            double baseSpeed = 0.02 * ((i % 2 == 0) ? 1 : -1) * 1.6;
            double angleOffset = Math.atan2(face.zOrbit, face.xOrbit); // initial angle
            double angle = ticks * (baseSpeed / radius) + angleOffset;

            double xOrbitFrame = face.xOrbit;
            double yOrbitFrame = face.yOrbit;
            double zOrbitFrame = face.zOrbit;

            // Final cube position
            float xFinal = FACE_POSITIONS[i][0];
            float yFinal = FACE_POSITIONS[i][1];
            float zFinal = FACE_POSITIONS[i][2];

            // Interpolate positions and rotations
            float x = (float)(xOrbitFrame * (1 - progress) + xFinal * progress);
            float y = (float)(yOrbitFrame * (1 - progress) + yFinal * progress);
            float z = (float)(zOrbitFrame * (1 - progress) + zFinal * progress);

            float yaw = face.yawOrbit * (1 - progress) + FACE_ROTATIONS[i][1] * progress;
            float pitch = face.pitchOrbit * (1 - progress) + FACE_ROTATIONS[i][0] * progress;
            float roll = face.rollOrbit * (1 - progress) + FACE_ROTATIONS[i][2] * progress;

            poseStack.push();
            poseStack.translate(0.5 + x, 2.5 + y, 0.5 + z);
            poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
            poseStack.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(yaw));
            poseStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
            poseStack.scale( 0.4f + progress * 0.4f, 0.4f + progress * 0.4f, 0.4f + progress * 0.4f);

            itemRenderer.renderItem(face.stack, ModelTransformationMode.FIXED, packedLight, packedOverlay,
                    poseStack, bufferSource, blockEntity.getWorld(), 0);

            poseStack.pop();
        }

    }

    private float mapProgress(float rawProgress) {
        if (rawProgress >= 0.8f) return 1f;
        return rawProgress / 0.8f;
    }
}