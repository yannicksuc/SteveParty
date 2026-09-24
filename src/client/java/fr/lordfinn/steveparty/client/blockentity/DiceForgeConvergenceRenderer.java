package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import net.minecraft.world.World;

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
                       List<DiceForgeOrbitRenderer.OrbitFace> orbitFaces, float coreYaw) {

        ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();
        // The 6 cube faces cycle through the orbiting faces (a die can have 2 to 12 faces)
        if (orbitFaces.isEmpty())
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

            // Final cube position: a die around the core, turned like the core (rotation about +Y, as GeckoLib does)
            float cos = MathHelper.cos(coreYaw), sin = MathHelper.sin(coreYaw);
            float xFinal = FACE_POSITIONS[i][0] * cos + FACE_POSITIONS[i][2] * sin;
            float yFinal = FACE_POSITIONS[i][1];
            float zFinal = -FACE_POSITIONS[i][0] * sin + FACE_POSITIONS[i][2] * cos;

            // Interpolate positions and rotations
            float x = (float)(xOrbitFrame * (1 - progress) + xFinal * progress);
            float y = (float)(yOrbitFrame * (1 - progress) + yFinal * progress);
            float z = (float)(zOrbitFrame * (1 - progress) + zFinal * progress);

            float yaw = face.yawOrbit * (1 - progress) + FACE_ROTATIONS[i][1] * progress;
            float pitch = face.pitchOrbit * (1 - progress) + FACE_ROTATIONS[i][0] * progress;
            float roll = face.rollOrbit * (1 - progress) + FACE_ROTATIONS[i][2] * progress;

            poseStack.push();
            poseStack.translate(0.5 + x, DiceForgeOrbitRenderer.getOrbitHeight(blockEntity, partialTick) + y, 0.5 + z);
            // Takes on the core rotation as the die forms
            poseStack.multiply(RotationAxis.POSITIVE_Y.rotation(coreYaw * progress));
            poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
            poseStack.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(yaw));
            poseStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
            poseStack.scale( 0.4f + progress * 0.4f, 0.4f + progress * 0.4f, 0.4f + progress * 0.4f);

            itemRenderer.renderItem(face.stack, ModelTransformationMode.FIXED, packedLight, packedOverlay,
                    poseStack, bufferSource, blockEntity.getWorld(), 0);

            poseStack.pop();
        }

    }

    /**
     * Draws the formed die: the 6 faces as a cube centered on ({@code x}, {@code y}, {@code z}) (block space), turned
     * by {@code yaw} (radians, about +Y like the core), at {@code size} (1 = the size the convergence ends at).
     */
    public static void renderDie(List<DiceForgeOrbitRenderer.OrbitFace> faces, MatrixStack poseStack,
                                 VertexConsumerProvider bufferSource, int packedLight, int packedOverlay, World world,
                                 double x, double y, double z, float yaw, float size) {
        if (faces.isEmpty() || size <= 0) return;
        ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();
        for (int i = 0; i < 6; i++) {
            ItemStack stack = faces.get(i % faces.size()).stack;
            if (stack.isEmpty()) continue;
            poseStack.push();
            poseStack.translate(x, y, z);
            poseStack.multiply(RotationAxis.POSITIVE_Y.rotation(yaw));
            poseStack.scale(size, size, size);
            poseStack.translate(FACE_POSITIONS[i][0], FACE_POSITIONS[i][1], FACE_POSITIONS[i][2]);
            poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(FACE_ROTATIONS[i][0]));
            poseStack.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(FACE_ROTATIONS[i][1]));
            poseStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(FACE_ROTATIONS[i][2]));
            poseStack.scale(0.8f, 0.8f, 0.8f);
            itemRenderer.renderItem(stack, ModelTransformationMode.FIXED, packedLight, packedOverlay,
                    poseStack, bufferSource, world, 0);
            poseStack.pop();
        }
    }

    private float mapProgress(float rawProgress) {
        if (rawProgress >= 0.8f) return 1f;
        return rawProgress / 0.8f;
    }
}