package fr.lordfinn.steveparty.client.entity;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

/**
 * The Frousseux's wax while it fades out of its owner's way: vanilla's translucent entity layers, but writing no depth.
 * The entity shaders cut out on the texture's alpha alone, before the faded colour: drawn in the usual layers, its
 * nearly invisible body still wrote its depth and hid its flame wherever it stood in front of it (seen from below or
 * from the side, the flame showed behind a candle no one could see). Tested against the world's depth all the same.
 */
final class FrousseuxGhostLayers extends RenderLayer {
    private static final Function<Identifier, RenderLayer> CULL = Util.memoize(texture -> of(
            "steveparty_frousseux_ghost_cull", true, texture));
    private static final Function<Identifier, RenderLayer> NO_CULL = Util.memoize(texture -> of(
            "steveparty_frousseux_ghost", false, texture));

    private FrousseuxGhostLayers(String name, VertexFormat format, VertexFormat.DrawMode mode, int size, boolean crumbling,
                                 boolean translucent, Runnable begin, Runnable end) {
        super(name, format, mode, size, crumbling, translucent, begin, end);
    }

    private static RenderLayer of(String name, boolean cull, Identifier texture) {
        return RenderLayer.of(name, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS,
                1536, true, true, MultiPhaseParameters.builder()
                        .program(cull ? ENTITY_TRANSLUCENT_CULL_PROGRAM : ENTITY_TRANSLUCENT_PROGRAM)
                        .texture(new Texture(texture, false, false))
                        .transparency(TRANSLUCENT_TRANSPARENCY)
                        .cull(cull ? ENABLE_CULLING : DISABLE_CULLING)
                        .lightmap(ENABLE_LIGHTMAP)
                        .overlay(ENABLE_OVERLAY_COLOR)
                        .writeMaskState(COLOR_MASK)
                        .build(true));
    }

    private static final Function<Identifier, RenderLayer> DEPTH = Util.memoize(texture -> RenderLayer.of(
            "steveparty_frousseux_flame_depth", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
            VertexFormat.DrawMode.QUADS, 1536, false, false, MultiPhaseParameters.builder()
                    .program(ENTITY_TRANSLUCENT_EMISSIVE_PROGRAM)
                    .texture(new Texture(texture, false, false))
                    .transparency(TRANSLUCENT_TRANSPARENCY)
                    .cull(DISABLE_CULLING)
                    .overlay(ENABLE_OVERLAY_COLOR)
                    .writeMaskState(DEPTH_MASK)
                    .build(false)));

    /**
     * The depth alone of what is drawn with {@code texture}, where it shows (the entity shader discards its nearly
     * transparent texels): drawn after the flame, so that the see-through film around it, drawn later, passes in front of
     * it only where it really is in front.
     */
    static RenderLayer depth(Identifier texture) {
        return DEPTH.apply(texture);
    }

    /** One-sided ({@link RenderLayer#getEntityTranslucentCull}), faded: no depth written. */
    static RenderLayer cull(Identifier texture) {
        return CULL.apply(texture);
    }

    /** Two-sided ({@link RenderLayer#getEntityTranslucent}), faded: no depth written. */
    static RenderLayer noCull(Identifier texture) {
        return NO_CULL.apply(texture);
    }
}
