package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.client.gui.HudDepth;
import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.client.gui.ToolHud;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.utils.Argb;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The panel of a board space ({@link TileInfo}), on a plate cut like the mod's screens (as the board view's), facing
 * the camera: the role's name centred and bigger in its colour, a thin rule, then one line per info (an icon, the
 * value). What a building tool adds comes last, under a second rule. Which lines are drawn is the {@link View}'s;
 * everything is measured once when the info arrives ({@link Layout}), a frame only draws.
 */
public final class TilePanel {
    /** Text pixels: the padding all around, a line, an icon, the title's size, the space around a rule. */
    static final int PAD = 7, ROW = 12, ICON = 9, ICON_GAP = 3, RULE_GAP = 4;
    static final float TITLE_SCALE = 1.5f;
    private static final int TITLE_HEIGHT = Math.round(8 * TITLE_SCALE);
    /** The rules: the plate's dark text, faint. */
    private static final int RULE = 0x553F3F3F;
    private static final int LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    private TilePanel() {
    }

    /** What a panel shows. */
    enum View {
        /** Its title and the first line it shows in game (the Explorer's Helmet's spaces around). */
        COMPACT(false, false, true),
        /** In game. */
        PLAY(false, false, false),
        /** In game and what the helmet adds. */
        DETAIL(true, false, false),
        /** In game and what a building tool adds. */
        BUILD(false, true, false),
        /** All of it (the helmet worn, a building tool in hand). */
        ALL(true, true, false);

        final boolean detail, build, compact;

        View(boolean detail, boolean build, boolean compact) {
            this.detail = detail;
            this.build = build;
            this.compact = compact;
        }

        static View of(boolean detail, boolean build) {
            return detail ? build ? ALL : DETAIL : build ? BUILD : PLAY;
        }

        boolean shows(TileInfo.Layer layer) {
            return switch (layer) {
                case PLAY -> true;
                case DETAIL -> detail;
                case BUILD -> build;
            };
        }
    }

    /** A space's info measured for drawing, and its ring's labels (« +3 » given, « −3 » taken). */
    static final class Layout {
        final TileInfo info;
        final OrderedText title;
        final int titleWidth, titleColor;
        final OrderedText[] lines;
        final ItemStack[] icons;
        final TileInfo.Glyph[] glyphs;
        final TileInfo.Layer[] layers;
        final int[] widths;
        final List<OrderedText> ringLabels;

        Layout(TileInfo info) {
            this.info = info;
            TextRenderer font = MinecraftClient.getInstance().textRenderer;
            title = info.title().asOrderedText();
            titleWidth = Math.round(font.getWidth(info.title()) * TITLE_SCALE);
            titleColor = 0xFF000000 | readable(info.accent());
            int count = info.lines().size();
            lines = new OrderedText[count];
            icons = new ItemStack[count];
            glyphs = new TileInfo.Glyph[count];
            layers = new TileInfo.Layer[count];
            widths = new int[count];
            for (int i = 0; i < count; i++) {
                TileInfo.Line line = info.lines().get(i);
                lines[i] = line.text().asOrderedText();
                icons[i] = line.icon();
                glyphs[i] = line.glyph();
                layers[i] = line.layer();
                boolean iconed = !line.icon().isEmpty() || line.glyph() != TileInfo.Glyph.NONE;
                widths[i] = font.getWidth(line.text()) + (iconed ? ICON + ICON_GAP : 0);
            }
            List<OrderedText> labels = new ArrayList<>(info.ring().size());
            for (ItemStack stack : info.ring()) {
                boolean taken = Boolean.TRUE.equals(stack.get(ModComponents.IS_NEGATIVE));
                labels.add(Text.literal((taken ? "−" : "+") + stack.getCount())
                        .withColor(taken ? 0xFF6B6B : 0x7CFC7C).asOrderedText());
            }
            ringLabels = List.copyOf(labels);
        }

        /** Whether line {@code i} is drawn in {@code view}. */
        boolean shown(int i, View view) {
            if (view.compact) return i == firstPlay();
            return view.shows(layers[i]);
        }

        private int firstPlay() {
            for (int i = 0; i < layers.length; i++) if (layers[i] == TileInfo.Layer.PLAY) return i;
            return -1;
        }

        /** Lines of the body (in game, the helmet's) and of the building part shown in {@code view}. */
        int bodyRows(View view) {
            int rows = 0;
            for (int i = 0; i < layers.length; i++) if (shown(i, view) && layers[i] != TileInfo.Layer.BUILD) rows++;
            return rows;
        }

        int buildRows(View view) {
            int rows = 0;
            for (int i = 0; i < layers.length; i++) if (shown(i, view) && layers[i] == TileInfo.Layer.BUILD) rows++;
            return rows;
        }

        /** Something to show in {@code view}: a line, or (not reduced) items circling the space. */
        boolean hasContent(View view) {
            return bodyRows(view) + buildRows(view) > 0 || !view.compact && !info.ring().isEmpty();
        }

        int width(View view) {
            int widest = titleWidth;
            for (int i = 0; i < widths.length; i++) if (shown(i, view)) widest = Math.max(widest, widths[i]);
            return widest + 2 * PAD;
        }

        /** Height in text pixels: the title, a rule, the body, then a rule and the building part if any. */
        int height(View view) {
            int body = bodyRows(view), build = buildRows(view);
            int height = PAD + TITLE_HEIGHT + PAD;
            if (body > 0) height += 2 * RULE_GAP + body * ROW;
            if (build > 0) height += 2 * RULE_GAP + build * ROW;
            return height;
        }
    }

