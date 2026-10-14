package fr.lordfinn.steveparty.client.render;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Hangs something over any entity, fitted to its hitbox (a vanilla mob, a modded one, a pawn, a baby, one scaled up):
 * its size from the hitbox's width, the point it hangs from at the hitbox's centre, and vanilla lead ropes from there
 * to where it is held (the lead's two-tone twisted strip, its sag, lit by the world at both ends).
 * <p>
 * Positions are in the entity's space from its feet (as an entity renderer draws), not turned with it. Opaque: drawn
 * with the entity, it writes depth like any model.
 */
public final class HitboxHarness {
    /** The lead's half-thickness and its pieces (vanilla's). */
    private static final float ROPE = 0.025f;
    private static final int PIECES = 24;

    private HitboxHarness() {
    }

    /** Where ropes meet on {@code entity}: the centre of its hitbox, from its feet. */
    public static Vector3f center(Entity entity) {
        return new Vector3f(0, entity.getHeight() / 2, 0);
    }

    /** A size fitted to {@code entity}'s width: {@code perWidth} times it, kept within {@code min}..{@code max}. */
    public static float fit(Entity entity, float perWidth, float min, float max) {
        return MathHelper.clamp(entity.getWidth() * perWidth, min, max);
    }

    /**
     * A lead rope from {@code from} to {@code to} (in {@code matrices}' space), sagging like vanilla's, lit
     * {@code lightFrom} at its start and {@code lightTo} at its end (packed lightmap coordinates).
     */
    public static void rope(MatrixStack matrices, VertexConsumerProvider consumers, Vector3f from, Vector3f to, int lightFrom, int lightTo) {
        float dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        float flat = dx * dx + dz * dz;
        // the strip's side, across the rope (a vertical rope: any side)
        float scale = flat < 1.0E-6f ? 0 : MathHelper.inverseSqrt(flat) * ROPE / 2;
        float sideX = flat < 1.0E-6f ? ROPE / 2 : dz * scale, sideZ = flat < 1.0E-6f ? 0 : dx * scale;
        int blockFrom = LightmapTextureManager.getBlockLightCoordinates(lightFrom), blockTo = LightmapTextureManager.getBlockLightCoordinates(lightTo);
        int skyFrom = LightmapTextureManager.getSkyLightCoordinates(lightFrom), skyTo = LightmapTextureManager.getSkyLightCoordinates(lightTo);
        matrices.push();
        matrices.translate(from.x, from.y, from.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        // one strip there and back, twisted, like vanilla's lead (a strip layer is never shared: one rope a draw)
        VertexConsumer strip = consumers.getBuffer(RenderLayer.getLeash());
        for (int i = 0; i <= PIECES; i++) piece(strip, matrix, dx, dy, dz, blockFrom, blockTo, skyFrom, skyTo, ROPE, ROPE, sideX, sideZ, i, false);
        for (int i = PIECES; i >= 0; i--) piece(strip, matrix, dx, dy, dz, blockFrom, blockTo, skyFrom, skyTo, ROPE, 0, sideX, sideZ, i, true);
        matrices.pop();
    }

    /** One piece of the strip (vanilla's renderLeashPiece): its shade alternating, its height sagging. */
    private static void piece(VertexConsumer strip, Matrix4f matrix, float dx, float dy, float dz, int blockFrom, int blockTo, int skyFrom,
                              int skyTo, float width, float lift, float sideX, float sideZ, int index, boolean twisted) {
        float t = index / (float) PIECES;
        int light = LightmapTextureManager.pack((int) MathHelper.lerp(t, blockFrom, blockTo), (int) MathHelper.lerp(t, skyFrom, skyTo));
        float shade = index % 2 == (twisted ? 1 : 0) ? 0.7f : 1.0f;
        float r = 0.5f * shade, g = 0.4f * shade, b = 0.3f * shade;
        float x = dx * t, z = dz * t;
        float y = dy > 0 ? dy * t * t : dy - dy * (1 - t) * (1 - t);
        strip.vertex(matrix, x - sideX, y + lift, z + sideZ).color(r, g, b, 1).light(light);
        strip.vertex(matrix, x + sideX, y + width - lift, z - sideZ).color(r, g, b, 1).light(light);
    }
}
