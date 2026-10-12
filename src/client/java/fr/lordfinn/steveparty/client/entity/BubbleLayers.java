package fr.lordfinn.steveparty.client.entity;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.SpriteAtlasTexture;

/**
 * The layer a Bubbler's bubbles are drawn in: the soap film of the block atlas, translucent, both sides, lit like the
 * world, its quads sorted back to front, tested against the world's depth but never writing it: whatever is drawn
 * after it (its riders' held items, another bubble) still shows through it.
 */
final class BubbleLayers extends RenderLayer {
    static final RenderLayer FILM = RenderLayer.of(
            "steveparty_bubble_film", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS,
            1536, false, true, MultiPhaseParameters.builder()
                    .program(ENTITY_TRANSLUCENT_PROGRAM)
                    .texture(new Texture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, false, false))
                    .transparency(TRANSLUCENT_TRANSPARENCY)
                    .cull(DISABLE_CULLING)
                    .lightmap(ENABLE_LIGHTMAP)
                    .overlay(ENABLE_OVERLAY_COLOR)
                    .writeMaskState(COLOR_MASK)
                    .build(false));

    private BubbleLayers(String name, VertexFormat format, VertexFormat.DrawMode mode, int size, boolean crumbling,
                         boolean translucent, Runnable begin, Runnable end) {
        super(name, format, mode, size, crumbling, translucent, begin, end);
    }
}