    /** The role's colour, dark enough to read on the light plates. */
    static int readable(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        float luminance = Argb.luminance(rgb) / 255f;
        if (luminance <= 0.4f) return rgb & 0xFFFFFF;
        float k = 0.4f / luminance;
        return ((int) (r * k) << 16) | ((int) (g * k) << 8) | (int) (b * k);
    }

    // ---------------------------------------------------------------- in the world

    /**
     * Draws {@code layout} with its bottom edge's middle at (x, bottom, z) (world coordinates).
     *
     * @param scale block per text pixel
     */
    static void draw(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, Camera camera, World world, double x,
                     double bottom, double z, Layout layout, View view, WorldDraw.Plate plate, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer font = client.textRenderer;
        int width = layout.width(view), height = layout.height(view);
        matrices.push();
        matrices.translate(x - camera.getPos().x, bottom + height * scale / 2 - camera.getPos().y, z - camera.getPos().z);
        matrices.multiply(camera.getRotation());
        matrices.scale(scale, -scale, scale);
        float left = -width / 2f, top = -height / 2f;
        WorldDraw.plate(matrices, consumers, plate, left, top, -left, -top);
        // The title, centred and bigger
        matrices.push();
        matrices.translate(-layout.titleWidth / 2f, top + PAD, 0);
        matrices.scale(TITLE_SCALE, TITLE_SCALE, 1);
        font.draw(layout.title, 0, 0, layout.titleColor, false, matrices.peek().getPositionMatrix(), consumers,
                TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LIGHT);
        matrices.pop();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float y = top + PAD + TITLE_HEIGHT + PAD / 2f;
        for (int part = 0; part < 2; part++) {
            boolean build = part == 1;
            if ((build ? layout.buildRows(view) : layout.bodyRows(view)) == 0) continue;
            WorldDraw.rule(matrices, consumers, left + PAD, -left - PAD, y, RULE);
            y += RULE_GAP + 1;
            for (int i = 0; i < layout.lines.length; i++) {
                if (!layout.shown(i, view) || (layout.layers[i] == TileInfo.Layer.BUILD) != build) continue;
                float textX = left + PAD;
                ItemStack icon = layout.icons[i];
                if (!icon.isEmpty()) {
                    matrices.push();
                    matrices.translate(textX + ICON / 2f, y + 3.5f, 0.5f);
                    matrices.scale(ICON, -ICON, 0.5f);
                    client.getItemRenderer().renderItem(icon, ModelTransformationMode.GUI, LIGHT, OverlayTexture.DEFAULT_UV,
                            matrices, consumers, world, 0);
                    matrices.pop();
                    textX += ICON + ICON_GAP;
                } else if (layout.glyphs[i] != TileInfo.Glyph.NONE) {
                    TileGlyphs.draw(matrices, consumers, layout.glyphs[i], textX, y - 0.5f, ICON);
                    textX += ICON + ICON_GAP;
                }
                font.draw(layout.lines[i], textX, y, WorldDraw.PLATE_TEXT, false, matrix, consumers,
                        TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LIGHT);
                y += ROW;
            }
            y += RULE_GAP - 1;
        }
        consumers.draw();
        matrices.pop();
    }

    // ---------------------------------------------------------------- on the HUD

    /** The same panel on the HUD, its top edge's middle at (centreX, top). */
    static void drawHud(DrawContext context, Layout layout, View view, int centreX, int top) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int width = layout.width(view), height = layout.height(view);
        int left = centreX - width / 2;
        ToolHud.plate(context, left, top, width, height, ToolHud.Plate.TEAL);
        context.getMatrices().push();
        context.getMatrices().translate(centreX - layout.titleWidth / 2f, top + PAD, 0);
        context.getMatrices().scale(TITLE_SCALE, TITLE_SCALE, 1);
        // The panel is as wide as its title (scaled): its slot is the title's width
        HudDepth.onTop(context, () -> UiText.line(context, font, layout.title, 0, 0, font.getWidth(layout.title), layout.titleColor, false));
        context.getMatrices().pop();
        int y = top + PAD + TITLE_HEIGHT + PAD / 2;
        for (int part = 0; part < 2; part++) {
            boolean build = part == 1;
            if ((build ? layout.buildRows(view) : layout.bodyRows(view)) == 0) continue;
            int rule = y;
            HudDepth.onTop(context, () -> context.fill(left + PAD, rule, left + width - PAD, rule + 1, RULE));
            y += RULE_GAP + 1;
            for (int i = 0; i < layout.lines.length; i++) {
                if (!layout.shown(i, view) || (layout.layers[i] == TileInfo.Layer.BUILD) != build) continue;
                int textX = left + PAD;
                ItemStack icon = layout.icons[i];
                if (!icon.isEmpty()) {
                    context.getMatrices().push();
                    context.getMatrices().translate(textX, y - 1, 0);
                    context.getMatrices().scale(ICON / 16f, ICON / 16f, 1);
                    HudDepth.item(context, () -> context.drawItem(icon, 0, 0));
                    context.getMatrices().pop();
                    textX += ICON + ICON_GAP;
                } else if (layout.glyphs[i] != TileInfo.Glyph.NONE) {
                    TileInfo.Glyph glyph = layout.glyphs[i];
                    int gx = textX, gy = y;
                    HudDepth.onTop(context, () -> TileGlyphs.drawHud(context, glyph, gx, gy));
                    textX += ICON + ICON_GAP;
                }
                OrderedText line = layout.lines[i];
                int lx = textX, ly = y;
                HudDepth.onTop(context, () -> UiText.line(context, font, line, lx, ly, left + width - PAD - lx, ToolHud.TEXT, false));
                y += ROW;
            }
            y += RULE_GAP - 1;
        }
    }
}
