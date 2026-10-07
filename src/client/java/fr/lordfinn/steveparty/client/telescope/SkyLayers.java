package fr.lordfinn.steveparty.client.telescope;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

/**
 * The depth alone of the stars drawn in the sky (after their colour): what is drawn later and farther, the clouds,
 * stays behind them. Only their visible part writes it (the entity shader discards the nearly transparent texels).
 */
final class SkyLayers extends RenderLayer {
    private static final Function<Identifier, RenderLayer> DEPTH = Util.memoize(texture -> RenderLayer.of(
            "steveparty_sky_depth", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS,
            1536, false, false, MultiPhaseParameters.builder()
                    .program(ENTITY_TRANSLUCENT_EMISSIVE_PROGRAM)
                    .texture(new Texture(texture, false, false))
                    .transparency(TRANSLUCENT_TRANSPARENCY)
                    .cull(DISABLE_CULLING)
                    .overlay(ENABLE_OVERLAY_COLOR)
                    .writeMaskState(DEPTH_MASK)
                    .build(false)));

    private SkyLayers(String name, VertexFormat format, VertexFormat.DrawMode mode, int size, boolean crumbling,
                      boolean translucent, Runnable begin, Runnable end) {
        super(name, format, mode, size, crumbling, translucent, begin, end);
    }

    static RenderLayer depth(Identifier texture) {
        return DEPTH.apply(texture);
    }
}
