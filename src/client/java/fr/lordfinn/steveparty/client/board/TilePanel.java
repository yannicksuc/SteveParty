package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.utils.Argb;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
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
 * The panel of a board space's game info ({@link TileInfo}): a plate cut like the mod's screens (as the board view's),
 * facing the camera, its title (a square in the role's colour, its name) over its lines, an item icon left of a line
 * when it has one. Everything measured once when the info arrives ({@link Layout}); a frame only draws.
 */
public final class TilePanel {
    /** Text pixels: a row, the plate's padding, an icon. */
    static final int ROW = 10, PAD = 5, ICON = 9, ICON_GAP = 2;
    private static final int LIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    private TilePanel() {
    }

    /** A space's info measured for drawing, and its ring's labels (« +3 » given, « −3 » taken). */
    static final class Layout {
        final TileInfo info;
        final OrderedText title;
        final OrderedText[] lines;
        final ItemStack[] icons;
        final int fullWidth, compactWidth;
        final List<OrderedText> ringLabels;

        Layout(TileInfo info) {
            this.info = info;
            TextRenderer font = MinecraftClient.getInstance().textRenderer;
            Text titleText = Text.empty().append(Text.literal("■ ").withColor(visible(info.accent()))).append(info.title());
            title = titleText.asOrderedText();
            int titleWidth = font.getWidth(titleText);
            int count = info.lines().size();
            lines = new OrderedText[count];
            icons = new ItemStack[count];
            int widest = titleWidth, first = titleWidth;
            for (int i = 0; i < count; i++) {
                TileInfo.Line line = info.lines().get(i);
                lines[i] = line.text().asOrderedText();
                icons[i] = line.icon();
                int width = font.getWidth(line.text()) + (line.icon().isEmpty() ? 0 : ICON + ICON_GAP);
                widest = Math.max(widest, width);
                if (i == 0) first = Math.max(first, width);
            }
            fullWidth = widest + 2 * PAD;
            compactWidth = first + 2 * PAD;
            List<OrderedText> labels = new ArrayList<>(info.ring().size());
            for (ItemStack stack : info.ring()) {
                boolean taken = Boolean.TRUE.equals(stack.get(ModComponents.IS_NEGATIVE));
                labels.add(Text.literal((taken ? "−" : "+") + stack.getCount())
                        .withColor(taken ? 0xFF6B6B : 0x7CFC7C).asOrderedText());
            }
            ringLabels = List.copyOf(labels);
        }

        int rows(boolean full) {
            return 1 + (full ? lines.length : Math.min(1, lines.length));
        }

        /** Height in text pixels. */
        int height(boolean full) {
            return rows(full) * ROW + 2 * PAD - 2;
        }

        int width(boolean full) {
            return full ? fullWidth : compactWidth;
        }
    }

    /** A colour readable on the light plates: a light one (a white cartridge's) is drawn grey. */
    static int visible(int rgb) {
        return Argb.luminance(rgb) > 215 ? 0xA8A8A8 : rgb & 0xFFFFFF;
    }

    /**
     * Draws {@code layout} with its bottom edge's middle at (x, bottom, z) (world coordinates).
     *
     * @param full  all its lines (else its title and first line)
     * @param scale block per text pixel
     */
    static void draw(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, Camera camera, World world, double x,
                     double bottom, double z, Layout layout, boolean full, WorldDraw.Plate plate, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer font = client.textRenderer;
        int width = layout.width(full), height = layout.height(full);
        matrices.push();
        matrices.translate(x - camera.getPos().x, bottom + height * scale / 2 - camera.getPos().y, z - camera.getPos().z);
        matrices.multiply(camera.getRotation());
        matrices.scale(scale, -scale, scale);
        float left = -width / 2f, top = -height / 2f;
        WorldDraw.plate(matrices, consumers, plate, left, top, -left, -top);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float y = top + PAD;
        font.draw(layout.title, left + PAD, y, WorldDraw.PLATE_TEXT, false, matrix, consumers,
                TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LIGHT);
        int shown = layout.rows(full) - 1;
        for (int i = 0; i < shown; i++) {
            y += ROW;
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
            }
            font.draw(layout.lines[i], textX, y, WorldDraw.PLATE_TEXT, false, matrix, consumers,
                    TextRenderer.TextLayerType.POLYGON_OFFSET, 0, LIGHT);
        }
        consumers.draw();
        matrices.pop();
    }
}
