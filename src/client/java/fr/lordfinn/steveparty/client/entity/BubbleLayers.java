package fr.lordfinn.steveparty.client.entity;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.SpriteAtlasTexture;

/**
 * The layers a Bubbler's bubbles are drawn in, both into the translucent target (the main one but with the Fabulous
 * graphics), depth-tested against the world before the translucent terrain ({@link BubbleFilmDepth}):
 * <ul>
 *     <li>{@link #FILM}: the soap film of the block atlas, translucent, both sides, lit like the world, its quads
 *     sorted back to front, never writing depth: what is drawn after it (the clouds, the rain, the particles behind
 *     it) still shows through it;</li>
 *     <li>{@link #FILM_DEPTH}, with the Fabulous graphics only: the same film writing the translucent target's depth
 *     and no colour, once that depth is back (with the water), so that the composite puts the film in front of what
 *     lies behind it in the other targets (a rider's held item, an item bubble's item, the particles).</li>
 * </ul>
 */
final class BubbleLayers extends RenderLayer {
    static final RenderLayer FILM = film("steveparty_bubble_film", COLOR_MASK);
    static final RenderLayer FILM_DEPTH = film("steveparty_bubble_film_depth", DEPTH_MASK);

    private static RenderLayer film(String name, WriteMaskState mask) {
        return RenderLayer.of(
                name, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS,
                1536, false, true, MultiPhaseParameters.builder()
                        .program(ENTITY_TRANSLUCENT_PROGRAM)
                        .texture(new Texture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, false, false))
                        .transparency(TRANSLUCENT_TRANSPARENCY)
                        .cull(DISABLE_CULLING)
                        .lightmap(ENABLE_LIGHTMAP)
                        .overlay(ENABLE_OVERLAY_COLOR)
                        .target(TRANSLUCENT_TARGET)
                        .writeMaskState(mask)
                        .build(false));
    }

    private BubbleLayers(String name, VertexFormat format, VertexFormat.DrawMode mode, int size, boolean crumbling,
                         boolean translucent, Runnable begin, Runnable end) {
        super(name, format, mode, size, crumbling, translucent, begin, end);
    }
}
