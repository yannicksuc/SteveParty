package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.RotationAxis;
import java.util.ArrayList;
import java.util.List;

public class DiceForgeOrbitRenderer {

    /**
     * Holds orbit information for each face.
     */
    public static class OrbitFace {
        public final ItemStack stack;
        public final double xOrbit, yOrbit, zOrbit;
        public final float yawOrbit, pitchOrbit, rollOrbit;

        public OrbitFace(ItemStack stack, double xOrbit, double yOrbit, double zOrbit,
                         float yawOrbit, float pitchOrbit, float rollOrbit) {
            this.stack = stack;
            this.xOrbit = xOrbit;
            this.yOrbit = yOrbit;
            this.zOrbit = zOrbit;
            this.yawOrbit = yawOrbit;
            this.pitchOrbit = pitchOrbit;
            this.rollOrbit = rollOrbit;
        }
    }

    /**
     * Renders all items orbiting around the dice forge block and returns orbit info for convergence.
     */
    public List<OrbitFace> render(DiceForgeBlockEntity blockEntity, float partialTick,
                                  MatrixStack poseStack, VertexConsumerProvider bufferSource,
                                  int packedLight, int packedOverlay, float scale) {

        List<OrbitFace> orbitFaces = new ArrayList<>();
        DefaultedList<ItemStack> inventory = blockEntity.getInventory();

        for (int i = 0; i < inventory.size(); i++) {
            ItemStack itemStack = inventory.get(i);
            if (itemStack.isEmpty()) continue;

            double baseSpeed = 0.02;
            float radius = 0.8f + i * 0.5f;
            double speed = baseSpeed * (1.0 / radius) * ((i % 2 == 0) ? 1 : -1) * 1.6;
            double angleOffset = Math.toRadians(i * 67);
            boolean spawnTrail = (i % 3 == 0);

            double ticks = blockEntity.getRotationTicks() + partialTick;
            double angle = (ticks * speed) + angleOffset;

            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            double y = 0;

            // World-space position
            double worldX = blockEntity.getPos().getX() + 0.5 + x;
            double worldY = blockEntity.getPos().getY() + 2.5 + y;
            double worldZ = blockEntity.getPos().getZ() + 0.5 + z;

            // Rotation facing inward
            float yaw = (float) Math.toDegrees(Math.atan2(-z, -x)) - 90f;

            // Save orbit info for convergence
            orbitFaces.add(new OrbitFace(itemStack, x, y, z, yaw, 0f, 0f));

            // Render orbiting item
            ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();
            poseStack.push();
            poseStack.translate(0.5 + x, 2.5, 0.5 + z);
            poseStack.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(yaw));
            poseStack.scale(scale, scale, scale);
            itemRenderer.renderItem(itemStack, ModelTransformationMode.FIXED,
                    packedLight, packedOverlay, poseStack, bufferSource, blockEntity.getWorld(), 0);
            poseStack.pop();

            if (spawnTrail) spawnTrailParticle(blockEntity, worldX, worldY, worldZ);
        }

        return orbitFaces;
    }

    private void spawnTrailParticle(DiceForgeBlockEntity blockEntity, double x, double y, double z) {
        if (blockEntity.getWorld() == null || !blockEntity.getWorld().isClient) return;
        if (blockEntity.getWorld().random.nextFloat() < 0.4f) {
            blockEntity.getWorld().addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0, 0);
        }
    }
}
