package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import fr.lordfinn.steveparty.particles.ModParticles;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.List;

/**
 * Plays when a die is forged: the die, formed around the core, falls into the forge like a shooting star, leaving a
 * streak of sparkles behind it, and shrinks until it vanishes into the forge.
 */
public class DiceForgeForgedRenderer {
    /** Ticks for the die to fall from the core to the forge. */
    public static final int FALL_TICKS = 16;
    /** Sparkles of the trail per block the die falls (plus a few around the die every tick). */
    private static final double TRAIL_DENSITY = 7;
    private static final int TRAIL_HEAD = 3;
    /** Height of the forge top (blocks above the block), where the die vanishes. */
    private static final double FORGE_TOP = 1.0;
    /** How late the die shrinks: it keeps its size most of the way, then melts away at the end (higher = later). */
    private static final float SHRINK_SHARPNESS = 6f;

    public void render(DiceForgeBlockEntity blockEntity, float partialTick, MatrixStack poseStack,
                       VertexConsumerProvider bufferSource, int packedLight, int packedOverlay,
                       List<DiceForgeOrbitRenderer.OrbitFace> faces, float coreYaw, boolean spawnParticles) {
        World world = blockEntity.getWorld();
        if (world == null || blockEntity.getForgedTime() < 0 || faces.isEmpty()) return;
        long tick = world.getTime() - blockEntity.getForgedTime();
        float elapsed = tick + partialTick;
        if (elapsed < 0 || elapsed > FALL_TICKS) return;

        BlockPos pos = blockEntity.getPos();
        double core = DiceForgeOrbitRenderer.getOrbitHeight(blockEntity, partialTick);
        // Falls faster and faster, like a shooting star, then shrinks to nothing at the very end
        float t = elapsed / FALL_TICKS;
        double y = fallHeight(core, t);
        float size = (1f - (float) Math.exp(SHRINK_SHARPNESS * (t - 1f))) / (1f - (float) Math.exp(-SHRINK_SHARPNESS));

        if (spawnParticles) {
            // The trail: sparkles all along the way the die went since the last tick, so that it is one continuous
            // streak starting from the die; they stay where they are, wider near the die, and fade out
            Random random = world.random;
            double to = fallHeight(core, (float) tick / FALL_TICKS);
            double from = tick == 0 ? to : fallHeight(core, (tick - 1f) / FALL_TICKS);
            int count = TRAIL_HEAD + (int) Math.round((from - to) * TRAIL_DENSITY);
            for (int i = 0; i < count; i++) {
                double along = i < TRAIL_HEAD ? 0 : random.nextDouble(); // 0 at the die, 1 a tick behind it
                double spread = (0.06 + 0.3 * size) * (1 - 0.6 * along);
                world.addParticle(ModParticles.FORGE_BEAM,
                        pos.getX() + 0.5 + (random.nextDouble() - 0.5) * spread,
                        pos.getY() + to + (from - to) * along,
                        pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * spread,
                        (random.nextDouble() - 0.5) * 0.02, 0.004, (random.nextDouble() - 0.5) * 0.02);
            }
            // A few brighter sparks thrown off the die itself
            if (random.nextFloat() < 0.6f) {
                world.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + to, pos.getZ() + 0.5,
                        (random.nextDouble() - 0.5) * 0.08, 0.02, (random.nextDouble() - 0.5) * 0.08);
            }
        }

        DiceForgeConvergenceRenderer.renderDie(faces, poseStack, bufferSource, packedLight, packedOverlay, world,
                0.5, y, 0.5, coreYaw, size);
    }

    /** @return height of the falling die above the block at {@code t} (0 at the core, 1 at the forge): faster and faster. */
    private static double fallHeight(double core, float t) {
        return core + (FORGE_TOP - core) * t * t;
    }
}
