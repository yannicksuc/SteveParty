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
 * Plays when a die is forged: a laser of sparkles shoots down from the core to the forge, and the die, formed around
 * the core, falls down along it like a shooting star, shrinking until it vanishes into the forge.
 */
public class DiceForgeForgedRenderer {
    /** Ticks for the die to fall from the core to the forge. */
    public static final int FALL_TICKS = 16;
    /** Ticks during which the laser shoots. */
    private static final int BEAM_TICKS = 10;
    /** Laser speed (blocks per tick) and sparkles per tick: one every {@code BEAM_SPEED / BEAM_DENSITY} blocks. */
    private static final double BEAM_SPEED = 1.2;
    private static final int BEAM_DENSITY = 4;
    /** Height of the forge top (blocks above the block), where the die vanishes. */
    private static final double FORGE_TOP = 1.0;

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
        // Falls faster and faster, like a shooting star, shrinking to nothing
        float t = elapsed / FALL_TICKS;
        double y = core + (FORGE_TOP - core) * t * t;
        float size = 1f - t;

        if (spawnParticles) {
            Random random = world.random;
            if (tick < BEAM_TICKS) {
                // Spread along the first tick of travel: a continuous line rather than dots
                for (int i = 0; i < BEAM_DENSITY; i++) {
                    world.addParticle(ModParticles.FORGE_BEAM,
                            pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.06,
                            pos.getY() + core - i * BEAM_SPEED / BEAM_DENSITY,
                            pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.06,
                            0, -BEAM_SPEED, 0);
                }
            }
            // The shooting star's tail
            world.addParticle(ParticleTypes.END_ROD,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3 * size,
                    pos.getY() + y,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3 * size,
                    0, 0.01, 0);
        }

        DiceForgeConvergenceRenderer.renderDie(faces, poseStack, bufferSource, packedLight, packedOverlay, world,
                0.5, y, 0.5, coreYaw, size);
    }
}
