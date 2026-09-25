package fr.lordfinn.steveparty.client.entity;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.TriState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vanilla's translucent emissive entity layer (same shader, blending and look), but also writing depth. With a shader
 * pack, glows drawn with the vanilla layer (which writes no depth) were covered by the clouds, composited later over
 * everything not deeper than them; writing depth keeps them in front, like the entity's body. Glow pixels fainter than
 * the shader's cut-off write nothing. Not outlined by the glowing effect. Render thread only.
 */
public final class GlowRenderLayer extends RenderLayer {
    private static final Map<Identifier, RenderLayer> LAYERS = new HashMap<>();

    private GlowRenderLayer(String name, List<RenderPhase> phases) {
        super(name, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS, 1536, true, true,
                () -> phases.forEach(RenderPhase::startDrawing), () -> phases.forEach(RenderPhase::endDrawing));
    }

    public static RenderLayer of(Identifier texture) {
        return LAYERS.computeIfAbsent(texture, id -> new GlowRenderLayer("steveparty_glow",
                List.of(ENTITY_TRANSLUCENT_EMISSIVE_PROGRAM, new RenderPhase.Texture(id, TriState.FALSE, false),
                        TRANSLUCENT_TRANSPARENCY, DISABLE_CULLING, LEQUAL_DEPTH_TEST, ALL_MASK, ENABLE_OVERLAY_COLOR,
                        NO_LAYERING, MAIN_TARGET)));
    }
}
