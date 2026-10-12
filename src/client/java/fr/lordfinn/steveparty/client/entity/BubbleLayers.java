package fr.lordfinn.steveparty.client.entity;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.SpriteAtlasTexture;

/**
 * The layers a Bubbler's bubbles are drawn in: the soap film of the block atlas, translucent, both sides, lit like the
 * world, its quads sorted back to front, drawn into the translucent target (the main one but with the Fabulous
 * graphics), tested against the depth of everything drawn before it (its riders, the water).
 * <ul>
 *     <li>{@link #FILM}: never writing depth, so that what is drawn after it in the main target (the clouds, the rain,
 *     the particles behind it) still shows through it.</li>
 *     <li>{@link #FILM_DEPTH}, with the Fabulous graphics: writing the translucent target's depth, so that the
 *     composite puts its film in front of what lies in the other targets inside it (the items, drawn in the item
 *     target: a rider's held item, an item bubble's item), as it does with the water.</li>
 * </ul>
 */
final class BubbleLayers extends RenderLayer {
    static final RenderLayer FILM = film("steveparty_bubble_film", COLOR_MASK);
    static final RenderLayer FILM_DEPTH = film("steveparty_bubble_film_depth", ALL_MASK);

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

    /** The film's layer for the current graphics ({@link #FILM_DEPTH} with Fabulous). */
    static RenderLayer film() {
        return MinecraftClient.isFabulousGraphicsOrBetter() ? FILM_DEPTH : FILM;
    }

    private BubbleLayers(String name, VertexFormat format, VertexFormat.DrawMode mode, int size, boolean crumbling,
                         boolean translucent, Runnable begin, Runnable end) {
        super(name, format, mode, size, crumbling, translucent, begin, end);
    }
}
