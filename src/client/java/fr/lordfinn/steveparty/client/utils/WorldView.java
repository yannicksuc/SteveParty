package fr.lordfinn.steveparty.client.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import org.joml.Matrix4fStack;

/**
 * Drawing in the world with the world's own view: the camera's turn and its view bobbing (in the projection, or in the
 * view with a shader pack) exactly as the terrain was drawn, like the vanilla block outline.
 * <p>
 * Late in the frame (LAST) the shared matrices are whatever the hooks before left there, other mods' included: in a
 * playtest, the brush's paint followed the camera's bobbing instead of sticking to the ground (sinking into it when
 * sneaking). Whatever is drawn inside must be drawn there and then: flush with {@code draw()}, never
 * {@code draw(layer)} alone (a shader pack's batched buffers ignore it, and what is left is drawn later, elsewhere).
 */
public final class WorldView {
    private WorldView() {
    }

    /** Runs {@code drawing} with the world's view and projection of this frame, then puts back the previous ones. */
    public static void draw(WorldRenderContext context, Runnable drawing) {
        Matrix4fStack view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        view.set(context.positionMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(context.projectionMatrix(), RenderSystem.getVertexSorting());
        try {
            drawing.run();
        } finally {
            RenderSystem.restoreProjectionMatrix();
            view.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }
}
