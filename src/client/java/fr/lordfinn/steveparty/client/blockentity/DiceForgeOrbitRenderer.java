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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import java.util.ArrayList;
import java.util.List;

/**
 * The dice faces orbiting the core. Near the ground they turn flat around it; the higher the core (star fragments),
 * the more their orbits tilt, each around its own axis, until at the top they form a sphere around the core.
 */
public class DiceForgeOrbitRenderer {
    /** Radius of the sphere the faces form once the core is at its highest (blocks). */
    private static final double SPHERE_RADIUS = 2.5;
    /** Tilt of the most tilted orbit at the top (degrees); the others spread evenly between -/+ this. */
    private static final double MAX_TILT = 77;
    /** Nodes of the tilted orbits spread by the golden angle, so they cover the sphere evenly. */
    private static final double GOLDEN_ANGLE = Math.toRadians(137.5);

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
                                  int packedLight, int packedOverlay, float scale, boolean spawnParticles) {

        List<OrbitFace> orbitFaces = new ArrayList<>();
        DefaultedList<ItemStack> inventory = blockEntity.getInventory();

        // Only the dice faces orbit (not the fragments nor the output die)
        int faceSlots = Math.min(inventory.size(), DiceForgeBlockEntity.FACE_SLOTS);
        float sphere = getSphereFactor(blockEntity, partialTick);
        for (int i = 0; i < faceSlots; i++) {
            ItemStack itemStack = inventory.get(i);
            if (itemStack.isEmpty()) continue;

            double baseSpeed = 0.02;
            float flatRadius = 0.8f + i * 0.5f;
            double speed = baseSpeed * (1.0 / flatRadius) * ((i % 2 == 0) ? 1 : -1) * 1.6;
            double angleOffset = Math.toRadians(i * 67);
            boolean spawnTrail = (i % 3 == 0);

            double ticks = blockEntity.getRotationTicks() + partialTick;
            double angle = (ticks * speed) + angleOffset;
            double[] p = orbitPoint(i, faceSlots, angle, flatRadius, sphere);
            double x = p[0], y = p[1], z = p[2];

            // Around the core, which floats higher with more star fragments
            double orbitY = getOrbitHeight(blockEntity, partialTick) + y;

            // Rotation facing inward
            float yaw = (float) Math.toDegrees(Math.atan2(-z, -x)) - 90f;

            // Save orbit info for convergence
            orbitFaces.add(new OrbitFace(itemStack, x, y, z, yaw, 0f, 0f));

            // Render orbiting item
            ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();
            poseStack.push();
            poseStack.translate(0.5 + x, orbitY, 0.5 + z);
            poseStack.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(yaw));
            poseStack.scale(scale, scale, scale);
            itemRenderer.renderItem(itemStack, ModelTransformationMode.FIXED,
                    packedLight, packedOverlay, poseStack, bufferSource, blockEntity.getWorld(), 0);
            poseStack.pop();

            if (spawnTrail && spawnParticles) {
                // Called once per tick: two samples along the orbit (tick start and half tick) give the
                // same trail density as the former per-frame spawning at ~60 FPS (0.4 x 60 = 24/s).
                double baseTicks = blockEntity.getRotationTicks();
                double baseY = blockEntity.getPos().getY() + getOrbitHeight(blockEntity, partialTick);
                for (double sub = 0; sub < 1; sub += 0.5) {
                    double[] q = orbitPoint(i, faceSlots, ((baseTicks + sub) * speed) + angleOffset, flatRadius, sphere);
                    spawnTrailParticle(blockEntity,
                            blockEntity.getPos().getX() + 0.5 + q[0],
                            baseY + q[1],
                            blockEntity.getPos().getZ() + 0.5 + q[2],
                            0.6f);
                }
            }
        }

        return orbitFaces;
    }

    /** @return 0 with the core at the forge (flat orbits) to 1 at its highest (orbits forming a sphere). */
    public static float getSphereFactor(DiceForgeBlockEntity blockEntity, float partialTick) {
        float lift = blockEntity.getCoreAltitude(partialTick) - DiceForgeBlockEntity.CORE_BASE_LIFT;
        return MathHelper.clamp(lift / DiceForgeBlockEntity.MAX_CORE_ALTITUDE, 0f, 1f);
    }

    /**
     * @return the position of face {@code i} (of {@code count}) on its orbit, relative to the core: a flat circle of
     * radius {@code flatRadius} tilted by {@code sphere} x its own tilt around its own axis, the radius moving to the
     * common sphere radius
     */
    private static double[] orbitPoint(int i, int count, double angle, double flatRadius, float sphere) {
        double radius = MathHelper.lerp(sphere, flatRadius, SPHERE_RADIUS);
        double x = Math.cos(angle) * radius, z = Math.sin(angle) * radius;
        double spread = count > 1 ? 2.0 * i / (count - 1) - 1 : 0;
        double tilt = Math.toRadians(MAX_TILT * spread) * sphere;
        // Tilt the orbit around a horizontal axis through the core (Rodrigues' rotation, the axis being horizontal)
        double node = i * GOLDEN_ANGLE;
        double ax = Math.cos(node), az = Math.sin(node);
        double cos = Math.cos(tilt), sin = Math.sin(tilt);
        double dot = ax * x + az * z;
        // v cos + (k x v) sin + k (k.v)(1 - cos), with k = (ax, 0, az) and v = (x, 0, z)
        double rx = x * cos + ax * dot * (1 - cos);
        double ry = (az * x - ax * z) * sin;
        double rz = z * cos + az * dot * (1 - cos);
        return new double[]{rx, ry, rz};
    }

    /** Height of the orbit above the block (blocks): level with the core, which follows the fragments. */
    public static double getOrbitHeight(DiceForgeBlockEntity blockEntity, float partialTick) {
        return DiceForgeCoreLayer.getCoreHeight(blockEntity, partialTick);
    }

    private void spawnTrailParticle(DiceForgeBlockEntity blockEntity, double x, double y, double z, float chance) {
        if (blockEntity.getWorld() == null || !blockEntity.getWorld().isClient) return;
        if (blockEntity.getWorld().random.nextFloat() < chance) {
            blockEntity.getWorld().addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0, 0);
        }
    }
}
