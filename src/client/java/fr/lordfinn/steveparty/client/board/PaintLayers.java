package fr.lordfinn.steveparty.client.board;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

/**
 * The layer the brush's paint is drawn in: opaque with cut-out edges (overlapping strokes keep one colour, never darker
 * where they cross), lit like the world, tested against the world's depth but never writing it, so that its own pieces
 * lying in the same plane are simply drawn one over the other, in order, without flickering. The cut-out test is on
 * the texture alone: drying, the paint switches to textures with fewer texels (see BrushTrail) instead of fading.
 */
final class PaintLayers extends RenderLayer {
    private static final Function<Identifier, RenderLayer> PAINT = Util.memoize(texture -> RenderLayer.of(
            "steveparty_brush_paint", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS,
            1536, false, false, MultiPhaseParameters.builder()
                    .program(ENTITY_CUTOUT_NONULL_PROGRAM)
                    .texture(new Texture(texture, false, false))
                    .transparency(NO_TRANSPARENCY)
                    .cull(DISABLE_CULLING)
                    .lightmap(ENABLE_LIGHTMAP)
                    .overlay(ENABLE_OVERLAY_COLOR)
                    .writeMaskState(COLOR_MASK)
                    .build(false)));

    /** The same paint, its depth alone (drawn after its colour). */
    private static final Function<Identifier, RenderLayer> DEPTH = Util.memoize(texture -> RenderLayer.of(
            "steveparty_brush_paint_depth", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS,
            1536, false, false, MultiPhaseParameters.builder()
                    .program(ENTITY_CUTOUT_NONULL_PROGRAM)
                    .texture(new Texture(texture, false, false))
                    .transparency(NO_TRANSPARENCY)
                    .cull(DISABLE_CULLING)
                    .lightmap(ENABLE_LIGHTMAP)
                    .overlay(ENABLE_OVERLAY_COLOR)
                    .writeMaskState(DEPTH_MASK)
                    .build(false)));

    private PaintLayers(String name, VertexFormat format, VertexFormat.DrawMode mode, int size, boolean crumbling,
                        boolean translucent, Runnable begin, Runnable end) {
        super(name, format, mode, size, crumbling, translucent, begin, end);
    }

    static RenderLayer paint(Identifier texture) {
        return PAINT.apply(texture);
    }

    static RenderLayer depth(Identifier texture) {
        return DEPTH.apply(texture);
    }
}
