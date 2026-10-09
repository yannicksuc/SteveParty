package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.client.gui.paint.PaintedTextures;
import fr.lordfinn.steveparty.client.gui.paint.PixelArt;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.Map;

/**
 * The pixel icons of the board space panels ({@link TileInfo.Glyph}), 8 x 8, painted like the party HUD's small icons
 * (the pixel-art kit: rows of characters, a dark outline, flat colours): ahead and back, a roll's condition, a padlock
 * open or closed, a trap's jaws, a die, a turn, every item, a shop. Painted once, drawn in the world and on the HUD.
 */
final class TileGlyphs {
    private static final PaintedTextures TEXTURES = new PaintedTextures("tile_info/glyph_", 32);
    private static final int D = 0xFF3A3A3A, W = 0xFFFFFFFF, G = 0xFF2FAE64, R = 0xFFE8413C, Y = 0xFFFFC900,
            B = 0xFF3A9BFF, S = 0xFFA7B2BC, O = 0xFFF9901D;
    private static final Map<TileInfo.Glyph, String[]> ROWS = new EnumMap<>(TileInfo.Glyph.class);
    private static final Map<TileInfo.Glyph, Map<Character, Integer>> COLOURS = new EnumMap<>(TileInfo.Glyph.class);
    private static final int LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    static {
        glyph(TileInfo.Glyph.FORWARD, Map.of('#', D, 'c', G),
                "   ##   ", "   #c#  ", "####cc# ", "#cccccc#", "####cc# ", "   #c#  ", "   ##   ", "        ");
        glyph(TileInfo.Glyph.BACK, Map.of('#', D, 'c', R),
                "   ##   ", "  #c#   ", " #cc####", "#cccccc#", " #cc####", "  #c#   ", "   ##   ", "        ");
        glyph(TileInfo.Glyph.CONDITION, Map.of('#', D, 'w', W, 'y', Y),
                "#      #", "########", "#yywwyy#", "#wwyyww#", "########", "#      #", "#      #", "##    ##");
        glyph(TileInfo.Glyph.OPEN, Map.of('#', D, 'c', G, 's', S),
                "  ####  ", " #s  #  ", " #s     ", "########", "#cccccc#", "#cc##cc#", "#cccccc#", "########");
        glyph(TileInfo.Glyph.CLOSED, Map.of('#', D, 'c', R, 's', S),
                "  ####  ", " #s  s# ", " #s  s# ", "########", "#cccccc#", "#cc##cc#", "#cccccc#", "########");
        glyph(TileInfo.Glyph.TRAP, Map.of('#', D, 's', S, 'c', R),
                "        ", "s s s s ", "ssssssss", "#cccccc#", "#cccccc#", "ssssssss", "s s s s ", "        ");
        glyph(TileInfo.Glyph.DICE, Map.of('#', D, 'w', W),
                "########", "#wwwwww#", "#w##www#", "#w##www#", "#www##w#", "#www##w#", "#wwwwww#", "########");
        glyph(TileInfo.Glyph.CYCLE, Map.of('#', D, 'c', B),
                "  ####  ", " #cccc# ", "#c#  #c#", "#c#  ###", "###  #c#", "#c#  #c#", " #cccc# ", "  ####  ");
        glyph(TileInfo.Glyph.ALL, Map.of('#', D, 'y', O, 'w', Y),
                "########", "#yyyyyy#", "#yyyyyy#", "###ww###", "#yy##yy#", "#yyyyyy#", "#yyyyyy#", "########");
        glyph(TileInfo.Glyph.SHOP, Map.of('#', D, 'g', G, 'w', W),
                "  ####  ", " #    # ", "########", "#gggggg#", "#gwwwwg#", "#gggggg#", "#gggggg#", "########");
    }

    private TileGlyphs() {
    }

    private static void glyph(TileInfo.Glyph glyph, Map<Character, Integer> colours, String... rows) {
        ROWS.put(glyph, rows);
        COLOURS.put(glyph, colours);
    }

    private static PaintedTextures.Tex tex(TileInfo.Glyph glyph) {
        return TEXTURES.get(glyph.name(), () -> PixelArt.pattern(ROWS.get(glyph), COLOURS.get(glyph)));
    }

    /** Draws {@code glyph} over (x, y)-(x + size, y + size) in label space (text pixels, y down), on a plate. */
    static void draw(MatrixStack matrices, VertexConsumerProvider consumers, TileInfo.Glyph glyph, float x, float y, float size) {
        if (glyph == TileInfo.Glyph.NONE) return;
        PaintedTextures.Tex tex = tex(glyph);
        RenderLayer layer = RenderLayer.getText(tex.id());
        VertexConsumer consumer = consumers.getBuffer(layer);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float z = -0.1f;
        consumer.vertex(matrix, x, y, z).color(0xFFFFFFFF).texture(0, 0).light(LIGHT);
        consumer.vertex(matrix, x, y + size, z).color(0xFFFFFFFF).texture(0, 1).light(LIGHT);
        consumer.vertex(matrix, x + size, y + size, z).color(0xFFFFFFFF).texture(1, 1).light(LIGHT);
        consumer.vertex(matrix, x + size, y, z).color(0xFFFFFFFF).texture(1, 0).light(LIGHT);
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(layer);
    }

    /** Draws {@code glyph} at (x, y) on the HUD, one texture pixel per GUI pixel. */
    static void drawHud(DrawContext context, TileInfo.Glyph glyph, int x, int y) {
        if (glyph != TileInfo.Glyph.NONE) tex(glyph).draw(context, x, y);
    }
}
